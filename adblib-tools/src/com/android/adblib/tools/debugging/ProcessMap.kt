/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adblib.tools.debugging

import java.util.SortedMap
import java.util.TreeMap

/**
 * A custom collection similar to a map of [Int] to [T], with the intent of storing a collection
 * of unique process IDs associated to arbitrary values of type [T], which are [AutoCloseable].
 *
 * [AutoCloseable.close] is automatically called for every instance [T] discarded from
 * the collection.
 *
 * Note: This collection is **not** thread-safe.
 */
class ProcessMap<T> where T: AutoCloseable {

    /**
     * Use a [SortedMap] (as opposed to a regular [Map]) merely for convenience,
     * to keep PIDs sorted.
     */
    private val map: SortedMap<Int, T> = TreeMap()

    /**
     * The [Set] of process IDs currently stored in this collection.
     */
    val pids: Set<Int>
        get() = map.keys

    /**
     * The [Collection] of [T] values currently stored in this collection.
     */
    val values: Collection<T>
        get() = map.values

    /**
     * Incrementally update this collection so that it contains exactly all the process IDs
     * from [keys], adding and removing [T] values as needed so that [pids] == [keys].
     *
     * * When adding an entry, [valueFactory] is invoked to create the corresponding [T] value.
     * * When removing an existing entry, [AutoCloseable.close] is invoked on the corresponding
     *   [T] value.
     */
    fun update(keys: Iterable<Int>, valueFactory: (Int) -> T) {
        val map = this
        val lastKnownPids = map.pids
        val effectivePids = keys.toHashSet()

        val added = effectivePids - lastKnownPids
        val removed = lastKnownPids - effectivePids
        removed.forEach { pid ->
            map.remove(pid)
        }
        added.forEach { pid ->
            map.add(pid, valueFactory(pid))
        }
    }

    /**
     * Remove all entries of this collection, calling [AutoCloseable.close] on each [T] value.
     */
    fun clear() {
        // Close all processes
        map.values.forEach {
            it.close()
        }
        map.clear()
    }

    private fun add(pid: Int, item: T) {
        map.put(pid, item)?.also {
            // This is a serious internal error: we have 2 entries with the same pid.
            it.close()
            throw IllegalStateException("Error adding an entry for pid $pid: the collection contained an existing entry ($it)")
        }
    }

    private fun remove(pid: Int) {
        map.remove(pid)?.close()
    }
}
