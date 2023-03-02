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
 * This map will retain entries even after they are deleted.
 *
 * Threading assumptions:
 * The following assumptions are made regarding thread safety:
 * 1. Writes may come from different threads but are guaranteed to are serialized.
 * 2. Reads may come from any thread.
 *
 * @param maxRetention The maximum number of keys that can be retained after being deleted.
 */
internal class RetainingMap<K, V>(private val maxRetention: Int) {

    private val map: MutableMap<K, V> = ConcurrentHashMap()
    private val retentionList: LinkedHashSet<K> = LinkedHashSet()

    operator fun set(key: K, value: V) {
        map[key] = value
        retentionList.remove(key) // Don't evict if a key was re-added.
    }

    operator fun get(key: K): V? = map[key]

    fun remove(key: K) {
        if (maxRetention <= 0) {
            map.remove(key)
        } else {
            if (map.containsKey(key)) {
                if (retentionList.size >= maxRetention) {
                    val evictedKey = retentionList.first()
                    retentionList.remove(evictedKey)
                    map.remove(evictedKey)
                }
                retentionList.add(key)
            }
        }
    }

    fun removeAll(keys: Collection<K>) {
        keys.forEach { remove(it) }
    }

    fun asMap() : Map<K, V> = map
}
