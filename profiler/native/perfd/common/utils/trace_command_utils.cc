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
using profiler::proto::TraceStopStatus;

namespace profiler {

Event PopulateTraceEvent(const CaptureInfo& capture,
                         const profiler::proto::Command& command_data,
                         proto::Event_Kind event_kind, bool is_end) {
  Event event;
  event.set_pid(command_data.pid());
  event.set_kind(event_kind);
  event.set_is_ended(is_end);
  event.set_command_id(command_data.command_id());
  if (is_end) {
    event.set_timestamp(capture.end_timestamp);
  } else {
    event.set_timestamp(capture.start_timestamp);
  }

  assert(event_kind == Event::CPU_TRACE || event_kind == Event::MEM_TRACE);

  auto* trace_info = is_end ? event.mutable_trace_data()
                                  ->mutable_trace_ended()
                                  ->mutable_trace_info()
                            : event.mutable_trace_data()
                                  ->mutable_trace_started()
                                  ->mutable_trace_info();

  trace_info->set_trace_id(capture.trace_id);
  trace_info->set_from_timestamp(capture.start_timestamp);
  trace_info->set_to_timestamp(capture.end_timestamp);
  trace_info->mutable_configuration()->CopyFrom(capture.configuration);
  trace_info->mutable_start_status()->CopyFrom(capture.start_status);
  if (is_end) {
    trace_info->mutable_stop_status()->CopyFrom(capture.stop_status);
  }

  if (event_kind == Event::CPU_TRACE) {
    // CPU trace uses capture's trace id as the group id.
    event.set_group_id(capture.trace_id);
  } else {
    // event_kind is MEM_TRACE
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
                               const proto::Event_Kind event_kind,
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
  } else {
    if (event_kind == Event::CPU_TRACE) {
      status_event.set_group_id(capture->trace_id);
    } else {
      // Event is for memory tracing, event_kind == MEM_TRACE
      status_event.set_group_id(capture->start_timestamp);
    }
    // This event is to acknowledgethe stop command. It doesn't have the full
    // result. Since UNSPECIFIED is the default value, it is actually an no-op.
    stop_status->set_status(TraceStopStatus::UNSPECIFIED);
  }
  return status_event;
}

}  // namespace profiler