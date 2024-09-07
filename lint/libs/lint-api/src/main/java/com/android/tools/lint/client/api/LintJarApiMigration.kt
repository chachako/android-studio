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
package com.android.tools.lint.client.api

import com.android.SdkConstants.DOT_CLASS
import com.android.SdkConstants.DOT_JAR
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.helpers.readAllBytes
import com.google.common.hash.Hashing
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.Type
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode

/**
 * Bytecode rewriter which can adapt *some* older lint check jars from old API references to new API
 * references, such as migrations in the Kotlin analysis API.
 *
 * The two main entry points are [migrateJar] which migrates the bytes of one lint jar and returns
 * it as an updated jar byte array, and [getMigratedJar] which operates at the File level and adds
 * caching.
 *
 * NOTE: This does NOT handle all the various changes that have been made to the analysis API so
 * far; it's focusing on the ones observed in our existing 3rd party library usages (chiefly
 * AndroidX).
 */
class LintJarApiMigration(private val client: LintClient) {
  /**
   * For a file that has been determined that it needs API migration (via the [LintJarVerifier]),
   * this method returns a migrated jar, possibly cached.
   *
   * The registryClass is only used to make the cached file names more readable and can be left
   * blank.
   */
  @Suppress("UnstableApiUsage") // Guava Hashing
  fun getMigratedJar(jar: File, registryClass: String): File {
    val cacheDir = client.getCacheDir("migrated-jars", true)
    val jarContents = jar.readBytes()

    val hashFunction = Hashing.farmHashFingerprint64()
    @Suppress("SpellCheckingInspection") val hasher = hashFunction.newHasher()
    hasher.putBytes(jarContents)
    val hashCode = hasher.hash()
    val hashCodeString = hashCode.toString()
    val fileName =
      "${if (registryClass.isNotBlank()) "$registryClass-" else ""}$hashCodeString.$DOT_JAR"
    val cachedJar = File(cacheDir, fileName)

    if (!cachedJar.isFile) {
      val bytes = migrateJar(jarContents)
      cachedJar.writeBytes(bytes)
    }
    return cachedJar
  }

  /**
   * Given the bytecode contents of a lint jar file, migrates the classes contained within and
   * returns the bytecode for the migrated class. The actual per class migration is performed by
   * [migrateClass].
   */
  private fun migrateJar(jarBytes: ByteArray): ByteArray {
    val output = ByteArrayOutputStream()
    JarInputStream(ByteArrayInputStream(jarBytes)).use { jis ->
      output.use { fos ->
        JarOutputStream(fos).use { jos ->
          var entry = jis.nextJarEntry
          while (entry != null) {
            jos.putNextEntry(JarEntry(entry.name))

            val name = entry.name
            if (name.endsWith(DOT_CLASS) && !entry.isDirectory) {
              val bytes = jis.readAllBytes(entry)
              val migrated = migrateClass(bytes)
              jos.write(migrated)
            } else {
              jis.copyTo(jos)
            }

            jos.closeEntry()
            entry = jis.nextJarEntry
          }
        }
      }
    }

    return output.toByteArray()
  }

  /**
   * Migrates the given `.class` file contents as bytecode. This current performs four migrations:
   * 1. Replaces inlined Kotlin analysis API `analyze()` method bodies
   * 2. Replaces all renamed and moved Kotlin analysis APIs class references (including in type
   *    signatures) to their new locations; this is based on the mapping table in [mapClass], which
   *    was extracted from type aliases left in the source code for compatibility reasons
   * 3. Changes from invoke virtual to invoke interface methods that have been moved from classes to
   *    interfaces; this is done by looking up each method via reflection (as the bytecode verifier
   *    already does) and checking the type of the declaring class
   * 4. Handles a small number of other refactorings, such as the KtTypeProviderMixin changes.
   *
   * (The exact set of migrations will likely change over time.)
   */
  fun migrateClass(bytes: ByteArray): ByteArray {
    return migrateClassNames(migrateAnalyzeCall(bytes))
  }

