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
package com.android.tools.lint.detector.api

import com.android.tools.lint.detector.api.ApiConstraint.Companion.atLeast
import org.junit.Assert.*
import org.junit.Test

class ApiLevelTest {
  @Test
  fun testBasic() {
    assertEquals(16, ApiLevel(16).major)
    assertEquals(0, ApiLevel(16).minor)
    assertEquals(16, ApiLevel(16, 1).major)
    assertEquals(1, ApiLevel(16, 1).minor)
    assertEquals(ApiLevel(16), ApiLevel(16))
    assertEquals("API level ≥ 16", ApiLevel(16).atLeast().toString())
  }

  @Test
  fun testConstraints() {
    assertEquals("API level ≥ 16", atLeast(ApiLevel(16)).toString())
    assertEquals("API level ≥ 16", atLeast(ApiLevel(16, 0)).toString())
    assertEquals("API level ≥ 16.1", atLeast(ApiLevel(16, 1)).toString())
  }

  @Test
  fun testFromString() {
    assertEquals(21, ApiLevel.get("L").major)
    assertEquals(35, ApiLevel.get("VanillaIceCream").major)
    assertEquals(35, ApiLevel.get("VANILLA_ICE_CREAM").major)
    assertEquals(35, ApiLevel.get("VANILLA_ICE_CREAM_0").major)
    assertEquals(1, ApiLevel.get("VANILLA_ICE_CREAM_1").minor)
    assertEquals(35, ApiLevel.get("35.1").major)
    assertEquals(1, ApiLevel.get("35.1").minor)
  }

  @Test
  fun testBuildCode() {
    assertEquals(
      "android.os.Build.VERSION_CODES_FULL.VANILLA_ICE_CREAM_0",
      ApiLevel(35, 0).toSourceReference(),
    )
    assertEquals(
      "android.os.Build.VERSION_CODES_FULL.VANILLA_ICE_CREAM_1",
      ApiLevel(35, 1).toSourceReference(),
    )
    assertEquals("VANILLA_ICE_CREAM_1", ApiLevel(35, 1).toSourceReference(fullyQualified = false))
    // Future API levels: just put constant reference in
    assertEquals("80", ApiLevel(80).toSourceReference())
    assertEquals("80_000_01", ApiLevel(80, 1).toSourceReference())
    assertEquals("8000001", ApiLevel(80, 1).toSourceReference(kotlin = false))
  }

  @Test
  fun testComparison() {
    assertTrue(ApiLevel(16) == ApiLevel(16))
    assertTrue(ApiLevel(16) > ApiLevel(15))
    assertTrue(ApiLevel(16) < ApiLevel(17))
    assertTrue(ApiLevel(16) < ApiLevel(17))

    assertTrue(ApiLevel(16, 2) < ApiLevel(17))
    assertTrue(ApiLevel(16, 2) < ApiLevel(16, 3))
    assertTrue(ApiLevel(16, 0) < ApiLevel(16, 1))
    assertTrue(ApiLevel(16, 1) > ApiLevel(16, 0))
    assertTrue(ApiLevel(16, 1) > ApiLevel(16))
  }
}
