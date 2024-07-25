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
import java.nio.file.Paths
import org.junit.Test

class SkinTest {
  @Test
  fun testSkinFromConfig_noSkin() {
    assertThat(skinFromConfig(emptyMap())).isNull()
    assertThat(
        skinFromConfig(mapOf(ConfigKey.SKIN_PATH to "_no_skin", ConfigKey.SKIN_NAME to "_no_skin"))
      )
      .isNull()
  }

  @Test
  fun testSkinFromConfig_onDiskSkin() {
    val skinPath = Paths.get(System.getProperty("java.io.tmpdir"), "skins", "pixel_6")

    assertThat(
        skinFromConfig(
          mapOf(ConfigKey.SKIN_PATH to skinPath.toString(), ConfigKey.SKIN_NAME to "pixel_6")
        )
      )
      .isEqualTo(OnDiskSkin(skinPath))
  }

  @Test
  fun testSkinFromConfig_genericSkin() {
    assertThat(
        skinFromConfig(
          mapOf(ConfigKey.SKIN_NAME to "1000x1400", ConfigKey.SKIN_PATH to "1000x1400")
        )
      )
      .isEqualTo(GenericSkin(1000, 1400))
  }
}
