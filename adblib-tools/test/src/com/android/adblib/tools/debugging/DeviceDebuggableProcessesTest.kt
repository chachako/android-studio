/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.adblib.tools.debugging

import com.android.adblib.ConnectedDevice
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.scope
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.CoroutineTestUtils
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.adblib.tools.testutils.waitForOnlineConnectedDevice
import com.android.fakeadbserver.DeviceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class DeviceDebuggableProcessesTest {

    @JvmField
    @Rule
    val fakeAdbRule = FakeAdbServerProviderRule {
        installDefaultCommandHandlers()
    }

    private val fakeAdb get() = fakeAdbRule.fakeAdb
    private val hostServices get() = fakeAdbRule.adbSession.hostServices

    @Test
    fun testConnectedDeviceDebuggableProcesses_tracksExistingProcess(): Unit =
        CoroutineTestUtils.runBlockingWithTimeout {
            // Prepare
            val (connectedDevice, fakeDevice) = connectOnlineDevice()
            val pid10 = 10
            fakeDevice.startClient(pid10, 0, "a.b.c", true)
            val processes = connectedDevice.appProcessFlow.first { it.isNotEmpty() }
            assertEquals(1, processes.size)
            waitForInitialProperties(processes[0].jdwpProcess!!)

            // Act / Assert
            val processUpdatesList = CopyOnWriteArrayList<JdwpProcessChange>()
            launch {
                yieldUntil { processUpdatesList.size >= 1 }
                with(processUpdatesList[0]) {
                    assertEquals(this::class, JdwpProcessChange.Added::class)
                    val processAdded = this as JdwpProcessChange.Added
                    assertTrue(processAdded.processInfo.properties.pid == pid10)
                }

                // Wait a little and asserts that the `deviceDebuggableProcessesFlow` didn't
                // collect some unexpected update
                delay(200)
                assertEquals(1, processUpdatesList.size)

                fakeAdb.disconnectDevice(fakeDevice.deviceId)
            }

            connectedDevice.scope.launch {
                connectedDevice.deviceDebuggableProcessesFlow.collect {
                    processUpdatesList.add(it)
                }
            }.join()
        }

    @Test
    fun testConnectedDeviceDebuggableProcesses_tracksNewProcess(): Unit =
        CoroutineTestUtils.runBlockingWithTimeout {
            // Prepare
            val deviceID = "1234"
            val fakeDevice = fakeAdb.connectDevice(
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

            // Act / Assert
            val processUpdatesList = CopyOnWriteArrayList<JdwpProcessChange>()
            launch {
                // Start a client
                // It triggers one `addedProcesses` update, followed by several `updatedPropertiesProcesses`
                // updates as a result of properties being updated from HELO, FEAT, and other DDMS packages
                fakeDevice.startClient(pid10, 0, "a.b.c.e", false)
                yieldUntil { processUpdatesList.size >= 2 }
                with(processUpdatesList[0]) {
                    assertEquals(this::class, JdwpProcessChange.Added::class)
                    val processAdded = this as JdwpProcessChange.Added
                    assertTrue(processAdded.processInfo.properties.pid == pid10)
                }
                // remaining items in the processUpdatesList should be about property updates
                yieldUntil {
                    (processUpdatesList.drop(1).last() as? JdwpProcessChange.Updated)?.processInfo
                        ?.properties?.packageName != null
                }
                val lastUpdate = processUpdatesList.last() as JdwpProcessChange.Updated
                assertTrue(lastUpdate.processInfo.properties.pid == pid10)
                assertEquals("a.b.c.e", lastUpdate.processInfo.properties.packageName)
                fakeAdb.disconnectDevice(fakeDevice.deviceId)
            }

            connectedDevice.scope.launch {
                connectedDevice.deviceDebuggableProcessesFlow.collect {
                    processUpdatesList.add(it)
                }
            }.join()
        }

    @Test
    fun testConnectedDeviceDebuggableProcesses_tracksRemovedProcess(): Unit =
        CoroutineTestUtils.runBlockingWithTimeout {
            // Prepare
            val (connectedDevice, fakeDevice) = connectOnlineDevice()
            val pid10 = 10
            fakeDevice.startClient(pid10, 0, "a.b.c", true)
            val processes = connectedDevice.appProcessFlow.first { it.isNotEmpty() }
            assertEquals(1, processes.size)
            waitForInitialProperties(processes[0].jdwpProcess!!)
            // wait for process to settle, e.g. process FEAT and WAIT packets
            delay(100)

            // Act / Assert
            val processUpdatesList = CopyOnWriteArrayList<JdwpProcessChange>()
            launch {
                // We should receive a single update about the previously started process
                yieldUntil { processUpdatesList.size == 1 }
                with(processUpdatesList[0]) {
                    assertEquals(this::class, JdwpProcessChange.Added::class)
                    val processAdded = this as JdwpProcessChange.Added
                    assertTrue(processAdded.processInfo.properties.pid == pid10)
                }

                // Wait a little to make sure we don't receive any additional updates.
                delay(200)
                assertEquals(1, processUpdatesList.size)

                // Remove process
                fakeDevice.stopClient(pid10)
                yieldUntil { processUpdatesList.size == 2 }
                with(processUpdatesList[1]) {
                    assertEquals(this::class, JdwpProcessChange.Removed::class)
                    val processRemoved = this as JdwpProcessChange.Removed
                    assertTrue(processRemoved.processInfo.properties.pid == pid10)
                }

                fakeAdb.disconnectDevice(fakeDevice.deviceId)
            }

            connectedDevice.scope.launch {
                connectedDevice.deviceDebuggableProcessesFlow.collect {
                    processUpdatesList.add(it)
                }
            }.join()
        }

    @Test
    fun testConnectedDeviceDebuggableProcesses_waitsForDeviceOnlineBeforeEmittingUpdates(): Unit =
        CoroutineTestUtils.runBlockingWithTimeout {
            // Prepare
            val deviceID = "1234"
            val pid10 = 10
            val fakeDevice = fakeAdb.connectDevice(
                deviceID,
                "test1",
                "test2",
                "model",
                "31", // SDK >= 31 is required for track_app feature.
                DeviceState.HostConnectionType.USB
            )
            val connectedDevice = hostServices.session.connectedDevicesTracker.connectedDevices
                .mapNotNull { connectedDevices ->
                    connectedDevices.firstOrNull { device -> device.serialNumber == deviceID }
                }.first()

            // Act / Assert
            val processUpdatesList = CopyOnWriteArrayList<JdwpProcessChange>()
            launch {
                delay(200)
                assertTrue(processUpdatesList.isEmpty())

                // Make the device go online and observe that we can receive updates after that
                fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
                fakeDevice.startClient(pid10, 0, "a.b.c", true)
                yieldUntil { processUpdatesList.size >= 1 }
                with(processUpdatesList[0]) {
                    assertEquals(this::class, JdwpProcessChange.Added::class)
                    val processAdded = this as JdwpProcessChange.Added
                    assertTrue(processAdded.processInfo.properties.pid == pid10)
                }

                fakeAdb.disconnectDevice(fakeDevice.deviceId)
            }

            connectedDevice.scope.launch {
                connectedDevice.deviceDebuggableProcessesFlow.collect {
                    processUpdatesList.add(it)
                }
            }.join()
        }

    @Test
    fun testConnectedDeviceDebuggableProcesses_synchronized(): Unit =
        CoroutineTestUtils.runBlockingWithTimeout {
            // Prepare
            val (connectedDevice, fakeDevice) = connectOnlineDevice()

            // Act / Assert
            val processUpdatesList = CopyOnWriteArrayList<JdwpProcessChange>()
            launch {
                // This test produces a bunch of info logs like this:
                // `AdbDeviceFailResponseException: 'No client exists for pid: 64' error on device
                // serial #1234 executing service 'jdwp:64'`
                // This happens since process update may arrive after the process has been stopped.
                for (i in 10..100) {
                    fakeDevice.startClient(i, 0, "a.b.c", true)
                    delay(5)
                    fakeDevice.stopClient(i)
                    delay(5)
                }
                delay(100)

                // Ensure that for every pid the first change is `added`, the last change
                // is `removed` with optional `updated` changes in between.
                processUpdatesList.map { processChange ->
                    when (processChange) {
                        is JdwpProcessChange.Added -> processChange.processInfo.properties.pid to "Added"
                        is JdwpProcessChange.Updated -> processChange.processInfo.properties.pid to "Updated"
                        is JdwpProcessChange.Removed -> processChange.processInfo.properties.pid to "Removed"
                        else -> throw IllegalStateException("Unexpected JdwpProcessChange type")
                    }
                }.groupBy { it.first }.forEach {
                    // This is a list of updates per process id
                    val processChanges = it.value.map { pidToStatus -> pidToStatus.second }
                    assertEquals("Added", processChanges.first())
                    assertEquals("Removed", processChanges.last())
                    processChanges.drop(1)
                        .dropLast(1)
                        .forEach { changeType -> assertEquals("Updated", changeType) }
                }

                fakeAdb.disconnectDevice(fakeDevice.deviceId)
            }

            connectedDevice.scope.launch {
                connectedDevice.deviceDebuggableProcessesFlow.collect {
                    processUpdatesList.add(it)
                }
            }.join()
        }

    private suspend fun connectOnlineDevice(): Pair<ConnectedDevice, DeviceState> {
        val deviceID = "1234"
        val fakeDevice = fakeAdb.connectDevice(
            deviceID,
            "test1",
            "test2",
            "model",
            "31", // SDK >= 31 is required for track_app feature.
            DeviceState.HostConnectionType.USB
        )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        return Pair(
            waitForOnlineConnectedDevice(hostServices.session, fakeDevice.deviceId), fakeDevice
        )
    }

    private suspend fun waitForInitialProperties(jdwpProcess: JdwpProcess) {
        // Wait for initial properties that get populated from HELO, FEAT, etc. DDMS packages
        // that are received right after JDWP-Handshake.
        yieldUntil { jdwpProcess.properties.processName != null && jdwpProcess.properties.features.isNotEmpty() }
    }
}
