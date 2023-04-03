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

import com.android.tools.debuggertests.Engine.EngineType.JVM
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/** Runs tests using a [JvmEngine] */
@RunWith(Parameterized::class)
internal class JvmEngineDebuggerTest(testClass: String) : DebuggerTestBase(testClass) {

  companion object {

    @JvmStatic @Parameters fun getTestClasses() = Resources.findTestClasses()
  }

  @Test
  fun test() {
    val actual = runBlocking { withTimeout(5.seconds) { JVM.getEngine(testClass).runTest() } }
    val expected = Resources.readGolden(testClass)

    assertThat(actual).named(describeTest()).isEqualTo(expected)
  }
}
