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

import com.android.SdkConstants
import com.android.SdkConstants.PLATFORM_WINDOWS
import com.android.repository.testframework.FakePackage.FakeLocalPackage
import com.android.sdklib.internal.avd.HardwareProperties.HardwarePropertyType
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.testutils.file.recordExistingFile
import com.android.utils.NullLogger
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EmulatorPackageTest {

  val sdkRoot = createInMemoryFileSystemAndFolder("sdk")
  val emulatorPath = sdkRoot.resolve("emulator")
  val emulatorPackage = EmulatorPackage(FakeLocalPackage("emulator", emulatorPath))

  @Suppress("PathAsIterable")
  @Test
  fun emulatorBinary() {
    val binaryPath =
      emulatorPath.resolve(
        if (SdkConstants.currentPlatform() == PLATFORM_WINDOWS) "emulator.exe" else "emulator"
      )

    assertThat(emulatorPackage.emulatorBinary).isNull()
    binaryPath.recordExistingFile()
    assertThat(emulatorPackage.emulatorBinary).isEqualTo(binaryPath)
  }

  @Test
  fun hardwareProperties() {
    emulatorPath
      .resolve(SdkConstants.FD_LIB)
      .resolve(SdkConstants.FN_HARDWARE_INI)
      .recordExistingFile(
        """
            name        = hw.cpu.arch
            type        = string
            default     = arm
            abstract    = CPU Architecture
            description = The CPU Architecture to emulator
        """
          .trimIndent()
      )

    val hardwareProperties = checkNotNull(emulatorPackage.getHardwareProperties(NullLogger()))
    val property = checkNotNull(hardwareProperties["hw.cpu.arch"])
    assertThat(property.name).isEqualTo("hw.cpu.arch")
    assertThat(property.type).isEqualTo(HardwarePropertyType.STRING)
    assertThat(property.default).isEqualTo("arm")
    assertThat(property.abstract).isEqualTo("CPU Architecture")
    assertThat(property.description).isEqualTo("The CPU Architecture to emulator")
  }
}
