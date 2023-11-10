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
package com.android.tools.lint.checks

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParameter
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtAnnotationCall
import org.jetbrains.kotlin.analysis.api.calls.KtCall
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.KtCompoundAccessCall
import org.jetbrains.kotlin.analysis.api.calls.KtImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.calls.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.psi
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.analysis.KotlinExtensionConstants
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService
import org.jetbrains.uast.toUElementOfType

/**
 * Resolve [KtElement] to function call, constructor call, or annotation call and return
 * function-like symbol if any.
 *
 * Please use UAST/PSI `resolve` first, which provides a general output (of [PsiMethod]).
 *
 * The returned function-like symbol from Analysis API should be used *within* [KtAnalysisSession].
 */
internal fun KtAnalysisSession.getFunctionLikeSymbol(ktElement: KtElement): KtFunctionLikeSymbol? {
  val callInfo = ktElement.resolveCall() ?: return null
  return callInfo.singleFunctionCallOrNull()?.symbol
    ?: callInfo.singleConstructorCallOrNull()?.symbol
    ?: callInfo.singleCallOrNull<KtAnnotationCall>()?.symbol
}

/**
 * Returns true if [ktElement] is a call of an extension function.
 *
 * Unlike [org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration], which only works if you have
 * the Kotlin source PSI of a function declaration (it will not work if you have a call that
 * resolves to a compiled function), this analysis API-based function can identify calls to
 * extensions functions, regardless of whether the function declaration is in source or binary.
 */
internal fun KtAnalysisSession.isExtensionFunctionCall(ktElement: KtElement): Boolean =
  getFunctionLikeSymbol(ktElement)?.isExtension == true

/** Returns the `<this>` UParameter of [this] ULambdaExpression, if it exists. */
@Suppress("UnstableApiUsage")
internal fun ULambdaExpression.getThisParameter(
  resolveProviderService: BaseKotlinUastResolveProviderService
): UParameter? {
  val ktLambdaExpression = this.sourcePsi as? KtLambdaExpression ?: return null
  // Note: includeExplicitParameters seems to currently do the opposite of what you might expect; it
  // must be true to be able to find the implicit <this> parameter.
  val parameters =
    resolveProviderService.getImplicitParameters(
      ktLambdaExpression,
      this,
      includeExplicitParameters = true
    ) as List<UParameter>
  return parameters.firstOrNull {
    (it.javaPsi as? PsiParameter)?.name == KotlinExtensionConstants.LAMBDA_THIS_PARAMETER_NAME
  }
}

/**
 * Returns the implicit receiver of the call-like expression [ktExpression] if the implicit receiver
 * resolves to a `<this>` UParameter of a lambda expression, otherwise null.
 */
internal fun KtAnalysisSession.getImplicitReceiverIfFromLambdaExpr(
  ktExpression: KtExpression,
  resolveProviderService: BaseKotlinUastResolveProviderService,
): UParameter? =
  getImplicitReceiverValue(ktExpression)
    ?.getImplicitReceiverPsi()
    ?.toUElementOfType<ULambdaExpression>()
    ?.getThisParameter(resolveProviderService)

/**
 * Returns the PSI for [this], which will be the owning lambda expression or the surrounding class.
 */
internal fun KtImplicitReceiverValue.getImplicitReceiverPsi(): PsiElement? {
  return when (val receiverParameterSymbol = this.symbol) {
    // the owning lambda expression
    is KtReceiverParameterSymbol -> receiverParameterSymbol.owningCallableSymbol.psi
    // the class that we are in, calling a method
    is KtClassOrObjectSymbol -> receiverParameterSymbol.psi
    else -> null
  }
}

/**
 * Returns the implicit receiver value of the call-like expression [ktExpression] (can include
 * property accesses, for example).
 */
internal fun KtAnalysisSession.getImplicitReceiverValue(
  ktExpression: KtExpression
): KtImplicitReceiverValue? {
  val partiallyAppliedSymbol =
    when (val call = ktExpression.resolveCall()?.singleCallOrNull<KtCall>()) {
      // Note: Calls that are a `KtCompoundAccessCall` (especially, `KtCompoundArrayAccessCall`) are
      // quite complex, as such a call essentially contains multiple calls. For example, in:
      //
      // m["a"] += "b"
      //
      // we can have MutableMap.get, String?.plus, MutableMap.set. We get the implicit receiver of
      // the second call, which can only exist in this example if the extension function also
      // requires a dispatch receiver (an instance of a class in which the extension function is
      // declared).
      is KtCompoundAccessCall -> call.compoundAccess.operationPartiallyAppliedSymbol
      is KtCallableMemberCall<*, *> -> call.partiallyAppliedSymbol
      else -> null
    } ?: return null

  return partiallyAppliedSymbol.extensionReceiver as? KtImplicitReceiverValue
    ?: partiallyAppliedSymbol.dispatchReceiver as? KtImplicitReceiverValue
}
