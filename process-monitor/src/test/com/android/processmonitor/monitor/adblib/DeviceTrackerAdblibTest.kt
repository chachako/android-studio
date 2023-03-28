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

import com.android.adblib.DeviceSelector
import com.android.adblib.testing.FakeAdbLoggerFactory
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.ddmlib.TimeoutException
import com.android.fakeadbserver.DeviceState.DeviceStatus
import com.android.fakeadbserver.DeviceState.DeviceStatus.ONLINE
import com.android.fakeadbserver.DeviceState.HostConnectionType.USB
import com.android.processmonitor.common.DeviceEvent.DeviceDisconnected
import com.android.processmonitor.common.DeviceEvent.DeviceOnline
import com.android.processmonitor.testutils.toChannel
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.testing.DeviceProvisionerRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Rule
import org.junit.Test
import java.time.Duration

/**
 * Tests for [com.android.processmonitor.monitor.adblib.DeviceTrackerAdblib]
 */
class DeviceTrackerAdblibTest {

    @get:Rule
    val deviceProvisionerRule = DeviceProvisionerRule()

    private val fakeAdb get() = deviceProvisionerRule.fakeAdb
    private val deviceProvisioner get() = deviceProvisionerRule.deviceProvisioner

    private val logger = FakeAdbLoggerFactory().logger

    @Test
    fun trackDevices_preexistingDevices(): Unit = runBlockingWithTimeout(Duration.ofSeconds(5)) {
        fakeAdb.connectDevice("device1", ONLINE)
        fakeAdb.connectDevice("device2", ONLINE)
        val connectedDevice1 = deviceProvisioner.waitForDevice("device1")
        val connectedDevice2 = deviceProvisioner.waitForDevice("device2")
        val tracker = DeviceTrackerAdblib(deviceProvisioner, logger)

        assertThat(tracker.trackDevices().take(2).toList()).containsExactly(
            DeviceOnline(connectedDevice1),
            DeviceOnline(connectedDevice2),
        )
    }

    @Test
    fun trackDevices_noPreexistingDevices(): Unit = runBlockingWithTimeout(Duration.ofSeconds(5)) {
        val tracker = DeviceTrackerAdblib(deviceProvisioner, logger)

        tracker.trackDevices().toChannel(this).use { channel ->
            assertThat(channel.receiveOrNull()).isNull()

            fakeAdb.connectDevice("device1", ONLINE)
            val connectedDevice = deviceProvisioner.waitForDevice("device1")
            assertThat(channel.receive()).isEqualTo(DeviceOnline(connectedDevice))
        }
    }

    @Test
    fun trackDevices_disconnects(): Unit = runBlockingWithTimeout(Duration.ofSeconds(5)) {
        fakeAdb.connectDevice("device1", ONLINE)
        fakeAdb.connectDevice("device2", ONLINE)
        val connectedDevice1 = deviceProvisioner.waitForDevice("device1")
        val connectedDevice2 = deviceProvisioner.waitForDevice("device2")
        val tracker = DeviceTrackerAdblib(deviceProvisioner, logger)

        tracker.trackDevices().toChannel(this).use { channel ->
            assertThat(channel.take(2)).containsExactly(
                DeviceOnline(connectedDevice1),
                DeviceOnline(connectedDevice2),
            )

            fakeAdb.disconnectDevice("device1")
            fakeAdb.disconnectDevice("device2")

            assertThat(channel.take(2)).containsExactly(
                DeviceDisconnected<DeviceState.Connected>("device1"),
                DeviceDisconnected<DeviceState.Connected>("device2"),
            )

            fakeAdb.connectDevice("device1", ONLINE)
            val reconnectedDevice1 = deviceProvisioner.waitForDevice("device1")
            assertThat(channel.receive()).isEqualTo(DeviceOnline(reconnectedDevice1))
        }
    }

    private fun FakeAdbServerProvider.connectDevice(
        serialNumber: String,
        status: DeviceStatus = ONLINE
    ) {
        connectDevice(serialNumber, "", "", "13", "33", USB).apply {
            deviceStatus = status
        }
    }

    private suspend fun DeviceProvisioner.waitForDevice(serialNumber: String): DeviceState.Connected {
        val handle = findConnectedDeviceHandle(DeviceSelector.fromSerialNumber(serialNumber))
        return withTimeoutOrNull(5_000) {
            handle?.stateFlow?.first { it.isOnline() } as DeviceState.Connected
        } ?: throw TimeoutException("waitForDevice")
    }
}
