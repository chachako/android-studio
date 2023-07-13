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

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.getOrElse

/**
 * A [SynchronizedChannel] is similar to a [Channel] with the additional capability
 * of sending elements and waiting for a receiver to consume it and try to consume the
 * next one.
 *
 * @see Channel
 */
interface SynchronizedChannel<E>: SynchronizedSendChannel<E>, SynchronizedReceiveChannel<E>

/**
 * A [SynchronizedSendChannel] is similar to a [SendChannel] with the additional capability
 * of sending elements and waiting for a receiver to consume it and try to consume the
 * next one.
 *
 * @see SendChannel
 */
interface SynchronizedSendChannel<E> {
    /**
     * Sends the specified [element] to this channel, suspending the caller until the element
     * has been consumed **and** the next element is requested. This assumes a "receiver" on the
     * other side that consumes elements in a loop.
     *
     * @see SendChannel.send
     */
    suspend fun send(element: E)

    /**
     * Sends the specified [element] to this channel, suspending the caller until the element
     * has been received.
     *
     * Same as [SendChannel.send]
     */
    suspend fun sendNoWait(element: E)

    /**
     * @see SendChannel.close
     */
    fun close(cause: Throwable? = null): Boolean
}

/**
 * A [SynchronizedReceiveChannel] is similar to a [ReceiveChannel] but act as the "receiver" side
 * of a [SynchronizedSendChannel]
 *
 * @see ReceiveChannel
 */
interface SynchronizedReceiveChannel<E> {

    /**
     * @see ReceiveChannel.receiveCatching
     */
    suspend fun receiveCatching(): ChannelResult<E>
}

/**
 * Calls [block] on all elements received from this [SynchronizedReceiveChannel] until a
 * [ChannelResult.isFailure] is encountered. This method is effectively a convenient way to
 * consume all elements of a [SynchronizedReceiveChannel] until the channel is closed.
 */
suspend inline fun <T> SynchronizedReceiveChannel<T>.receiveAll(
    block: (T) -> Unit
): ChannelResult<T> {
    while (true) {
        val result = receiveCatching()
        val value = result.getOrElse {
            return@receiveAll result
        }
        block(value)
    }
}


/**
 * Creates a [SynchronizedChannel] instance
 */
fun <E> SynchronizedChannel(): SynchronizedChannel<E> {
    return SynchronizedChannelImpl()
}

internal class SynchronizedChannelImpl<E> : SynchronizedChannel<E> {
    private val channel = Channel<Any?>()

    override suspend fun sendNoWait(element: E) {
        channel.send(element)
    }

    override suspend fun send(element: E) {
        channel.send(element)
        channel.send(SKIP)
    }

    override fun close(cause: Throwable?): Boolean {
        return channel.close(cause)
    }

    override suspend fun receiveCatching(): ChannelResult<E> {
        while (true) {
            val result = channel.receiveCatching()
            if (result.isSuccess && result.getOrThrow() === SKIP) {
                continue
            }
            @Suppress("UNCHECKED_CAST")
            return result as ChannelResult<E>
        }
    }

    companion object {
        val SKIP = Any()
    }
}
