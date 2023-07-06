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
import com.android.adblib.skipRemaining
import com.android.adblib.tools.debugging.packets.ddms.withPayload
import com.android.adblib.tools.debugging.utils.AdbRewindableInputChannel
import com.android.adblib.tools.debugging.utils.SupportsOffline
import com.android.adblib.tools.debugging.utils.ThreadSafetySupport
import com.android.adblib.tools.debugging.utils.toOffline
import com.android.adblib.utils.ResizableBuffer
import kotlinx.coroutines.sync.Mutex
import java.nio.ByteBuffer

/**
 * A provider of [AdbInputChannel] instances that can be read multiple times.
 */
internal interface PayloadProvider: SupportsOffline<PayloadProvider>, AutoCloseable {

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
     *
     * **Note to implementors**: Similar to [Mutex.unlock] (for example), this function is
     * **not** a `suspend` function "by design", to ensure that a `finally` block that calls
     * [releasePayload] is executed even when a coroutine cancellation occurs in the
     * `try` block corresponding to the `finally` block.
     */
    fun releasePayload()

    /**
     * Shuts down this [PayloadProvider], releasing resources if necessary.
     */
    suspend fun shutdown(workBuffer: ResizableBuffer)

    /**
     * Clone this [PayloadProvider] into a [PayloadProvider] that is guaranteed to always have a
     * `payload` available.
     */
    override suspend fun toOffline(workBuffer: ResizableBuffer): PayloadProvider

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
            return ForByteBuffer(buffer)
        }

        /**
         * Creates a [PayloadProvider] wrapping the given [AdbInputChannel].
         */
        fun forInputChannel(channel: AdbInputChannel): PayloadProvider {
            val rewindableChannel = if (channel is AdbRewindableInputChannel) {
                channel
            } else {
                AdbRewindableInputChannel.forInputChannel(channel)
            }
            return ForInputChannel(rewindableChannel)
        }

        private object Empty : PayloadProvider, ThreadSafetySupport {

            override val isThreadSafeAndImmutable: Boolean
                get() = true // Can be safely shared across threads

            override suspend fun acquirePayload(): AdbInputChannel {
                return AdbRewindableInputChannel.empty()
            }

            override fun releasePayload() {
                // Nothing to do
            }

            override suspend fun shutdown(workBuffer: ResizableBuffer) {
                // Nothing to do
            }

            override suspend fun toOffline(workBuffer: ResizableBuffer): PayloadProvider {
                return this
            }

            override fun close() {
                // Nothing to do
            }

            override fun toString(): String {
                return "Empty payload provider"
            }
        }

        private class ForInputChannel(payload: AdbInputChannel) : PayloadProvider {
            private val rewindablePayload = if (payload is AdbRewindableInputChannel) {
                payload
            } else {
                AdbRewindableInputChannel.forInputChannel(payload)
            }
            private var closed = false

            override suspend fun acquirePayload(): AdbInputChannel {
                throwIfClosed()
                rewindablePayload.rewind()
                return rewindablePayload
            }

            override fun releasePayload() {
                // Nothing to do
            }

            override suspend fun shutdown(workBuffer: ResizableBuffer) {
                if (closed) {
                    return
                }
                closed = true
                rewindablePayload.finalRewind()
                rewindablePayload.skipRemaining(workBuffer)
            }

            override suspend fun toOffline(workBuffer: ResizableBuffer): PayloadProvider {
                throwIfClosed()

                return forInputChannel(rewindablePayload.toOffline(workBuffer))
            }

            override fun close() {
                closed = true
                rewindablePayload.close()
            }

            override fun toString(): String {
                return "${this::class.simpleName}(payload=$rewindablePayload, closed=$closed)"
            }

            private fun throwIfClosed() {
                if (closed) {
                    throw IllegalStateException("Payload is not available anymore because the provider has been closed")
                }
            }
        }

        /**
         * A thread-safe, cancellation safe, lock-free implementation of [PayloadProvider],
         * wrapping a [ByteBuffer].
         */
        private class ForByteBuffer(
            private val payload: ByteBuffer
        ): PayloadProvider, ThreadSafetySupport {

            override val isThreadSafeAndImmutable: Boolean
                get() = true // Can be safely shared across threads

            override suspend fun acquirePayload(): AdbInputChannel {
                // To ensure lock-free thread-safety, we create a new AdbInputChannel
                // instance for each call, but without copying the ByteBuffer contents
                // (just wrap it into another buffer sharing the same contents).
                return ByteBufferAdbInputChannel(payload.duplicate())
            }

            override fun releasePayload() {
                // Nothing to do
            }

            override suspend fun shutdown(workBuffer: ResizableBuffer) {
                // Nothing to do
            }

            override suspend fun toOffline(workBuffer: ResizableBuffer): PayloadProvider {
                // Return 'this' as we are thread-safe, lock-free and immutable
                return this
            }

            override fun close() {
                // Nothing to do
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

/**
 * Returns whether this [PayloadProvider] is thread-safe and immutable, meaning it can be
 * safely shared across threads and coroutines.
 *
 * @see ThreadSafetySupport.isThreadSafeAndImmutable
 */
internal val PayloadProvider.isThreadSafeAndImmutable: Boolean
    get() {
        return when (this) {
            is ThreadSafetySupport -> {
                isThreadSafeAndImmutable
            }

            else -> {
                false
            }
        }
    }
