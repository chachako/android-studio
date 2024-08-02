/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.adblib.tools.debugging

import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceState
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

/**
 * This flow can be used to keep track of debuggable processes.
 *
 * The [Flow] starts when the device becomes [DeviceState.ONLINE] and ends
 * when the device scope is disconnected. This flow keeps the caller informed about
 * the lifecycle of debuggable processes, notifying it when they start, stop, or
 * have their properties modified.
 *
 * See [JdwpProcessChange] for more info.
 */
val ConnectedDevice.deviceDebuggableProcessesFlow: Flow<JdwpProcessChange>
    get() = channelFlow {
        val device = this@deviceDebuggableProcessesFlow
        var currentProcesses = mapOf<Int, JdwpProcess>()
        val currentProcessTrackingJobs: MutableMap<Int, Job> = mutableMapOf()

        waitForDeviceOnline(device)
        val flow = when (device.isTrackAppSupported()) {
            true -> device.appProcessFlow.asJdwpProcessFlow()
            false -> device.jdwpProcessFlow
        }
        flow.collect { processes ->
            val processesById = processes.associateBy { it.pid }
            val removedPids = currentProcesses.keys - processesById.keys
            val addedPids = processesById.keys - currentProcesses.keys

            // Send [ProcessChange] for removed processes
            removedPids.forEach { pid ->
                currentProcessTrackingJobs.remove(pid)?.also { propertiesJob ->
                    propertiesJob.cancel("Cancelling process tracking job [pid=$pid]")
                    propertiesJob.join()
                }
                send(JdwpProcessChange.Removed(currentProcesses[pid]!!.toJdwpProcessInfo()))
            }

            addedPids.forEach { pid ->
                val addedProcess = processesById[pid]!!
                val addedProcessInfo = addedProcess.toJdwpProcessInfo()
                // Send [ProcessChange] for added process.
                send(JdwpProcessChange.Added(addedProcessInfo))

                // Keep track of process properties updates
                val job = addedProcess.scope.launch {
                    addedProcess.propertiesFlow
                        .collectIndexed { index, newProperties ->
                            val updatedProcessInfo =
                                JdwpProcessInfo(addedProcess.device, newProperties)
                            // Skip the first update if we just sent it out as part of `Added` update
                            if (index != 0 || updatedProcessInfo != addedProcessInfo) {
                                // Send [ProcessChange] for updated process.
                                send(JdwpProcessChange.Updated(updatedProcessInfo))
                            }
                        }
                }
                currentProcessTrackingJobs[pid] = job
            }

            currentProcesses = processesById
        }
    }

private suspend fun waitForDeviceOnline(connectedDevice: ConnectedDevice) {
    connectedDevice.deviceInfoFlow.first { deviceInfo ->
        deviceInfo.deviceState == com.android.adblib.DeviceState.ONLINE
    }
}

private fun Flow<List<AppProcess>>.asJdwpProcessFlow() =
    transform { emit(it.mapNotNull { process -> process.jdwpProcess }) }

/**
 * Represents a change in debuggable processes on a [ConnectedDevice]
 */
sealed class JdwpProcessChange(val processInfo: JdwpProcessInfo) {

    /**
     * Debuggable process added since last flow emit
     */
    class Added(processInfo: JdwpProcessInfo) : JdwpProcessChange(processInfo)

    /**
     * Debuggable process removed since last flow emit
     */
    class Removed(processInfo: JdwpProcessInfo) : JdwpProcessChange(processInfo)

    /**
     * Debuggable process for which the process properties have changed
     */
    class Updated(processInfo: JdwpProcessInfo) : JdwpProcessChange(processInfo)
}
