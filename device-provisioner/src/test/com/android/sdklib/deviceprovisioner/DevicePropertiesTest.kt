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
        "ro.build.version.sdk" to "33",
        "ro.product.cpu.abi" to SdkConstants.ABI_ARM64_V8A,
        "ro.build.version.release" to "13",
        "ro.build.characteristics" to "watch,nosdcard"
      )
    )
    val props = builder.buildBase()
    assertThat(props.manufacturer).isEqualTo("Google")
    assertThat(props.model).isEqualTo("Pixel 5")
    assertThat(props.androidVersion).isEqualTo(AndroidVersion(33))
    assertThat(props.androidRelease).isEqualTo("13")
    assertThat(props.abi).isEqualTo(Abi.ARM64_V8A)
    assertThat(props.deviceType).isEqualTo(DeviceType.WEAR)
  }
}
