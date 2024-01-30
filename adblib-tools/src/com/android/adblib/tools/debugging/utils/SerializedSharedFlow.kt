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
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

/**
 * A [Flow] similar to [SharedFlow] that ensures collectors are serialized.
 * See [MutableSerializedSharedFlow] for an implementation that allows
 * [emitting][MutableSerializedSharedFlow.emit] elements to the flow.
 *
 * Like [SharedFlow.collect], [SerializedSharedFlow.collect] never completes.
 */
internal interface SerializedSharedFlow<T>: Flow<T> {

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

    /**
     * Returns a flow that invokes the given [action] **after** this shared flow starts to
     * be collected (after the subscription is registered).
     *
     * The [action] is called before any value is emitted from the upstream flow to this
     * subscription but after the subscription is established. It is guaranteed that all emissions
     * to the upstream flow that happen inside or immediately after this `onSubscription` action
     * will be collected by this subscription.
     *
     * The receiver of the [action] is [FlowCollector], so `onSubscription` can emit
     * additional elements.
     *
     * Note: [source](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/on-subscription.html)
     *
     * @see SharedFlow.onSubscription
     */
    fun onSubscription(action: suspend FlowCollector<T>.() -> Unit): SerializedSharedFlow<T>
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

    override fun onSubscription(action: suspend FlowCollector<T>.() -> Unit): MutableSerializedSharedFlow<T>

    /**
     * Emits [value] to this shared flow, waiting for all active collectors to be done
     * collecting it. This method should be used, for example, if [value] is mutable
     * between calls to [emit].
     *
     * In the example below, it is safe to call `sb.toString()` when collecting the flow:
     *
     *     val flow = MutableSerializedSharedFlow<StringBuilder>()
     *     val sb = StringBuilder()
     *     sb.append("Foo")
     *     flow.emit(sb)
     *     sb.clear().append("Bar")
     *     flow.emit(sb)
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
 * Creates a [MutableSerializedSharedFlow] instance
 */
internal fun <T> MutableSerializedSharedFlow(): MutableSerializedSharedFlow<T> {
    return MutableSerializedSharedFlowImpl()
}

/**
 * Creates a [MutableSerializedSharedFlow] instance, calling [onEmit] during each
 * [MutableSerializedSharedFlow.emit] invocation.
 *
 * **Note: This class and the [onEmit] callback use are intended for testing purpose only.
 * Use [MutableSerializedSharedFlow] in production code.**
 */
@Suppress("FunctionName") // Mirroring coroutines API: Functions as constructors
internal fun <T> MutableSerializedSharedFlowForTesting(
    onEmit: (suspend (T) -> Unit)? = null
): MutableSerializedSharedFlow<T> {
    return MutableSerializedSharedFlowImpl(onEmit)
}

/**
 * Implementation of [MutableSerializedSharedFlow]
 */
private class MutableSerializedSharedFlowImpl<T>(
    private val onEmit: (suspend (T) -> Unit)? = null
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
    private val sharedFlow = MutableSharedFlow<Any?>()

    /**
     * [Mutex] to ensure [emit] is atomic
     */
    private val emitMutex = Mutex()

    override val subscriptionCount: StateFlow<Int>
        get() = sharedFlow.subscriptionCount

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        collectSharedFlow(collector, sharedFlow)
    }

    private suspend fun collectSharedFlow(
        collector: FlowCollector<T>,
        sharedFlow: SharedFlow<Any?>
    ): Nothing {
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

        emitMutex.withLock {
            // We emit the value, then the "SKIP" constant to ensure all collectors are
            // done processing the value before returning from this "emit" call
            // See https://github.com/Kotlin/kotlinx.coroutines/issues/2603#issuecomment-808859170
            //
            // Note on thread-safety: We need to prevent 2 concurrent threads from emitting
            // concurrently, so that each [emit] operation ends only when the [SKIP] value
            // has been received by the collector(s).
            sharedFlow.emit(value)
            onEmit?.invoke(value)
            sharedFlow.emit(SKIP)
        }
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

    override fun onSubscription(action: suspend FlowCollector<T>.() -> Unit): MutableSerializedSharedFlow<T> {
        return SubscribedSerializedSharedFlow(this, sharedFlow.onSubscription(action))
    }

    private class SubscribedSerializedSharedFlow<T>(
        private val serializedSharedFlow: MutableSerializedSharedFlowImpl<T>,
        private val sharedFlowWithSubscription: SharedFlow<Any?>
    ) : MutableSerializedSharedFlow<T> {

        override val subscriptionCount: StateFlow<Int>
            get() = serializedSharedFlow.subscriptionCount

        override suspend fun collect(collector: FlowCollector<T>): Nothing {
            serializedSharedFlow.collectSharedFlow(collector, sharedFlowWithSubscription)
        }

        override fun onSubscription(action: suspend FlowCollector<T>.() -> Unit): MutableSerializedSharedFlow<T> {
            return SubscribedSerializedSharedFlow(
                serializedSharedFlow,
                sharedFlowWithSubscription.onSubscription(action)
            )
        }

        override suspend fun emit(value: T) {
            serializedSharedFlow.emit(value)
        }

        override fun close() {
            serializedSharedFlow.close()
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
