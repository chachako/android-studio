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
import org.jetbrains.uast.UastFacade
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AnalysisApiFirDiagnosticsTest : AbstractCheckTest(), AnalysisApiDiagnosticsTestBase {
  companion object {
    private var lastKey: String? = null

    @BeforeClass
    @JvmStatic
    fun setup() {
      lastKey = System.getProperty(FIR_UAST_KEY, "false")
      System.setProperty(FIR_UAST_KEY, "true")
      UastFacade.clearCachedPlugin()
    }

    @AfterClass
    @JvmStatic
    fun teardown() {
      lastKey?.let { System.setProperty(FIR_UAST_KEY, it) }
      lastKey = null
      UastEnvironment.disposeApplicationEnvironment()
    }
  }

  override fun getDetector(): Detector {
    return TestDiagnosticsDetector()
  }

  @Test
  fun testDiagnostics_NullableFromJava_jspecify() {
    checkDiagnostics_NullableFromJava_jspecify(
      """
      src/main.kt:3: Warning: $NULLNESS_MESSAGE @R|org/jspecify/annotations/Nullable|()  kotlin/String? [KotlinCompilerDiagnostic]
      fun go(j: J) = j.s().length
                     ~~~~~~~~~~~~
      0 errors, 1 warnings
      """,
      "2.0"
    )
  }

  @Test
  fun testDiagnostics_NullableFromJava_androidx() {
    if (isWindows()) {
      // TODO(jsjeon): investigate why it fails only in Windows
      //  * Type argument is not within its bounds: should be subtype of
      //  '@R|androidx/annotation/Nullable|()  Object?'
      //  * Unresolved reference: compareTo.
      return
    }
    checkDiagnostics_NullableFromJava_androidx(
      """
      src/main.kt:7: Warning: $NULLNESS_MESSAGE kotlin/String? [KotlinCompilerDiagnostic]
        val unused = v.compareTo("foo")
                     ~~~~~~~~~~~~~~~~~~
      0 errors, 1 warnings
      """
    )
  }

  @Test
  fun testDiagnostics_NullableFromKt() {
    checkDiagnostics_NullableFromKt(
      """
       src/main.kt:7: Warning: $NULLNESS_MESSAGE kotlin/String? [KotlinCompilerDiagnostic]
         val unused = v.compareTo("foo")
                      ~~~~~~~~~~~~~~~~~~
       0 errors, 1 warnings
      """
    )
  }
}
