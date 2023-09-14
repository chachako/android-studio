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
package com.android.adblib.ddmlibcompatibility.debugging

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.adbLogger
import com.android.adblib.scope
import com.android.adblib.tools.debugging.JdwpProcess
import com.android.adblib.tools.debugging.jdwpProcessFlow
import com.android.ddmlib.IDevice
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class JdwpTracker(private val trackerHost: ProcessTrackerHost) {

    private val session: AdbSession
        get() = trackerHost.device.session

    private val device: ConnectedDevice
        get() = trackerHost.device

    private val iDevice: IDevice
        get() = trackerHost.iDevice

    private val logger = adbLogger(session)

    fun startTracking() {
        device.scope.launch(session.host.ioDispatcher) {
            logger.debug { "Starting process tracking for device $iDevice" }
            val processEntryMap = mutableMapOf<Int, AdblibClientWrapper>()
            try {
                // Run the 'jdwp-track' service and collect PIDs
                device.jdwpProcessFlow
                    .collect { processList ->
                        updateJdwpProcessList(processEntryMap, processList)
                    }
            } finally {
                updateJdwpProcessList(processEntryMap, emptyList())
                logger.debug { "Stop process tracking for device $iDevice (scope.isActive=${device.scope.isActive})" }
            }
        }
    }

    /**
     * Update our list of processes and invoke listeners.
     */
    private suspend fun updateJdwpProcessList(
        currentProcessEntryMap: MutableMap<Int, AdblibClientWrapper>,
        newJdwpProcessList: List<JdwpProcess>
    ) {
        val knownPids = currentProcessEntryMap.keys.toHashSet()
        val effectivePids = newJdwpProcessList.map { it.pid }.toHashSet()
        val addedPids = effectivePids - knownPids
        val removePids = knownPids - effectivePids

        // Remove old pids
        removePids.forEach { pid ->
            logger.debug { "Removing PID $pid from list of Client processes" }
            currentProcessEntryMap.remove(pid)
        }

        // Add new pids
        addedPids.forEach { pid ->
            logger.debug { "Adding PID $pid to list of Client processes" }
            val jdwpProcess = newJdwpProcessList.first { it.pid == pid }
            val clientWrapper = AdblibClientWrapper(trackerHost, jdwpProcess)
            currentProcessEntryMap[pid] = clientWrapper
            clientWrapper.startTracking()
        }

        assert(currentProcessEntryMap.keys.size == newJdwpProcessList.size)
        trackerHost.clientsUpdated(currentProcessEntryMap.values.toList())
    }
}
