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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Tests for the [FakeClock] class. */
@RunWith(JUnit4::class)
class FakeClockTest {
    @Test
    fun constructor_default() {
        FakeClock().let {
            assertThat(it.instant()).isEqualTo(Instant.EPOCH)
            assertThat(it.zone).isEqualTo(ZoneOffset.UTC)
        }
    }

    @Test
    fun constructor_now() {
        listOf(Instant.now(), Instant.MIN, Instant.MAX).forEach {
            assertThat(FakeClock(now = it).instant()).isEqualTo(it)
        }
    }

    @Test
    fun constructor_zoneId() {
        val zones = listOf(ZoneId.of("Z"), ZoneId.of("UTC+8"),
                           ZoneId.of("Europe/Paris"), ZoneId.of("America/New_York"))
        zones.forEach {
            assertThat(FakeClock(zoneId = it).zone).isEqualTo(it)
        }
    }

    @Test
    fun withZone_changesZone() {
        val zones = listOf(ZoneId.of("Z"), ZoneId.of("UTC+8"),
                           ZoneId.of("Europe/Paris"), ZoneId.of("America/New_York"))
        zones.forEach {
            assertThat(FakeClock().withZone(it).zone).isEqualTo(it)
        }
    }

    @Test
    fun withZone_preservesTime() {
        val origClock = FakeClock()
        val derivedClock = origClock.withZone(ZoneId.of("Europe/Paris"))

        for (i in 0L until 10L) {
            origClock.advanceTimeBy(i)
            assertThat(origClock.instant()).isEqualTo(derivedClock.instant())
        }
    }

    @Test
    fun advanceTimeBy() {
        val fakeClock = FakeClock()
        fakeClock.advanceTimeBy(10)
        assertThat(fakeClock.instant().toEpochMilli()).isEqualTo(10)
        fakeClock.advanceTimeBy(Duration.ofMillis(27))
        assertThat(fakeClock.instant().toEpochMilli()).isEqualTo(37)
        fakeClock.advanceTimeBy(42.milliseconds)
        assertThat(fakeClock.instant().toEpochMilli()).isEqualTo(79)
        fakeClock += Duration.ofSeconds(5)
        assertThat(fakeClock.instant().toEpochMilli()).isEqualTo(5079)
        fakeClock += 2.seconds
        assertThat(fakeClock.instant().toEpochMilli()).isEqualTo(7079)
    }
}
