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

import com.android.tools.lint.checks.infrastructure.TestFile.BinaryTestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.base64gzip
import com.android.tools.lint.checks.infrastructure.TestFiles.jar
import com.android.tools.lint.checks.infrastructure.TestLintClient
import com.android.tools.lint.checks.infrastructure.TestLintResult.Companion.getDiff
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.detector.api.Severity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.objectweb.asm.ClassReader
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceMethodVisitor

@Suppress("LintDocExample")
class LintJarApiMigrationTest {
  @get:Rule var temporaryFolder = TemporaryFolder()

  @Test
  fun testCache() {
    val dir = temporaryFolder.root
    val project =
      lint().files(jar("lint.jar", getNavigationLintUtilsClass())).createProjects(dir).first()
    val jarFile = File(project, "lint.jar")
    assertTrue(jarFile.isFile)

    val client = TestLintClient()
    var verifier = LintJarVerifier(client, jarFile)
    assertFalse(verifier.isCompatible())
    assertEquals(
      "In androidx.navigation.common.lint.LintUtilKt.isClassReference: org.jetbrains.kotlin.analysis.api.session.KtAnalysisSessionProvider#Companion",
      verifier.toString(),
    )
    assertTrue(verifier.needsApiMigration())

    val migratedJar = LintJarApiMigration.getMigratedJar(client, jarFile)

    verifier = LintJarVerifier(client, migratedJar)
    assertTrue(verifier.isCompatible())
    assertFalse(verifier.needsApiMigration())

    // Make sure cache returns the same file
    val migratedJar2 = LintJarApiMigration.getMigratedJar(client, jarFile)
    assertEquals(migratedJar.path, migratedJar2.path)

    // Make sure we didn't recreate it; change the file behind the scenes and
    // make sure it's still the same file
    migratedJar.writeText("test")
    val migratedJar3 = LintJarApiMigration.getMigratedJar(client, jarFile)
    assertEquals("test", migratedJar3.readText())
  }

  @Test
  fun testMigrateKtTypeProviderMixIn() {
    val file =
      base64gzip(
        "runtime-android-1.7.0-beta07-lint.jar-androidx/lifecycle/lint/NonNullableMutableLiveDataDetector\$createUastHandler\$1.visitCallExpression.class",
        """
        H4sIAAAAAAAA/+1YXWwU1xX+7szujne8tpfFEMYEamABs9her38WsEnAAZNs
        /ANhidPwEzJej83g9ay9M+vYtGkIaUOg4Uep1Jb+pS1pIBFRI1VCUKmpG6lU
        6kP70odWkfLQt0p5bl5a3HNnZrHXXsqSmrSVItlzv7n3nPOde865P7O/v/3L
        DwA8gosMXaoxmM3og5PRVGZ0LGNq0WzOsPRRLZrWDSvam7PUgbS2K5NOaylL
        zxhuR9JSLW23ZlFnJtttSWAMwePqhBpNq8ZwdO/AcRqRIDLUlWSiccRiWNIz
        krGINtqrWeqgaqkdDMLohEi+Mv4o4w8wsBEOBBqc1DlqIjQYY9g/fXKFPH1S
        FoKVsrBCsKHbBMupqVkepIdnBWtiEaFJaPYFxRqhyfOEUFMd9BLyFfZ+eNUn
        ccvNDOLQuME4dVtPJjscPa5ZA1lVN8yo67BqqOkpUzej6pge7bY63dekZpo0
        YZqGb8Q6MDWmMTSVYMAiSZPMcA3SXaqbC2LIEHDz1MDzxBDJx+74xGiUerQs
        WYwmM7lsioI8kBvumrQ0w/XGO6GmcxoT6pK9nfvkUhMkd9sMciRZm0d75M21
        sdqSDSxKrRFlc+3RWSmTW3Ynn5rtjc6R6LaOHj1a8C5HeuTYulh9rK25PSbH
        trRuWddc39Iea2uZ+xKf+7Jtzku8ee5L29yXrbPxseP+XwoSn19LfF2MT6me
        JtWyxcZxG2+18TaOW5s5jjfbuMXGbTZutfFWwpEumeFY3Weq/Put9k0HC/aR
        pJXVjWEJqxhWObqNc1LcOBuFMnyJYXkRkV51rAxrGGrvVSES1tEyTeumtXeI
        YX3doZ7521nHJqcrZ+npaA8JdgSwHhtkhLGRIVyMfH6yyrCp+ExcSdvbzQzV
        nKiRExXMsYGhYnbEFo4yhGYdTdC653YkxFwjjrezRiS0MEi62TU6Zk3Rxla3
        6WAAbYiXoxVbGFo+Q5IlbGNovL9ES6BtqLIwnBIeYSjT+RSohimddXMDnnD7
        Keg7sLMczejMT71gXMIumuAx1ezTJq0AuvjUdmMPg8egDopK3urczAbwBBJc
        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
        /FQjQwpfP7bCyn8BqjNN0bsbAAA=""",
      )
    checkBytecodeMigration(
      file,
      "isMutableCollection",
      """
      @@ -112 +112
      -  INVOKESTATIC androidx/compose/runtime/lint/MutableCollectionMutableStateDetectorKt.fqn (Lorg/jetbrains/kotlin/analysis/api/KtAnalysisSession;Lorg/jetbrains/kotlin/analysis/api/types/KtType;)Ljava/lang/String;
      +  INVOKESTATIC androidx/compose/runtime/lint/MutableCollectionMutableStateDetectorKt.fqn (Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/analysis/api/types/KaType;)Ljava/lang/String;
      @@ -175 +175
      -  INVOKESTATIC androidx/compose/runtime/lint/MutableCollectionMutableStateDetectorKt.fqn (Lorg/jetbrains/kotlin/analysis/api/KtAnalysisSession;Lorg/jetbrains/kotlin/analysis/api/types/KtType;)Ljava/lang/String;
      +  INVOKESTATIC androidx/compose/runtime/lint/MutableCollectionMutableStateDetectorKt.fqn (Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/analysis/api/types/KaType;)Ljava/lang/String;
      @@ -238 +238
      -  INVOKESTATIC androidx/compose/runtime/lint/MutableCollectionMutableStateDetectorKt.fqn (Lorg/jetbrains/kotlin/analysis/api/KtAnalysisSession;Lorg/jetbrains/kotlin/analysis/api/types/KtType;)Ljava/lang/String;
      +  INVOKESTATIC androidx/compose/runtime/lint/MutableCollectionMutableStateDetectorKt.fqn (Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/analysis/api/types/KaType;)Ljava/lang/String;
      @@ -259 +259
      -  CHECKCAST org/jetbrains/kotlin/analysis/api/components/KtTypeProviderMixIn
      @@ -262 +261
      -  ICONST_1
      -  ACONST_NULL
      -  INVOKESTATIC org/jetbrains/kotlin/analysis/api/components/KtTypeProviderMixIn.getAllSuperTypes＄default (Lorg/jetbrains/kotlin/analysis/api/components/KtTypeProviderMixIn;Lorg/jetbrains/kotlin/analysis/api/types/KtType;ZILjava/lang/Object;)Ljava/util/List; (itf)
      +  INVOKEINTERFACE org/jetbrains/kotlin/analysis/api/KaSession.allSupertypes (Lorg/jetbrains/kotlin/analysis/api/types/KaType;Z)Lkotlin/sequences/Sequence; (itf)
      @@ -301 +298
      -  CHECKCAST org/jetbrains/kotlin/analysis/api/types/KtType
      +  CHECKCAST org/jetbrains/kotlin/analysis/api/types/KaType
      @@ -351 +348
      -  INVOKESTATIC androidx/compose/runtime/lint/MutableCollectionMutableStateDetectorKt.fqn (Lorg/jetbrains/kotlin/analysis/api/KtAnalysisSession;Lorg/jetbrains/kotlin/analysis/api/types/KtType;)Ljava/lang/String;
      +  INVOKESTATIC androidx/compose/runtime/lint/MutableCollectionMutableStateDetectorKt.fqn (Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/analysis/api/types/KaType;)Ljava/lang/String;
      @@ -417 +414
      -  CHECKCAST org/jetbrains/kotlin/analysis/api/types/KtType
      +  CHECKCAST org/jetbrains/kotlin/analysis/api/types/KaType
      @@ -467 +464
      -  INVOKESTATIC androidx/compose/runtime/lint/MutableCollectionMutableStateDetectorKt.fqn (Lorg/jetbrains/kotlin/analysis/api/KtAnalysisSession;Lorg/jetbrains/kotlin/analysis/api/types/KtType;)Ljava/lang/String;
      +  INVOKESTATIC androidx/compose/runtime/lint/MutableCollectionMutableStateDetectorKt.fqn (Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/analysis/api/types/KaType;)Ljava/lang/String;
      @@ -533 +530
      -  CHECKCAST org/jetbrains/kotlin/analysis/api/types/KtType
      +  CHECKCAST org/jetbrains/kotlin/analysis/api/types/KaType
      @@ -583 +580
      -  INVOKESTATIC androidx/compose/runtime/lint/MutableCollectionMutableStateDetectorKt.fqn (Lorg/jetbrains/kotlin/analysis/api/KtAnalysisSession;Lorg/jetbrains/kotlin/analysis/api/types/KtType;)Ljava/lang/String;
      +  INVOKESTATIC androidx/compose/runtime/lint/MutableCollectionMutableStateDetectorKt.fqn (Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/analysis/api/types/KaType;)Ljava/lang/String;
      """,
      true,
    )
  }

