package com.android.tools.lint.checks

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.components.KtTypeInfoProviderMixIn
import org.jetbrains.kotlin.analysis.api.types.KtType

context(KtTypeInfoProviderMixIn)
val KtType.isArrayOrPrimitiveArray: Boolean
	get() = isArrayOrPrimitiveArray()
