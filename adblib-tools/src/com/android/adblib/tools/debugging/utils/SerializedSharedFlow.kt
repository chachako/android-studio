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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlin.coroutines.CoroutineContext

/**
 * A [Flow] similar to [SharedFlow] that ensures collectors are serialized.
 * See [MutableSerializedSharedFlow] for an implementation that allows
 * [emitting][MutableSerializedSharedFlow.emit] elements to the flow.
 *
 * Like [SharedFlow.collect], [SerializedSharedFlow.collect] never completes.
 */
internal interface SerializedSharedFlow<T> : Flow<T> {

    /**
     * A snapshot of the replay cache
     *
     * @see SharedFlow.replayCache
     */
    val replayCache: List<T>

    /**
     * Accepts the given [collector] and [emits][FlowCollector.emit] values into it.
     * To emit values from a shared flow into a specific collector, either
     * `collector.emitAll(flow)` or `collect { ... }` SAM-conversion can be used.
     *
     * Like [SharedFlow.collect], **a serialized shared flow never completes**.
     * A call to [Flow.collect] or any other terminal operator on a serialized shared flow never
     * completes normally.
     */
    override suspend fun collect(collector: FlowCollector<T>): Nothing
}

/**
 * A [SerializedSharedFlow] similar to [MutableSharedFlow] with the following characteristics:
 * * [emit] ensures all active collectors are done processing the value before [emit] completes.
 * See [kotlinx.coroutines issue 2603: SharedFlow.emit() doesn't wait for a subscriber to complete collecting](https://github.com/Kotlin/kotlinx.coroutines/issues/2603)
 * for an explanation on how this is different from [MutableSharedFlow].
 * * Buffering is not supported and the only supported buffer overflow strategy is
 * [BufferOverflow.SUSPEND].
 * * [AutoCloseable.close] can be used to cancel existing active collectors as well as future
 * collectors.
 */
internal interface MutableSerializedSharedFlow<T> : SerializedSharedFlow<T>,
                                                    FlowCollector<T>,
                                                    AutoCloseable {

    /**
     * The number of subscribers (active collectors) to this shared flow.
     *
     * @see MutableSharedFlow.subscriptionCount
     */
    val subscriptionCount: StateFlow<Int>

    /**
     * Emits a [value] to this shared flow, invoke all active collectors sequentially
     * and returns when all active collectors are done collecting the [value].
     *
     * Note: This is different from [MutableSharedFlow.emit], where all collectors are
     * invoke concurrently where the [MutableSharedFlow.emit] may complete before all
     * collectors are done collecting the [value]
     */
    override suspend fun emit(value: T)

    /**
     * Closes this [SerializedSharedFlow], cancelling all active collectors and preventing
     * any further uses of this [SerializedSharedFlow] instance.
     */
    override fun close()
}

/**
 * Creates a [MutableSerializedSharedFlow] given the maximum number of [replay] packets.
 */
internal fun <T> MutableSerializedSharedFlow(replay: Int = 0): MutableSerializedSharedFlow<T> {
    return MutableSerializedSharedFlowImpl(replay)
}

/**
 * Implementation of [MutableSerializedSharedFlow]
 */
private class MutableSerializedSharedFlowImpl<T>(
    replay: Int
) : MutableSerializedSharedFlow<T>, AutoCloseable {

    /**
     * Lock for [closed] and [activeCollectors]
     */
    private val lock = Any()

    private var closed = false

    /**
     * Keep track of active collectors so that they can be cancelled on [close].
     */
    private val activeCollectors = mutableListOf<CoroutineContext>()

    /**
     * The underlying [MutableSharedFlow], used to emit `['value:T', SKIP]` sequences for each
     * `'value:T'` emitted to this flow.
     */
    private val sharedFlow = MutableSharedFlow<Any?>(replay = replay * 2)

    override val replayCache: List<T>
        get() {
            return sharedFlow.replayCache
                .mapNotNull {
                    @Suppress("UNCHECKED_CAST")
                    it as? T
                }.toList()
        }

    override val subscriptionCount: StateFlow<Int>
        get() = sharedFlow.subscriptionCount

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        val context = currentCoroutineContext()
        synchronized(lock) {
            cancelIfClosed()
            activeCollectors.add(context)
        }
        try {
            sharedFlow.collect {
                when {
                    it === SKIP -> {
                        // Nothing to do, skip it
                    }

                    else -> {
                        @Suppress("UNCHECKED_CAST")
                        collector.emit(it as T)
                    }
                }
            }
        } finally {
            synchronized(lock) {
                activeCollectors.remove(context)
            }
        }
    }

    override suspend fun emit(value: T) {
        cancelIfClosed()

        // We emit the value, then the "SKIP" constant to ensure all collectors are
        // done processing the value before returning from this "emit" call
        // See https://github.com/Kotlin/kotlinx.coroutines/issues/2603#issuecomment-808859170
        //
        // Note on thread-safety: If 2 threads calls this method concurrently with respectively
        // [value1] and [value2], the order of packets may not be [value1, skip, value2, skip],
        // but something like [value2, value1, skip, skip].
        // Even though this is non-deterministic, it stills guarantee that no thread will exit
        // this method before their respective value ([value1] or [value2]) has been receive and processed
        // by all receivers.
        sharedFlow.emit(value)
        sharedFlow.emit(SKIP)
    }

    override fun close() {
        val toClose = synchronized(lock) {
            closed = true
            activeCollectors.toList().also {
                activeCollectors.clear()
            }
        }
        toClose.forEach {
            it.cancel(closedException())
        }
    }

    private fun cancelIfClosed() {
        if (closed) {
            throw closedException()
        }
    }

    private fun closedException(): CancellationException {
        val message = "${MutableSerializedSharedFlow::class.simpleName} has been closed"
        return CancellationException(message)
    }

    companion object {
        private object SKIP
    }
}
