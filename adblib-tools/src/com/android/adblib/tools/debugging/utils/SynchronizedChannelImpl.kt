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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.onSuccess
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap

internal class SynchronizedChannelImpl<E> : SynchronizedChannel<E> {

    /**
     * Underlying [Channel], contains either [E] or [SendToken] entries.
     */
    private val channel = Channel<Any?>()

    /**
     * [SendToken] for each pending [send] operation
     */
    private val pendingSends = ConcurrentHashMap.newKeySet<SendToken<E>>()
    private var closedForSend = false

    override suspend fun send(element: E) {
        throwIfClosedForSend()
        val token = SendToken(element)
        pendingSends.add(token)
        try {
            channel.send(token)
            token.deferred.await()
        } finally {
            pendingSends.remove(token)
        }
    }

    override suspend fun sendNoWait(element: E) {
        throwIfClosedForSend()
        channel.send(element)
    }

    override suspend fun receiveCatching(block: suspend (E) -> Unit): SynchronizedChannelResult {
        val receiveResult = channel.receiveCatching()
        receiveResult.onSuccess { anyValue ->
            processReceivedElement(anyValue, block)
            return@receiveCatching SynchronizedChannelResult.success()
        }.onClosed { throwable ->
            return@receiveCatching SynchronizedChannelResult.closed(throwable)
        }.onFailure {
            return@receiveCatching SynchronizedChannelResult.failure()
        }
        throw IllegalStateException("Invalid channel result value ($receiveResult)")
    }

    override suspend fun receive(block: suspend (E) -> Unit) {
        val anyValue = channel.receive()
        processReceivedElement(anyValue, block)
    }

    private suspend fun processReceivedElement(anyValue: Any?, block: suspend (E) -> Unit) {
        when (anyValue) {
            is SendToken<*> -> {
                // This corresponds to a [send] operation
                try {
                    @Suppress("UNCHECKED_CAST")
                    block(anyValue.element as E)
                } finally {
                    anyValue.deferred.complete(Unit)
                }
            }

            else -> {
                // This corresponds to a [sendNoWait] operation
                @Suppress("UNCHECKED_CAST")
                block(anyValue as E)
            }
        }
    }

    override fun close(cause: Throwable?): Boolean {
        closedForSend = true
        return channel.close(cause).also {
            val exception = cause
                ?: ClosedSendChannelException("${this::class.simpleName} has been closed")
            cancelPendingSends(exception)
        }
    }

    override fun cancel(cause: CancellationException?) {
        closedForSend = true
        channel.cancel(cause).also {
            val exception = cause
                ?: CancellationException("${this::class.simpleName} has been closed")
            cancelPendingSends(exception)
        }
    }

    private fun throwIfClosedForSend() {
        if (closedForSend) {
            throw ClosedSendChannelException("${this::class.simpleName} has been closed")
        }
    }

    private fun cancelPendingSends(exception: Throwable) {
        pendingSends.forEach { token ->
            token.deferred.completeExceptionally(exception)
        }
        pendingSends.clear()
    }

    private class SendToken<E>(val element: E) {

        val deferred = CompletableDeferred<Unit>()
    }
}
