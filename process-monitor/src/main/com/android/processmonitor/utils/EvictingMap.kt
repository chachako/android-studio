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
package com.android.processmonitor.utils

import java.util.concurrent.ConcurrentHashMap

/**
 * A mutable map that retains deleted entries.
 *
 * This map will retain entries even after they are deleted unless the size of the map including the
 * deleted entries exceeds a maximum size.
 *
 * TODO(aalbert): Consider changing the semantics to setting a limit on the size of the eviction
 *  list. This allows us to keep removed entries even if num-active-entries > maxSize.
 */
internal class EvictingMap<K, V>(private val maxSize: Int) {

    private val map: MutableMap<K, V> = ConcurrentHashMap()
    private val evictionList: LinkedHashSet<K> = LinkedHashSet()

    operator fun set(key: K, value: V) {
        map[key] = value
        evictionList.remove(key) // Don't evict if a key was re-added.
        evict()
    }

    operator fun get(key: K): V? = map[key]

    fun remove(key: K) {
        if (map.containsKey(key)) {
            evictionList.add(key)
            evict()
        }
    }

    fun removeAll(keys: Collection<K>) {
        val size = evictionList.size
        keys.forEach {
            if (map.containsKey(it)) {
                evictionList.add(it)
            }
        }
        if (evictionList.size > size) {
            evict()
        }
    }

    fun asMap() : Map<K, V> = map

    private fun evict() {
        val liveKeys = map.keys - evictionList
        val excessEntries = liveKeys.size + evictionList.size - maxSize
        if (excessEntries <= 0) {
            return
        }
        repeat(excessEntries) {
            if (evictionList.isEmpty()) {
                return
            }
            val evictedKey = evictionList.first()
            evictionList.remove(evictedKey)
            map.remove(evictedKey)
        }
    }
}
