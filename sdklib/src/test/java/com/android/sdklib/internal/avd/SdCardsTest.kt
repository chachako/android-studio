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

import com.android.sdklib.devices.Storage
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SdCardsTest {
  @Test
  fun testParseSdCard() {
    assertThat(parseSdCard("1G")).isEqualTo(InternalSdCard(1024 * 1024 * 1024))
    assertThat(parseSdCard("300M")).isEqualTo(InternalSdCard(300 * 1024 * 1024))
    assertThat(parseSdCard("1000000K")).isEqualTo(InternalSdCard(1000000 * 1024))
    assertThat(parseSdCard("4")).isEqualTo(ExternalSdCard("4"))
    assertThat(runCatching { parseSdCard("1K") }.exceptionOrNull())
      .isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun testSdCardFromConfig() {
    assertThat(
        sdCardFromConfig(
          mapOf(ConfigKey.SDCARD_PATH to "/tmp/sdcard", ConfigKey.SDCARD_SIZE to "300M")
        )
      )
      .isEqualTo(ExternalSdCard("/tmp/sdcard"))

    assertThat(sdCardFromConfig(mapOf(ConfigKey.SDCARD_SIZE to "300M")))
      .isEqualTo(InternalSdCard(Storage(300, Storage.Unit.MiB).size))
  }
}