  /**
   * For APIs relevant to API migration, returns a new internal name for the given class, or null if
   * it has not moved or is unknown.
   */
  fun mapClass(s: String?): String? {
    if (s == null || !isRelevantType(s)) {
      return null
    }
    val innerClassIndex = s.indexOf('$')
    if (innerClassIndex != -1) {
      val mapped = mapClass(s.substring(0, innerClassIndex))
      if (mapped != null) {
        return mapped + s.substring(innerClassIndex)
      }
    }

    return when (s) {
      // Extracted via ExtractMigrationTable.kt in the unit tests
      "org/jetbrains/kotlin/analysis/api/KaStarTypeProjection" ->
        "org/jetbrains/kotlin/analysis/api/types/KaStarTypeProjection"
      "org/jetbrains/kotlin/analysis/api/KaSymbolBasedReference" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaSymbolBasedReference"
      "org/jetbrains/kotlin/analysis/api/KaTypeArgumentWithVariance" ->
        "org/jetbrains/kotlin/analysis/api/types/KaTypeArgumentWithVariance"
      "org/jetbrains/kotlin/analysis/api/KaTypeProjection" ->
        "org/jetbrains/kotlin/analysis/api/types/KaTypeProjection"
      "org/jetbrains/kotlin/analysis/api/KtAnalysisApiInternals" ->
        "org/jetbrains/kotlin/analysis/api/KaAnalysisApiInternals"
      "org/jetbrains/kotlin/analysis/api/KtAnalysisNonPublicApi" ->
        "org/jetbrains/kotlin/analysis/api/KaAnalysisNonPublicApi"
      "org/jetbrains/kotlin/analysis/api/KtAnalysisSession" ->
        "org/jetbrains/kotlin/analysis/api/KaSession"
      "org/jetbrains/kotlin/analysis/api/KtStarTypeProjection" ->
        "org/jetbrains/kotlin/analysis/api/types/KaStarTypeProjection"
      "org/jetbrains/kotlin/analysis/api/KtSymbolBasedReference" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaSymbolBasedReference"
      "org/jetbrains/kotlin/analysis/api/KtTypeArgumentWithVariance" ->
        "org/jetbrains/kotlin/analysis/api/types/KaTypeArgumentWithVariance"
      "org/jetbrains/kotlin/analysis/api/KtTypeProjection" ->
        "org/jetbrains/kotlin/analysis/api/types/KaTypeProjection"
      "org/jetbrains/kotlin/analysis/api/annotations/KaAnnotationApplication" ->
        "org/jetbrains/kotlin/analysis/api/annotations/KaAnnotation"
      "org/jetbrains/kotlin/analysis/api/annotations/KaAnnotationApplicationInfo" ->
        "org/jetbrains/kotlin/analysis/api/annotations/KaAnnotation"
      "org/jetbrains/kotlin/analysis/api/annotations/KaAnnotationApplicationValue" ->
        "KaAnnotationValue\$NestedAnnotationValue"
      "org/jetbrains/kotlin/analysis/api/annotations/KaAnnotationApplicationWithArgumentsInfo" ->
        "org/jetbrains/kotlin/analysis/api/annotations/KaAnnotation"
      "org/jetbrains/kotlin/analysis/api/annotations/KaAnnotationsList" ->
        "org/jetbrains/kotlin/analysis/api/annotations/KaAnnotationList"
      "org/jetbrains/kotlin/analysis/api/annotations/KaArrayAnnotationValue" ->
        "KaAnnotationValue\$ArrayValue"
      "org/jetbrains/kotlin/analysis/api/annotations/KaConstantAnnotationValue" ->
        "KaAnnotationValue\$ConstantValue"
      "org/jetbrains/kotlin/analysis/api/annotations/KaEnumEntryAnnotationValue" ->
        "KaAnnotationValue\$EnumEntryValue"
      "org/jetbrains/kotlin/analysis/api/annotations/KaKClassAnnotationValue" ->
        "KaAnnotationValue\$ClassLiteralValue"
      "org/jetbrains/kotlin/analysis/api/annotations/KaUnsupportedAnnotationValue" ->
        "KaAnnotationValue\$UnsupportedValue"
      "org/jetbrains/kotlin/analysis/api/annotations/KtAnnotated" ->
        "org/jetbrains/kotlin/analysis/api/annotations/KaAnnotated"
      "org/jetbrains/kotlin/analysis/api/annotations/KtAnnotationApplication" ->
        "org/jetbrains/kotlin/analysis/api/annotations/KaAnnotation"
      "org/jetbrains/kotlin/analysis/api/annotations/KtAnnotationApplicationInfo" ->
        "org/jetbrains/kotlin/analysis/api/annotations/KaAnnotation"
      "org/jetbrains/kotlin/analysis/api/annotations/KtAnnotationApplicationValue" ->
        "KaAnnotationValue\$NestedAnnotationValue"
      "org/jetbrains/kotlin/analysis/api/annotations/KtAnnotationApplicationWithArgumentsInfo" ->
        "org/jetbrains/kotlin/analysis/api/annotations/KaAnnotation"
      "org/jetbrains/kotlin/analysis/api/annotations/KtAnnotationValue" ->
        "org/jetbrains/kotlin/analysis/api/annotations/KaAnnotationValue"
      "org/jetbrains/kotlin/analysis/api/annotations/KtAnnotationsList" ->
        "org/jetbrains/kotlin/analysis/api/annotations/KaAnnotationList"
      "org/jetbrains/kotlin/analysis/api/annotations/KtArrayAnnotationValue" ->
        "KaAnnotationValue\$ArrayValue"
      "org/jetbrains/kotlin/analysis/api/annotations/KtConstantAnnotationValue" ->
        "KaAnnotationValue\$ConstantValue"
      "org/jetbrains/kotlin/analysis/api/annotations/KtEnumEntryAnnotationValue" ->
        "KaAnnotationValue\$EnumEntryValue"
      "org/jetbrains/kotlin/analysis/api/annotations/KtKClassAnnotationValue" ->
        "KaAnnotationValue\$ClassLiteralValue"
      "org/jetbrains/kotlin/analysis/api/annotations/KtNamedAnnotationValue" ->
        "org/jetbrains/kotlin/analysis/api/annotations/KaNamedAnnotationValue"
      "org/jetbrains/kotlin/analysis/api/annotations/KtUnsupportedAnnotationValue" ->
        "KaAnnotationValue\$UnsupportedValue"
      "org/jetbrains/kotlin/analysis/api/base/KtConstantValue" ->
        "org/jetbrains/kotlin/analysis/api/base/KaConstantValue"
      "org/jetbrains/kotlin/analysis/api/base/KtContextReceiver" ->
        "org/jetbrains/kotlin/analysis/api/base/KaContextReceiver"
      "org/jetbrains/kotlin/analysis/api/base/KtContextReceiversOwner" ->
        "org/jetbrains/kotlin/analysis/api/base/KaContextReceiversOwner"
      "org/jetbrains/kotlin/analysis/api/calls/KaAnnotationCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaAnnotationCall"
      "org/jetbrains/kotlin/analysis/api/calls/KaApplicableCallCandidateInfo" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaApplicableCallCandidateInfo"
      "org/jetbrains/kotlin/analysis/api/calls/KaCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaCall"
      "org/jetbrains/kotlin/analysis/api/calls/KaCallCandidateInfo" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaCallCandidateInfo"
      "org/jetbrains/kotlin/analysis/api/calls/KaCallInfo" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaCallInfo"
      "org/jetbrains/kotlin/analysis/api/calls/KaCallableMemberCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaCallableMemberCall"
      "org/jetbrains/kotlin/analysis/api/calls/KaCompoundAccess" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaCompoundAccess"
      "org/jetbrains/kotlin/analysis/api/calls/KaCompoundAccessCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaCompoundAccessCall"
      "org/jetbrains/kotlin/analysis/api/calls/KaCompoundArrayAccessCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaCompoundArrayAccessCall"
      "org/jetbrains/kotlin/analysis/api/calls/KaCompoundVariableAccessCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaCompoundVariableAccessCall"
      "org/jetbrains/kotlin/analysis/api/calls/KaDelegatedConstructorCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaDelegatedConstructorCall"
      "org/jetbrains/kotlin/analysis/api/calls/KaErrorCallInfo" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaErrorCallInfo"
      "org/jetbrains/kotlin/analysis/api/calls/KaExplicitReceiverValue" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaExplicitReceiverValue"
      "org/jetbrains/kotlin/analysis/api/calls/KaFunctionCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaFunctionCall"
      "org/jetbrains/kotlin/analysis/api/calls/KaImplicitReceiverValue" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaImplicitReceiverValue"
      "org/jetbrains/kotlin/analysis/api/calls/KaInapplicableCallCandidateInfo" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaInapplicableCallCandidateInfo"
      "org/jetbrains/kotlin/analysis/api/calls/KaPartiallyAppliedFunctionSymbol" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedFunctionSymbol"
      "org/jetbrains/kotlin/analysis/api/calls/KaPartiallyAppliedSymbol" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedSymbol"
      "org/jetbrains/kotlin/analysis/api/calls/KaPartiallyAppliedVariableSymbol" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedVariableSymbol"
      "org/jetbrains/kotlin/analysis/api/calls/KaReceiverValue" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaReceiverValue"
      "org/jetbrains/kotlin/analysis/api/calls/KaSimpleFunctionCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaSimpleFunctionCall"
      "org/jetbrains/kotlin/analysis/api/calls/KaSimpleVariableAccess" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaSimpleVariableAccess"
      "org/jetbrains/kotlin/analysis/api/calls/KaSimpleVariableAccessCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaSimpleVariableAccessCall"
      "org/jetbrains/kotlin/analysis/api/calls/KaSmartCastedReceiverValue" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaSmartCastedReceiverValue"
      "org/jetbrains/kotlin/analysis/api/calls/KaSuccessCallInfo" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaSuccessCallInfo"
      "org/jetbrains/kotlin/analysis/api/calls/KaVariableAccessCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaVariableAccessCall"
      "org/jetbrains/kotlin/analysis/api/calls/KtAnnotationCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaAnnotationCall"
      "org/jetbrains/kotlin/analysis/api/calls/KtApplicableCallCandidateInfo" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaApplicableCallCandidateInfo"
      "org/jetbrains/kotlin/analysis/api/calls/KtCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaCall"
      "org/jetbrains/kotlin/analysis/api/calls/KtCallCandidateInfo" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaCallCandidateInfo"
      "org/jetbrains/kotlin/analysis/api/calls/KtCallInfo" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaCallInfo"
      "org/jetbrains/kotlin/analysis/api/calls/KtCallableMemberCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaCallableMemberCall"
      "org/jetbrains/kotlin/analysis/api/calls/KtCompoundAccess" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaCompoundAccess"
      "org/jetbrains/kotlin/analysis/api/calls/KtCompoundAccessCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaCompoundAccessCall"
      "org/jetbrains/kotlin/analysis/api/calls/KtCompoundArrayAccessCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaCompoundArrayAccessCall"
      "org/jetbrains/kotlin/analysis/api/calls/KtCompoundVariableAccessCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaCompoundVariableAccessCall"
      "org/jetbrains/kotlin/analysis/api/calls/KtDelegatedConstructorCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaDelegatedConstructorCall"
      "org/jetbrains/kotlin/analysis/api/calls/KtErrorCallInfo" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaErrorCallInfo"
      "org/jetbrains/kotlin/analysis/api/calls/KtExplicitReceiverValue" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaExplicitReceiverValue"
      "org/jetbrains/kotlin/analysis/api/calls/KtFunctionCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaFunctionCall"
      "org/jetbrains/kotlin/analysis/api/calls/KtImplicitReceiverValue" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaImplicitReceiverValue"
      "org/jetbrains/kotlin/analysis/api/calls/KtInapplicableCallCandidateInfo" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaInapplicableCallCandidateInfo"
      "org/jetbrains/kotlin/analysis/api/calls/KtPartiallyAppliedFunctionSymbol" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedFunctionSymbol"
      "org/jetbrains/kotlin/analysis/api/calls/KtPartiallyAppliedSymbol" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedSymbol"
      "org/jetbrains/kotlin/analysis/api/calls/KtPartiallyAppliedVariableSymbol" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedVariableSymbol"
      "org/jetbrains/kotlin/analysis/api/calls/KtReceiverValue" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaReceiverValue"
      "org/jetbrains/kotlin/analysis/api/calls/KtSimpleFunctionCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaSimpleFunctionCall"
      "org/jetbrains/kotlin/analysis/api/calls/KtSimpleVariableAccess" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaSimpleVariableAccess"
      "org/jetbrains/kotlin/analysis/api/calls/KtSimpleVariableAccessCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaSimpleVariableAccessCall"
      "org/jetbrains/kotlin/analysis/api/calls/KtSmartCastedReceiverValue" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaSmartCastedReceiverValue"
      "org/jetbrains/kotlin/analysis/api/calls/KtSuccessCallInfo" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaSuccessCallInfo"
      "org/jetbrains/kotlin/analysis/api/calls/KtVariableAccessCall" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaVariableAccessCall"
      "org/jetbrains/kotlin/analysis/api/components/KtBuiltinTypes" ->
        "org/jetbrains/kotlin/analysis/api/components/KaBuiltinTypes"
      "org/jetbrains/kotlin/analysis/api/components/KtClassTypeBuilder" ->
        "org/jetbrains/kotlin/analysis/api/components/KaClassTypeBuilder"
      "org/jetbrains/kotlin/analysis/api/components/KtCompilationResult" ->
        "org/jetbrains/kotlin/analysis/api/components/KaCompilationResult"
      "org/jetbrains/kotlin/analysis/api/components/KtCompiledFile" ->
        "org/jetbrains/kotlin/analysis/api/components/KaCompiledFile"
      "org/jetbrains/kotlin/analysis/api/components/KtCompilerTarget" ->
        "org/jetbrains/kotlin/analysis/api/components/KaCompilerTarget"
      "org/jetbrains/kotlin/analysis/api/components/KtCompletionExtensionCandidateChecker" ->
        "org/jetbrains/kotlin/analysis/api/components/KaCompletionExtensionCandidateChecker"
      "org/jetbrains/kotlin/analysis/api/components/KtDataFlowExitPointSnapshot" ->
        "org/jetbrains/kotlin/analysis/api/components/KaDataFlowExitPointSnapshot"
      "org/jetbrains/kotlin/analysis/api/components/KtDiagnosticCheckerFilter" ->
        "org/jetbrains/kotlin/analysis/api/components/KaDiagnosticCheckerFilter"
      "org/jetbrains/kotlin/analysis/api/components/KtExtensionApplicabilityResult" ->
        "org/jetbrains/kotlin/analysis/api/components/KaExtensionApplicabilityResult"
      "org/jetbrains/kotlin/analysis/api/components/KtImplicitReceiver" ->
        "org/jetbrains/kotlin/analysis/api/components/KaImplicitReceiver"
      "org/jetbrains/kotlin/analysis/api/components/KtImplicitReceiverSmartCast" ->
        "org/jetbrains/kotlin/analysis/api/components/KaImplicitReceiverSmartCast"
      "org/jetbrains/kotlin/analysis/api/components/KtImplicitReceiverSmartCastKind" ->
        "org/jetbrains/kotlin/analysis/api/components/KaImplicitReceiverSmartCastKind"
      "org/jetbrains/kotlin/analysis/api/components/KtImportOptimizerResult" ->
        "org/jetbrains/kotlin/analysis/api/components/KaImportOptimizerResult"
      "org/jetbrains/kotlin/analysis/api/components/KtScopeContext" ->
        "org/jetbrains/kotlin/analysis/api/components/KaScopeContext"
      "org/jetbrains/kotlin/analysis/api/components/KtScopeKind" ->
        "org/jetbrains/kotlin/analysis/api/components/KaScopeKind"
      "org/jetbrains/kotlin/analysis/api/components/KtScopeWithKind" ->
        "org/jetbrains/kotlin/analysis/api/components/KaScopeWithKind"
      "org/jetbrains/kotlin/analysis/api/components/KtSmartCastInfo" ->
        "org/jetbrains/kotlin/analysis/api/components/KaSmartCastInfo"
      "org/jetbrains/kotlin/analysis/api/components/KtSubstitutorBuilder" ->
        "org/jetbrains/kotlin/analysis/api/components/KaSubstitutorBuilder"
      "org/jetbrains/kotlin/analysis/api/components/KtSubtypingErrorTypePolicy" ->
        "org/jetbrains/kotlin/analysis/api/components/KaSubtypingErrorTypePolicy"
      "org/jetbrains/kotlin/analysis/api/components/KtTypeBuilder" ->
        "org/jetbrains/kotlin/analysis/api/components/KaTypeBuilder"
      "org/jetbrains/kotlin/analysis/api/components/KtTypeParameterTypeBuilder" ->
        "org/jetbrains/kotlin/analysis/api/components/KaTypeParameterTypeBuilder"
      "org/jetbrains/kotlin/analysis/api/contracts/description/KtContractCallsInPlaceContractEffectDeclaration" ->
        "org/jetbrains/kotlin/analysis/api/contracts/description/KaContractCallsInPlaceContractEffectDeclaration"
      "org/jetbrains/kotlin/analysis/api/contracts/description/KtContractConditionalContractEffectDeclaration" ->
        "org/jetbrains/kotlin/analysis/api/contracts/description/KaContractConditionalContractEffectDeclaration"
      "org/jetbrains/kotlin/analysis/api/contracts/description/KtContractConstantValue" ->
        "org/jetbrains/kotlin/analysis/api/contracts/description/KaContractConstantValue"
      "org/jetbrains/kotlin/analysis/api/contracts/description/KtContractEffectDeclaration" ->
        "org/jetbrains/kotlin/analysis/api/contracts/description/KaContractEffectDeclaration"
      "org/jetbrains/kotlin/analysis/api/contracts/description/KtContractParameterValue" ->
        "org/jetbrains/kotlin/analysis/api/contracts/description/KaContractParameterValue"
      "org/jetbrains/kotlin/analysis/api/contracts/description/KtContractReturnsContractEffectDeclaration" ->
        "org/jetbrains/kotlin/analysis/api/contracts/description/KaContractReturnsContractEffectDeclaration"
      "org/jetbrains/kotlin/analysis/api/contracts/description/booleans/KtContractBinaryLogicExpression" ->
        "org/jetbrains/kotlin/analysis/api/contracts/description/booleans/KaContractBinaryLogicExpression"
      "org/jetbrains/kotlin/analysis/api/contracts/description/booleans/KtContractBooleanConstantExpression" ->
        "org/jetbrains/kotlin/analysis/api/contracts/description/booleans/KaContractBooleanConstantExpression"
      "org/jetbrains/kotlin/analysis/api/contracts/description/booleans/KtContractBooleanExpression" ->
        "org/jetbrains/kotlin/analysis/api/contracts/description/booleans/KaContractBooleanExpression"
      "org/jetbrains/kotlin/analysis/api/contracts/description/booleans/KtContractBooleanValueParameterExpression" ->
        "org/jetbrains/kotlin/analysis/api/contracts/description/booleans/KaContractBooleanValueParameterExpression"
      "org/jetbrains/kotlin/analysis/api/contracts/description/booleans/KtContractIsInstancePredicateExpression" ->
        "org/jetbrains/kotlin/analysis/api/contracts/description/booleans/KaContractIsInstancePredicateExpression"
      "org/jetbrains/kotlin/analysis/api/contracts/description/booleans/KtContractIsNullPredicateExpression" ->
        "org/jetbrains/kotlin/analysis/api/contracts/description/booleans/KaContractIsNullPredicateExpression"
      "org/jetbrains/kotlin/analysis/api/contracts/description/booleans/KtContractLogicalNotExpression" ->
        "org/jetbrains/kotlin/analysis/api/contracts/description/booleans/KaContractLogicalNotExpression"
      "org/jetbrains/kotlin/analysis/api/diagnostics/KtDiagnostic" ->
        "org/jetbrains/kotlin/analysis/api/diagnostics/KaDiagnostic"
      "org/jetbrains/kotlin/analysis/api/diagnostics/KtDiagnosticWithPsi" ->
        "org/jetbrains/kotlin/analysis/api/diagnostics/KaDiagnosticWithPsi"
      "org/jetbrains/kotlin/analysis/api/fir/diagnostics/KtCompilerPluginDiagnostic0" ->
        "org/jetbrains/kotlin/analysis/api/fir/diagnostics/KaCompilerPluginDiagnostic0"
      "org/jetbrains/kotlin/analysis/api/fir/diagnostics/KtCompilerPluginDiagnostic1" ->
        "org/jetbrains/kotlin/analysis/api/fir/diagnostics/KaCompilerPluginDiagnostic1"
      "org/jetbrains/kotlin/analysis/api/fir/diagnostics/KtCompilerPluginDiagnostic2" ->
        "org/jetbrains/kotlin/analysis/api/fir/diagnostics/KaCompilerPluginDiagnostic2"
      "org/jetbrains/kotlin/analysis/api/fir/diagnostics/KtCompilerPluginDiagnostic3" ->
        "org/jetbrains/kotlin/analysis/api/fir/diagnostics/KaCompilerPluginDiagnostic3"
      "org/jetbrains/kotlin/analysis/api/fir/diagnostics/KtCompilerPluginDiagnostic4" ->
        "org/jetbrains/kotlin/analysis/api/fir/diagnostics/KaCompilerPluginDiagnostic4"
      "org/jetbrains/kotlin/analysis/api/lifetime/KtIllegalLifetimeOwnerAccessException" ->
        "org/jetbrains/kotlin/analysis/api/lifetime/KaIllegalLifetimeOwnerAccessException"
      "org/jetbrains/kotlin/analysis/api/lifetime/KtInaccessibleLifetimeOwnerAccessException" ->
        "org/jetbrains/kotlin/analysis/api/lifetime/KaInaccessibleLifetimeOwnerAccessException"
      "org/jetbrains/kotlin/analysis/api/lifetime/KtInvalidLifetimeOwnerAccessException" ->
        "org/jetbrains/kotlin/analysis/api/lifetime/KaInvalidLifetimeOwnerAccessException"
      "org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeOwner" ->
        "org/jetbrains/kotlin/analysis/api/lifetime/KaLifetimeOwner"
      "org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeOwnerField" ->
        "org/jetbrains/kotlin/analysis/api/lifetime/KaLifetimeOwnerField"
      "org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken" ->
        "org/jetbrains/kotlin/analysis/api/lifetime/KaLifetimeToken"
      "org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory" ->
        "org/jetbrains/kotlin/analysis/api/lifetime/KaLifetimeTokenFactory"
      "org/jetbrains/kotlin/analysis/api/renderer/base/KtKeywordRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/base/KaKeywordRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/base/KtKeywordsRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/base/KaKeywordsRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/base/annotations/KtAnnotationRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/base/annotations/KaAnnotationRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/base/annotations/KtAnnotationRendererForSource" ->
        "org/jetbrains/kotlin/analysis/api/renderer/base/annotations/KaAnnotationRendererForSource"
      "org/jetbrains/kotlin/analysis/api/renderer/base/annotations/KtRendererAnnotationsFilter" ->
        "org/jetbrains/kotlin/analysis/api/renderer/base/annotations/KaRendererAnnotationsFilter"
      "org/jetbrains/kotlin/analysis/api/renderer/base/annotations/renderers/KtAnnotationArgumentsRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/base/annotations/renderers/KaAnnotationArgumentsRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/base/annotations/renderers/KtAnnotationListRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/base/annotations/renderers/KaAnnotationListRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/base/annotations/renderers/KtAnnotationQualifierRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/base/annotations/renderers/KaAnnotationQualifierRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/base/annotations/renderers/KtAnnotationUseSiteTargetRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/base/annotations/renderers/KaAnnotationUseSiteTargetRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/base/contextReceivers/KtContextReceiversRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/base/contextReceivers/KaContextReceiversRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/base/contextReceivers/KtContextReceiversRendererForSource" ->
        "org/jetbrains/kotlin/analysis/api/renderer/base/contextReceivers/KaContextReceiversRendererForSource"
      "org/jetbrains/kotlin/analysis/api/renderer/base/contextReceivers/renderers/KtContextReceiverLabelRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/base/contextReceivers/renderers/KaContextReceiverLabelRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/base/contextReceivers/renderers/KtContextReceiverListRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/base/contextReceivers/renderers/KaContextReceiverListRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/KtCallableReturnTypeFilter" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/KaCallableReturnTypeFilter"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/KtDeclarationRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/KaDeclarationRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/KtRecommendedRendererCodeStyle" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/KaRecommendedRendererCodeStyle"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/KtRendererCodeStyle" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/KaRendererCodeStyle"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/KtRendererTypeApproximator" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/KaRendererTypeApproximator"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/bodies/KtFunctionLikeBodyRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/bodies/KaFunctionLikeBodyRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/bodies/KtParameterDefaultValueRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/bodies/KaParameterDefaultValueRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/bodies/KtPropertyAccessorBodyRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/bodies/KaPropertyAccessorBodyRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/bodies/KtRendererBodyMemberScopeProvider" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/bodies/KaRendererBodyMemberScopeProvider"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/bodies/KtRendererBodyMemberScopeSorter" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/bodies/KaRendererBodyMemberScopeSorter"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/bodies/KtScriptInitializerRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/bodies/KaScriptInitializerRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/bodies/KtVariableInitializerRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/bodies/KaVariableInitializerRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/impl/KtDeclarationRendererForDebug" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/impl/KaDeclarationRendererForDebug"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/impl/KtDeclarationRendererForSource" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/impl/KaDeclarationRendererForSource"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/modifiers/KtDeclarationModifiersRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/modifiers/KaDeclarationModifiersRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/modifiers/impl/KtDeclarationModifiersRendererForSource" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/modifiers/impl/KaDeclarationModifiersRendererForSource"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/modifiers/renderers/KtModifierListRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/modifiers/renderers/KaModifierListRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/modifiers/renderers/KtModifiersSorter" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/modifiers/renderers/KaModifiersSorter"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/modifiers/renderers/KtRendererKeywordFilter" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/modifiers/renderers/KaRendererKeywordFilter"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/modifiers/renderers/KtRendererModalityModifierProvider" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/modifiers/renderers/KaRendererModalityModifierProvider"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/modifiers/renderers/KtRendererOtherModifiersProvider" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/modifiers/renderers/KaRendererOtherModifiersProvider"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/modifiers/renderers/KtRendererVisibilityModifierProvider" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/modifiers/renderers/KaRendererVisibilityModifierProvider"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/KtCallableParameterRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/KaCallableParameterRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/KtClassInitializerRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/KaClassInitializerRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/KtClassifierBodyRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/KaClassifierBodyRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/KtClassifierBodyWithMembersRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/KaClassifierBodyWithMembersRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/KtDeclarationNameRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/KaDeclarationNameRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/KtTypeParameterRendererFilter" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/KaTypeParameterRendererFilter"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/KtTypeParametersRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/KaTypeParametersRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KaFunctionSymbolRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KaNamedFunctionSymbolRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KtAnonymousFunctionSymbolRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KaAnonymousFunctionSymbolRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KtBackingFieldSymbolRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KaBackingFieldSymbolRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KtCallableReceiverRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KaCallableReceiverRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KtCallableReturnTypeRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KaCallableReturnTypeRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KtCallableSignatureRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KaCallableSignatureRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KtConstructorSymbolRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KaConstructorSymbolRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KtDestructuringDeclarationRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KaDestructuringDeclarationRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KtEnumEntrySymbolRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KaEnumEntrySymbolRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KtFunctionSymbolRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KaNamedFunctionSymbolRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KtJavaFieldSymbolRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KaJavaFieldSymbolRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KtKotlinPropertySymbolRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KaKotlinPropertySymbolRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KtLocalVariableSymbolRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KaLocalVariableSymbolRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KtPropertyAccessorsRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KaPropertyAccessorsRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KtPropertyGetterSymbolRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KaPropertyGetterSymbolRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KtPropertySetterSymbolRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KaPropertySetterSymbolRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KtSamConstructorSymbolRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KaSamConstructorSymbolRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KtScriptSymbolRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KaScriptSymbolRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KtSyntheticJavaPropertySymbolRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KaSyntheticJavaPropertySymbolRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KtValueParameterSymbolRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/callables/KaValueParameterSymbolRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/classifiers/KaNamedClassOrObjectSymbolRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/classifiers/KaNamedClassSymbolRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/classifiers/KtAnonymousObjectSymbolRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/classifiers/KaAnonymousObjectSymbolRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/classifiers/KtNamedClassOrObjectSymbolRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/classifiers/KaNamedClassSymbolRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/classifiers/KtSingleTypeParameterSymbolRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/classifiers/KaSingleTypeParameterSymbolRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/classifiers/KtTypeAliasSymbolRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/renderers/classifiers/KaTypeAliasSymbolRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/superTypes/KtSuperTypeListRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/superTypes/KaSuperTypeListRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/superTypes/KtSuperTypeRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/superTypes/KaSuperTypeRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/superTypes/KtSuperTypesCallArgumentsRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/superTypes/KaSuperTypesCallArgumentsRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/declarations/superTypes/KtSuperTypesFilter" ->
        "org/jetbrains/kotlin/analysis/api/renderer/declarations/superTypes/KaSuperTypesFilter"
      "org/jetbrains/kotlin/analysis/api/renderer/types/KtExpandedTypeRenderingMode" ->
        "org/jetbrains/kotlin/analysis/api/renderer/types/KaExpandedTypeRenderingMode"
      "org/jetbrains/kotlin/analysis/api/renderer/types/KtTypeRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/types/KaTypeRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/types/impl/KtTypeRendererForDebug" ->
        "org/jetbrains/kotlin/analysis/api/renderer/types/impl/KaTypeRendererForDebug"
      "org/jetbrains/kotlin/analysis/api/renderer/types/impl/KtTypeRendererForSource" ->
        "org/jetbrains/kotlin/analysis/api/renderer/types/impl/KaTypeRendererForSource"
      "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KtCapturedTypeRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KaCapturedTypeRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KtClassTypeQualifierRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KaClassTypeQualifierRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KtDefinitelyNotNullTypeRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KaDefinitelyNotNullTypeRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KtDynamicTypeRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KaDynamicTypeRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KtFlexibleTypeRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KaFlexibleTypeRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KtFunctionalTypeRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KaFunctionalTypeRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KtIntersectionTypeRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KaIntersectionTypeRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KtTypeErrorTypeRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KaErrorTypeRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KtTypeNameRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KaTypeNameRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KtTypeParameterTypeRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KaTypeParameterTypeRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KtTypeProjectionRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KaTypeProjectionRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KtUnresolvedClassErrorTypeRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KaUnresolvedClassErrorTypeRenderer"
      "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KtUsualClassTypeRenderer" ->
        "org/jetbrains/kotlin/analysis/api/renderer/types/renderers/KaUsualClassTypeRenderer"
      "org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedFunctionSymbol" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedSymbol"
      "org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedVariableSymbol" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedSymbol"
      "org/jetbrains/kotlin/analysis/api/resolution/KtApplicableCallCandidateInfo" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaApplicableCallCandidateInfo"
      "org/jetbrains/kotlin/analysis/api/resolution/KtCallCandidateInfo" ->
        "org/jetbrains/kotlin/analysis/api/resolution/KaCallCandidateInfo"
      "org/jetbrains/kotlin/analysis/api/resolve/extensions/KtResolveExtension" ->
        "org/jetbrains/kotlin/analysis/api/resolve/extensions/KaResolveExtension"
      "org/jetbrains/kotlin/analysis/api/resolve/extensions/KtResolveExtensionFile" ->
        "org/jetbrains/kotlin/analysis/api/resolve/extensions/KaResolveExtensionFile"
      "org/jetbrains/kotlin/analysis/api/resolve/extensions/KtResolveExtensionNavigationTargetsProvider" ->
        "org/jetbrains/kotlin/analysis/api/resolve/extensions/KaResolveExtensionNavigationTargetsProvider"
      "org/jetbrains/kotlin/analysis/api/resolve/extensions/KtResolveExtensionProvider" ->
        "org/jetbrains/kotlin/analysis/api/resolve/extensions/KaResolveExtensionProvider"
      "org/jetbrains/kotlin/analysis/api/scopes/KtScope" ->
        "org/jetbrains/kotlin/analysis/api/scopes/KaScope"
      "org/jetbrains/kotlin/analysis/api/scopes/KtScopeLike" ->
        "org/jetbrains/kotlin/analysis/api/scopes/KaScopeLike"
      "org/jetbrains/kotlin/analysis/api/scopes/KtScopeNameFilter" ->
        "org/jetbrains/kotlin/analysis/api/scopes/(Name) -> Boolean"
      "org/jetbrains/kotlin/analysis/api/scopes/KtTypeScope" ->
        "org/jetbrains/kotlin/analysis/api/scopes/KaTypeScope"
      "org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider" ->
        "org/jetbrains/kotlin/analysis/api/session/KaSessionProvider"
      "org/jetbrains/kotlin/analysis/api/signatures/KaFunctionLikeSignature" ->
        "org/jetbrains/kotlin/analysis/api/signatures/KaFunctionSignature"
      "org/jetbrains/kotlin/analysis/api/signatures/KaVariableLikeSignature" ->
        "org/jetbrains/kotlin/analysis/api/signatures/KaVariableSignature"
      "org/jetbrains/kotlin/analysis/api/signatures/KtCallableSignature" ->
        "org/jetbrains/kotlin/analysis/api/signatures/KaCallableSignature"
      "org/jetbrains/kotlin/analysis/api/signatures/KtFunctionLikeSignature" ->
        "org/jetbrains/kotlin/analysis/api/signatures/KaFunctionSignature"
      "org/jetbrains/kotlin/analysis/api/signatures/KtVariableLikeSignature" ->
        "org/jetbrains/kotlin/analysis/api/signatures/KaVariableSignature"
      "org/jetbrains/kotlin/analysis/api/symbols/KaClassOrObjectSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaClassSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KaFunctionLikeSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaFunctionSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KaNamedClassOrObjectSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaNamedClassSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KaVariableLikeSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaVariableSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtAnonymousFunctionSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaAnonymousFunctionSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtAnonymousObjectSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaAnonymousObjectSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtBackingFieldSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaBackingFieldSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtCallableSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaCallableSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtClassInitializerSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaClassInitializerSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtClassKind" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaClassKind"
      "org/jetbrains/kotlin/analysis/api/symbols/KtClassLikeSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaClassLikeSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtClassOrObjectSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaClassSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtClassifierSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaClassifierSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtConstructorSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaConstructorSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtDeclarationSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaDeclarationSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtDestructuringDeclarationSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaDestructuringDeclarationSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtEnumEntryInitializerSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaEnumEntryInitializerSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtEnumEntrySymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaEnumEntrySymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtFileSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaFileSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtFunctionLikeSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaFunctionSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtFunctionSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaNamedFunctionSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtJavaFieldSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaJavaFieldSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtKotlinPropertySymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaKotlinPropertySymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtLocalVariableSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaLocalVariableSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtNamedClassOrObjectSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaNamedClassSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtPackageSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaPackageSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtParameterSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaParameterSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtPropertyAccessorSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaPropertyAccessorSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtPropertyGetterSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaPropertyGetterSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtPropertySetterSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaPropertySetterSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtPropertySymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaPropertySymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtReceiverParameterSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaReceiverParameterSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtSamConstructorSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaSamConstructorSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtScriptSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaScriptSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtSymbolOrigin" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaSymbolOrigin"
      "org/jetbrains/kotlin/analysis/api/symbols/KtSyntheticJavaPropertySymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaSyntheticJavaPropertySymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtTypeAliasSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaTypeAliasSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtTypeParameterSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaTypeParameterSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtValueParameterSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaValueParameterSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtVariableLikeSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaVariableSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/KtVariableSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaVariableSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/markers/KaPossibleMultiplatformSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaDeclarationSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/markers/KaSymbolWithMembers" ->
        "org/jetbrains/kotlin/analysis/api/symbols/markers/KaDeclarationContainerSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/markers/KaSymbolWithModality" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaDeclarationSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/markers/KaSymbolWithTypeParameters" ->
        "org/jetbrains/kotlin/analysis/api/symbols/markers/KaTypeParameterOwnerSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/markers/KaSymbolWithVisibility" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaDeclarationSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/markers/KtAnnotatedSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/markers/KaAnnotatedSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/markers/KtNamedSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/markers/KaNamedSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/markers/KtPossibleMultiplatformSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaDeclarationSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/markers/KtPossiblyNamedSymbol" ->
        "org/jetbrains/kotlin/analysis/api/symbols/markers/KaPossiblyNamedSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/markers/KtSymbolKind" ->
        "org/jetbrains/kotlin/analysis/api/symbols/markers/KaSymbolKind"
      "org/jetbrains/kotlin/analysis/api/symbols/markers/KtSymbolWithKind" ->
        "org/jetbrains/kotlin/analysis/api/symbols/markers/KaSymbolWithKind"
      "org/jetbrains/kotlin/analysis/api/symbols/markers/KtSymbolWithMembers" ->
        "org/jetbrains/kotlin/analysis/api/symbols/markers/KaDeclarationContainerSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/markers/KtSymbolWithModality" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaDeclarationSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/markers/KtSymbolWithTypeParameters" ->
        "org/jetbrains/kotlin/analysis/api/symbols/markers/KaTypeParameterOwnerSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/markers/KtSymbolWithVisibility" ->
        "org/jetbrains/kotlin/analysis/api/symbols/KaDeclarationSymbol"
      "org/jetbrains/kotlin/analysis/api/symbols/pointers/KtPsiBasedSymbolPointer" ->
        "org/jetbrains/kotlin/analysis/api/symbols/pointers/KaPsiBasedSymbolPointer"
      "org/jetbrains/kotlin/analysis/api/symbols/pointers/KtSymbolPointer" ->
        "org/jetbrains/kotlin/analysis/api/symbols/pointers/KaSymbolPointer"
      "org/jetbrains/kotlin/analysis/api/types/KaFunctionalType" ->
        "org/jetbrains/kotlin/analysis/api/types/KaFunctionType"
      "org/jetbrains/kotlin/analysis/api/types/KaNonErrorClassType" ->
        "org/jetbrains/kotlin/analysis/api/types/KaClassType"
      "org/jetbrains/kotlin/analysis/api/types/KaTypeErrorType" ->
        "org/jetbrains/kotlin/analysis/api/types/KaErrorType"
      "org/jetbrains/kotlin/analysis/api/types/KtCapturedType" ->
        "org/jetbrains/kotlin/analysis/api/types/KaCapturedType"
      "org/jetbrains/kotlin/analysis/api/types/KtClassErrorType" ->
        "org/jetbrains/kotlin/analysis/api/types/KaClassErrorType"
      "org/jetbrains/kotlin/analysis/api/types/KtClassTypeQualifier" ->
        "org/jetbrains/kotlin/analysis/api/types/KaClassTypeQualifier"
      "org/jetbrains/kotlin/analysis/api/types/KtDefinitelyNotNullType" ->
        "org/jetbrains/kotlin/analysis/api/types/KaDefinitelyNotNullType"
      "org/jetbrains/kotlin/analysis/api/types/KtDynamicType" ->
        "org/jetbrains/kotlin/analysis/api/types/KaDynamicType"
      "org/jetbrains/kotlin/analysis/api/types/KtErrorType" ->
        "org/jetbrains/kotlin/analysis/api/types/KaErrorType"
      "org/jetbrains/kotlin/analysis/api/types/KtFlexibleType" ->
        "org/jetbrains/kotlin/analysis/api/types/KaFlexibleType"
      "org/jetbrains/kotlin/analysis/api/types/KtFunctionalType" ->
        "org/jetbrains/kotlin/analysis/api/types/KaFunctionType"
      "org/jetbrains/kotlin/analysis/api/types/KtIntersectionType" ->
        "org/jetbrains/kotlin/analysis/api/types/KaIntersectionType"
      "org/jetbrains/kotlin/analysis/api/types/KtNonErrorClassType" ->
        "org/jetbrains/kotlin/analysis/api/types/KaClassType"
      "org/jetbrains/kotlin/analysis/api/types/KtType" ->
        "org/jetbrains/kotlin/analysis/api/types/KaType"
      "org/jetbrains/kotlin/analysis/api/types/KtTypeErrorType" ->
        "org/jetbrains/kotlin/analysis/api/types/KaErrorType"
      "org/jetbrains/kotlin/analysis/api/types/KtTypeMappingMode" ->
        "org/jetbrains/kotlin/analysis/api/types/KaTypeMappingMode"
      "org/jetbrains/kotlin/analysis/api/types/KtTypeNullability" ->
        "org/jetbrains/kotlin/analysis/api/types/KaTypeNullability"
      "org/jetbrains/kotlin/analysis/api/types/KtTypeParameterType" ->
        "org/jetbrains/kotlin/analysis/api/types/KaTypeParameterType"
      "org/jetbrains/kotlin/analysis/api/types/KtUsualClassType" ->
        "org/jetbrains/kotlin/analysis/api/types/KaUsualClassType"
      else -> {
        val ktPrefix = "org/jetbrains/kotlin/analysis/api/"
        val lastIndex = s.lastIndexOf("/")
        if (s.startsWith(ktPrefix) && s.startsWith("Kt", lastIndex + 1)) {
          // Package level methods live in classes like KtCallKt, which don't have associated
          // type aliases but must be moved
          s.substring(0, lastIndex + 1) + "Ka" + s.substring(lastIndex + 3)
        } else {
          null
        }
      }
    }
  }

