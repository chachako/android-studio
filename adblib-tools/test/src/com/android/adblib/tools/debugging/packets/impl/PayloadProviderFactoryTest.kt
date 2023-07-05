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

import com.android.adblib.testing.FakeAdbSession
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.tools.debugging.packets.JdwpPacketConstants.PACKET_HEADER_LENGTH
import com.android.adblib.tools.debugging.packets.withPayload
import com.android.adblib.tools.debugging.utils.AdbBufferedInputChannel
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class PayloadProviderFactoryTest {

    @Test
    fun testEmptyPacket(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = FakeAdbSession()
        val factory = PayloadProviderFactory(session, maxInMemoryPayloadLength = 10)
        val sourcePacket = MutableJdwpPacket()
        sourcePacket.length = PACKET_HEADER_LENGTH
        sourcePacket.id = -10
        sourcePacket.cmdSet = 160
        sourcePacket.cmd = 240

        // Act
        val payloadProvider = sourcePacket.withPayload {
            factory.create(sourcePacket, it)
        }

        // Assert
        assertSame(PayloadProvider.emptyPayload(), payloadProvider)
    }

    @Test
    fun testSmallPacket(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = FakeAdbSession()
        val factory = PayloadProviderFactory(session, maxInMemoryPayloadLength = 10)
        val sourcePacket = MutableJdwpPacket()
        val payloadLength = 5
        sourcePacket.length = PACKET_HEADER_LENGTH + payloadLength
        sourcePacket.id = -10
        sourcePacket.cmdSet = 160
        sourcePacket.cmd = 240
        sourcePacket.payloadProvider =
            PayloadProvider.forByteBuffer(ByteBuffer.allocate(payloadLength))

        // Act
        val payloadProvider = sourcePacket.withPayload {
            factory.create(sourcePacket, it)
        }

        // Assert:
        // Implementation detail: the payload provider for small packet does not need
        // a "buffered" input channel, so we assert that condition.
        assertTrue(payloadProvider.acquirePayload() !is AdbBufferedInputChannel)
    }

    @Test
    fun testLargePacket(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = FakeAdbSession()
        val factory = PayloadProviderFactory(session, maxInMemoryPayloadLength = 10)
        val sourcePacket = MutableJdwpPacket()
        val payloadLength = 400
        sourcePacket.length = PACKET_HEADER_LENGTH + payloadLength
        sourcePacket.id = -10
        sourcePacket.cmdSet = 160
        sourcePacket.cmd = 240
        sourcePacket.payloadProvider =
            PayloadProvider.forByteBuffer(ByteBuffer.allocate(payloadLength))

        // Act
        val payloadProvider = sourcePacket.withPayload {
            factory.create(sourcePacket, it)
        }

        // Assert:
        // Implementation detail: the payload provider for large packets needs to use
        // a "buffered" input channel (to support "rewinding"), so we assert that condition.
        assertTrue(payloadProvider.acquirePayload() is AdbBufferedInputChannel)
    }
}
