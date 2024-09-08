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
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.lint.detector.api.Intervals
import com.android.tools.lint.detector.api.Intervals.Companion.atLeast
import com.android.tools.lint.detector.api.Intervals.Companion.atMost
import com.android.tools.lint.detector.api.Intervals.Companion.below
import com.android.tools.lint.detector.api.Intervals.Companion.exactly
import com.android.tools.lint.detector.api.Intervals.Companion.range
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalsTest {
  private fun p(set: Intervals): String {
    // Run through double serialization to make sure it works
    val restored = Intervals.deserialize(set.serialize())
    return restored.toString("x", compact = true)
  }

  @Suppress("LocalVariableName")
  @Test
  fun testBasics() {
    val exactly12 = exactly(12)
    val atLeast12 = atLeast(12)
    val range_10_to_12 = range(10, 12)
    val range_10_to_15 = range(10, 15)
    val range_12_to_17 = range(12, 17)
    val range_14_to_17 = range(14, 17)
    val range_20_to_23 = range(20, 23)
    val range_25_to_27 = range(25, 27)
    val range_15_to_22 = range(15, 22)
    val range_10_to_17_without_12_and_13 = range_10_to_12 or range_14_to_17
    val atLeast15_2 = atLeast(15, 2)
    val below16_2 = below(16, 2)
    val between15_2_and_16_2 = atLeast15_2 and below16_2

    assertEquals(exactly(12), exactly(12))
    assertEquals(range(1, 6), below(6))
    assertEquals(range(1, 1), Intervals.NONE)
    assertEquals(range(1, 5) and range(10, 17), Intervals.NONE)
    assertNotEquals(exactly(12), exactly(13))
    assertNotEquals(exactly(12), atLeast(13))
    assertEquals(range(1, 6), below(6))
    assertEquals(range(5, 5), range(2, 1)) // empty == empty
    assertNotEquals(range_14_to_17, range_14_to_17 or exactly(19))
    assertEquals(
      range_10_to_17_without_12_and_13,
      atLeast(10) and below(17) and (exactly(12) or exactly(13)).not(),
    )
    assertFalse(range_15_to_22.contains(14))
    assertTrue(range_15_to_22.contains(15))
    assertTrue(range_15_to_22.contains(21))
    assertFalse(range_15_to_22.contains(22))
    assertFalse(range_15_to_22.contains(23))
    assertEquals(-1, Intervals.NONE.fromInclusive())
    assertEquals("x = 12", p(exactly12))
    assertEquals("x ≥ 12", p(atLeast12))
    assertEquals("x < 12", p((below(12))))
    assertEquals("x < 13", p((atMost(12))))
    assertEquals("x ≥ 12", p((atLeast(10) and atLeast12)))
    assertEquals("x ≥ 10", p((atLeast(10) or atLeast12)))
    assertEquals("10 ≤ x < 12", p(range_10_to_12))
    assertEquals("x < 10 or x ≥ 12", p((range_10_to_12.not())))
    assertEquals("x ≠ 12", p((exactly12.not())))
    assertEquals("No xs", p((atLeast(10) and below(10))))
    assertEquals("No xs", p((atLeast(10) and below(5))))
    assertEquals("All xs", p((atLeast(10) or below(15))))

    assertEquals("12 ≤ x < 15", p((range_10_to_15 and range_12_to_17)))
    assertEquals("10 ≤ x < 17", p((range_10_to_12 or range_12_to_17)))
    assertEquals("15.2 ≤ x < 16.2", p(between15_2_and_16_2))
    assertEquals("x < 15.2 or x ≥ 16.2", p(between15_2_and_16_2.not()))
    assertEquals("15.2 ≤ x < 16.2", p(between15_2_and_16_2.not().not()))
    assertEquals("x ≠ 12", p(exactly12.not()))
    assertEquals("x = 12", p(exactly12.not().not()))
    assertEquals("10 ≤ x < 12 or 14 ≤ x < 17", p(range_10_to_17_without_12_and_13))
    assertEquals("x < 10 or 12 ≤ x < 14 or x ≥ 17", p(range_10_to_17_without_12_and_13.not()))
    assertEquals("x = 5 or x ≥ 12", p((exactly(5) or atLeast12)))
    assertEquals("x < 5 or 6 ≤ x < 12", p(((exactly(5) or atLeast12)).not()))
    assertEquals("10 ≤ x < 17", p(range_10_to_15 or range_12_to_17))
    assertEquals("10 ≤ x < 12 or 14 ≤ x < 17", p(range_10_to_17_without_12_and_13))
    assertEquals(
      "10 ≤ x < 12 or 14 ≤ x < 17 or 20 ≤ x < 23",
      p(range_10_to_17_without_12_and_13 or range_20_to_23),
    )
    assertEquals(
      "10 ≤ x < 12 or 14 ≤ x < 17 or 20 ≤ x < 23 or 25 ≤ x < 27",
      p(range_10_to_17_without_12_and_13 or (range_20_to_23 or range_25_to_27)),
    )
    assertEquals(
      "No xs",
      p((range_10_to_17_without_12_and_13 and (range_20_to_23 or range_25_to_27))),
    )
    assertTrue(ApiConstraint.isInfinity(atLeast12.toExclusive()))
    assertFalse(ApiConstraint.isInfinity(atLeast12.fromInclusive()))
    assertFalse(ApiConstraint.isInfinity(below16_2.toExclusive()))
    assertTrue(ApiConstraint.isInfinity(ApiConstraint.ALL.toExclusive()))

    assertTrue(atLeast12.isOpenEnded())
    assertFalse(range_15_to_22.isOpenEnded())
    assertTrue((exactly12 or atLeast15_2).isOpenEnded())
    assertFalse(below16_2.isOpenEnded())
  }

  @Test
  fun testMinor() {
    assertEquals("15.2 ≤ x < 19.1", p(range(15, 2, 19, 1)))
    assertEquals("x = 15.1", p(exactly(15, 1)))
    assertEquals("x ≠ 15.2", p(exactly(15, 2).not()))
    assertEquals("x ≠ 15.1", p(exactly(15, 1).not()))
    assertEquals("x = 15.1", p(exactly(15, 1).not().not()))

    assertEquals(exactly(15, 1), Intervals.deserialize(exactly(15, 1).serialize()))

    assertTrue(range(15, 0, 19, 1).contains(15))
    assertFalse(range(15, 2, 19, 1).contains(15))
    assertTrue(range(15, 2, 19, 1).contains(16))
    assertTrue(range(15, 2, 19, 1).contains(19))
    assertFalse(range(15, 2, 19, 1).contains(20))
    assertFalse(range(15, 2, 19, 0).contains(19))

    assertEquals("11.2 ≤ x < 13", p(range(10, 1, 13, 0) and range(11, 2, 15, 3)))
    assertEquals("10.1 ≤ x < 15.3", p(range(10, 1, 13, 0) or range(11, 2, 15, 3)))
  }
}