  private fun migrateClassNames(jarBytes: ByteArray): ByteArray {
    val classReader = ClassReader(jarBytes)
    val classWriter =
      object : ClassWriter(classReader, COMPUTE_MAXS or COMPUTE_FRAMES) {
        override fun getCommonSuperClass(type1: String, type2: String): String {
          return try {
            super.getCommonSuperClass(type1, type2)
          } catch (e: TypeNotPresentException) {
            "java/lang/Object"
          }
        }
      }
    val mapper =
      object : Remapper() {
        override fun mapMethodName(owner: String?, name: String?, descriptor: String?): String? {
          return mapClass(name) ?: name
        }

        override fun mapInvokeDynamicMethodName(name: String?, descriptor: String?): String? {
          return mapClass(name) ?: name
        }

        override fun mapRecordComponentName(
          owner: String?,
          name: String,
          descriptor: String?,
        ): String {
          return mapClass(name) ?: name
        }

        override fun mapFieldName(owner: String?, name: String?, descriptor: String?): String? {
          return mapClass(name) ?: name
        }

        override fun mapPackageName(name: String): String {
          return mapClass(name) ?: name
        }

        override fun mapModuleName(name: String): String {
          return mapClass(name) ?: name
        }

        override fun map(internalName: String?): String? {
          return mapClass(internalName) ?: internalName
        }
      }

    val myVisitor = SwitchToInterfaceClassVisitor(classWriter)
    val classMapper = ClassRemapper(myVisitor, mapper)
    classReader.accept(classMapper, 0)
    return classWriter.toByteArray()
  }

