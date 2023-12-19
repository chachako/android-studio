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
package com.android.sdklib

import com.android.sdklib.AndroidVersionUtil.androidVersionFromDeviceProperties
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** Tests for [AndroidVersionUtil] */
class AndroidVersionUtilTest {
  @Test
  fun testFromProperties() {
    assertStrictlyEqual(
      androidVersionFromDeviceProperties(
        mapOf(
          "ro.build.version.sdk" to "24",
        )
      ),
      AndroidVersion(24, null, null, true)
    )
  }

  @Test
  fun testFromProperties_r() {
    assertStrictlyEqual(
      androidVersionFromDeviceProperties(
        mapOf(
          "ro.build.version.sdk" to "30",
          "build.version.extensions.r" to "0",
        )
      ),
      AndroidVersion(30, null, null, true)
    )
  }

  @Test
  fun testFromProperties_tiramisuSidegrade() {
    assertStrictlyEqual(
      androidVersionFromDeviceProperties(
        mapOf(
          "ro.build.version.sdk" to "33",
          "build.version.extensions.r" to "5",
          "build.version.extensions.s" to "5",
          "build.version.extensions.t" to "3",
        )
      ),
      AndroidVersion(33, null, 3, true)
    )
  }

  @Test
  fun testFromProperties_tiramisu_ext4() {
    assertStrictlyEqual(
      androidVersionFromDeviceProperties(
        mapOf(
          "ro.build.version.sdk" to "33",
          "build.version.extensions.r" to "4",
          "build.version.extensions.s" to "4",
          "build.version.extensions.t" to "4",
        )
      ),
      AndroidVersion(33, null, 4, false)
    )
  }

  @Test
  fun testFromProperties_tiramisu_udc() {
    assertStrictlyEqual(
      androidVersionFromDeviceProperties(
        mapOf(
          "ro.build.version.sdk" to "33",
          "ro.build.version.codename" to "UpsideDownCake",
          "build.version.extensions.r" to "5",
          "build.version.extensions.s" to "5",
          "build.version.extensions.t" to "5",
        )
      ),
      AndroidVersion(33, "UpsideDownCake", 5, true)
    )
  }

  @Test
  fun testFromProperties_api34_ext10() {
    assertStrictlyEqual(
      androidVersionFromDeviceProperties(
        mapOf(
          "ro.build.version.sdk" to "34",
          "ro.build.version.codename" to "REL",
          "build.version.extensions.r" to "10",
          "build.version.extensions.s" to "10",
          "build.version.extensions.t" to "10",
          "build.version.extensions.u" to "10",
          "build.version.extensions.ad_services" to "10",
        )
      ),
      AndroidVersion(34, null, 10, false)
    )
  }

  @Test
  fun testFromProperties_noSdk() {
    assertThat(androidVersionFromDeviceProperties(mapOf())).isNull()
  }

  @Test
  fun testFromProperties_badSdk() {
    assertThat(
        androidVersionFromDeviceProperties(
          mapOf(
            "ro.build.version.sdk" to "NaN",
          )
        )
      )
      .isNull()
  }

  @Test
  fun testFromProperties_badExtension() {
    assertStrictlyEqual(
      androidVersionFromDeviceProperties(
        mapOf(
          "ro.build.version.sdk" to "33",
          "build.version.extensions.r" to "NaN",
        )
      ),
      AndroidVersion(33, null, null, true)
    )
  }
}

/**
 * AndroidVersion.equals() is a bit loose: it ignores isBaseExtension if the extensionLevels are
 * equal. Here, we want to know that we read the AndroidVersion exactly as expected, not just
 * equivalent.
 */
private fun assertStrictlyEqual(actual: AndroidVersion?, expected: AndroidVersion) {
  checkNotNull(actual)
  assertThat(actual).isEqualTo(expected)
  assertWithMessage("AndroidVersion.isBaseExtension")
    .that(actual.isBaseExtension)
    .isEqualTo(expected.isBaseExtension)
  assertWithMessage("AndroidVersion.extensionLevel")
    .that(actual.extensionLevel)
    .isEqualTo(expected.extensionLevel)
}
