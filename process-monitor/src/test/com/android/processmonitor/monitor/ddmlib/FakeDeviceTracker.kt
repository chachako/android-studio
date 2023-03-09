/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.ddmlib.IDevice
import com.android.processmonitor.common.DeviceEvent
import com.android.processmonitor.common.DeviceTracker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import java.io.Closeable

/**
 * A test implementation of [DeviceTracker]
 */
internal class FakeDeviceTracker : DeviceTracker<IDevice>, Closeable {

    private val deviceEventsChannel = Channel<DeviceEvent<IDevice>>(10)

    suspend fun sendDeviceEvents(vararg events: DeviceEvent<IDevice>) {
        events.forEach {
            deviceEventsChannel.send(it)
        }
    }

    override fun trackDevices(): Flow<DeviceEvent<IDevice>> = deviceEventsChannel.consumeAsFlow()

    override fun close() {
        deviceEventsChannel.close()
    }
}
