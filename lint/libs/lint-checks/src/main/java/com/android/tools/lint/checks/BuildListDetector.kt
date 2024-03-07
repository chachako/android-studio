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

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.CommonClassNames.JAVA_UTIL_LIST
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

/** Makes sure that `buildList` calls actually add items */
class BuildListDetector : Detector(), SourceCodeScanner {
  companion object Issues {
    private val IMPLEMENTATION =
      Implementation(BuildListDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Missing Add Call */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "BuildListAdds",
        briefDescription = "Missing `add` call in `buildList`",
        explanation =
          """
          The `buildList { }` standard library function is a convenient way to \
          build lists, but you need to actually call `add` on the items.
          """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    private const val BUILD_LIST_OWNER = "kotlin.collections.CollectionsKt__CollectionsKt"
  }

  override fun getApplicableMethodNames(): List<String> = listOf("buildList")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator
    if (node.valueArgumentCount == 1 && evaluator.isMemberInClass(method, BUILD_LIST_OWNER)) {
      val argument = node.valueArguments[0] as? ULambdaExpression ?: return
      var isAdding = false
      argument.body.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val name = node.methodName ?: node.methodIdentifier?.name
            if (name != null && name.startsWith("add")) {
              val receiver = node.receiver
              if (receiver == null || receiver is UThisExpression) {
                val containingClass = node.resolve()?.containingClass?.qualifiedName
                if (containingClass == null || containingClass == JAVA_UTIL_LIST) {
                  isAdding = true
                } else {
                  // Extension function on the list?
                  val sourcePsi = node.sourcePsi
                  if (
                    sourcePsi is KtCallExpression &&
                      node.receiverType?.let { TypeConversionUtil.erasure(it) }?.canonicalText ==
                        JAVA_UTIL_LIST
                  ) {
                    isAdding = true
                  }
                }
              }
            }
            return isAdding
          }
        }
      )
      if (!isAdding) {
        val message = "No `add` calls within `buildList` lambda; this is usually a mistake"
        context.report(ISSUE, node, context.getNameLocation(node), message)
      }
    }
  }
}
