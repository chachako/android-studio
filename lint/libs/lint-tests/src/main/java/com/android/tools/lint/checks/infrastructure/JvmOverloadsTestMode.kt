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

import com.android.SdkConstants
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.acceptSourceFile
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.JvmStandardClassIds.JVM_OVERLOADS_FQ_NAME
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod

/**
 * Test mode which converts if statements in Kotlin files to when statements. In the future we could
 * also try to convert Java if statements into switches if the comparisons are eligible (e.g.
 * constant expressions).
 */
class JvmOverloadsTestMode :
  UastSourceTransformationTestMode(
    description = "Handling @JvmOverloads methods",
    "TestMode.JVM_OVERLOADS",
    "jvmoverloads",
  ) {
  override val diffExplanation: String =
    // first line shorter: expecting to prefix that line with
    // "org.junit.ComparisonFailure: "
    """
        Kotlin methods with @JvmOverloads in
        the signature will be *inlined* as multiple repeated `UMethod`s in
        UAST. This means that you can see the same underlying method multiple times
        in the source code (with varying signatures according to the default
        parameters).

        This breaks some lint checks (and can result in repeated reports
        of the same problem). This lint check deliberately injects @JvmOverloads
        into Kotlin methods in the test sources in order to simulate this
        environment and catch lint checks that would be broken.

        If you have your own UAST visitor, one thing you can try is adding this
        to your visitMethod override:
            override fun visitMethod(node: UMethod): Boolean {
          +    if (node.isDuplicatedOverload()) {
          +     return true
          +   }
              ....

        In the unlikely event that your lint check is actually doing something
        specific to @JvmOverloads or exact parameter signatures, you can turn off this
        test mode using `.skipTestModes($fieldName)`.
        """
      .trimIndent()

  override fun isRelevantFile(file: TestFile): Boolean {
    // Only applies to Kotlin
    return file.targetRelativePath.endsWith(SdkConstants.DOT_KT)
  }

  override fun transform(
    source: String,
    context: JavaContext,
    root: UFile,
    clientData: MutableMap<String, Any>,
  ): MutableList<Edit> {
    if (!isKotlin(root.lang)) {
      return mutableListOf()
    }
    val seen = LinkedHashSet<PsiElement>()
    val edits = mutableListOf<Edit>()
    root.acceptSourceFile(
      object : EditVisitor() {
        override fun visitMethod(node: UMethod): Boolean {
          rewriteMethod(node)
          return super.visitMethod(node)
        }

        private fun rewriteMethod(node: UMethod) {
          // Already annotated?
          val method = node.sourcePsi as? KtFunction ?: return
          if (!seen.add(method)) {
            return
          }

          // Can't have defaults or @JvmOverloads on interface methods
          val parentClass = method.parentOfType<KtClass>()
          if (parentClass != null && (parentClass.isInterface() || parentClass.isAnnotation())) {
            return
          }

          if (
            method.annotationEntries.any {
              it.shortName?.asString() == JVM_OVERLOADS_FQ_NAME.shortName().asString()
            }
          ) {
            return
          }

          val modifierList = method.modifierList
          if (
            modifierList != null &&
              (modifierList.hasModifier(KtTokens.OVERRIDE_KEYWORD) ||
                modifierList.hasModifier(KtTokens.OPERATOR_KEYWORD) ||
                modifierList.hasModifier(KtTokens.INFIX_KEYWORD))
          ) {
            return
          }

          // If there is a single parameter, and it's of an interface type, the method
          // may be accessed as a SAM, which would break if we introduce an extra
          // default parameter.
          val valueParameters = method.valueParameters
          if (valueParameters.size == 1) {
            val parameter = valueParameters[0]
            analyze(parameter) {
              val parameterSymbol = parameter.getParameterSymbol()
              val returnType = parameterSymbol.returnType
              val typeSymbol = returnType.expandedClassSymbol
              if (typeSymbol is KtClassOrObjectSymbol) {
                val classKind = typeSymbol.classKind
                if (classKind == KtClassKind.INTERFACE) {
                  return
                }
              }
            }
          }

          val lastParameter = valueParameters.lastOrNull()

          if (lastParameter != null) {
            // Last parameter is a vararg parameter? Don't add default argument
            // after since it's ambiguous at the call site.
            if (lastParameter.isVarArg) {
              return
            }

            // Last parameter is lambda? Don't add default argument after since
            // caller may have placed lambda outside the argument list
            if (
              lastParameter.typeReference?.text?.contains("->") == true &&
                !lastParameter.hasDefaultValue()
            ) {
              return
            }
          }

          val constructor = method as? KtPrimaryConstructor

          val startOffset =
            (method as? KtNamedFunction)?.funKeyword?.startOffset ?: method.startOffset

          val lineBegin = source.lastIndexOf('\n', startOffset - 1) + 1
          val prefix =
            if (constructor != null) {
              " "
            } else {
              ""
            }
          val suffix =
            if (constructor != null && constructor.getConstructorKeyword() == null) {
              " constructor "
            } else if (source.substring(lineBegin, startOffset).isBlank()) {
              "\n" + getIndent(source, startOffset)
            } else {
              " "
            }
          edits.add(insert(startOffset, "$prefix@JvmOverloads$suffix"))

          // Do we have to inject a default parameter?
          val needDefault = valueParameters.none { it.hasDefaultValue() }
          if (needDefault) {
            val valueEnd = method.valueParameterList?.endOffset ?: return
            if (source[valueEnd - 1] == ')') {
              val comma =
                valueParameters.isNotEmpty() &&
                  valueParameters.last().nextSibling?.text?.endsWith(",") != true
              edits.add(
                insert(valueEnd - 1, "${if (comma) ", " else ""}$DEFAULT_PROPERTY_PARAMETER")
              )
            } else {
              println(
                "Unexpected missing ) in value parameter list `${method.valueParameterList?.text}`"
              )
            }
          }
        }
      }
    )

    return edits
  }

  companion object {
    // Property inserted; we pick something unique here to make it very
    // unlikely that this extra parameter actually conflicts with another
    // method of the same name (and we accidentally break resolve)
    const val DEFAULT_PROPERTY_PARAMETER = "_newarg: java.math.BigInteger? = null"
  }
}
