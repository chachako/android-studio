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
import com.android.adblib.withPrefix
import com.android.ddmlib.IDevice
import com.android.processmonitor.monitor.PerDeviceMonitor
import com.android.processmonitor.monitor.ProcessNameMonitor
import com.android.processmonitor.monitor.ProcessNames
import com.android.processmonitor.monitor.ProcessTrackerFactory
import com.android.processmonitor.monitor.ddmlib.DeviceMonitorEvent.Disconnected
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of ProcessNameMonitor
 */
class ProcessNameMonitorDdmlib @TestOnly internal constructor(
    parentScope: CoroutineScope,
    private val deviceTracker: DeviceTracker,
    private val processTrackerFactory: ProcessTrackerFactory<IDevice>,
    private val maxProcessRetention: Int,
    private val logger: AdbLogger,
) : ProcessNameMonitor, Closeable {

    constructor(
        parentScope: CoroutineScope,
        adbSession: AdbSession,
        adbAdapter: AdbAdapter,
        config: ProcessNameMonitor.Config,
        logger: AdbLogger,
    ) : this(
        parentScope,
        DeviceTrackerImpl(adbAdapter, logger, adbSession.ioDispatcher),
        ProcessTrackerFactoryDdmlib(adbSession, adbAdapter, config.agentConfig, logger),
        config.maxProcessRetention,
        logger,
    )

    private val scope: CoroutineScope =
        CoroutineScope(parentScope.coroutineContext + SupervisorJob())

    @Volatile
    private var isStarted = false

    // Connected devices.
    @VisibleForTesting
    internal val devices = ConcurrentHashMap<String, PerDeviceMonitor>()

    override fun start() {
        if (isStarted) {
            return
        }
        synchronized(this) {
            if (isStarted) {
                return
            }
            isStarted = true
        }
        scope.launch {
            deviceTracker.trackDevices().collect {
                when (it) {
                    is DeviceMonitorEvent.Online -> addDevice(it.device)
                    is Disconnected -> removeDevice(it.device)
                }
            }
        }
    }

    override fun getProcessNames(serialNumber: String, pid: Int): ProcessNames? {
        return devices[serialNumber]?.getProcessNames(pid)
    }

    override fun close() {
        scope.cancel()
    }

    private suspend fun addDevice(device: IDevice) {
        logger.info { "Adding ${device.serialNumber}" }
        val processTracker = processTrackerFactory.createProcessTracker(device)
        val logger = logger.withPrefix("PerDeviceMonitor: ${device.serialNumber}: ")
        devices[device.serialNumber] =
            PerDeviceMonitor(scope, logger, maxProcessRetention, processTracker).apply {
                start()
            }
    }

    private fun removeDevice(device: IDevice) {
        logger.info { ("Removing ${device.serialNumber}: ${System.identityHashCode(device)}") }
        val clientMonitor = devices.remove(device.serialNumber)
        clientMonitor?.close()
    }
}
