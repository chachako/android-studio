/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.adblib.AdbSession
import com.android.adblib.property
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.tools.AdbLibToolsProperties.DDMS_REPLY_WAIT_TIMEOUT
import com.android.adblib.tools.debugging.DdmsProtocolKind
import com.android.adblib.tools.debugging.JdwpSession
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.Signal
import com.android.adblib.tools.debugging.ddmsProtocolKind
import com.android.adblib.tools.debugging.handleDdmsCommandAndReplyProtocol
import com.android.adblib.tools.debugging.withTimeoutAfterSignal
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import com.android.adblib.tools.testutils.FakeJdwpCommandProgress
import com.android.adblib.tools.testutils.waitForOnlineConnectedDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Duration

class SharedJdwpSessionUtilsTest : AdbLibToolsTestBase() {

    @Test
    fun withTimeoutAfterSignalWorks() = runBlockingWithTimeout {
        // Act
        val result = withTimeoutAfterSignal<Int>(Duration.ofSeconds(2)) { signal ->
            signal.complete(100)
        }

        // Assert
        assertEquals(100, result)
    }

    @Test
    fun withTimeoutAfterSignalWorksIfDelayBeforeSignal() = runBlockingWithTimeout {
        // Act
        // Note: The timeout value is smaller than the `delay` value
        val result = withTimeoutAfterSignal<Int>(Duration.ofMillis(200)) { signal ->
            delay(500)
            signal.complete(100)
        }

        // Assert
        assertEquals(100, result)
    }

    @Test
    fun withTimeoutAfterSignalThrowsIfDelayAfterSignal() = runBlockingWithTimeout {
        // Act
        exceptionRule.expect(TimeoutCancellationException::class.java)
        withTimeoutAfterSignal<Int>(Duration.ofMillis(50)) { signal ->
            signal.complete(100)
            delay(10_000)
        }

        // Assert
        fail("Should not be reached")
    }

    @Test
    fun withTimeoutAfterSignalIsTransparentToExceptionsBeforeSignal() = runBlockingWithTimeout {
        // Act
        exceptionRule.expect(IOException::class.java)
        exceptionRule.expectMessage("Foo")
        withTimeoutAfterSignal<Int>(Duration.ofSeconds(2)) { signal ->
            signal.complete(100)
            throw IOException("Foo")
        }

        // Assert
        fail("Should not be reached")
    }

    @Test
    fun withTimeoutAfterSignalIsTransparentToExceptionsAfterSignal() = runBlockingWithTimeout {
        // Act
        exceptionRule.expect(IOException::class.java)
        exceptionRule.expectMessage("Foo")
        withTimeoutAfterSignal<Int>(Duration.ofSeconds(2)) { _ ->
            throw IOException("Foo")
        }

        // Assert
        fail("Should not be reached")
    }

    @Test
    fun withTimeoutAfterSignalIsTransparentToCancellationBeforeSignal() = runBlockingWithTimeout {
        // Act
        exceptionRule.expect(CancellationException::class.java)
        exceptionRule.expectMessage("Foo")
        withTimeoutAfterSignal<Int>(Duration.ofSeconds(2)) { signal ->
            signal.complete(100)
            cancel("Foo")
        }

        // Assert
        fail("Should not be reached")
    }

    @Test
    fun withTimeoutAfterSignalIsTransparentToCancellationAfterSignal() = runBlockingWithTimeout {
        // Act
        exceptionRule.expect(CancellationException::class.java)
        exceptionRule.expectMessage("Foo")
        withTimeoutAfterSignal<Int>(Duration.ofSeconds(2)) { _ ->
            cancel("Foo")
        }

        // Assert
        fail("Should not be reached")
    }

    @Test
    fun handleDdmsCommandWithEmptyReplyWithEmptyRepliesAllowed_doesNotTimeOut() =
        runBlockingWithTimeout {
            // Api 27 maps to DdmsProtocolKind.EmptyRepliesAllowed
            val fakeDevice = addFakeDevice(fakeAdb, 27)
            fakeDevice.startClient(10, 0, "a.b.c", false)

            // Act
            val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)
            val jdwpCommandProgress = FakeJdwpCommandProgress()
            val result =
                jdwpSession.handleDdmsCommandAndReplyProtocol(jdwpCommandProgress) { signal: Signal<Long> ->
                    signalAndWait(
                        session.property(DDMS_REPLY_WAIT_TIMEOUT).toMillis() + 100,
                        signal
                    ) { 101L }
                }

