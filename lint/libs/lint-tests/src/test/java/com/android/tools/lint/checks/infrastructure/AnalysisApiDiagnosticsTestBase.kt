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

import com.android.tools.lint.checks.AbstractCheckTest
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint

internal interface AnalysisApiDiagnosticsTestBase {
  fun checkDiagnostics_NullableFromJava(expectedMessage: String) {
    lint()
      .files(
        kotlin(
            "src/main.kt",
            """
          @file:Suppress("UNUSED_VARIABLE")
          import my.flags.Flag

          fun nullableReceiver(flag: Flag<String>) {
            val v = flag.parsableStringValue()
            // BUG: Diagnostic contains:
            val unused = v.compareTo("foo")
          }
      """
          )
          .indented(),
        java(
            "src/my/flags/Flag.java",
            """
          package my.flags;
          import androidx.annotation.Nullable;

          public class Flag<T extends @Nullable Object> {
            volatile T value;

            public T get() {
              return value;
            }

            public final @Nullable String parsableStringValue() {
              T value = get();
              return (value != null) ? parsableStringValue(value) : null;
            }

            String parsableStringValue(T value) {
              return value.toString();
            }
          }
        """
          )
          .indented(),
        AbstractCheckTest.SUPPORT_ANNOTATIONS_JAR
      )
      .issues(TestDiagnosticsDetector.ID)
      .testModes(TestMode.DEFAULT)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expect(expectedMessage)
  }

  fun checkDiagnostics_NullableFromKt(expectedMessage: String) {
    lint()
      .files(
        kotlin(
            "src/main.kt",
            """
          @file:Suppress("UNUSED_VARIABLE")
          import my.flags.Flag

          fun nullableReceiver(flag: Flag<String>) {
            val v = flag.parsableStringValue()
            // BUG: Diagnostic contains:
            val unused = v.compareTo("foo")
          }
        """
          )
          .indented(),
        kotlin(
            "src/my/flags/Flag.kt",
            """
          package my.flags

          class Flag<T>(val value: T) {
            fun parsableStringValue(): String? {
              return value?.let { parsableStringValue(it) }
            }

            internal fun parsableStringValue(value: T): String {
              return value!!.toString()
            }
          }
        """
          )
          .indented()
      )
      .issues(TestDiagnosticsDetector.ID)
      .testModes(TestMode.DEFAULT)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expect(expectedMessage)
  }
}
