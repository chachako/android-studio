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
package com.android.adblib.tools.debugging.packets.ddms

import com.android.adblib.ByteBufferAdbOutputChannel
import com.android.adblib.forwardTo
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.tools.debugging.packets.AdbBufferedInputChannel
import com.android.adblib.tools.debugging.packets.JdwpPacketConstants
import com.android.adblib.tools.debugging.packets.MutableJdwpPacket
import com.android.adblib.tools.debugging.packets.PayloadProvider
import com.android.adblib.tools.debugging.toByteArray
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import com.android.adblib.utils.ResizableBuffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class DdmsChunkViewTest : AdbLibToolsTestBase() {

    @Test
    fun test_DdmsChunkView_Write() = runBlockingWithTimeout {
        // Prepare
        val packet = createTestDdmsChunk()

        // Act
        val outputBuffer = ResizableBuffer()
        val output = ByteBufferAdbOutputChannel(outputBuffer)
        packet.writeToChannel(output)

        // Assert: Data should be from [0, outputBuffer.position[
        var index = 0
        assertEquals(12, outputBuffer.position)

        // Type
        assertEquals('R'.code.toByte(), outputBuffer[index++])
        assertEquals('E'.code.toByte(), outputBuffer[index++])
        assertEquals('A'.code.toByte(), outputBuffer[index++])
        assertEquals('Q'.code.toByte(), outputBuffer[index++])

        // Length
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(4.toByte(), outputBuffer[index++])

        // Contents
        assertEquals(128.toByte(), outputBuffer[index++])
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(255.toByte(), outputBuffer[index++])
        assertEquals(10.toByte(), outputBuffer[index])
    }

    @Test
    fun test_DdmsChunkView_Clone() = runBlockingWithTimeout {
        // Prepare
        val packet = createTestDdmsChunk()

        // Act
        val clone = packet.clone()

        // Assert: header
        assertEquals(packet.type, clone.type)
        assertEquals(packet.length, clone.length)

        // Assert: payload
        val outputBuffer = ResizableBuffer()
        val output = ByteBufferAdbOutputChannel(outputBuffer)
        packet.withPayload { it.forwardTo(output) }

        var index = 0
        assertEquals(128.toByte(), outputBuffer[index++])
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(255.toByte(), outputBuffer[index++])
        assertEquals(10.toByte(), outputBuffer[index])

        //TODO: assertTrue(clone.payload.rewind())
    }

    @Test
    fun test_JdwpPacketView_ddmsChunks_Works() = runBlockingWithTimeout {
        // Prepare
        val jdwpPacket = createJdwpPacketWithThreeDdmsChunks()

        // Act
        val payloads = mutableListOf<ByteArray>()
        val chunks = jdwpPacket.ddmsChunks().filter {
            payloads.add(it.payloadByteArray())
            true
        }.toList()

        // Assert
        assertEquals(3, chunks.size)
        assertEquals(3, payloads.size)

        assertEquals(DdmsChunkType.REAQ, chunks[0].type)
        assertEquals(4, chunks[0].length)
        assertArrayEquals(byteArrayOf(127, 128.toByte(), 255.toByte(), 0), payloads[0])

        assertEquals(DdmsChunkType.APNM, chunks[1].type)
        assertEquals(0, chunks[1].length)
        assertArrayEquals(byteArrayOf(), payloads[1])

        assertEquals(DdmsChunkType.HELO, chunks[2].type)
        assertEquals(8, chunks[2].length)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), payloads[2])
    }

    @Test
    fun test_JdwpPacketView_ddmsChunks_FlowIsRepeatable() = runBlockingWithTimeout {
        // Prepare
        val jdwpPacket = createJdwpPacketWithThreeDdmsChunks()
        jdwpPacket.ddmsChunks().toList()

        // Act
        val payloads = mutableListOf<ByteArray>()
        val chunks = jdwpPacket.ddmsChunks().filter {
            payloads.add(it.payloadByteArray())
            true
        }.toList()

        // Assert
        assertEquals(3, chunks.size)
        assertEquals(3, payloads.size)

        assertEquals(DdmsChunkType.REAQ, chunks[0].type)
        assertEquals(4, chunks[0].length)
        assertArrayEquals(byteArrayOf(127, 128.toByte(), 255.toByte(), 0), payloads[0])

        assertEquals(DdmsChunkType.APNM, chunks[1].type)
        assertEquals(0, chunks[1].length)
        assertArrayEquals(byteArrayOf(), payloads[1])

        assertEquals(DdmsChunkType.HELO, chunks[2].type)
        assertEquals(8, chunks[2].length)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), payloads[2])
    }

    @Test
    fun test_JdwpPacketView_ddmsChunks_ChunkPayloadThrowsAfterFlowIsTerminated() = runBlockingWithTimeout {
        // Prepare
        val jdwpPacket = createJdwpPacketWithThreeDdmsChunks()

        // Act
        val chunk = jdwpPacket.ddmsChunks().first()

        // Assert: withPayload should throw since flow is terminated
        exceptionRule.expect(IllegalStateException::class.java)
        chunk.withPayload {  }
    }

    @Test
    fun test_JdwpPacketView_ddmsChunks_ChunkPayloadIsValidAfterClone() = runBlockingWithTimeout {
        // Prepare
        val jdwpPacket = createJdwpPacketWithThreeDdmsChunks()

        // Act
        val chunk = jdwpPacket.ddmsChunks().map { it.clone() }.first()

        // Assert: withPayload should work since we cloned the chunk
        assertArrayEquals(byteArrayOf(127, 128.toByte(), 255.toByte(), 0), chunk.payloadByteArray())
    }

    private suspend fun createJdwpPacketWithThreeDdmsChunks(): MutableJdwpPacket {
        val chunk1 = createTestDdmsChunk(DdmsChunkType.REAQ, listOf(127, 128, 255, 0))
        val chunk2 = createTestDdmsChunk(DdmsChunkType.APNM, listOf())
        val chunk3 = createTestDdmsChunk(DdmsChunkType.HELO, listOf(1, 2, 3, 4, 5, 6, 7, 8))
        val outputBuffer = ResizableBuffer()
        val output = ByteBufferAdbOutputChannel(outputBuffer)
        chunk1.writeToChannel(output)
        chunk2.writeToChannel(output)
        chunk3.writeToChannel(output)
        val buffer = outputBuffer.forChannelWrite()

        val jdwpPacket = MutableJdwpPacket()
        jdwpPacket.length = JdwpPacketConstants.PACKET_HEADER_LENGTH + buffer.remaining()
        jdwpPacket.id = 10
        jdwpPacket.cmdSet = DdmsPacketConstants.DDMS_CMD_SET
        jdwpPacket.cmd = DdmsPacketConstants.DDMS_CMD
        jdwpPacket.payload = AdbBufferedInputChannel.forByteBuffer(buffer)
        return jdwpPacket
    }

    private fun createTestDdmsChunk(
        chunkType: DdmsChunkType = DdmsChunkType.REAQ,
        bytes: List<Int> = listOf(128, 0, 255, 10)
    ): DdmsChunkView {
        val ddmsChunk = MutableDdmsChunk()
        ddmsChunk.type = chunkType
        ddmsChunk.length = bytes.size
        ddmsChunk.payloadProvider = PayloadProvider.forBufferedInputChannel(bytesToBufferedInputChannel(bytes))
        return ddmsChunk
    }

    private fun bytesToBufferedInputChannel(bytes: List<Int>): AdbBufferedInputChannel {
        val buffer = ByteBuffer.allocate(bytes.size)
        for (value in bytes) {
            buffer.put(value.toByte())
        }
        buffer.flip()
        return AdbBufferedInputChannel.forByteBuffer(buffer)
    }

    private suspend fun DdmsChunkView.payloadByteArray() : ByteArray {
        return withPayload { input ->
            input.toByteArray(length)
        }
    }
}
