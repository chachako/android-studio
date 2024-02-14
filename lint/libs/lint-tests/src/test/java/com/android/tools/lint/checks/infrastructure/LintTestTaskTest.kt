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

package com.android.tools.lint.checks.infrastructure

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.junit.Test

class LintTestTaskTest {

  @Test
  fun checkFlagsAcrossTestModes() {
    // Regression test for: https://issuetracker.google.com/323703301
    // Lint tests will run multiple times in different test modes, which sometimes involves creating
    // a fresh TestLintClient. There was previously a bug where LintCliFlags would not always
    // propagate to the fresh client, meaning that flag values were different in some test modes.
    // Some of the flags values end up in LintDriver, so we can test this by checking LintDriver
    // properties.
    lint()
      .configureOptions { flags -> flags.isCheckTestSources = true }
      .allowMissingSdk()
      .files(
        kotlin(
            """
                    fun foo() {
                        hello()
                    }

                    fun hello() {
                    }
"""
          )
          .indented()
      )
      .issues(MyCheckFlagsDetector.ISSUE)
      .run()
      .expect(
        """
        src/test.kt:2: Warning: true [_MyCheckFlagsDetectorIssue]
            hello()
            ~~~~~~~
        0 errors, 1 warnings
"""
      )
  }

  class MyCheckFlagsDetector : Detector(), SourceCodeScanner {
    override fun getApplicableMethodNames(): List<String> {
      return listOf("hello")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
      context.report(ISSUE, node, context.getLocation(node), "${context.driver.checkTestSources}")
    }

    companion object {
      val ISSUE =
        Issue.create(
          id = "_MyCheckFlagsDetectorIssue",
          briefDescription = "Not applicable",
          explanation = "Not applicable",
          category = Category.CORRECTNESS,
          priority = 10,
          severity = Severity.WARNING,
          implementation = Implementation(MyCheckFlagsDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )
    }
  }
}
