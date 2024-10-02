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

import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.internal.avd.AvdNames.cleanAvdName
import com.android.sdklib.internal.avd.AvdNames.isValid
import com.android.sdklib.internal.avd.AvdNames.cleanDisplayName
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.utils.NullLogger
import com.google.common.truth.Truth.assertThat
import kotlin.io.path.createDirectories
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
  fun testCleanDisplayName() {
    assertThat(cleanDisplayName("Simple")).isEqualTo("Simple")
    assertThat(cleanDisplayName("this.name is-also_(OK) 45"))
      .isEqualTo("this.name is-also_(OK) 45")

    assertThat(cleanDisplayName("either/or")).isEqualTo("either or")
    assertThat(cleanDisplayName("9\" nails")).isEqualTo("9 nails")
    assertThat(cleanDisplayName("6' under")).isEqualTo("6 under")
  }

  @Test
  fun testCleanAvdName() {
    assertThat(cleanAvdName("")).isEqualTo("myavd")
    assertThat(cleanAvdName("Simple")).isEqualTo("Simple")
    assertThat(cleanAvdName("no_change.f0r_this-string")).isEqualTo("no_change.f0r_this-string")

    assertThat(cleanAvdName(" ")).isEqualTo("myavd")
    assertThat(cleanAvdName("this.name is-also_(OK) 45")).isEqualTo("this.name_is-also_OK_45")
    assertThat(cleanAvdName("  either/or _ _more ")).isEqualTo("either_or_more")
    assertThat(cleanAvdName("9\" nails__  ")).isEqualTo("9_nails")
    assertThat(cleanAvdName("'6' under'")).isEqualTo("6_under")
    assertThat(cleanAvdName("Pixel (2)")).isEqualTo("Pixel_2")
    assertThat(cleanAvdName("__Name_")).isEqualTo("Name")
  }

  @Test
  fun testUniquify() {
    val names = setOf("Test", "Test 2", "Test 3")
    assertThat(AvdNames.uniquify("Test", " ") { it in names }).isEqualTo("Test 4")
  }

  @Test
  fun testUniquifyAvdFolder() {
    val root = createInMemoryFileSystemAndFolder("root")
    val sdk = root.resolve("sdk").createDirectories()
    val avds = root.resolve("avds").createDirectories()
    val sdkHandler = AndroidSdkHandler(sdk, avds)
    val avdManager =
      AvdManager.createInstance(
        sdkHandler,
        avds,
        DeviceManager.createInstance(sdkHandler, NullLogger()),
        NullLogger(),
      )

    avds.resolve("Pixel.avd").createDirectories()
    avds.resolve("Pixel_2.avd").createDirectories()

    assertThat(avdManager.uniquifyAvdFolder("Pixel").toString())
      .isEqualTo(avds.resolve("Pixel_3.avd").toString())
    // We could perhaps be smarter about this
    assertThat(avdManager.uniquifyAvdFolder("Pixel_2").toString())
      .isEqualTo(avds.resolve("Pixel_2_2.avd").toString())
  }
}