  /**
   * Class visitor which looks up method calls, locates the corresponding method call via reflection
   * and checks whether the bytecode needs to be changed to interface invocation (since a bunch of
   * Kotlin analysis API methods were moved to interfaces.)
   */
  private inner class SwitchToInterfaceClassVisitor(chained: ClassVisitor) :
    ClassVisitor(ASM9, chained) {
    override fun visitMethod(
      access: Int,
      name: String?,
      descriptor: String?,
      signature: String?,
      exceptions: Array<out String>?,
    ): MethodVisitor {
      return SwitchToInterfaceMethodVisitor(
        super.visitMethod(access, name, descriptor, signature, exceptions)
      )
    }
  }

  private inner class SwitchToInterfaceMethodVisitor(chained: MethodVisitor) :
    MethodVisitor(ASM9, chained) {
    override fun visitMethodInsn(
      opcode: Int,
      owner: String,
      name: String,
      descriptor: String,
      isInterface: Boolean,
    ) {
      if (
        (opcode == Opcodes.INVOKESTATIC || opcode == Opcodes.INVOKEVIRTUAL) && isRelevantType(owner)
      ) {
        val clz = findClass(owner)
        if (clz == null) {
          client.log(
            Severity.WARNING,
            null,
            "WARNING: Missing analysis API method ${owner}#${name}${descriptor}",
          )
        } else if (clz.isInterface) {
          // Should be invoked on interface instead!
          super.visitMethodInsn(Opcodes.INVOKEINTERFACE, owner, name, descriptor, true)
          return
        }
      }

      super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    }
  }

