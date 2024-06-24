package org.jetbrains.kotlin.analysis.api.symbols

import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithTypeParameters

context(KtSymbolWithTypeParameters)
val KtDeclarationSymbol.typeParameters: List<KtTypeParameterSymbol>
	get() = this@KtSymbolWithTypeParameters.typeParameters
