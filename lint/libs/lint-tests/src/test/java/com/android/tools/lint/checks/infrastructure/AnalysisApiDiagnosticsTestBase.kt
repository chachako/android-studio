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
import com.android.tools.lint.checks.infrastructure.TestFiles.bytecode
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.jetbrains.kotlin.cli.common.arguments.JavaTypeEnhancementStateParser
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.JvmAnalysisFlags
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.config.toKotlinVersion

internal interface AnalysisApiDiagnosticsTestBase {

  fun checkDiagnostics_NullableFromJava_jspecify(
    expectedMessage: String,
    kotlinLanguageVersion: String? = null
  ) {
    lint()
      .apply {
        val languageLevel =
          LanguageVersion.fromVersionString(kotlinLanguageVersion) ?: LanguageVersion.LATEST_STABLE
        val apiVersion = ApiVersion.createByLanguageVersion(languageLevel)
        kotlinLanguageLevel =
          LanguageVersionSettingsImpl(
            languageLevel,
            apiVersion,
            // TODO: need to pass/parse (compiler) CLI argument
            // -Xjspecify-annotations=strict
            mapOf(
              JvmAnalysisFlags.javaTypeEnhancementState to
                JavaTypeEnhancementStateParser(
                    MessageCollector.NONE,
                    languageLevel.toKotlinVersion()
                  )
                  .parse(
                    jsr305Args = null,
                    supportCompatqualCheckerFrameworkAnnotations = null,
                    jspecifyState = "strict",
                    nullabilityAnnotations = null
                  )
            ),
            // TODO: need to pass/parse (compiler) CLI argument
            // -Xtype-enhancement-improvements-strict-mode
            mapOf(
              LanguageFeature.TypeEnhancementImprovementsInStrictMode to
                LanguageFeature.State.ENABLED
            )
          )
      }
      .files(
        kotlin(
            "src/main.kt",
            """
          import p.J

          fun go(j: J) = j.s().length
      """
          )
          .indented(),
        java(
            "src/p/J.java",
            """
          package p;
          import org.jspecify.annotations.Nullable;

          public interface J {
            @Nullable String s();
          }
        """
          )
          .indented(),
        bytecode(
          "libs/jspecify.jar",
          java(
            "src/org/jspecify/annotations/Nullable.java",
            """
              package org.jspecify.annotations;
              import static java.lang.annotation.ElementType.TYPE_USE;
              import static java.lang.annotation.RetentionPolicy.RUNTIME;
              import java.lang.annotation.Retention;
              import java.lang.annotation.Target;

              @Target(TYPE_USE)
              @Retention(RUNTIME)
              public @interface Nullable {}
            """
          ),
          0x8eacd2bc,
          """
                org/jspecify/annotations/Nullable.class:
                H4sIAAAAAAAA/4WMzUrDQBSFz63W1Gq1LkXEn0WXzgOICxcpCFpLmgriQqbh
                GiZMJyWZFPJqLnwAH0q8ETSbggMz98y53zmfX+8fAG5wFKBDuMiLVGXlihPz
                VivtXO61N7kr1aSyVi8sB9gmDDO91spql6rHRcaJD7BDOGvdNqlu/yShP8ur
                IuGxsUwY/FZeNTnCcVQ5b5b8ZEojbpsrCaf3G7tjXaTsrwndtbaVdF5u5kLL
                S3Y+rlcscC9+noav81lION/MR+wFFyX06B9kmluT1AIG0XwS3z2EIwJhS24X
                zekg+Hl72JV5Iqovu70XEGMfAxw0P8Yhht//N/zNjAEAAA==
                """
        )
      )
      .issues(TestDiagnosticsDetector.ID)
      .testModes(TestMode.DEFAULT)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expect(expectedMessage)
  }

  fun checkDiagnostics_NullableFromJava_androidx(expectedMessage: String) {
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
