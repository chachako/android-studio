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
package com.android.utils

import com.android.utils.sleep.TestThreadSleeper
import com.android.utils.time.TestTimeSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Tests utilities related to retrying code. */
@RunWith(JUnit4::class)
class RetryTest {
    private val testTimeSource = TestTimeSource()
    private val testThreadSleeper = TestThreadSleeper()

    @Test
    fun runsAtLeastOnce() {
        var timesRun = 0
        assertThat(executeWithRetries<Throwable, Int>(-100) { ++timesRun }).isEqualTo(1)

        timesRun = 0
        executeWithRetries<Throwable>(-100) { ++timesRun }
        assertThat(timesRun).isEqualTo(1)
    }

    @Test
    fun runsAtLeastOnce_duration() {
        var timesRun = 0
        assertThat(executeWithRetries<Throwable, Int>(Duration.ZERO) { ++timesRun }).isEqualTo(1)

        timesRun = 0
        executeWithRetries<Throwable>(Duration.ZERO) { ++timesRun }
        assertThat(timesRun).isEqualTo(1)
    }

    @Test
    fun runsAtLeastOnce_condition() {
        var timesRun = 0
        assertThat(executeWithRetries<Throwable, Int>({ false }) { ++timesRun }).isEqualTo(1)

        timesRun = 0
        executeWithRetries<Throwable>({ false }) { ++timesRun }
        assertThat(timesRun).isEqualTo(1)
    }

    @Test
    fun sleepDuration_notUsedIfDoesNotRetry() {
        executeWithRetries<Throwable, Unit>(
            duration = 100.seconds,
            sleepBetweenRetries = 5.seconds,
            timeSource = testTimeSource,
            threadSleeper = testThreadSleeper,
        ) { }
        assertThat(testThreadSleeper.sleepDurations).isEmpty()

        testThreadSleeper.reset()

        executeWithRetries<Throwable>(
            duration = 100.seconds,
            sleepBetweenRetries = 5.seconds,
            timeSource = testTimeSource,
            threadSleeper = testThreadSleeper,
        ) { }
        assertThat(testThreadSleeper.sleepDurations).isEmpty()
    }

    @Test
    fun throwsWhenOutOfRetries() {
        val maxRetries = 5
        val msg = "No cloning!"
        var timesRun = 0

        var thrown = assertFailsWith<CloneNotSupportedException> {
            executeWithRetries<CloneNotSupportedException>(maxRetries) {
                ++timesRun
                throw CloneNotSupportedException(msg)
            }
        }
        assertThat(thrown).hasMessageThat().isEqualTo(msg)
        assertThat(timesRun).isEqualTo(maxRetries + 1)

        timesRun = 0

        thrown = assertFailsWith<CloneNotSupportedException> {
            executeWithRetries<CloneNotSupportedException, Nothing>(maxRetries) {
                ++timesRun
                throw CloneNotSupportedException(msg)
            }
        }
        assertThat(thrown).hasMessageThat().isEqualTo(msg)
        assertThat(timesRun).isEqualTo(maxRetries + 1)
    }

    @Test
    fun throwsWhenOutOfRetries_duration() {
        val duration = 30.seconds
        val msg = "No cloning!"

        var thrown = assertFailsWith<CloneNotSupportedException> {
            executeWithRetries<CloneNotSupportedException>(duration, timeSource = testTimeSource) {
                testTimeSource += 6.seconds
                throw CloneNotSupportedException(msg)
            }
        }
        assertThat(thrown).hasMessageThat().isEqualTo(msg)

        thrown = assertFailsWith<CloneNotSupportedException> {
            executeWithRetries<CloneNotSupportedException, Nothing>(duration, timeSource = testTimeSource) {
                testTimeSource += 6.seconds
                throw CloneNotSupportedException(msg)
            }
        }
        assertThat(thrown).hasMessageThat().isEqualTo(msg)
    }

