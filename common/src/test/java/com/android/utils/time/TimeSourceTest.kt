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
package com.android.utils.time

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@RunWith(JUnit4::class)
class TimeSourceTest {
    private val timeSource = TimeSource.Monotonic
    @Test
    fun markNow() {
        val mark = timeSource.markNow()
        assertThat(mark.elapsedNow()).isAtLeast(0.milliseconds)
        Thread.sleep(100)
        assertThat(mark.elapsedNow()).isAtLeast(50.milliseconds)
    }

    @Test
    fun plusAndMinus() {
        val mark = timeSource.markNow()
        val earlier = mark - 1.days
        val later = mark + 1.days
        assertThat(mark.elapsedNow()).isLessThan(earlier.elapsedNow())
        assertThat(mark.elapsedNow()).isGreaterThan(later.elapsedNow())
        assertThat(earlier.elapsedNow() - 23.hours).isGreaterThan(mark.elapsedNow())
        assertThat(later.elapsedNow() + 23.hours).isLessThan(mark.elapsedNow())
    }
}