  @Test
  fun verifyNavigationClassReferenceUtility() {
    // Checks the rewriting of the androidx/navigation/common/lint/LintUtilKt.class isClassReference
    // method body
    val file = getNavigationLintUtilsClass()
    checkBytecodeMigration(
      file,
      "isClassReference",
      """
      @Lorg/jetbrains/annotations/NotNull;([]) // invisible
        // annotable parameter count: 1 (invisible)
        @Lorg/jetbrains/annotations/NotNull;([]) // invisible, parameter 0
       L0
        ALOAD 0
        INVOKESTATIC com/android/tools/lint/detector/api/UastLintUtilsKt.isClassReference (Lorg/jetbrains/uast/UExpression;)Lkotlin/Pair;
        ARETURN
       L1
        LOCALVARIABLE ＄this＄isClassReference Lorg/jetbrains/uast/UExpression; L0 L1 0
        MAXSTACK = 1
        MAXLOCALS = 1
      """,
      false,
    )
  }

  private fun getNavigationLintUtilsClass(): BinaryTestFile {
    val file =
      base64gzip(
        "navigation-common-2.8.0-beta04-lint.jar-androidx/navigation/common/lint/LintUtilKt.class",
        """
          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""",
      )
    return file
  }

  @Test
  fun verifyAnalyzeRewriting() {
    // Checks the rewriting of the androidx/navigation/common/lint/LintUtilKt.class isClassReference
    // method body
    val file =
      base64gzip(
        "ui-android-1.7.0-alpha02-lint.jar-androidx/compose/ui/lint/SuspiciousModifierThenDetector.visitMethodCall.class",
        """
        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
        633oDf8DzkPXZxcdAAA=""",
      )
    checkBytecodeMigration(
      file,
      "visitCallExpression",
      """
      @@ -85 +85
      -  GETSTATIC org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.Companion : Lorg/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider＄Companion;
      +  GETSTATIC org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.Companion : Lorg/jetbrains/kotlin/analysis/api/session/KaSessionProvider＄Companion;
      @@ -92 +92
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider＄Companion.getInstance (Lcom/intellij/openapi/project/Project;)Lorg/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider;
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider＄Companion.getInstance (Lcom/intellij/openapi/project/Project;)Lorg/jetbrains/kotlin/analysis/api/session/KaSessionProvider;
      @@ -107 +107
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getAnalysisSession (Lorg/jetbrains/kotlin/psi/KtElement;)Lorg/jetbrains/kotlin/analysis/api/KtAnalysisSession;
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.getAnalysisSession (Lorg/jetbrains/kotlin/psi/KtElement;)Lorg/jetbrains/kotlin/analysis/api/KaSession;
      @@ -115 +115
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getNoWriteActionInAnalyseCallChecker ()Lorg/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker.beforeEnteringAnalysisContext ()V
      - L24
      -  LINENUMBER 170 L24
      -  ALOAD 9
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getTokenFactory ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory;
      @@ -122 +116
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getToken ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory.beforeEnteringAnalysisContext (Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;)V
      +  ALOAD 4
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.beforeEnteringAnalysis (Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/psi/KtElement;)V
      @@ -139 +134
      -  INVOKESTATIC androidx/compose/ui/lint/SuspiciousModifierThenDetectorKt.access＄getImplicitReceiverValue (Lorg/jetbrains/kotlin/analysis/api/KtAnalysisSession;Lorg/jetbrains/kotlin/psi/KtExpression;)Lorg/jetbrains/kotlin/analysis/api/calls/KtImplicitReceiverValue;
      +  INVOKESTATIC androidx/compose/ui/lint/SuspiciousModifierThenDetectorKt.access＄getImplicitReceiverValue (Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/psi/KtExpression;)Lorg/jetbrains/kotlin/analysis/api/resolution/KaImplicitReceiverValue;
      @@ -141 +136
      -  IFNULL L28
      -  INVOKESTATIC androidx/compose/ui/lint/SuspiciousModifierThenDetectorKt.access＄getImplicitReceiverPsi (Lorg/jetbrains/kotlin/analysis/api/calls/KtImplicitReceiverValue;)Lcom/intellij/psi/PsiElement;
      -  GOTO L29
      - L28
      - FRAME FULL [androidx/compose/ui/lint/SuspiciousModifierThenDetector＄visitMethodCall＄1 org/jetbrains/uast/UCallExpression I I org/jetbrains/kotlin/psi/KtCallExpression T I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider org/jetbrains/kotlin/analysis/api/KtAnalysisSession I org/jetbrains/kotlin/analysis/api/KtAnalysisSession I] [org/jetbrains/kotlin/analysis/api/calls/KtImplicitReceiverValue]
      +  IFNULL L27
      +  INVOKESTATIC androidx/compose/ui/lint/SuspiciousModifierThenDetectorKt.access＄getImplicitReceiverPsi (Lorg/jetbrains/kotlin/analysis/api/resolution/KaImplicitReceiverValue;)Lcom/intellij/psi/PsiElement;
      +  GOTO L28
      + L27
      + FRAME FULL [androidx/compose/ui/lint/SuspiciousModifierThenDetector＄visitMethodCall＄1 org/jetbrains/uast/UCallExpression I I org/jetbrains/kotlin/psi/KtCallExpression T I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider org/jetbrains/kotlin/analysis/api/KaSession I org/jetbrains/kotlin/analysis/api/KaSession I] [org/jetbrains/kotlin/analysis/api/resolution/KaImplicitReceiverValue]
      @@ -155 +150
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getTokenFactory ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory;
      @@ -157 +151
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getToken ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory.afterLeavingAnalysisContext (Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;)V
      - L30
      -  LINENUMBER 175 L30
      -  ALOAD 9
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getNoWriteActionInAnalyseCallChecker ()Lorg/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker.afterLeavingAnalysisContext ()V
      - L31
      -  LINENUMBER 176 L31
      -  GOTO L32
      +  ALOAD 4
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.afterLeavingAnalysis (Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/psi/KtElement;)V
      + L29
      +  LINENUMBER 175 L29
      +  LINENUMBER 176 L29
      +  GOTO L30
      @@ -173 +163
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getTokenFactory ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory;
      @@ -175 +164
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getToken ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory.afterLeavingAnalysisContext (Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;)V
      - L33
      -  LINENUMBER 175 L33
      -  ALOAD 9
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getNoWriteActionInAnalyseCallChecker ()Lorg/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker.afterLeavingAnalysisContext ()V
      +  ALOAD 4
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.afterLeavingAnalysis (Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/psi/KtElement;)V
      + L31
      +  LINENUMBER 175 L31
      @@ -208 +194
      -  IFEQ L39
      - L40
      -  LINENUMBER 88 L40
      +  IFEQ L37
      + L38
      +  LINENUMBER 88 L38
      """,
      true,
    )
  }

