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
package com.android.adblib.ddmlibcompatibility.debugging

import com.android.adblib.ConnectedDevice
import com.android.adblib.CoroutineScopeCache.Key
import com.android.adblib.DeviceState
import com.android.adblib.adbLogger
import com.android.adblib.serialNumber
import com.android.adblib.withPrefix
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice

private object IDeviceKey : Key<IDevice>(IDevice::class.java.simpleName)

/**
 * For a [ConnectedDevice] find an equivalent [IDevice] as tracked by `AndroidDebugBridge.devices`.
 *
 * This method returns `null` if the device has been disconnected and therefore removed from
 * `AndroidDebugBridge.devices`.
 *
 * Care should be taken when using this method as there is a slight delay between
 * when a [ConnectedDevice] starts to be tracked by `adblib` and when `adblib-ddmlibcompatibility`
 * layer starts exposing it under [AndroidDebugBridge.getBridge().devices].
 */
fun ConnectedDevice.associatedIDevice(): IDevice? {
    if (this.deviceInfoFlow.value.deviceState == DeviceState.DISCONNECTED) {
        // Connected device is disconnected and should not be used
        adbLogger(session).withPrefix("device=$this - ")
            .warn("trying to lookup `IDevice` for a disconnected `ConnectedDevice`")
        return null
    }

    return try {
        this.cache.getOrPut(IDeviceKey) {
            val iDevice =
                AndroidDebugBridge.getBridge()?.devices?.firstOrNull { it.serialNumber == serialNumber }
            if (iDevice == null) {
                throw Exception("Failed to lookup IDevice by `serialNumber`")
            }
            iDevice
        }
    } catch (e: Throwable) {
        // Couldn't find an associated IDevice
        // Either it's not yet tracked by `AndroidDebugBridge` or a `ConnectedDevice` has been
        // disconnected and is no longer tracked by `AndroidDebugBridge`
        adbLogger(session).withPrefix("device=$this - ")
            .warn("failed to lookup `IDevice` using the `ConnectedDevice` `serialNumber")
        null
    }
}
