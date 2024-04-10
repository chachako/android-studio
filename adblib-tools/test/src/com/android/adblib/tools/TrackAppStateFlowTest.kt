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

import com.android.adblib.AdbDeviceFailResponseException
import com.android.adblib.AdbSessionHost
import com.android.adblib.AppProcessEntry
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.adblib.testingutils.TestingAdbSessionHost
import com.android.adblib.tools.debugging.trackAppStateFlow
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

class TrackAppStateFlowTest {

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
        val fakeDevice =
            fakeAdb.connectDevice(
                deviceID,
                "test1",
                "test2",
                "model",
                "31", // SDK >= 31 is required for track-app service.
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val connectedDevice =
            waitForOnlineConnectedDevice(hostServices.session, fakeDevice.deviceId)
        val pid10 = 10
        val pid11 = 11

        // Act
        val listOfProcessList = CopyOnWriteArrayList<List<AppProcessEntry>>()
        launch {
            fakeDevice.startClient(pid10, 0, "a.b.c", false)
            Assert.assertNotNull(fakeDevice.getClient(pid10))
            yieldUntil { listOfProcessList.size == 1 }

            fakeDevice.startProfileableProcess(pid11, "x86", "")
            Assert.assertNotNull(fakeDevice.getClient(pid10))
            Assert.assertNotNull(fakeDevice.getProfileableProcess(pid11))
            yieldUntil { listOfProcessList.size == 2 }

            // Note: Depending on how fast FakeAdbServer is, adblib may get one or two
            //       app tracking event
            fakeDevice.stopClient(pid10)
            fakeDevice.stopProfileableProcess(pid11)
            Assert.assertNull(fakeDevice.getClient(pid10))
            Assert.assertNull(fakeDevice.getProfileableProcess(pid11))
        }

        val trackAppFlow = connectedDevice.trackAppStateFlow()
        // Collecting the flow deterministically is a little tricky, as the list of events
        // in the flow depends on how fast FakeAdbServer emits events from the "track-app"
        // event and how fast adblib collects and emits these events in the app tracker
        // flow.
        trackAppFlow.takeWhile { item ->
            val processList = item.entries

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
                    if (processList.isNotEmpty()) {
                        assert(processList.size == 1)
                        listOfProcessList.add(processList)
                    }
                    true
                }
                // When the list has one element, add an element only if the process list
                // contains 2 elements.
                1 -> {
                    // The flow may emit a list of 1 process multiple times, because
                    // FakeAdbServer sometimes emit the same list multiple times
                    if (processList.size == 2) {
                        listOfProcessList.add(processList)
                    }
                    true
                }
                // When the list has 2 elements, wait until we get an empty process list
                // stop collecting at that point.
                2 -> {
                    if (processList.isEmpty()) {
                        // There may be 1 or 2 lists depending on how fast the flow
                        // catches up with the 2 process terminations.
                        listOfProcessList.add(processList)
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
        Assert.assertEquals(listOf(pid10), listOfProcessList[0].map { it.pid }.toList())

        // Second list has 2 processes
        Assert.assertEquals(2, listOfProcessList[1].size)
        Assert.assertEquals(listOf(pid10, pid11), listOfProcessList[1].map { it.pid }.toList())
        Assert.assertTrue(listOfProcessList[1].first { it.pid == pid10 }.debuggable)
        Assert.assertFalse(listOfProcessList[1].first { it.pid == pid11 }.debuggable)

        // Last list is empty
        Assert.assertEquals(0, listOfProcessList[2].size)

        // Ensure AppProcess instances are re-used across flow changes
        Assert.assertEquals(listOfProcessList[0].first { it.pid == pid10 },
                            listOfProcessList[1].first { it.pid == pid10 })

        val process10 = listOfProcessList[0].first { it.pid == pid10 }
        Assert.assertEquals(pid10, process10.pid)
    }

    @Test
    fun testStateFlowRetriesWhenChannelIsClosed(): Unit = runBlockingWithTimeout {
        // Prepare
        val deviceID = "1234"
        val fakeDevice =
            fakeAdb.connectDevice(
                deviceID,
                "test1",
                "test2",
                "model",
                "31", // SDK >= 31 is required for track_app feature.
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val connectedDevice =
            waitForOnlineConnectedDevice(hostServices.session, fakeDevice.deviceId)
        val pid10 = 10
        val pid11 = 11
        setHostPropertyValue(
            hostServices.session.host,
            AdbLibToolsProperties.TRACK_APP_RETRY_DELAY,
            Duration.ofMillis(100)
        )

        // Act
        val listOfProcessList = CopyOnWriteArrayList<List<AppProcessEntry>>()
        val trackAppFlow = connectedDevice.trackAppStateFlow()
        launch(Dispatchers.Default) {
            fakeDevice.startClient(pid10, 0, "a.b.c", false)
            fakeDevice.startClient(pid11, 0, "a.b.c.e", false)
            yieldUntil { listOfProcessList.size >= 1 && listOfProcessList.last().size == 2 }

            // Close the currently active `track-app` command
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
        trackAppFlow.first { trackAppItem ->
            listOfProcessList.add(trackAppItem.entries)
            trackAppItem.isEndOfFlow
        }
        // We don't assert anything, the fact we reached this point means the
        // flow was cancelled when the device was disconnected.
    }

    @Test
    fun testStateFlowReturnsErrorEntryWhenTrackAppNotSupported(): Unit = runBlockingWithTimeout {
        val deviceID = "1234"
        val fakeDevice =
            fakeAdb.connectDevice(
                deviceID,
                "test1",
                "test2",
                "model",
                "30", // track-app is not supported on API <= 30
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val connectedDevice =
            waitForOnlineConnectedDevice(hostServices.session, fakeDevice.deviceId)
        fakeDevice.startClient(10, 0, "a.b.c", false)

        // Act
        exceptionRule.expect(AdbDeviceFailResponseException::class.java)
        connectedDevice.trackAppStateFlow()

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testStateFlowStopsWhenDeviceDisconnects(): Unit = runBlockingWithTimeout {
        // Prepare
        val deviceID = "1234"
        val fakeDevice =
            fakeAdb.connectDevice(
                deviceID,
                "test1",
                "test2",
                "model",
                "31", // SDK >= 31 is required for track_app feature.
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val connectedDevice =
            waitForOnlineConnectedDevice(hostServices.session, fakeDevice.deviceId)
        val pid10 = 10
        val pid11 = 11

        // Act
        val listOfProcessList = CopyOnWriteArrayList<List<AppProcessEntry>>()
        val trackAppFlow = connectedDevice.trackAppStateFlow()
        launch(Dispatchers.Default) {
            fakeDevice.startClient(pid10, 0, "a.b.c", false)
            fakeDevice.startClient(pid11, 0, "a.b.c.e", false)
            yieldUntil { listOfProcessList.size >= 1 && listOfProcessList.last().size == 2 }

            fakeAdb.disconnectDevice(fakeDevice.deviceId)
        }

        // Assert
        trackAppFlow.first { item ->
            listOfProcessList.add(item.entries)
            item.isEndOfFlow
        }
        // We don't assert anything, the fact we reached this point means the
        // flow was cancelled when the device was disconnected.
    }

    @Test
    fun testStateFlowIsTransparentToException(): Unit = runBlockingWithTimeout {
        // Prepare
        val deviceID = "1234"
        val fakeDevice =
            fakeAdb.connectDevice(
                deviceID,
                "test1",
                "test2",
                "model",
                "31", // SDK >= 30 is required for track_app feature.
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val connectedDevice =
            waitForOnlineConnectedDevice(hostServices.session, fakeDevice.deviceId)
        val pid10 = 10

        // Act
        exceptionRule.expect(Exception::class.java)
        exceptionRule.expectMessage("My Test Exception")
        fakeDevice.startClient(pid10, 0, "a.b.c", false)
        val appTracker = connectedDevice.trackAppStateFlow()
        runBlocking {
            appTracker.collect {
                throw Exception("My Test Exception")
            }
        }

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testStateFlowCanBeCancelled(): Unit = runBlockingWithTimeout {
        // Prepare
        val deviceID = "1234"
        val fakeDevice =
            fakeAdb.connectDevice(
                deviceID,
                "test1",
                "test2",
                "model",
                "31", // SDK >= 30 is required for track_app feature.
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val connectedDevice =
            waitForOnlineConnectedDevice(hostServices.session, fakeDevice.deviceId)
        val pid10 = 10

        // Act/Assert
        exceptionRule.expect(CancellationException::class.java)
        exceptionRule.expectMessage("My Test Exception")
        fakeDevice.startClient(pid10, 0, "a.b.c", false)
        val appTracker = connectedDevice.trackAppStateFlow()
        runBlocking {
            appTracker.collect {
                cancel("My Test Exception")
            }
        }

        // Assert
        Assert.fail("Should not reach")
    }

    private fun <T : Any> setHostPropertyValue(
        host: AdbSessionHost,
        property: AdbSessionHost.Property<T>,
        value: T
    ) {
        (host as TestingAdbSessionHost).setPropertyValue(property, value)
    }
}
