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

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtAnnotationCall
import org.jetbrains.kotlin.analysis.api.calls.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.psi.KtElement

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
