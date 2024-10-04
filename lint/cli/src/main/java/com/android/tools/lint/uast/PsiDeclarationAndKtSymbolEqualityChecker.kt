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
package com.android.tools.lint.uast

import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiEllipsisType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.impl.source.PsiClassReferenceType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeMappingMode
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType

// TODO replace with structural type comparison?
@OptIn(KaExperimentalApi::class)
internal object PsiDeclarationAndKtSymbolEqualityChecker {
  fun KaSession.representsTheSameDeclaration(psi: PsiMethod, symbol: KaCallableSymbol): Boolean {
    // TODO: receiver type comparison?
    if (!returnTypesMatch(psi, symbol)) return false
    if (!typeParametersMatch(psi, symbol)) return false
    if (symbol is KaFunctionSymbol && !valueParametersMatch(psi, symbol)) return false
    return true
  }

  private fun KaSession.returnTypesMatch(psi: PsiMethod, symbol: KaCallableSymbol): Boolean {
    if (symbol is KaConstructorSymbol) return psi.isConstructor
    return psi.returnType?.let {
      isTheSameTypes(psi, it, symbol.returnType, KaTypeMappingMode.RETURN_TYPE)
    } ?: false
  }

  private fun typeParametersMatch(psi: PsiMethod, symbol: KaCallableSymbol): Boolean {
    // PsiMethod for constructor won't have type parameters
    if (symbol is KaConstructorSymbol) return psi.isConstructor
    val symbolTypeParameters =
      symbol.typeParameters.takeIf { it.isNotEmpty() }
        ?: symbol.receiverParameter?.owningCallableSymbol?.typeParameters
        ?: emptyList()
    if (psi.typeParameters.size != symbolTypeParameters.size) return false
    psi.typeParameters.zip(symbolTypeParameters) { psiTypeParameter, typeParameterSymbol ->
      if (psiTypeParameter.name != typeParameterSymbol.name.asString()) return false
      // TODO: type parameter bounds comparison
    }
    return true
  }

  private fun KaSession.valueParametersMatch(psi: PsiMethod, symbol: KaFunctionSymbol): Boolean {
    val isExtension =
      when (symbol) {
        is KaPropertyAccessorSymbol -> symbol.receiverParameter != null
        else -> symbol.isExtension
      }
    val valueParameterCount =
      if (isExtension) symbol.valueParameters.size + 1 else symbol.valueParameters.size
    if (psi.parameterList.parametersCount != valueParameterCount) return false
    if (isExtension) {
      val psiParameter = psi.parameterList.parameters[0]
      if (symbol.receiverType?.let { isTheSameTypes(psi, psiParameter.type, it) } != true)
        return false
    }
    val offset = if (isExtension) 1 else 0
    symbol.valueParameters.forEachIndexed { index, valueParameterSymbol ->
      val psiParameter = psi.parameterList.parameters[index + offset]
      // The type of `vararg` value param at last v.s. non-last is mapped differently:
      //   * last -> ellipsis type
      //   * non-last -> array type
      // In [PsiParameter], we only know whether it `isVarArgs`.
      // If that's set to `true`, obviously symbol's `isVararg` should be `true`.
      // But, `isVarArgs` being `false` doesn't mean it is not `vararg`.
      // It may be the case that it's just not the last value parameter.
      if (psiParameter.isVarArgs && !valueParameterSymbol.isVararg) {
        return false
      }
      if (
        !isTheSameTypes(
          psi,
          psiParameter.type,
          valueParameterSymbol.returnType,
          KaTypeMappingMode.VALUE_PARAMETER,
          valueParameterSymbol.isVararg,
          psiParameter.isVarArgs,
        )
      )
        return false
    }
    return true
  }

  private fun KaSession.isTheSameTypes(
    context: PsiMethod,
    psi: PsiType,
    kaType: KaType,
    mode: KaTypeMappingMode = KaTypeMappingMode.DEFAULT,
    isVararg: Boolean = false,
    isVarargs: Boolean = false, // isVarargs == isVararg && last param
  ): Boolean {
    // Shortcut: primitive void == Unit as a function return type
    if (psi == PsiTypes.voidType() && kaType.isUnitType) return true
    // Without type substitution (from resolved call info), we can't
    // tell their equality: assume they are matched conservatively,
    // when the counterpart [PsiType] is Object, as if it's converted
    // from a type parameter.
    if (kaType is KaTypeParameterType && psi.isObject) {
      return true
    }
    val ktTypeRendered = kaType.asPsiType(context, allowErrorTypes = true, mode) ?: return false
    return if (isVararg) {
      if (isVarargs) {
        // last vararg
        PsiEllipsisType(ktTypeRendered) == psi
      } else {
        // non-last vararg
        PsiArrayType(ktTypeRendered) == psi
      }
    } else {
      ktTypeRendered == psi
    }
  }

  private const val OBJECT_TYPE = "java.lang.Object"

  private val PsiType.isObject: Boolean
    get() {
      return this is PsiClassReferenceType && canonicalText == OBJECT_TYPE
    }
}
