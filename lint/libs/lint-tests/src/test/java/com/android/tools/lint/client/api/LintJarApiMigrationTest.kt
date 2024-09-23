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
import com.android.testutils.TestUtils
import com.android.tools.lint.checks.infrastructure.BytecodeTestFile
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFile.BinaryTestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.base64gzip
import com.android.tools.lint.checks.infrastructure.TestFiles.bytecode
import com.android.tools.lint.checks.infrastructure.TestFiles.jar
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintClient
import com.android.tools.lint.checks.infrastructure.TestLintResult.Companion.getDiff
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.parseFirst
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.isClassReference
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import java.io.File
import kotlin.math.min
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.toUElement
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
  fun testMigrateAnalyze() {
    val file =
      bytecode(
        "code.jar",
        kotlin(
            """
            package test.pkg

            import org.jetbrains.kotlin.analysis.api.analyze
            import org.jetbrains.kotlin.psi.KtExpression

            fun testAnalyze(element: KtExpression): Boolean {
                analyze(element) {
                    return element.getKtType()?.canBeNull ?: false
                }
            }
            """
          )
          .indented(),
        // To compute this, use
        // lint().files(file, *getLintClassPath(),
        //  LibraryReferenceTestFile(File("/downloaded/kotlin-compiler-31.7.0-alpha03.jar")),
        // ).createProjects(tempFolder)
        0xcf74546,
        """
        META-INF/main.kotlin_module:
        H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijgEuLiKEktLtEryE4XYgsBsrxLlBi0
        GAAvgr4WLAAAAA==
        """,
        """
        test/pkg/TestKt.class:
        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
        /we5oQ7XfQ8AAA==
        """,
      )

    checkBytecodeMigration(
      file,
      "testAnalyze",
      """
      @@ -15 +15
      -  GETSTATIC org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.Companion : Lorg/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider＄Companion;
      +  GETSTATIC org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.Companion : Lorg/jetbrains/kotlin/analysis/api/session/KaSessionProvider＄Companion;
      @@ -22 +22
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider＄Companion.getInstance (Lcom/intellij/openapi/project/Project;)Lorg/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider;
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider＄Companion.getInstance (Lcom/intellij/openapi/project/Project;)Lorg/jetbrains/kotlin/analysis/api/session/KaSessionProvider;
      @@ -37 +37
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getAnalysisSession (Lorg/jetbrains/kotlin/psi/KtElement;)Lorg/jetbrains/kotlin/analysis/api/KtAnalysisSession;
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.getAnalysisSession (Lorg/jetbrains/kotlin/psi/KtElement;)Lorg/jetbrains/kotlin/analysis/api/KaSession;
      @@ -45 +45
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getNoWriteActionInAnalyseCallChecker ()Lorg/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker.beforeEnteringAnalysisContext ()V
      - L12
      -  LINENUMBER 15 L12
      -  ALOAD 4
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getTokenFactory ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory;
      @@ -52 +46
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getToken ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory.beforeEnteringAnalysisContext (Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;)V
      +  ALOAD 0
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.beforeEnteringAnalysis (Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/psi/KtElement;)V
      @@ -68 +63
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getKtType (Lorg/jetbrains/kotlin/psi/KtExpression;)Lorg/jetbrains/kotlin/analysis/api/types/KtType;
      +  INVOKEINTERFACE org/jetbrains/kotlin/analysis/api/KaSession.getKtType (Lorg/jetbrains/kotlin/psi/KtExpression;)Lorg/jetbrains/kotlin/analysis/api/types/KaType; (itf)
      @@ -70 +65
      -  IFNULL L16
      +  IFNULL L15
      @@ -73 +68
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getCanBeNull (Lorg/jetbrains/kotlin/analysis/api/types/KtType;)Z
      -  GOTO L17
      - L16
      - FRAME FULL [org/jetbrains/kotlin/psi/KtExpression I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider I org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider org/jetbrains/kotlin/analysis/api/KtAnalysisSession I org/jetbrains/kotlin/analysis/api/KtAnalysisSession I] [org/jetbrains/kotlin/analysis/api/types/KtType]
      +  INVOKEINTERFACE org/jetbrains/kotlin/analysis/api/KaSession.getCanBeNull (Lorg/jetbrains/kotlin/analysis/api/types/KaType;)Z (itf)
      +  GOTO L16
      + L15
      + FRAME FULL [org/jetbrains/kotlin/psi/KtExpression I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider org/jetbrains/kotlin/analysis/api/KaSession I org/jetbrains/kotlin/analysis/api/KaSession I] [org/jetbrains/kotlin/analysis/api/types/KaType]
      @@ -85 +80
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getTokenFactory ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory;
      @@ -87 +81
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getToken ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory.afterLeavingAnalysisContext (Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;)V
      - L18
      -  LINENUMBER 20 L18
      -  ALOAD 4
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getNoWriteActionInAnalyseCallChecker ()Lorg/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker.afterLeavingAnalysisContext ()V
      +  ALOAD 0
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.afterLeavingAnalysis (Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/psi/KtElement;)V
      + L17
      +  LINENUMBER 20 L17
      @@ -102 +93
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getTokenFactory ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory;
      @@ -104 +94
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getToken ()Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeTokenFactory.afterLeavingAnalysisContext (Lorg/jetbrains/kotlin/analysis/api/lifetime/KtLifetimeToken;)V
      - L19
      -  LINENUMBER 20 L19
      -  ALOAD 4
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getNoWriteActionInAnalyseCallChecker ()Lorg/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker;
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/lifetime/impl/NoWriteActionInAnalyseCallChecker.afterLeavingAnalysisContext ()V
      +  ALOAD 0
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.afterLeavingAnalysis (Lorg/jetbrains/kotlin/analysis/api/KaSession;Lorg/jetbrains/kotlin/psi/KtElement;)V
      + L18
      +  LINENUMBER 20 L18
      """,
      true,
      checkSource =
        kotlin(
          """
          fun self1(s: String) = s
          fun self2(s: String?) = s
          """
        ),
      checks = { ktFile, migratedClass ->
        // This method is invoked with ktFile containing the PSI representation of the above
        // `checkSource`source field, and migratedClass containing the bytecode migrated version
        // of the original source above.
        //
        // In this case, our migrated code is performing analysis API lookups to see if a Kotlin
        // expression is nullable. To make sure that the modified code is actually executing
        // correctly, we'll use reflection to call into this migrated code, passing in valid KT
        // elements, and checking that the results are correct. In this specific case, we'll
        // use the kotlin expression method bodies, where the first one is not nullable and the
        // second one is.
        fun canBeNull(ktExpression: KtExpression): Boolean {
          val method = migratedClass.declaredMethods[0]
          return method.invoke(null, ktExpression) as Boolean
        }
        val func1 = ktFile.declarations[0] as KtNamedFunction
        val func2 = ktFile.declarations[1] as KtNamedFunction
        val exp1 = func1.bodyExpression as KtExpression
        val exp2 = func2.bodyExpression as KtExpression

        assertEquals(false, canBeNull(exp1))
        assertEquals(true, canBeNull(exp2))
      },
    )
  }

  @Test
  fun testMigrateToInvokeInterface() {
    // resolveCall should be moved from INVOKESTATIC to INVOKEINTERFACE.
    // Also, isNothing was renamed to isNothingType.
    val file =
      bytecode(
        "code.jar",
        kotlin(
            """
            package test.pkg

            import org.jetbrains.kotlin.analysis.api.analyze
            import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
            import org.jetbrains.kotlin.analysis.api.calls.symbol
            import org.jetbrains.kotlin.psi.KtCallExpression

            fun returnsString(element: KtCallExpression): Boolean {
                analyze(element) {
                    return element.resolveCall()?.singleFunctionCallOrNull()?.symbol?.returnType?.isString ?: false
                }
            }
            """
          )
          .indented(),
        0x9d1d5f7f,
        """
        META-INF/main.kotlin_module:
        H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijgEuLiKEktLtEryE4XYgsBsrxLlBi0
        GAAvgr4WLAAAAA==
        """,
        """
        test/pkg/TestKt.class:
        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
        """,
      )

    checkBytecodeMigration(
      file,
      "returnsString",
      """
      ...
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.resolveCall (Lorg/jetbrains/kotlin/psi/KtElement;)Lorg/jetbrains/kotlin/analysis/api/calls/KtCallInfo;
      +  INVOKEINTERFACE org/jetbrains/kotlin/analysis/api/KaSession.resolveCall (Lorg/jetbrains/kotlin/psi/KtElement;)Lorg/jetbrains/kotlin/analysis/api/resolution/KaCallInfo; (itf)
      ...
      """,
      skipFirst = 22,
      maxLines = 2,
      showDiff = true,
      checkSource =
        kotlin(
          """
          fun stringMethod(): String = "hello"
          fun intMethod(): Int = 42
          fun call1() = stringMethod()
          fun call2() = intMethod()
          """
        ),
      checks = { ktFile, migratedClass ->
        fun isString(ktExpression: KtExpression): Boolean {
          val method = migratedClass.declaredMethods[0]
          return method.invoke(null, ktExpression) as Boolean
        }
        val call1 = (ktFile.declarations[2] as KtNamedFunction).bodyExpression as KtCallExpression
        val call2 = (ktFile.declarations[3] as KtNamedFunction).bodyExpression as KtCallExpression

        assertEquals(true, isString(call1))
        assertEquals(false, isString(call2))
      },
    )
  }

  @Test
  fun testSuspiciousModifierSnippet() {
    // Verify code which tripped up bytecode verifier in google3
    @Suppress("KotlinConstantConditions")
    val file =
      bytecode(
        "code.jar",
        kotlin(
            "src/test/pkg/test.kt",
            """
            package test.pkg
            import com.intellij.psi.PsiElement
            import org.jetbrains.kotlin.analysis.api.analyze
            import org.jetbrains.kotlin.analysis.api.calls.KtCall
            import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
            import org.jetbrains.kotlin.analysis.api.calls.KtCompoundAccessCall
            import org.jetbrains.kotlin.analysis.api.calls.KtImplicitReceiverValue
            import org.jetbrains.kotlin.analysis.api.calls.singleCallOrNull
            import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
            import org.jetbrains.kotlin.analysis.api.symbols.KtReceiverParameterSymbol
            import org.jetbrains.kotlin.psi.KtExpression

            fun getPsiForReceiver(ktCallExpression: KtExpression): PsiElement? =
                analyze(ktCallExpression) {
                    getImplicitReceiverValue(ktCallExpression)?.getImplicitReceiverPsi()
                }

            fun KtImplicitReceiverValue.getImplicitReceiverPsi(): PsiElement? {
                return when (val receiverParameterSymbol = this.symbol) {
                    // the owning lambda expression
                    is KtReceiverParameterSymbol -> receiverParameterSymbol.owningCallableSymbol.psi
                    // the class that we are in, calling a method
                    is KtClassOrObjectSymbol -> receiverParameterSymbol.psi
                    else -> null
                }
            }

            fun getImplicitReceiverValue(
                ktExpression: KtExpression
            ): KtImplicitReceiverValue? {
                analyze(ktExpression) {
                    val partiallyAppliedSymbol =
                        when (val call = ktExpression.resolveCall()?.singleCallOrNull<KtCall>()) {
                            is KtCompoundAccessCall -> call.compoundAccess.operationPartiallyAppliedSymbol
                            is KtCallableMemberCall<*, *> -> call.partiallyAppliedSymbol
                            else -> null
                        } ?: return null

                    return partiallyAppliedSymbol.extensionReceiver as? KtImplicitReceiverValue
                        ?: partiallyAppliedSymbol.dispatchReceiver as? KtImplicitReceiverValue
                }
            }
            """,
          )
          .indented(),
        0x4899033,
        """
        META-INF/main.kotlin_module:
        H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijgEuLiKEktLtEryE4XYgsBsrxLlBi0
        GAAvgr4WLAAAAA==""",
        """
        test/pkg/TestKt.class:
        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
        I2npUnQ179jn+cyXZn/jYT5W5HfHTYM8RCqiu5n1Xa4rNBL26zv1npH+6VzA
        +amj9tUvqb2ro3G3GpGhttkC1IqukmivRd1Q4imJjs/5xsmx6pKIy8nh89ui
        0WG2p9aULHjZSEzYee8sXMRb4jhMYhJn1x2cXlvS3RQKBHT7g4gphUSm+Weo
        7hiONqu7u3vWu1rRrnpKPZXeGp9Hrd5SWl1Z7fN6o73aWrXWU1rj89apdbWl
        NZWbfd56h1Jb71C8vtrNan0VezW+eq9aW1Pq9dVVc2xLaW2lx+Orq5kxl23l
        89lM4vGWegiislqtITSCYM+h1UXa+srNkbH6SLu50uv06r2VNWpNDXuE4bQ1
        BKJWNKvMRk2hXt6IsrsszT/YoQ3vkQlRYFm7EdR3jciTMELJaw9R2Xu1sCHf
        I8RVN44E5eeL1uCoYRokNc58lRYomzs6XXrPYlMdj28xpMSCeO7PPBwRtXfe
        MrzhJyFFhj2S4UIq0tj+nklgklSV1Psr8tRTyE3e+iSKKqZQfBqXCezPu+IU
        1kxiXQeSt1X6UirlyCQ2+FKT69JcKZNwT6JWtltcqZO4ZhI74Ur1Kcl16RWn
        cN3+F5BxCu0TSN0nfBmxbJ2RuTdM4HKfEnfEpTznyiCuk8TdwgvkGPtjvKOM
        2Tv4A39bkHIOh5Ch4BahIE8+ChqEfMDfw40Kes5xr8ocBjnYexYuh7kHf6Ss
        XcinRnKQjkpcgY1Yg03UmBtbUMV1PLgW1eRZhlvYHoaX98RajKCOaLwstGtZ
        +XpwD+fcy7n3IRd/okxC45xc3Iguym3jCntwExRy9mIvaWnk78c+vIUWkL39
        HE3lyiP2aDJW4ygOcDSJs+/CQdKEtBOlHmL7Zz5ppChs/8JH4b7ICG64Fdc7
        xuVFIIl/gKcir4/GrZjEQPuGMwiMI3fDFAKTGJ7E6AQKSBsbR9aG03ibwARS
        xMlpLWcj+X9YreDWZdujyiqy3ScfmSgkyBVUUzHVlG9vuojrZeJKvB3vsOF6
        8E7ctjjcZmrYhptyBzWxgj55e0Xe7ZfQF12Kw3oHHTLoy0iuU130yw9N4U5f
        ZnJdlvBlJ9fluDJP42O89i9zLTuN+wTG4ZG9T5KU68qdwgnf8uS6PNfyMzgx
        fu6NwpxxbmECxa5cX3ZKXc7EuZcLcx6wSamubHLD8fp8V/4ZPDQOtlN46DQ+
        KzCJL05ghaQ/No4cSX9sEo9Lpe9/Eas5pyh+PBSd9BW4CibxlCP08Dgy5eTD
        kZmX7ZODX48z6CuML7Dw5MKhR4O04KzopzGKRKfd9gtDthG3eA2552jk3DjB
        p+AVPnfySeFzN3AWG9lXcEL8BxnpDEvGbdqCcfsKGU7IuC2IxzD9j0xAHn3y
        ZDefc3TBwvPwv0o+gDMOr5I/C6YG28cfxDpiXE0XrWdC2Myw3EJf9vG3gZrZ
        yoRwNYN7DZPBNiaDazCM7TDRyEDdQdfZxvk78DCa8Cjd8HG6+FO4Ds8wMp/n
        2Lc47yWspR5L8S+U4d98/y9j5iyuF5loE0VYw6dEFGOdqEW56GDbybFDHOtn
        O8pkwlgTeyljnLF4iqkhj6knHafxNJYTw1p8A99k6hli8nmGo1nE14lnScsm
        ynY8hykmuwEmnDNElMld+ImqiwfBzejDt/EdJpmXmApfoLx87vNxvCiTGBHV
        4rv4HgqI4AlyOOns5el09up0Ont1Op29Pp3OfjOdzv7ppDPuZmVMz+azezbf
        NC3N7tmS7Z4tmRqqj8yVPWeu7Dlzo7Q0u+fMlb2Zuc6OZM/ZB9MOji+eWlPw
        V1kvcyN/Y/NmiflmiXnJSkz83a5oBH7AoPnhQSS34ket+HErfoKftuJn+Hkr
        foFfHoQw8Su8dhAZJppN3GbiuIlDJlJNvG5KYquJX9sOnENZb/D5rT3nd/8H
        gGho19MhAAA=""",
      )

    checkBytecodeMigration(
      file,
      "getImplicitReceiverPsi",
      """
      @@ -11 +11
      -   INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/calls/KtImplicitReceiverValue.getSymbol ()Lorg/jetbrains/kotlin/analysis/api/symbols/KtSymbol;
      +   INVOKEINTERFACE org/jetbrains/kotlin/analysis/api/resolution/KaImplicitReceiverValue.getSymbol ()Lorg/jetbrains/kotlin/analysis/api/symbols/KaSymbol; (itf)
      @@ -16 +16
      -   INSTANCEOF org/jetbrains/kotlin/analysis/api/symbols/KtReceiverParameterSymbol
      +   INSTANCEOF org/jetbrains/kotlin/analysis/api/symbols/KaReceiverParameterSymbol
      @@ -19 +19
      -   CHECKCAST org/jetbrains/kotlin/analysis/api/symbols/KtReceiverParameterSymbol
      -   INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/symbols/KtReceiverParameterSymbol.getOwningCallableSymbol ()Lorg/jetbrains/kotlin/analysis/api/symbols/KtCallableSymbol;
      -   INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/symbols/KtCallableSymbol.getPsi ()Lcom/intellij/psi/PsiElement;
      +   CHECKCAST org/jetbrains/kotlin/analysis/api/symbols/KaReceiverParameterSymbol
      +   INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/symbols/KaReceiverParameterSymbol.getOwningCallableSymbol ()Lorg/jetbrains/kotlin/analysis/api/symbols/KaCallableSymbol;
      +   INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/symbols/KaCallableSymbol.getPsi ()Lcom/intellij/psi/PsiElement;
      @@ -27 +27
      -   INSTANCEOF org/jetbrains/kotlin/analysis/api/symbols/KtClassOrObjectSymbol
      +   INSTANCEOF org/jetbrains/kotlin/analysis/api/symbols/KaClassSymbol
      @@ -30 +30
      -   INVOKEINTERFACE org/jetbrains/kotlin/analysis/api/symbols/KtSymbol.getPsi ()Lcom/intellij/psi/PsiElement; (itf)
      +   INVOKEINTERFACE org/jetbrains/kotlin/analysis/api/symbols/KaSymbol.getPsi ()Lcom/intellij/psi/PsiElement; (itf)
      """,
      showDiff = true,
      checkSource =
        kotlin(
          """
          class Foo {
             // The implicit receiver of method2 is "this" is Foo.this
             fun method1(): Int = method2()
             fun method2(): Int = 42
          }
          """
        ),
      checks = { ktFile, migratedClass ->
        fun getImplicitReceiverPsi(ktExpression: KtExpression): PsiElement? {
          val method = migratedClass.declaredMethods.find { it.name == "getPsiForReceiver" }!!
          return method.invoke(null, ktExpression) as PsiElement?
        }
        val call =
          ((ktFile.declarations[0] as KtClass).declarations[0] as KtNamedFunction).bodyExpression
            as KtCallExpression
        assertEquals("Foo", (getImplicitReceiverPsi(call) as KtClass).name)
      },
    )
  }

  @Test
  fun testRenameIsNothing() {
    // isNothing was renamed to isNothingType.

    val file =
      bytecode(
        "code.jar",
        kotlin(
            """
            package test.pkg

            import org.jetbrains.kotlin.analysis.api.analyze
            import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
            import org.jetbrains.kotlin.analysis.api.calls.symbol
            import org.jetbrains.kotlin.psi.KtCallExpression

            fun isNothingType(element: KtCallExpression): Boolean {
                analyze(element) {
                    return element.resolveCall()?.singleFunctionCallOrNull()?.symbol?.returnType?.isNothing ?: false
                }
            }
            """
          )
          .indented(),
        0x58432e00,
        """
        META-INF/main.kotlin_module:
        H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijgEuLiKEktLtEryE4XYgsBsrxLlBi0
        GAAvgr4WLAAAAA==
        """,
        """
        test/pkg/TestKt.class:
        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
        """,
      )

    checkBytecodeMigration(
      file,
      "isNothingType",
      """
      @@ -15 +15
      -  GETSTATIC org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.Companion : Lorg/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider＄Companion;
      +  GETSTATIC org/jetbrains/kotlin/analysis/api/session/KaSessionProvider.Companion : Lorg/jetbrains/kotlin/analysis/api/session/KaSessionProvider＄Companion;
      @@ -22 +22
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider＄Companion.getInstance (Lcom/intellij/openapi/project/Project;)Lorg/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider;
      +  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KaSessionProvider＄Companion.getInstance (Lcom/intellij/openapi/project/Project;)Lorg/jetbrains/kotlin/analysis/api/session/KaSessionProvider;
      @@ -37 +37
      -  INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/session/KtAnalysisSessionProvider.getAnalysisSession (Lorg/jetbrains/kotlin/psi/KtElement;)Lorg/jetbrains/kotlin/analysis/api/KtAnalysisSession;
      ...
      """,
      maxLines = 8,
      showDiff = true,
      checkSource =
        kotlin(
          """
          fun method1(): String = "hello"
          fun method2(): Nothing = TODO()
          fun call1() = method1()
          fun call2() = method2()
          """
        ),
      checks = { ktFile, migratedClass ->
        fun isNothing(ktExpression: KtExpression): Boolean {
          val method = migratedClass.declaredMethods[0]
          return method.invoke(null, ktExpression) as Boolean
        }
        val call1 = (ktFile.declarations[2] as KtNamedFunction).bodyExpression as KtCallExpression
        val call2 = (ktFile.declarations[3] as KtNamedFunction).bodyExpression as KtCallExpression

        assertEquals(false, isNothing(call1))
        assertEquals(true, isNothing(call2))
      },
    )
  }

  @Test
  fun testSuperTypes() {
    // Check getAllSuperTypes renaming and type providers
    val file =
      bytecode(
        "code.jar",
        kotlin(
            """
            package test.pkg

            import org.jetbrains.kotlin.analysis.api.analyze
            import org.jetbrains.kotlin.psi.KtExpression

            fun getExpressionTypes(expression: KtExpression): Any? {
                analyze(expression) {
                    val ktType = expression.getKtType()
                    return ktType?.getAllSuperTypes()
                }
            }
            """
          )
          .indented(),
        0x36d63860,
        """
        META-INF/main.kotlin_module:
        H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijgEuLiKEktLtEryE4XYgsBsrxLlBi0
        GAAvgr4WLAAAAA==
        """,
        """
        test/pkg/TestKt.class:
        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
        suG3KXIibq20+RE9yxWmlf8AmOlRJfwQAAA=
        """,
      )

    checkBytecodeMigration(
      file,
      "getExpressionTypes",
      """
        ...
        @@ -69 +64
        -   INVOKEVIRTUAL org/jetbrains/kotlin/analysis/api/KtAnalysisSession.getKtType (Lorg/jetbrains/kotlin/psi/KtExpression;)Lorg/jetbrains/kotlin/analysis/api/types/KtType;
        +   INVOKEINTERFACE org/jetbrains/kotlin/analysis/api/KaSession.getKtType (Lorg/jetbrains/kotlin/psi/KtExpression;)Lorg/jetbrains/kotlin/analysis/api/types/KaType; (itf)
        @@ -74 +69
        -   IFNULL L17
        +   IFNULL L16
        @@ -76 +71
        -   CHECKCAST org/jetbrains/kotlin/analysis/api/components/KtTypeProviderMixIn
        @@ -79 +73
        -   ICONST_1
        +   INVOKEINTERFACE org/jetbrains/kotlin/analysis/api/KaSession.getAllSuperTypes (Lorg/jetbrains/kotlin/analysis/api/types/KaType;Z)Ljava/util/List; (itf)
        +   GOTO L17
        +  L16
        +  FRAME FULL [org/jetbrains/kotlin/psi/KtExpression I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider I org/jetbrains/kotlin/analysis/api/session/KaSessionProvider org/jetbrains/kotlin/analysis/api/KaSession I org/jetbrains/kotlin/analysis/api/KaSession I org/jetbrains/kotlin/analysis/api/types/KaType] []
        @@ -81 +78
        -   INVOKESTATIC org/jetbrains/kotlin/analysis/api/components/KtTypeProviderMixIn.getAllSuperTypes＄default (Lorg/jetbrains/kotlin/analysis/api/components/KtTypeProviderMixIn;Lorg/jetbrains/kotlin/analysis/api/types/KtType;ZILjava/lang/Object;)Ljava/util/List; (itf)
        -   GOTO L18
        ...
      """,
      skipFirst = 21,
      maxLines = 17,
      showDiff = true,
      checkSource =
        kotlin(
          """
          fun method1(): String = "hello"
          fun method2(): List<String> = emptyList()
          fun call1() = method1()
          fun call2() = method2()
          """
        ),
      checks = { ktFile, migratedClass ->
        fun getFirstSuperTypeName(ktExpression: KtExpression): Any? {
          val method = migratedClass.declaredMethods[0]
          return (method.invoke(null, ktExpression))
        }
        val call1 = (ktFile.declarations[2] as KtNamedFunction).bodyExpression as KtCallExpression
        val call2 = (ktFile.declarations[3] as KtNamedFunction).bodyExpression as KtCallExpression

        assertEquals(
          "[kotlin/Comparable<kotlin/String>, kotlin/CharSequence, java/io/Serializable, kotlin/Any]",
          getFirstSuperTypeName(call1).toString(),
        )
        assertEquals(
          "[kotlin/collections/Collection<kotlin/String>, kotlin/collections/Iterable<kotlin/String>, kotlin/Any]",
          getFirstSuperTypeName(call2).toString(),
        )
      },
    )
  }

  @Test
  fun testMigrateLintUtil1() {
    val file =
      bytecode(
        "code.jar",
        kotlin(
            "src/androidx/navigation/lint/common/LintUtil.kt",
            // Old version of this utility: no parameters
            """
            package androidx.navigation.lint.common

            import org.jetbrains.uast.UExpression

            fun UExpression.isClassReference(
            ): Pair<Boolean, String?> {
                TODO() // implementation doesn't matter
            }
            """,
          )
          .indented(),
        0x1f81deac,
        """
        META-INF/main.kotlin_module:
        H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijg0uWST8xLKcrPTKnQy0ssy0xPLMnM
        z9PLycwr0UvOz83NzxPi8gFyQksyc7xLlBi0GAC8YwKBRwAAAA==
        """,
        """
        androidx/navigation/lint/common/LintUtilKt.class:
        H4sIAAAAAAAA/5VSUU8TQRD+9lpaKCDlFCxFEaVKqcoV45MlJAqYXCxIQJoY
        nrbXpSy92yN7ew2P/CuNJsqzP8o4V4poITEmu7PfzM7MN7M7P35++QbgJaoM
        Fa5aOpStU0fxrmxzI0Pl+FIZxwuDgHCd8L6R/juTBWPIH/Mud3yu2s775rHw
        yJoiq4zWfR5Fu+JQaKE8weCU66FuO8fCNDWXKnJiHhlnf/P0RIsoIpbaUr0T
        GqJydrjUNYbG/0Ws1q9KeROGvuCq9odpz2ip2rU1SrwwkJcrFZpep5GzHZrt
        2PfJK7NqjmS0NowRhrk+z3E3cKh/oRX3HVclKSPpRVmMMkx5R8Lr9ON3uOaB
        IEeGxXJ98I1uqGupMYZx3MphDBMMxT4fZXODE18Egkhbm1qHOovJpDappFlj
        2ChfT+XWb6p2Qxzy2Dfr1KTRsWdCvcV1R+ge823cycHGFMN0Kem6dP3/5v/1
        GQyTl8RbwvAWN5xsVtBN0WyxRIwkAgyskwCLLk9lgmjsrNYKw8r5mZ07P8tZ
        BStn5Udp30pwsZA/Pyvm7bRtVS07Y6cLrJqqsopVTSeBLxjGByaHOEYv53S5
        YxjS62GLepggo9iOg6bQH3jTJ4tdDz3uN7iWid43juzJtuIm1oRnd2NlZCBc
        1ZWRpOvXV8PCUBq8/f3vf7nl9sJYe+KtTLLP9GMa1/JhBRbSuHikGQwhQ1qZ
        tBph6gqTFTv3GfmvsD+yNPuE6e/JO2KJZIYcMsiiQnjswhl3UaDzac8ni2d9
        r2E6n9POsr5iYbknF+HQ+arPXTxAysWsi3su7mPOxQPMu3iIRwdgERZQOsBQ
        lKzHEZ70VuEXRwdRL0gEAAA=
        """,
      )

    checkBytecodeMigration(
      file,
      "isClassReference",
      """
        @Lorg/jetbrains/annotations/NotNull;([]) // invisible
          // annotable parameter count: 1 (invisible)
          @Lorg/jetbrains/annotations/NotNull;([]) // invisible, parameter 0
         L0
          ALOAD 0
          ICONST_1
          ICONST_1
          ICONST_1
          INVOKESTATIC com/android/tools/lint/detector/api/UastLintUtilsKt.isClassReference (Lorg/jetbrains/uast/UExpression;ZZZ)Lkotlin/Pair;
          ARETURN
         L1
          LOCALVARIABLE ＄this＄isClassReference Lorg/jetbrains/uast/UExpression; L0 L1 0
          MAXSTACK = 4
          MAXLOCALS = 1
        """,
      showDiff = false,
      checkSource =
        kotlin(
          """
          class MyClass {
            companion object {}
          }
          class MyInterface
          object MyObject {
            val prop = 1
          }
          fun test1() = MyObject
          fun test2() = MyObject.prop
          fun test3() = MyObject::class
          """
        ),
      checks = { ktFile, migratedClass ->
        fun UExpression.isClassReferenceReflection(): Pair<Boolean, String?> {
          val method = migratedClass.declaredMethods[0]
          @Suppress("UNCHECKED_CAST") return method.invoke(null, this) as Pair<Boolean, String?>
        }

        val functions = ktFile.declarations.filterIsInstance<KtNamedFunction>()
        val expression1 = functions[0].bodyExpression.toUElement() as UExpression
        val expression2 = functions[1].bodyExpression.toUElement() as UExpression
        val expression3 = functions[2].bodyExpression.toUElement() as UExpression

        // The real implementation in LintUtils:
        assertEquals(false to "MyObject", expression1.isClassReference())
        assertEquals(false to null, expression2.isClassReference())
        assertEquals(false to null, expression3.isClassReference())

        // The migrated, reflection wrapped version
        assertEquals(false to "MyObject", expression1.isClassReferenceReflection())
        assertEquals(false to null, expression2.isClassReferenceReflection())
        assertEquals(false to null, expression3.isClassReferenceReflection())
      },
    )
  }

  @Test
  fun testMigrateLintUtil2() {
    val file =
      bytecode(
        "code.jar",
        kotlin(
            "src/androidx/navigation/lint/common/LintUtil.kt",
            // New version of this class: added multiple parameters and defaults
            """
            package androidx.navigation.lint.common

            import org.jetbrains.uast.UExpression

            fun UExpression.isClassReference(
                checkClass: Boolean = true,
                checkInterface: Boolean = true,
                checkCompanion: Boolean = true
            ): Pair<Boolean, String?> {
                TODO() // implementation doesn't matter
            }

            fun usageExample1(expression: UExpression): Pair<Boolean, String?> {
                return expression.isClassReference()
            }
            fun usageExample2(expression: UExpression): Pair<Boolean, String?> {
                return expression.isClassReference(checkClass = true)
            }
            """,
          )
          .indented(),
        0xb00733eb,
        """
        META-INF/main.kotlin_module:
        H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijg0uWST8xLKcrPTKnQy0ssy0xPLMnM
        z9PLycwr0UvOz83NzxPi8gFyQksyc7xLlBi0GAC8YwKBRwAAAA==
        """,
        """
        androidx/navigation/lint/common/LintUtilKt.class:
        H4sIAAAAAAAA/51UXW8bRRQ9s15/bex0bUhx7DS0xFDHQNcNpXw4TSlpkFY4
        oWpopCQS0sSeuBuvd6OddZQnlN/AG/+ib0QgQZ75UYg7k21K7UBbLO/MmXtn
        zr3nzt39869ffwdwBw8YmjzoRaHXO3YCfuT1eeyFgeN7Qex0w+GQcIfwk9jz
        v42zYAz2AT/ijs+DvvPd3oHokjVFVk+u+lzKx2JfRCLoCoalRieM+s6BiPci
        7gXSGXEZO0/Wjg8jISVFae/s7Cx2BmFM0ZxH3IvaDNtvfGi58yKhr8PQFzxo
        /8O0GUde0G+vEPfCGDUPgjDWeqWzEcYbI9+nXZnl+KknV3LIM8wncQ6Ohg5V
        QUQB9x03UJTS68osphhmuk9Fd5Ccf8QjPhS0keFmozNeqUvyWtwqoIhpCwVc
        Yagm8YjNHR76YigoaG8tisIoi5LKzQu8eIXhYWOSyu1clu1Dsc9HfrxKIuNo
        1I3DaJ1HAxHpyG/hbQtlzDBcrSvV9clbvP6q+2CwdAX0QQa2wzCtDa5KYZ8r
        knPDajg85AGdYaiMB6r3zvNkWHudDnAna/tyJxWQRsaCgXcZiiPJ+2LtmKuS
        3mZwXhlhvCu33uzE67ekJS4ocqgXcA3zKukPxpJeYig9D7AuYt7jMafTxvAo
        Ra8xU0NeDaD6DxQwyHnsKdQi1CPRP52dVKyzE8uoGJZhT9EzfbHMpatz9tlJ
        1S6bZaNllDNls8JaqRZbythm1Wgpd+Y/vHft7KS3SZ6lXM6wc7TH0Ch/gSyF
        VGakrDhWbBIx9fybc2tAHWGuhj1qoitkFBuj4Z6Ivud7PlnKnbDL/S0eeWqd
        GPObXj/g8SgiXHs8CmJvKNzgyJMeuR+8eOUZ6uPei7f3pW3FzZh3B+v8MAlg
        bYajqCu+8dRiNuHYmuDHbbpIU10KjbOqHWn+nFbbhJW91Cxbp7B/Q3mbmewX
        XP1DXRy+oDFDNcjAwpeEm+eb8Q4qmqxEZFXyK1QjZGg0RyiFtmbIYjnhyNF8
        j56imSz0z86rPiOLyuYHos/QvDBjmj/+jLS5PGOmNbg3Y2Y1WGnW5q6d4voz
        zb5CYxqpfC6nU52ncMqQJZY8zTahMs2zFK+m5xt4L1He1klp5e+T8mYqlbKz
        7BQ3n40pn9bKC4nKulZ+rm1hQluWJYtZNP41kkmRMpdFKv2fSAbu6/EzfEVz
        h6yLpLS5i5SLD1185OJj3HLhoOVSFyztgkl8gju7mJK4IfGpxF39r0tUJBoS
        aYlZiZrE3N9oxKqtmgcAAA==
        """,
      )

    checkBytecodeMigration(
      file,
      "isClassReference\$default",
      """
      @@ -40 +40
      -  INVOKESTATIC androidx/navigation/lint/common/LintUtilKt.isClassReference (Lorg/jetbrains/uast/UExpression;ZZZ)Lkotlin/Pair;
      +  INVOKESTATIC com/android/tools/lint/detector/api/UastLintUtilsKt.isClassReference (Lorg/jetbrains/uast/UExpression;ZZZ)Lkotlin/Pair;
      """,
      showDiff = true,
      checkSource =
        kotlin(
          """
          class MyClass {
            companion object {}
          }
          class MyInterface
          object MyObject {
            val prop = 1
          }
          fun test1() = MyObject
          fun test2() = MyObject.prop
          fun test3() = MyObject::class
          """
        ),
      checks = { ktFile, migratedClass ->
        fun UExpression.isClassReferenceReflection1(): Pair<Boolean, String?> {
          val method = migratedClass.declaredMethods.find { it.name == "usageExample1" }
          @Suppress("UNCHECKED_CAST") return method!!.invoke(null, this) as Pair<Boolean, String?>
        }
        fun UExpression.isClassReferenceReflection2(): Pair<Boolean, String?> {
          val method = migratedClass.declaredMethods.find { it.name == "usageExample2" }
          @Suppress("UNCHECKED_CAST") return method!!.invoke(null, this) as Pair<Boolean, String?>
        }

        val functions = ktFile.declarations.filterIsInstance<KtNamedFunction>()
        val expression1 = functions[0].bodyExpression.toUElement() as UExpression
        val expression2 = functions[1].bodyExpression.toUElement() as UExpression
        val expression3 = functions[2].bodyExpression.toUElement() as UExpression

        // The real implementation in LintUtils:
        assertEquals(false to "MyObject", expression1.isClassReference())
        assertEquals(false to null, expression2.isClassReference())
        assertEquals(false to null, expression3.isClassReference())

        // The migrated, reflection wrapped version
        assertEquals(false to "MyObject", expression1.isClassReferenceReflection1())
        assertEquals(false to null, expression2.isClassReferenceReflection1())
        assertEquals(false to null, expression3.isClassReferenceReflection1())
        // Using different sets of default parameters at the call site
        assertEquals(false to "MyObject", expression1.isClassReferenceReflection2())
        assertEquals(false to null, expression2.isClassReferenceReflection2())
        assertEquals(false to null, expression3.isClassReferenceReflection2())
      },
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
      +  INVOKEINTERFACE org/jetbrains/kotlin/analysis/api/resolution/KaFunctionCall.getTypeArgumentsMapping ()Ljava/util/Map; (itf)
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

  private fun String.escapeDollar(): String {
    return replace('$', '＄')
  }

  private fun checkMigratedFunction(
    kotlinTestSource: TestFile,
    bytecode: ByteArray,
    checks: (KtFile, Class<*>) -> Unit,
  ) {
    val (context, disposable) =
      parseFirst(
        temporaryFolder = temporaryFolder,
        sdkHome = TestUtils.getSdk().toFile(),
        testFiles = arrayOf(kotlinTestSource.indented()),
      )
    val psiFile = context.psiFile as KtFile

    val cr = ClassReader(bytecode)
    val cn = ClassNode(ASM9)
    cr.accept(cn, 0)
    val className = cn.name.replace("/", ".").replace("$", ".")

    val classLoader =
      object : ClassLoader(this.javaClass.classLoader) {
        override fun findClass(name: String): Class<*> {
          return defineClass(name, bytecode, 0, bytecode.size)
        }
      }

    val clazz = classLoader.loadClass(className)

    checks(psiFile, clazz)
    Disposer.dispose(disposable)
  }

  private fun checkBytecodeMigration(
    file: TestFile,
    methodName: String,
    expected: String,
    showDiff: Boolean,
    skipFirst: Int = 0,
    maxLines: Int = 200,
    checkSource: TestFile? = null,
    checks: ((KtFile, Class<*>) -> Unit)? = null,
  ) {
    val bytes =
      if (file is BinaryTestFile) file.binaryContents
      else if (file is BytecodeTestFile) {
        (file
            .getBytecodeFiles()
            .map { it as BinaryTestFile }
            .single() { it.targetRelativePath.endsWith(DOT_CLASS) })
          .binaryContents
      } else {
        error("Unsupported test file type")
      }
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

    val truncated = getTruncatedOutput(output, skipFirst, maxLines)
    assertEquals(trimmedExpected.trim(), truncated.trim())

    if (checks != null && checkSource != null) {
      checkMigratedFunction(checkSource, newBytes, checks)
    } else if (checks != null || checkSource != null) {
      fail("Must specify both test file and checks lambda together")
    }
  }

  private fun getTruncatedOutput(output: String, skipFirst: Int, maxLines: Int): String {
    val lines = output.lines()
    val truncate = lines.size > maxLines - skipFirst
    val sb = StringBuilder(output.length)
    if (skipFirst > 0) {
      sb.append("...\n")
    }
    sb.append(lines.subList(skipFirst, min(lines.size, skipFirst + maxLines)).joinToString("\n"))
    if (truncate) {
      sb.append("\n...")
    }
    val truncated = sb.toString()
    return truncated
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