  @Test
  fun verifyAnalyzeRewriting2() {
    // This also tests replacing methods from classes to interfaces (INVOKEVIRTUAL to INVOKESTATIC)
    val file =
      base64gzip(
        "ui-android-1.7.0-beta07-lint.jar-androidx/compose/ui/lint/ModifierDeclarationDetectorKt\$ensureReceiverIsReferenced\$1.visitCallExpression.class",
        """
        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
        50SHpuQ/En3G6/cjAAA=""",
      )
    checkBytecodeMigration(
      file,
      "visitCallExpression",
      """
      @@ -53 +53
      -  GETSTATIC org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.Companion : Lorg/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider＄Companion;
      +  GETSTATIC org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.Companion : Lorg/jetbrains/kotlin/analysis/api/session/KaSessionProvider＄Companion;
      @@ -60 +60
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider＄Companion.getInstance (Lcom/intellij/openapi/project/Project;)Lorg/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider;
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider＄Companion.getInstance (Lcom/intellij/openapi/project/Project;)Lorg/jetbrains/kotlin/analysis/api/session/KaSessionProvider;
      @@ -75 +75
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getAnalysisSession (Lorg/jetbrains/kotlin/psi/KtElement;)Lorg/jetbrains/kotlin/analysis/api/KtAnalysisSession;
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.getAnalysisSession (Lorg/jetbrains/kotlin/psi/KtElement;)Lorg/jetbrains/kotlin/analysis/api/KaSession;
      @@ -83 +83
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getNoWriteActionInAnalyseCallChecker ()Lorg/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker.beforeEnteringAnalysisContext ()V
      - L21
      -  LINENUMBER 378 L21
      -  ALOAD 7
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getTokenFactory ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory;
      @@ -90 +84
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getToken ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory.beforeEnteringAnalysisContext (Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;)V
      +  ALOAD 2
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.beforeEnteringAnalysis (Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/psi/KtElement;)V
      @@ -107 +102
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.resolveCall (Lorg/jetbrains/kotlin/psi/KtElement;)Lorg/jetbrains/kotlin/analysis/api/calls/KtCallInfo;
      +  INVOKEINTERFACE org/jetbrains/kotlin/analysis/api/KaSession.resolveCall (Lorg/jetbrains/kotlin/psi/KtElement;)Lorg/jetbrains/kotlin/analysis/api/resolution/KaCallInfo; (itf)
      @@ -109 +104
      -  IFNULL L25
      +  IFNULL L24
      @@ -117 +112
      -  INVOKESTATIC org/jetbrains/kotlin/analysis/api/calls/KtCallKt.getCalls (Lorg/jetbrains/kotlin/analysis/api/calls/KtCallInfo;)Ljava/util/List;
      +  INVOKESTATIC org/jetbrains/kotlin/analysis/api/calls/KaCallKt.getCalls (Lorg/jetbrains/kotlin/analysis/api/resolution/KaCallInfo;)Ljava/util/List;
      @@ -140 +135
      -  IFEQ L33
      +  IFEQ L32
      @@ -147 +142
      -  CHECKCAST org/jetbrains/kotlin/analysis/api/calls/KtCall
      +  CHECKCAST org/jetbrains/kotlin/analysis/api/resolution/KaCall
      @@ -155 +150
      -  INSTANCEOF org/jetbrains/kotlin/analysis/api/calls/KtCall
      +  INSTANCEOF org/jetbrains/kotlin/analysis/api/resolution/KaCall
      + L36
      +  LINENUMBER 385 L36
      +  IFEQ L31
      @@ -157 +155
      -  LINENUMBER 385 L37
      -  IFEQ L32
      - L38
      -  LINENUMBER 386 L38
      +  LINENUMBER 386 L37
      @@ -162 +157
      -  IFEQ L39
      +  IFEQ L38
      @@ -164 +159
      -  GOTO L40
      - L39
      -  LINENUMBER 387 L39
      - FRAME APPEND [java/lang/Object org/jetbrains/kotlin/analysis/api/calls/KtCall I]
      +  GOTO L39
      + L38
      +  LINENUMBER 387 L38
      + FRAME APPEND [java/lang/Object org/jetbrains/kotlin/analysis/api/resolution/KaCall I]
      @@ -174 +169
      - L42
      -  GOTO L32
      - L33
      -  LINENUMBER 391 L33
      + L41
      +  GOTO L31
      + L32
      +  LINENUMBER 391 L32
      @@ -180 +175
      -  IFNE L43
      +  IFNE L42
      @@ -182 +177
      -  GOTO L40
      - L43
      -  LINENUMBER 392 L43
      +  GOTO L39
      + L42
      +  LINENUMBER 392 L42
      @@ -189 +184
      -  CHECKCAST org/jetbrains/kotlin/analysis/api/calls/KtCall
      - L44
      -  LINENUMBER 381 L44
      +  CHECKCAST org/jetbrains/kotlin/analysis/api/resolution/KaCall
      + L43
      +  LINENUMBER 381 L43
      @@ -193 +188
      - L45
      -  GOTO L46
      - L25
      -  LINENUMBER 242 L25
      - FRAME FULL [androidx/compose/ui/lint/ModifierDeclarationDetectorKt＄ensureReceiverIsReferenced＄1 org/jetbrains/uast/UCallExpression org/jetbrains/kotlin/psi/KtCallExpression kotlin/jvm/internal/Ref＄BooleanRef I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider org/jetbrains/kotlin/analysis/api/KtAnalysisSession I org/jetbrains/kotlin/analysis/api/KtAnalysisSession I] [org/jetbrains/kotlin/analysis/api/calls/KtCallInfo]
      + L44
      +  GOTO L45
      + L24
      +  LINENUMBER 242 L24
      + FRAME FULL [androidx/compose/ui/lint/ModifierDeclarationDetectorKt＄ensureReceiverIsReferenced＄1 org/jetbrains/uast/UCallExpression org/jetbrains/kotlin/psi/KtCallExpression kotlin/jvm/internal/Ref＄BooleanRef I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider org/jetbrains/kotlin/analysis/api/KaSession I org/jetbrains/kotlin/analysis/api/KaSession I] [org/jetbrains/kotlin/analysis/api/resolution/KaCallInfo]
      @@ -206 +201
      -  INSTANCEOF org/jetbrains/kotlin/analysis/api/calls/KtCallableMemberCall
      -  IFEQ L48
      +  INSTANCEOF org/jetbrains/kotlin/analysis/api/resolution/KaCallableMemberCall
      +  IFEQ L47
      @@ -209 +204
      -  CHECKCAST org/jetbrains/kotlin/analysis/api/calls/KtCallableMemberCall
      -  GOTO L49
      - L48
      - FRAME FULL [androidx/compose/ui/lint/ModifierDeclarationDetectorKt＄ensureReceiverIsReferenced＄1 org/jetbrains/uast/UCallExpression org/jetbrains/kotlin/psi/KtCallExpression kotlin/jvm/internal/Ref＄BooleanRef I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider org/jetbrains/kotlin/analysis/api/KtAnalysisSession I org/jetbrains/kotlin/analysis/api/KtAnalysisSession I T T T T T T T T T T org/jetbrains/kotlin/analysis/api/calls/KtCall] []
      +  CHECKCAST org/jetbrains/kotlin/analysis/api/resolution/KaCallableMemberCall
      +  GOTO L48
      + L47
      + FRAME FULL [androidx/compose/ui/lint/ModifierDeclarationDetectorKt＄ensureReceiverIsReferenced＄1 org/jetbrains/uast/UCallExpression org/jetbrains/kotlin/psi/KtCallExpression kotlin/jvm/internal/Ref＄BooleanRef I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider org/jetbrains/kotlin/analysis/api/KaSession I org/jetbrains/kotlin/analysis/api/KaSession I T T T T T T T T T T org/jetbrains/kotlin/analysis/api/resolution/KaCall] []
      @@ -217 +212
      -  IFNULL L50
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/calls/KtCallableMemberCall.getPartiallyAppliedSymbol ()Lorg/jetbrains/kotlin/analysis/api/calls/KtPartiallyAppliedSymbol;
      -  GOTO L51
      - L50
      - FRAME SAME1 org/jetbrains/kotlin/analysis/api/calls/KtCallableMemberCall
      +  IFNULL L49
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/resolution/KaCallableMemberCall.getPartiallyAppliedSymbol ()Lorg/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedSymbol;
      +  GOTO L50
      + L49
      + FRAME SAME1 org/jetbrains/kotlin/analysis/api/resolution/KaCallableMemberCall
      @@ -231 +226
      -  IFNULL L53
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/calls/KtPartiallyAppliedSymbol.getExtensionReceiver ()Lorg/jetbrains/kotlin/analysis/api/calls/KtReceiverValue;
      +  IFNULL L52
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedSymbol.getExtensionReceiver ()Lorg/jetbrains/kotlin/analysis/api/resolution/KaReceiverValue;
      @@ -234 +229
      -  IFNONNULL L54
      - L53
      - FRAME FULL [androidx/compose/ui/lint/ModifierDeclarationDetectorKt＄ensureReceiverIsReferenced＄1 org/jetbrains/uast/UCallExpression org/jetbrains/kotlin/psi/KtCallExpression kotlin/jvm/internal/Ref＄BooleanRef I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider org/jetbrains/kotlin/analysis/api/KtAnalysisSession I org/jetbrains/kotlin/analysis/api/KtAnalysisSession I T T T T T T T T T T org/jetbrains/kotlin/analysis/api/calls/KtCall org/jetbrains/kotlin/analysis/api/calls/KtPartiallyAppliedSymbol] [java/lang/Object]
      +  IFNONNULL L53
      + L52
      + FRAME FULL [androidx/compose/ui/lint/ModifierDeclarationDetectorKt＄ensureReceiverIsReferenced＄1 org/jetbrains/uast/UCallExpression org/jetbrains/kotlin/psi/KtCallExpression kotlin/jvm/internal/Ref＄BooleanRef I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider org/jetbrains/kotlin/analysis/api/KaSession I org/jetbrains/kotlin/analysis/api/KaSession I T T T T T T T T T T org/jetbrains/kotlin/analysis/api/resolution/KaCall org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedSymbol] [java/lang/Object]
      @@ -240 +235
      -  IFNULL L55
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/calls/KtPartiallyAppliedSymbol.getDispatchReceiver ()Lorg/jetbrains/kotlin/analysis/api/calls/KtReceiverValue;
      -  GOTO L54
      - L55
      - FRAME SAME1 org/jetbrains/kotlin/analysis/api/calls/KtPartiallyAppliedSymbol
      +  IFNULL L54
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedSymbol.getDispatchReceiver ()Lorg/jetbrains/kotlin/analysis/api/resolution/KaReceiverValue;
      +  GOTO L53
      + L54
      + FRAME SAME1 org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedSymbol
      @@ -254 +249
      -  IFNULL L57
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/calls/KtReceiverValue.getType ()Lorg/jetbrains/kotlin/analysis/api/types/KtType;
      +  IFNULL L56
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/resolution/KaReceiverValue.getType ()Lorg/jetbrains/kotlin/analysis/api/types/KaType;
      @@ -257 +252
      -  IFNULL L57
      +  IFNULL L56
      @@ -260 +255
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getExpandedClassSymbol (Lorg/jetbrains/kotlin/analysis/api/types/KtType;)Lorg/jetbrains/kotlin/analysis/api/symbols/KtClassOrObjectSymbol;
      +  INVOKEINTERFACE org/jetbrains/kotlin/analysis/api/KaSession.getExpandedClassSymbol (Lorg/jetbrains/kotlin/analysis/api/types/KaType;)Lorg/jetbrains/kotlin/analysis/api/symbols/KaClassSymbol; (itf)
      @@ -262 +257
      -  IFNULL L57
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/symbols/KtClassOrObjectSymbol.getClassIdIfNonLocal ()Lorg/jetbrains/kotlin/name/ClassId;
      -  GOTO L58
      - L57
      - FRAME FULL [androidx/compose/ui/lint/ModifierDeclarationDetectorKt＄ensureReceiverIsReferenced＄1 org/jetbrains/uast/UCallExpression org/jetbrains/kotlin/psi/KtCallExpression kotlin/jvm/internal/Ref＄BooleanRef I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider org/jetbrains/kotlin/analysis/api/KtAnalysisSession I org/jetbrains/kotlin/analysis/api/KtAnalysisSession I org/jetbrains/kotlin/analysis/api/calls/KtReceiverValue T T T T T T T T T org/jetbrains/kotlin/analysis/api/calls/KtCall org/jetbrains/kotlin/analysis/api/calls/KtPartiallyAppliedSymbol] [java/lang/Object]
      +  IFNULL L56
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/symbols/KaClassSymbol.getClassIdIfNonLocal ()Lorg/jetbrains/kotlin/name/ClassId;
      +  GOTO L57
      + L56
      + FRAME FULL [androidx/compose/ui/lint/ModifierDeclarationDetectorKt＄ensureReceiverIsReferenced＄1 org/jetbrains/uast/UCallExpression org/jetbrains/kotlin/psi/KtCallExpression kotlin/jvm/internal/Ref＄BooleanRef I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider org/jetbrains/kotlin/analysis/api/KaSession I org/jetbrains/kotlin/analysis/api/KaSession I org/jetbrains/kotlin/analysis/api/resolution/KaReceiverValue T T T T T T T T T org/jetbrains/kotlin/analysis/api/resolution/KaCall org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedSymbol] [java/lang/Object]
      @@ -276 +271
      -  IFNULL L60
      +  IFNULL L59
      @@ -278 +273
      -  GOTO L61
      - L60
      - FRAME FULL [androidx/compose/ui/lint/ModifierDeclarationDetectorKt＄ensureReceiverIsReferenced＄1 org/jetbrains/uast/UCallExpression org/jetbrains/kotlin/psi/KtCallExpression kotlin/jvm/internal/Ref＄BooleanRef I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider org/jetbrains/kotlin/analysis/api/KtAnalysisSession I org/jetbrains/kotlin/analysis/api/KtAnalysisSession I org/jetbrains/kotlin/analysis/api/calls/KtReceiverValue org/jetbrains/kotlin/name/ClassId T T T T T T T T org/jetbrains/kotlin/analysis/api/calls/KtCall org/jetbrains/kotlin/analysis/api/calls/KtPartiallyAppliedSymbol] [org/jetbrains/kotlin/name/ClassId]
      +  GOTO L60
      + L59
      + FRAME FULL [androidx/compose/ui/lint/ModifierDeclarationDetectorKt＄ensureReceiverIsReferenced＄1 org/jetbrains/uast/UCallExpression org/jetbrains/kotlin/psi/KtCallExpression kotlin/jvm/internal/Ref＄BooleanRef I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider org/jetbrains/kotlin/analysis/api/KaSession I org/jetbrains/kotlin/analysis/api/KaSession I org/jetbrains/kotlin/analysis/api/resolution/KaReceiverValue org/jetbrains/kotlin/name/ClassId T T T T T T T T org/jetbrains/kotlin/analysis/api/resolution/KaCall org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedSymbol] [org/jetbrains/kotlin/name/ClassId]
      @@ -303 +298
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getTokenFactory ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory;
      @@ -305 +299
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getToken ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory.afterLeavingAnalysisContext (Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;)V
      - L65
      -  LINENUMBER 394 L65
      -  ALOAD 7
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getNoWriteActionInAnalyseCallChecker ()Lorg/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker.afterLeavingAnalysisContext ()V
      +  ALOAD 2
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.afterLeavingAnalysis (Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/psi/KtElement;)V
      + L64
      +  LINENUMBER 394 L64
      @@ -326 +317
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getTokenFactory ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory;
      @@ -328 +318
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getToken ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory.afterLeavingAnalysisContext (Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;)V
      - L68
      -  LINENUMBER 394 L68
      -  ALOAD 7
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getNoWriteActionInAnalyseCallChecker ()Lorg/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker;
      ...
      """,
      true,
    )
  }

