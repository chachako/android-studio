/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.ide.common.resources

import com.android.ide.common.util.PathString
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.awt.Color

class ResourcesUtilTest {
  @Test
  fun testFlattenResourceName() {
    assertEquals("", resourceNameToFieldName(""))
    assertEquals("my_key_test", resourceNameToFieldName("my.key:test"))
    assertEquals("my_key_test", resourceNameToFieldName("my_key_test"))
    assertEquals("_key_test", resourceNameToFieldName(".key_test"))
    assertEquals("_key_test_", resourceNameToFieldName(".key_test:"))
    assertEquals("_key test_", resourceNameToFieldName("-key test:"))
  }

  @Test
  fun testToFileResourcePathString() {
    assertThat(toFileResourcePathString("apk:///foo.apk!/bar.baz"))
        .isEqualTo(PathString("apk", "/foo.apk!/bar.baz"))
  }

  @Test
  fun testColorToString() {
    assertEquals("#0FFF0000", colorToString(Color(0x0fff0000, true)))
    assertEquals("#00FF00", colorToString(Color(0x00ff00)))
    assertEquals("#00000000", colorToString(Color(0x00000000, true)))
    assertEquals("#F0112233", colorToString(Color(0x11, 0x22, 0x33, 0xf0)))
    assertEquals("#00FFFFFF", colorToString(Color(0xff, 0xff, 0xff, 0x00)))
  }

  @Test
  fun testColorToStringWithAlpha() {
    assertEquals("0x0FFF0000", colorToStringWithAlpha(Color(0x0fff0000, true)))
    assertEquals("0xFF00FF00", colorToStringWithAlpha(Color(0x00ff00)))
    assertEquals("0x00000000", colorToStringWithAlpha(Color(0x00000000, true)))
    assertEquals("0xF0112233", colorToStringWithAlpha(Color(0x11, 0x22, 0x33, 0xf0)))
    assertEquals("0x00FFFFFF", colorToStringWithAlpha(Color(0xff, 0xff, 0xff, 0x00)))
  }

  @Test
  fun testParseColor() {
    assertEquals(-0xff00bc, parseColor("#0f4")!!.rgb)
    assertEquals(0x11223377, parseColor("#1237")!!.rgb)
    assertEquals(-0xedcbaa, parseColor("#123456")!!.rgb)
    assertEquals(0x08123456, parseColor("#08123456")!!.rgb)

    // Test that spaces are correctly trimmed
    assertEquals(-0xff00bc, parseColor("#0f4 ")!!.rgb)
    assertEquals(0x11223377, parseColor(" #1237")!!.rgb)
    assertEquals(-0xedcbaa, parseColor("#123456\n\n ")!!.rgb)
    assertNull(parseColor("#123 456"))
  }
}
