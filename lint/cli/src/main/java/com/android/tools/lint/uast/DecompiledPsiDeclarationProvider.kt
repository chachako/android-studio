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

import com.android.tools.lint.uast.PsiDeclarationAndKtSymbolEqualityChecker.representsTheSameDeclaration
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.uast.kotlin.internal.FirKotlinUastLibraryPsiProviderService

internal object DecompiledPsiDeclarationProvider : FirKotlinUastLibraryPsiProviderService {
  override fun KaSession.provide(symbol: KaSymbol): PsiElement? {
    val project = symbol.containingModule.project
    return when (symbol) {
      is KaConstructorSymbol -> providePsiForConstructor(symbol, project)
      is KaFunctionLikeSymbol -> providePsiForFunction(symbol, project)
      is KaEnumEntrySymbol -> providePsiForEnumEntry(symbol, project)
      is KaVariableLikeSymbol -> providePsiForProperty(symbol, project)
      is KaClassLikeSymbol -> providePsiForClass(symbol, project)
      else -> null
    }
  }

  private fun KaSession.providePsiForConstructor(
    constructorSymbol: KaConstructorSymbol,
    project: Project,
  ): PsiElement? {
    val classId = constructorSymbol.containingClassId ?: return null
    val candidates =
      project
        .createPsiDeclarationProvider(constructorSymbol.scope(project))
        ?.getClassesByClassId(classId)
        ?.firstOrNull()
        ?.constructors ?: return null
    return if (candidates.size == 1) candidates.single()
    else {
      candidates.find { psiMethod -> representsTheSameDeclaration(psiMethod, constructorSymbol) }
    }
  }

  private fun KaSession.providePsiForFunction(
    functionLikeSymbol: KaFunctionLikeSymbol,
    project: Project,
  ): PsiElement? {
    val candidates =
      project
        .createPsiDeclarationProvider(functionLikeSymbol.scope(project))
        ?.getFunctions(functionLikeSymbol)
    return if (candidates?.size == 1) candidates.single()
    else
      candidates?.find { psiMethod -> representsTheSameDeclaration(psiMethod, functionLikeSymbol) }
  }

  private fun KaSession.providePsiForProperty(
    variableLikeSymbol: KaVariableLikeSymbol,
    project: Project,
  ): PsiElement? {
    val candidates =
      project
        .createPsiDeclarationProvider(variableLikeSymbol.scope(project))
        ?.getProperties(variableLikeSymbol)
    if (candidates?.size == 1) return candidates.single()
    else {
      // Weigh [PsiField]
      candidates
        ?.firstOrNull { psiMember -> psiMember is PsiField }
        ?.let {
          return it
        }
      if (variableLikeSymbol is KaPropertySymbol) {
        val getterSymbol = variableLikeSymbol.getter
        val setterSymbol = variableLikeSymbol.setter
        candidates
          ?.filterIsInstance<PsiMethod>()
          ?.firstOrNull { psiMethod ->
            (getterSymbol != null && representsTheSameDeclaration(psiMethod, getterSymbol) ||
              setterSymbol != null && representsTheSameDeclaration(psiMethod, setterSymbol))
          }
          ?.let {
            return it
          }
      }
      return candidates?.firstOrNull()
    }
  }

  private fun providePsiForClass(
    classLikeSymbol: KaClassLikeSymbol,
    project: Project,
  ): PsiElement? {
    return classLikeSymbol.classId?.let {
      project
        .createPsiDeclarationProvider(classLikeSymbol.scope(project))
        ?.getClassesByClassId(it)
        ?.firstOrNull()
    }
  }

  private fun providePsiForEnumEntry(
    enumEntrySymbol: KaEnumEntrySymbol,
    project: Project,
  ): PsiElement? {
    val classId = enumEntrySymbol.callableId?.classId ?: return null
    val psiClass =
      project
        .createPsiDeclarationProvider(enumEntrySymbol.scope(project))
        ?.getClassesByClassId(classId)
        ?.firstOrNull() ?: return null
    return psiClass.fields.find { it.name == enumEntrySymbol.name.asString() }
  }

  private fun KaSymbol.scope(project: Project): GlobalSearchScope {
    // TODO: finding containing module and use a narrower scope?
    return GlobalSearchScope.allScope(project)
  }
}
