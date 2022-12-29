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
#ifndef PERFD_CPU_COMMANDS_TRACE_COMMAND_UTILS_H_
#define PERFD_CPU_COMMANDS_TRACE_COMMAND_UTILS_H_

#include <unordered_map>

#include "perfd/common/capture_info.h"
#include "proto/commands.pb.h"
#include "proto/common.pb.h"

using profiler::proto::Event;
using profiler::proto::ProfilerType;

namespace profiler {

profiler::proto::Event PopulateTraceEvent(
    const CaptureInfo& capture, const profiler::proto::Command& command_data,
    const ProfilerType profiler_type, bool is_end);

profiler::proto::Event PopulateTraceStatusEvent(
    const profiler::proto::Command& command_data,
    const ProfilerType profiler_type, const CaptureInfo* capture);

const std::unordered_map<ProfilerType, proto::Event_Kind>
    profiler_type_to_event = {{ProfilerType::CPU, Event::CPU_TRACE},
                              {ProfilerType::MEMORY, Event::MEMORY_TRACE}};
}  // namespace profiler

#endif  // PERFD_CPU_COMMANDS_TRACE_COMMAND_UTILS_H_