            assertEquals(
                DdmsProtocolKind.EmptyRepliesAllowed,
                jdwpSession.device.ddmsProtocolKind()
            )
            assertEquals(101L, result)
            // For DdmsProtocolKind.EmptyRepliesAllowed timeout is not triggered after the
            // DDMS_NO_REPLY_WAIT_TIMEOUT has passed
            assertFalse(jdwpCommandProgress.onReplyTimeoutIsCalled)
        }

    @Test
    fun handleDdmsCommandWithEmptyReplyWithEmptyRepliesAllowed_canHandleExceptionAfterSignal() =
        runBlockingWithTimeout {
            // Api 27 maps to DdmsProtocolKind.EmptyRepliesAllowed
            val fakeDevice = addFakeDevice(fakeAdb, 27)
            fakeDevice.startClient(10, 0, "a.b.c", false)

            // Act
            val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)
            assertEquals(
                DdmsProtocolKind.EmptyRepliesAllowed,
                jdwpSession.device.ddmsProtocolKind()
            )

            exceptionRule.expect(IOException::class.java)
            exceptionRule.expectMessage("Foo")
            jdwpSession.handleDdmsCommandAndReplyProtocol(null) { signal: Signal<Long> ->
                signalAndWait(100, signal) { 101L }
                throw IOException("Foo")
            }

            // Assert
            fail("Should not be reached")
        }

    @Test
    fun handleDdmsCommandWithEmptyReplyWithEmptyRepliesDiscarded_returnsResultBeforeTimeout() =
        runBlockingWithTimeout {
            val fakeDevice = addFakeDevice(fakeAdb, 30)
            fakeDevice.startClient(10, 0, "a.b.c", false)

            // Act
            val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)
            val jdwpCommandProgress = FakeJdwpCommandProgress()
            val result =
                jdwpSession.handleDdmsCommandAndReplyProtocol(jdwpCommandProgress) { signal: Signal<Long> ->
                    signalAndWait(100, signal) { 101L }
                }

            // Assert
            assertEquals(
                DdmsProtocolKind.EmptyRepliesDiscarded,
                jdwpSession.device.ddmsProtocolKind()
            )
            assertEquals(101L, result)
            // Doesn't wait for timeout if the result is available sooner
            assertFalse(jdwpCommandProgress.onReplyTimeoutIsCalled)
        }

    @Test
    fun handleDdmsCommandWithEmptyReplyWithEmptyRepliesDiscarded_returnsResultOnTimeout() =
        runBlockingWithTimeout {
            val fakeDevice = addFakeDevice(fakeAdb, 30)
            fakeDevice.startClient(10, 0, "a.b.c", false)

            // Act
            val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)
            val jdwpCommandProgress = FakeJdwpCommandProgress()
            val result =
                jdwpSession.handleDdmsCommandAndReplyProtocol(jdwpCommandProgress) { signal: Signal<Long> ->
                    signalAndWait(500000, signal) { 101L }
                }

            assertEquals(
                DdmsProtocolKind.EmptyRepliesDiscarded,
                jdwpSession.device.ddmsProtocolKind()
            )
            assertEquals(101L, result)
            assertTrue(jdwpCommandProgress.onReplyTimeoutIsCalled)
        }

    @Test
    fun handleDdmsCommandWithEmptyReplyWithEmptyRepliesDiscarded_canHandleExceptionAfterSignal() =
        runBlockingWithTimeout {
            val fakeDevice = addFakeDevice(fakeAdb, 30)
            fakeDevice.startClient(10, 0, "a.b.c", false)

            // Act
            val jdwpSession = openSharedJdwpSession(session, fakeDevice.deviceId, 10)
            assertEquals(
                DdmsProtocolKind.EmptyRepliesDiscarded,
                jdwpSession.device.ddmsProtocolKind()
            )

            exceptionRule.expect(IOException::class.java)
            exceptionRule.expectMessage("Foo")
            jdwpSession.handleDdmsCommandAndReplyProtocol(null) { signal: Signal<Long> ->
                signalAndWait(100, signal) { 101L }
                throw IOException("Foo")
            }

            // Assert
            fail("Should not be reached")
        }

    private suspend fun signalAndWait(
        waitTimeMillis: Long,
        signal: Signal<Long>,
        block: suspend () -> Long
    ) {
        signal.complete(block())
        delay(waitTimeMillis)
    }

    private suspend fun openSharedJdwpSession(
        session: AdbSession,
        deviceSerial: String,
        pid: Int
    ): SharedJdwpSession {
        val connectedDevice = waitForOnlineConnectedDevice(session, deviceSerial)
        val jdwpSession = JdwpSession.openJdwpSession(connectedDevice, pid, 100)
        return registerCloseable(SharedJdwpSession.create(jdwpSession, pid))
    }
}
