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
package com.android.adblib.ddmlibcompatibility.debugging

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.ddmlibcompatibility.AdbLibIDeviceManagerFactory
import com.android.adblib.isOnline
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.ddmlib.AdbInitOptions
import com.android.ddmlib.AndroidDebugBridge
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.devicecommandhandlers.SyncCommandHandler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class ConnectedDeviceExtensionsTest {

    @JvmField
    @Rule
    val fakeAdbRule = FakeAdbServerProviderRule {
        installDefaultCommandHandlers()
        installDeviceHandler(SyncCommandHandler())
    }

    private val fakeAdb get() = fakeAdbRule.fakeAdb

    @Before
    fun setUp() {
        AndroidDebugBridge.enableFakeAdbServerMode(fakeAdbRule.fakeAdb.port)
        val adbInitOptions =
            AdbInitOptions.builder().setClientSupportEnabled(true)
                .setIDeviceManagerFactory(AdbLibIDeviceManagerFactory(fakeAdbRule.adbSession))
        AndroidDebugBridge.init(adbInitOptions.build())
        AndroidDebugBridge.createBridge(10, TimeUnit.SECONDS) ?: error("Could not create ADB bridge")
    }

    @After
    fun tearDown() {
        AndroidDebugBridge.terminate()
        AndroidDebugBridge.disableFakeAdbServerMode()
    }

    @Test
    fun testAssociatedIDevice(): Unit = runBlocking {
        // Setup
        val serialNumber = "serial123"
        val device = createConnectedDevice(serialNumber)

        // Act
        // There is a slight delay between when a [ConnectedDevice] starts to be tracked by `adblib`
        // and when `adblib-ddmlibcompatibility` layer exposes it in `AndroidDebugBridge.devices`.
        yieldUntil {
            device.associatedIDevice() != null
        }
        assertEquals("serial123", device.associatedIDevice()?.serialNumber)

        // Act: disconnect device and assert `associatedIDevice` starts returning `null`
        fakeAdb.disconnectDevice(serialNumber)
        yieldUntil {
            device.associatedIDevice() == null
        }
        assertEquals(com.android.adblib.DeviceState.DISCONNECTED, device.deviceInfoFlow.value.deviceState)
    }

    private suspend fun createConnectedDevice(
        serialNumber: String,
        sdk: String = "29"
    ): ConnectedDevice {
        val fakeDevice =
            fakeAdb.connectDevice(
                serialNumber,
                "Google",
                "Pixel",
                "versionX",
                sdk,
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        return waitForOnlineConnectedDevice(fakeAdbRule.adbSession, serialNumber)
    }

    private suspend fun waitForOnlineConnectedDevice(
        session: AdbSession,
        serialNumber: String
    ): ConnectedDevice {
        return session.connectedDevicesTracker.connectedDevices
            .mapNotNull { connectedDevices ->
                connectedDevices.firstOrNull { device ->
                    device.isOnline && device.serialNumber == serialNumber
                }
            }.first()
    }
}
