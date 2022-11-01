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
import java.util.concurrent.TimeUnit
import kotlin.time.DurationUnit

@RunWith(JUnit4::class)
class DurationUtilTest {
    @Test
    fun toDurationUnit() {
        assertThat(TimeUnit.NANOSECONDS.toDurationUnit()).isEqualTo(DurationUnit.NANOSECONDS)
        assertThat(TimeUnit.MICROSECONDS.toDurationUnit()).isEqualTo(DurationUnit.MICROSECONDS)
        assertThat(TimeUnit.MILLISECONDS.toDurationUnit()).isEqualTo(DurationUnit.MILLISECONDS)
        assertThat(TimeUnit.SECONDS.toDurationUnit()).isEqualTo(DurationUnit.SECONDS)
        assertThat(TimeUnit.MINUTES.toDurationUnit()).isEqualTo(DurationUnit.MINUTES)
        assertThat(TimeUnit.HOURS.toDurationUnit()).isEqualTo(DurationUnit.HOURS)
        assertThat(TimeUnit.DAYS.toDurationUnit()).isEqualTo(DurationUnit.DAYS)
    }
}