  private fun isRelevantMethod(methodNode: MethodNode): Boolean {
    return methodNode.find {
      it is MethodInsnNode && it.owner.startsWith("org/jetbrains/kotlin/analysis/api/")
    } != null
  }

  private fun MethodNode.find(condition: (AbstractInsnNode) -> Boolean): AbstractInsnNode? {
    for (instruction in instructions) {
      if (condition(instruction)) {
        return instruction
      }
    }
    return null
  }

  private fun migrateAnalyzeCall(classBytes: ByteArray): ByteArray {
    val cr = ClassReader(classBytes)
    val cn = ClassNode()
    cr.accept(cn, 0)

    var modified = false
    for (method in cn.methods) {
      if (isRelevantMethod(method)) {
        val before = if (DEBUG) method.prettyPrint() else ""

        modified = migrateAnalyzeCall(method) || modified

        if (DEBUG) {
          val after = method.prettyPrint()
          File("/tmp/before").writeText(before)
          File("/tmp/after").writeText(after)
          // breakpoint here then run idea diff /tmp/before /tmp/after
        }
      }

      if (
        (cn.name == "androidx/navigation/lint/common/LintUtilKt" ||
          cn.name == "androidx/navigation/common/lint/LintUtilKt") &&
          (method.name == "isClassReference" || method.name == "isClassReference\$default")
      ) {
        if (handleLintUtilRedirection(method)) {
          modified = true
        }
      }
    }

    if (!modified) {
      return classBytes
    }

    val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
    cn.accept(cw)
    val result = cw.toByteArray()
    return result
  }

