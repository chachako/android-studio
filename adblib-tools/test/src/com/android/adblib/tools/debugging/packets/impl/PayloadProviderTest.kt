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
package com.android.adblib.tools.debugging.packets.impl

import com.android.adblib.ByteBufferAdbInputChannel
import com.android.adblib.readRemaining
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.utils.ResizableBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class PayloadProviderTest {

    @Test
    fun testEmptyPayload(): Unit = runBlockingWithTimeout {
        // Act
        val provider = PayloadProvider.emptyPayload()

        // Assert
        assertSame(PayloadProvider.emptyPayload(), provider.toOffline())
        assertEquals(0, provider.toByteArray().size)
        assertTrue(provider.isThreadSafeAndImmutable)
    }

    @Test
    fun testForByteBufferPayload(): Unit = runBlockingWithTimeout {
        // Prepare
        val bytes = byteArrayOf(4, 5, 6, 7)
        val buffer = ByteBuffer.allocate(bytes.size + 6)
        buffer.position(3)
        buffer.put(bytes)
        buffer.position(3)
        buffer.limit(3 + bytes.size)

        // Act
        val provider = PayloadProvider.forByteBuffer(buffer)

        // Assert
        assertTrue(provider.isThreadSafeAndImmutable)
        assertArrayEquals(bytes, provider.toByteArray())
        assertArrayEquals(
            "2nd access to payload should work too",
            bytes,
            provider.toByteArray()
        )
    }

    @Test
    fun testForByteBufferPayloadIsStillValidAfterClose(): Unit = runBlockingWithTimeout {
        // Prepare
        val bytes = byteArrayOf(4, 5, 6, 7)
        val buffer = ByteBuffer.allocate(bytes.size + 6)
        buffer.position(3)
        buffer.put(bytes)
        buffer.position(3)
        buffer.limit(3 + bytes.size)

        // Act
        val provider = PayloadProvider.forByteBuffer(buffer)
        provider.close()

        // Assert
        assertTrue(provider.isThreadSafeAndImmutable)
        assertArrayEquals(bytes, provider.toByteArray())
        assertArrayEquals(
            "2nd access to payload should work too",
            bytes,
            provider.toByteArray()
        )
    }

    @Test
    fun testForByteBufferPayloadIsStillValidAfterShutdown(): Unit = runBlockingWithTimeout {
        // Prepare
        val bytes = byteArrayOf(4, 5, 6, 7)
        val buffer = ByteBuffer.allocate(bytes.size + 6)
        buffer.position(3)
        buffer.put(bytes)
        buffer.position(3)
        buffer.limit(3 + bytes.size)

        // Act
        val provider = PayloadProvider.forByteBuffer(buffer)
        provider.shutdown(ResizableBuffer())

        // Assert
        assertTrue(provider.isThreadSafeAndImmutable)
        assertArrayEquals(bytes, provider.toByteArray())
        assertArrayEquals(
            "2nd access to payload should work too",
            bytes,
            provider.toByteArray()
        )
    }

    @Test
    fun testForInputChannelPayload(): Unit = runBlockingWithTimeout {
        // Prepare
        val bytes = byteArrayOf(4, 5, 6, 7)
        val buffer = ByteBuffer.allocate(bytes.size + 6)
        buffer.position(3)
        buffer.put(bytes)
        buffer.position(3)
        buffer.limit(3 + bytes.size)
        val inputChannel = ByteBufferAdbInputChannel(buffer)

        // Act
        val provider = PayloadProvider.forInputChannel(inputChannel)

        // Assert
        assertFalse(provider.isThreadSafeAndImmutable)
        assertArrayEquals(bytes, provider.toByteArray())
        assertArrayEquals(
            "2nd access to payload should work too",
            bytes,
            provider.toByteArray()
        )
    }

    private suspend fun PayloadProvider.toByteArray(): ByteArray {
        val workBuffer = ResizableBuffer()
        return withPayload {
            it.readRemaining(workBuffer)
            val buffer = workBuffer.afterChannelRead(false)
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            bytes
        }
    }
}
