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
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.processmonitor.common.ProcessEvent
import com.android.processmonitor.common.ProcessEvent.ProcessAdded
import com.android.processmonitor.common.ProcessEvent.ProcessRemoved
import com.android.processmonitor.common.ProcessTracker
import com.android.processmonitor.monitor.ddmlib.ClientMonitorListener.ClientEvent.ClientChanged
import com.android.processmonitor.monitor.ddmlib.ClientMonitorListener.ClientEvent.ClientListChanged
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.transform

/**
 * A [ProcessTracker] that tracks clients Ddmlib [Client]s
 */
internal class ClientProcessTracker(
    private val device: IDevice,
    private val adbAdapter: AdbAdapter,
    logger: AdbLogger
) : ProcessTracker {

    private val logger = logger.withPrefix("${this::class.simpleName}: ${device.serialNumber}: ")

    override suspend fun trackProcesses(): Flow<ProcessEvent> {
        val clients = mutableMapOf<Int, Client>()

        return callbackFlow {
            val listener = ClientMonitorListener(device, this, logger)
            adbAdapter.addDeviceChangeListener(listener)
            adbAdapter.addClientChangeListener(listener)

            // Adding a listener does not fire events about existing clients, so we have to add them manually.
            trySendBlocking(ClientListChanged(device.clients))
                .onFailure { logger.warn(it, "Failed to send a ClientEvent") }

            awaitClose {
                adbAdapter.removeDeviceChangeListener(listener)
                adbAdapter.removeClientChangeListener(listener)
            }
        }.transform { clientEvent ->
            when (clientEvent) {
                is ClientListChanged -> handleClientListChanged(clientEvent, clients)
                is ClientChanged -> handleClientChanged(clientEvent, clients)
            }
        }
    }

    private suspend fun FlowCollector<ProcessEvent>.handleClientListChanged(
        clientEvent: ClientListChanged,
        clientMap: MutableMap<Int, Client>
    ) {
        val newClients = clientEvent.clients.associateBy { it.pid() }
        val addedClients = newClients.keys - clientMap.keys
        val removedClients = clientMap.keys - newClients.keys
        removedClients.forEach {
            clientMap.remove(it)
            val event = ProcessRemoved(it)
            logger.verbose { event.toString() }
            emit(event)
        }

        addedClients.forEach {
            val client = newClients[it] ?: return@forEach
            if (client.isInitialized()) {
                clientMap[it] = client
                val event = client.toProcessAddedEvent()
                logger.verbose { event.toString() }
                emit(event)
            } else {
                logger.verbose { "Skipping uninitialized client $it" }
            }
        }
    }

    private suspend fun FlowCollector<ProcessEvent>.handleClientChanged(
        clientEvent: ClientChanged,
        clients: MutableMap<Int, Client>
    ) {
        val client = clientEvent.client
        if (!clients.contains(client.pid()) && client.isInitialized()) {
            clients[client.pid()] = client
            val event = client.toProcessAddedEvent()
            logger.verbose { "Adding initialized $event" }
            emit(event)
        }
    }
}

private fun Client.pid(): Int = clientData.pid
private fun Client.processName(): String = clientData.clientDescription ?: ""
private fun Client.packageName(): String = clientData.packageName ?: ""
private fun Client.isInitialized(): Boolean = clientData.packageName != null
private fun Client.toProcessAddedEvent(): ProcessAdded =
    ProcessAdded(pid(), packageName(), processName())
