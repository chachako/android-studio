/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.adblib.tools.debugging.processinventory.server

import com.android.adblib.AdbLogger
import com.android.adblib.IsThreadSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * A map of [K] to elements of type [V], where elements are removed from the map
 * when they are not used for more than a [Duration] of [removalDelay]. Elements can
 * be accessed using [withValue] and are considered "in-use" as long as there is an
 * active [withValue] value.
 *
 * Note: All methods of this class are thread-safe.
 */
@IsThreadSafe
internal class UsageTrackingMap<K, V>(
    private val logger: AdbLogger,
    parentScope: CoroutineScope,
    private val removalDelay: Duration,
    private val clock: Clock,
    private val factory: (K) -> V,
) where K: Any {

    /**
     * The lock for [map] and [scheduledForRemoval]
     */
    private val lock = Any()

    /**
     * All the values stored in this map, wrapped inside a [MapEntry]
     */
    private val map = HashMap<K, MapEntry<K, V>>()

    /**
     * The elements that have reached a [MapEntry.refCount] of zero, hence are candidates for
     * removal.
     */
    private val scheduledForRemoval = HashSet<MapEntry<K, V>>()

    init {
        parentScope.launch {
            removeUnusedValuesLoop()
        }
    }

    /**
     * Invokes [block] with the value [V] associated to [key]. If there is no value associated
     * to [key], it is created using the [factory] and stored in the map before calling [block].
     * During the execution of [block], the value associated to [key] is marked as "in-use" and
     * won't be removed from the map. After [block] has terminated, the value is considered unused
     * and becomes candidate for removal (unless another [block] is started or pending).
     */
    inline fun <R> withValue(key: K, block: (V) -> R): R {
        val entry = retain(key)
        return try {
            block(unwrapValue(entry))
        } finally {
            release(entry)
        }
    }

    /**
     * Performs an explicit pass of removing for unused values from the map. Calling this method
     * is typically not necessary, this method is already called on a regular basis in a worker
     * coroutine.
     */
    fun removeAllUnused() {
        synchronized(lock) {
            if (scheduledForRemoval.isNotEmpty()) {
                val now = clock.instant()
                scheduledForRemoval.filter {
                    assert(it.refCount == 0)
                    // An entry is not used if lastUsed value is past the removalDelay.
                    Duration.between(it.lastUsed, now) >= removalDelay
                }.forEach {
                    logger.debug { "Removing $it from map because it has not been used " +
                            "for a duration of ${Duration.between(it.lastUsed, now)}" }
                    map.remove(it.key)
                    scheduledForRemoval.remove(it)
                }
            }
        }
    }

    private fun retain(key: K): MapEntry<K, V> {
        return synchronized(lock) {
            map.getOrPut(key, defaultValue = { newEntry(key) }).also { entry ->
                entry.refCount++
                touchEntry(entry)
            }
        }
    }

    private fun release(entry: MapEntry<K, V>) {
        return synchronized(lock) {
            entry.refCount--
            touchEntry(entry)
        }
    }

    private fun newEntry(key: K): MapEntry<K, V> {
        return MapEntry(key, factory(key), clock.instant()).also {
            logger.debug { "New entry created: $it" }
        }
    }

    private fun touchEntry(entry: MapEntry<K, V>) {
        entry.lastUsed = clock.instant()
        if (entry.refCount == 0) {
            scheduledForRemoval.add(entry)
        } else {
            scheduledForRemoval.remove(entry)
        }
    }

    /**
     * Note: We need this so that [withValue] can be inline ([MapEntry.value] is private
     * and can be directly accessed from an inline function).
     */
    private fun unwrapValue(entry: MapEntry<K, V>) = entry.value

    private suspend fun removeUnusedValuesLoop() {
        val delayMillis = (removalDelay.toMillis() / 10).coerceAtLeast(MIN_DELAY_MILLIS)
        while (true) {
            delay(delayMillis)
            removeAllUnused()
        }
    }

    private class MapEntry<K, V>(val key: K, val value: V, now: Instant) where K: Any {

        /**
         * The number of active [UsageTrackingMap.withValue] calls
         */
        var refCount = 0

        /**
         * Last time the value was used inside a [UsageTrackingMap.withValue] call
         */
        var lastUsed = now

        override fun equals(other: Any?): Boolean {
            return if (other is MapEntry<*, *>) {
                key == other.key
            } else {
                false
            }
        }

        override fun hashCode(): Int {
            return key.hashCode()
        }

        override fun toString(): String {
            return "${this::class.java.simpleName}(key=$key, value=$value, " +
                    "refCount=$refCount, lastUsed=$lastUsed)"
        }
    }
    companion object {

        private const val MIN_DELAY_MILLIS = 20L
    }
}
