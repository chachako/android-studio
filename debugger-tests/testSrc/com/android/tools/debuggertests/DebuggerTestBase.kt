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

import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** A base class for Debugger Tests */
internal abstract class DebuggerTestBase(
  private val testClass: String,
  private val engine: Engine,
) {

  private fun describeTest() = "at $testClass(${testClass.substringAfterLast(".")}.kt:20)"

  fun runLocalVariablesTest() {
    val listener = LocalVariablesFrameListener()

    runBlocking { withTimeout(5.seconds) { engine.runTest(testClass, listener) } }

    val expected = Resources.readGolden(testClass, engine.vmName)
    val actual = listener.getText()
    assertThat(actual).named(describeTest()).isEqualTo(expected)
  }

  fun runInlineFramesTest() {
    val listener = InlineStackFrameFrameListener()

    runBlocking { withTimeout(5.seconds) { engine.runTest(testClass, listener) } }

    val expected = Resources.readGolden(testClass, "inline-stack-frames")
    val actual = listener.getText()
    assertThat(actual).named(describeTest()).isEqualTo(expected)
  }
}
