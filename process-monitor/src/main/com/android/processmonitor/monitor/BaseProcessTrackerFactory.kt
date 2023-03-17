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
package com.android.processmonitor.monitor

import com.android.adblib.AdbLogger
import com.android.adblib.AdbSession
import com.android.processmonitor.agenttracker.AgentProcessTracker
import com.android.processmonitor.agenttracker.AgentProcessTrackerConfig
import com.android.processmonitor.common.ProcessTracker

/**
 * A [ProcessTrackerFactory] creates a tracker that is optionally merged with an [AgentProcessTracker]
 */
internal abstract class BaseProcessTrackerFactory<T>(
    private val adbSession: AdbSession,
    private val agentConfig: AgentProcessTrackerConfig?,
    private val logger: AdbLogger,
) : ProcessTrackerFactory<T> {

    override suspend fun createProcessTracker(device: T): ProcessTracker {
        val agentTracker = createAgentProcessTracker(device)
        val mainTracker = createMainTracker(device)
        return when (agentTracker) {
            null -> mainTracker
            else -> MergedProcessTracker(mainTracker, agentTracker)
        }
    }

    abstract fun createMainTracker(device: T): ProcessTracker

    abstract suspend fun getDeviceApiLevel(device: T): Int

    abstract suspend fun getDeviceAbi(device: T): String?

    abstract fun getDeviceSerialNumber(device: T): String

    private suspend fun createAgentProcessTracker(device: T): ProcessTracker? {
        // The agent is a native executable, and we don't have the ability build it for API<21
        if (getDeviceApiLevel(device) < 21 || agentConfig == null) {
            return null
        }
        val serialNumber = getDeviceSerialNumber(device)
        val abi = getDeviceAbi(device) ?: return null
        val agentProcessTracker = AgentProcessTracker(
            adbSession,
            serialNumber,
            abi,
            agentConfig.sourcePath,
            agentConfig.pollingIntervalMillis,
            logger,
        )

        // Don't let failures in the agent tracker to fail the main tracker
        return SafeProcessTracker(agentProcessTracker, "Agent tracker error", logger)
    }
}
