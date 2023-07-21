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

/**
 * A thread-safe reference counting implementation to any resource of type [T].
 *
 * * The [retain] method increments the reference count, after creating an instance of
 * the resource using [factory] if current reference count value was 0.
 *
 * * The [release] method decrements the reference count. If [T] is [AutoCloseable],
 * the resource is [closed][AutoCloseable.close] when the reference count reaches 0.
 *
 * This class implements [AutoCloseable] to allow releasing the resource even when there
 * are pending references, so that the resource can be released even if still in use.
 */
internal class ReferenceCountedFactory<T>(private val factory: () -> T) : AutoCloseable {

    private val lock = Any()

    private var refCount = 0

    private var value: T? = null

    /**
     * (**testing only**) Whether the resource is currently in use
     */
    internal val isRetained: Boolean
        get() = refCount > 0

    /**
     * Returns [value] after incrementing the reference count and calling [factory] if current
     * reference count value was 0.
     */
    fun retain(): T {
        synchronized(lock) {
            assert(refCount >= 0) { "Unmatched calls to retain/release" }
            refCount++
            return if (refCount == 1) {
                factory().also { newValue ->
                    value = newValue
                }
            } else {
                value ?: throw IllegalStateException("Value should not be null")
            }
        }
    }

    /**
     * Returns the reference count associated to [value] **after** decrementing it.
     * If the return value is `zero`, [AutoCloseable.close] has been called on [value].
     * A return value of `zero` is typically used to check if this was the last reference
     * to [value].
     */
    fun release(): Int {
        return synchronized(lock) {
            assert(refCount >= 0) { "Unmatched calls to retain/release" }
            refCount--
            if (refCount == 0) {
                (value as? AutoCloseable)?.close()
                value = null
            }
            refCount
        }
    }

    /**
     * Returns the reference count associated to [value] **after** decrementing it.
     * A return value of `zero` is typically used to check if this was the last reference
     * to [value].
     */
    fun releaseNoClose(): Int {
        return synchronized(lock) {
            assert(refCount >= 0) { "Unmatched calls to retain/release" }
            refCount--
            if (refCount == 0) {
                value = null
            }
            refCount
        }
    }

    /**
     * Calls [AutoCloseable.close] on [value], leaving the reference count untouched. Calling
     * [retain] after [close] is invalid, but calling [release] is still supported.
     */
    override fun close() {
        synchronized(lock) {
            (value as? AutoCloseable)
        }?.close()
    }
}
