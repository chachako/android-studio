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
import com.android.adblib.AdbInputChannelSlice
import com.android.adblib.AdbSession
import com.android.adblib.property
import com.android.adblib.thisLogger
import com.android.adblib.tools.AdbLibToolsProperties
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.payloadLength
import java.nio.ByteBuffer

/**
 * Creates instances of [PayloadProvider] optimized for specific
 * [payload length][JdwpPacketView.payloadLength] values.
 */
internal open class PayloadProviderFactory(
    session: AdbSession,
    private val maxInMemoryPayloadLength: Int =
        session.property(AdbLibToolsProperties.SHARED_JDWP_PACKET_IN_MEMORY_MAX_PAYLOAD_LENGTH)
) {

    private val logger = thisLogger(session)

    /**
     * Creates a [PayloadProvider] instance for the given [packetPayload] of [packet],
     * optimized according to the [payload length][JdwpPacketView.payloadLength].
     */
    suspend fun create(packet: JdwpPacketView, packetPayload: AdbInputChannel): PayloadProvider {
        val payloadLength = packet.payloadLength
        return when {
            payloadLength <= 0 -> {
                PayloadProvider.emptyPayload()
            }

            payloadLength <= maxInMemoryPayloadLength -> {
                // Load payload in memory
                val payload = ByteBuffer.allocate(payloadLength)
                packetPayload.readExactly(payload)
                payload.flip()
                // Wrap it in a thread-safe PayloadProvider
                PayloadProvider.forByteBuffer(payload)
            }

            else -> {
                assert(payloadLength > maxInMemoryPayloadLength)
                createLargePacketProvider(packet, packetPayload, payloadLength)
            }
        }.also {
            logger.verbose { "Created payload provider '$it' for a payload of $payloadLength byte(s)" }
        }
    }

    /**
     * Default implementation for "large" packets: create an [AdbInputChannelSlice] wrapping the
     * packet payload.
     */
    protected open fun createLargePacketProvider(
        packet: JdwpPacketView,
        packetPayload: AdbInputChannel,
        packetPayloadLength: Int
    ): PayloadProvider {
        // Create a "slice" input channel for the payload, then wrap it into a payload provider
        val channelSlice = AdbInputChannelSlice(packetPayload, packetPayloadLength)
        return PayloadProvider.forInputChannel(channelSlice)
    }
}
