/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include "thread_state_request_handler.h"

#include "scheduling_state_decoding.h"

using namespace profiler::perfetto;
using profiler::perfetto::proto::QueryParameters;
using profiler::perfetto::proto::SchedulingEventsResult;
using profiler::perfetto::proto::ThreadStatesResult;

typedef QueryParameters::ThreadStatesParameters ThreadStatesParameters;

void ThreadStateRequestHandler::PopulateEvents(ThreadStatesParameters params,
                                               ThreadStatesResult* result) {
  if (result == nullptr) {
    return;
  }

  std::string query_string =
      "SELECT tid, ts, dur, state "
      "FROM thread_state INNER JOIN thread using(utid) "
      "                  INNER JOIN process using(upid) "
      "WHERE pid = " +
      std::to_string(params.process_id()) + " ORDER BY tid ASC, ts ASC";

  result->set_process_id(params.process_id());

  auto it_state = tp_->ExecuteQuery(query_string);
  while (it_state.Next()) {
    auto state_proto = result->add_state_event();

    auto thread_id = it_state.Get(0).long_value;
    state_proto->set_thread_id(thread_id);

    auto ts_nanos = it_state.Get(1).long_value;
    state_proto->set_timestamp_nanoseconds(ts_nanos);

    auto dur_nanos = it_state.Get(2).long_value;
    // There are occasionally some events' duration being -1, even when
    // querying using Perfetto's trace processor. Set them to 1 for
    // common data assumption.
    if (dur_nanos < 0) dur_nanos = 1;
    state_proto->set_duration_nanoseconds(dur_nanos);

    auto state_sql_value = it_state.Get(3);
    auto* mutable_state = state_proto->mutable_state();
    if (state_sql_value.is_null()) {
      mutable_state->set_non_running(
          SchedulingEventsResult::SchedulingEvent::UNKNOWN);
    } else {
      auto state = state_sql_value.string_value;
      if (STATE_RUNNING.compare(state) == 0) {
        mutable_state->set_running(true);
      } else {
        mutable_state->set_non_running(CpuSchedulingState::Decode(state));
      }
    }
  }
}
