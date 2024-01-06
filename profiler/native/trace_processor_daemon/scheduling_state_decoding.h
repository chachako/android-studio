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

#ifndef _TRACE_PROCESSOR_DAEMON_SCHEDULING_STATE_DECODING_H_
#define _TRACE_PROCESSOR_DAEMON_SCHEDULING_STATE_DECODING_H_

#include <string_view>

#include "proto/trace_processor_service.pb.h"

namespace profiler {
namespace perfetto {

// Non-running states.
// Note: the mapping in Perfetto is different from ftrace. See
// https://perfetto.dev/docs/data-sources/cpu-scheduling#decoding-code-end_state-code-
constexpr std::string_view STATE_RUNNABLE = "R";
constexpr std::string_view STATE_RUNNABLE_PREEMPTED = "R+";
constexpr std::string_view STATE_SLEEPING = "S";
constexpr std::string_view STATE_UNINTERRUPTIBLE = "D";
constexpr std::string_view STATE_UNINTERRUPTIBLE_WAKEKILL = "DK";
constexpr std::string_view STATE_WAKEKILL = "K";
constexpr std::string_view STATE_WAKING = "W";
// Both maps to Task DEAD states, depends on the kernel version.
constexpr std::string_view STATE_TASK_DEAD_1 = "x";
constexpr std::string_view STATE_TASK_DEAD_2 = "I";
constexpr std::string_view STATE_EXIT_DEAD = "X";
constexpr std::string_view STATE_ZOMBIE = "Z";

// Running state. Its definition is different from non-running states.
// See https://perfetto.dev/docs/analysis/sql-tables#thread_state
constexpr std::string_view STATE_RUNNING = "Running";

class CpuSchedulingState {
 public:
  // Returns the enum value for the given scheduing state in string format.
  static profiler::perfetto::proto::SchedulingEventsResult::SchedulingEvent::
      SchedulingState
      Decode(const std::string& state);
};

}  // namespace perfetto
}  // namespace profiler
#endif  //  _TRACE_PROCESSOR_DAEMON_SCHEDULING_STATE_DECODING_H_
