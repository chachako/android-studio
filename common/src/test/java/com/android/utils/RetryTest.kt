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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertFailsWith

/** Tests utilities related to retrying code. */
@RunWith(JUnit4::class)
class RetryTest {
    @Test
    fun runsAtLeastOnce() {
        var timesRun = 0
        assertThat(executeWithRetries<Throwable, Int>(-100) { ++timesRun }).isEqualTo(1)

        timesRun = 0
        executeWithRetries<Throwable>(-100) { ++timesRun }
        assertThat(timesRun).isEqualTo(1)
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

        val returned = executeWithRetries<IllegalArgumentException, String>(maxRetries) {
            --retriesRemaining
            if (retriesRemaining > 0) throw IllegalArgumentException("Boom!")
            msg
        }

        assertThat(retriesRemaining).isEqualTo(0)
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
}
