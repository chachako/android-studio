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
import com.android.adblib.tools.debugging.JdwpProcessChange
import com.android.adblib.tools.debugging.deviceDebuggableProcessesFlow
import com.android.adblib.withPrefix
import com.android.processmonitor.common.ProcessEvent
import com.android.processmonitor.common.ProcessEvent.ProcessAdded
import com.android.processmonitor.common.ProcessEvent.ProcessRemoved
import com.android.processmonitor.common.ProcessTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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

    override fun trackProcesses(): Flow<ProcessEvent> {
        return flow {
            // Keep track of PIDs for which we sent out a `ProcessAdded` event
            val sentProcessAddedEvents: MutableSet<Int> = mutableSetOf()

            device.deviceDebuggableProcessesFlow.collect { processChange ->
                val processProperties = processChange.processInfo.properties
                when (processChange) {
                    is JdwpProcessChange.Removed -> {
                        emit(ProcessRemoved(processProperties.pid))
                        // remove pid from `sentProcessAddedEvents` in case a process with
                        // the same id is created later on
                        sentProcessAddedEvents.remove(processProperties.pid)
                    }

                    // We want to emit `ProcessAdded` events only when the JDWP process name
                    // is known, so we process `Added` and `Updated` events the same way.
                    is JdwpProcessChange.Added, is JdwpProcessChange.Updated -> {
                        if (!sentProcessAddedEvents.contains(processProperties.pid)) {
                            if (processProperties.processName != null || processProperties.completed) {
                                val processName = processProperties.processName
                                if (processName == null) {
                                    logger.warn("Incomplete properties: $processProperties")
                                } else {
                                    val packageName = processProperties.packageName
                                    val event =
                                        ProcessAdded(
                                            processProperties.pid,
                                            packageName,
                                            processName
                                        )
                                    logger.verbose { "$event" }
                                    sentProcessAddedEvents.add(processProperties.pid)
                                    emit(event)
                                }
                            }
                        }
                    }
                }
            }
        }.flowOn(device.session.ioDispatcher + parentContext)
    }
}
