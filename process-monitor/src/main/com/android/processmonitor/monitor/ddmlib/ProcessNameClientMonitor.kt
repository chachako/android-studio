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
import com.android.adblib.DeviceSelector
import com.android.adblib.ShellCommandOutputElement.StdoutLine
import com.android.adblib.shellCommand
import com.android.adblib.withLineCollector
import com.android.adblib.withPrefix
import com.android.ddmlib.IDevice
import com.android.processmonitor.common.ProcessEvent.ProcessAdded
import com.android.processmonitor.common.ProcessEvent.ProcessRemoved
import com.android.processmonitor.monitor.ProcessNames
import com.android.processmonitor.utils.RetainingMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference

private const val DEVICE_PROCESSES_UPDATE_INTERVAL_MS = 2000L

/**
 * Monitors a device and keeps track of process names.
 *
 * Some process information is kept even after they terminate.
 *
 * @param device The [IDevice] to monitor
 * @param flows A flow where [ProcessNames] are sent to
 * @param adbSession An [AdbSession]
 * @param maxProcessRetention The maximum number of dead pids to retain in the cache
 * @param enablePsPolling If true, use `ps` command to poll device for processes
 */
internal class ProcessNameClientMonitor(
    parentScope: CoroutineScope,
    private val device: IDevice,
    private val flows: ProcessNameMonitorFlows,
    private val adbSession: AdbSession,
    private val logger: AdbLogger,
    private val maxProcessRetention: Int,
    enablePsPolling: Boolean = false,
) : Closeable {

    private val thisLogger =
        logger.withPrefix("${this::class.simpleName}: ${device.serialNumber}: ")

    /**
     * The map of pid -> [ProcessNames] for currently alive processes, plus recently terminated
     * processes.
     */
    private val processes = RetainingMap<Int, ProcessNames>(maxProcessRetention)
    private val deviceProcessUpdater = if (enablePsPolling) DeviceProcessUpdater() else null

    private val scope: CoroutineScope =
        CoroutineScope(parentScope.coroutineContext + SupervisorJob())

    fun start() {
        scope.launch {
            ClientProcessTracker(flows, device, logger).trackProcesses().collect {
                when (it) {
                    is ProcessAdded -> processes[it.pid] = it.toProcessNames()
                    is ProcessRemoved -> processes.remove(it.pid)
                }
                thisLogger.debug { it.toString() }
            }
        }
        if (deviceProcessUpdater != null) {
            scope.launch {
                while (true) {
                    deviceProcessUpdater.updateNow()
                    delay(DEVICE_PROCESSES_UPDATE_INTERVAL_MS)
                }
            }
        }
    }

    fun getProcessNames(pid: Int): ProcessNames? =
        processes[pid] ?: deviceProcessUpdater?.getPidName(pid)

    override fun close() {
        scope.cancel()
    }

    private inner class DeviceProcessUpdater {

        private val lastKnownPids = AtomicReference(mapOf<Int, ProcessNames>())

        suspend fun updateNow() {
            try {
                val names = mutableMapOf<Int, ProcessNames>()
                val deviceSelector = DeviceSelector.fromSerialNumber(device.serialNumber)
                val command = "ps -A -o PID,NAME"
                adbSession.deviceServices.shellCommand(deviceSelector, command)
                    .withLineCollector()
                    .execute()
                    .collect shellAsLines@{
                        //TODO: Check for `stderr` and `exitCode` to report errors
                        if (it is StdoutLine) {
                            val split = it.contents.trim().split(" ")
                            val pid = split[0].toIntOrNull() ?: return@shellAsLines
                            val processName = split[1]
                            names[pid] = ProcessNames("", processName)
                        }
                    }
                thisLogger.debug { "Adding ${names.size} processes from ps command" }
                lastKnownPids.set(names)
            } catch (e: Throwable) {
                thisLogger.warn(e, "Error listing device processes")
                // We have no idea what error to expect here and how long this may last, so safer to
                // discard old data.
                lastKnownPids.set(mapOf())
            }
        }

        fun getPidName(pid: Int): ProcessNames? = lastKnownPids.get()[pid]
    }
}