package org.jetbrains.kotlin.analysis.api.resolution

import org.jetbrains.kotlin.analysis.api.calls.calls
import org.jetbrains.kotlin.analysis.api.calls.KtCall
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.KtCallInfo
import org.jetbrains.kotlin.analysis.api.calls.KtFunctionCall
import org.jetbrains.kotlin.analysis.api.calls.KtPartiallyAppliedSymbol
import org.jetbrains.kotlin.analysis.api.signatures.KtCallableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol

inline fun <reified T : KtCall> KtCallInfo.singleCallOrNull(): T? = calls.singleOrNull { it is T } as T?

fun KtCallInfo.singleFunctionCallOrNull(): KtFunctionCall<*>? = singleCallOrNull()

@Suppress("UNCHECKED_CAST")
fun KtCallInfo.singleConstructorCallOrNull(): KtFunctionCall<KtConstructorSymbol>? =
    singleCallOrNull<KtFunctionCall<*>>()?.takeIf { it.symbol is KtConstructorSymbol } as KtFunctionCall<KtConstructorSymbol>?

val <S : KtCallableSymbol, C : KtCallableSignature<S>> KtPartiallyAppliedSymbol<S, C>.symbol: S get() = signature.symbol

val <S : KtCallableSymbol, C : KtCallableSignature<S>> KtCallableMemberCall<S, C>.symbol: S get() = partiallyAppliedSymbol.symbol
