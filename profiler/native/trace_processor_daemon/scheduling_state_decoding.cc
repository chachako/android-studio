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

#include "scheduling_state_decoding.h"

typedef profiler::perfetto::proto::SchedulingEventsResult::SchedulingEvent
    SchedulingEvent;

namespace profiler {
namespace perfetto {

SchedulingEvent::SchedulingState CpuSchedulingState::Decode(
    const std::string& state) {
  if (STATE_RUNNABLE.compare(state) == 0) {
    return SchedulingEvent::RUNNABLE;
  } else if (STATE_RUNNABLE_PREEMPTED.compare(state) == 0) {
    return SchedulingEvent::RUNNABLE_PREEMPTED;
  } else if (STATE_SLEEPING.compare(state) == 0) {
    return SchedulingEvent::SLEEPING;
  } else if (STATE_UNINTERRUPTIBLE.compare(state) == 0 ||
             STATE_UNINTERRUPTIBLE_WAKEKILL.compare(state) == 0) {
    return SchedulingEvent::SLEEPING_UNINTERRUPTIBLE;
  } else if (STATE_WAKEKILL.compare(state) == 0) {
    return SchedulingEvent::WAKE_KILL;
  } else if (STATE_WAKING.compare(state) == 0) {
    return SchedulingEvent::WAKING;
  } else if (STATE_TASK_DEAD_1.compare(state) == 0 ||
             STATE_TASK_DEAD_2.compare(state) == 0 ||
             STATE_EXIT_DEAD.compare(state) == 0 ||
             STATE_ZOMBIE.compare(state) == 0) {
    return SchedulingEvent::DEAD;
  } else {
    std::cerr << "Unknown scheduling state encountered: " << state << std::endl;
    return SchedulingEvent::UNKNOWN;
  }
}

}  // namespace perfetto
}  // namespace profiler
