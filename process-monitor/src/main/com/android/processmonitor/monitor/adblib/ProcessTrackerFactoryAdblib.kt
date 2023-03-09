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
import com.android.adblib.AdbSession
import com.android.adblib.serialNumber
import com.android.processmonitor.agenttracker.AgentProcessTrackerConfig
import com.android.processmonitor.common.ProcessTracker
import com.android.processmonitor.monitor.BaseProcessTrackerFactory
import com.android.sdklib.deviceprovisioner.DeviceState

internal class ProcessTrackerFactoryAdblib(
    adbSession: AdbSession,
    agentConfig: AgentProcessTrackerConfig?,
    private val logger: AdbLogger,
) : BaseProcessTrackerFactory<DeviceState.Connected>(adbSession, agentConfig, logger) {

    override fun createMainTracker(device: DeviceState.Connected): ProcessTracker =
        JdwpProcessTracker(device.connectedDevice, logger)

    override suspend fun getDeviceApiLevel(device: DeviceState.Connected): Int =
        device.properties.androidVersion?.apiLevel ?: 1

    override suspend fun getDeviceAbi(device: DeviceState.Connected): String? =
        device.properties.abi?.toString()

    override fun getDeviceSerialNumber(device: DeviceState.Connected): String =
        device.connectedDevice.serialNumber
}
