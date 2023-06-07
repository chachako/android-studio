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

import com.android.adblib.testing.FakeAdbLoggerFactory
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.ddmlib.IDevice
import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.ClientState
import com.android.fakeadbserver.DeviceState
import com.android.processmonitor.common.ProcessEvent.ProcessAdded
import com.android.processmonitor.common.ProcessEvent.ProcessRemoved
import com.android.processmonitor.monitor.SharedProcessTracker
import com.android.processmonitor.testutils.toChannel
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Duration

/**
 * Tests for [com.android.processmonitor.monitor.ddmlib.ClientProcessTracker]
 */
@Suppress("OPT_IN_IS_NOT_ENABLED")
@OptIn(ExperimentalCoroutinesApi::class) // runTest is experimental (replaced runTestTest)
class ClientProcessTrackerTest {

    @get:Rule
    val adbRule = FakeAdbRule()

    private val logger = FakeAdbLoggerFactory().logger
    private val adbAdapter get() = AdbAdapterImpl(Futures.immediateFuture(adbRule.bridge))

    @Test
    fun initialClients() = runTest {
        val device = adbRule.setupDevice("device1")
        device.startClient(pid = 101, "package1", "process1")
        device.startClient(pid = 102, "package2", "process2")
        val iDevice = device.iDevice()
        runBlocking { yieldUntil(Duration.ofSeconds(1)) { iDevice.clients.count() == 2 } }

        val tracker = ClientProcessTracker(iDevice, adbAdapter, logger)

        tracker.trackProcesses().toChannel(this).use { channel ->
            assertThat(channel.take(2)).containsExactly(
                ProcessAdded(101, "package1", "process1"),
                ProcessAdded(102, "package2", "process2"),
            )
        }
    }

    @Test
    fun clientListChanged(): Unit = runTest {
        val device = adbRule.setupDevice("device1")
        val tracker = ClientProcessTracker(device.iDevice(), adbAdapter, logger)

        tracker.trackProcesses().toChannel(this).use { channel ->
            device.startClient(pid = 101, "package1", "process1")
            assertThat(channel.receive()).isEqualTo(ProcessAdded(101, "package1", "process1"))

            device.stopClient(101)
            device.startClient(pid = 102, "package2", "process2")
            device.startClient(pid = 103, "package3", "process3")
            assertThat(channel.take(3)).containsExactly(
                ProcessRemoved(101),
                ProcessAdded(102, "package2", "process2"),
                ProcessAdded(103, "package3", "process3"),
            )

            device.stopClient(102)
            assertThat(channel.receive()).isEqualTo(ProcessRemoved(102))
        }
    }

    @Test
    fun clientListChanged_otherDevice(): Unit = runTest {
        val device1 = adbRule.setupDevice("device1")
        val device2 = adbRule.setupDevice("device2")
        val tracker = ClientProcessTracker(device1.iDevice(), adbAdapter, logger)

        tracker.trackProcesses().toChannel(this).use { channel ->
            device1.startClient(pid = 101, "package1", "process1")
            assertThat(channel.receive()).isEqualTo(ProcessAdded(101, "package1", "process1"))

            device2.startClient(pid = 101, "package1", "process1")
            assertThat(channel.receiveOrNull()).named("Expected to time out").isNull()
        }
    }

    @Test
    fun trackAsSharedFlow_deviceDisconnects_unregistersListener(): Unit = runTest {
        val fakeAdbAdapter = FakeAdbAdapter()
        val device = mockDevice("device")
        val tracker = ClientProcessTracker(device, fakeAdbAdapter, logger)
        val sharedTracker = SharedProcessTracker(this, tracker)

        val job = launch { sharedTracker.trackProcesses().collect() }
        advanceUntilIdle()
        fakeAdbAdapter.fireDeviceDisconnected(device)
        advanceUntilIdle()

        assertThat(fakeAdbAdapter.deviceChangeListeners).isEmpty()
        job.cancel()
    }

    private fun DeviceState.iDevice(): IDevice =
        adbRule.bridge.devices.first { it.serialNumber == this.deviceId }
}

private fun FakeAdbRule.setupDevice(serialNumber: String): DeviceState {
    return attachDevice(serialNumber, "", "", "13", "33")
}

private fun DeviceState.startClient(
    pid: Int,
    packageName: String,
    processName: String
): ClientState {
    return startClient(pid, 0, processName, packageName, false)
}
