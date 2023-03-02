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
 * Tests for [RetainingMap]
 */
class RetainingMapTest {

    @Test
    fun noOverflow_doesNotEvict() {
        val retainingMap = retainingMap(
            maxRetention = 3,
            1 to "e1",
            2 to "e2",
            3 to "e3",
        )

        retainingMap.removeAll(1, 2, 3)

        assertThat(retainingMap.asMap()).containsExactly(
            1, "e1",
            2, "e2",
            3, "e3",
        )
    }

    @Test
    fun withOverflow_evicts() {
        val retainingMap = retainingMap(
            maxRetention = 2,
            1 to "e1",
            2 to "e2",
            3 to "e3",
        )

        retainingMap.removeAll(1, 2, 3)

        assertThat(retainingMap.asMap()).containsExactly(
            2, "e2",
            3, "e3",
        )
    }

    @Test
    fun removedFirst_evictedFirst() {
        val retainingMap = retainingMap(
            maxRetention = 2,
            1 to "e1",
            2 to "e2",
            3 to "e3",
        )

        retainingMap.removeAll(2, 1, 3)

        assertThat(retainingMap.asMap()).containsExactly(
            1, "e1",
            3, "e3",
        )
    }

    @Test
    fun addingNewAndRemovingOld() {
        val retainingMap = retainingMap(
            maxRetention = 2,
            1 to "e1",
            2 to "e2",
            3 to "e3",
        )

        retainingMap.removeAll(1, 2, 3)
        retainingMap.addAll(
            4 to "e4",
            5 to "e5",
        )

        assertThat(retainingMap.asMap()).containsExactly(
            2, "e2",
            3, "e3",
            4, "e4",
            5, "e5",
        )
    }

    @Test
    fun activeKeys_retained() {
        val retainingMap = retainingMap(
            maxRetention = 3,
            1 to "e1",
            2 to "e2",
            3 to "e3",
            4 to "e4",
            5 to "e5",
        )

        assertThat(retainingMap.asMap()).containsExactly(
            1, "e1",
            2, "e2",
            3, "e3",
            4, "e4",
            5, "e5",
        )
    }

    @Test
    fun reAddedRemovedKey_retained() {
        val retainingMap = retainingMap(
            maxRetention = 3,
            1 to "e1",
            2 to "e2",
            3 to "e3",
        )

        retainingMap.removeAll(1, 2, 3)
        retainingMap.addAll(
            1 to "e1",
            4 to "e4",
        )

        assertThat(retainingMap.asMap()).containsExactly(
            1, "e1",
            2, "e2",
            3, "e3",
            4, "e4",
        )
    }

    @Test
    fun noRetention() {
        val retainingMap = retainingMap(
            maxRetention = 0,
            1 to "e1",
            2 to "e2",
            3 to "e3",
        )

        retainingMap.removeAll(1, 2, 3)

        assertThat(retainingMap.asMap()).isEmpty()
    }

    @Test
    fun negativeRetention_doesNotThrow() {
        val retainingMap = retainingMap(
            maxRetention = -1,
            1 to "e1",
        )

        retainingMap.removeAll(1)

        assertThat(retainingMap.asMap()).isEmpty()
    }
}

private fun <K, V> retainingMap(maxRetention: Int, vararg items: Pair<K, V>) =
    RetainingMap<K, V>(maxRetention).apply { addAll(*items) }

private fun <K, V> RetainingMap<K, V>.removeAll(vararg keys: K) = removeAll(keys.asList())

private fun <K, V> RetainingMap<K, V>.addAll(vararg items: Pair<K, V>) {
    items.forEach { this[it.first] = it.second }
}
