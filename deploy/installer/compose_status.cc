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
 */

#include "tools/base/deploy/installer/compose_status.h"

#include "tools/base/deploy/installer/binary_extract.h"

namespace deploy {

void ComposeStatusCommand::ParseParameters(
    const proto::InstallerRequest& request) {
  if (!request.has_compose_status_request()) {
    return;
  }

  request_ = request.compose_status_request();
  package_name_ = request_.application_id();
  std::vector<int> pids(request_.process_ids().begin(),
                        request_.process_ids().end());
  process_ids_ = pids;
  ready_to_run_ = true;
}

void ComposeStatusCommand::Run(proto::InstallerResponse* response) {
  proto::ComposeStatusResponse* cs_response =
      response->mutable_compose_status_response();

  if (!PrepareInteraction(request_.arch())) {
    ErrEvent("Unable to prepare interaction");
    return;
  }

  auto listen_response = ListenForAgents();
  if (listen_response == nullptr) {
    cs_response->set_status(
        proto::ComposeStatusResponse::INSTALL_SERVER_COM_ERR);
    return;
  }

  if (listen_response->status() != proto::OpenAgentSocketResponse::OK) {
    cs_response->set_status(
        proto::ComposeStatusResponse::READY_FOR_AGENTS_NOT_RECEIVED);
    return;
  }

  if (!Attach(process_ids_)) {
    cs_response->set_status(proto::ComposeStatusResponse::AGENT_ATTACH_FAILED);
    return;
  }

  // Send compose status request to agents
  proto::SendAgentMessageRequest req;
  req.set_agent_count(process_ids_.size());
  *req.mutable_agent_request()->mutable_compose_status_request() = request_;
  auto resp = client_->SendAgentMessage(req);
  if (!resp) {
    cs_response->set_status(
        proto::ComposeStatusResponse::INSTALL_SERVER_COM_ERR);
    return;
  }

  // Success indicates communication status with the agent for this request.
  // It does not imply the last composition was error-free or not.
  bool success = true;

  // We currently pile up all the errors from all the processes of the
  // running application. In the future we might want to separate each of them.
  // Although it is highly unlikely that there are two process running Compose
  // UI for a single application.
  for (const auto& agent_response : resp->agent_responses()) {
    ConvertProtoEventsToEvents(agent_response.events());
    if (agent_response.status() == proto::AgentResponse::OK) {
      for (const auto& exception :
           agent_response.compose_status_response().exceptions()) {
        auto exp = cs_response->add_exceptions();
        *exp = exception;
      }
    } else {
      std::string error_message =
          agent_response.compose_status_response().error_message();
      if (!error_message.empty()) {
        cs_response->add_error_message(error_message);
      }
      success = false;
    }
  }

  if (success) {
    cs_response->set_status(proto::ComposeStatusResponse::OK);
  } else {
    cs_response->set_status(proto::ComposeStatusResponse::AGENT_ERROR);
  }
}

}  // namespace deploy
