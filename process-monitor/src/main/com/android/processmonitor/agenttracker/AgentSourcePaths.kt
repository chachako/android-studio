/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.processmonitor.agenttracker

/** Exposes the locations of the agent source */
object AgentSourcePaths {

    /**
     * Location of the agent binary in a development environment.
     *
     * See //tools/base/process-monitor/process-tracker-agent/BUILD
     */
    const val AGENT_SOURCE_DEV = "tools/base/process-monitor/process-tracker-agent"

    /**
     * Location of the agent binary in production.
     *
     * See //tools/base/process-monitor/process-tracker-agent/BUILD
     */
    const val AGENT_RESOURCE_PROD = "resources/process-tracker-agent"
}
