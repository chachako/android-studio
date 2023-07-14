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
package com.android.adblib.tools.debugging.utils

import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CancellationException

class SerializedChannelTest : AdbLibToolsTestBase() {

    @Test
    fun testSendNoWaitWorks() = runBlockingWithTimeout {
        // Prepare
        val channel = SynchronizedChannel<MutableIntWrapper>()

        // Act
        val intValue = async {
            channel.unsafeReceive()
        }
        channel.sendNoWait(MutableIntWrapper(5))

        // Assert
        assertEquals(5, intValue.await().mutableValue)
    }

    @Test
    fun testSendNoWaitWithNoReceiveBlocks() = runBlockingWithTimeout {
        // Prepare
        val channel = SynchronizedChannel<MutableIntWrapper>()

        // Act: There is only one "receive", so "send" blocks
        val result = withTimeoutOrNull(100) {
            channel.sendNoWait(MutableIntWrapper(5))
        }

        // Assert
        assertNull(result)
    }

    @Test
    fun testSendWithNoReceiveBlocks() = runBlockingWithTimeout {
        // Prepare
        val channel = SynchronizedChannel<MutableIntWrapper>()

        // Act: There is only one "receive", so "send" blocks
        val result = withTimeoutOrNull(100) {
            channel.send(MutableIntWrapper(5))
        }

        // Assert
        assertNull(result)
    }

    @Test
    fun testSendBlocks() = runBlockingWithTimeout {
        // Prepare
        val channel = SynchronizedChannel<MutableIntWrapper>()

        // Act: There is only one "receive", so 2nd "send" blocks
        val job = async {
            channel.unsafeReceive()
        }
        channel.send(MutableIntWrapper(5))
        val result = withTimeoutOrNull(100) {
            channel.send(MutableIntWrapper(6))
        }
        job.join()

        // Assert
        assertNull(result)
    }

    @Test
    fun testSendWaitsForReceiverToComplete() = runBlockingWithTimeout {
        // Prepare
        val channel = SynchronizedChannel<MutableIntWrapper>()

        // Act: There is only one "receive", so 2nd "send" blocks
        val job = async {
            channel.receive {
                delay(10)
                it.mutableValue = 101
            }
        }
        val element = MutableIntWrapper(10)
        channel.send(element)
        job.join()

        // Assert
        assertEquals(101, element.mutableValue)
    }

    @Test
    fun testSendUnblocksAfterSecondReceive() = runBlockingWithTimeout {
        // Prepare
        val channel = SynchronizedChannel<MutableIntWrapper>()

        // Act: There is only one "receive", so "send" blocks
        val intValue = async {
            channel.unsafeReceive()
            channel.unsafeReceive()
        }
        channel.send(MutableIntWrapper(5))
        channel.send(MutableIntWrapper(10))

        // Assert
        assertEquals(10, intValue.await().mutableValue)
    }

    @Test
    fun testReceiveAllWorks() = runBlockingWithTimeout {
        // Prepare
        val channel = SynchronizedChannel<MutableIntWrapper>()

        // Act: There is only one "receive", so "send" blocks
        val values = mutableListOf<MutableIntWrapper>()
        val job = async {
            channel.receiveAllCatching {
                values.add(it)
                if (values.size == 4) {
                    cancel("Foo")
                }
            }
        }
        channel.send(MutableIntWrapper(5))
        channel.send(MutableIntWrapper(6))
        channel.send(MutableIntWrapper(7))
        channel.send(MutableIntWrapper(8))

        job.join() // "join" does not throw on cancellation

        // Assert
        assertEquals(4, values.size)
        assertEquals(5, values[0].mutableValue)
        assertEquals(6, values[1].mutableValue)
        assertEquals(7, values[2].mutableValue)
        assertEquals(8, values[3].mutableValue)
    }

    @Test
    fun testCancelWorksForReceiveAll() = runBlockingWithTimeout {
        // Prepare
        val channel = SynchronizedChannel<MutableIntWrapper>()

        // Act: There is only one "receive", so "send" blocks
        val values = mutableListOf<MutableIntWrapper>()
        val job = async {
            channel.receiveAllCatching {
                values.add(it)
            }
        }
        channel.send(MutableIntWrapper(5))
        channel.send(MutableIntWrapper(6))
        channel.send(MutableIntWrapper(7))
        channel.send(MutableIntWrapper(8))
        channel.cancel()

        job.await()

        // Assert
        assertEquals(4, values.size)
        assertEquals(5, values[0].mutableValue)
        assertEquals(6, values[1].mutableValue)
        assertEquals(7, values[2].mutableValue)
        assertEquals(8, values[3].mutableValue)
    }

    @Test
    fun testCancelWorksForPendingSend() = runBlockingWithTimeout {
        // Prepare
        val channel = SynchronizedChannel<MutableIntWrapper>()

        // Act: There is only one "receive", so "send" blocks
        val job = async {
            channel.send(MutableIntWrapper(5))
        }
        delay(100)
        channel.cancel()

        exceptionRule.expect(CancellationException::class.java)
        job.await()

        // Assert
        fail("Should not reach")
    }

    @Test
    fun testCancelWorksForSlowReceiver() = runBlockingWithTimeout {
        // Prepare
        val channel = SynchronizedChannel<MutableIntWrapper>()

        // Act: There is only one "receive", so "send" blocks
        val deferredReceive = CompletableDeferred<Unit>()
        val sendJob = async {
            channel.send(MutableIntWrapper(5))
        }
        launch {
            channel.receiveCatching {
                deferredReceive.complete(Unit)
                delay(10_000)
            }
        }
        deferredReceive.await()
        channel.cancel()

        exceptionRule.expect(CancellationException::class.java)
        sendJob.await()

        // Assert
        fail("Should not reach")
    }

    @Test
    fun testCancelDoesNotCancelSlowReceiver() = runBlockingWithTimeout {
        // Prepare
        val channel = SynchronizedChannel<MutableIntWrapper>()

        // Act: There is only one "receive", so "send" blocks
        val deferredReceive = CompletableDeferred<Unit>()
        launch {
            channel.send(MutableIntWrapper(5))
        }
        val receiveJob = async {
            channel.receiveCatching {
                deferredReceive.complete(Unit)
                delay(500)
            }
        }
        deferredReceive.await()
        channel.cancel()
        val result = receiveJob.await()

        // Assert
        assertTrue(result.isSuccess)
    }

    private suspend fun <E> SynchronizedReceiveChannel<out E>.unsafeReceive(): E {
        var result: Result<E>? = null
        receiveCatching {
            result = Result.success(it)
        }.onFailure {
            throw it ?: IllegalStateException("Unexpected channel failure")
        }
        return result!!.getOrThrow()
    }

    private class MutableIntWrapper(var mutableValue: Int = 0)
}
