/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.detector.api.JavaContext
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiField
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.KaDynamicType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

abstract class AnalysisApiServicesTestBase {

  // Usage from [InteroperabilityDetector] (built-in Lint detector)
  protected fun checkDynamicType() {
    listOf(
        kotlin(
          """
                fun jsFun(p: String): dynamic
                """
        )
      )
      .use { context ->
        context.uastFile!!.accept(
          object : AbstractUastVisitor() {
            override fun visitMethod(node: UMethod): Boolean {
              val returnTypeReference =
                node.returnTypeReference?.sourcePsi as? KtTypeReference
                  ?: return super.visitMethod(node)

              analyze(returnTypeReference) {
                val ktType = returnTypeReference.type
                assertTrue(ktType is KaDynamicType)
              }

              return super.visitMethod(node)
            }
          }
        )
      }
  }

  // Usage from PsiModifierItem in Metalava
  protected fun checkInternalModifier() {
    listOf(
        kotlin(
          """
                internal fun foo() {}
                """
        )
      )
      .use { context ->
        context.uastFile!!.accept(
          object : AbstractUastVisitor() {
            override fun visitMethod(node: UMethod): Boolean {
              val ktDeclaration = node.sourcePsi as? KtDeclaration ?: return super.visitMethod(node)

              analyze(ktDeclaration) {
                val visibility = ktDeclaration.symbol.visibility
                assertEquals(KaSymbolVisibility.INTERNAL, visibility)
              }

              return super.visitMethod(node)
            }
          }
        )
      }
  }

  // Usage from PsiParameterItem in Metalava
  protected fun checkSamType() {
    listOf(
        kotlin(
          """
                fun interface Fun {
                  fun run()
                }
                fun foo(p : Fun) { p.run() }
                """
        )
      )
      .use { context ->
        context.uastFile!!.accept(
          object : AbstractUastVisitor() {
            override fun visitParameter(node: UParameter): Boolean {
              val ktParameter = node.sourcePsi as? KtParameter ?: return super.visitParameter(node)

              analyze(ktParameter) {
                val ktType = ktParameter.symbol.returnType
                assertTrue(ktType.isFunctionalInterface)
              }

              return super.visitParameter(node)
            }
          }
        )
      }
  }

