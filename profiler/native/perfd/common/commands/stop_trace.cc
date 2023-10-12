/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include "stop_trace.h"

#include <sstream>
#include "perfd/common/capture_info.h"
#include "perfd/common/utils/trace_command_utils.h"
#include "perfd/sessions/sessions_manager.h"
#include "proto/cpu.pb.h"
#include "utils/fs/disk_file_system.h"
#include "utils/process_manager.h"
#include "utils/thread_name.h"

using grpc::Status;
using profiler::proto::Command;
using profiler::proto::Event;
using profiler::proto::TraceStopStatus;

namespace profiler {

namespace {
// "cache/complete" is where the generic bytes rpc fetches content
constexpr char kCacheLocation[] = "cache/complete/";

// Helper function to stop the tracing. This function works in the async
// environment because it doesn't require a |profiler::StopTrace| object.
void Stop(Daemon* daemon, const profiler::proto::Command command_data,
          TraceManager* trace_manager, SessionsManager* sessions_manager,
          bool is_task_based_ux_enabled) {
  auto& stop_command = command_data.stop_trace();
  auto profiler_type = stop_command.profiler_type();
  const std::string& app_name = stop_command.configuration().app_name();

  int64_t stop_timestamp;
  bool stopped_from_api = stop_command.has_api_stop_metadata();
  if (stopped_from_api) {
    stop_timestamp = stop_command.api_stop_metadata().stop_timestamp();
  } else {
    stop_timestamp = daemon->clock()->GetCurrentTime();
  }

  const auto* ongoing = trace_manager->GetOngoingCapture(app_name);
  Event status_event =
      PopulateTraceStatusEvent(command_data, profiler_type, ongoing);

  if (ongoing == nullptr || profiler_type == ProfilerType::UNSPECIFIED) {
    // PopulateTraceStatusEvent will create a failure-based status event and
    // send it right back if either the ongoing capture is null or profiler_type
    // is UNSPECIFIED. After this early exit, we need to also exit early from
    // this method after sending the TRACE_STATUS event to prevent calling
    // StopCapture with erroneous preconditions.
    daemon->buffer()->Add(status_event);

    // In the Task-Based UX, if stopping the trace fails, we want to also end
    // the session wrapping such capture.
    if (is_task_based_ux_enabled) {
      sessions_manager->EndSession(daemon, command_data.session_id());
    }
    return;
  }

  CaptureInfo* capture = nullptr;
  TraceStopStatus status;
  capture = trace_manager->StopCapture(
      stop_timestamp, app_name, stop_command.need_trace_response(), &status);
  auto* stop_status =
      status_event.mutable_trace_status()->mutable_trace_stop_status();
  stop_status->CopyFrom(status);

  daemon->buffer()->Add(status_event);
  if (capture != nullptr) {
    if (status.status() == TraceStopStatus::SUCCESS) {
      std::string from_file_name;
      if (stopped_from_api) {
        // The trace file has been sent via SendBytes API before the command
        // arrives.
        from_file_name = CurrentProcess::dir();
        from_file_name.append(kCacheLocation)
            .append(stop_command.api_stop_metadata().trace_name());
      } else {
        // TODO b/133321803 save this move by having Daemon generate a path in
        // the byte cache that traces can output contents to directly.
        from_file_name = capture->configuration.temp_path();
      }
      std::ostringstream oss;

      int64_t file_id;
      if (profiler_type == ProfilerType::CPU) {
        file_id = capture->trace_id;
      } else {
        // profiler_type == ProfilerType::MEMORY
        file_id = capture->start_timestamp;
      }
      oss << CurrentProcess::dir() << kCacheLocation << file_id;

      std::string to_file_name = oss.str();
      DiskFileSystem fs;
      bool move_success = fs.MoveFile(from_file_name, to_file_name);
      if (!move_success) {
        capture->stop_status.set_status(TraceStopStatus::CANNOT_READ_FILE);
        capture->stop_status.set_error_message(
            "Failed to read trace from device");
      }
    }
    Event trace_event =
        PopulateTraceEvent(*capture, command_data, profiler_type, true);
    daemon->buffer()->Add(trace_event);
  } else {
    // When execution reaches here, a TRACE_STATUS event has been sent
    // to signal the stopping has initiated. In case the ongoing recording
    // cannot be found when StopProfiling() is called, we still a CPU_TRACE
    // event to mark the end of the stopping.
    status.set_error_message("No ongoing capture exists");
    status.set_status(TraceStopStatus::NO_ONGOING_PROFILING);

    Event trace_event =
        PopulateTraceEvent(*ongoing, command_data, profiler_type, true);
    // The PopulateTraceEvent method will utilize the passed in capture object's
    // status to set the stop_status. Whether or not a stop_status exists in the
    // ongoing capture, we should override it by setting it to the status
    // retrieved from the StopCapture call done above. This gives us the most
    // accurate stoppage status.
    trace_event.mutable_trace_data()
        ->mutable_trace_ended()
        ->mutable_trace_info()
        ->mutable_stop_status()
        ->CopyFrom(status);
    daemon->buffer()->Add(trace_event);
  }

  // In the Task-Based UX, when the trace is complete, as indicated by he
  // CPU_TRACE or MEMORY_TRACE event, we want to also end the session wrapping
  // such capture.
  if (is_task_based_ux_enabled) {
    sessions_manager->EndSession(daemon, command_data.session_id());
  }
}

}  // namespace

Status StopTrace::ExecuteOn(Daemon* daemon) {
  // In order to make this command to return immediately, start a new
  // detached thread to stop CPU recording which which may take several seconds.
  // For example, we may need to wait for several seconds before the trace files
  // from ART to be complete.
  //
  // We need to capture the values of the fields of |this| object because when
  // the thread is executing, |this| object may be recycled.
  profiler::proto::Command command_data = command();
  TraceManager* trace_manager = trace_manager_;
  SessionsManager* sessions_manager = sessions_manager_;
  bool is_task_based_ux_enabled = is_task_based_ux_enabled_;
  std::thread worker([daemon, command_data, trace_manager, sessions_manager,
                      is_task_based_ux_enabled]() {
    SetThreadName("Studio:StopTrace");
    Stop(daemon, command_data, trace_manager, sessions_manager,
         is_task_based_ux_enabled);
  });
  worker.detach();
  return Status::OK;
}

}  // namespace profiler
