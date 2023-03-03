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
package com.android.processmonitor.monitor.adblib

import com.android.adblib.AdbLogger
import com.android.adblib.ConnectedDevice
import com.android.adblib.deviceInfo
import com.android.adblib.tools.debugging.AppProcess
import com.android.adblib.tools.debugging.appProcessFlow
import com.android.adblib.tools.debugging.isAppProcessTrackerSupported
import com.android.adblib.tools.debugging.jdwpProcessFlow
import com.android.adblib.withPrefix
import com.android.processmonitor.common.ProcessEvent
import com.android.processmonitor.common.ProcessEvent.ProcessAdded
import com.android.processmonitor.common.ProcessEvent.ProcessRemoved
import com.android.processmonitor.common.ProcessTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** A [ProcessTracker] that uses JdwpProcessTracker */
internal class JdwpProcessTracker(
    private val device: ConnectedDevice,
    logger: AdbLogger,
    private val parentContext: CoroutineContext = EmptyCoroutineContext,
) : ProcessTracker {

    private val logger =
        logger.withPrefix("JdwpProcessTracker: ${device.deviceInfo.serialNumber}: ")

    override suspend fun trackProcesses(): Flow<ProcessEvent> {
        return channelFlow {
            val currentPids: MutableSet<Int> = mutableSetOf()

            val flow = when (device.isAppProcessTrackerSupported()) {
                true -> device.appProcessFlow.asJdwpProcessFlow()
                false -> device.jdwpProcessFlow
            }
            flow.collect { processes ->
                val currentProcesses = processes.associateBy { it.pid }
                val removed = currentPids - currentProcesses.keys
                val added = currentProcesses.keys - currentPids

                removed.forEach { pid ->
                    currentPids.remove(pid)
                    val event = ProcessRemoved(pid)
                    logger.verbose { "$event" }
                    send(event)
                }

                added.forEach { pid ->
                    val process = currentProcesses[pid] ?: return@forEach
                    currentPids.add(pid)
                    process.scope.launch {
                        logger.verbose { "Waiting for properties of $pid" }
                        val properties = process.propertiesFlow.first {
                            it.processName != null || it.completed
                        }
                        logger.verbose { "Properties of $pid: $properties" }

                        val processName = properties.processName
                        if (processName == null) {
                            logger.warn("Incomplete properties: $properties")
                        } else {
                            val packageName = properties.packageName
                            val event = ProcessAdded(pid, packageName, processName)
                            logger.verbose { "$event" }
                            send(event)
                        }
                    }
                }
            }
        }.flowOn(device.session.ioDispatcher + parentContext)
    }
}

private fun Flow<List<AppProcess>>.asJdwpProcessFlow() =
    transform { emit(it.mapNotNull { process -> process.jdwpProcess }) }
