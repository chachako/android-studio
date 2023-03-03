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

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.device
import com.android.adblib.serialNumber
import com.android.adblib.testing.FakeAdbLoggerFactory
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.testingutils.TestingAdbSessionHost
import com.android.fakeadbserver.DeviceState.DeviceStatus.ONLINE
import com.android.fakeadbserver.DeviceState.HostConnectionType.USB
import com.android.processmonitor.common.ProcessEvent.ProcessAdded
import com.android.processmonitor.common.ProcessEvent.ProcessRemoved
import com.android.processmonitor.testutils.toChannel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [JdwpProcessTracker]
 */
@Suppress("OPT_IN_IS_NOT_ENABLED")
@OptIn(ExperimentalCoroutinesApi::class) // runTest is experimental (replaced runTestTest)
class JdwpProcessTrackerTest {

    @get:Rule
    val closeables = CloseablesRule()

    private val fakeAdb = closeables.register(
        FakeAdbServerProvider()
            .buildDefault()
            .start()
    )
    private val adbHost = closeables.register(TestingAdbSessionHost())
    private val adbSession = closeables.register(
        AdbSession.create(adbHost, fakeAdb.createChannelProvider(adbHost))
    )
    private val logger = FakeAdbLoggerFactory().logger

    @Test
    fun trackProcesses_addProcesses_appProcessFlow(): Unit = runBlocking {
        val device = setupDevice("device1", 33)
        val connectedDevice = adbSession.waitForDevice("device1")
        val tracker = JdwpProcessTracker(connectedDevice, logger)

        device.startClient(101, 1, "processName1", "packageName1", false)
        device.startClient(102, 1, "processName2", "packageName2", false)
        device.startClient(103, 1, "processName3", "packageName3", false)
        val events = tracker.trackProcesses().take(3).toList()

        assertThat(events).containsExactly(
            ProcessAdded(pid = 101, "packageName1", "processName1"),
            ProcessAdded(pid = 102, "packageName2", "processName2"),
            ProcessAdded(pid = 103, "packageName3", "processName3"),
        )
    }

    @Test
    fun trackProcesses_addAndThenRemove_appProcessFlow() = runTest(dispatchTimeoutMs = 5_000) {
        val device = setupDevice("device2", 33)
        val connectedDevice = adbSession.waitForDevice("device2")
        val tracker = JdwpProcessTracker(connectedDevice, logger)

        tracker.trackProcesses().toChannel(this).use { channel ->
            device.startClient(101, 1, "processName1", "packageName1", false)
            device.startClient(102, 1, "processName2", "packageName2", false)
            val addedEvents = channel.take(2)
            device.stopClient(101)
            device.stopClient(102)
            val removedEvents = channel.take(2)

            assertThat(addedEvents).containsExactly(
                ProcessAdded(pid = 101, "packageName1", "processName1"),
                ProcessAdded(pid = 102, "packageName2", "processName2"),
            )
            assertThat(removedEvents).containsExactly(
                ProcessRemoved(pid = 102),
                ProcessRemoved(pid = 101),
            )
        }
    }

    @Test
    fun trackProcesses_addProcesses_jdwpProcessFlow(): Unit = runBlocking {
        val device = setupDevice("device1", 30)
        val connectedDevice = adbSession.waitForDevice("device1")
        val tracker = JdwpProcessTracker(connectedDevice, logger)

        device.startClient(101, 1, "processName1", "packageName1", false)
        device.startClient(102, 1, "processName2", "packageName2", false)
        device.startClient(103, 1, "processName3", "packageName3", false)
        val events = tracker.trackProcesses().take(3).toList()

        assertThat(events).containsExactly(
            ProcessAdded(pid = 101, "packageName1", "processName1"),
            ProcessAdded(pid = 102, "packageName2", "processName2"),
            ProcessAdded(pid = 103, "packageName3", "processName3"),
        )
    }

    @Test
    fun trackProcesses_addAndThenRemove_jdwpProcessFlow() = runTest(dispatchTimeoutMs = 5_000) {
        val device = setupDevice("device2", 30)
        val connectedDevice = adbSession.waitForDevice("device2")
        val tracker = JdwpProcessTracker(connectedDevice, logger)

        tracker.trackProcesses().toChannel(this).use { channel ->
            device.startClient(101, 1, "processName1", "packageName1", false)
            device.startClient(102, 1, "processName2", "packageName2", false)
            val addedEvents = channel.take(2)
            device.stopClient(101)
            device.stopClient(102)
            val removedEvents = channel.take(2)

            assertThat(addedEvents).containsExactly(
                ProcessAdded(pid = 101, "packageName1", "processName1"),
                ProcessAdded(pid = 102, "packageName2", "processName2"),
            )
            assertThat(removedEvents).containsExactly(
                ProcessRemoved(pid = 102),
                ProcessRemoved(pid = 101),
            )
        }
    }

    private fun setupDevice(serialNumber: String, sdk: Int) =
        fakeAdb.connectDevice(serialNumber, "", "", "13", sdk.toString(), USB).apply {
            deviceStatus = ONLINE
        }

    private suspend fun AdbSession.waitForDevice(serialNumber: String): ConnectedDevice {
        connectedDevicesTracker.connectedDevices.first {
            it.findDevice(serialNumber) != null
        }
        return connectedDevicesTracker.device(serialNumber)
    }
}

private fun List<ConnectedDevice>.findDevice(serialNumber: String) =
    firstOrNull { it.serialNumber == serialNumber }
