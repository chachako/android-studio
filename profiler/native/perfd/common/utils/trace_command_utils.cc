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
#include "trace_command_utils.h"

using profiler::proto::Command;
using profiler::proto::Event;
using profiler::proto::ProfilerType;
using profiler::proto::TraceStopStatus;

namespace profiler {

Event PopulateTraceEvent(const CaptureInfo& capture,
                         const profiler::proto::Command& command_data,
                         const ProfilerType profiler_type, bool is_end) {
  // If not a valid ProfilerType, there will not be a mapping to a valid Event
  // Kind. This is a doubly-protective measure as we already check and return
  // early from this methods caller if the ProfilerType == UNSPECIFIED.
  assert(profiler_type_to_event.find(profiler_type) !=
         profiler_type_to_event.end());
  auto event_kind = profiler_type_to_event.find(profiler_type)->second;

  Event event;
  event.set_pid(command_data.pid());
  event.set_kind(event_kind);
  event.set_is_ended(is_end);
  event.set_command_id(command_data.command_id());

  auto* trace_info = is_end ? event.mutable_trace_data()
                                  ->mutable_trace_ended()
                                  ->mutable_trace_info()
                            : event.mutable_trace_data()
                                  ->mutable_trace_started()
                                  ->mutable_trace_info();
  if (is_end) {
    event.set_timestamp(capture.end_timestamp);
    trace_info->mutable_stop_status()->CopyFrom(capture.stop_status);
  } else {
    event.set_timestamp(capture.start_timestamp);
  }
  trace_info->set_trace_id(capture.trace_id);
  trace_info->set_from_timestamp(capture.start_timestamp);
  trace_info->set_to_timestamp(capture.end_timestamp);
  trace_info->mutable_configuration()->CopyFrom(capture.configuration);
  trace_info->mutable_start_status()->CopyFrom(capture.start_status);

  if (profiler_type == ProfilerType::CPU) {
    // CPU trace uses capture's trace id as the group id.
    event.set_group_id(capture.trace_id);
  } else {
    // profiler_type is MEMORY
    // Memory trace uses start timestamp of trace as group id.
    event.set_group_id(capture.start_timestamp);
    if (!is_end) {
      // LLONG_MAX as to timestamp indicates ongoing trace for memory tracing.
      trace_info->set_to_timestamp(LLONG_MAX);
    }
  }

  return event;
}

Event PopulateTraceStatusEvent(const profiler::proto::Command& command_data,
                               const ProfilerType profiler_type,
                               const CaptureInfo* capture) {
  Event status_event;
  status_event.set_pid(command_data.pid());
  status_event.set_kind(Event::TRACE_STATUS);
  status_event.set_command_id(command_data.command_id());
  status_event.set_is_ended(true);

  auto* stop_status =
      status_event.mutable_trace_status()->mutable_trace_stop_status();
  if (capture == nullptr) {
    stop_status->set_error_message("No ongoing capture exists");
    stop_status->set_status(TraceStopStatus::NO_ONGOING_PROFILING);
    return status_event;
  }

  if (profiler_type == ProfilerType::UNSPECIFIED) {
    stop_status->set_status(TraceStopStatus::STOP_COMMAND_FAILED);
    stop_status->set_error_message("No trace type specified");
    return status_event;
  }

  if (profiler_type == ProfilerType::CPU) {
    status_event.set_group_id(capture->trace_id);
  } else {
    // profiler_type == ProfilerType::MEMORY
    status_event.set_group_id(capture->start_timestamp);
  }
  // This event is to acknowledgethe stop command. It doesn't have the full
  // result. Since UNSPECIFIED is the default value, it is actually an no-op.
  stop_status->set_status(TraceStopStatus::UNSPECIFIED);
  return status_event;
}

}  // namespace profiler