  @Test
  fun verifyAnalyzeRewriting3() {
    val file =
      base64gzip(
        "runtime-android-1.7.0-beta07-lint.jar-androidx/compose/runtime/lint/AutoboxingStateCreationDetector.getSuggestedReplacementName.class",
        """
        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
        ZTk9K8SSwf8C+5Hyq1MjAAA=""",
      )
    checkBytecodeMigration(
      file,
      "getSuggestedReplacementName",
      """
      @@ -44 +44
      -  GETSTATIC org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.Companion : Lorg/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider＄Companion;
      +  GETSTATIC org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.Companion : Lorg/jetbrains/kotlin/analysis/api/session/KaSessionProvider＄Companion;
      @@ -50 +50
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider＄Companion.getInstance (Lcom/intellij/openapi/project/Project;)Lorg/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider;
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider＄Companion.getInstance (Lcom/intellij/openapi/project/Project;)Lorg/jetbrains/kotlin/analysis/api/session/KaSessionProvider;
      @@ -64 +64
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getAnalysisSession (Lorg/jetbrains/kotlin/psi/KtElement;)Lorg/jetbrains/kotlin/analysis/api/KtAnalysisSession;
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.getAnalysisSession (Lorg/jetbrains/kotlin/psi/KtElement;)Lorg/jetbrains/kotlin/analysis/api/KaSession;
      @@ -72 +72
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getNoWriteActionInAnalyseCallChecker ()Lorg/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker.beforeEnteringAnalysisContext ()V
      - L20
      -  LINENUMBER 212 L20
      -  ALOAD 6
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getTokenFactory ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory;
      @@ -79 +73
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getToken ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory.beforeEnteringAnalysisContext (Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;)V
      +  ALOAD 2
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.beforeEnteringAnalysis (Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/psi/KtElement;)V
      @@ -95 +90
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.resolveCall (Lorg/jetbrains/kotlin/psi/KtElement;)Lorg/jetbrains/kotlin/analysis/api/calls/KtCallInfo;
      +  INVOKEINTERFACE org/jetbrains/kotlin/analysis/api/KaSession.resolveCall (Lorg/jetbrains/kotlin/psi/KtElement;)Lorg/jetbrains/kotlin/analysis/api/resolution/KaCallInfo; (itf)
      @@ -97 +92
      -  IFNULL L24
      -  INVOKESTATIC org/jetbrains/kotlin/analysis/api/calls/KtCallKt.singleFunctionCallOrNull (Lorg/jetbrains/kotlin/analysis/api/calls/KtCallInfo;)Lorg/jetbrains/kotlin/analysis/api/calls/KtFunctionCall;
      +  IFNULL L23
      +  INVOKESTATIC org/jetbrains/kotlin/analysis/api/calls/KaCallKt.singleFunctionCallOrNull (Lorg/jetbrains/kotlin/analysis/api/resolution/KaCallInfo;)Lorg/jetbrains/kotlin/analysis/api/resolution/KaFunctionCall;
      @@ -110 +105
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getTokenFactory ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory;
      @@ -112 +106
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getToken ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory.afterLeavingAnalysisContext (Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;)V
      - L26
      -  LINENUMBER 217 L26
      -  ALOAD 6
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getNoWriteActionInAnalyseCallChecker ()Lorg/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker.afterLeavingAnalysisContext ()V
      +  ALOAD 2
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.afterLeavingAnalysis (Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/psi/KtElement;)V
      + L25
      +  LINENUMBER 217 L25
      @@ -127 +118
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/calls/KtFunctionCall.getTypeArgumentsMapping ()Ljava/util/Map;
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/resolution/KaFunctionCall.getTypeArgumentsMapping ()Ljava/util/Map;
      @@ -135 +126
      -  IFNULL L29
      +  IFNULL L28
      @@ -137 +128
      -  CHECKCAST org/jetbrains/kotlin/analysis/api/types/KtType
      +  CHECKCAST org/jetbrains/kotlin/analysis/api/types/KaType
      @@ -149 +140
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getTokenFactory ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory;
      @@ -151 +141
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getToken ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory.afterLeavingAnalysisContext (Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;)V
      - L31
      -  LINENUMBER 217 L31
      -  ALOAD 6
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getNoWriteActionInAnalyseCallChecker ()Lorg/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker.afterLeavingAnalysisContext ()V
      +  ALOAD 2
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.afterLeavingAnalysis (Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/psi/KtElement;)V
      + L30
      +  LINENUMBER 217 L30
      @@ -171 +158
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.isMarkedNullable (Lorg/jetbrains/kotlin/analysis/api/types/KtType;)Z
      -  IFEQ L34
      +  INVOKEINTERFACE org/jetbrains/kotlin/analysis/api/KaSession.isMarkedNullable (Lorg/jetbrains/kotlin/analysis/api/types/KaType;)Z (itf)
      +  IFEQ L33
      @@ -174 +161
      -  GOTO L35
      - L34
      -  LINENUMBER 152 L34
      - FRAME APPEND [org/jetbrains/kotlin/analysis/api/types/KtType]
      +  GOTO L34
      + L33
      +  LINENUMBER 152 L33
      + FRAME APPEND [org/jetbrains/kotlin/analysis/api/types/KaType]
      @@ -180 +167
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getExpandedClassSymbol (Lorg/jetbrains/kotlin/analysis/api/types/KtType;)Lorg/jetbrains/kotlin/analysis/api/symbols/KtClassOrObjectSymbol;
      +  INVOKEINTERFACE org/jetbrains/kotlin/analysis/api/KaSession.getExpandedClassSymbol (Lorg/jetbrains/kotlin/analysis/api/types/KaType;)Lorg/jetbrains/kotlin/analysis/api/symbols/KaClassSymbol; (itf)
      @@ -182 +169
      -  IFNULL L36
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/symbols/KtClassOrObjectSymbol.getClassIdIfNonLocal ()Lorg/jetbrains/kotlin/name/ClassId;
      +  IFNULL L35
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/symbols/KaClassSymbol.getClassIdIfNonLocal ()Lorg/jetbrains/kotlin/name/ClassId;
      @@ -185 +172
      -  IFNULL L36
      +  IFNULL L35
      @@ -187 +174
      -  GOTO L37
      - L36
      +  GOTO L36
      + L35
      @@ -210 +197
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getTokenFactory ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory;
      @@ -212 +198
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getToken ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory.afterLeavingAnalysisContext (Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;)V
      - L40
      -  LINENUMBER 217 L40
      -  ALOAD 6
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getNoWriteActionInAnalyseCallChecker ()Lorg/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker.afterLeavingAnalysisContext ()V
      +  ALOAD 2
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.afterLeavingAnalysis (Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/psi/KtElement;)V
      + L39
      +  LINENUMBER 217 L39
      @@ -227 +210
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getTokenFactory ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory;
      @@ -229 +211
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getToken ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory.afterLeavingAnalysisContext (Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;)V
      - L41
      -  LINENUMBER 217 L41
      -  ALOAD 6
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getNoWriteActionInAnalyseCallChecker ()Lorg/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker.afterLeavingAnalysisContext ()V
      +  ALOAD 2
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.afterLeavingAnalysis (Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/psi/KtElement;)V
      + L40
      +  LINENUMBER 217 L40
      """,
      true,
    )
  }