    @Test
    fun doesNotSleepIfNotEnoughTimeLeft() {
        val duration = 30.seconds
        val msg = "No cloning!"

        assertFailsWith<CloneNotSupportedException> {
            executeWithRetries<CloneNotSupportedException>(
                duration = duration,
                sleepBetweenRetries = 10.seconds,
                timeSource = testTimeSource,
                threadSleeper = testThreadSleeper,
            ) {
                testTimeSource += 6.seconds
                throw CloneNotSupportedException(msg)
            }
        }

        // After the 4th invocation we only have 6 seconds remaining which is not enough to
        // sleep 10 seconds, so it shouldn't sleep again.
        assertThat(testThreadSleeper.sleepDurations).containsExactly(10.seconds, 10.seconds, 10.seconds)

        testThreadSleeper.reset()

        assertFailsWith<CloneNotSupportedException> {
            executeWithRetries<CloneNotSupportedException, Nothing>(
                duration = duration,
                sleepBetweenRetries = 10.seconds,
                timeSource = testTimeSource,
                threadSleeper = testThreadSleeper,
            ) {
                testTimeSource += 6.seconds
                throw CloneNotSupportedException(msg)
            }
        }
        // After the 4th invocation we only have 6 seconds remaining which is not enough to
        // sleep 10 seconds, so it shouldn't sleep again.
        assertThat(testThreadSleeper.sleepDurations).containsExactly(10.seconds, 10.seconds, 10.seconds)
    }

    @Test
    fun throwsWhenOutOfRetries_condition() {
        val list = (0 until 5).toMutableList()
        val msg = "No cloning!"

        var thrown = assertFailsWith<CloneNotSupportedException> {
            executeWithRetries<CloneNotSupportedException>(list::isNotEmpty) {
                list.removeLast()
                throw CloneNotSupportedException(msg)
            }
        }
        assertThat(thrown).hasMessageThat().isEqualTo(msg)

        list.addAll(0 until 5)

        thrown = assertFailsWith<CloneNotSupportedException> {
            executeWithRetries<CloneNotSupportedException, Nothing>(list::isNotEmpty) {
                list.removeLast()
                throw CloneNotSupportedException(msg)
            }
        }
        assertThat(thrown).hasMessageThat().isEqualTo(msg)
    }

    @Test
    fun usesAllRetries() {
        val maxRetries = 5
        var retriesRemaining = maxRetries

        executeWithRetries<IllegalArgumentException>(maxRetries) {
            --retriesRemaining
            if (retriesRemaining > 0) throw IllegalArgumentException("Boom!")
        }

        assertThat(retriesRemaining).isEqualTo(0)

        retriesRemaining = maxRetries
        val msg = "Woohoo!"

        val returned =
            executeWithRetries<IllegalArgumentException, String>(maxRetries) {
                --retriesRemaining
                if (retriesRemaining > 0) throw IllegalArgumentException("Boom!")
                msg
            }

        assertThat(retriesRemaining).isEqualTo(0)
        assertThat(returned).isEqualTo(msg)
    }

    @Test
    fun usesAllRetries_duration() {
        val duration = 30.seconds
        var startTime = testTimeSource.markNow()

        executeWithRetries<IllegalArgumentException>(duration, timeSource = testTimeSource) {
            testTimeSource += 6.seconds
            if (startTime.elapsedNow() < duration) throw IllegalArgumentException("Boom!")
        }

        assertThat(startTime.elapsedNow()).isAtLeast(duration)

        startTime = testTimeSource.markNow()
        val msg = "Woohoo!"

        val returned =
            executeWithRetries<IllegalArgumentException, String>(duration, timeSource = testTimeSource) {
                testTimeSource += 6.seconds
                if (startTime.elapsedNow() < duration) throw IllegalArgumentException("Boom!")
                msg
            }

        assertThat(startTime.elapsedNow()).isAtLeast(duration)
        assertThat(returned).isEqualTo(msg)
    }

