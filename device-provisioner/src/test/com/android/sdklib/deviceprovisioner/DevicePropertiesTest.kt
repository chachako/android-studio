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
package com.android.sdklib.deviceprovisioner

import com.android.SdkConstants
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DevicePropertiesTest {
  @Test
  fun readCommonProperties() {
    val builder = DeviceProperties.Builder()
    builder.readCommonProperties(
      mapOf(
        "ro.manufacturer" to "Google",
        "ro.product.model" to "Pixel 5",
        "ro.build.version.sdk" to "29",
        "ro.product.cpu.abi" to SdkConstants.ABI_ARM64_V8A,
        "ro.build.version.release" to "10",
        "ro.build.characteristics" to "watch,nosdcard",
        "ro.kernel.qemu" to "1",
      )
    )
    val props = builder.buildBase()
    assertThat(props.manufacturer).isEqualTo("Google")
    assertThat(props.model).isEqualTo("Pixel 5")
    assertThat(props.androidVersion).isEqualTo(AndroidVersion(29))
    assertThat(props.androidRelease).isEqualTo("10")
    assertThat(props.abi).isEqualTo(Abi.ARM64_V8A)
    assertThat(props.deviceType).isEqualTo(DeviceType.WEAR)
    assertThat(props.isVirtual).isTrue()
  }

  @Test
  fun readAndroidVersion_r() {
    assertThat(
        readAndroidVersion(
          mapOf(
            "ro.build.version.sdk" to "30",
            "build.version.extensions.r" to "0",
          )
        )
      )
      .isEqualTo(AndroidVersion(30, null, null, true))
  }

  @Test
  fun readAndroidVersion_tiramisuSidegrade() {
    assertThat(
        readAndroidVersion(
          mapOf(
            "ro.build.version.sdk" to "33",
            "build.version.extensions.r" to "5",
            "build.version.extensions.s" to "5",
            "build.version.extensions.t" to "3",
          )
        )
      )
      .isEqualTo(AndroidVersion(33, null, 3, true))
  }

  @Test
  fun readAndroidVersion_tiramisu_ext4() {
    assertThat(
        readAndroidVersion(
          mapOf(
            "ro.build.version.sdk" to "33",
            "build.version.extensions.r" to "4",
            "build.version.extensions.s" to "4",
            "build.version.extensions.t" to "4",
          )
        )
      )
      .isEqualTo(AndroidVersion(33, null, 4, false))
  }

  @Test
  fun readAndroidVersion_udc() {
    assertThat(
        readAndroidVersion(
          mapOf(
            "ro.build.version.sdk" to "33",
            "ro.build.version.codename" to "UpsideDownCake",
            "build.version.extensions.r" to "5",
            "build.version.extensions.s" to "5",
            "build.version.extensions.t" to "5",
          )
        )
      )
      .isEqualTo(AndroidVersion(33, "UpsideDownCake", 5, true))
  }
}