  @Test
  fun verifyAnalyzeRewriting4() {
    val file =
      base64gzip(
        "lifecycle-livedata-core-2.9.0-alpha01-lint.jar-androidx/lifecycle/lint/NonNullableMutableLiveDataDetector\$createUastHandler\$1.visitCallExpression.class",
        """
        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
        hswqnaIGzqDwy+UKHavkSpcsVPcqJclsPJrM3LJCxaWArJz+0mVCIpHVY7TS
        PiOtmirFZ/qQ5BavrNVljVTbkTIpl6Als05GnXaq2j8zk1qpY9yVekb/bLE0
        WWd7txpNbA/2qXiTnvK3BROHOEzqDeJX1Ddwn4xNc0qTjYL12d31ZQr+C2WT
        DodsZnVNOQRXZIp3GQoEuUaaXHK1bKF97IhpXWiGI2GTh9u7pj1ajdfcjO82
        s7vXdMtWuVapbBtjQGW4Mli5Or0QWp06bVXao6pjz1QSmqdpCQhyw6bVXjvD
        A5iyUvLbJfEtEqWTdSiYRbm6K8fXZ8lyLYXn6o71R7tSPbmBUbTSumBOx0pN
        z8mUs+lTaWzG1skTSjqlNAUoUnUXc+ak9Gbaps9OceMKcZ7KbVslvxquvsgv
        Dxk+51Zy++cdMe2zf56S3WAkuebdn8NYN8VHs1PDRZ+UqLz4uNS7cQbKm3Sq
        ILkq27wZVHGwv6YuEuzt7AqqVq2g/qIu/Cix5Vk2GCUuUgwP2t5oeVHzJbnV
        pM8Hx0+lsXCl8Rwn0O0UaHZ/wugIm8bodY4FNPE3ARnvfQRR2xMjhnkpvTCS
        RFRGqgyOZmjlnY4wt8yS6fM0C61pP27QGVNf2ZjtgmkVQPmFkrj6eMNjoxnv
        VwtK/x3EyukUNum3FAXhxLVG1IiHQ8wNRyb9qCJD0s34jYSUll4gP9Pe6eoU
        5ChnEOx4F1/eMhmSjHKarJ+TFHaYwdAR5t3dtoaLWa0Zbf2quk6OlLbGuL9T
        9koOVu7qj6pLyUBUuYRaZqrQbRz72MbVBKLUm7U+g93i5mgoElPh2T6n0KD2
        Z66tYUV0TqYflzCqJ3nttTmNY7Bwohxps6jmyS0HgKOkRJ0tAb55oLN/roSl
        aGCdcAfgKiDUQsB57crSDWfQ6Ny48iyuFfhzPbnnEBiEy5M7jMBp5Mj+8ygY
        xvbTWL4vCZPnyTuHdgWTN4z2cTC5+55rWz2Mnf4cpy/3eez15JwF093+0hvP
        4OAQDH8enD7Nk+fP9+R5coYQ9hc4fbonfwi9Q0io922egiHcMYS74Cnwu5w+
        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
        8amwSFT+N3KwCvXvNQAA""",
      )
    checkBytecodeMigration(
      file,
      "visitCallExpression",
      """
      @@ -77 +77
      -  GETSTATIC org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.Companion : Lorg/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider＄Companion;
      +  GETSTATIC org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.Companion : Lorg/jetbrains/kotlin/analysis/api/session/KaSessionProvider＄Companion;
      @@ -83 +83
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider＄Companion.getInstance (Lcom/intellij/openapi/project/Project;)Lorg/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider;
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider＄Companion.getInstance (Lcom/intellij/openapi/project/Project;)Lorg/jetbrains/kotlin/analysis/api/session/KaSessionProvider;
      @@ -97 +97
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getAnalysisSession (Lorg/jetbrains/kotlin/psi/KtElement;)Lorg/jetbrains/kotlin/analysis/api/KtAnalysisSession;
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.getAnalysisSession (Lorg/jetbrains/kotlin/psi/KtElement;)Lorg/jetbrains/kotlin/analysis/api/KaSession;
      @@ -105 +105
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getNoWriteActionInAnalyseCallChecker ()Lorg/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker.beforeEnteringAnalysisContext ()V
      - L23
      -  LINENUMBER 341 L23
      -  ALOAD 8
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getTokenFactory ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory;
      @@ -112 +106
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getToken ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory.beforeEnteringAnalysisContext (Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;)V
      +  ALOAD 4
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.beforeEnteringAnalysis (Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/psi/KtElement;)V
      @@ -129 +124
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.resolveCall (Lorg/jetbrains/kotlin/psi/KtElement;)Lorg/jetbrains/kotlin/analysis/api/calls/KtCallInfo;
      +  INVOKEINTERFACE org/jetbrains/kotlin/analysis/api/KaSession.resolveCall (Lorg/jetbrains/kotlin/psi/KtElement;)Lorg/jetbrains/kotlin/analysis/api/resolution/KaCallInfo; (itf)
      @@ -131 +126
      -  IFNULL L27
      +  IFNULL L26
      @@ -139 +134
      -  INVOKESTATIC org/jetbrains/kotlin/analysis/api/calls/KtCallKt.getCalls (Lorg/jetbrains/kotlin/analysis/api/calls/KtCallInfo;)Ljava/util/List;
      +  INVOKESTATIC org/jetbrains/kotlin/analysis/api/calls/KaCallKt.getCalls (Lorg/jetbrains/kotlin/analysis/api/resolution/KaCallInfo;)Ljava/util/List;
      @@ -162 +157
      -  IFEQ L35
      +  IFEQ L34
      @@ -169 +164
      -  CHECKCAST org/jetbrains/kotlin/analysis/api/calls/KtCall
      +  CHECKCAST org/jetbrains/kotlin/analysis/api/resolution/KaCall
      @@ -177 +172
      -  INSTANCEOF org/jetbrains/kotlin/analysis/api/calls/KtCall
      +  INSTANCEOF org/jetbrains/kotlin/analysis/api/resolution/KaCall
      + L38
      +  LINENUMBER 348 L38
      +  IFEQ L33
      @@ -179 +177
      -  LINENUMBER 348 L39
      -  IFEQ L34
      - L40
      -  LINENUMBER 349 L40
      +  LINENUMBER 349 L39
      @@ -184 +179
      -  IFEQ L41
      +  IFEQ L40
      @@ -186 +181
      -  GOTO L42
      - L41
      -  LINENUMBER 350 L41
      - FRAME APPEND [java/lang/Object org/jetbrains/kotlin/analysis/api/calls/KtCall I]
      +  GOTO L41
      + L40
      +  LINENUMBER 350 L40
      + FRAME APPEND [java/lang/Object org/jetbrains/kotlin/analysis/api/resolution/KaCall I]
      @@ -196 +191
      - L44
      -  GOTO L34
      - L35
      -  LINENUMBER 354 L35
      + L43
      +  GOTO L33
      + L34
      +  LINENUMBER 354 L34
      @@ -202 +197
      -  IFNE L45
      +  IFNE L44
      @@ -204 +199
      -  GOTO L42
      - L45
      -  LINENUMBER 355 L45
      +  GOTO L41
      + L44
      +  LINENUMBER 355 L44
      @@ -211 +206
      -  CHECKCAST org/jetbrains/kotlin/analysis/api/calls/KtCall
      - L46
      -  LINENUMBER 344 L46
      +  CHECKCAST org/jetbrains/kotlin/analysis/api/resolution/KaCall
      + L45
      +  LINENUMBER 344 L45
      @@ -215 +210
      - L47
      -  GOTO L48
      - L27
      -  LINENUMBER 137 L27
      - FRAME FULL [androidx/lifecycle/lint/NonNullableMutableLiveDataDetector＄createUastHandler＄1 org/jetbrains/uast/UCallExpression I com/intellij/psi/NavigatablePsiElement org/jetbrains/kotlin/psi/KtElement I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider org/jetbrains/kotlin/analysis/api/KtAnalysisSession I org/jetbrains/kotlin/analysis/api/KtAnalysisSession I] [org/jetbrains/kotlin/analysis/api/calls/KtCallInfo]
      + L46
      +  GOTO L47
      + L26
      +  LINENUMBER 137 L26
      + FRAME FULL [androidx/lifecycle/lint/NonNullableMutableLiveDataDetector＄createUastHandler＄1 org/jetbrains/uast/UCallExpression I com/intellij/psi/NavigatablePsiElement org/jetbrains/kotlin/psi/KtElement I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider org/jetbrains/kotlin/analysis/api/KaSession I org/jetbrains/kotlin/analysis/api/KaSession I] [org/jetbrains/kotlin/analysis/api/resolution/KaCallInfo]
      @@ -228 +223
      -  INSTANCEOF org/jetbrains/kotlin/analysis/api/calls/KtCallableMemberCall
      -  IFEQ L50
      +  INSTANCEOF org/jetbrains/kotlin/analysis/api/resolution/KaCallableMemberCall
      +  IFEQ L49
      @@ -231 +226
      -  CHECKCAST org/jetbrains/kotlin/analysis/api/calls/KtCallableMemberCall
      -  GOTO L51
      - L50
      - FRAME FULL [androidx/lifecycle/lint/NonNullableMutableLiveDataDetector＄createUastHandler＄1 org/jetbrains/uast/UCallExpression I com/intellij/psi/NavigatablePsiElement org/jetbrains/kotlin/psi/KtElement I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider org/jetbrains/kotlin/analysis/api/KtAnalysisSession I org/jetbrains/kotlin/analysis/api/KtAnalysisSession I T T T T T T T T T T org/jetbrains/kotlin/analysis/api/calls/KtCall] []
      +  CHECKCAST org/jetbrains/kotlin/analysis/api/resolution/KaCallableMemberCall
      +  GOTO L50
      + L49
      + FRAME FULL [androidx/lifecycle/lint/NonNullableMutableLiveDataDetector＄createUastHandler＄1 org/jetbrains/uast/UCallExpression I com/intellij/psi/NavigatablePsiElement org/jetbrains/kotlin/psi/KtElement I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider org/jetbrains/kotlin/analysis/api/KaSession I org/jetbrains/kotlin/analysis/api/KaSession I T T T T T T T T T T org/jetbrains/kotlin/analysis/api/resolution/KaCall] []
      @@ -239 +234
      -  IFNULL L52
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/calls/KtCallableMemberCall.getPartiallyAppliedSymbol ()Lorg/jetbrains/kotlin/analysis/api/calls/KtPartiallyAppliedSymbol;
      -  GOTO L53
      - L52
      - FRAME SAME1 org/jetbrains/kotlin/analysis/api/calls/KtCallableMemberCall
      +  IFNULL L51
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/resolution/KaCallableMemberCall.getPartiallyAppliedSymbol ()Lorg/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedSymbol;
      +  GOTO L52
      + L51
      + FRAME SAME1 org/jetbrains/kotlin/analysis/api/resolution/KaCallableMemberCall
      @@ -253 +248
      -  IFNULL L55
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/calls/KtPartiallyAppliedSymbol.getExtensionReceiver ()Lorg/jetbrains/kotlin/analysis/api/calls/KtReceiverValue;
      +  IFNULL L54
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedSymbol.getExtensionReceiver ()Lorg/jetbrains/kotlin/analysis/api/resolution/KaReceiverValue;
      @@ -256 +251
      -  IFNONNULL L56
      - L55
      - FRAME FULL [androidx/lifecycle/lint/NonNullableMutableLiveDataDetector＄createUastHandler＄1 org/jetbrains/uast/UCallExpression I com/intellij/psi/NavigatablePsiElement org/jetbrains/kotlin/psi/KtElement I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider org/jetbrains/kotlin/analysis/api/KtAnalysisSession I org/jetbrains/kotlin/analysis/api/KtAnalysisSession I T T T T T T T T T T org/jetbrains/kotlin/analysis/api/calls/KtCall org/jetbrains/kotlin/analysis/api/calls/KtPartiallyAppliedSymbol] [java/lang/Object]
      +  IFNONNULL L55
      + L54
      + FRAME FULL [androidx/lifecycle/lint/NonNullableMutableLiveDataDetector＄createUastHandler＄1 org/jetbrains/uast/UCallExpression I com/intellij/psi/NavigatablePsiElement org/jetbrains/kotlin/psi/KtElement I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider org/jetbrains/kotlin/analysis/api/KaSession I org/jetbrains/kotlin/analysis/api/KaSession I T T T T T T T T T T org/jetbrains/kotlin/analysis/api/resolution/KaCall org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedSymbol] [java/lang/Object]
      @@ -262 +257
      -  IFNULL L57
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/calls/KtPartiallyAppliedSymbol.getDispatchReceiver ()Lorg/jetbrains/kotlin/analysis/api/calls/KtReceiverValue;
      -  GOTO L56
      - L57
      - FRAME SAME1 org/jetbrains/kotlin/analysis/api/calls/KtPartiallyAppliedSymbol
      +  IFNULL L56
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedSymbol.getDispatchReceiver ()Lorg/jetbrains/kotlin/analysis/api/resolution/KaReceiverValue;
      +  GOTO L55
      + L56
      + FRAME SAME1 org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedSymbol
      @@ -276 +271
      -  IFNULL L59
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/calls/KtReceiverValue.getType ()Lorg/jetbrains/kotlin/analysis/api/types/KtType;
      -  GOTO L60
      - L59
      - FRAME FULL [androidx/lifecycle/lint/NonNullableMutableLiveDataDetector＄createUastHandler＄1 org/jetbrains/uast/UCallExpression I com/intellij/psi/NavigatablePsiElement org/jetbrains/kotlin/psi/KtElement I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider org/jetbrains/kotlin/analysis/api/KtAnalysisSession I org/jetbrains/kotlin/analysis/api/KtAnalysisSession I org/jetbrains/kotlin/analysis/api/calls/KtReceiverValue T T T T T T T T T org/jetbrains/kotlin/analysis/api/calls/KtCall org/jetbrains/kotlin/analysis/api/calls/KtPartiallyAppliedSymbol] [org/jetbrains/kotlin/analysis/api/calls/KtReceiverValue]
      +  IFNULL L58
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/resolution/KaReceiverValue.getType ()Lorg/jetbrains/kotlin/analysis/api/types/KaType;
      +  GOTO L59
      + L58
      + FRAME FULL [androidx/lifecycle/lint/NonNullableMutableLiveDataDetector＄createUastHandler＄1 org/jetbrains/uast/UCallExpression I com/intellij/psi/NavigatablePsiElement org/jetbrains/kotlin/psi/KtElement I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider org/jetbrains/kotlin/analysis/api/KaSession I org/jetbrains/kotlin/analysis/api/KaSession I org/jetbrains/kotlin/analysis/api/resolution/KaReceiverValue T T T T T T T T T org/jetbrains/kotlin/analysis/api/resolution/KaCall org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedSymbol] [org/jetbrains/kotlin/analysis/api/resolution/KaReceiverValue]
      @@ -287 +282
      -  INSTANCEOF org/jetbrains/kotlin/analysis/api/types/KtNonErrorClassType
      -  IFEQ L61
      +  INSTANCEOF org/jetbrains/kotlin/analysis/api/types/KaClassType
      +  IFEQ L60
      @@ -290 +285
      -  CHECKCAST org/jetbrains/kotlin/analysis/api/types/KtNonErrorClassType
      -  GOTO L62
      - L61
      - FRAME FULL [androidx/lifecycle/lint/NonNullableMutableLiveDataDetector＄createUastHandler＄1 org/jetbrains/uast/UCallExpression I com/intellij/psi/NavigatablePsiElement org/jetbrains/kotlin/psi/KtElement I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider org/jetbrains/kotlin/analysis/api/KtAnalysisSession I org/jetbrains/kotlin/analysis/api/KtAnalysisSession I org/jetbrains/kotlin/analysis/api/calls/KtReceiverValue T org/jetbrains/kotlin/analysis/api/types/KtType T T T T T T T org/jetbrains/kotlin/analysis/api/calls/KtCall org/jetbrains/kotlin/analysis/api/calls/KtPartiallyAppliedSymbol] []
      +  CHECKCAST org/jetbrains/kotlin/analysis/api/types/KaClassType
      +  GOTO L61
      + L60
      + FRAME FULL [androidx/lifecycle/lint/NonNullableMutableLiveDataDetector＄createUastHandler＄1 org/jetbrains/uast/UCallExpression I com/intellij/psi/NavigatablePsiElement org/jetbrains/kotlin/psi/KtElement I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider org/jetbrains/kotlin/analysis/api/KaSession I org/jetbrains/kotlin/analysis/api/KaSession I org/jetbrains/kotlin/analysis/api/resolution/KaReceiverValue T org/jetbrains/kotlin/analysis/api/types/KaType T T T T T T T org/jetbrains/kotlin/analysis/api/resolution/KaCall org/jetbrains/kotlin/analysis/api/resolution/KaPartiallyAppliedSymbol] []
      @@ -302 +297
      -  IFNE L64
      +  IFNE L63
      @@ -304 +299
      -  IFNULL L64
      - L65
      -  LINENUMBER 142 L65
      +  IFNULL L63
      + L64
      +  LINENUMBER 142 L64
      @@ -308 +303
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/types/KtNonErrorClassType.getOwnTypeArguments ()Ljava/util/List;
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/types/KaClassType.getOwnTypeArguments ()Ljava/util/List;
      @@ -310 +305
      -  CHECKCAST org/jetbrains/kotlin/analysis/api/KtTypeProjection
      +  CHECKCAST org/jetbrains/kotlin/analysis/api/types/KaTypeProjection
      @@ -312 +307
      -  IFNULL L66
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtTypeProjection.getType ()Lorg/jetbrains/kotlin/analysis/api/types/KtType;
      -  GOTO L67
      - L66
      - FRAME SAME1 org/jetbrains/kotlin/analysis/api/KtTypeProjection
      +  IFNULL L65
      +  INVOKEINTERFACE org/jetbrains/kotlin/analysis/api/types/KaTypeProjection.getType ()Lorg/jetbrains/kotlin/analysis/api/types/KaType; (itf)
      +  GOTO L66
      + L65
      + FRAME SAME1 org/jetbrains/kotlin/analysis/api/types/KaTypeProjection
      @@ -325 +320
      -  INSTANCEOF org/jetbrains/kotlin/analysis/api/types/KtTypeParameterType
      -  IFEQ L69
      ...
      """,
      true,
    )
  }

