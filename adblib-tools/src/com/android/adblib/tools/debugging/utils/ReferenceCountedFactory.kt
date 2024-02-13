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

import com.android.adblib.AutoShutdown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * A thread-safe reference counting implementation to any resource of type [T].
 * The resource instance is typically accessed by calling the [withResource] method.
 *
 * * The [retain] method increments the reference count, after creating an instance of
 * the resource using [factory] if current reference count value was 0.
 *
 * * The [release] method decrements the reference count. If [T] is [AutoCloseable],
 * the resource is [shutdown][AutoShutdown.shutdown] and/or [closed][AutoCloseable.close]
 * when the reference count reaches 0.
 *
 * This class implements [AutoCloseable] to allow releasing the resource even when there
 * are pending references, so that the resource can be released even if still in use.
 */
internal class ReferenceCountedFactory<T>(private val factory: suspend () -> T) : AutoCloseable {

    /**
     * [Mutex] used to ensure calls to [retain] and [release] are serialized across coroutines.
     */
    private val mutex = Mutex()

    /**
     * CoroutineScope used to execute [retain] and [release], so that the [close]
     * method can effectively cancel all pending (and incoming) [retain]/[release] calls
     * with a simple [CoroutineScope.cancel] call.
     */
    private val executionScope = CoroutineScope(SupervisorJob())

    private val refCountAndValue = RefCountAndValue<T>()

    /**
     * Invokes [block] with an instance of [T] that is valid during the duration of [block].
     * If called concurrently, the same instance of [T] is re-used for each concurrent call.
     *
     * Note:
     * * If a new instance of [T] is needed, [factory] is invoked before calling [block]
     * * If the newly created instance of [T] is not needed anymore after calling [block] (i.e.
     * there are no pending concurrent calls to this method), [AutoShutdown.shutdown]
     * and [AutoCloseable.close] are invoked on the [T] instance, if it implements the
     * corresponding interfaces. In case of [CancellationException] during [block], only
     * [AutoCloseable.close] is invoked.
     */
    suspend inline fun <R> withResource(block: (T) -> R): R {
        var throwable: Throwable? = null
        val value = retain()
        return try {
            block(value)
        } catch(t: Throwable) {
            throwable = t
            throw t
        } finally {
            // Handling cancellation should never involve `suspend` code, so we
            // have to release the resource "promptly"
            if (throwable is CancellationException) {
                releasePromptly(value)
            } else {
                release(value)
            }
        }
    }

    /**
     * Calls [AutoCloseable.close] on [refCountAndValue] and cancels all pending calls
     * to [withResource], [retain] and [release].
     *
     * Calling [retain] or [release] after calling [close] results in a [CancellationException]
     * to be thrown.
     */
    override fun close() {
        // Cancel all pending (and incoming) calls to [retain] and [release]
        executionScope.cancel("${this::class.java.simpleName} is closed")
        refCountAndValue.close()
    }

    /**
     * Returns [value][T] after incrementing the reference count and calling [factory] if current
     * reference count value was 0.
     */
    suspend fun retain(): T {
        refCountAndValue.increment()
        return try {
            ensureValue()
        } catch (t: Throwable) {
            // Note: `ensureValue()` sets the internal value only when it succeeds,
            // so calling `decrementAndReturnIfLast()` here will always only decrement the
            // reference count (i.e. there should be no value returned)
            val currentValue = refCountAndValue.decrementAndReturnIfLast()
            assert(currentValue == null) {
                "Given `ensureValue()` failed, `decrementAndReturnIfLast()` should have returned `null`"
            }
            throw t
        }
    }

    /**
     * Decrements the reference count associated to [value][refCountAndValue], calling
     * [AutoShutdown.shutdown] and [AutoCloseable.close] on [retainedValue] if this is the
     * last reference count.
     */
    suspend fun release(retainedValue: T) {
        // Run inside executionScope to ensure pending (and incoming) calls to retain/release
        // are (promptly) cancelled.
        executionScope.async {
            // Use mutex to ensure only one thread releases the value
            mutex.withLock {
                refCountAndValue.decrementAndReturnIfLast()?.also {
                    assert(it === retainedValue) {
                        "The current value should always be the same object as the retained value"
                    }
                    // Call `shutdown` then `close`
                    try {
                        (it as? AutoShutdown)?.shutdown()
                    } finally {
                        closeValue(it)
                    }
                }
            }
        }.await()
    }

    /**
     * Decrements the reference count associated to [value][refCountAndValue], calling
     * [AutoCloseable.close] (but *not* calling [AutoShutdown.shutdown]) on [retainedValue] if
     * this is the last reference count.
     */
    fun releasePromptly(retainedValue: T) {
        refCountAndValue.decrementAndReturnIfLast()?.also {
            assert(retainedValue === it) {
                "The current value should always be the same object as the retained value"
            }
            closeValue(it)
        }
    }

    private suspend fun ensureValue(): T {
        // Run inside executionScope to ensure pending (and incoming) calls to this method
        // are (promptly) cancelled if `close` is (or has already been) called.
        return executionScope.async {
            // Use mutex to ensure calls to `factory` are serialized
            mutex.withLock {
                // Coroutine execution is serialized here, meaning the value stored in
                // `refCountAndValue` is guaranteed to remain `null` until the current
                // coroutine sets it (if `factory` succeeds).
                // In case of cancellation (or exception) in `factory`, the value is never set
                // so that another incoming call starts from a "blank" slate.
                refCountAndValue.ensureValue { factory() }
            }
        }.await()
    }


    private fun closeValue(currentValue: T) {
        (currentValue as? AutoCloseable)?.close()
    }

    private class RefCountAndValue<T> {
        private val lock = Any()

        private var refCount: Int = 0

        @Volatile
        private var value: T? = null

        suspend inline fun ensureValue(
            // Make `suspend` explicit because callers always use a suspending `block`, and
            // we want to ensure the Kotlin compiler does not allow wrapping its invocation
            // inside a `synchronized` expression inside this method.
            @Suppress("REDUNDANT_INLINE_SUSPEND_FUNCTION_TYPE")
            block: suspend () -> T
        ): T {
            assert(refCount > 0) {
                "ensureValue() should only be called when refCount > 0"
            }
            // Note: We don't use `lock` here because `block()` is a suspending function
            return value ?: run {
                block().also { newValue ->
                    assert(value == null) {
                        "The value should always be `null` at this point, since " +
                                "the caller is responsible for serializing calls to this method"
                    }
                    value = newValue
                }
            }
        }

        fun increment(): Int {
            return synchronized(lock) {
                assert(refCount >= 0) {
                    "increment() should never be called when refCount is < 0"
                }
                ++refCount
            }
        }

        /**
         * Decrement ref count and returns [T] if this was the last reference
         */
        fun decrementAndReturnIfLast(): T? {
            return synchronized(lock) {
                assert(refCount >= 1) {
                    "decrementAndReturnIfLast() should only be called when refCount is >= 0"
                }
                if (--refCount == 0) {
                    value.also {
                        value = null
                    }
                } else {
                    null
                }
            }
        }

        fun close() {
            synchronized(lock) {
                value.also {
                    value = null
                }
            }?.also {
                // Call `close()` outside lock to prevent potential deadlocks
                (it as? AutoCloseable)?.close()
            }
        }
    }
}
