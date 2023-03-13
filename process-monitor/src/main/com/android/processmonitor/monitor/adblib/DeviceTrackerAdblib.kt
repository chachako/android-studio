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
import com.android.adblib.serialNumber
import com.android.processmonitor.common.DeviceEvent
import com.android.processmonitor.common.DeviceEvent.DeviceDisconnected
import com.android.processmonitor.common.DeviceEvent.DeviceOnline
import com.android.processmonitor.common.DeviceTracker
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.mapStateNotNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

/**
 * A [DeviceTracker] for Adblib
 *
 * Uses [DeviceProvisioner.devices] flow to track devices.
 *
 */
internal class DeviceTrackerAdblib(
    private val deviceProvisioner: DeviceProvisioner,
    private val logger: AdbLogger,
) : DeviceTracker<DeviceState.Connected> {


    override fun trackDevices(): Flow<DeviceEvent<DeviceState.Connected>> {
        val currentDevices = mutableSetOf<String>()
        return deviceProvisioner.mapStateNotNull { _, state -> state.asOnline() }
            .transform { states ->
                val serialToState = states.associateBy { it.connectedDevice.serialNumber }
                val removed = currentDevices - serialToState.keys
                val added = serialToState.keys - currentDevices

                removed.forEach {
                    logger.debug { "DeviceDisconnected($it)" }
                    currentDevices.remove(it)
                    emit(DeviceDisconnected(it))
                }
                added.mapNotNull { serialToState[it] }.forEach {
                    currentDevices.add(it.connectedDevice.serialNumber)
                    logger.debug { "DeviceOnline(${it.connectedDevice.serialNumber})" }
                    emit(DeviceOnline(it))
                }
            }
    }

    override fun getDeviceSerialNumber(device: DeviceState.Connected): String =
        device.connectedDevice.serialNumber
}

private fun DeviceState.asOnline() = takeIf { it.isOnline() } as? DeviceState.Connected