  private fun String.escapeDollar(): String {
    return replace('$', '＄')
  }

  private fun checkBytecodeMigration(
    file: BinaryTestFile,
    methodName: String,
    expected: String,
    showDiff: Boolean,
    maxLines: Int = 200,
  ) {
    val bytes = file.binaryContents
    val before = prettyPrint(bytes, methodName).trimIndent()
    val client =
      object : TestLintClient() {
        override fun log(
          severity: Severity,
          exception: Throwable?,
          format: String?,
          vararg args: Any,
        ) = error("Didn't expect output")

        override fun log(exception: Throwable?, format: String?, vararg args: Any) =
          error("Didn't expect output")
      }
    val newBytes = LintJarApiMigration(client).migrateClass(bytes)
    val after = prettyPrint(newBytes, methodName).trimIndent()

    // Make sure we don't have any old references left
    if (after.contains("org/jetbrains/kotlin/analysis/api/session/KtAnalysisSession")) {
      fail("Found old APIs in $after")
    }

    val trimmedExpected = expected.trimIndent()
    val output =
      if (showDiff) {
        val diff = getDiff(before, after)
        // Drop irrelevant diffs: contains only label, line number and frame diffs
        val sb = StringBuilder()
        var offset = 0
        val chunks = mutableListOf<String>()
        while (true) {
          val index = diff.indexOf("@@ ", offset + 1)
          if (index == -1) {
            chunks.add(diff.substring(offset, diff.length))
            break
          }
          chunks.add(diff.substring(offset, index))
          offset = index
        }
        val relevant =
          chunks.filter { s ->
            val lines = s.lines()
            val irrelevant =
              lines.all {
                val content = it.substringAfter("+ ").substringAfter("- ").trimStart()
                it.isBlank() ||
                  it.startsWith("@@ ") ||
                  content.startsWith("L") && content[1].isDigit() ||
                  content.startsWith("LINENUMBER") ||
                  content.startsWith("LOCALVARIABLE ") ||
                  content.startsWith("MAXSTACK ") ||
                  content.startsWith("FRAME ")
              }
            !irrelevant
          }
        relevant.joinToString("").escapeDollar().trim()
      } else {
        after.escapeDollar().trim()
      }

    val truncated =
      if (maxLines > 0) {
        val lines = output.lines()
        if (lines.size > maxLines) lines.take(maxLines).joinToString("\n") + "\n..." else output
      } else {
        output
      }
    assertEquals(trimmedExpected.trim(), truncated.trim())
  }

  private fun prettyPrint(classBytes: ByteArray, methodName: String): String {
    val cr = ClassReader(classBytes)
    val cn = ClassNode(ASM9)
    cr.accept(cn, 0)
    val method = cn.methods.first { it.name == methodName }
    return method.prettyPrint()
  }

  fun MethodNode.prettyPrint(): String {
    val textifier = Textifier()
    val tcv: MethodVisitor = TraceMethodVisitor(textifier) // Print to console
    accept(tcv)
    return textifier.getText().joinToString("")
  }
}
