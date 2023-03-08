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
package com.android.processmonitor.monitor.ddmlib

import com.android.adblib.AdbLogger
import com.android.adblib.AdbSession
import com.android.ddmlib.IDevice
import com.android.processmonitor.agenttracker.AgentProcessTracker
import com.android.processmonitor.common.ProcessTracker
import com.android.processmonitor.monitor.MergedProcessTracker
import com.android.processmonitor.monitor.ProcessTrackerFactory
import java.nio.file.Path

/** A [ProcessTrackerFactory] for [ProcessNameMonitorDdmlib] */
internal class ProcessTrackerFactoryDdmlib(
    private val adbSession: AdbSession,
    private val adbAdapter: AdbAdapter,
    private val trackerAgentPath: Path?,
    private val trackerAgentInterval: Int,
    private val logger: AdbLogger,
) : ProcessTrackerFactory<IDevice> {

    override fun createProcessTracker(device: IDevice): ProcessTracker {
        val agentTracker = createAgentProcessTracker(device)
        val clientTracker = ClientProcessTracker(device, adbAdapter, logger)
        return when (agentTracker) {
            null -> clientTracker
            else -> MergedProcessTracker(clientTracker, agentTracker)
        }
    }

    private fun createAgentProcessTracker(device: IDevice): AgentProcessTracker? {
        // TODO(b/272009795): Investigate further
        if (device.version.apiLevel < 21 || trackerAgentPath == null) {
            return null
        }
        val serialNumber = device.serialNumber
        val abi = device.abis.first()
        return AgentProcessTracker(
            adbSession, serialNumber, abi, trackerAgentPath, trackerAgentInterval, logger
        )
    }
}
