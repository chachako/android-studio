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

#include <grpc++/grpc++.h>
#include <gtest/gtest.h>

#include "perfetto/trace_processor/basic_types.h"
#include "perfetto/trace_processor/read_trace.h"
#include "perfetto/trace_processor/trace_processor.h"

namespace profiler {
namespace perfetto {
namespace {

typedef proto::QueryParameters::ThreadStatesParameters ThreadStatesParameters;
typedef proto::ThreadStatesResult ThreadStatesResult;
typedef proto::SchedulingEventsResult::SchedulingEvent SchedulingEvent;

typedef ::perfetto::trace_processor::TraceProcessor TraceProcessor;
typedef ::perfetto::trace_processor::Config Config;

const std::string TANK_TESTDATA_PATH(
    "tools/base/profiler/native/trace_processor_daemon/testdata/tank.trace");

const std::string EMPTY_SCHED_TABLE_TESTDATA_PATH(
    "tools/base/profiler/native/trace_processor_daemon/testdata/"
    "empty_sched_table.trace");

const long TANK_PROCESS_PID = 9796;

// SchedulingEvent::SchedulingState doesn't define the running state, so we
// use a magic number in this test to simplify the logic.
const int RUNNING_STATE = 1000;

std::unique_ptr<TraceProcessor> LoadTrace(std::string trace_path) {
  Config config;
  config.ingest_ftrace_in_raw_table = false;
  auto tp = TraceProcessor::CreateInstance(config);
  auto read_status = ReadTrace(tp.get(), trace_path.c_str(), {});
  EXPECT_TRUE(read_status.ok());
  return tp;
}

int ConvertThreadStateToInt(
    ThreadStatesResult::ThreadStateEvent::ThreadState state) {
  if (state.running())
    return RUNNING_STATE;
  else
    return state.non_running();
}

TEST(ThreadStateRequestHandlerTest, PopulateEventsByProcessId) {
  auto tp = LoadTrace(TANK_TESTDATA_PATH);
  auto handler = ThreadStateRequestHandler(tp.get());

  ThreadStatesParameters params_proto;
  params_proto.set_process_id(TANK_PROCESS_PID);

  ThreadStatesResult result;
  handler.PopulateEvents(params_proto, &result);

  // TODO(b/324640108): Update the expected value after upgrading Perfetto
  // version, The expected value should be 204456.
  EXPECT_EQ(300079, result.state_event_size());

  std::unordered_map<int, long> states_count = {
      {SchedulingEvent::UNKNOWN, 0},
      {SchedulingEvent::RUNNABLE, 0},
      {SchedulingEvent::RUNNABLE_PREEMPTED, 0},
      {SchedulingEvent::SLEEPING, 0},
      {SchedulingEvent::SLEEPING_UNINTERRUPTIBLE, 0},
      {SchedulingEvent::WAKE_KILL, 0},
      {SchedulingEvent::WAKING, 0},
      {SchedulingEvent::DEAD, 0},
      {RUNNING_STATE, 0}};

  EXPECT_EQ(result.process_id(), TANK_PROCESS_PID);
  for (auto event : result.state_event()) {
    EXPECT_GE(event.timestamp_nanoseconds(), 0);
    EXPECT_GT(event.duration_nanoseconds(), 0);

    states_count[ConvertThreadStateToInt(event.state())]++;
  }

  EXPECT_EQ(states_count[SchedulingEvent::UNKNOWN], 0);
  // TODO(b/324640108): Update the expected value after upgrading Perfetto
  // version, The expected value should be 1552.
  EXPECT_EQ(states_count[SchedulingEvent::RUNNABLE], 97175);
  EXPECT_EQ(states_count[SchedulingEvent::RUNNABLE_PREEMPTED], 5020);
  EXPECT_EQ(states_count[SchedulingEvent::SLEEPING], 89828);
  EXPECT_EQ(states_count[SchedulingEvent::SLEEPING_UNINTERRUPTIBLE], 5822);
  EXPECT_EQ(states_count[SchedulingEvent::WAKE_KILL], 0);
  EXPECT_EQ(states_count[SchedulingEvent::WAKING], 0);
  EXPECT_EQ(states_count[SchedulingEvent::DEAD], 4);
  EXPECT_EQ(states_count[RUNNING_STATE], 102230);
}

}  // namespace
}  // namespace perfetto
}  // namespace profiler
