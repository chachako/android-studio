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
import com.android.adblib.withPrefix
import com.android.ddmlib.IDevice
import com.android.processmonitor.common.ProcessEvent
import com.android.processmonitor.common.ProcessEvent.ProcessAdded
import com.android.processmonitor.common.ProcessEvent.ProcessRemoved
import com.android.processmonitor.common.ProcessTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

/**
 * A [ProcessTracker] that tracks clients using a [ProcessNameMonitorFlows]
 *
 * TODO(aalbert): Refactor to not use ProcessNameMonitorFlows and instead, use
 * [ClientMonitorListener] directly.
 */
internal class ClientProcessTracker(
    private val flows: ProcessNameMonitorFlows,
    private val device: IDevice,
    logger: AdbLogger,
) : ProcessTracker {

    private val logger = logger.withPrefix("${this::class.simpleName}: ${device.serialNumber}: ")

    override suspend fun trackProcesses(): Flow<ProcessEvent> {
        return flows.trackClients(device).transform {
            val removed = it.removedProcesses.map { pid ->
                ProcessRemoved(pid)
            }
            val added = it.addedProcesses.map { (pid, names) ->
                ProcessAdded(pid, names.applicationId, names.processName)
            }
            (removed + added).forEach { event ->
                logger.debug { event.toString() }
                emit(event)
            }
        }
    }
}
