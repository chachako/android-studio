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

import junit.framework.Assert
import junit.framework.TestCase

/** Tests for [AndroidVersionUtil] */
class AndroidVersionUtilTest : TestCase() {

  fun testFromProperties() {
    val androidVersion =
      AndroidVersionUtil.androidVersionFromDeviceProperties(
        mapOf(
          "ro.build.version.sdk" to "24",
        )
      )
    Assert.assertEquals(androidVersion, AndroidVersion(24, null, null, true))
  }

  fun testFromProperties_r() {
    val androidVersion =
      AndroidVersionUtil.androidVersionFromDeviceProperties(
        mapOf(
          "ro.build.version.sdk" to "30",
          "build.version.extensions.r" to "0",
        )
      )
    Assert.assertEquals(androidVersion, AndroidVersion(30, null, null, true))
  }

  fun testFromProperties_tiramisuSidegrade() {
    val androidVersion =
      AndroidVersionUtil.androidVersionFromDeviceProperties(
        mapOf(
          "ro.build.version.sdk" to "33",
          "build.version.extensions.r" to "5",
          "build.version.extensions.s" to "5",
          "build.version.extensions.t" to "3",
        )
      )
    Assert.assertEquals(androidVersion, AndroidVersion(33, null, 3, true))
  }

  fun testFromProperties_tiramisu_ext4() {
    val androidVersion =
      AndroidVersionUtil.androidVersionFromDeviceProperties(
        mapOf(
          "ro.build.version.sdk" to "33",
          "build.version.extensions.r" to "4",
          "build.version.extensions.s" to "4",
          "build.version.extensions.t" to "4",
        )
      )
    Assert.assertEquals(androidVersion, AndroidVersion(33, null, 4, true))
  }

  fun testFromProperties_tiramisu_udc() {
    val androidVersion =
      AndroidVersionUtil.androidVersionFromDeviceProperties(
        mapOf(
          "ro.build.version.sdk" to "33",
          "ro.build.version.codename" to "UpsideDownCake",
          "build.version.extensions.r" to "5",
          "build.version.extensions.s" to "5",
          "build.version.extensions.t" to "5",
        )
      )
    Assert.assertEquals(androidVersion, AndroidVersion(33, "UpsideDownCake", 5, true))
  }

  fun testFromProperties_noSdk() {
    val androidVersion = AndroidVersionUtil.androidVersionFromDeviceProperties(mapOf())
    Assert.assertNull(androidVersion)
  }

  fun testFromProperties_badSdk() {
    val androidVersion =
      AndroidVersionUtil.androidVersionFromDeviceProperties(
        mapOf(
          "ro.build.version.sdk" to "NaN",
        )
      )
    Assert.assertNull(androidVersion)
  }

  fun testFromProperties_badExtension() {
    val androidVersion =
      AndroidVersionUtil.androidVersionFromDeviceProperties(
        mapOf(
          "ro.build.version.sdk" to "33",
          "build.version.extensions.r" to "NaN",
        )
      )
    Assert.assertEquals(androidVersion, AndroidVersion(33, null, null, true))
  }
}
