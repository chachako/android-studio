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
@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.android.processmonitor.monitor.ddmlib

import com.android.adblib.testing.FakeAdbLoggerFactory
import com.android.ddmlib.IDevice
import com.android.ddmlib.IDevice.CHANGE_STATE
import com.android.ddmlib.IDevice.DeviceState.DISCONNECTED
import com.android.ddmlib.IDevice.DeviceState.OFFLINE
import com.android.ddmlib.IDevice.DeviceState.ONLINE
import com.android.processmonitor.common.DeviceEvent.DeviceDisconnected
import com.android.processmonitor.common.DeviceEvent.DeviceOnline
import com.android.processmonitor.testutils.toChannel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests for [DeviceTrackerDdmlib]
 */
@Suppress("OPT_IN_IS_NOT_ENABLED")
@OptIn(ExperimentalCoroutinesApi::class) // runTest is experimental (replaced runTestTest)
class DeviceTrackerDdmlibTest {

    private val adbAdapter = FakeAdbAdapter()

    private val deviceTracker = DeviceTrackerDdmlib(adbAdapter, FakeAdbLoggerFactory().logger)

    @Test
    fun trackDevices_noInitialDevices(): Unit = runTest {
        deviceTracker.trackDevices().toChannel(this).use { channel ->
            advanceUntilIdle()

            val device = mockDevice("device1", ONLINE)
            adbAdapter.fireDeviceConnected(device)
            assertThat(channel.receive()).isEqualTo(DeviceOnline(device))

            device.setState(DISCONNECTED)
            adbAdapter.fireDeviceDisconnected(device)
            assertThat(channel.receive()).isEqualTo(DeviceDisconnected<IDevice>("device1"))

            device.setState(OFFLINE)
            adbAdapter.fireDeviceConnected(device)
            assertThat(channel.receiveOrNull()).named("Expected to timeout").isEqualTo(null)

            device.setState(ONLINE)
            adbAdapter.fireDeviceChange(device, CHANGE_STATE)
            assertThat(channel.receive()).isEqualTo(DeviceOnline(device))
        }
    }

    @Test
    fun trackDevices_withInitialDevices(): Unit = runTest {
        val device1 = mockDevice("device1", OFFLINE)
        val device2 = mockDevice("device2", ONLINE)
        adbAdapter.devices = listOf(
            device1,
            device2,
        )

        deviceTracker.trackDevices().toChannel(this).use { channel ->
            assertThat(channel.receive()).isEqualTo(DeviceOnline(device2))
        }

    }

    @Test
    fun trackDevices_initialOfflineDevice_becomesOnline(): Unit = runTest {
        val device1 = mockDevice("device1", OFFLINE)
        val device2 = mockDevice("device2", ONLINE)
        adbAdapter.devices = listOf(
            device1,
            device2,
        )

        deviceTracker.trackDevices().toChannel(this).use { channel ->
            assertThat(channel.receive()).isEqualTo(DeviceOnline(device2))

            device1.setState(ONLINE)
            adbAdapter.fireDeviceConnected(device1)
            assertThat(channel.receive()).isEqualTo(DeviceOnline(device1))
        }
    }

    @Test
    fun trackDevices_jobCanceled_unregisters(): Unit = runTest {
        val job = launch { deviceTracker.trackDevices().collect { } }
        advanceUntilIdle()
        assertThat(adbAdapter.deviceChangeListeners).isNotEmpty()

        job.cancel()

        advanceUntilIdle()
        assertThat(adbAdapter.deviceChangeListeners).isEmpty()
    }
}
