/*
 * Copyright (C) 2016 The Android Open Source Project
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
#ifndef PERFD_GENERIC_COMPONENT_H_
#define PERFD_GENERIC_COMPONENT_H_

#include <unordered_map>

#include "perfd/agent_service.h"
#include "perfd/daemon.h"
#include "perfd/profiler_component.h"
#include "perfd/profiler_service.h"
#include "perfd/sessions/sessions_manager.h"

namespace profiler {

using AgentStatusChanged = std::function<void(int processId)>;

class GenericComponent final : public ProfilerComponent {
 public:
  static constexpr int64_t kHeartbeatThresholdNs = Clock::ms_to_ns(500);

  explicit GenericComponent(Daemon* daemon, SessionsManager* sessions);

  // Returns the service that talks to desktop clients (e.g., Studio).
  grpc::Service* GetPublicService() override {
    return &generic_public_service_;
  }

  // Returns the service that talks to device clients (e.g., the agent).
  grpc::Service* GetInternalService() override { return &agent_service_; }

  void AddAgentStatusChangedCallback(AgentStatusChanged callback) {
    agent_status_changed_callbacks_.push_back(callback);
  }

 private:
  void RunAgentStatusThread();

  Daemon* daemon_;
  ProfilerServiceImpl generic_public_service_;
  AgentServiceImpl agent_service_;

  std::list<AgentStatusChanged> agent_status_changed_callbacks_;

  std::thread status_thread_;
};

}  // namespace profiler

#endif  // PERFD_GENERIC_COMPONENT_H_
