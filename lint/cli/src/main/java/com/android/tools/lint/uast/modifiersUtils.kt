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

import com.intellij.psi.PsiModifier
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject

private val ktTokenToPsiModifier =
  listOf(
    KtTokens.PUBLIC_KEYWORD to PsiModifier.PUBLIC,
    KtTokens.INTERNAL_KEYWORD to PsiModifier.PUBLIC,
    KtTokens.PROTECTED_KEYWORD to PsiModifier.PROTECTED,
  )

internal fun KtClassOrObject.computeModifiersByPsi(
  hasAbstractMember: (KtClassOrObject) -> Boolean
): Set<String> {
  val psiModifiers = hashSetOf<String>()

  for (tokenAndModifier in ktTokenToPsiModifier) {
    if (hasModifier(tokenAndModifier.first)) {
      psiModifiers.add(tokenAndModifier.second)
    }
  }

  if (hasModifier(KtTokens.PRIVATE_KEYWORD)) {
    // Top-level private class has PACKAGE_LOCAL visibility in Java
    // Nested private class has PRIVATE visibility
    psiModifiers.add(if (isTopLevel()) PsiModifier.PACKAGE_LOCAL else PsiModifier.PRIVATE)
  } else if (!psiModifiers.contains(PsiModifier.PROTECTED)) {
    psiModifiers.add(PsiModifier.PUBLIC)
  }

  // ABSTRACT
  if (isAbstract(hasAbstractMember) || isSealed) {
    psiModifiers.add(PsiModifier.ABSTRACT)
  }

  // STATIC
  if (!isTopLevel() && !hasModifier(KtTokens.INNER_KEYWORD)) {
    psiModifiers.add(PsiModifier.STATIC)
  }

  return psiModifiers
}

private fun KtClassOrObject.isAbstract(hasAbstractMember: (KtClassOrObject) -> Boolean): Boolean =
  hasModifier(KtTokens.ABSTRACT_KEYWORD) || isInterface || (isEnum && hasAbstractMember(this))

private val KtClassOrObject.isInterface: Boolean
  get() {
    if (this !is KtClass) return false
    return isInterface() || isAnnotation()
  }

private val KtClassOrObject.isEnum: Boolean
  get() = this is KtClass && isEnum()

private val KtClassOrObject.isSealed: Boolean
  get() = hasModifier(KtTokens.SEALED_KEYWORD)
