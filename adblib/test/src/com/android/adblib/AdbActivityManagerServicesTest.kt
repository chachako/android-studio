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
package com.android.adblib

import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.devicecommandhandlers.SyncCommandHandler
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class AdbActivityManagerServicesTest {
    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    @JvmField
    @Rule
    val fakeAdbRule = FakeAdbServerProviderRule {
        installDefaultCommandHandlers()
        installDeviceHandler(SyncCommandHandler())
    }

    private val fakeAdb get() = fakeAdbRule.fakeAdb
    private val activityManagerServices get() = fakeAdbRule.adbSession.activityManagerServices

    @Test
    fun testForceStop(): Unit = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)
        val pid = 101
        device.startClient(pid, 1000, "package1", false)
        yieldUntil { device.getClient(pid) != null }

        // Act
        activityManagerServices.forceStop(deviceSelector, "package1")

        // Assert
        yieldUntil { device.getClient(pid) == null }
    }

    @Test
    fun testForceStopThrows_whenPackageContainsInvalidCharacters(): Unit = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)

        // Act
        exceptionRule.expect(IllegalArgumentException::class.java)
        activityManagerServices.forceStop(deviceSelector, "package name with spaces")

        // Assert
        Assert.fail("Test should have thrown an exception")
    }

    @Test
    fun testCrash(): Unit = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)
        val pid = 101
        device.startClient(pid, 1000, "package1", false)
        yieldUntil { device.getClient(pid) != null }

        // Act
        activityManagerServices.crash(deviceSelector, "package1")

        // Assert
        yieldUntil { device.getClient(pid) == null }
    }

    @Test
    fun testCrashThrows_whenPackageContainsInvalidCharacters(): Unit = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)

        // Act
        exceptionRule.expect(IllegalArgumentException::class.java)
        activityManagerServices.crash(deviceSelector, "package&ampersand")

        // Assert
        Assert.fail("Test should have thrown an exception")
    }

    private fun addFakeDevice(fakeAdb: FakeAdbServerProvider, sdk: Int = 30): DeviceState {
        val fakeDevice =
            fakeAdb.connectDevice(
                "1234",
                "test1",
                "test2",
                "model",
                "$sdk",
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        return fakeDevice
    }
}
