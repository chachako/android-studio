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
import com.android.processmonitor.common.ProcessEvent.ProcessAdded
import com.android.processmonitor.common.ProcessEvent.ProcessRemoved
import com.android.processmonitor.common.ProcessTracker
import com.android.processmonitor.monitor.ProcessNames
import com.android.processmonitor.utils.RetainingMap
import com.google.common.annotations.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.Closeable

/**
 * Monitors a device and keeps track of process names.
 *
 * Some process information is kept even after they terminate.
 *
 * @param parentScope a parent [CoroutineScope] to inherit from
 * @param device The [IDevice] to monitor
 * @param flows A flow where [ProcessNames] are sent to
 * @param processTracker An optional [ProcessTracker]
 * @param logger An [AdbLogger] to log with
 * @param maxProcessRetention The maximum number of dead pids to retain in the cache
 */
internal class ProcessNameClientMonitor(
    parentScope: CoroutineScope,
    private val device: IDevice,
    private val flows: ProcessNameMonitorFlows,
    @VisibleForTesting
    val processTracker: ProcessTracker?,
    private val logger: AdbLogger,
    private val maxProcessRetention: Int,
) : Closeable {

    private val thisLogger = logger.withPrefix("${this::class.simpleName}: ${device.serialNumber}: ")
    @VisibleForTesting
    val clientProcessNames = RetainingMap<Int, ProcessNames>(maxProcessRetention)
    @VisibleForTesting
    val trackerProcessNames = RetainingMap<Int, ProcessNames>(maxProcessRetention)

    private val scope: CoroutineScope =
        CoroutineScope(parentScope.coroutineContext + SupervisorJob())

    fun start() {
        scope.launch {
            ClientProcessTracker(flows, device, logger).trackProcesses().collect {
                when (it) {
                    is ProcessAdded -> clientProcessNames[it.pid] = it.toProcessNames()
                    is ProcessRemoved -> clientProcessNames.remove(it.pid)
                }
                thisLogger.debug { it.toString() }
            }
        }
        if (processTracker != null) {
            scope.launch {
                processTracker.trackProcesses().collect {
                    when (it) {
                        is ProcessRemoved -> trackerProcessNames.remove(it.pid)
                        is ProcessAdded -> {
                            val names = it.toProcessNames()
                            val pid = it.pid
                            thisLogger.debug { "Adding process $pid -> $names" }
                            trackerProcessNames[it.pid] = names
                        }
                    }
                }
            }
        }
    }

    fun getProcessNames(pid: Int): ProcessNames? =
        clientProcessNames[pid] ?: trackerProcessNames[pid]

    override fun close() {
        scope.cancel()
    }
}
