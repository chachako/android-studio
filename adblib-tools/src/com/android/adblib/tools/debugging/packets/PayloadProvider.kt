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
package com.android.adblib.tools.debugging.packets

import com.android.adblib.AdbInputChannel
import com.android.adblib.skipRemaining
import com.android.adblib.tools.debugging.packets.ddms.withPayload
import com.android.adblib.utils.ResizableBuffer
import java.nio.ByteBuffer

/**
 * A provider of [AdbInputChannel] instances that can be read multiple times.
 */
interface PayloadProvider: AutoCloseable {

    /**
     * **Note: Do NOT use directly, use [withPayload] instead**
     *
     * Returns the [AdbInputChannel] wrapped by this [PayloadProvider].
     *
     * @throws IllegalStateException if the `payload` of this [PayloadProvider] instance is not
     *  available anymore.
     */
    suspend fun acquirePayload(): AdbInputChannel

    /**
     * **Note: Do NOT use directly, use [withPayload] instead**
     *
     * Releases the [AdbInputChannel] previously returned by [acquirePayload]
     */
    suspend fun releasePayload()

    /**
     * Shuts down this [PayloadProvider], releasing resources if necessary.
     */
    suspend fun shutdown(workBuffer: ResizableBuffer)

    companion object {

        fun emptyPayload(): PayloadProvider {
            return EmptyPayloadProvider
        }

        fun forByteBuffer(buffer: ByteBuffer): PayloadProvider {
            val channel = AdbBufferedInputChannel.forByteBuffer(buffer)
            return BufferedInputChannelPayloadProvider(channel)
        }

        fun forBufferedInputChannel(channel: AdbBufferedInputChannel): PayloadProvider {
            return BufferedInputChannelPayloadProvider(channel)
        }

        fun forInputChannel(channel: AdbInputChannel): PayloadProvider {
            val bufferedChannel = AdbBufferedInputChannel.forInputChannel(channel)
            return BufferedInputChannelPayloadProvider(bufferedChannel)
        }

        object EmptyPayloadProvider : PayloadProvider {

            override suspend fun acquirePayload(): AdbInputChannel {
                return AdbBufferedInputChannel.empty()
            }

            override suspend fun releasePayload() {
                // Nothing to do
            }

            override suspend fun shutdown(workBuffer: ResizableBuffer) {
                // Nothing to do
            }

            override fun close() {
                // Nothing to do
            }

            override fun toString(): String {
                return "Empty payload provider"
            }
        }

        private class BufferedInputChannelPayloadProvider(
            private val bufferedPayload: AdbBufferedInputChannel
        ) : PayloadProvider {
            private var closed = false

            override suspend fun acquirePayload(): AdbInputChannel {
                throwIfClosed()
                return bufferedPayload
            }

            override suspend fun releasePayload() {
                bufferedPayload.rewind()
            }

            override suspend fun shutdown(workBuffer: ResizableBuffer) {
                if (closed) {
                    return
                }
                closed = true
                bufferedPayload.finalRewind()
                bufferedPayload.skipRemaining(workBuffer)
            }

            override fun close() {
                closed = true
                bufferedPayload.close()
            }

            override fun toString(): String {
                return "BufferedInputChannelPayloadProvider(payload=$bufferedPayload, closed=$closed)"
            }

            private fun throwIfClosed() {
                if (closed) {
                    throw IllegalStateException("Payload is not available anymore because the provider has been closed")
                }
            }
        }
    }
}

/**
 * Invokes [block] with payload of this [PayloadProvider]. The payload is passed to [block]
 * as an [AdbInputChannel] instance that is valid only during the [block] invocation.
 */
suspend inline fun <R> PayloadProvider.withPayload(block: (AdbInputChannel) -> R): R {
    val payload = acquirePayload()
    return try {
        block(payload)
    } finally {
        releasePayload()
    }
}
