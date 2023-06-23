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
internal interface PayloadProvider: AutoCloseable {

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

        /**
         * Creates a [PayloadProvider] wrapping an empty `payload`.
         */
        fun emptyPayload(): PayloadProvider {
            return Empty
        }

        /**
         * Creates a [PayloadProvider] wrapping the contents of the given [ByteBuffer],
         * from [ByteBuffer.position] to [ByteBuffer.limit].
         *
         * **Note**
         *
         * The contents of [buffer] should not change after calling this method, to ensure
         * the [withPayload] method returns a valid `payload`.
         */
        fun forByteBuffer(buffer: ByteBuffer): PayloadProvider {
            return forInputChannel(AdbBufferedInputChannel.forByteBuffer(buffer))
        }

        /**
         * Creates a [PayloadProvider] wrapping the given [AdbInputChannel].
         */
        fun forInputChannel(channel: AdbInputChannel): PayloadProvider {
            val bufferedChannel = if (channel is AdbBufferedInputChannel) {
                channel
            } else {
                AdbBufferedInputChannel.forInputChannel(channel)
            }
            return ForInputChannel(bufferedChannel)
        }

        private object Empty : PayloadProvider {

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

        private class ForInputChannel(payload: AdbInputChannel) : PayloadProvider {
            private val bufferedPayload = if (payload is AdbBufferedInputChannel) {
                payload
            } else {
                AdbBufferedInputChannel.forInputChannel(payload)
            }
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
internal suspend inline fun <R> PayloadProvider.withPayload(block: (AdbInputChannel) -> R): R {
    val payload = acquirePayload()
    return try {
        block(payload)
    } finally {
        releasePayload()
    }
}
