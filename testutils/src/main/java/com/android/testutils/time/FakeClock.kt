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
package com.android.testutils.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime

/** Versatile fake [Clock] class for use in tests. */
@OptIn(ExperimentalTime::class) // For Duration which is no longer experimental
open class FakeClock(
    private var now: Instant = Instant.EPOCH,
    private val zoneId: ZoneId = ZoneOffset.UTC,
): Clock() {
    override fun instant() = now

    override fun withZone(otherZoneId: ZoneId): Clock = object : Clock() {
        // Defer to the original clock for all but the zone.
        override fun instant(): Instant = this@FakeClock.instant()
        override fun withZone(zone: ZoneId) = this@FakeClock.withZone(zone)
        override fun getZone(): ZoneId = otherZoneId
    }

    override fun getZone(): ZoneId = zoneId

    open fun advanceTimeBy(timeMillis: Long) {
        now = now.plusMillis(timeMillis)
    }

    fun advanceTimeBy(duration: java.time.Duration) {
        advanceTimeBy(duration.toMillis())
    }

    fun advanceTimeBy(duration: kotlin.time.Duration) {
        advanceTimeBy(duration.inWholeMilliseconds)
    }

    operator fun plusAssign(duration: java.time.Duration) {
        advanceTimeBy(duration)
    }

    operator fun plusAssign(duration: kotlin.time.Duration) {
        advanceTimeBy(duration)
    }
}