  private fun handleLintUtilRedirection(method: MethodNode): Boolean {
    if (
      method.name == "isClassReference" &&
        (method.desc == "(Lorg/jetbrains/uast/UExpression;)Lkotlin/Pair;" ||
          method.desc == "(Lorg/jetbrains/uast/UExpression;ZZZ)Lkotlin/Pair;")
    ) {
      // Map isClassReference() to LintUtils isClassReference(true, true, true) in lint

      // Special case: get rid of this AndroidX utility method implementation and
      // just delegate to the built-in one
      val instructions = method.instructions
      instructions.clear()
      val startLabel = LabelNode()
      instructions.add(startLabel)
      instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
      if (method.desc == "(Lorg/jetbrains/uast/UExpression;)Lkotlin/Pair;") {
        instructions.add(InsnNode(Opcodes.ICONST_1))
        instructions.add(InsnNode(Opcodes.ICONST_1))
        instructions.add(InsnNode(Opcodes.ICONST_1))
      } else if (method.desc == "(Lorg/jetbrains/uast/UExpression;ZZZ)Lkotlin/Pair;") {
        instructions.add(VarInsnNode(Opcodes.ILOAD, 1))
        instructions.add(VarInsnNode(Opcodes.ILOAD, 2))
        instructions.add(VarInsnNode(Opcodes.ILOAD, 3))
      } else {
        return false
      }
      instructions.add(
        MethodInsnNode(
          Opcodes.INVOKESTATIC,
          "com/android/tools/lint/detector/api/UastLintUtilsKt",
          method.name,
          "(Lorg/jetbrains/uast/UExpression;ZZZ)Lkotlin/Pair;",
        )
      )
      instructions.add(InsnNode(Opcodes.ARETURN))
      val endLabel = LabelNode()
      instructions.add(endLabel)
      method.maxStack = 2
      method.maxLocals = 4
      method.tryCatchBlocks.clear()
      method.localVariables.clear()
      method.localVariables.add(
        LocalVariableNode(
          "\$this\$isClassReference",
          "Lorg/jetbrains/uast/UExpression;",
          null,
          startLabel,
          endLabel,
          0,
        )
      )
      return true
    } else if (
      method.name == "isClassReference\$default" &&
        method.desc == "(Lorg/jetbrains/uast/UExpression;ZZZILjava/lang/Object;)Lkotlin/Pair;"
    ) {
      val replace =
        method.instructions.firstOrNull {
          it.isStaticCall(
            "isClassReference",
            "androidx/navigation/lint/common/LintUtilKt",
            "(Lorg/jetbrains/uast/UExpression;ZZZ)Lkotlin/Pair;",
          )
        } ?: return false
      (replace as MethodInsnNode).owner = "com/android/tools/lint/detector/api/UastLintUtilsKt"
      return true
    }

    return false
  }

