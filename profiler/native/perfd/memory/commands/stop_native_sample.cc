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
#include "stop_native_sample.h"

#include "perfd/common/utils/trace_command_utils.h"
#include "proto/memory_data.pb.h"
#include "proto/trace.pb.h"

using grpc::Status;
using profiler::proto::Event;
using profiler::proto::MemoryHeapDumpData;
using profiler::proto::ProfilerType;
using profiler::proto::TraceStopStatus;
using std::string;

namespace profiler {
// "cache/complete" is where the generic bytes rpc fetches contents
constexpr char kCacheLocation[] = "cache/complete/";

Status StopNativeSample::ExecuteOn(Daemon* daemon) {
  auto& stop_command = command().stop_native_sample();
  const std::string& app_name = stop_command.configuration().app_name();
  // Used as the group id for this recording's events.
  // The raw bytes will be available in the file cache via this id.
  int64_t stop_timestamp;
  bool stopped_from_api = stop_command.has_api_stop_metadata();
  if (stopped_from_api) {
    stop_timestamp = stop_command.api_stop_metadata().stop_timestamp();
  } else {
    stop_timestamp = daemon->clock()->GetCurrentTime();
  }

  const auto* ongoing = trace_manager_->GetOngoingCapture(app_name);
  Event status_event =
      PopulateTraceStatusEvent(command(), ProfilerType::MEMORY, ongoing);
  auto* stop_status =
      status_event.mutable_trace_status()->mutable_trace_stop_status();

  if (ongoing == nullptr) {
    daemon->buffer()->Add(status_event);
    return Status::OK;
  }

  int64_t trace_id = ongoing->trace_id;
  TraceStopStatus status;
  auto* capture = trace_manager_->StopCapture(
      stop_timestamp, app_name, stop_command.need_trace_response(), &status);
  stop_status->CopyFrom(status);

  daemon->buffer()->Add(status_event);

  // Send CPU_TRACE event after the stopping has returned, successfully or not.
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
      oss << CurrentProcess::dir() << kCacheLocation
          << capture->start_timestamp;
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
        PopulateTraceEvent(*capture, command(), ProfilerType::MEMORY, true);
    daemon->buffer()->Add(trace_event);
  } else {
    // When execution reaches here, a TRACE_STATUS event has been sent
    // to signal the stopping has initiated. In case the ongoing recording
    // cannot be found when StopProfiling() is called, we still a CPU_TRACE
    // event to mark the end of the stopping.
    status.set_error_message("No ongoing capture exists");
    status.set_status(TraceStopStatus::NO_ONGOING_PROFILING);

    Event trace_event;
    trace_event.set_pid(command().pid());
    trace_event.set_kind(Event::MEMORY_TRACE);
    trace_event.set_group_id(capture->start_timestamp);
    trace_event.set_is_ended(true);
    trace_event.set_command_id(command().command_id());
    trace_event.mutable_trace_data()
        ->mutable_trace_ended()
        ->mutable_trace_info()
        ->mutable_stop_status()
        ->CopyFrom(status);
    daemon->buffer()->Add(trace_event);
  }

  return Status::OK;
}

}  // namespace profiler