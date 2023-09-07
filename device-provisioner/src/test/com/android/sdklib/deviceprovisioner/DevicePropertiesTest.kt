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
import com.google.wireless.android.sdk.stats.DeviceInfo
import org.junit.Assert.fail
import org.junit.Test

class DevicePropertiesTest {
  @Test
  fun readCommonProperties() {
    val props =
      props(
        "ro.manufacturer" to "Google",
        "ro.product.model" to "Pixel 5",
        "ro.build.version.sdk" to "29",
        "ro.product.cpu.abi" to SdkConstants.ABI_ARM64_V8A,
        "ro.build.version.release" to "10",
        "ro.build.characteristics" to "watch,nosdcard",
        "ro.build.type" to "userdebug",
        "ro.kernel.qemu" to "1",
      )

    assertThat(props.manufacturer).isEqualTo("Google")
    assertThat(props.model).isEqualTo("Pixel 5")
    assertThat(props.androidVersion).isEqualTo(AndroidVersion(29))
    assertThat(props.androidRelease).isEqualTo("10")
    assertThat(props.primaryAbi).isEqualTo(Abi.ARM64_V8A)
    assertThat(props.deviceType).isEqualTo(DeviceType.WEAR)
    assertThat(props.isVirtual).isTrue()
    assertThat(props.isDebuggable).isTrue()
  }

  @Test
  fun readAbiList() {
    val props =
      props(
        "ro.product.cpu.abilist" to "${SdkConstants.ABI_ARM64_V8A},${SdkConstants.ABI_ARMEABI_V7A}"
      )
    assertThat(props.primaryAbi).isEqualTo(Abi.ARM64_V8A)
    assertThat(props.abiList).containsExactly(Abi.ARM64_V8A, Abi.ARMEABI_V7A)
  }

  @Test
  fun readAbi2() {
    val props =
      props(
        "ro.product.cpu.abi" to SdkConstants.ABI_ARMEABI_V7A,
        "ro.product.cpu.abi2" to SdkConstants.ABI_ARMEABI,
      )
    assertThat(props.primaryAbi).isEqualTo(Abi.ARMEABI_V7A)
    assertThat(props.abiList).containsExactly(Abi.ARMEABI_V7A, Abi.ARMEABI)
  }

  @Test
  fun readIsVirtual() {
    assertThat(props().isVirtual).isFalse()
    // Some Samsung physical devices do this:
    assertThat(props("ro.kernel.qemu" to "0").isVirtual).isFalse()
    assertThat(props("ro.kernel.qemu" to "1").isVirtual).isTrue()
  }

  @Test
  fun parseMdnsConnectionType_notMdns() {
    SerialNumberAndMdnsConnectionType.fromAdbSerialNumber("435DT06WH").apply {
      assertThat(serialNumber).isEqualTo("435DT06WH")
      assertThat(mdnsConnectionType).isEqualTo(DeviceInfo.MdnsConnectionType.MDNS_NONE)
    }
  }

  @Test
  fun parseMdnsConnectionType_clear() {
    SerialNumberAndMdnsConnectionType.fromAdbSerialNumber("adb-435DT06WH-vWgJpq._adb._tcp.").apply {
      assertThat(serialNumber).isEqualTo("435DT06WH")
      assertThat(mdnsConnectionType)
        .isEqualTo(DeviceInfo.MdnsConnectionType.MDNS_AUTO_CONNECT_UNENCRYPTED)
    }
  }

  @Test
  fun parseMdnsConnectionType_tls() {
    SerialNumberAndMdnsConnectionType.fromAdbSerialNumber(
        "adb-435DT06WH-vWgJpq._adb-tls-connect._tcp."
      )
      .apply {
        assertThat(serialNumber).isEqualTo("435DT06WH")
        assertThat(mdnsConnectionType)
          .isEqualTo(DeviceInfo.MdnsConnectionType.MDNS_AUTO_CONNECT_TLS)
      }
  }

  @Test
  fun build() {
    try {
      DeviceProperties.build { icon = EmptyIcon.DEFAULT }
      fail("Expected exception")
    } catch (expected: Exception) {}
  }

  private fun props(vararg pairs: Pair<String, String>) =
    DeviceProperties.buildForTest {
      readCommonProperties(mapOf(*pairs))
      icon = EmptyIcon.DEFAULT
    }
}
