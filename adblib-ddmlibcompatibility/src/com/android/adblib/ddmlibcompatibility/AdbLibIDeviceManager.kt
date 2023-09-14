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
import com.android.adblib.ConnectedDevice
import com.android.adblib.adbLogger
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.ddmlibcompatibility.debugging.AdbLibDeviceClientManager
import com.android.adblib.ddmlibcompatibility.debugging.AdblibIDeviceWrapper
import com.android.adblib.utils.createChildScope
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.idevicemanager.IDeviceManager
import com.android.ddmlib.idevicemanager.IDeviceManagerListener
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReference

internal class AdbLibIDeviceManager(
    private val session: AdbSession,
    private val bridge: AndroidDebugBridge,
    private val iDeviceManagerListener: IDeviceManagerListener
) : IDeviceManager {

    private val logger = adbLogger(session)

    private val scope = session.scope.createChildScope(isSupervisor = true)

    // Using `IdentityHashMap` as `connectedDevicesTracker.connectedDevices` guarantees
    // to return the same instance for the same device
    private val deviceMap = IdentityHashMap<ConnectedDevice, AdblibIDeviceWrapper>()

    private val deviceList = AtomicReference<List<IDevice>>(emptyList())
    private val ddmlibEventQueue =
        AdbLibDeviceClientManager.DdmlibEventQueue(logger, "DeviceUpdates")
    private var initialDeviceListDone = false

    init {

        session.scope.launch {
            ddmlibEventQueue.runDispatcher()
        }

        scope.launch {
            session.connectedDevicesTracker.connectedDevices.collect { value ->
                run {
                    // Process added devices
                    val added = value.filter { !deviceMap.containsKey(it) }
                    val addedIDevices = mutableListOf<IDevice>()
                    for (key in added) {
                        val iDevice = AdblibIDeviceWrapper(key, bridge)
                        deviceMap[key] = iDevice
                        addedIDevices.add(iDevice)
                    }

                    // Process removed devices
                    val removed = deviceMap.keys.filter { !value.contains(it)}
                    val removedIDevices = mutableListOf<IDevice>()
                    for (key in removed) {
                        deviceMap.remove(key)?.also { removedIDevices.add(it) }
                    }

                    deviceList.set(deviceMap.values.toList())
                    // flag the fact that we have build the list at least once
                    initialDeviceListDone = true

                    if (addedIDevices.isNotEmpty()) {
                        ddmlibEventQueue.post(scope, "devices added") {
                            iDeviceManagerListener.addedDevices(addedIDevices)
                        }
                    }

                    if (removedIDevices.isNotEmpty()) {
                        ddmlibEventQueue.post(scope, "devices removed") {
                            iDeviceManagerListener.removedDevices(removedIDevices)
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        scope.cancel("${this::class.simpleName} has been closed")
    }

    override fun getDevices(): MutableList<IDevice> {
        return deviceList.get().toMutableList()
    }

    override fun hasInitialDeviceList(): Boolean {
        return initialDeviceListDone
    }
}
