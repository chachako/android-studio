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
package com.android.adblib.tools.testutils

import org.junit.Assert
import java.time.Duration
import java.util.Locale

internal data class NanoTimeSpan(val startNano: Long, val endNano: Long) {

    override fun toString(): String {
        val s = String.format(Locale.ROOT, "%,d", startNano)
        val e = String.format(Locale.ROOT, "%,d", endNano - startNano)
        return ("TimeSpan(start=$s, duration=$e)")
    }

    companion object {
        fun assertNanoTimeSpansAreSorted(
            timeSpans: List<NanoTimeSpan>,
            expectedCount: Int
        ) {
            // Assert expected number of time spans
            Assert.assertEquals(expectedCount, timeSpans.size)

            // Assert that the end nano of each entry is <= the start nano of the next entry,
            // which verifies that all "emit" calls to consumer flows were never made
            // concurrently
            val sortedSpans = timeSpans.sortedBy { it.startNano }
            sortedSpans.fold(NanoTimeSpan(Long.MIN_VALUE, Long.MIN_VALUE)) { prev, cur ->
                Assert.assertTrue(
                    "'${prev.endNano} <= ${cur.startNano}' is expected to be true (time spans overlap)",
                    prev.endNano <= cur.startNano
                )
                cur
            }
        }

        fun assertNanoTimeSpansAreInterleaved(
            timeSpans: List<NanoTimeSpan>,
            expectedEntryCount: Int,
            entryDurationMillis: Int,
        ) {
            // Assert expected number of time spans
            Assert.assertEquals(expectedEntryCount, timeSpans.size)

            // Given each "entry" is expected to run at least "entryDurationMillis" and we want to
            // assert their execution were not serialized, we can assert the range of time span
            // collected by each entry is not as large as the expected duration if they were
            // executed sequentially.
            val minStartNano = timeSpans.minOf { it.startNano }
            val maxEndNano = timeSpans.maxOf { it.endNano }
            val measuredDuration = Duration.ofNanos(maxEndNano - minStartNano)
            val maximumExpectedDuration = Duration.ofMillis(expectedEntryCount * entryDurationMillis.toLong())

            Assert.assertTrue(
                "$measuredDuration <= $maximumExpectedDuration (Time spans should be interleaved)",
                measuredDuration <= maximumExpectedDuration
            )
        }
    }
}