  // Usage from KotlinPsiUtils in g3
  protected fun checkExtensionLambda() {
    listOf(
        kotlin(
          """
                inline fun <T> myInit(e: T, init : T.() -> Unit): T {
                    e.init()
                    return e
                }

                fun foo() {
                  val initialized = myInit("hello") {
                    it + ", DSL"
                  }
                }
                """
        )
      )
      .use { context ->
        context.uastFile!!.accept(
          object : AbstractUastVisitor() {
            override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
              val ktLambdaExpression =
                node.sourcePsi as? KtLambdaExpression ?: return super.visitLambdaExpression(node)

              analyze(ktLambdaExpression) {
                val lambdaType = ktLambdaExpression.expressionType
                assertTrue(lambdaType is KaFunctionType && lambdaType.hasReceiver)
              }

              return super.visitLambdaExpression(node)
            }
          }
        )
      }
  }

  // Usage from KotlinPsiUtils in g3
  protected fun checkAnnotationOnTypeParameter() {
    listOf(
        kotlin(
          """
                @Target(AnnotationTarget.TYPE_PARAMETER)
                annotation class Ann

                class Foo<@Ann T> {}
                """
        )
      )
      .use { context ->
        context.uastFile!!.accept(
          object : AbstractUastVisitor() {
            override fun visitClass(node: UClass): Boolean {
              if (node.isAnnotationType) return super.visitClass(node)

              val ktClass = node.sourcePsi as? KtClassOrObject ?: return super.visitClass(node)

              analyze(ktClass) {
                val symbol = ktClass.classSymbol!!
                @OptIn(KaExperimentalApi::class) val typeParams = symbol.typeParameters
                assertEquals(1, typeParams.size)
                val typeParam = typeParams.single()
                val hasAnn = typeParam.annotations.any { it.classId?.asFqNameString() == "Ann" }
                assertTrue(hasAnn)
              }

              return super.visitClass(node)
            }
          }
        )
      }
  }

  // Usage from AbstractGuardedByVisitor in g3
  protected fun checkParameterModifiers() {
    listOf(
        kotlin(
          """
                inline fun <T, R> T.myLet(noinline myBlock: (T) -> R): R {
                  return block(this)
                }

                fun test() {
                  4.let { "no modifier" }
                  2.myLet { "noinline" }
                }
                """
        )
      )
      .use { context ->
        context.uastFile!!.accept(
          object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
              val ktElement = node.sourcePsi as? KtElement ?: return super.visitCallExpression(node)
              analyze(ktElement) {
                val ktFunctionSymbol =
                  ktElement.resolveToCall()?.singleFunctionCallOrNull()?.symbol
                    ?: return super.visitCallExpression(node)
                val ktParamSymbol = ktFunctionSymbol.valueParameters.single()
                if (ktFunctionSymbol.callableId?.callableName?.identifier == "myLet") {
                  assertTrue(ktParamSymbol.isNoinline)
                } else { // built-in `let`
                  assertFalse(ktParamSymbol.isNoinline)
                }
              }

              return super.visitCallExpression(node)
            }
          }
        )
      }
  }

  protected fun checkCancellation() {
    listOf(
        kotlin(
          """
          fun foo() { }
               """
        )
      )
      .use { context ->
        context.uastFile!!.accept(
          object : AbstractUastVisitor() {
            override fun visitMethod(node: UMethod): Boolean {
              assertTrue(ProgressManager.getInstance().isInNonCancelableSection)

              return super.visitMethod(node)
            }
          }
        )
      }
  }

  @OptIn(KaImplementationDetail::class)
  protected fun checkAnalysisAPIOnPsiElement(isK2: Boolean) {
    listOf(
        kotlin(
          """
          fun test(i : JavaClass) {
              val c = i.count
          }
        """
        ),
        java(
          """
          public class JavaClass {
              public Integer count = 0;
          }
        """
        ),
      )
      .use { context -> checkJavaSymbol(context, isK2) }
  }

  protected fun checkAnalysisAPIOnJava(isK2: Boolean) {
    listOf(
        java(
          """
          class Test {
              static void test(JavaClass i) {
                  Integer c = i.count;
              }
          }
        """
        ),
        java(
          """
          public class JavaClass {
              public Integer count = 0;
          }
        """
        ),
      )
      .use { context -> checkJavaSymbol(context, isK2) }
  }

  private fun checkJavaSymbol(context: JavaContext, isK2: Boolean) {
    context.uastFile!!.accept(
      object : AbstractUastVisitor() {
        override fun visitSimpleNameReferenceExpression(
          node: USimpleNameReferenceExpression
        ): Boolean {
          val c = node.resolve()
          assertNotNull(c)

          if (node.resolvedName != "count") {
            // parameter i
            return super.visitSimpleNameReferenceExpression(node)
          }

          assertTrue(c is PsiField)
          assertEquals("JavaClass", c.containingClass?.qualifiedName)

          if (!isK2) {
            return super.visitSimpleNameReferenceExpression(node)
          }

          val projectStructureProvider =
            c.project.getService(KotlinProjectStructureProvider::class.java)
          val module = projectStructureProvider.getModule(c, null)
          analyze(module) {
            val symbolFromPsiElement = c.callableSymbol
            val callableId = symbolFromPsiElement?.callableId
            assertEquals("JavaClass", callableId?.classId?.asFqNameString())
            assertEquals("count", callableId?.callableName?.identifier)
          }

          return super.visitSimpleNameReferenceExpression(node)
        }
      }
    )
  }
}
