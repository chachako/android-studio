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

class AdbDeviceServicesCommandDetector : Detector(), SourceCodeScanner {

  companion object Issues {
    private const val adbDeviceServicesClassName = "com.android.adblib.AdbDeviceServices"

    private val discouragedMethods = listOf("exec", "shell", "shellV2")

    private val IMPLEMENTATION =
      Implementation(AdbDeviceServicesCommandDetector::class.java, Scope.JAVA_FILE_SCOPE)

    @JvmField
    val ISSUE =
      Issue.create(
        id = "AdbDeviceServicesCommand",
        briefDescription = "Use `shellCommand` instead of `exec`, `shell`, and `shellV2`",
        explanation =
          """
                It is strongly recommended to use `AdbDeviceServices.shellCommand` \
                instead of `exec`, `shell`, and `shellV2` commands.
            """,
        category = Category.CORRECTNESS,
        severity = Severity.ERROR,
        platforms = STUDIO_PLATFORMS,
        implementation = IMPLEMENTATION
      )
  }

  override fun getApplicableMethodNames() = discouragedMethods

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, adbDeviceServicesClassName)) {
      return
    }

    // Do not trigger lint violations for adblib implementation
    if (context.uastFile?.packageName == "com.android.adblib.impl") {
      return
    }

    context.report(
      ISSUE,
      method,
      context.getLocation(node),
      "Use of `$adbDeviceServicesClassName#${method.name}` is discouraged. Consider using `AdbDeviceServices.shellCommand()` instead"
    )
  }
}
