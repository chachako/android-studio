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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BootModeTest {
  @Test
  fun fromProperties_coldBoot() {
    assertThat(BootMode.fromProperties(mapOf(ConfigKey.FORCE_COLD_BOOT_MODE to "yes")))
      .isEqualTo(ColdBoot)
  }

  @Test
  fun fromProperties_quickBoot() {
    assertThat(BootMode.fromProperties(mapOf(ConfigKey.FORCE_FAST_BOOT_MODE to "yes")))
      .isEqualTo(QuickBoot)
  }

  @Test
  fun fromProperties_snapshotBoot() {
    assertThat(
        BootMode.fromProperties(
          mapOf(
            ConfigKey.FORCE_CHOSEN_SNAPSHOT_BOOT_MODE to "yes",
            ConfigKey.CHOSEN_SNAPSHOT_FILE to "snap",
          )
        )
      )
      .isEqualTo(BootSnapshot("snap"))
  }

  @Test
  fun fromProperties_default() {
    assertThat(
        BootMode.fromProperties(
          mapOf(
            ConfigKey.FORCE_COLD_BOOT_MODE to "no",
            ConfigKey.FORCE_FAST_BOOT_MODE to "no",
            ConfigKey.FORCE_CHOSEN_SNAPSHOT_BOOT_MODE to "no",
          )
        )
      )
      .isEqualTo(QuickBoot)
  }

  @Test
  fun roundtrip() {
    for (mode in listOf(QuickBoot, ColdBoot, BootSnapshot("snap"))) {
      assertThat(BootMode.fromProperties(mode.properties())).isEqualTo(mode)
    }
  }
}