  private fun AbstractInsnNode.isCall(name: String, owner: String, desc: String? = null): Boolean {
    val curr = this as? MethodInsnNode ?: return false
    return curr.name == name && curr.owner == owner && (desc == null || curr.desc == desc)
  }

  private fun AbstractInsnNode.isVirtualCall(
    name: String,
    owner: String,
    desc: String? = null,
  ): Boolean {
    return opcode == Opcodes.INVOKEVIRTUAL && isCall(name, owner, desc)
  }

  private fun AbstractInsnNode.isStaticCall(
    name: String,
    owner: String,
    desc: String? = null,
  ): Boolean {
    return opcode == Opcodes.INVOKESTATIC && isCall(name, owner, desc)
  }

  @Suppress("UNUSED_VARIABLE")
  private fun migrateAnalyzeCall(methodNode: MethodNode): Boolean {
    var modified = false

    val instructions = methodNode.instructions

    var curr = instructions.first

    fun prev(): AbstractInsnNode? {
      curr = curr.prev()
      return curr
    }

    fun expectAload(): Int? {
      return if (curr.opcode == Opcodes.ALOAD) {
        (curr as VarInsnNode).`var`
      } else {
        null
      }
    }

    fun expectVirtualCall(name: String, owner: String, desc: String? = null): AbstractInsnNode? {
      return if (curr.isVirtualCall(name, owner, desc)) curr else null
    }

    var ktElementVariable = -1
    while (curr != null) {
      if (
        curr.isVirtualCall(
          "beforeEnteringAnalysisContext",
          "org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory",
          "(Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;)V",
        )
      ) {
        // We've found the fingerprint of the beginning of an analyze() {} call
        // using the old analysis API implementation:
        //          ALOAD 4
        //          ASTORE 6
        //          ALOAD 4
        //          ALOAD 2 // this register holds the KtElement (and there may be a checkcast here
        // first in some cases)
        //      ==> INVOKEVIRTUAL
        // org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getAnalysisSession
        // (Lorg/jetbrains/kotlin/psi/KtElement;)Lorg/jetbrains/kotlin/analysis/api/KtAnalysisSession;
        //          ASTORE 7
        //         L16
        //          ICONST_0
        //          ISTORE 8
        //         L17
        //          LINENUMBER 210 L17
        //          ALOAD 6
        //          INVOKEVIRTUAL
        // org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getNoWriteActionInAnalyseCallChecker ()Lorg/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker;
        //          INVOKEVIRTUAL
        // org/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker.beforeEnteringAnalysisContext ()V
        //         L18
        //          LINENUMBER 211 L18
        //          ALOAD 6
        //          INVOKEVIRTUAL
        // org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getTokenFactory
        // ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory;
        //          ALOAD 7
        //          INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getToken
        // ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;
        //          INVOKEVIRTUAL
        // org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory.beforeEnteringAnalysisContext (Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;)V
        val end = curr.next

        prev() ?: break
        expectVirtualCall("getToken", "org/jetbrains/kotlin/analysis/api/KtAnalysisSession")
          ?: break
        prev() ?: break
        val sessionVariable = expectAload() ?: break
        prev() ?: break
        expectVirtualCall(
          "getTokenFactory",
          "org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider",
        ) ?: break
        prev() ?: break
        val sessionProviderVariable = expectAload() ?: break
        prev() ?: break
        expectVirtualCall(
          "beforeEnteringAnalysisContext",
          "org/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker",
        ) ?: break
        prev() ?: break
        expectVirtualCall(
          "getNoWriteActionInAnalyseCallChecker",
          "org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider",
        ) ?: break
        prev() ?: break
        val sessionProviderVariable2 = expectAload() ?: break
        val startReplace: AbstractInsnNode = curr
        prev() ?: break // ISTORE
        prev() ?: break // ICONST-0
        prev() ?: break // ASTORE (analysis session variable)
        val getAnalysisSession = prev() ?: break
        expectVirtualCall(
          "getAnalysisSession",
          "org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider",
        ) ?: break

        prev() ?: break
        if (curr.opcode == Opcodes.CHECKCAST) {
          prev() ?: break
        }
        ktElementVariable = expectAload() ?: break

        val last = startReplace.previous
        var c = last.next
        while (true) {
          if (c === end) {
            break
          }
          val next = c.next
          if (c !is LabelNode && c !is LineNumberNode) {
            instructions.remove(c)
          }
          c = next
        }

        val i1 = VarInsnNode(Opcodes.ALOAD, sessionProviderVariable)
        val i2 = VarInsnNode(Opcodes.ALOAD, sessionVariable)
        val i3 = VarInsnNode(Opcodes.ALOAD, ktElementVariable)
        val i4 =
          MethodInsnNode(
            Opcodes.INVOKEVIRTUAL,
            "org/jetbrains/kotlin/analysis/api/session/KaSessionProvider",
            "beforeEnteringAnalysis",
            "(Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/psi/KtElement;)V",
            false,
          )

        instructions.insert(last, i1)
        instructions.insert(i1, i2)
        instructions.insert(i2, i3)
        instructions.insert(i3, i4)

        curr = end
        modified = true
      } else if (
        curr.isVirtualCall(
          "afterLeavingAnalysisContext",
          "org/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker",
        )
      ) {
        // This is the fingerprint of the *end* of the old analysis API's version
        // of the analyze() call, after the lambda. Replace it with the new
        // implementation.
        //
        // Match this segment:
        //      80: aload         5
        //      82: invokevirtual #56                 // Method
        // org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getTokenFactory:()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory;
        //      85: aload         6
        //      87: invokevirtual #62                 // Method
        // org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getToken:()Lorg/jetbrains/kotlin/analysis/providers/lifetime/KtLifetimeToken;
        //      90: invokevirtual #90                 // Method
        // org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory.afterLeavingAnalysisContext:(Lorg/jetbrains/kotlin/analysis/providers/lifetime/KtLifetimeToken;)V
        //      93: aload         5
        //      95: invokevirtual #47                 // Method
        // org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getNoWriteActionInAnalyseCallChecker:()Lorg/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker;
        //      98: invokevirtual #92                 // Method
        // org/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker.afterLeavingAnalysisContext:()V
        //
        // and replace it with this:
        //      60: aload $sessionProviderVar
        //      61: aload $sessionVar
        //      63: aload $ktElementVar
        //      64: invokevirtual #70                 // Method
        // org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.afterLeavingAnalysis:(Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/psi/KtElement;)V
        val end = curr.next

        prev() ?: break
        expectVirtualCall(
          "getNoWriteActionInAnalyseCallChecker",
          "org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider",
        ) ?: break
        prev() ?: break
        val sessionProviderVariable = expectAload() ?: break
        prev() ?: break
        expectVirtualCall(
          "afterLeavingAnalysisContext",
          "org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory",
        ) ?: break
        prev() ?: break
        expectVirtualCall("getToken", "org/jetbrains/kotlin/analysis/api/KtAnalysisSession")
          ?: break
        prev() ?: break
        val sessionVariable = expectAload() ?: break
        prev() ?: break
        expectVirtualCall(
          "getTokenFactory",
          "org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider",
        ) ?: break
        prev() ?: break
        expectAload() ?: break

        val last = curr.previous ?: break
        var c = last.next
        while (true) {
          if (c === end) {
            break
          }
          val next = c.next
          if (c !is LabelNode && c !is LineNumberNode) {
            instructions.remove(c)
          }
          c = next
        }

        val i1 = VarInsnNode(Opcodes.ALOAD, sessionProviderVariable)
        val i2 = VarInsnNode(Opcodes.ALOAD, sessionVariable)
        val i3 = VarInsnNode(Opcodes.ALOAD, ktElementVariable)
        val i4 =
          MethodInsnNode(
            Opcodes.INVOKEVIRTUAL,
            "org/jetbrains/kotlin/analysis/api/session/KaSessionProvider",
            "afterLeavingAnalysis",
            "(Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/psi/KtElement;)V",
            false,
          )

        instructions.insert(last, i1)
        instructions.insert(i1, i2)
        instructions.insert(i2, i3)
        instructions.insert(i3, i4)

        curr = end
        modified = true
      } else if (
        curr.isStaticCall(
          "getAllSuperTypes\$default",
          "org/jetbrains/kotlin/analysis/api/components/KtTypeProviderMixIn",
          // "((Lorg/jetbrains/kotlin/analysis/api/components/KtTypeProviderMixIn;Lorg/jetbrains/kotlin/analysis/api/types/KtType;ZILjava/lang/Object;)Ljava/util/List;"
        )
      ) {
        val end = curr.next

        //  ALOAD 11 // KaAnalysisSession
        //  CHECKCAST org/jetbrains/kotlin/analysis/api/components/KtTypeProviderMixIn
        //  ALOAD 13 // type
        //  ICONST_0
        //  ICONST_1
        //  ACONST_NULL
        //  INVOKESTATIC
        // org/jetbrains/kotlin/analysis/api/components/KtTypeProviderMixIn.getAllSuperTypes$default
        // (Lorg/jetbrains/kotlin/analysis/api/components/KtTypeProviderMixIn;Lorg/jetbrains/kotlin/analysis/api/types/KtType;ZILjava/lang/Object;)Ljava/util/List; (itf)
        //
        // Convert to
        //  ALOAD 11 // session
        //  ALOAD 13 // type
        //  ICONST_0  (shouldApproximate false is ICONST_0, true is ICONST_1
        //  INVOKEINTERFACE org/jetbrains/kotlin/analysis/api/KaSession.allSupertypes
        // (Lorg/jetbrains/kotlin/analysis/api/types/KaType;Z)Lkotlin/sequences/Sequence; (itf)

        val aconstNull = prev() ?: break // ACONST_NULL
        val iconst1 = prev() ?: break // ICONST_1
        val shouldApproximateLoad = prev() ?: break // ICONST_0
        val typeLoad = prev() ?: break
        expectAload()
        val checkCast = prev() ?: break
        val sessionLoad = checkCast.previous
        val beforeInvokeStatic = curr.previous

        instructions.remove(checkCast)
        instructions.remove(iconst1)
        instructions.remove(aconstNull)

        // TODO: Handle the other related type provider renames seen in (Kotlin repo commits)
        // 3bffc6b788147b75d16a7d61a20127e8a3d22c08 and afc4032c85b5d2f838d6170de4753163566d8b0b
        val newCall =
          MethodInsnNode(
            Opcodes.INVOKEINTERFACE,
            "org/jetbrains/kotlin/analysis/api/KaSession",
            "getAllSuperTypes",
            "(Lorg/jetbrains/kotlin/analysis/api/types/KaType;Z)Ljava/util/List;",
            true,
          )
        instructions.insert(shouldApproximateLoad, newCall)
        val next = newCall.next
        instructions.remove(next)

        curr = end
        modified = true
      } else {
        curr = curr.next ?: break
      }
    }
    return modified
  }

