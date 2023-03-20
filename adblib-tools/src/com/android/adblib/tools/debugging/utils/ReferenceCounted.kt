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

import java.util.concurrent.atomic.AtomicInteger

/**
 * A thread-safe reference counting implementation to any resource of type [T].
 * If [T] is [AutoCloseable], the resource is [closed][AutoCloseable.close] when
 * the reference count reaches `zero`.
 *
 * This class implements [AutoCloseable] to allow releasing the resource even when there
 * are pending references, so that the resource can be released even if still in use.
 */
internal class ReferenceCounted<T>(private val value: T) : AutoCloseable {

    private val refCount = AtomicInteger()

    /**
     * Returns [value] after incrementing its associated reference count.
     */
    fun retain(): T {
        val oldRefCount = refCount.getAndIncrement()
        assert(oldRefCount >= 0) { "Unmatched calls to retain/release" }
        return value
    }

    /**
     * Returns the reference count associated to [value] **after** decrementing it.
     * If the return value is `zero`, [AutoCloseable.close] has been called on [value].
     * A return value of `zero` is typically used to check if this was the last reference
     * to [value].
     */
    fun release(): Int {
        val newRefCount = refCount.decrementAndGet()
        assert(newRefCount >= 0) { "Unmatched calls to retain/release" }
        if (newRefCount == 0) {
            if (value is AutoCloseable) {
                value.close()
            }
        }
        return newRefCount
    }

    /**
     * Calls [AutoCloseable.close] on [value], leaving the reference count untouched. Calling
     * [retain] after [close] is invalid, but calling [release] is still supported.
     */
    override fun close() {
        if (value is AutoCloseable) {
            value.close()
        }
    }
}