    @Test
    fun sleepsBetweenRetries() {
        val duration = 30.seconds
        var startTime = testTimeSource.markNow()

        executeWithRetries<IllegalArgumentException>(
            duration = duration,
            sleepBetweenRetries = 2.seconds,
            timeSource = testTimeSource,
            threadSleeper = testThreadSleeper,
        ) {
            testTimeSource += 6.seconds
            if (startTime.elapsedNow() < duration) throw IllegalArgumentException("Boom!")
        }

        // Should not sleep the last time, so even though the block executes 5 times, we should
        // only sleep 4 times.
        assertThat(testThreadSleeper.sleepDurations).containsExactly(2.seconds, 2.seconds, 2.seconds, 2.seconds)

        testThreadSleeper.reset()
        startTime = testTimeSource.markNow()

        val returned =
            executeWithRetries<IllegalArgumentException, Unit>(
                duration = duration,
                sleepBetweenRetries = 2.seconds,
                timeSource = testTimeSource,
                threadSleeper = testThreadSleeper,
            ) {
                testTimeSource += 6.seconds
                if (startTime.elapsedNow() < duration) throw IllegalArgumentException("Boom!")
            }

        // Should not sleep the last time, so even though the block executes 5 times, we should
        // only sleep 4 times.
        assertThat(testThreadSleeper.sleepDurations).containsExactly(2.seconds, 2.seconds, 2.seconds, 2.seconds)

    }

    @Test
    fun usesAllRetries_condition() {
        val list = (0 until 5).toMutableList()

        executeWithRetries<IllegalArgumentException>(list::isNotEmpty) {
            list.removeLast()
            if (list.isNotEmpty()) throw IllegalArgumentException("Boom!")
        }

        assertThat(list).isEmpty()

        list.addAll(0 until 5)
        val msg = "Woohoo!"

        val returned = executeWithRetries<IllegalArgumentException, String>(list::isNotEmpty) {
            list.removeLast()
            if (list.isNotEmpty()) throw IllegalArgumentException("Boom!")
            msg
        }

        assertThat(list).isEmpty()
        assertThat(returned).isEqualTo(msg)
    }

    @Test
    fun onlyCatchesDeclaredException() {
        val maxRetries = 5
        val msg = "kaboom!"
        var timesRun = 0
        var thrown = assertFailsWith<IllegalArgumentException> {
            executeWithRetries<IllegalStateException>(maxRetries) {
                ++timesRun
                throw IllegalArgumentException(msg)
            }
        }

        assertThat(thrown).hasMessageThat().isEqualTo(msg)
        assertThat(timesRun).isEqualTo(1)

        timesRun = 0
        thrown = assertFailsWith<IllegalArgumentException> {
            executeWithRetries<IllegalStateException, Nothing>(maxRetries) {
                ++timesRun
                throw IllegalArgumentException(msg)
            }
        }

        assertThat(thrown).hasMessageThat().isEqualTo(msg)
        assertThat(timesRun).isEqualTo(1)
    }

    @Test
    fun onlyCatchesDeclaredException_duration() {
        val duration = 30.seconds
        val msg = "kaboom!"
        var timesRun = 0
        var thrown = assertFailsWith<IllegalArgumentException> {
            executeWithRetries<IllegalStateException>(duration, timeSource = testTimeSource) {
                ++timesRun
                throw IllegalArgumentException(msg)
            }
        }

        assertThat(thrown).hasMessageThat().isEqualTo(msg)
        assertThat(timesRun).isEqualTo(1)

        timesRun = 0
        thrown = assertFailsWith<IllegalArgumentException> {
            executeWithRetries<IllegalStateException, Nothing>(duration, timeSource = testTimeSource) {
                ++timesRun
                throw IllegalArgumentException(msg)
            }
        }

        assertThat(thrown).hasMessageThat().isEqualTo(msg)
        assertThat(timesRun).isEqualTo(1)
    }

    @Test
    fun onlyCatchesDeclaredException_condition() {
        val msg = "kaboom!"
        var timesRun = 0
        var thrown = assertFailsWith<IllegalArgumentException> {
            executeWithRetries<IllegalStateException>({ true }) {
                ++timesRun
                throw IllegalArgumentException(msg)
            }
        }

        assertThat(thrown).hasMessageThat().isEqualTo(msg)
        assertThat(timesRun).isEqualTo(1)

        timesRun = 0
        thrown = assertFailsWith<IllegalArgumentException> {
            executeWithRetries<IllegalStateException, Nothing>({ true }) {
                ++timesRun
                throw IllegalArgumentException(msg)
            }
        }

        assertThat(thrown).hasMessageThat().isEqualTo(msg)
        assertThat(timesRun).isEqualTo(1)
    }
}
