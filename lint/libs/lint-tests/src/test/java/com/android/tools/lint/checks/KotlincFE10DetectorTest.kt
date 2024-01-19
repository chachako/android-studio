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
package com.android.tools.lint.checks

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector

class KotlincFE10DetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return KotlincFE10Detector()
  }

  fun testDocumentationExample() {
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import com.android.tools.lint.client.api.UElementHandler
                import com.android.tools.lint.detector.api.JavaContext
                import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithVisibility
                import org.jetbrains.kotlin.descriptors.EffectiveVisibility
                import org.jetbrains.kotlin.descriptors.effectiveVisibility
                import org.jetbrains.kotlin.psi.KtElement
                import org.jetbrains.kotlin.psi.KtCallableDeclaration
                import org.jetbrains.kotlin.psi.KtFunction
                import org.jetbrains.kotlin.psi.KtProperty
                import org.jetbrains.kotlin.resolve.BindingContext
                import org.jetbrains.kotlin.types.isDynamic
                import org.jetbrains.uast.UElement
                import org.jetbrains.uast.UMethod
                import org.jetbrains.uast.kotlin.KotlinUastResolveProviderService

                class TestVisitor(val context: JavaContext) : UElementHandler() {
                    // Example from [InteroperabilityDetector.KotlinVisitor#visitMethod]
                    override fun visitMethod(node: UMethod) {
                        val declaration = node.sourcePsi as? KtCallableDeclaration ?: return
                        val expression = when (declaration) {
                          is KtProperty -> declaration.initializer ?: declaration.delegateExpression ?: return
                          is KtFunction -> declaration.bodyExpression ?: declaration.bodyBlockExpression ?: return
                          else -> return
                        }
                        val service = declaration.project.getService(KotlinUastResolveProviderService::class.java)
                        val bindingContext = service.getBindingContext(declaration) // ERROR
                        val type = bindingContext.getType(expression) ?: return // ERROR
                        if (type.isDynamic()) return // ERROR
                        // report something at the end
                    }

                    // Example from [PsiModifierItem#computeFlag] in Metalava
                    private fun computeFlag(
                        node: UElement,
                        bindingContext: BindingContext, // ERROR
                    ) : Int {
                        var visibilityFlag = PACKAGE_PRIVATE
                        if (node.sourcePsi is KtElement) {
                            val descriptor = bindingContext.get(BindingContext.DECLARATION_TO_DESCRIPTOR, node.sourcePsi) // ERROR
                            if (descriptor is DeclarationDescriptorWithVisibility) { // ERROR
                                val effectiveVisibility = descriptor.visibility.effectiveVisibility(descriptor, false) // ERROR
                                if (effectiveVisibility == EffectiveVisibility.Internal) {
                                    visibilityFlag = INTERNAL
                                }
                            }
                        }
                        return visibilityFlag
                    }

                    companion object {
                        private const val INTERNAL = 1
                        private const val PACKAGE_PRIVATE = 2
                    }
                }
                """
          )
          .indented(),
        *TestFiles.getLintClassPath(),
      )
      .skipTestModes(TestMode.BODY_REMOVAL, TestMode.IMPORT_ALIAS)
      .run()
      .expect(
        """
                src/test/pkg/TestVisitor.kt:28: Warning: org.jetbrains.kotlin.resolve.BindingContext appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                        val bindingContext = service.getBindingContext(declaration) // ERROR
                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestVisitor.kt:29: Warning: org.jetbrains.kotlin.resolve.BindingContext appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                        val type = bindingContext.getType(expression) ?: return // ERROR
                                   ~~~~~~~~~~~~~~
                src/test/pkg/TestVisitor.kt:30: Warning: org.jetbrains.kotlin.types.DynamicTypesKt appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                        if (type.isDynamic()) return // ERROR
                            ~~~~~~~~~~~~~~~~
                src/test/pkg/TestVisitor.kt:30: Warning: org.jetbrains.kotlin.types.KotlinType appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                        if (type.isDynamic()) return // ERROR
                            ~~~~
                src/test/pkg/TestVisitor.kt:37: Warning: org.jetbrains.kotlin.resolve.BindingContext appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                        bindingContext: BindingContext, // ERROR
                                        ~~~~~~~~~~~~~~
                src/test/pkg/TestVisitor.kt:41: Warning: org.jetbrains.kotlin.resolve.BindingContext appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                            val descriptor = bindingContext.get(BindingContext.DECLARATION_TO_DESCRIPTOR, node.sourcePsi) // ERROR
                                             ~~~~~~~~~~~~~~
                src/test/pkg/TestVisitor.kt:42: Warning: org.jetbrains.kotlin.descriptors.DeclarationDescriptor appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                            if (descriptor is DeclarationDescriptorWithVisibility) { // ERROR
                                ~~~~~~~~~~
                src/test/pkg/TestVisitor.kt:42: Warning: org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithVisibility appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                            if (descriptor is DeclarationDescriptorWithVisibility) { // ERROR
                                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestVisitor.kt:43: Warning: org.jetbrains.kotlin.descriptors.DeclarationDescriptor appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                                val effectiveVisibility = descriptor.visibility.effectiveVisibility(descriptor, false) // ERROR
                                                          ~~~~~~~~~~
                src/test/pkg/TestVisitor.kt:43: Warning: org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithVisibility appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                                val effectiveVisibility = descriptor.visibility.effectiveVisibility(descriptor, false) // ERROR
                                                                     ~~~~~~~~~~
                0 errors, 10 warnings
                """
      )
  }

  fun testKotlinPsiUtils() {
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import org.jetbrains.kotlin.descriptors.CallableDescriptor
                import org.jetbrains.kotlin.descriptors.FunctionDescriptor
                import org.jetbrains.kotlin.psi.KtCallElement
                import org.jetbrains.kotlin.psi.KtElement
                import org.jetbrains.kotlin.resolve.BindingContext
                import org.jetbrains.uast.UCallExpression
                import org.jetbrains.uast.UMethod
                import org.jetbrains.uast.kotlin.KotlinUastResolveProviderService

                object KotlinPsiUtils {
                    fun UCallExpression.getKtMethodDescriptor(): CallableDescriptor? {
                        val ktSource = (sourcePsi as? KtCallElement) ?: return null
                        val context = ktSource.getBindingContext() // ERROR
                        val call = context[BindingContext.CALL, ktSource.calleeExpression] ?: return null // ERROR
                        return context[BindingContext.RESOLVED_CALL, call]?.resultingDescriptor // ERROR
                    }

                    fun UMethod.getKtMethodDescriptor(): FunctionDescriptor? {
                        return (sourcePsi as? KtElement)?.getBindingContext() // ERROR
                          ?.get(BindingContext.FUNCTION, sourcePsi) // ERROR
                    }

                    fun KtElement.getBindingContext(): BindingContext {
                        val service = this.project.getService(KotlinUastResolveProviderService::class.java)
                        return service?.getBindingContext(this) ?: BindingContext.EMPTY // ERROR
                    }
                }
                """
          )
          .indented(),
        *TestFiles.getLintClassPath(),
      )
      .skipTestModes(TestMode.BODY_REMOVAL, TestMode.IMPORT_ALIAS)
      .run()
      .expect(
        """
                src/test/pkg/KotlinPsiUtils.kt:15: Warning: org.jetbrains.kotlin.resolve.BindingContext appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                        val context = ktSource.getBindingContext() // ERROR
                                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/KotlinPsiUtils.kt:16: Warning: org.jetbrains.kotlin.resolve.BindingContext appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                        val call = context[BindingContext.CALL, ktSource.calleeExpression] ?: return null // ERROR
                                   ~~~~~~~
                src/test/pkg/KotlinPsiUtils.kt:17: Warning: org.jetbrains.kotlin.resolve.BindingContext appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                        return context[BindingContext.RESOLVED_CALL, call]?.resultingDescriptor // ERROR
                               ~~~~~~~
                src/test/pkg/KotlinPsiUtils.kt:17: Warning: org.jetbrains.kotlin.resolve.calls.model.ResolvedCall appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                        return context[BindingContext.RESOLVED_CALL, call]?.resultingDescriptor // ERROR
                                                                            ~~~~~~~~~~~~~~~~~~~
                src/test/pkg/KotlinPsiUtils.kt:21: Warning: org.jetbrains.kotlin.resolve.BindingContext appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                        return (sourcePsi as? KtElement)?.getBindingContext() // ERROR
                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/KotlinPsiUtils.kt:22: Warning: org.jetbrains.kotlin.resolve.BindingContext appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                          ?.get(BindingContext.FUNCTION, sourcePsi) // ERROR
                                ~~~~~~~~~~~~~~
                src/test/pkg/KotlinPsiUtils.kt:27: Warning: org.jetbrains.kotlin.resolve.BindingContext appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                        return service?.getBindingContext(this) ?: BindingContext.EMPTY // ERROR
                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 7 warnings
                """
      )
  }

  fun testUnusedValueUtils() {
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import org.jetbrains.kotlin.descriptors.synthetic.FunctionInterfaceConstructorDescriptor
                import org.jetbrains.kotlin.load.java.sam.JavaSingleAbstractMethodUtils.isSamType
                import org.jetbrains.kotlin.psi.KtElement
                import org.jetbrains.kotlin.psi.KtExpression
                import org.jetbrains.kotlin.psi.KtValueArgument
                import org.jetbrains.kotlin.resolve.BindingContext
                import org.jetbrains.kotlin.resolve.calls.components.isVararg
                import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
                import org.jetbrains.kotlin.resolve.calls.util.getParameterForArgument
                import org.jetbrains.kotlin.resolve.calls.util.getParentResolvedCall
                import org.jetbrains.kotlin.types.KotlinType
                import org.jetbrains.uast.UCallExpression
                import org.jetbrains.uast.UMethod
                import org.jetbrains.uast.kotlin.KotlinUastResolveProviderService

                object UnusedValueUtils {
                    fun KtElement.getBindingContext(): BindingContext {
                        val service = this.project.getService(KotlinUastResolveProviderService::class.java)
                        return service?.getBindingContext(this) ?: BindingContext.EMPTY // ERROR
                    }

                    private val KtExpression.functionalInterfaceType: KotlinType?
                        get() {
                            val context = getBindingContext() // ERROR
                            val type = context.getType(this) // ERROR
                            if (type != null && isSamType(type)) { // ERROR
                                // E.g., `::getResource as FunInterface`.
                                return type // ERROR
                            }
                            val argument = parent as? KtValueArgument ?: return null
                            val call = argument.getParentResolvedCall(context) ?: return null // ERROR
                            val descriptor = call.resultingDescriptor // ERROR
                            // Below is a false positive: bug in KtReference?
                            return if (descriptor.original is FunctionInterfaceConstructorDescriptor) { // ERROR
                                // E.g., `FunInterface(::method)`.
                                descriptor.returnType // ERROR
                            } else {
                                // E.g., `call(..., ::method, ...)`.
                                call.getParameterTypeForArgument(argument)?.takeIf { isSamType(it) } // ERROR
                            }
                        }

                    private fun ResolvedCall<*>.getParameterTypeForArgument( // ERROR
                        argument: KtValueArgument
                    ): KotlinType? {
                        val parameter = getParameterForArgument(argument) ?: return null // ERROR
                        return if (parameter.isVararg) parameter.varargElementType // ERROR
                        else parameter.type // ERROR
                    }
                }
                """
          )
          .indented(),
        *TestFiles.getLintClassPath(),
      )
      .skipTestModes(TestMode.BODY_REMOVAL, TestMode.IMPORT_ALIAS, TestMode.IF_TO_WHEN)
      .allowDuplicates()
      .run()
      .expect(
        """
                src/test/pkg/UnusedValueUtils.kt:21: Warning: org.jetbrains.kotlin.resolve.BindingContext appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                        return service?.getBindingContext(this) ?: BindingContext.EMPTY // ERROR
                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/UnusedValueUtils.kt:26: Warning: org.jetbrains.kotlin.resolve.BindingContext appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                            val context = getBindingContext() // ERROR
                                          ~~~~~~~~~~~~~~~~~~~
                src/test/pkg/UnusedValueUtils.kt:27: Warning: org.jetbrains.kotlin.resolve.BindingContext appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                            val type = context.getType(this) // ERROR
                                       ~~~~~~~
                src/test/pkg/UnusedValueUtils.kt:28: Warning: org.jetbrains.kotlin.types.KotlinType appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                            if (type != null && isSamType(type)) { // ERROR
                                ~~~~
                src/test/pkg/UnusedValueUtils.kt:30: Warning: org.jetbrains.kotlin.types.KotlinType appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                                return type // ERROR
                                       ~~~~
                src/test/pkg/UnusedValueUtils.kt:33: Warning: org.jetbrains.kotlin.resolve.BindingContext appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                            val call = argument.getParentResolvedCall(context) ?: return null // ERROR
                                                                      ~~~~~~~
                src/test/pkg/UnusedValueUtils.kt:33: Warning: org.jetbrains.kotlin.resolve.calls.util.CallUtilKt appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                            val call = argument.getParentResolvedCall(context) ?: return null // ERROR
                                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/UnusedValueUtils.kt:34: Warning: org.jetbrains.kotlin.resolve.calls.model.ResolvedCall appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                            val descriptor = call.resultingDescriptor // ERROR
                                                  ~~~~~~~~~~~~~~~~~~~
                src/test/pkg/UnusedValueUtils.kt:34: Warning: org.jetbrains.kotlin.resolve.calls.model.ResolvedCall<? extends org.jetbrains.kotlin.descriptors.CallableDescriptor> appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                            val descriptor = call.resultingDescriptor // ERROR
                                             ~~~~
                src/test/pkg/UnusedValueUtils.kt:36: Warning: org.jetbrains.kotlin.descriptors.CallableDescriptor appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                            return if (descriptor.original is FunctionInterfaceConstructorDescriptor) { // ERROR
                                       ~~~~~~~~~~
                src/test/pkg/UnusedValueUtils.kt:36: Warning: org.jetbrains.kotlin.descriptors.synthetic.FunctionInterfaceConstructorDescriptor appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                            return if (descriptor.original is FunctionInterfaceConstructorDescriptor) { // ERROR
                                                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/UnusedValueUtils.kt:38: Warning: org.jetbrains.kotlin.descriptors.CallableDescriptor appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                                descriptor.returnType // ERROR
                                ~~~~~~~~~~
                src/test/pkg/UnusedValueUtils.kt:41: Warning: org.jetbrains.kotlin.resolve.calls.model.ResolvedCall<? extends org.jetbrains.kotlin.descriptors.CallableDescriptor> appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                                call.getParameterTypeForArgument(argument)?.takeIf { isSamType(it) } // ERROR
                                ~~~~
                src/test/pkg/UnusedValueUtils.kt:41: Warning: org.jetbrains.kotlin.types.KotlinType appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                                call.getParameterTypeForArgument(argument)?.takeIf { isSamType(it) } // ERROR
                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/UnusedValueUtils.kt:45: Warning: org.jetbrains.kotlin.resolve.calls.model.ResolvedCall appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                    private fun ResolvedCall<*>.getParameterTypeForArgument( // ERROR
                                ~~~~~~~~~~~~~~~
                src/test/pkg/UnusedValueUtils.kt:48: Warning: org.jetbrains.kotlin.resolve.calls.util.CallUtilKt appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                        val parameter = getParameterForArgument(argument) ?: return null // ERROR
                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/UnusedValueUtils.kt:49: Warning: org.jetbrains.kotlin.descriptors.ValueParameterDescriptor appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                        return if (parameter.isVararg) parameter.varargElementType // ERROR
                                   ~~~~~~~~~
                src/test/pkg/UnusedValueUtils.kt:49: Warning: org.jetbrains.kotlin.resolve.calls.components.ArgumentsUtilsKt appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                        return if (parameter.isVararg) parameter.varargElementType // ERROR
                                             ~~~~~~~~
                src/test/pkg/UnusedValueUtils.kt:50: Warning: org.jetbrains.kotlin.descriptors.ValueDescriptor appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                        else parameter.type // ERROR
                                       ~~~~
                src/test/pkg/UnusedValueUtils.kt:50: Warning: org.jetbrains.kotlin.descriptors.ValueParameterDescriptor appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon. [KotlincFE10]
                        else parameter.type // ERROR
                             ~~~~~~~~~
                0 errors, 20 warnings
                """
      )
  }
}
