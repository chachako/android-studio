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
package com.android.adblib.ddmlibcompatibility.debugging

import com.android.adblib.AdbDeviceFailResponseException
import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.isOnline
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.ddmlib.AdbHelper
import com.android.ddmlib.IShellOutputReceiver
import com.android.ddmlib.MultiLineReceiver
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.devicecommandhandlers.SyncCommandHandler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ShellTest {

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

    @Test
    fun executeShellCommandShouldWork() = runBlockingWithTimeout {
        // Prepare
        val device = createConnectedDevice("42")
        val receiver = ListReceiver()

        // Act
        executeShellCommand(
            AdbHelper.AdbService.SHELL,
            device,
            "getprop",
            receiver,
            0,
            0,
            TimeUnit.MILLISECONDS,
            null,
            true
        )

        // Assert
        val expected = """# This is some build info
# This is more build info

[ro.build.version.release]: [versionX]
[ro.build.version.sdk]: [29]
[ro.product.cpu.abi]: [x86_64]
[ro.product.manufacturer]: [Google]
[ro.product.model]: [Pix3l]
[ro.serialno]: [42]
"""
        assertEquals(expected, receiver.lines.joinToString("\n"))
    }

    @Test
    @Throws(Exception::class)
    fun executeShellCommandShouldThrowIfInvalidCommand() = runBlockingWithTimeout {
        // Prepare
        val device = createConnectedDevice("42")
        val receiver = ListReceiver()

        // Act
        exceptionRule.expect(AdbDeviceFailResponseException::class.java)
        executeShellCommand(
            AdbHelper.AdbService.SHELL,
            device,
            "foobarz",
            receiver,
            0,
            0,
            TimeUnit.MILLISECONDS,
            null,
            true
        )

        // Assert
        Assert.fail() // should not be reached
    }

    @Test
    fun executeAbbCommandShouldWork() = runBlockingWithTimeout {
        // Prepare
        val device = createConnectedDevice("42", sdk = "30")
        val receiver = ListReceiver()

        // Act
        executeAbbCommand(
            AdbHelper.AdbService.ABB_EXEC,
            device,
            "package path com.foo.bar.appp",
            receiver,
            0,
            0,
            TimeUnit.MILLISECONDS,
            null,
            true
        )

        // Assert
        val expected = "/data/app/com.foo.bar.appp/base.apk"
        assertEquals(expected, receiver.lines.joinToString())
    }

    @Test
    @Throws(Exception::class)
    fun executeAbbCommandOnUnsupportedDeviceShouldThrow() = runBlockingWithTimeout {
        // Prepare
        // Create a device that doesn't support ABB
        val device = createConnectedDevice("42", sdk = "20")
        val receiver = ListReceiver()

        // Act
        exceptionRule.expect(IllegalArgumentException::class.java)
        exceptionRule.expectMessage("No compatible abb protocol is supported or allowed")
        executeAbbCommand(
            AdbHelper.AdbService.ABB_EXEC,
            device,
            "package list packages",
            receiver,
            0,
            0,
            TimeUnit.MILLISECONDS,
            null,
            true
        )

        // Assert
        Assert.fail() // should not be reached
    }

    @Test
    fun executeShellCommand_doesntCallReceiverConcurrently() = runBlockingWithTimeout {
        // Prepare
        val device = createConnectedDevice("42")
        val receiver = ConcurrencyTrackingIShellOutputReceiver()

        // Act
        executeShellCommand(
            AdbHelper.AdbService.SHELL,
            device,
            "getprop",
            receiver,
            0,
            0,
            TimeUnit.MILLISECONDS,
            null,
            true
        )

        // Assert
        assertEquals(0, receiver.concurrentCallsDetected.get())
        assertEquals(1, receiver.addOutputCallCount.get())
        assertEquals(1, receiver.flushCallCount.get())
        // isCancelledCallCount is non-deterministic as there is a cancellation prober
        assertTrue(receiver.isCancelledCallCount.get() >= 1)
    }

    @Test
    fun executeAbbCommand_doesntCallReceiverConcurrently() = runBlockingWithTimeout {
        // Prepare
        val device = createConnectedDevice("42", sdk = "30")
        val receiver = ConcurrencyTrackingIShellOutputReceiver()

        // Act
        executeAbbCommand(
            AdbHelper.AdbService.ABB_EXEC,
            device,
            "package path com.foo.bar.appp",
            receiver,
            0,
            0,
            TimeUnit.MILLISECONDS,
            null,
            true
        )

        // Assert
        assertEquals(0, receiver.concurrentCallsDetected.get())
        assertEquals(1, receiver.addOutputCallCount.get())
        assertEquals(1, receiver.flushCallCount.get())
        // isCancelledCallCount is non-deterministic as there is a cancellation prober
        assertTrue(receiver.isCancelledCallCount.get() >= 1)
    }

    private suspend fun createConnectedDevice(
        serialNumber: String,
        sdk: String = "29"
    ): ConnectedDevice {
        val fakeDevice =
            fakeAdb.connectDevice(
                serialNumber,
                "Google",
                "Pix3l",
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

internal class ListReceiver : MultiLineReceiver() {

    val lines = mutableListOf<String>()

    override fun processNewLines(lines: Array<out String>) {
        this.lines.addAll(lines)
    }

    override fun isCancelled() = false
}

private class ConcurrencyTrackingIShellOutputReceiver : IShellOutputReceiver {
    val addOutputCallCount = AtomicInteger(0)
    val flushCallCount = AtomicInteger(0)
    val isCancelledCallCount = AtomicInteger(0)
    val concurrentCallsDetected = AtomicInteger(0)
    private val executionCounter = AtomicInteger(0)

    override fun addOutput(data: ByteArray, offset: Int, length: Int) {
        addOutputCallCount.incrementAndGet()
        sleepAndCheckConcurrency()
    }

    override fun flush() {
        flushCallCount.incrementAndGet()
        sleepAndCheckConcurrency()
    }

    override fun isCancelled(): Boolean {
        isCancelledCallCount.incrementAndGet()
        sleepAndCheckConcurrency()
        return false
    }

    private fun sleepAndCheckConcurrency() {
        val currentCount = executionCounter.incrementAndGet()
        if (currentCount != 1) {
            concurrentCallsDetected.incrementAndGet()
        }
        Thread.sleep(50)
        executionCounter.decrementAndGet()
    }
}
