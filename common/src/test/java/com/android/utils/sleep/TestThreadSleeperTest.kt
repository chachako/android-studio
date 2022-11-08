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
package com.android.utils.sleep

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/** Test the [TestThreadSleeper]. */
@RunWith(JUnit4::class)
class TestThreadSleeperTest {
    private val testThreadSleeper = TestThreadSleeper()

    @Test
    fun sleep() {
        testThreadSleeper.sleep(millis = 100, nanos = 25)
        testThreadSleeper.sleep(40.seconds)
        testThreadSleeper.sleep(millis = 3_025)
        testThreadSleeper.sleep(num = 15, unit = TimeUnit.HOURS)

        assertThat(testThreadSleeper.sleepArguments).containsExactly(
            100L to 25,
            40_000L to 0,
            3_025L to 0,
            15.hours.inWholeMilliseconds to 0,
        ).inOrder()

        assertThat(testThreadSleeper.sleepDurations).containsExactly(
            100.milliseconds + 25.nanoseconds,
            40.seconds,
            3025.milliseconds,
            15.hours,
        ).inOrder()

        assertThat(testThreadSleeper.totalMillisecondsSlept).isEqualTo(
            100 + 40_000 + 3_025 + 15.hours.inWholeMilliseconds
        )

        assertThat(testThreadSleeper.totalTimeSlept).isEqualTo(
            100.milliseconds + 25.nanoseconds + 40.seconds + 3_025.milliseconds + 15.hours
        )
    }

    @Test
    fun sleepZero_doesNotSleep() {
        testThreadSleeper.sleep(millis = 0, nanos = 0)
        testThreadSleeper.sleep(0.seconds)
        testThreadSleeper.sleep(millis = 0)
        testThreadSleeper.sleep(num = 0, unit = TimeUnit.HOURS)

        assertThat(testThreadSleeper.sleepArguments).isEmpty()
        assertThat(testThreadSleeper.sleepDurations).isEmpty()
        assertThat(testThreadSleeper.totalMillisecondsSlept).isEqualTo(0L)
        assertThat(testThreadSleeper.totalTimeSlept).isEqualTo(0.nanoseconds)
    }

    @Test
    fun reset() {
        testThreadSleeper.sleep(millis = 100, nanos = 25)
        testThreadSleeper.sleep(40.seconds)
        testThreadSleeper.sleep(millis = 3_025)
        testThreadSleeper.sleep(num = 15, unit = TimeUnit.HOURS)
        testThreadSleeper.reset()

        assertThat(testThreadSleeper.sleepArguments).isEmpty()
        assertThat(testThreadSleeper.sleepDurations).isEmpty()
        assertThat(testThreadSleeper.totalMillisecondsSlept).isEqualTo(0L)
        assertThat(testThreadSleeper.totalTimeSlept).isEqualTo(0.nanoseconds)
    }
}
