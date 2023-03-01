/*
 * Copyright (C) 2022 The Android Open Source Project
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
 *
 */

#include "tools/base/deploy/agent/native/compose_status.h"

#include <unordered_map>
#include <unordered_set>

#include "tools/base/deploy/agent/native/compose_status.h"
#include "tools/base/deploy/agent/native/recompose.h"
#include "tools/base/deploy/common/log.h"

namespace deploy {

proto::AgentComposeStatusResponse ComposeStatus(
    jvmtiEnv* jvmti, JNIEnv* jni, const proto::ComposeStatusRequest& req) {
  proto::AgentComposeStatusResponse resp;

  Recompose recompose(jvmti, jni);
  jobject reloader = recompose.GetComposeHotReload();

  if (!reloader) {
    resp.set_status(proto::AgentComposeStatusResponse::OK);
    return resp;
  }

  std::vector<std::string> names;
  std::vector<std::string> messages;
  std::vector<bool> recoverable;
  std::string error = "";

  bool success = recompose.getCurrentErrors(reloader, &recoverable, &names,
                                            &messages, error);

  if (!success) {
    resp.set_status(proto::AgentComposeStatusResponse::ERROR);
    resp.set_error_message("Fail to invoke recompose.getCurrentErrors");
  }

  if (names.size() != recoverable.size()) {
    resp.set_status(proto::AgentComposeStatusResponse::ERROR);
    resp.set_error_message("names.size() differs from recoverable.size()");
    return resp;
  }

  for (int i = 0; i < names.size(); i++) {
    auto proto = resp.add_exceptions();
    proto->set_recoverable(recoverable[i]);
    proto->set_exception_class_name(names[i].c_str());
    proto->set_message(messages[i].c_str());
  }

  if (error.empty()) {
    resp.set_status(proto::AgentComposeStatusResponse::OK);
  } else {
    resp.set_status(proto::AgentComposeStatusResponse::ERROR);
    resp.set_error_message(error.c_str());
  }
  return resp;
}

}  // namespace deploy
