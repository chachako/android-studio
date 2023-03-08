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
import com.android.ddmlib.IDevice
import com.android.processmonitor.agenttracker.AgentProcessTrackerConfig
import com.android.processmonitor.common.ProcessTracker
import com.android.processmonitor.monitor.BaseProcessTrackerFactory
import com.android.processmonitor.monitor.ProcessTrackerFactory

/** A [ProcessTrackerFactory] for [ProcessNameMonitorDdmlib] */
internal class ProcessTrackerFactoryDdmlib(
    adbSession: AdbSession,
    private val adbAdapter: AdbAdapter,
    agentConfig: AgentProcessTrackerConfig?,
    private val logger: AdbLogger,
) : BaseProcessTrackerFactory<IDevice>(adbSession, agentConfig, logger) {

    override fun createMainTracker(device: IDevice): ProcessTracker =
        ClientProcessTracker(device, adbAdapter, logger)

    override suspend fun getDeviceApiLevel(device: IDevice): Int = device.version.apiLevel

    override suspend fun getDeviceAbi(device: IDevice): String? = device.abis.firstOrNull()

    override fun getDeviceSerialNumber(device: IDevice): String = device.serialNumber
}
