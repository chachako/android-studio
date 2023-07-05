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

import com.android.adblib.AdbInputChannel
import com.android.adblib.ByteBufferAdbInputChannel
import com.android.adblib.EmptyAdbInputChannel
import com.android.adblib.readNBytes
import com.android.adblib.readRemaining
import com.android.adblib.testingutils.ByteBufferUtils
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.tools.debugging.packets.JdwpPacketConstants
import com.android.adblib.tools.debugging.packets.impl.JdwpCommands.CmdSet.SET_THREADREF
import com.android.adblib.tools.debugging.packets.impl.JdwpCommands.ThreadRefCmd.CMD_THREADREF_NAME
import com.android.adblib.tools.debugging.packets.isThreadSafeAndImmutable
import com.android.adblib.tools.debugging.packets.payloadLength
import com.android.adblib.tools.debugging.packets.withPayload
import com.android.adblib.tools.debugging.toByteArray
import com.android.adblib.tools.debugging.utils.AdbBufferedInputChannel
import com.android.adblib.tools.testutils.NanoTimeSpan
import com.android.adblib.tools.testutils.NanoTimeSpan.Companion.assertNanoTimeSpansAreSorted
import com.android.adblib.utils.ResizableBuffer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.nio.ByteBuffer

class EphemeralJdwpPacketTest {

    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    @Test
    fun testPacketCommandProperties() {
        // Prepare
        val sourcePacket = MutableJdwpPacket()
        sourcePacket.length = 1_000_000
        sourcePacket.id = -10
        sourcePacket.cmdSet = 160
        sourcePacket.cmd = 240

        // Act
        val packet = EphemeralJdwpPacket.fromPacket(sourcePacket, AdbBufferedInputChannel.empty())

        // Assert
        assertEquals(1_000_000, packet.length)
        assertEquals(-10, packet.id)
        assertEquals(0, packet.flags)
        assertTrue(packet.isCommand)
        assertFalse(packet.isReply)
        assertEquals(160, packet.cmdSet)
        assertEquals(240, packet.cmd)
    }

    @Test
    fun testPacketReplyProperties() {
        // Prepare
        val sourcePacket = MutableJdwpPacket()
        sourcePacket.length = 1_000_000
        sourcePacket.id = -10
        sourcePacket.isReply = true
        sourcePacket.errorCode = 1_000

        // Act
        val packet = EphemeralJdwpPacket.fromPacket(sourcePacket, AdbBufferedInputChannel.empty())

        // Assert
        assertEquals(1_000_000, packet.length)
        assertEquals(-10, packet.id)
        assertFalse(packet.isCommand)
        assertTrue(packet.isReply)
        assertEquals(1_000, packet.errorCode)
    }

