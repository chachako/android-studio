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
import com.android.adblib.tools.debugging.AppProcess
import com.android.adblib.tools.debugging.appProcessFlow
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.ddmlib.ProfileableClient
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class AppProcessTracker(private val trackerHost: ProcessTrackerHost) {

    private val session: AdbSession
        get() = trackerHost.device.session

    private val device: ConnectedDevice
        get() = trackerHost.device

    private val iDevice: IDevice
        get() = trackerHost.iDevice

    private val logger = adbLogger(session)

    fun startTracking() {
        device.scope.launch(session.host.ioDispatcher) {
            logger.debug { "Starting app process tracking for device $iDevice" }
            val processEntryMap = mutableMapOf<Int, AdblibProfileableClientWrapper>()
            try {
                // Run the 'track-app' service and collect processes info
                device.appProcessFlow
                    .collect { appProcessList ->
                        updateAppProcessList(processEntryMap, appProcessList)
                    }
            } finally {
                updateAppProcessList(processEntryMap, emptyList())
                logger.debug { "Stop process tracking for device $iDevice (scope.isActive=${device.scope.isActive})" }
            }
        }
    }

    /**
     * Update our list of processes and invoke listeners.
     */
    private suspend fun updateAppProcessList(
        currentProcessEntryMap: MutableMap<Int, AdblibProfileableClientWrapper>,
        newAppProcessList: List<AppProcess>
    ) {
        logger.debug {
            "Updating list of app processes: " +
                    "before=${currentProcessEntryMap.size}, after=${newAppProcessList.size}"
        }
        val knownPids = currentProcessEntryMap.keys.toHashSet()
        val effectivePids = newAppProcessList.map { it.pid }.toHashSet()
        val addedPids = effectivePids - knownPids
        val removedPids = knownPids - effectivePids
        val changeTracker = ChangeTracker()

        // Remove old pids
        removedPids.forEach { pid ->
            logger.verbose { "Removing PID $pid from list of app processes" }
            val profileableClientWrapper = currentProcessEntryMap.remove(pid)
            assert(profileableClientWrapper != null)
            profileableClientWrapper?.also { changeTracker.onChange(it) }
        }

        // Add new pids
        addedPids.forEach { pid ->
            logger.verbose { "Adding PID $pid to list of app processes" }
            val appProcess = newAppProcessList.first { it.pid == pid }
            val profileableClientWrapper = AdblibProfileableClientWrapper(trackerHost, appProcess)
            currentProcessEntryMap[pid] = profileableClientWrapper
            profileableClientWrapper.startTracking()
            changeTracker.onChange(profileableClientWrapper)
        }

        assert(currentProcessEntryMap.keys.size == newAppProcessList.size)

        if (changeTracker.clientsChanged) {
            val clients = currentProcessEntryMap.values
                .mapNotNull { it.exportAsClient() }
                .toList()
            logger.verbose { "Updated 'Client' list: pids=${clients.map { it.clientData.pid }}" }
            trackerHost.clientsUpdated(clients)
        }

        if (changeTracker.profileableClientsChanged) {
            val profileableClients = currentProcessEntryMap.values
                .mapNotNull { it.exportAsProfileableClient() }
                .toList()
            logger.verbose { "Updated 'ProfileableClient' list: pids=${profileableClients.map { it.profileableClientData.pid }}" }
            trackerHost.profileableClientsUpdated(profileableClients)
        }
    }

    private class ChangeTracker(
        var clientsChanged: Boolean = false,
        var profileableClientsChanged: Boolean = false
    )

    private fun ChangeTracker.onChange(profileableClientWrapper: AdblibProfileableClientWrapper) {
        clientsChanged = clientsChanged or
                (profileableClientWrapper.exportAsClient() != null)

        profileableClientsChanged = profileableClientsChanged or
                (profileableClientWrapper.exportAsProfileableClient() != null)
    }

    /**
     * Returns the [Client] for this [AdblibProfileableClientWrapper] if it should
     * be exported as a [Client] instance to the ddmlib APIs, `null` otherwise.
     */
    private fun AdblibProfileableClientWrapper.exportAsClient(): Client? {
        return clientWrapper
    }

    /**
     * Returns the [ProfileableClient] for this [AdblibProfileableClientWrapper] if it should
     * be exported as a [ProfileableClient] instance to the ddmlib APIs, `null` otherwise.
     */
    private fun AdblibProfileableClientWrapper.exportAsProfileableClient(): ProfileableClient? {
        return if (debuggable || profileable) {
            this
        } else {
            null
        }
    }

}
