package com.android.tools.lint.detector.api

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.components.KtExpressionTypeProviderMixIn
import org.jetbrains.kotlin.analysis.api.types.KtType

context(KtExpressionTypeProviderMixIn)
val PsiElement.expectedType: KtType?
	get() = getExpectedType()
