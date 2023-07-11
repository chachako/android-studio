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
package com.android.adblib.tools.debugging.utils

import com.android.adblib.ByteBufferAdbInputChannel
import com.android.adblib.readRemaining
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.tools.debugging.toByteArray
import com.android.adblib.utils.ResizableBuffer
import org.junit.Assert
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class AdbRewindableInputChannelTest {

    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    @Test
    fun testEmptyInputChannel() = runBlockingWithTimeout {
        // Act
        val channel = AdbRewindableInputChannel.empty()
        val outputBuffer = ResizableBuffer()
        val byteCount = channel.readRemaining(outputBuffer)
        channel.rewind()

        // Assert
        assertEquals(0, byteCount)
        assertEquals(0, outputBuffer.position)
    }

    @Test
    fun testForInputChannelSupportsReadingAllBytes() = runBlockingWithTimeout {
        // Prepare
        val buffer = ByteBuffer.allocate(2_048)
        for(i in 0 until 1_024) {
            buffer.put((i and 0xff).toByte())
        }
        buffer.flip()
        val adbInputChannel = ByteBufferAdbInputChannel(buffer)

        // Act
        val rewindablePacketChannel = AdbRewindableInputChannel.forInputChannel(adbInputChannel)
        val workBuffer = ResizableBuffer()
        val byteCount = rewindablePacketChannel.readRemaining(workBuffer, 20)

        // Assert
        assertEquals(1_024, byteCount)
        assertEquals(
            (0 until 1_024).map { (it and 0xff).toByte() },
            workBuffer.afterChannelRead(useMarkedPosition = false).toByteArray().toList()
        )
    }

    @Test
    fun testForInputChannelSupportsRewind() = runBlockingWithTimeout {
        // Prepare
        val buffer = ByteBuffer.allocate(2_048)
        for(i in 0 until 1_024) {
            buffer.put((i and 0xff).toByte())
        }
        buffer.flip()
        val adbInputChannel = ByteBufferAdbInputChannel(buffer)

        // Act
        val rewindablePacketChannel = AdbRewindableInputChannel.forInputChannel(adbInputChannel)
        val workBuffer1 = ResizableBuffer()
        val byteCount1 = rewindablePacketChannel.readRemaining(workBuffer1, 20)

        rewindablePacketChannel.rewind()
        val workBuffer2 = ResizableBuffer()
        val byteCount2 = rewindablePacketChannel.readRemaining(workBuffer2, 20)

        // Assert
        assertEquals(1_024, byteCount1)
        assertEquals(
            (0 until 1_024).map { (it and 0xff).toByte() },
            workBuffer1.afterChannelRead(useMarkedPosition = false).toByteArray().toList()
        )

        assertEquals(1_024, byteCount2)
        assertEquals(
            (0 until 1_024).map { (it and 0xff).toByte() },
            workBuffer2.afterChannelRead(useMarkedPosition = false).toByteArray().toList()
        )
    }

    @Test
    fun testForInputChannelDoesNotAllowRewindAfterClose() = runBlockingWithTimeout {
        // Prepare
        val buffer = ByteBuffer.allocate(2_048)
        for(i in 0 until 1_024) {
            buffer.put((i and 0xff).toByte())
        }
        buffer.flip()
        val adbInputChannel = ByteBufferAdbInputChannel(buffer)
        val rewindablePacketChannel = AdbRewindableInputChannel.forInputChannel(adbInputChannel)

        // Act
        rewindablePacketChannel.readRemaining(ResizableBuffer(), 20)
        rewindablePacketChannel.close()

        exceptionRule.expect(IllegalStateException::class.java)
        rewindablePacketChannel.rewind()

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testForByteBufferReadsAllData() = runBlockingWithTimeout {
        // Prepare
        val buffer = ByteBuffer.allocate(10)
        buffer.put(5)
        buffer.put(6)
        buffer.flip()

        // Act
        val channel = AdbRewindableInputChannel.forByteBuffer(buffer)
        val outputBuffer = ResizableBuffer()
        val byteCount = channel.readRemaining(outputBuffer)

        // Assert
        assertEquals(2, byteCount)
    }

    @Test
    fun testForByteBufferSupportsRewind() = runBlockingWithTimeout {
        // Prepare
        val buffer = ByteBuffer.allocate(10)
        buffer.put(5)
        buffer.put(6)
        buffer.flip()
        val channel = AdbRewindableInputChannel.forByteBuffer(buffer)
        channel.readRemaining(ResizableBuffer())
        channel.rewind()
        val workBuffer = ResizableBuffer()

        // Act
        workBuffer.clear()
        val byteCount = channel.readRemaining(workBuffer)
        channel.rewind()

        workBuffer.clear()
        val byteCount2 = channel.readRemaining(workBuffer)

        // Assert
        assertEquals(2, byteCount)
        assertEquals(2, byteCount2)
    }

    @Test
    fun testForByteBufferSupportsOffline() = runBlockingWithTimeout {
        // Prepare
        val workBuffer = ResizableBuffer()
        val buffer = ByteBuffer.allocate(10)
        buffer.put(5)
        buffer.put(6)
        buffer.flip()
        val channel = AdbRewindableInputChannel.forByteBuffer(buffer)
        channel.readRemaining(workBuffer)

        // Act
        val offlineChannel = channel.toOffline(workBuffer)

        // Assert
        val offlineChannelBytes = offlineChannel.toByteArray(2)
        assertArrayEquals(byteArrayOf(5, 6), offlineChannelBytes)
    }

    @Test
    fun testForInputChannelSupportsOffline() = runBlockingWithTimeout {
        // Prepare
        val workBuffer = ResizableBuffer()
        val buffer = ByteBuffer.allocate(10)
        buffer.put(5)
        buffer.put(6)
        buffer.flip()
        val channel = AdbRewindableInputChannel.forInputChannel(ByteBufferAdbInputChannel(buffer))
        channel.readRemaining(workBuffer)

        // Act
        val offlineChannel = channel.toOffline(workBuffer)

        // Assert
        val offlineChannelBytes = offlineChannel.toByteArray(2)
        assertArrayEquals(byteArrayOf(5, 6), offlineChannelBytes)
    }

    @Test
    fun testForCustomBufferInputChannelSupportsOffline() = runBlockingWithTimeout {
        // Prepare
        val workBuffer = ResizableBuffer()
        val buffer = ByteBuffer.allocate(10)
        buffer.put(5)
        buffer.put(6)
        buffer.flip()
        val channel = object: AdbRewindableInputChannel {
            private val delegateChannel = AdbRewindableInputChannel.forByteBuffer(buffer)
            override suspend fun rewind() {
                delegateChannel.rewind()
            }

            override suspend fun read(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
                return delegateChannel.read(buffer, timeout, unit)
            }

            override fun close() {
                delegateChannel.close()
            }
        }
        channel.readRemaining(workBuffer)

        // Act: Use "slow path" for custom AdbRewindableInputChannel
        val offlineChannel = channel.toOffline(workBuffer)

        // Assert
        val offlineChannelBytes = offlineChannel.toByteArray(2)
        assertArrayEquals(byteArrayOf(5, 6), offlineChannelBytes)
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        val result = ByteArray(this.remaining())
        this.get(result)
        this.position(this.position() - result.size)
        return result
    }
}
