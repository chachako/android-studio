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

import com.android.tools.lint.analyzeForLint
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import org.jetbrains.kotlin.analysis.api.annotations.annotations
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.analysis.api.types.KtDynamicType
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.descriptors.Visibilities
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
import org.jetbrains.uast.visitor.AbstractUastVisitor
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class AnalysisApiServicesTestBase {

    // Usage from [InteroperabilityDetector] (built-in Lint detector)
    protected fun checkDynamicType() {
        listOf(
            kotlin(
                """
                fun jsFun(p: String): dynamic
                """
            )
        ).use { context ->
            context.uastFile!!.accept(object : AbstractUastVisitor() {
                override fun visitMethod(node: UMethod): Boolean {
                    val returnTypeReference = node.returnTypeReference?.sourcePsi as? KtTypeReference
                        ?: return super.visitMethod(node)

                    analyzeForLint(returnTypeReference) {
                        val ktType = returnTypeReference.getKtType()
                        assertTrue(ktType is KtDynamicType)
                    }

                    return super.visitMethod(node)
                }
            })
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
        ).use { context ->
            context.uastFile!!.accept(object : AbstractUastVisitor() {
                override fun visitMethod(node: UMethod): Boolean {
                    val ktDeclaration = node.sourcePsi as? KtDeclaration ?: return super.visitMethod(node)

                    analyzeForLint(ktDeclaration) {
                        val symbol = ktDeclaration.getSymbol()
                        val visibility = (symbol as? KtSymbolWithVisibility)?.visibility
                        assertEquals(Visibilities.Internal, visibility)
                    }

                    return super.visitMethod(node)
                }
            })
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
        ).use { context ->
            context.uastFile!!.accept(object : AbstractUastVisitor() {
                override fun visitParameter(node: UParameter): Boolean {
                    val ktParameter = node.sourcePsi as? KtParameter ?: return super.visitParameter(node)

                    analyzeForLint(ktParameter) {
                        val ktType = ktParameter.getParameterSymbol().returnType
                        assertTrue(ktType.isFunctionalInterfaceType)
                    }

                    return super.visitParameter(node)
                }
            })
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
        ).use { context ->
            context.uastFile!!.accept(object : AbstractUastVisitor() {
                override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
                    val ktLambdaExpression = node.sourcePsi as? KtLambdaExpression ?: return super.visitLambdaExpression(node)

                    analyzeForLint(ktLambdaExpression) {
                        val lambdaType = ktLambdaExpression.getKtType()
                        assertTrue(lambdaType is KtFunctionalType && lambdaType.hasReceiver)
                    }

                    return super.visitLambdaExpression(node)
                }
            })
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
        ).use { context ->
            context.uastFile!!.accept(object : AbstractUastVisitor() {
                override fun visitClass(node: UClass): Boolean {
                    if (node.isAnnotationType) return super.visitClass(node)

                    val ktClass = node.sourcePsi as? KtClassOrObject ?: return super.visitClass(node)

                    analyzeForLint(ktClass) {
                        val symbol = ktClass.getClassOrObjectSymbol()
                        val typeParams = symbol.typeParameters
                        assertEquals(1, typeParams.size)
                        val typeParam = typeParams.single()
                        val hasAnn = typeParam.annotations.any {
                            it.classId?.asFqNameString() == "Ann"
                        }
                        assertTrue(hasAnn)
                    }

                    return super.visitClass(node)
                }
            })
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
        ).use { context ->
            context.uastFile!!.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val ktElement = node.sourcePsi as? KtElement ?: return super.visitCallExpression(node)
                    analyzeForLint(ktElement) {
                        val ktFunctionSymbol = ktElement.resolveCall()?.singleFunctionCallOrNull()?.symbol
                            ?: return super.visitCallExpression(node)
                        val ktParamSymbol = ktFunctionSymbol.valueParameters.single()
                        assertEquals(
                            ktFunctionSymbol.callableIdIfNonLocal?.callableName?.identifier == "myLet",
                            ktParamSymbol.isNoinline
                        )
                    }

                    return super.visitCallExpression(node)
                }
            })
        }
    }
}
