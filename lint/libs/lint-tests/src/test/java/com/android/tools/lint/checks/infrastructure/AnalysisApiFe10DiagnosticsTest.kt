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
package com.android.tools.lint.checks.infrastructure

import com.android.tools.lint.FIR_UAST_KEY
import com.android.tools.lint.UastEnvironment
import com.android.tools.lint.checks.AbstractCheckTest
import com.android.tools.lint.checks.infrastructure.TestDiagnosticsDetector.Companion.NULLNESS_MESSAGE
import com.android.tools.lint.detector.api.Detector
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Ignore("b/184100083: FE1.0 UastEnvironment does not record compiler diagnostics for now")
@RunWith(JUnit4::class)
class AnalysisApiFe10DiagnosticsTest : AbstractCheckTest(), AnalysisApiDiagnosticsTestBase {
  companion object {
    @BeforeClass
    @JvmStatic
    fun setup() {
      System.setProperty(FIR_UAST_KEY, "false")
    }

    @AfterClass
    @JvmStatic
    fun teardown() {
      UastEnvironment.disposeApplicationEnvironment()
    }
  }

  override fun getDetector(): Detector {
    return TestDiagnosticsDetector()
  }

  @Test
  fun testDiagnostics_NullableFromJava() {
    checkDiagnostics_NullableFromJava(
      """
      src/main.kt:7: Warning: $NULLNESS_MESSAGE String? [KotlinCompilerDiagnostic]
        val unused = v.compareTo("foo")
                      ~
      0 errors, 1 warnings
      """
    )
  }

  @Test
  fun testDiagnostics_NullableFromKt() {
    checkDiagnostics_NullableFromKt(
      """
      src/main.kt:7: Warning: $NULLNESS_MESSAGE String? [KotlinCompilerDiagnostic]
        val unused = v.compareTo("foo")
                      ~
      0 errors, 1 warnings
      """
    )
  }
}
