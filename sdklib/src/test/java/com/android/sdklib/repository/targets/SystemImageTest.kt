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
package com.android.sdklib.repository.targets

import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.testutils.file.recordExistingFile
import com.google.common.truth.Truth.assertThat
import kotlin.io.path.createDirectories
import org.junit.Test

class SystemImageTest {
  @Test
  fun testReadAbisFromBuildProps_abiList() {
    val root = createInMemoryFileSystemAndFolder("system-images")
    val systemImageLocation = root.resolve("android-34").createDirectories()
    systemImageLocation
      .resolve("build.prop")
      .recordExistingFile(
        """
                ro.product.cpu.abilist=arm64-v8a,armeabi-v7a,armeabi
                ro.product.cpu.abi=mips64
                ro.product.cpu.abi2=mips
        """
          .trimIndent()
      )

    assertThat(SystemImage.readAbisFromBuildProps(systemImageLocation))
      .containsExactly("arm64-v8a", "armeabi-v7a", "armeabi")
      .inOrder()
  }

  @Test
  fun testReadAbisFromBuildProps_abi() {
    val root = createInMemoryFileSystemAndFolder("system-images")
    val systemImageLocation = root.resolve("android-34").createDirectories()
    systemImageLocation
      .resolve("build.prop")
      .recordExistingFile(
        """
                ro.product.cpu.abi=mips64
                ro.product.cpu.abi2=mips
        """
          .trimIndent()
      )

    assertThat(SystemImage.readAbisFromBuildProps(systemImageLocation))
      .containsExactly("mips64", "mips")
      .inOrder()
  }
}
