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
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Runs tests using a [AndroidEngine] */
@Ignore
@RunWith(Parameterized::class)
internal class AndroidEngineDebuggerTest(testClass: String) :
  DebuggerTestBase(testClass, AndroidEngine(emulatorRule.emulator.serialNumber)) {

  companion object {

    @JvmStatic @Parameterized.Parameters fun getTestClasses() = Resources.findTestClasses()

    @JvmStatic @get:ClassRule val emulatorRule = EmulatorRule()
  }

  @Test
  fun localVariablesTest() {
    runLocalVariablesTest()
  }

  @Test
  fun inlineFramesTest() {
    runInlineFramesTest()
  }
}
