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
package com.android.adblib.tools

import com.android.adblib.AdbSessionHost
import com.android.adblib.ProcessIdList
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.adblib.testingutils.TestingAdbSessionHost
import com.android.adblib.tools.debugging.trackJdwpStateFlow
import com.android.adblib.tools.testutils.waitForOnlineConnectedDevice
import com.android.fakeadbserver.DeviceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList

class TrackJdwpStateFlowTest {

    @JvmField
    @Rule
    val fakeAdbRule = FakeAdbServerProviderRule {
        installDefaultCommandHandlers()
    }

    @JvmField
    @Rule
    val exceptionRule: ExpectedException = ExpectedException.none()

    private val fakeAdb get() = fakeAdbRule.fakeAdb
    private val hostServices get() = fakeAdbRule.adbSession.hostServices

    @Test
    fun testStateFlowWorks(): Unit = runBlockingWithTimeout {
        val deviceID = "1234"
        val fakeDevice = createFakeDevice(deviceID)
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val connectedDevice =
            waitForOnlineConnectedDevice(hostServices.session, fakeDevice.deviceId)
        val pid10 = 10
        val pid11 = 11

        // Act
        val listOfProcessList = CopyOnWriteArrayList<ProcessIdList>()
        launch {
            fakeDevice.startClient(pid10, 0, "a.b.c", false)
            Assert.assertNotNull(fakeDevice.getClient(pid10))
            yieldUntil {
                val size = listOfProcessList.size
                size == 1
            }

            fakeDevice.startClient(pid11, 0, "d.e.f", false)
            Assert.assertNotNull(fakeDevice.getClient(pid10))
            Assert.assertNotNull(fakeDevice.getClient(pid11))
            yieldUntil { listOfProcessList.size == 2 }

            // Note: Depending on how fast FakeAdbServer is, adblib may get one or two
            //       app tracking event
            fakeDevice.stopClient(pid10)
            fakeDevice.stopClient(pid11)
            Assert.assertNull(fakeDevice.getClient(pid10))
            Assert.assertNull(fakeDevice.getClient(pid11))
        }

        val trackJdwpFlow = connectedDevice.trackJdwpStateFlow()
        // Collecting the flow deterministically is a little tricky, as the list of events
        // in the flow depends on how fast FakeAdbServer emits events from the "track-app"
        // event and how fast adblib collects and emits these events in the app tracker
        // flow.
        trackJdwpFlow.takeWhile { trackJdwpItem ->
            val processIds = trackJdwpItem.processIds

            // The goal here is to collect 3 list of processes in `listOfProcessList`
            // * One with a single process
            // * One with 2 processes
            // * One with no processes (after both processes are stopped)
            // The flow itself may emit a variable amount of "no process" lists in the flow,
            // then a variable amount of "1 process" lists in the flow, then a variable
            // amount of "2 processes" lists, then a variable amount of "no process" lists.
            when (listOfProcessList.size) {
                // When the list is empty, only add a non-empty process list, it should be a list
                // of 1 process.
                0 -> {
                    if (processIds.isNotEmpty()) {
                        assert(processIds.size == 1)
                        listOfProcessList.add(processIds)
                    }
                    true
                }
                // When the list has one element, add an element only if the process list
                // contains 2 elements.
                1 -> {
                    // The flow may emit a list of 1 process multiple times, because
                    // FakeAdbServer sometimes emit the same list multiple times
                    if (processIds.size == 2) {
                        listOfProcessList.add(processIds)
                    }
                    true
                }
                // When the list has 2 elements, wait until we get an empty process list
                // stop collecting at that point.
                2 -> {
                    if (processIds.isEmpty()) {
                        // There may be 1 or 2 lists depending on how fast the flow
                        // catches up with the 2 process terminations.
                        listOfProcessList.add(processIds)
                        false
                    } else {
                        true
                    }
                }

                else -> {
                    Assert.fail("Should not reach")
                    false
                }
            }
        }.collect()

        // Assert: We should have 3 lists: 1 process, 2 processes, empty list.
        Assert.assertTrue(listOfProcessList.size == 3)

        // First list has one process
        Assert.assertEquals(1, listOfProcessList[0].size)
        Assert.assertEquals(listOf(pid10), listOfProcessList[0])

        // Second list has 2 processes
        Assert.assertEquals(2, listOfProcessList[1].size)
        Assert.assertEquals(listOf(pid10, pid11), listOfProcessList[1])

        // Last list is empty
        Assert.assertEquals(0, listOfProcessList[2].size)
    }

