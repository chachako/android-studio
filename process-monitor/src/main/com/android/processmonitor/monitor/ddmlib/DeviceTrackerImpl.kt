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
import com.android.processmonitor.monitor.ddmlib.DeviceMonitorEvent.Online
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Production implementation of [DeviceTracker]
 *
 * Uses a [com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener] to track events.
 */
internal class DeviceTrackerImpl(
    private val adbAdapter: AdbAdapter,
    private val logger: AdbLogger,
    private val context: CoroutineContext = EmptyCoroutineContext,
) : DeviceTracker {

    override fun trackDevices(): Flow<DeviceMonitorEvent> = callbackFlow {
        val listener = DevicesMonitorListener(this, logger)
        adbAdapter.addDeviceChangeListener(listener)

        // Adding a listener does not fire events about existing devices, so we have to add them
        // manually.
        adbAdapter.getDevices().filter { it.isOnline }.forEach {
            trySendBlocking(Online(it))
                .onFailure { e -> logger.warn(e, "Failed to send a DeviceMonitorEvent") }
        }

        awaitClose {
            adbAdapter.removeDeviceChangeListener(listener)
        }
    }.flowOn(context)
}
