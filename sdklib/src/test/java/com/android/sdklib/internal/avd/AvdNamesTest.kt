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
package com.android.sdklib.internal.avd

import com.android.sdklib.internal.avd.AvdNames.isValid
import com.android.sdklib.internal.avd.AvdNames.stripBadCharacters
import com.android.sdklib.internal.avd.AvdNames.stripBadCharactersAndCollapse
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Tests for [AvdNames] */
class AvdNamesTest {
  @Test
  fun testIsValid() {
    assertThat(isValid("Simple")).isTrue()
    assertThat(isValid("this.name is-also_(OK) 45")).isTrue()

    assertThat(isValid("either/or")).isFalse()
    assertThat(isValid("9\" nails")).isFalse()
    assertThat(isValid("6' under")).isFalse()
    assertThat(isValid("")).isFalse()
  }

  @Test
  fun testStripBadCharacters() {
    assertThat(stripBadCharacters("Simple")).isEqualTo("Simple")
    assertThat(stripBadCharacters("this.name is-also_(OK) 45"))
      .isEqualTo("this.name is-also_(OK) 45")

    assertThat(stripBadCharacters("either/or")).isEqualTo("either or")
    assertThat(stripBadCharacters("9\" nails")).isEqualTo("9  nails")
    assertThat(stripBadCharacters("6' under")).isEqualTo("6  under")
  }

  @Test
  fun testStripBadCharactersAndCollapse() {
    assertThat(stripBadCharactersAndCollapse("")).isEqualTo("")
    assertThat(stripBadCharactersAndCollapse("Simple")).isEqualTo("Simple")
    assertThat(stripBadCharactersAndCollapse("no_change.f0r_this-string"))
      .isEqualTo("no_change.f0r_this-string")

    assertThat(stripBadCharactersAndCollapse(" ")).isEqualTo("")
    assertThat(stripBadCharactersAndCollapse("this.name is-also_(OK) 45"))
      .isEqualTo("this.name_is-also_OK_45")
    assertThat(stripBadCharactersAndCollapse("  either/or _ _more ")).isEqualTo("either_or_more")
    assertThat(stripBadCharactersAndCollapse("9\" nails__  ")).isEqualTo("9_nails_")
    assertThat(stripBadCharactersAndCollapse("'6' under'")).isEqualTo("6_under")
  }
}