    @Test
    fun testStateFlowRetriesWhenChannelIsClosed(): Unit = runBlockingWithTimeout {
        // Prepare
        val deviceID = "1234"
        val fakeDevice = createFakeDevice(deviceID)
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val connectedDevice =
            waitForOnlineConnectedDevice(hostServices.session, fakeDevice.deviceId)
        val pid10 = 10
        val pid11 = 11
        setHostPropertyValue(
            hostServices.session.host,
            AdbLibToolsProperties.TRACK_JDWP_RETRY_DELAY,
            Duration.ofMillis(100)
        )

        // Act
        val listOfProcessList = CopyOnWriteArrayList<ProcessIdList>()
        val trackJdwpFlow = connectedDevice.trackJdwpStateFlow()
        launch(Dispatchers.Default) {
            fakeDevice.startClient(pid10, 0, "a.b.c", false)
            fakeDevice.startClient(pid11, 0, "a.b.c.e", false)
            yieldUntil { listOfProcessList.size >= 1 && listOfProcessList.last().size == 2 }

            // Close the currently active `track-jdwp` command
            fakeAdb.channelProvider.lastCreatedChannel?.close()

            // Stop a process, then wait until list has been updated accordingly,
            // meaning the flow has "recovered" from the channel closure
            fakeDevice.stopClient(pid11)

            // The list keeps updating even after closing the active channel
            yieldUntil { listOfProcessList.size >= 1 && listOfProcessList.last().size == 1 }

            // Stop the flow
            fakeAdb.disconnectDevice(fakeDevice.deviceId)
        }

        // Assert
        trackJdwpFlow.first { trackJdwpItem ->
            listOfProcessList.add(trackJdwpItem.processIds)
            trackJdwpItem.isEndOfFlow
        }
        // We don't assert anything, the fact we reached this point means the
        // flow was cancelled when the device was disconnected.
    }

    @Test
    fun testStateFlowStopsWhenDeviceDisconnects(): Unit = runBlockingWithTimeout {
        // Prepare
        val deviceID = "1234"
        val fakeDevice = createFakeDevice(deviceID)
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val connectedDevice =
            waitForOnlineConnectedDevice(hostServices.session, fakeDevice.deviceId)
        val pid10 = 10
        val pid11 = 11

        // Act
        val listOfProcessList = CopyOnWriteArrayList<ProcessIdList>()
        val trackJdwpFlow = connectedDevice.trackJdwpStateFlow()
        launch(Dispatchers.Default) {
            fakeDevice.startClient(pid10, 0, "a.b.c", false)
            fakeDevice.startClient(pid11, 0, "a.b.c.e", false)
            yieldUntil { listOfProcessList.size >= 1 && listOfProcessList.last().size == 2 }

            fakeAdb.disconnectDevice(fakeDevice.deviceId)
        }

        // Assert
        trackJdwpFlow.first { trackJdwpItem ->
            listOfProcessList.add(trackJdwpItem.processIds)
            trackJdwpItem.isEndOfFlow
        }
        // We don't assert anything, the fact we reached this point means the
        // flow was cancelled when the device was disconnected.
    }

    @Test
    fun testStateFlowIsTransparentToException(): Unit = runBlockingWithTimeout {
        // Prepare
        val deviceID = "1234"
        val fakeDevice = createFakeDevice(deviceID)
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val connectedDevice =
            waitForOnlineConnectedDevice(hostServices.session, fakeDevice.deviceId)
        val pid10 = 10

        // Act
        exceptionRule.expect(Exception::class.java)
        exceptionRule.expectMessage("My Test Exception")
        fakeDevice.startClient(pid10, 0, "a.b.c", false)
        val trackJdwpFlow = connectedDevice.trackJdwpStateFlow()
        runBlocking {
            trackJdwpFlow.collect {
                throw Exception("My Test Exception")
            }
        }

        // Assert
    }

    @Test
    fun testStateFlowCanBeCancelled(): Unit = runBlockingWithTimeout {
        // Prepare
        val deviceID = "1234"
        val fakeDevice = createFakeDevice(deviceID)
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val connectedDevice =
            waitForOnlineConnectedDevice(hostServices.session, fakeDevice.deviceId)
        val pid10 = 10

        // Act/Assert
        exceptionRule.expect(CancellationException::class.java)
        exceptionRule.expectMessage("My Test Exception")
        fakeDevice.startClient(pid10, 0, "a.b.c", false)
        val trackJdwpFlow = connectedDevice.trackJdwpStateFlow()
        runBlocking {
            trackJdwpFlow.collect {
                cancel("My Test Exception")
            }
        }

        // Assert
    }

    private fun createFakeDevice(deviceID: String, sdk: Int = 30): DeviceState {
        return fakeAdb.connectDevice(
            deviceID,
            "test1",
            "test2",
            "model",
            sdk.toString(),
            DeviceState.HostConnectionType.USB
        )
    }

    private fun <T : Any> setHostPropertyValue(
        host: AdbSessionHost,
        property: AdbSessionHost.Property<T>,
        value: T
    ) {
        (host as TestingAdbSessionHost).setPropertyValue(property, value)
    }
}
