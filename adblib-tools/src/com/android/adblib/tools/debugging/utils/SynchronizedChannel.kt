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
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException

/**
 * A [SynchronizedChannel] is similar to a [Channel] with the additional ability
 * of sending elements "synchronously", meaning a [send] operation waits until a receiver
 * is done consuming a value before completing.
 *
 * This variant of [Channel] is typically useful for mutable values, where it is important
 * for the caller of [send] to know the value [E] has been fully consumed by the receiver,
 * so the value can be safely mutated after [send] completes.
 *
 * @see Channel
 */
interface SynchronizedChannel<E> : SynchronizedSendChannel<E>, SynchronizedReceiveChannel<E>

/**
 * A [SynchronizedSendChannel] is similar to a [SendChannel] with the additional capability
 * of sending elements "synchronously" (see [SynchronizedChannel] documentation)
 *
 * @see SynchronizedChannel
 * @see SendChannel
 */
interface SynchronizedSendChannel<E> {

    /**
     * Sends the specified [element] to this channel "synchronously", i.e. suspending the caller
     * until the element has been consumed by the corresponding "receiver" in
     * [SynchronizedReceiveChannel.receiveCatching].
     */
    suspend fun send(element: E)

    /**
     * Sends the specified [element] to this channel, suspending the caller until the element
     * has been received **but** not fully processed. The behavior is identical to
     * [SendChannel.send].
     */
    suspend fun sendNoWait(element: E)

    /**
     * Closes this [SynchronizedSendChannel] by (conceptually) sending a special "close token"
     * over this channel, meaning that any pending [send] or [sendNoWait] operation is
     * **not cancelled** by a call to this function.
     *
     * To cancel [SynchronizedSendChannel.send] and [SynchronizedChannel.receiveCatching]
     * operation, use the [SynchronizedChannel.cancel] function.
     *
     * @see SynchronizedChannel.cancel
     * @see SendChannel.close
     */
    fun close(cause: Throwable? = null): Boolean
}

/**
 * A [SynchronizedReceiveChannel] is similar to a [ReceiveChannel] except that "receive"
 * operations can suspend senders until complete (see [SynchronizedChannel] documentation).
 *
 * @see SynchronizedChannel
 * @see SynchronizedSendChannel.send
 * @see SynchronizedSendChannel.sendNoWait
 * @see ReceiveChannel
 */
interface SynchronizedReceiveChannel<E> {

    /**
     * Receives an element from this channel, and pass it to [block] for processing.
     *
     * * If the element was sent with [SynchronizedChannel.send], the sender resumes when [block]
     * terminates.
     * * If the element was sent with [SynchronizedChannel.sendNoWait], the sender resumes
     * when [block] starts.
     *
     * Returns a [SynchronizedChannelResult]
     *
     * @see ReceiveChannel.receiveCatching
     */
    suspend fun receiveCatching(block: suspend (E) -> Unit): SynchronizedChannelResult

    /**
     * Retrieves an element from this channel and calls [block] if it's not empty, or suspends
     * the caller while the channel is empty, or throws a [ClosedReceiveChannelException] if the
     * channel is closed for `receive`.
     *
     * If the channel was closed because of an exception, it is called a _failed_ channel and this function
     * will throw the original [close][SendChannel.close] cause exception.
     *
     * * If the element was sent with [SynchronizedChannel.send], the sender resumes when [block]
     * terminates.
     * * If the element was sent with [SynchronizedChannel.sendNoWait], the sender resumes
     * when [block] starts.
     *
     * @see ReceiveChannel.receive
     */
    suspend fun receive(block: suspend (E) -> Unit)

    /**
     * Cancels reception of remaining elements from this channel with an optional [cause].
     * This function closes the channel and removes all buffered sent elements from it.
     * Any further call to [SynchronizedReceiveChannel.receiveCatching] or
     * [SynchronizedSendChannel.send] will immediately fail with a [CancellationException].
     *
     * @see ReceiveChannel.cancel
     */
    fun cancel(cause: CancellationException? = null)
}

/**
 * Similar to [ChannelResult] except the [SynchronizedChannelResult.success] case does not
 * contain an element value.
 */
@JvmInline
value class SynchronizedChannelResult @PublishedApi internal constructor(
    @PublishedApi internal val holder: Any?
) {

    val isSuccess: Boolean get() = holder !is Failed
    val isFailure: Boolean get() = holder is Failed
    val isClosed: Boolean get() = holder is Closed

    inline fun onFailure(action: (exception: Throwable?) -> Unit): SynchronizedChannelResult {
        if (holder is Failed) action(exceptionOrNull())
        return this
    }

    inline fun onClosed(action: (exception: Throwable?) -> Unit): SynchronizedChannelResult {
        if (holder is Closed) action(exceptionOrNull())
        return this
    }

    fun exceptionOrNull(): Throwable? = (holder as? Closed)?.cause

    internal open class Failed {

        override fun toString(): String = "Failed"
    }

    internal class Closed(@JvmField val cause: Throwable?) : Failed() {

        override fun equals(other: Any?): Boolean = other is Closed && cause == other.cause
        override fun hashCode(): Int = cause.hashCode()
        override fun toString(): String = "Closed($cause)"
    }

    @Suppress("NOTHING_TO_INLINE")
    companion object {

        @PublishedApi internal  val failed = Failed()
        @PublishedApi internal val success = Any()

        inline fun success(): SynchronizedChannelResult =
            SynchronizedChannelResult(success)

        inline fun failure(): SynchronizedChannelResult =
            SynchronizedChannelResult(failed)

        fun closed(cause: Throwable?): SynchronizedChannelResult =
            SynchronizedChannelResult(Closed(cause))
    }

    override fun toString(): String =
        when (holder) {
            is Closed -> holder.toString()
            else -> "Value($holder)"
        }

}

/**
 * Calls [block] on all elements received from this [SynchronizedReceiveChannel] until a
 * [SynchronizedChannelResult.isFailure] is encountered. This method offers a convenient way
 * to consume all elements of a [SynchronizedReceiveChannel] until the channel is closed.
 */
suspend inline fun <T> SynchronizedReceiveChannel<T>.receiveAllCatching(
    crossinline block: suspend (T) -> Unit
): SynchronizedChannelResult {
    while (true) {
        currentCoroutineContext().ensureActive()

        val result = receiveCatching { block(it) }
        if (result.isFailure) {
            return result
        }
    }
}

/**
 * Calls [block] on all elements received from this [SynchronizedReceiveChannel].
 *
 * The operation is _terminal_, i.e. completes only when an exception occurs in the
 * [SynchronizedReceiveChannel] or in [block].
 */
suspend inline fun <T> SynchronizedReceiveChannel<T>.receiveAll(
    crossinline block: suspend (T) -> Unit
) {
    while (true) {
        currentCoroutineContext().ensureActive()

        receive { block(it) }
    }
}

/**
 * Creates a [SynchronizedChannel] instance
 */
fun <E> SynchronizedChannel(): SynchronizedChannel<E> {
    return SynchronizedChannelImpl()
}
