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
package com.android.adblib.ddmlibcompatibility

import com.android.adblib.AdbSession
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.utils.createChildScope
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.idevicemanager.IDeviceManager
import kotlinx.coroutines.cancel

internal class AdbLibIDeviceManager(
    private val session: AdbSession,
    private val bridge: AndroidDebugBridge
) : IDeviceManager {

    private val scope = session.scope.createChildScope(isSupervisor = true)

    override fun close() {
        scope.cancel("${this::class.simpleName} has been closed")
    }

    override fun getDevices(): MutableList<IDevice> {
        return session.connectedDevicesTracker.connectedDevices.value.map {
            TODO("Not yet implemented")
        }.toMutableList()
    }
}
