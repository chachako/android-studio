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

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableLikeSymbol
import org.jetbrains.kotlin.name.ClassId

/**
 * A [PsiMember] declaration provider for a given scope. Can be created via
 * [KotlinPsiDeclarationProviderFactory].
 */
internal abstract class KotlinPsiDeclarationProvider {
  /**
   * Gets a collection of [PsiClass] by [ClassId]
   *
   * In standalone mode, this is simply [PsiClassStub]-based [PsiClass]
   */
  abstract fun getClassesByClassId(classId: ClassId): Collection<PsiClass>

  abstract fun getProperties(variableLikeSymbol: KaVariableLikeSymbol): Collection<PsiMember>

  abstract fun getFunctions(functionLikeSymbol: KaFunctionLikeSymbol): Collection<PsiMethod>
}

internal abstract class KotlinPsiDeclarationProviderFactory {
  abstract fun createPsiDeclarationProvider(
    searchScope: GlobalSearchScope
  ): KotlinPsiDeclarationProvider
}

internal fun Project.createPsiDeclarationProvider(
  searchScope: GlobalSearchScope
): KotlinPsiDeclarationProvider? =
  // TODO: avoid using fail-safe service loading once the factory has an easy-to-register ctor.
  getServiceIfCreated(KotlinPsiDeclarationProviderFactory::class.java)
    ?.createPsiDeclarationProvider(searchScope)
