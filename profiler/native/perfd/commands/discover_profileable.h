/*
 * Copyright (C) 2021 The Android Open Source Project
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
#ifndef PERFD_COMMANDS_DISCOVER_PROFILEABLE_H_
#define PERFD_COMMANDS_DISCOVER_PROFILEABLE_H_

#include "daemon/daemon.h"
#include "perfd/common/trace_manager.h"
#include "proto/transport.grpc.pb.h"

namespace profiler {

class DiscoverProfileable : public CommandT<DiscoverProfileable> {
 public:
  DiscoverProfileable(const proto::Command& command,
                      TraceManager* trace_manager)
      : CommandT(command), trace_manager_(trace_manager) {}

  static Command* Create(const proto::Command& command,
                         TraceManager* trace_manager) {
    return new DiscoverProfileable(command, trace_manager);
  }

  virtual grpc::Status ExecuteOn(Daemon* daemon) override;

 private:
  // An instance of TraceManager is passed into this command
  // so that it can be passed into the creation of
  // a ProfileableDetector instance. This profileable detector
  // utilizes the trace manager during the check for a process being
  // profileable. By calling TraceManager::GetOngoingCpature we can
  // see if the inspected process has an ongoing capture already.
  // If so, we can prevent the call to the ProfileableChecker::Check
  // method. This method, if called on a process that has an ongoing
  // capture, can lead to harmful side-effects. One of which being it's
  // execution of the `profile stop` command prematurely ending an
  // ongoing capture of a startup trace.
  TraceManager* trace_manager_;
};

}  // namespace profiler

#endif  // PERFD_COMMANDS_DISCOVER_PROFILEABLE_H_
