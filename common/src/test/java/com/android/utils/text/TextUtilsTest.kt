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
package com.android.utils.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests for functions in `TextUtils.kt`. */
@RunWith(JUnit4::class)
class TextUtilsTest {
  @Test
  fun toCommaSeparatedList() {
    val strList = listOf("a", "b", "c")
    assertThat(strList.toCommaSeparatedList("and")).isEqualTo("a, b and c")
    assertThat(strList.toCommaSeparatedList("or")).isEqualTo("a, b or c")
    assertThat(strList.toCommaSeparatedList("fnord", oxfordComma = true)).isEqualTo("a, b, fnord c")
    val numSet = setOf(1, 2, 3)
    assertThat(numSet.toCommaSeparatedList("and")).isEqualTo("1, 2 and 3")
    assertThat(numSet.toCommaSeparatedList("or")).isEqualTo("1, 2 or 3")
    assertThat(numSet.toCommaSeparatedList("fnord", oxfordComma = true)).isEqualTo("1, 2, fnord 3")
  }
}
