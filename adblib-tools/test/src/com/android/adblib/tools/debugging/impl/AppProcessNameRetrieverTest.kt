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
package com.android.adblib.tools.debugging.impl

import com.android.adblib.connectedDevicesTracker
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.CoroutineTestUtils
import com.android.adblib.tools.debugging.AppProcessTracker
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import com.android.fakeadbserver.DeviceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.junit.Assert
import org.junit.Test
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class AppProcessNameRetrieverTest : AdbLibToolsTestBase() {

    @Test
    fun retrieveProcessNameFromJdwpProcess(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val deviceId = "1234"
        val fakeDevice =
            fakeAdb.connectDevice(
                deviceId,
                "test1",
                "test2",
                "model",
                "30", // SDK >= 30 is required for abb_exec feature.
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val connectedDevice =
            hostServices.session.connectedDevicesTracker.connectedDevices
                .mapNotNull { connectedDevices ->
                    connectedDevices.firstOrNull { device ->
                        device.serialNumber == fakeDevice.deviceId
                    }
                }.first()
        val pid10 = 10
        fakeDevice.startClient(pid10, 0, "a.b.c", false)
        val appTracker = AppProcessTracker.create(connectedDevice)
        val appProcesses =
            appTracker.appProcessFlow.first { appProcesses -> appProcesses.isNotEmpty() }
        val appProcessNameRetriever = AppProcessNameRetriever(appProcesses[0])

        // Act
        val appProcessName = appProcessNameRetriever.retrieve(1, Duration.ofMillis(0))

        // Assert
        Assert.assertEquals("a.b.c", appProcessName)
    }

    @Test
    fun retrieveProcessNameFromProc(): Unit = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val deviceId = "1234"
        val fakeDevice =
            fakeAdb.connectDevice(
                deviceId,
                "test1",
                "test2",
                "model",
                "30", // SDK >= 30 is required for abb_exec feature.
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val connectedDevice =
            hostServices.session.connectedDevicesTracker.connectedDevices
                .mapNotNull { connectedDevices ->
                    connectedDevices.firstOrNull { device ->
                        device.serialNumber == fakeDevice.deviceId
                    }
                }.first()
        val pid10 = 10
        fakeDevice.startProfileableProcess(pid10, "x86", "a.b.c")
        val appTracker = AppProcessTracker.create(connectedDevice)
        val appProcesses =
            appTracker.appProcessFlow.first { appProcesses -> appProcesses.isNotEmpty() }
        val appProcessNameRetriever = AppProcessNameRetriever(appProcesses[0])

        // Act
        val appProcessName = appProcessNameRetriever.retrieve(1, Duration.ofMillis(0))

        // Assert
        Assert.assertEquals("a.b.c", appProcessName)
    }

    @Test
    fun retrieveProcessNameFromProc_throwsCancellationException_whenAppProcessIsClosed(): Unit =
        CoroutineTestUtils.runBlockingWithTimeout {
            // Prepare
            val deviceId = "1234"
            val fakeDevice =
                fakeAdb.connectDevice(
                    deviceId,
                    "test1",
                    "test2",
                    "model",
                    "30", // SDK >= 30 is required for abb_exec feature.
                    DeviceState.HostConnectionType.USB
                )
            fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
            val connectedDevice =
                hostServices.session.connectedDevicesTracker.connectedDevices
                    .mapNotNull { connectedDevices ->
                        connectedDevices.firstOrNull { device ->
                            device.serialNumber == fakeDevice.deviceId
                        }
                    }.first()
            val appTracker = AppProcessTracker.create(connectedDevice)

            val pid10 = 10
            fakeDevice.startProfileableProcess(pid10, "x86", "a.b.c")
            val appProcess =
                appTracker.appProcessFlow.first { appProcesses -> appProcesses.isNotEmpty() }[0]
            val appProcessNameRetriever = AppProcessNameRetriever(appProcess)
            // Delay output so that we could close the `AppProcess` while retrieving AppProcessName
            fakeDevice.delayStdout = 2.seconds
            exceptionRule.expect(CancellationException::class.java)

            // Act
            launch {
                // delay closing `appProcess` to give `AppProcessNameRetriever` time to start process name retrieval
                delay(500)
            }.invokeOnCompletion {
                (appProcess as AppProcessImpl).close()
            }
            appProcessNameRetriever.retrieve(1, Duration.ofMillis(0))

            // Assert
            Assert.fail("Should not reach")
        }
}