    @Test
    fun testPacketThrowsIfInvalidLength() {
        // Act
        exceptionRule.expect(IllegalArgumentException::class.java)
        EphemeralJdwpPacket.Command(
            id = 5,
            length = 5,
            cmdSet = 0,
            cmd = 0,
            PayloadProvider.emptyPayload()
        )

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testPacketThrowsIfInvalidCmdSet() {
        // Act
        exceptionRule.expect(IllegalArgumentException::class.java)
        EphemeralJdwpPacket.Command(
            id = 5,
            length = 5,
            cmdSet = -10,
            cmd = 0,
            PayloadProvider.emptyPayload()
        )

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testPacketThrowsIfInvalidCmd() {
        // Act
        exceptionRule.expect(IllegalArgumentException::class.java)
        EphemeralJdwpPacket.Command(
            id = 5,
            length = 5,
            cmdSet = 0,
            cmd = -10,
            PayloadProvider.emptyPayload()
        )

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testPacketThrowsIfInvalidErrorCode() {
        // Act
        exceptionRule.expect(IllegalArgumentException::class.java)
        EphemeralJdwpPacket.Reply(
            id = 5,
            length = 5,
            errorCode = -10,
            PayloadProvider.emptyPayload()
        )

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testPacketThrowsOnErrorCodeIfCommand() {
        // Prepare
        val packet = EphemeralJdwpPacket.Command(
            id = 10,
            length = 20,
            cmdSet = 10,
            cmd = 10,
            payloadProvider = PayloadProvider.emptyPayload()
        )

        // Act
        exceptionRule.expect(IllegalStateException::class.java)
        @Suppress("UNUSED_VARIABLE")
        val errorCode = packet.errorCode

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testPacketThrowsOnCmdSetIfReply() {
        // Prepare
        val packet = EphemeralJdwpPacket.Reply(
            id = 10,
            length = 20,
            errorCode = 10,
            payloadProvider = PayloadProvider.emptyPayload()
        )

        // Act
        exceptionRule.expect(IllegalStateException::class.java)
        @Suppress("UNUSED_VARIABLE")
        val cmdSet = packet.cmdSet

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testPacketThrowsOnCmdIfReply() {
        // Prepare
        val packet = EphemeralJdwpPacket.Reply(
            id = 10,
            length = 20,
            errorCode = 10,
            payloadProvider = PayloadProvider.emptyPayload()
        )

        // Act
        exceptionRule.expect(IllegalStateException::class.java)
        @Suppress("UNUSED_VARIABLE")
        val cmd = packet.cmd

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testPacketSupportsAccessToPayload() = runBlockingWithTimeout {
        // Prepare
        val payloadBuffer = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 5))
        val packet = EphemeralJdwpPacket.Command(
            id = 5,
            length = JdwpPacketConstants.PACKET_HEADER_LENGTH + payloadBuffer.remaining(),
            cmdSet = 0,
            cmd = 0,
            payloadProvider = PayloadProvider.forByteBuffer(payloadBuffer)
        )

        // Act
        val buffer = packet.withPayload {
            it.readAsByteArray()
        }

        // Assert
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), buffer)
    }

    @Test
    fun testPacketSupportsCancellationOfPayloadRead() = runBlockingWithTimeout {
        // Prepare
        val payloadBuffer = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 5))
        val packet = EphemeralJdwpPacket.Command(
            id = 5,
            length = JdwpPacketConstants.PACKET_HEADER_LENGTH + payloadBuffer.remaining(),
            cmdSet = 0,
            cmd = 0,
            payloadProvider = PayloadProvider.forByteBuffer(payloadBuffer)
        )
        val first2BytesRead = CompletableDeferred<Unit>()

        // Act: Read 2 bytes from input channel, then cancel
        val job = launch {
            packet.withPayload {
                val workBuffer = ResizableBuffer()
                it.readNBytes(workBuffer, 2)
                first2BytesRead.complete(Unit)
                delay(50_000)
            }
        }
        first2BytesRead.await()
        job.cancel("Cancellation from test")

        // Read input channel again, we should get all the data (i.e. 5 bytes)
        val buffer = packet.withPayload {
            it.readAsByteArray()
        }

        // Assert
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), buffer)
    }

    @Test
    fun testPacketToStringForCommandPacket() {
        // Prepare
        val sourcePacket = MutableJdwpPacket()
        sourcePacket.length = 11
        sourcePacket.id = 10
        sourcePacket.cmdSet = SET_THREADREF.value
        sourcePacket.cmd = CMD_THREADREF_NAME.value

        // Act
        val packet = EphemeralJdwpPacket.fromPacket(sourcePacket, PayloadProvider.emptyPayload())
        val text = packet.toString()

        // Assert
        assertEquals(
            "EphemeralJdwpPacket(id=10, length=11, flags=0x00, isCommand=true, cmdSet=SET_THREADREF[11], cmd=CMD_THREADREF_NAME[1])",
            text
        )
    }

    @Test
    fun testPacketToStringForReplyPacket() {
        // Prepare
        val sourcePacket = MutableJdwpPacket()
        sourcePacket.length = 11
        sourcePacket.id = 10
        sourcePacket.isReply = true
        sourcePacket.errorCode = 67

        // Act
        val packet = EphemeralJdwpPacket.fromPacket(sourcePacket, PayloadProvider.emptyPayload())
        val text = packet.toString()

        // Assert
        assertEquals(
            "EphemeralJdwpPacket(id=10, length=11, flags=0x80, isReply=true, errorCode=DELETE_METHOD_NOT_IMPLEMENTED[67])",
            text
        )
    }

    @Test
    fun testPacketToOfflineAllowsAccessToPayloadAfterShutdown() = runBlockingWithTimeout {
        // Prepare
        val sourcePacket = MutableJdwpPacket()
        sourcePacket.length = 11
        sourcePacket.id = 10
        sourcePacket.isReply = true
        sourcePacket.errorCode = 67
        val buffer = ByteBuffer.allocate(5)
        buffer.put(1)
        buffer.put(2)
        buffer.put(3)
        buffer.put(4)
        buffer.put(5)
        buffer.flip()

        // Act
        val packet = EphemeralJdwpPacket.fromPacket(sourcePacket,
                                                    PayloadProvider.forByteBuffer(buffer))
        val offlinePacket = packet.toOffline()
        packet.shutdown()
        val readBuffer = offlinePacket.withPayload {
            it.readAsByteArray()
        }

        // Assert
        assertEquals(5, readBuffer.size)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), readBuffer)
    }

    @Test
    fun testPacketToOfflineReturnsSameInstanceIfFromByteBuffer() = runBlockingWithTimeout {
        // Prepare
        val sourcePacket = MutableJdwpPacket()
        sourcePacket.length = 11
        sourcePacket.id = 10
        sourcePacket.isReply = true
        sourcePacket.errorCode = 67
        val buffer = ByteBuffer.allocate(5)
        buffer.put(1)
        buffer.put(2)
        buffer.put(3)
        buffer.put(4)
        buffer.put(5)
        buffer.flip()
        val packet = EphemeralJdwpPacket.fromPacket(sourcePacket,
                                                    PayloadProvider.forByteBuffer(buffer))

        // Act
        val offlinePacket = packet.toOffline()

        // Assert
        assertSame(packet, offlinePacket)
    }

    @Test
    fun testPacketFromByteBufferIsThreadSafeAndImmutable() = runBlockingWithTimeout {
        // Prepare
        val sourcePacket = MutableJdwpPacket()
        sourcePacket.length = 11
        sourcePacket.id = 10
        sourcePacket.isReply = true
        sourcePacket.errorCode = 67
        val buffer = ByteBuffer.allocate(5)
        buffer.put(1)
        buffer.put(2)
        buffer.put(3)
        buffer.put(4)
        buffer.put(5)
        buffer.flip()

        // Act
        val packet = EphemeralJdwpPacket.fromPacket(sourcePacket,
                                                    PayloadProvider.forByteBuffer(buffer))

        // Assert
        assertTrue(packet.isThreadSafeAndImmutable)
    }

    @Test
    fun testPacketFromInputChannelIsNotThreadSafeAndImmutable() = runBlockingWithTimeout {
        // Prepare
        val sourcePacket = MutableJdwpPacket()
        sourcePacket.length = 11
        sourcePacket.id = 10
        sourcePacket.isReply = true
        sourcePacket.errorCode = 67
        val buffer = ByteBuffer.allocate(5)
        buffer.put(1)
        buffer.put(2)
        buffer.put(3)
        buffer.put(4)
        buffer.put(5)
        buffer.flip()
        val inputChannel = ByteBufferAdbInputChannel(buffer)

        // Act
        val packet = EphemeralJdwpPacket.fromPacket(sourcePacket,
                                                    PayloadProvider.forInputChannel(inputChannel))

        // Assert
        assertFalse(packet.isThreadSafeAndImmutable)
    }

    @Test
    fun testOfflinePacketToStringForCommandPacket() = runBlockingWithTimeout {
        // Prepare
        val sourcePacket = MutableJdwpPacket()
        sourcePacket.length = 11
        sourcePacket.id = 10
        sourcePacket.cmdSet = SET_THREADREF.value
        sourcePacket.cmd = CMD_THREADREF_NAME.value

        // Act
        val packet = EphemeralJdwpPacket
            .fromPacket(sourcePacket, PayloadProvider.emptyPayload())
            .toOffline()
        val text = packet.toString()

        // Assert
        assertEquals(
            "EphemeralJdwpPacket(id=10, length=11, flags=0x00, isCommand=true, cmdSet=SET_THREADREF[11], cmd=CMD_THREADREF_NAME[1])",
            text
        )
    }

    @Test
    fun testOfflinePacketToStringForCommandPacket2() = runBlockingWithTimeout {
        // Prepare
        val sourcePacket = MutableJdwpPacket()
        sourcePacket.length = 11
        sourcePacket.id = 10
        sourcePacket.cmdSet = SET_THREADREF.value
        sourcePacket.cmd = CMD_THREADREF_NAME.value

        // Act
        val packet = EphemeralJdwpPacket
            .fromPacket(sourcePacket, PayloadProvider.forInputChannel(EmptyAdbInputChannel()))
            .toOffline()
        val text = packet.toString()

        // Assert
        assertEquals(
            "OfflineJdwpPacket(id=10, length=11, flags=0x00, isCommand=true, cmdSet=SET_THREADREF[11], cmd=CMD_THREADREF_NAME[1])",
            text
        )
    }

    @Test
    fun testOfflinePacketToStringForReplyPacket() = runBlockingWithTimeout {
        // Prepare
        val sourcePacket = MutableJdwpPacket()
        sourcePacket.length = 11
        sourcePacket.id = 10
        sourcePacket.isReply = true
        sourcePacket.errorCode = 67

        // Act
        val packet = EphemeralJdwpPacket
            .fromPacket(sourcePacket, PayloadProvider.emptyPayload())
            .toOffline()
        val text = packet.toString()

        // Assert
        assertEquals(
            "EphemeralJdwpPacket(id=10, length=11, flags=0x80, isReply=true, errorCode=DELETE_METHOD_NOT_IMPLEMENTED[67])",
            text
        )
    }

    @Test
    fun testOfflinePacketToStringForReplyPacket2() = runBlockingWithTimeout {
        // Prepare
        val sourcePacket = MutableJdwpPacket()
        sourcePacket.length = 11
        sourcePacket.id = 10
        sourcePacket.isReply = true
        sourcePacket.errorCode = 67

        // Act
        val packet = EphemeralJdwpPacket
            .fromPacket(sourcePacket, PayloadProvider.forInputChannel(EmptyAdbInputChannel()))
            .toOffline()
        val text = packet.toString()

        // Assert
        assertEquals(
            "OfflineJdwpPacket(id=10, length=11, flags=0x80, isReply=true, errorCode=DELETE_METHOD_NOT_IMPLEMENTED[67])",
            text
        )
    }

    @Test
    fun testOfflinePacketPayloadIsThreadSafe() = runBlockingWithTimeout {
        // Prepare
        val payloadBuffer = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 5))
        val sourcePacket = EphemeralJdwpPacket.Command(
            id = 5,
            length = JdwpPacketConstants.PACKET_HEADER_LENGTH + payloadBuffer.remaining(),
            cmdSet = 0,
            cmd = 0,
            payloadProvider = PayloadProvider.forByteBuffer(payloadBuffer)
        )
        val taskCount = 100

        // Act
        val packet = sourcePacket.toOffline()
        val payloads = (1..taskCount).map {
                async {
                packet.withPayload {
                    it.toByteArray(packet.payloadLength)
                }
            }
        }.awaitAll()

        // Assert
        assertEquals(taskCount, payloads.size)
        payloads.forEach { payload ->
            assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), payload)
        }
    }

    @Test
    fun testOfflinePacketPayloadIsThreadSafeAndImmutable() = runBlockingWithTimeout {
        // Prepare
        val payloadBuffer = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 5))
        val sourcePacket = EphemeralJdwpPacket.Command(
            id = 5,
            length = JdwpPacketConstants.PACKET_HEADER_LENGTH + payloadBuffer.remaining(),
            cmdSet = 0,
            cmd = 0,
            payloadProvider = PayloadProvider.forByteBuffer(payloadBuffer)
        )

        // Act
        val packet = sourcePacket.toOffline()

        // Assert
        assertTrue(packet.isThreadSafeAndImmutable)
    }

    @Test
    fun testOfflinePacketPayloadAccessIsSerialized() = runBlockingWithTimeout {
        // Prepare
        val payloadBuffer = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 5))
        val sourcePacket = EphemeralJdwpPacket.Command(
            id = 5,
            length = JdwpPacketConstants.PACKET_HEADER_LENGTH + payloadBuffer.remaining(),
            cmdSet = 0,
            cmd = 0,
            payloadProvider = PayloadProvider.forInputChannel(ByteBufferAdbInputChannel(payloadBuffer))
        )
        val taskCount = 1_000

        // Act
        val packet = sourcePacket.toOffline()
        val timeSpans = (1..taskCount).map {
            async(Dispatchers.Default) {
                packet.withPayload {
                    val startNano = System.nanoTime()
                    it.toByteArray(packet.payloadLength)
                    delay(1)
                    NanoTimeSpan(startNano, System.nanoTime())
                }
            }
        }.awaitAll()

        // Assert
        assertNanoTimeSpansAreSorted(timeSpans, taskCount)
    }

    @Test
    fun testOfflinePacketSupportsCancellationOfPayloadRead() = runBlockingWithTimeout {
        // Prepare
        val payloadBuffer = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 5))
        val sourcePacket = EphemeralJdwpPacket.Command(
            id = 5,
            length = JdwpPacketConstants.PACKET_HEADER_LENGTH + payloadBuffer.remaining(),
            cmdSet = 0,
            cmd = 0,
            payloadProvider = PayloadProvider.forByteBuffer(payloadBuffer)
        )
        val packet = sourcePacket.toOffline()
        val first2BytesRead = CompletableDeferred<Unit>()

        // Act: Read 2 bytes from input channel, then cancel
        val job = launch {
            packet.withPayload {
                val workBuffer = ResizableBuffer()
                it.readNBytes(workBuffer, 2)
                first2BytesRead.complete(Unit)
                delay(50_000)
            }
        }
        first2BytesRead.await()
        job.cancel("Cancellation from test")

        // Read input channel again, we should get all the data (i.e. 5 bytes)
        val buffer = packet.withPayload {
            it.readAsByteArray()
        }

        // Assert
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), buffer)
    }

    private suspend fun AdbInputChannel.readAsByteArray(): ByteArray {
        val workBuffer = ResizableBuffer()
        readRemaining(workBuffer)
        return ByteBufferUtils.byteBufferToByteArray(workBuffer.afterChannelRead(false))
    }
}
