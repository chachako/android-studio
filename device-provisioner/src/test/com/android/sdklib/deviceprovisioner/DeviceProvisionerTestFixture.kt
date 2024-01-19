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
package com.android.sdklib.deviceprovisioner

import com.android.adblib.DeviceInfo
import com.android.adblib.DeviceList
import com.android.adblib.DevicePropertyNames
import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState
import com.android.adblib.testing.FakeAdbSession
import com.google.common.truth.Truth.assertThat

abstract class DeviceProvisionerTestFixture {
  protected val fakeSession = FakeAdbSession()

  protected val deviceIcons =
    DeviceIcons(EmptyIcon.DEFAULT, EmptyIcon.DEFAULT, EmptyIcon.DEFAULT, EmptyIcon.DEFAULT)

  protected object SerialNumbers {
    const val PHYSICAL1_USB = "X1058A"
    const val PHYSICAL2_USB = "X1BQ704RX2B"
    const val PHYSICAL2_WIFI = "adb-X1BQ704RX2B-VQ4ADB._adb-tls-connect._tcp."
    const val EMULATOR = "emulator-5554"

    val ALL = listOf(PHYSICAL1_USB, PHYSICAL2_USB, PHYSICAL2_WIFI, EMULATOR)
  }

  val baseProperties =
    mapOf(
      DevicePropertyNames.RO_BUILD_VERSION_SDK to "31",
      DevicePropertyNames.RO_PRODUCT_MANUFACTURER to "Google",
      DevicePropertyNames.RO_PRODUCT_MODEL to "Pixel 6",
      DevicePropertyNames.RO_SF_LCD_DENSITY to "320",
    )

  val devicePropertiesBySerial =
    mapOf(
      SerialNumbers.PHYSICAL1_USB to
        baseProperties + mapOf("ro.serialno" to SerialNumbers.PHYSICAL1_USB),
      SerialNumbers.PHYSICAL2_USB to
        baseProperties + mapOf("ro.serialno" to SerialNumbers.PHYSICAL2_USB),
      SerialNumbers.PHYSICAL2_WIFI to
        baseProperties + mapOf("ro.serialno" to SerialNumbers.PHYSICAL2_USB),
      SerialNumbers.EMULATOR to
        baseProperties +
          mapOf(
            "ro.serialno" to "EMULATOR31X3X7X0",
            DevicePropertyNames.RO_PRODUCT_MODEL to "sdk_goog3_x86_64",
          ),
    )

  init {
    for (serial in SerialNumbers.ALL) {
      fakeSession.deviceServices.configureDeviceProperties(
        DeviceSelector.fromSerialNumber(serial),
        devicePropertiesBySerial[serial]!!,
      )
      fakeSession.deviceServices.configureShellCommand(
        DeviceSelector.fromSerialNumber(serial),
        command = "wm size",
        stdout = "Physical size: 2000x1500\n",
      )
    }
  }

  protected fun setDevices(vararg serialNumber: String, state: DeviceState = DeviceState.ONLINE) {
    fakeSession.hostServices.devices =
      DeviceList(serialNumber.map { DeviceInfo(it, state) }, emptyList())
  }

  protected fun setBootComplete(serial: String) {
    fakeSession.deviceServices.configureDeviceProperties(
      DeviceSelector.fromSerialNumber(serial),
      devicePropertiesBySerial[serial]!! + mapOf("dev.bootcomplete" to "1"),
    )
  }

  protected fun DeviceProperties.checkPixel6lDeviceProperties() {
    assertThat(resolution).isEqualTo(Resolution(2000, 1500))
    assertThat(resolutionDp).isEqualTo(Resolution(1000, 750))

    deviceInfoProto.apply {
      assertThat(manufacturer).isEqualTo("Google")
      assertThat(model).isEqualTo("Pixel 6")
      assertThat(deviceType)
        .isEqualTo(com.google.wireless.android.sdk.stats.DeviceInfo.DeviceType.LOCAL_PHYSICAL)
    }
  }
}