  private fun findClass(owner: String): Class<*>? {
    try {
      val className = Type.getObjectType(owner).className
      return Class.forName(className, false, LintJarApiMigration::class.java.classLoader)
    } catch (e: ClassNotFoundException) {
      return null
    }
  }

  /** Like [AbstractInsnNode.getPrevious] but skips label and line number nodes. */
  private fun AbstractInsnNode.prev(): AbstractInsnNode? {
    var curr = previous
    while (curr is LabelNode || curr is LineNumberNode) {
      curr = curr.previous
    }
    return curr
  }

  fun MethodNode.prettyPrint(): String {
    if (DEBUG) {
      /*
       * Using reflection for
       *     val textifier = org.objectweb.asm.util.Textifier()
       *     val tcv: MethodVisitor = org.objectweb.asm.util.TraceMethodVisitor(textifier) // Print to console
       *     accept(tcv)
       *     return textifier.getText().joinToString("")
       * such that this works in the IDE while debugging (we have asm-utils on the classpath)
       * without having to add a dependency from the CLI build.
       */
      val textifierClass = Class.forName("org.objectweb.asm.util.Textifier")
      val printerClass = Class.forName("org.objectweb.asm.util.Printer")
      val traceVisitorClass = Class.forName("org.objectweb.asm.util.TraceMethodVisitor")
      val textifier = textifierClass.getDeclaredConstructor().newInstance()
      val tcv = traceVisitorClass.getDeclaredConstructor(printerClass).newInstance(textifier)
      accept(tcv as MethodVisitor)
      @Suppress("UNCHECKED_CAST")
      val lines = printerClass.getDeclaredMethod("getText").invoke(textifier) as List<Any>
      return lines.joinToString("")
    } else {
      return toString()
    }
  }

  companion object {
    /** Useful for before/after diffing while debugging */
    private const val DEBUG = false

    /**
     * Is the given internal JVM name related to an API that this API migration tool cares about?
     */
    fun isRelevantType(s: String) = s.startsWith("org/jetbrains/kotlin/analysis/api/")

    /**
     * For a file that has been determined that it needs API migration (via the [LintJarVerifier],
     * this method returns a migrated jar, possibly cached.)
     */
    fun getMigratedJar(client: LintClient, jar: File, registryClass: String = ""): File {
      return LintJarApiMigration(client).getMigratedJar(jar, registryClass)
    }
  }
}
