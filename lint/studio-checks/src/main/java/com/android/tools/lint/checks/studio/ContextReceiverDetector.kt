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
package com.android.tools.lint.checks.studio

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtContextReceiver
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile

/**
 * Context receivers are currently an experimental feature, so we want to limit their adoption for
 * the time being. Historically, context receivers were restricted at the compiler level for Android
 * Studio but with the IntelliJ 2023.3 merge we have to allow it for the K2 analysis API.
 */
class ContextReceiverDetector : Detector(), SourceCodeScanner {

  companion object Issues {
    private val IMPLEMENTATION =
      Implementation(ContextReceiverDetector::class.java, Scope.JAVA_FILE_SCOPE)

    @JvmField
    val ISSUE =
      Issue.create(
        id = "ContextReceiver",
        briefDescription = "Context receivers are only experimental",
        explanation =
          """
                Context receivers are experimental and not ready for broad use within Android Studio code.
          """,
        category = CROSS_PLATFORM,
        severity = Severity.ERROR,
        platforms = STUDIO_PLATFORMS,
        implementation = IMPLEMENTATION
      )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> {
    return listOf(UFile::class.java)
  }

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      override fun visitFile(node: UFile) {
        val ktFile = node.sourcePsi as? KtFile ?: return
        PsiTreeUtil.findChildrenOfType(ktFile, KtContextReceiver::class.java).forEach {
          contextReceiver ->
          context.report(
            ISSUE,
            contextReceiver,
            context.getLocation(contextReceiver),
            "Do not use context receivers. They are an experimental feature at this time."
          )
        }
      }
    }
  }
}
