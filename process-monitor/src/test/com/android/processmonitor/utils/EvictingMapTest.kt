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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [EvictingMap]
 */
class EvictingMapTest {

    @Test
    fun noOverflow_doesNotEvict() {
        val evictingMap = evictingMap(
            maxSize = 3,
            1 to "e1",
            2 to "e2",
            3 to "e3",
        )

        evictingMap.removeAll(1, 2, 3)

        assertThat(evictingMap.asMap()).containsExactly(
            1, "e1",
            2, "e2",
            3, "e3",
        )
    }

    @Test
    fun withOverflow_evicts() {
        val evictingMap = evictingMap(
            maxSize = 2,
            1 to "e1",
            2 to "e2",
            3 to "e3",
        )

        evictingMap.removeAll(1, 2, 3)

        assertThat(evictingMap.asMap()).containsExactly(
            2, "e2",
            3, "e3",
        )
    }

    @Test
    fun removedFirst_evictedFirst() {
        val evictingMap = evictingMap(
            maxSize = 2,
            1 to "e1",
            2 to "e2",
            3 to "e3",
        )

        evictingMap.removeAll(2, 1, 3)

        assertThat(evictingMap.asMap()).containsExactly(
            1, "e1",
            3, "e3",
        )
    }

    @Test
    fun addingNewAndRemovingOld() {
        val evictingMap = evictingMap(
            maxSize = 3,
            1 to "e1",
            2 to "e2",
            3 to "e3",
        )

        evictingMap.removeAll(1, 2, 3)
        evictingMap.addAll(
            4 to "e4",
            5 to "e5",
        )

        assertThat(evictingMap.asMap()).containsExactly(
            3, "e3",
            4, "e4",
            5, "e5",
        )
    }

    @Test
    fun activeKeys_notEvicted() {
        val evictingMap = evictingMap(
            maxSize = 3,
            1 to "e1",
            2 to "e2",
            3 to "e3",
            4 to "e4",
            5 to "e5",
        )

        assertThat(evictingMap.asMap()).containsExactly(
            1, "e1",
            2, "e2",
            3, "e3",
            4, "e4",
            5, "e5",
        )
    }

    @Test
    fun readdedRemovedKey_isNotEvicted() {
        val evictingMap = evictingMap(
            maxSize = 3,
            1 to "e1",
            2 to "e2",
            3 to "e3",
        )

        evictingMap.removeAll(1, 2, 3)
        evictingMap.addAll(
            1 to "e1",
            4 to "e4",
        )

        assertThat(evictingMap.asMap()).containsExactly(
            1, "e1",
            3, "e3",
            4, "e4",
        )
    }
}

private fun <K, V> evictingMap(maxSize: Int, vararg items: Pair<K, V>) =
    EvictingMap<K, V>(maxSize).apply { addAll(*items) }

private fun <K, V> EvictingMap<K, V>.removeAll(vararg keys: K) = removeAll(keys.asList())

private fun <K, V> EvictingMap<K, V>.addAll(vararg items: Pair<K, V>) {
    items.forEach { this[it.first] = it.second }
}
