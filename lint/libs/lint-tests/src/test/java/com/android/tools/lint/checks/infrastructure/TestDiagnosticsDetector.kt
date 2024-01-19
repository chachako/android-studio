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
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile

internal class TestDiagnosticsDetector : Detector(), SourceCodeScanner {
  override fun getApplicableUastTypes(): List<Class<out UElement>> =
    listOf(UFile::class.java, UCallExpression::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler =
    object : UElementHandler() {
      override fun visitFile(node: UFile) {
        val ktFile = node.sourcePsi as? KtFile ?: return
        if (ktFile.name != "main.kt") return

        analyze(ktFile) {
          val diagnostics =
            ktFile.collectDiagnosticsForFile(KtDiagnosticCheckerFilter.EXTENDED_AND_COMMON_CHECKERS)
          assertEquals(
            1,
            diagnostics.size,
            diagnostics.joinToString(separator = System.lineSeparator()) { it.defaultMessage },
          )
          val diagnostic = diagnostics.single()
          assertTrue(
            diagnostic.defaultMessage.contains(NULLNESS_MESSAGE),
            diagnostic.defaultMessage,
          )
          context.report(ID, node, context.getLocation(diagnostic.psi), diagnostic.defaultMessage)
        }
      }

      override fun visitCallExpression(node: UCallExpression) {
        if (!useK2Uast) {
          // In AA FE1.0, diagnostics on dot-qualified expression are bound to _dot_ leaf node. :o
          return
        }
        if (node.methodName != "compareTo") return

        val ktSource = node.sourcePsi as? KtElement ?: return
        val withReceiver = ktSource.parent as? KtDotQualifiedExpression ?: return
        analyze(withReceiver) {
          val diagnostics =
            withReceiver.getDiagnostics(KtDiagnosticCheckerFilter.EXTENDED_AND_COMMON_CHECKERS)
          assertEquals(
            1,
            diagnostics.size,
            diagnostics.joinToString(separator = System.lineSeparator()) { it.defaultMessage },
          )
          val diagnostic = diagnostics.single()
          assertTrue(
            diagnostic.defaultMessage.contains(NULLNESS_MESSAGE),
            diagnostic.defaultMessage,
          )
        }
      }
    }

  companion object {
    private val IMPLEMENTATION =
      Implementation(TestDiagnosticsDetector::class.java, Scope.JAVA_FILE_SCOPE)

    @JvmField
    val ID =
      Issue.create(
        id = "KotlinCompilerDiagnostic",
        briefDescription = "Errors Reported by the Kotlin Compiler",
        explanation =
          "Lint runs on top of compiler, hence able to collect diagnostics from compiler too",
        category = Category.LINT,
        priority = 5,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    const val NULLNESS_MESSAGE =
      "Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type"

    private val useK2Uast = System.getProperty(FIR_UAST_KEY, "false").toBoolean()
  }
}
