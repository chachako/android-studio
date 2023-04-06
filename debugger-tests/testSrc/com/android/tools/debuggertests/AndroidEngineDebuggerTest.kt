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
package com.android.tools.debuggertests

import com.android.tools.asdriver.tests.EmulatorRule
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Test

class AndroidEngineDebuggerTest {

  companion object {

    @JvmStatic @get:ClassRule val emulatorRule = EmulatorRule()
  }

  @Test
  fun runEmulatorTest1() {
    println("Test1")
    assertThat(emulatorRule.emulator.serialNumber).startsWith("emulator-")
  }

  @Test
  fun runEmulatorTest2() {
    println("Test2")
    assertThat(emulatorRule.emulator.serialNumber).startsWith("emulator-")
  }
}
