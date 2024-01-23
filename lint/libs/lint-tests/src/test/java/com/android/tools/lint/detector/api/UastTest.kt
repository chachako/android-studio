/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.lint.detector.api

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.bytecode
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.dos2unix
import com.android.tools.lint.helpers.DefaultJavaEvaluator
import com.android.tools.lint.useFirUast
import com.intellij.codeInsight.AnnotationTargetUtil
import com.intellij.openapi.util.Disposer
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiAnnotation.TargetType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiWildcardType
import junit.framework.TestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.asJava.elements.KotlinLightTypeParameterBuilder
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UForEachExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UPostfixExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.util.isAssignment
import org.jetbrains.uast.util.isConstructorCall
import org.jetbrains.uast.visitor.AbstractUastVisitor

// Misc tests to verify type handling in the Kotlin UAST initialization.
class UastTest : TestCase() {
  private fun check(source: TestFile, check: (UFile) -> Unit) {
    check(sources = arrayOf(source), check = check)
  }

  private fun check(
    vararg sources: TestFile,
    android: Boolean = true,
    library: Boolean = false,
    javaLanguageLevel: LanguageLevel? = null,
    kotlinLanguageLevel: LanguageVersionSettings? = null,
    check: (UFile) -> Unit = {},
  ) {
    val pair =
      LintUtilsTest.parse(
        testFiles = sources,
        javaLanguageLevel = javaLanguageLevel,
        kotlinLanguageLevel = kotlinLanguageLevel,
        android = android,
        library = library,
      )
    val uastFile = pair.first.uastFile
    assertNotNull(uastFile)
    check(uastFile!!)

    // Validity check: everything should be convertible
    pair.first.psiFile?.accept(
      object : PsiRecursiveElementVisitor() {
        override fun visitElement(element: PsiElement) {
          try {
            element.toUElement()
          } catch (e: Throwable) {
            System.err.println(
              "Converting element " + element + " of class " + element.javaClass + ":"
            )
            throw e
          }
          super.visitElement(element)
        }
      }
    )

    Disposer.dispose(pair.second)
  }

  fun test263980844() {
    val testFiles =
      arrayOf(
        bytecode(
          "libs/lib1.jar",
          kotlin(
            "src/test/pkg/KotlinFoo.kt",
            """
                    package test.pkg
                    open class KotlinFoo {
                        fun kotlinBar() {}
                    }
                    class SubKotlinFoo : KotlinFoo()
                    """,
          ),
          0xa46d4086,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGIOBijg4uJiEGILSS0u8S5RYtBiAAB9et6n
                JAAAAA==
                """,
          """
                test/pkg/KotlinFoo.class:
                H4sIAAAAAAAA/2VQXU8aQRQ9dxYWXLAuaC1Q28Q3bdMuGp+sMakmJCjaRBte
                eBpgQ4eP3YYZjI/8Fv+BT036YIiP/ijjncUQo5Psufecu+fOnfvw+P8OwB4+
                E4om1Cb4O+gFp7EZqqgWxxkQwe/LKxkMZdQLfrX7Ycdk4BDcAxUpc0hwtrab
                eaThekghQ0iZP0oT1hpv2/0gLA0SciTHhEJjToKz0MiuNJLrYnTl8EBkIWsB
                BBqwfq0sq3LW3SF8m019T5SEJ/zZ1BNZm4jsemk23RVVOkrf37gsnLi+UxHV
                lDXtkm2VX8zyfWB41uO4GxJWGioKzyejdjj+LdtDVoqNuCOHTTlWlj+L3mU8
                GXfCmrKkfDGJjBqFTaUVV39GUWykUXGksQnBq7BH8JW8GcYys8C+hWP6yz9k
                b5NyhdFNRAcfGfPzH7AEj2MBuYX5a7IK/l4bUy+M9GwU2EiwhE8c9+3Duely
                C04d7+pYqcNHgVMU61jFWguk8R7rLaQ1PI0PGq5GjpMnUjk4fyACAAA=
                """,
          """
                test/pkg/SubKotlinFoo.class:
                H4sIAAAAAAAA/21Ry07CQBQ9t4WCtcpDUFDZqwsLxJ3GRE1IGqsLMWxYFWh0
                UugYOjUu+Rb/wJWJC0Nc+lHG20qIiS7m5DxuZk7ufH69vQM4QoNQVX6k7Ifg
                zu7Gg0upxiLsSJkDEcrL6JevE4wTEQp1StD39nsWsjBMZJAjZNS9iAhb7r93
                HhNKbpAq+8pX3shTHnva5FHnMpRAPgEQKGD/SSSqyWzUIjTmM9PUalp65rPa
                fNbWmnSe/Xg2tKKWDLUJFfdvZX7CWorDQHHNCznyCQVXhP51PBn401tvMGan
                7MqhN+55U5HohWl2ZTwd+h2RiPpNHCox8XsiEpyehaFUnhIyjNCCxltY9E+W
                wlhjZacayB68Iv/CREOd0UhNA9uM1s8AVmCm+U6KW9hNP4mwypnVh+5gzcG6
                gwKKTFFyUMZGHxShgirnEcwImxGMb1x6FCzhAQAA
                """,
        ),
        kotlin(
          """
                import test.pkg.SubKotlinFoo

                fun test(instance: SubKotlinFoo) {
                    instance.kotlinBar()
                }
                """
        ),
      )

    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            if (node.sourcePsi is KtSuperTypeCallEntry) return super.visitCallExpression(node)

            val bar = node.resolve()
            assertNotNull(bar)
            assertEquals("kotlinBar", bar!!.name)
            assertEquals("KotlinFoo", bar.containingClass?.name)

            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun test257514416() {
    val testFiles =
      arrayOf(
        bytecode(
          "libs/lib1.jar",
          kotlin(
            "src/test/Dependency.kt",
            """
                    package test

                    object Dependency {
                        fun foo() {}
                        fun String.bar(): Int = this.length
                    }
                    """,
          ),
          0xbd459443,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGIOBijg4uJiEGILSS0u8S5RYtBiAAB9et6n
                JAAAAA==
                """,
          """
                test/Dependency.class:
                H4sIAAAAAAAA/21S227TQBA96yS266ZtWugdyqWB3qBOS7lIqYpKAeEqBESr
                SqhPjrOkmzg2sjcRvPWJD+GZlwqJIpBQBW98FGLWiXoDWZ6Z3T1zZubs/v7z
                7QeAFTxgGJA8lvZj/pYHVR547w0whlzdbbu27wY1+0Wlzj1pIMWgr4pAyDWG
                1OzcThYZ6BbSMBjSck/EDIOlc1xFgr4JQ7IVN2IYni2d8G7JSAS14pzDMF0K
                o5pd57ISuSKIbTcIQulKEVJcDmW55ftFVV1VWTMxwDDVCKUvArvebtoikDwK
                XN92AkUZCy82MEjVvD3uNbr5L93IbXICMsyc7qIzXfE/fdGAF3DRwhCGz+jR
                OTcwSh35PKjJvUQPJ4txTFgYwyRDT161mk+GHvqXm8Fc9fxESwuaEtB0ylvb
                6+WNJ1lcg9VDm9eVnN0pn3PpVl3pUqLWbKfo5pgypjJgYA3afyfUqkBRdYnh
                2dH+hKWNaZaWO9q3NFMFmqXCnEl/v/nrgzZ2tL+sFdgjw9R+ftTpfFPPpSa0
                QnrTymXI6/NawVB8y0xV6Tu51sWGZJh81QqkaHInaItYVHy+fnJr9CI2wiqn
                x1USAS+3mhUebbuEUXKEnuvvuJFQ6+5m/jzX8XWdIbW2wlbk8adC5Yx3c3b+
                qY4l0i+dSDOu5CS/RCudfD/5NJ1mktUyrWwlIPnM/CHMAwo03OmCFXSFbLYD
                QA9RAYPoPU5eSIrQfz4xcyqRHSdm0UcolfiQvEa+d2Eo9xUjC19w6dMZCp0+
                RTHSgXUpVHQZU3R+l2KDdScyceW4pdEkgYDfob0+xNXPmD5INjTcS2wB98lv
                EDxPbd7YRcrBTQczDmYxRyHmHZrr1i5YjNtY3IUZw4phx9Bj9CZBNrH2X1tj
                1gpGBAAA
                """,
        ),
        kotlin(
          """
                import test.Dependency.foo
                import test.Dependency.bar

                fun test() {
                    foo()
                    "42".bar()
                }
                """
        ),
      )

    val names = setOf("foo", "bar")

    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val resolved = node.resolve()
            assertNotNull(resolved)
            assertTrue(resolved!!.name in names)
            assertEquals("Dependency", resolved.containingClass?.name)

            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun testJavaAnnotationTarget_fromSource() {
    // Regression test from b/266740119: not applicable only for project-type dependency.
    val testFiles =
      arrayOf(
        java(
            """
                package test;

                class Test {
                    @MyNullable
                    String foo(CharSequence cs) {
                        return cs.toString();
                    }
                }
                """
          )
          .indented(),
        kotlin(
            """
                package test

                import java.lang.annotation.ElementType.METHOD
                import java.lang.annotation.ElementType.PACKAGE

                @Retention(AnnotationRetention.BINARY)
                @Target(
                    AnnotationTarget.FUNCTION,
                    AnnotationTarget.FILE,
                )
                @Suppress("DEPRECATED_JAVA_ANNOTATION")
                @java.lang.annotation.Target(
                  METHOD,
                  PACKAGE,
                )
                annotation class MyNullable
                """
          )
          .indented(),
      )

    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitAnnotation(node: UAnnotation): Boolean {
            if (node.qualifiedName == "test.MyNullable" && node.javaPsi != null) {
              val targets = AnnotationTargetUtil.getTargetsForLocation(node.javaPsi?.owner)
              val applicable = AnnotationTargetUtil.findAnnotationTarget(node.javaPsi!!, *targets)
              // https://youtrack.jetbrains.com/issue/KTIJ-24597
              // annotation target found for K2 only
              if (useFirUast()) {
                assertEquals(TargetType.METHOD, applicable)
              } else {
                assertNull(applicable)
              }
            }

            return super.visitAnnotation(node)
          }
        }
      )
    }
  }

  fun testJavaAnnotationTarget_fromBytecode() {
    // Regression test from b/266740119: applicable if prebuilt bytecode is given.
    val testFiles =
      arrayOf(
        java(
            """
                package test;

                class Test {
                    @MyNullable
                    String foo(CharSequence cs) {
                        return cs.toString();
                    }
                }
                """
          )
          .indented(),
        bytecode(
          "libs/lib1.jar",
          kotlin(
              """
                    package test

                    import java.lang.annotation.ElementType.METHOD
                    import java.lang.annotation.ElementType.PACKAGE

                    @Retention(AnnotationRetention.BINARY)
                    @Target(
                        AnnotationTarget.FUNCTION,
                        AnnotationTarget.FILE,
                    )
                    @Suppress("DEPRECATED_JAVA_ANNOTATION")
                    @java.lang.annotation.Target(
                      METHOD,
                      PACKAGE,
                    )
                    annotation class MyNullable
                    """
            )
            .indented(),
          0xf7411d45,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBihQYtBiAAD1Iry9GAAAAA==
                """,
          """
                test/MyNullable.class:
                H4sIAAAAAAAA/4VSTU9aQRQ99yGC1A/UfoDW4ifuxBp3rp4U6ot8GKBNDKsR
                JubJ8J7xDbTs2PU/uTCkS3+U6R1NgTQvdXPnzL3nnLl3Zh6f7h8AHGOfsKRl
                oHPlQaWnlLhSMgYiJG9EX+SU8K5z1asb2dIxRAiZSVZ4nq+Fdn0vZ49hDFHC
                Rqnja+V605Sa1NIz6IQQ7QvVk4T9EN7Ealoxe+pU7NolYS1E0hB311Iza1Eo
                5f+Q7ZdEQNj97wFjXbz4rZJvONUKYabolApmgtA5x4rt8HpByS733BjcStN0
                udA4q34hxC7s/Ln9lX03w3XTo2ZfoVz4ym0NzC3mS3a9Tlj+O2RZatEWWnDN
                6vYj/LpkwpwJIFCH8z9dsztk1P5MSI+G8YSVshJWcj3++5eVGg2PrEM6HQ0N
                4ch4//M12JutFiaJg44mJOp+764li67iV03XetxoV353A5cJk/sOsuyKGdbP
                mo4Y7z3HXWR57SIK/niIS8whgTcM55uwJBawaMISkqb6nFrGigmrePsieIf3
                +MD6VBMRB2kHaw7W8ZEhNhx8QqYJCrCJLVYH2A6w8wdjiabo/wIAAA==
                """,
        ),
      )

    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitAnnotation(node: UAnnotation): Boolean {
            if (node.qualifiedName == "test.MyNullable" && node.javaPsi != null) {
              val targets = AnnotationTargetUtil.getTargetsForLocation(node.javaPsi?.owner)
              val applicable = AnnotationTargetUtil.findAnnotationTarget(node.javaPsi!!, *targets)
              assertEquals(TargetType.METHOD, applicable)
            }

            return super.visitAnnotation(node)
          }
        }
      )
    }
  }

  fun testExpressionTypeOfCallToInternalOperator() {
    // Regression test from b/270595352.
    val testFiles =
      arrayOf(
        bytecode(
          "libs/lib1.jar",
          kotlin(
            """
                      package test

                      object Dependency {
                          internal operator fun unaryPlus() = Any()
                          operator fun unaryMinus() = Any()
                      }

                      class OtherDependency {
                          internal operator fun inc() = this
                          operator fun dec() = this
                      }
                  """
          ),
          0x6fcad72,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijg4uJiEGILSS0u8S5RYtBiAABz6lUC
                JAAAAA==
                """,
          """
                test/Dependency.class:
                H4sIAAAAAAAA/41RTU8TURQ973U6nQ6lTBGlgN+gFhcONO5EE0SNJaUaIU0M
                q9f2BV87nTGdN43suuKH+AskLkwkMQR3/ijjfUMDBBc6i/t5znn33vn1+/sP
                AI9RYZjSMtb+C/lRhh0ZtvdzYAxeVwyFH4hwz3/T6sq2ziHDYK+pUOlnDJnK
                crOALGwXFnIMlv6gYoZS/ZLWE4ZiEorB/tsgiZf6QoUMM5Xl+mV1wi3Wo8Ge
                35W6NSBY7IswjLTQKqK4EelGEgSEclO1LRUm9Jyz1g7SgVxwM4VTa2zvrDc2
                XhZQgpun4rSZqRdpgvlbUouO0IJUeH+YofWZMXljwMB6VP+kTLZCUWeVYeN4
                VHB5mbvcOx653LGcnwe8fDyq8hX2POfwk8829/hm3svMU+X1yQHfLHrWaTxK
                u07WSFWZeSB/dgiGyfMTPepphoV3SahVX9bCoYpVK5Dr59vTdTeijqQfVVeh
                bCT9lhzsCMIwTNejtgiaYqBMPi6621EyaMtXyiRzY+HmX7JYpftY6epz5lzk
                71Jmk58kb1E3m2aLlPnmQOSzD7/BOaSAY2kMNtB7ZAunAORJCiQ4QRWekqvk
                Tc85gvWe+F8u8bMX+M6Yf3GUEor/rWX/Q8vB1NlSs8Q038QROGl5X3HlMC1w
                3E/tHTwg/5TgMzTk1V1karhWw2wNZcxRiPkaFnB9FyzGDdzchR3DjXErNkEx
                xu0YE38ApJb8UmoDAAA=
                """,
          """
                test/OtherDependency.class:
                H4sIAAAAAAAA/41RyW4TQRB93eN17DjjEIKTsCY5JBFinIgbm1iEmGhwJEC+
                +NQet5K2xz1opm3BzSc+hC+AExIHZOXIRyGqzUhBLII+vKp69eqpq/vrt89f
                ANzGNsOqkZnxj82pTJ/I11IPpI7elsEYvKGYCj8W+sQ/7g9lZMpwGEp3lVbm
                PoOzu9eto4iSiwLKDAVzqjKGtfBPhncYKkpHO2OhNENrd+9vqu0wSU/8oTT9
                lKSZL7ROjDAqobyTmM4kjknlDGTE0AxHiYmV9p9LIwbCCOrw8dSh1ZiFqgUw
                sBHxb5St2pQNDhgezGd1l7e4y735zOWVQms+O+Rt9qh49r7EPX5U9ZwN3p7P
                np2940cNr5AXM277laK1OWTW3KG1GJbOl7g1MvQYj5OBZFgOlZadybgv01ei
                HxOzEiaRiLsiVbbOyc0XE23UWAZ6qjJF1MPzrRncl8kkjeRTZaXrubT7mxAH
                4PQT9jh0L/oYwitU+fYJKBb3P6HykRKOq4SlBVnFNcL6DwFVLsUmasTwxfDN
                fJjvf/hl0v1pkueT1/Pu0sKl8R8utX+4cNxY4GVsUbxH7DLd0+vBCdAMsBLg
                AlYpxcUAa7jUA8vQwnoPpQxuho3MJo0Mmxlq3wFRZP9W9wIAAA==
                """,
        ),
        kotlin(
          """
                 import test.Dependency
                 import test.OtherDependency

                 fun test() {
                     +Dependency
                     Dependency.unaryPlus()
                     -Dependency
                     Dependency.unaryMinus()

                     var x = OtherDependency()
                     x++
                     x.inc()
                     x--
                     x.dec()
                 }
              """
        ),
      )

    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            if (node.isConstructorCall()) return super.visitCallExpression(node)

            if (node.methodName?.startsWith("unary") == true) {
              assertEquals("java.lang.Object", node.getExpressionType()?.canonicalText)
            } else {
              assertEquals("test.OtherDependency", node.getExpressionType()?.canonicalText)
            }

            return super.visitCallExpression(node)
          }

          override fun visitPrefixExpression(node: UPrefixExpression): Boolean {
            val t = node.getExpressionType()
            assertEquals("java.lang.Object", t?.canonicalText)

            return super.visitPrefixExpression(node)
          }

          override fun visitPostfixExpression(node: UPostfixExpression): Boolean {
            val t = node.getExpressionType()
            assertEquals("test.OtherDependency", t?.canonicalText)

            return super.visitPostfixExpression(node)
          }
        }
      )
    }
  }

  fun testPropertiesInCompanionObject_fromBytecode() {
    // Regression test from b/301453029
    val testFiles =
      arrayOf(
        bytecode(
          "libs/lib1.jar",
          kotlin(
              """
              package some

              interface Flag<T>

              class Dependency {
                  companion object {
                      @JvmField val JVM_FIELD_FLAG: Flag<*> = TODO()
                      @JvmStatic val JVM_STATIC_FLAG: Flag<*> = TODO()
                      val VAL_FLAG: Flag<*> = TODO()
                      var varFlag: Flag<*> = TODO()
                  }
              }

              class OtherDependency {
                  companion object Named {
                      @JvmField val JVM_FIELD_FLAG: Flag<*> = TODO()
                      @JvmStatic val JVM_STATIC_FLAG: Flag<*> = TODO()
                      val VAL_FLAG: Flag<*> = TODO()
                      var varFlag: Flag<*> = TODO()
                  }
              }

              object DependencyObject {
                  val VAL_FLAG: Flag<*> = TODO()
                  var varFlag: Flag<*> = TODO()
              }

              val DEPENDENCY_TOP_LEVEL_VAL_FLAG: Flag<*> = TODO()
            """
            )
            .indented(),
          0xc7b66f2d,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijgEuNiKc7PTRVic8tJTPcuEWILSS0u
                8S5RYtBiAAD0O465MAAAAA==
                """,
          """
                some/Dependency＄Companion.class:
                H4sIAAAAAAAA/5VVTVMbRxB9sxLSahFikYHw4YA/lFiAjYA4iRMIGAtjCy8k
                hSgVVRyoYZnghdUutbNSJTdOzv/IJddwCpVDiuKYH5VKz2oBIUiVo8N89Hv9
                eqa7Z/X3P3/+BeA51hmGpV8XpRVxLLx94dk/F8p+/Zh7ju+lwRjMQ97kJZd7
                B6Xv9w6FHaaRYEgtOJ4TLjIkihO1LLqQMpBEmiEZvnckw6j1n6rzDPkDEa7V
                1nerW8tblfLuqrX8hqGnONFyWnX5AZHM9v3C5CKZHlt+cFA6FOFewB1Plrjn
                +SEPSVSWNvxwo+G6xOrtCJ1GL8N9bttCysLtyAX7OIs+ZA2YyDOM3cFoi8Mw
                aB35oet4pcNmvbTWrFcVYlPcbvKsLVvxdQauA14ao0iftCINMRgK4oG6HkN/
                G71li9ijLfZ9Yss2dq7YlqqJGkNf8WaulC29QC7PlhZ1PKRrtR3a8UIReNwt
                VbwwoDw6tkzjMR3Zfi/soziRP/CA1wURGZ4Urc4mmG+zVJWIOkYWn+FzAwU8
                ub6PvHmfCYyr+0xStm4Ue7Zo3XXAFfEjb7hhmRIfBg079IN1HhyJgIIZ0FS/
                3SvY1+BuPUIZpv+fGuXv0mFdhHyfh5xsWr2ZoEfC1JBRAxjYEdl/ctRuhlb7
                swy/nZ/0G9qQZmjm+Ymh6Vproxv6xYfE0PnJnDbDXqV17eLXlGZqm/1mYiSV
                1/TETPJFavvil6SyG+cnmw/MrltAjoB8Uk+Z2khST5v65oCZuSQRrBNMgEHA
                mNl9CWxffMjFvgRmTf1RUu8xc+q4c0xdIqf6e7Xy2lqJe7X31mPUrxs53bzs
                uszVGyarMk0fhfTiy/6+IA3L8cRGo74ngi2+55Ilb/k2d6n6jtrHxkzVOfB4
                2AhoPbrZ8EKnLipe05EOwcvt78ygFxwIm4dinz5SMbV2B7HQKXPVujdo2Yrn
                iaDscimFkq/6jcAWq44rMEu9lFQFRppW9DGjJJVpV1IVp7lr8g/op7TQsEJj
                KjKm8JrGbIuADAya+9BNlkTkPB07J89w7/cOX73NNxn7rkacHryJWTnAzKCf
                1i29HrR+7DSa3kZjJSYPRMEH7wo+3Bk883HBSW/kLr1PO/WMj9Ybw3ist0So
                RnP3VP7RGYpTZ5i6md9ULDvYosWyavUQTwlvBXiANVU0FqeLUSGexbVTsxbX
                bvo0erzXel1Xel1U5BnCEngXXUU5mVjGMKwowqsoxdtknyXu3A4SFXxRwfMK
                vsRXtMTXFbzANztgEt9ifof+fehTjQWJlMR3Ek8lFiX6JbolshJLEoMSLyVG
                JMYkxv8F0/QddIkHAAA=
                """,
          """
                some/Dependency.class:
                H4sIAAAAAAAA/51UW28TRxT+Zu3YzmaJTUhoHMKlxG0dCtkkpTdsAsaJ6aJN
                KjWRJZQHNFlP3U32Eu2OrfYtv6W/gFKpSEVqoz7yoxBn1gs2ufQBS56Zc853
                vjPnMvv6zd//ALiLHxiKcegLc10ciqAjAue3PBhDaZ/3uenxoGv+uLcvHJlH
                hiFXdwNXrjFkqottA2PI6cgiz5CVv7gxw0X7BFeNYaor5JP25rPtncaO1XzW
                shuPGS5UFwfQlse7BCqNyvVba6RasMOoa+4LuRdxN4hNHgSh5NIN6bwVyq2e
                5xFqvBn6hzwgLcOVk9Er7401A0WUxqHhIkP5XFgelwxMwNAxjRmGee44Io4r
                pzOoOIdUuFNpTYwkZWAWZRVxjmFmSNRu2EOGwjvJwNUB+BrD9AiYR4orweb7
                A8HApwPozSE0/hA6WR25x2KbfOuEuPOAGleoO17aw5WqfRBKksz9vm+6gRRR
                wD2qy8+858km1VlGPUeG0SaPDkRUG3S8qmqzyDCX+lIrLP/QE74ggs5GFIVR
                Hl8yrFft4Qhty8gNujXrYwLe0XEbS5SUqnbL2rDX02JPnpyYmVH6J32/5Qqv
                U1NTmeo3heQdLjnpNL+foQfA1DKuFjCwA9L/6ippmU6dFYbK8ZGha7OarpWO
                j3StoM0eH93IrGrL7B7LPBr77/ecVtIUdpUphry6zNKBpFn8qRdI1xdW0Hdj
                d88TjeH40tRuu92Ay14k6Ok0ww5tRdsNxFbP3xPRDic8PRw7dLhHfXWVnCoN
                KwhE1PR4HAsi0rfDXuSIlqts5TRm+1RErNC8ZCmvMZTVQ6CrPiQpR/sk7WUs
                019Dg84aYZVNp9Mq7co+p0b5hP2d75ya3HNsuhrVc2z0lVHfD1ofkWSq8qv7
                3XqJwh8JvJmC1aXXaTXS8zjRqivTM0Umcb6UWIjyBab+wuXniTCIeWE0ZqmM
                TxIa5TSfOmVf4MrzpP0jAQk5fxby+lnIG2chF85CVvDZqXz/xML/5/s55QoU
                8MX7Yi2RVv0uv8L0U/YSxDH1CrefsiwJ5r8fBJ6gQmewkTSDvhcUp0h8rSTi
                Azym/VvSf0WMd3eRsfC1hW8s0n1HR3xv4R5qu2Ax6ri/i2wMPcZajFyM4ltZ
                loBxQwYAAA==
                """,
          """
                some/DependencyObject.class:
                H4sIAAAAAAAA/41UTW/bRhB9S9EkRSk25fhTad02SRPZbkzH/a5Vp4odFwIU
                NbADI4EPwZraKrQoMiBXQnvzKT+kv6BtDi4aoDXSW39U0VmRTiTFBcoDd+fN
                zJu3s0P+/c/vfwD4BHcZZpOoK9wd8UyELRF6P353dCw8aYIxOMe8z92Ah233
                HM0xGFU/9OUWQ66yfFDEBAwbOkwGXT71E4b5xoWMmwyFtpAHtcaT3UbtW4ZL
                leU0cjfgbfI6w3Z1ZYuga40obrvHQh7F3A8Tl4dhJLn0I9o3I9nsBQFFWW84
                C0OMRUzByUNDicFWlXmscAazn+6KmEkDZikgGQqYrAzRLB8wlCqjyhRmVinl
                1p0tC2WGpU4kAz90j/td1w+liEMeuPVQxqTa9xIT71CjvafC62SyH/CYdwUF
                MtysNMb7vDmE7CsSJaOIJbxn4128T0euesHgFmyST6236s39h7Xm9r0irsNW
                Z/qQoZyJopL17rNAdAUpa92L4yg2cZNhp/J2mXrjopPsiO95L5Db1HcZ9zwZ
                xfd53BHxZjoAyzYqWKG+jd9f6ZztvpC8xSUnTOv2czR7TL3y6gUG1iH8B19Z
                67Rr3Wbwz05mbG1BszXn7MTWLC01LMP667m2cHayoa2zu6alvfrJ0Bxtb9bJ
                lY1pzcqt64RY9tlJWbcmHGNvyTHPHY9ePZ8k52TqtBzjqm7lHVsV3GBKhqmU
                r3Ukw5W9Xij9rqiHfT/xjwJRezN5DPl9vx1y2YsFDf121KJlquGHotnrHon4
                Iad4hulG5PGAZspXdgZeH+d9PQcjBez9qBd7YtdXOYtZzsFbSnCbLlqnpuWw
                qO6djvAVWQatk7SW1QdAEZu01/DRiM9Ws/8fvglCJgZWlSxX3Y9CV05h/TII
                /zoLBuaxRe9iGoA80QIlFAjJDZLXsmT9V0z/PJa7MJSrZ7mpnEsjckq4fBHf
                3Djf4v/mm6faKd8d8mq0Flanr/yGD1ZfYG70iEZGO5eGZbRqV6b+sazAIhFB
                fYdpBQtXX7fvFvVFPbMvoT0+xbUXuPESlcdMZ6dY/XMw/ee1bJKl4ZsB45eo
                0dogVBGsHSJXh1vHep3ufIO2+LhOv+9PD8ESfIbPD1FI6B+GLxIYCaYSOIlC
                iglmElxOMJ9g4V8LzDpB9QUAAA==
                """,
          """
                some/Flag.class:
                H4sIAAAAAAAA/2VQS0/CQBicb0EKxUfxWU9ejQeLxJMaEy8kTTAmQrxwWmBt
                Fso2YbfEY3+XB9OzP8r4FRMPuoeZb2Yns4/Pr/cPANcICS2bLVXUT2XigQjn
                d6ObwVyuZZRKk0RPk7mautv7/xYh+Ot5qBM6g0XmUm2iR+XkTDrJSbFc1/g8
                qqBVAQi0YP9NV6rL0+yKcFYWvi9C4TOLoCyar2FZXNSbZRFQT3RFFesR2oPf
                K3M3jaiq8yp5uXD8oKFOjHT5ShH8YZavpqqvUxanz7lxeqletNWTVD0Ykznp
                dGZsg5uxhZ9VwxGjYD7e8CFONn9FaHDGG6MWoxmjFcNHm0dsx9jB7hhksYeA
                9y06FvsWB98ARADnaAEAAA==
                """,
          """
                some/FlagKt.class:
                H4sIAAAAAAAA/4VRXU8TQRQ9s1va7ZaPgqK0iCD40frggjHxAUJCaGs2LoUI
                aUJ4aKbt2Gy7H2Z2tvGR3+IvML6YaKLER3+U8U6tgvLgJDNz77nnnJmb+/3H
                py8AnqHKUEjiUDiNgPdfqhwYQ3HAR9wJeNR3DjsD0SXUZFjrC1WrH9WbtXpz
                /7R9cnjU9uqtutdu7Xnthrf3gmGmUvX+mG2T0dV85/EuQRteLPvOQKiO5H6U
                ODyKYsWVH1PcjFUzDQJirfznocKVZ6aRh52HgQKDtdMN/MhXuwxmpdpiKA9j
                RYi2dsM3gQhFpESvLmUsc5hjyO5M6LWKd9n1sZJ+1N92vYl6MAodn4Qy4oFT
                E695Gqh9+rGSaVfF8oDLoZDb1dY05rFgo4gbDLP/dj7/2+1AKN7jihNmhCOT
                xsD0kdcHGNhQBwYV3/o62qSot8VQuTi3bdrGkmEblrG+WLw4L2cXDMvcZN/e
                ZS2qlTOWUTQ1/ylDTj/9ZKgY8sd+P+IqlYJh+VUaKT8UbjTyE78TiL3LATBk
                9uMekeY8PxLNNOwIecKJw2Afx6nsioavk9LEo3XNAVs0h8y4jbIeC2Ubuhks
                4j7dWcKtcV7CFGUmHlB2h1C9Mh8w/X6sfTjh6tovfe4vvYUZzFKs1RvkpFfh
                M4qnLMM+4ubXayYGHo1t1lGh+zmht0h2+wymiyUXJZe+u+wSccXFXayegSVY
                w70zZBLkE9gJphJkfwJs1hl+NQMAAA==
                """,
          """
                some/OtherDependency＄Named.class:
                H4sIAAAAAAAA/5VVS1MbRxD+ZvVaFiEW8QhgB/xQYgE2AuIkTiAQDMYRWXAK
                USqqOFDDMoGF1cq1M1IlN07O/8gl13AKlUOK4pgflUrPajEClCpHh3n09/XX
                M909q7//+fMvAM+xwTAq6zVReqOORLgq3orgQATuz4VNXhMHGTAG+5g3ecnn
                wWHpzf6xcFUGCYb0ghd4apEhUZyoZpFC2kISGYakOvIkw33nv2XnGfKHQq1X
                N/Yq28vb5ZW9NWf5NUNPcaLltebzQyLZ7fuFyUUyPXbq4WHpWKj9kHuBLPEg
                qCuuvDqtN+tqs+H7xBroFDuDXjoWd10hZeFu+IL7Nos+ZC3YyDOMdWC0BWMY
                ck7qyveC0nGzVlpv1ioacSl4N3lWl534ToPXAa+MUaSPWpGGGSwN8VDfkU7e
                Rm/ZIva9Fvs+sWUbO1dsy9dElaGveDNh2pZZIJdnS4smHtK12g7tBUqEAfdL
                5UCFlEzPlRk8piO7R8I9ibP5Aw+pZERkeFJ0brfCfJulokX0MbL4BJ9aKODJ
                9X3kzftMYFzfZ5KydaPis0Wn0wFXxY+84asVSrwKG66qhxs8PBEhBbNg6K7r
                L7jX4F4tQhmm/58a5e/KYUMofsAVJ5tRayborTA9dOkBDOyE7D95ejdDq4NZ
                ht8uTgcsY9iwDPvi1DJMo7UxLfPyXWL44nTOmGEvM6Zx+WvasI2tATsxms4b
                ZmIm+SK9c/lLUtuti9OtB3bqDpAjIJ8007YxmjQztrk1aHddkQg2CSbAImDM
                7r4Cdi7f5WJfArO2+Shp9tg5fdw5pi+R0/29Vn7lrMa92nvnRZrXjZxpXnVd
                KnrEZNHb6RNFb36lfiDI3/ECsdmo7Ytwm+/7ZMk7dZf7VHlP72NjV8U7DLhq
                hLS+t9UIlFcT5aDpSY/g5fY3ZtHrDYXLlQ43ElOrHYiF2zLv2/YGLVsOAhGu
                +FxKoeUr9UboijXPF5ilPkrq4iJDK/qcUYJWaFfS1aY5NfkHzDNaGFilMR0Z
                e/GKxmyLgC5YNPehmyyJyHk6dk6eo//3W759bb7J2Hct4vTgdczKAXYXBmjd
                0utB68fOoum7aCzH5MFIdKhT8JHbwfMfFpz0RjvpfXxbr/+D9cYwHustEWrQ
                3D2Vf3SO4tQ5pm7mNx3LDrVosaxePcRTwlsBHmBdF43F6aL2xLO4dno24tpN
                n0UP91ov9V4vRUWeISyB72lnRU42ljECJ4rwMkrxDtlniTu3i0QZn5XxvIzP
                8QUt8WUZL/DVLpjE15jfRa+kzzQWJNIS30g8lViU9J+EbomsxJLEkMS3kv54
                MSYx/i/sb3NVjAcAAA==
                """,
          """
                some/OtherDependency.class:
                H4sIAAAAAAAA/51UW08bRxT+Zm1ss2ywQ4BiQtskuI3JhQWa3mKXxLFxutFC
                pIIsRTxEw3rqLOwF7Y6t9o3f0l+QplIjNVKL+tgfVfXM2tROMHmIJc/MOeeb
                71xn//n3jz8B3MP3DLNx6AvzqXwhooY4FkFbBM7PWTCGwiHvcdPjQcd8enAo
                HJlFiiFTdQNXbjKkyistAxPI6Egjy5CWL9yYYd4eR1hhmOkI+aS1/Xx3r7Zn
                1Z837dpjhkvllT6+6fEOgQqjcvXWJqmW7TDqmIdCHkTcDWKTB0EouXRDOu+E
                cqfreYSa2OG+aDMsjXVfSqwVA3kUJqHhMsPixbgsrhiYgqFjFnNEyR1HxHHp
                fPwl55ghfy6pqZGUDCygqFwuMswNiVo1e8iQO5MMfNwHf0J9GQHzSHEl2Gyv
                Lxi43ofeGELjt6HT5ZE4Vlp0t0qIuw+od7mq4w3auF62j0JJknnY8003kCIK
                uGc2xI+868k6VVlGXUeG0TaPjkRU6Te9rGqzQlUc3KVGWP6xJ3xBBO2tKAqj
                LG4zNMr2cIp2ZeQGnYr1IQ7v6riDVUpKVbtpbdmNQbGn352XuVH6Jz2/6Qqv
                TfrLZ/ptIXmbS046ze+l6CEwtUyqBQzsiPQ/uUpao1N7naF0emLo2oKma4XT
                E13LaQunJ9dSG9oau89Sjyb+/iWjFTSF3WCKIauCWT2SDFd/6AbS9YUV9NzY
                PfBEbTi8DJO7bifgshsJej31sE1b3nYDsdP1D0S0xwlPz8YOHe5RX10lD5SG
                FQQiqns8jgUR6bthN3JE01W24sBn65xHrNO8pCmvCRTVS6BQH5KUoX2a9iLW
                6K+hRmeNsMqm02mDdmVfVKP8jv3s7qKa3AtsuhrVC2z0oVGfEFofkWSq8qv4
                br1G7tcEXh+AVQgNWo0+AJNEq0KmZ4pUcvlKYiHKV5j5HfMvE6Hv89Koz0IR
                HyU06tLS4FL6Fa6+TNo/4pCQS+OQn45DXhuHXB6HLOGzc/n+huX35/s55Qrk
                cPP/Yq2SVv3m32D2GXsN4ph5gzvPWJoE86+3HE9Rs1PYSppB3wvykye+ZuLx
                AR7T/jXpvyDGe/tIWfjSwlcW6b6hI761cB+VfbAYVXy3j3QMPcZmjEyM/H+K
                owqASwYAAA==
                """,
        ),
        kotlin(
          """
            package some

            private fun consumeFlag(p: Flag<*>) {
              println(p)
            }

            fun test() {
              consumeFlag(Dependency.JVM_FIELD_FLAG)
              consumeFlag(Dependency.JVM_STATIC_FLAG)
              consumeFlag(Dependency.VAL_FLAG)
              consumeFlag(Dependency.varFlag)
              consumeFlag(OtherDependency.JVM_FIELD_FLAG)
              consumeFlag(OtherDependency.JVM_STATIC_FLAG)
              consumeFlag(OtherDependency.VAL_FLAG)
              consumeFlag(OtherDependency.varFlag)
              consumeFlag(DependencyObject.VAL_FLAG)
              consumeFlag(DependencyObject.varFlag)
              consumeFlag(DEPENDENCY_TOP_LEVEL_VAL_FLAG)
            }
          """
        ),
      )

    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            // Test call-sites of `consumeFlag`, not `consumeFlag` itself.
            if (node.methodName != "consumeFlag") {
              return super.visitCallExpression(node)
            }

            val arg = node.valueArguments.singleOrNull()
            val selector = arg?.findSelector() as? USimpleNameReferenceExpression
            val resolved = selector?.resolve()
            assertNotNull(resolved)
            assertTrue(resolved is PsiField)

            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun test126439418() {
    // Regression test for https://issuetracker.google.com/126439418 /
    //  https://youtrack.jetbrains.com/issue/KT-35801
    val source =
      kotlin(
          """
                private val variable: Any = Object()

                fun foo1() {

                    @Suppress("MoveLambdaOutsideParentheses")
                    foo2({ variable.hashCode() })
                }

                fun foo2(function: () -> Int) {}
            """
        )
        .indented()

    check(source) { file ->
      assertEquals(
        "" +
          "public final class TestKt {\n" +
          "    @org.jetbrains.annotations.NotNull private static final var variable: java.lang.Object = <init>()\n" +
          "    public static final fun foo1() : void {\n" +
          // Using plain string literal such that we can have our trailing space
          // here without IntelliJ removing it every time we save this file:
          "        foo2({ \n" +
          "            return variable.hashCode()\n" +
          "        })\n" +
          "    }\n" +
          "    public static final fun foo2(@org.jetbrains.annotations.NotNull function: kotlin.jvm.functions.Function0<java.lang.Integer>) : void {\n" +
          "    }\n" +
          "}",
        file.asSourceString().dos2unix().trim(),
      )
    }
  }

  fun testKt25298() {
    // Regression test for
    // 	KT-25298 UAST: NPE ClsFileImpl.getMirror during lambda inference session
    val source =
      java(
          """
            package test.pkg;
            import java.util.concurrent.Executors;
            import java.util.concurrent.ScheduledExecutorService;
            import java.util.concurrent.TimeUnit;
            public class MyTestCase
            {
                private final ScheduledExecutorService mExecutorService;
                public MyTestCase()
                {
                    mExecutorService = Executors.newSingleThreadScheduledExecutor();
                }
                public void foo()
                {
                    mExecutorService.schedule(this::initBar, 10, TimeUnit.SECONDS);
                }
                private boolean initBar()
                {
                    //...
                    return true;
                }
            }"""
        )
        .indented()

    check(source) { file ->
      assertEquals(
        "" +
          "UFile (package = test.pkg) [package test.pkg...]\n" +
          "    UImportStatement (isOnDemand = false) [import java.util.concurrent.Executors]\n" +
          "    UImportStatement (isOnDemand = false) [import java.util.concurrent.ScheduledExecutorService]\n" +
          "    UImportStatement (isOnDemand = false) [import java.util.concurrent.TimeUnit]\n" +
          "    UClass (name = MyTestCase) [public class MyTestCase {...}]\n" +
          "        UField (name = mExecutorService) [private final var mExecutorService: java.util.concurrent.ScheduledExecutorService] : PsiType:ScheduledExecutorService\n" +
          "        UMethod (name = MyTestCase) [public fun MyTestCase() {...}]\n" +
          "            UBlockExpression [{...}]\n" +
          "                UBinaryExpression (operator = =) [mExecutorService = Executors.newSingleThreadScheduledExecutor()] : PsiType:ScheduledExecutorService\n" +
          "                    USimpleNameReferenceExpression (identifier = mExecutorService) [mExecutorService] : PsiType:ScheduledExecutorService\n" +
          "                    UQualifiedReferenceExpression [Executors.newSingleThreadScheduledExecutor()] : PsiType:ScheduledExecutorService\n" +
          "                        USimpleNameReferenceExpression (identifier = Executors) [Executors]\n" +
          "                        UCallExpression (kind = UastCallKind(name='method_call'), argCount = 0)) [newSingleThreadScheduledExecutor()] : PsiType:ScheduledExecutorService\n" +
          "                            UIdentifier (Identifier (newSingleThreadScheduledExecutor)) [UIdentifier (Identifier (newSingleThreadScheduledExecutor))]\n" +
          "        UMethod (name = foo) [public fun foo() : void {...}] : PsiType:void\n" +
          "            UBlockExpression [{...}]\n" +
          "                UQualifiedReferenceExpression [mExecutorService.schedule(this::initBar, 10, TimeUnit.SECONDS)]\n" +
          "                    USimpleNameReferenceExpression (identifier = mExecutorService) [mExecutorService] : PsiType:ScheduledExecutorService\n" +
          "                    UCallExpression (kind = UastCallKind(name='method_call'), argCount = 3)) [schedule(this::initBar, 10, TimeUnit.SECONDS)]\n" +
          "                        UIdentifier (Identifier (schedule)) [UIdentifier (Identifier (schedule))]\n" +
          "                        UCallableReferenceExpression (name = initBar) [this::initBar] : PsiType:<method reference>\n" +
          "                            UThisExpression (label = null) [this] : PsiType:MyTestCase\n" +
          "                        ULiteralExpression (value = 10) [10] : PsiType:int\n" +
          "                        UQualifiedReferenceExpression [TimeUnit.SECONDS] : PsiType:TimeUnit\n" +
          "                            USimpleNameReferenceExpression (identifier = TimeUnit) [TimeUnit]\n" +
          "                            USimpleNameReferenceExpression (identifier = SECONDS) [SECONDS]\n" +
          "        UMethod (name = initBar) [private fun initBar() : boolean {...}] : PsiType:boolean\n" +
          "            UBlockExpression [{...}]\n" +
          "                UReturnExpression [return true]\n" +
          "                    ULiteralExpression (value = true) [true] : PsiType:boolean\n",
        file.asLogTypes(),
      )
    }
  }

  fun testNodeIsConstructor() {
    // Regression test for
    // 206982645: UMethod#isConstructor returns false on actual constructor
    // https://youtrack.jetbrains.com/issue/KTIJ-20200
    val source =
      kotlin(
          """
            class Test(private val parameter: Int)  {
                @Deprecated(message = "Binary compatibility", level = DeprecationLevel.HIDDEN)
                constructor() : this(42)
            }
          """
        )
        .indented()

    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitMethod(node: UMethod): Boolean {
            if (node.sourcePsi is KtConstructor<*>) {
              assertTrue("`${node.name}` is not marked as a UAST constructor", node.isConstructor)
            }
            return super.visitMethod(node)
          }
        }
      )
    }
  }

  fun test123923544() {
    // Regression test for
    //  https://youtrack.jetbrains.com/issue/KT-30033
    // 	https://issuetracker.google.com/123923544
    val source =
      kotlin(
          """
            interface Base {
                fun print()
            }

            class BaseImpl(val x: Int) : Base {
                override fun print() { println(x) }
            }

            fun createBase(i: Int): Base {
                return BaseImpl(i)
            }

            class Derived(b: Base) : Base by createBase(10)
            """
        )
        .indented()

    check(source) { file ->
      assertEquals(
        """
                public final class BaseKt {
                    public static final fun createBase(@org.jetbrains.annotations.NotNull i: int) : Base {
                        return <init>(i)
                    }
                }

                public abstract interface Base {
                    public abstract fun print() : void = UastEmptyExpression
                }

                public final class BaseImpl : Base {
                    @org.jetbrains.annotations.NotNull private final var x: int
                    public fun print() : void {
                        println(x)
                    }
                    public final fun getX() : int = UastEmptyExpression
                    public fun BaseImpl(@org.jetbrains.annotations.NotNull x: int) = UastEmptyExpression
                }

                public final class Derived : Base {
                    public fun Derived(@org.jetbrains.annotations.NotNull b: Base) = UastEmptyExpression
                }

                """
          .trimIndent(),
        file.asSourceString().dos2unix(),
      )

      assertEquals(
        """
                UFile (package = ) [public final class BaseKt {...]
                    UClass (name = BaseKt) [public final class BaseKt {...}]
                        UMethod (name = createBase) [public static final fun createBase(@org.jetbrains.annotations.NotNull i: int) : Base {...}] : PsiType:Base
                            UParameter (name = i) [@org.jetbrains.annotations.NotNull var i: int] : PsiType:int
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            UBlockExpression [{...}] : PsiType:Void
                                UReturnExpression [return <init>(i)] : PsiType:Void
                                    UCallExpression (kind = UastCallKind(name='constructor_call'), argCount = 1)) [<init>(i)] : PsiType:BaseImpl
                                        UIdentifier (Identifier (BaseImpl)) [UIdentifier (Identifier (BaseImpl))]
                                        USimpleNameReferenceExpression (identifier = <init>, resolvesTo = PsiClass: BaseImpl) [<init>] : PsiType:BaseImpl
                                        USimpleNameReferenceExpression (identifier = i) [i] : PsiType:int
                    UClass (name = Base) [public abstract interface Base {...}]
                        UMethod (name = print) [public abstract fun print() : void = UastEmptyExpression] : PsiType:void
                    UClass (name = BaseImpl) [public final class BaseImpl : Base {...}]
                        UField (name = x) [@org.jetbrains.annotations.NotNull private final var x: int] : PsiType:int
                            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                        UMethod (name = print) [public fun print() : void {...}] : PsiType:void
                            UBlockExpression [{...}] : PsiType:void
                                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println(x)] : PsiType:Unit
                                    UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                    USimpleNameReferenceExpression (identifier = println, resolvesTo = null) [println] : PsiType:Unit
                                    USimpleNameReferenceExpression (identifier = x) [x] : PsiType:int
                        UMethod (name = getX) [public final fun getX() : int = UastEmptyExpression] : PsiType:int
                        UMethod (name = BaseImpl) [public fun BaseImpl(@org.jetbrains.annotations.NotNull x: int) = UastEmptyExpression]
                            UParameter (name = x) [@org.jetbrains.annotations.NotNull var x: int] : PsiType:int
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                    UClass (name = Derived) [public final class Derived : Base {...}]
                        UExpressionList (super_delegation) [super_delegation Base : createBase(10)]
                            UTypeReferenceExpression (name = Base) [Base]
                            UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [createBase(10)] : PsiType:Base
                                UIdentifier (Identifier (createBase)) [UIdentifier (Identifier (createBase))]
                                USimpleNameReferenceExpression (identifier = createBase, resolvesTo = null) [createBase] : PsiType:Base
                                ULiteralExpression (value = 10) [10] : PsiType:int
                        UMethod (name = Derived) [public fun Derived(@org.jetbrains.annotations.NotNull b: Base) = UastEmptyExpression]
                            UParameter (name = b) [@org.jetbrains.annotations.NotNull var b: Base] : PsiType:Base
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]

                """
          .trimIndent(),
        file.asLogTypes(),
      )
    }
  }

  fun test13Features() {
    check(
      kotlin(
          """
                package test.pkg

                // Assignment in when
                fun test(s: String) =
                        when (val something = s.hashCode()) {
                            is Int -> println(something)
                            else -> ""
                        }

                interface FooInterface {
                    companion object {
                        @JvmField
                        val answer: Int = 42

                        @JvmStatic
                        fun sayHello() {
                            println("Hello, world!")
                        }
                    }
                }

                // Nested declarations in annotation classes
                annotation class FooAnnotation {
                    enum class Direction { UP, DOWN, LEFT, RIGHT }

                    annotation class Bar

                    companion object {
                        fun foo(): Int = 42
                        val bar: Int = 42
                    }
                }

                // Inline classes
                inline class Name(val s: String)

                // Unsigned
                // You can define unsigned types using literal suffixes
                val uint = 42u
                val ulong = 42uL
                val ubyte: UByte = 255u


                // @JvmDefault
                interface FooInterface2 {
                    // Will be generated as 'default' method
                    @JvmDefault
                    fun foo(): Int = 42
                }
                """
        )
        .indented()
    ) { file ->
      // val ubyte: UByte = 255u
      val uByteValue = if (useFirUast()) 255 else -1
      assertEquals(
        """
                UFile (package = test.pkg) [package test.pkg...]
                    UClass (name = FooInterfaceKt) [public final class FooInterfaceKt {...}]
                        UField (name = uint) [@org.jetbrains.annotations.NotNull private static final var uint: int = 42] : PsiType:int
                            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            ULiteralExpression (value = 42) [42] : PsiType:int
                        UField (name = ulong) [@org.jetbrains.annotations.NotNull private static final var ulong: long = 42] : PsiType:long
                            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            ULiteralExpression (value = 42) [42] : PsiType:long
                        UField (name = ubyte) [@org.jetbrains.annotations.NotNull private static final var ubyte: byte = $uByteValue] : PsiType:byte
                            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            ULiteralExpression (value = $uByteValue) [$uByteValue] : PsiType:byte
                        UMethod (name = test) [public static final fun test(@org.jetbrains.annotations.NotNull s: java.lang.String) : java.lang.Object {...}] : PsiType:Object
                            UParameter (name = s) [@org.jetbrains.annotations.NotNull var s: java.lang.String] : PsiType:String
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            UBlockExpression [{...}]
                                UReturnExpression [return switch (var something: int = s.hashCode())  {...]
                                    USwitchExpression [switch (var something: int = s.hashCode())  {...] : PsiType:Object
                                        UDeclarationsExpression [var something: int = s.hashCode()]
                                            ULocalVariable (name = something) [var something: int = s.hashCode()] : PsiType:int
                                                UQualifiedReferenceExpression [s.hashCode()] : PsiType:int
                                                    USimpleNameReferenceExpression (identifier = s) [s] : PsiType:String
                                                    UCallExpression (kind = UastCallKind(name='method_call'), argCount = 0)) [hashCode()] : PsiType:int
                                                        UIdentifier (Identifier (hashCode)) [UIdentifier (Identifier (hashCode))]
                                                        USimpleNameReferenceExpression (identifier = hashCode, resolvesTo = null) [hashCode] : PsiType:int
                                        UExpressionList (when) [    it is java.lang.Integer -> {...    ] : PsiType:Object
                                            USwitchClauseExpressionWithBody [it is java.lang.Integer -> {...]
                                                UBinaryExpressionWithType [it is java.lang.Integer]
                                                    USimpleNameReferenceExpression (identifier = it) [it]
                                                    UTypeReferenceExpression (name = java.lang.Integer) [java.lang.Integer]
                                                UExpressionList (when_entry) [{...]
                                                    UYieldExpression [yield println(something)]
                                                        UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println(something)] : PsiType:Unit
                                                            UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                                            USimpleNameReferenceExpression (identifier = println, resolvesTo = null) [println] : PsiType:Unit
                                                            USimpleNameReferenceExpression (identifier = something) [something] : PsiType:int
                                            USwitchClauseExpressionWithBody [ -> {...]
                                                UExpressionList (when_entry) [{...]
                                                    UYieldExpression [yield ""]
                                                        ULiteralExpression (value = "") [""] : PsiType:String
                        UMethod (name = getUint) [public static final fun getUint() : int = UastEmptyExpression] : PsiType:int
                        UMethod (name = getUlong) [public static final fun getUlong() : long = UastEmptyExpression] : PsiType:long
                        UMethod (name = getUbyte) [public static final fun getUbyte() : byte = UastEmptyExpression] : PsiType:byte
                    UClass (name = FooInterface) [public abstract interface FooInterface {...}]
                        UField (name = Companion) [@null public static final var Companion: test.pkg.FooInterface.Companion] : PsiType:Companion
                            UAnnotation (fqName = null) [@null]
                        UField (name = answer) [@org.jetbrains.annotations.NotNull @kotlin.jvm.JvmField public static final var answer: int = 42] : PsiType:int
                            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            UAnnotation (fqName = kotlin.jvm.JvmField) [@kotlin.jvm.JvmField]
                            ULiteralExpression (value = 42) [42] : PsiType:int
                        UMethod (name = sayHello) [public static fun sayHello() : void {...}] : PsiType:void
                            UBlockExpression [{...}] : PsiType:void
                                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println("Hello, world!")] : PsiType:Unit
                                    UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                    USimpleNameReferenceExpression (identifier = println, resolvesTo = null) [println] : PsiType:Unit
                                    ULiteralExpression (value = "Hello, world!") ["Hello, world!"] : PsiType:String
                        UClass (name = Companion) [public static final class Companion {...}]
                            UMethod (name = sayHello) [@kotlin.jvm.JvmStatic...}] : PsiType:void
                                UAnnotation (fqName = kotlin.jvm.JvmStatic) [@kotlin.jvm.JvmStatic]
                                UBlockExpression [{...}] : PsiType:void
                                    UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println("Hello, world!")] : PsiType:Unit
                                        UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                        USimpleNameReferenceExpression (identifier = println, resolvesTo = null) [println] : PsiType:Unit
                                        ULiteralExpression (value = "Hello, world!") ["Hello, world!"] : PsiType:String
                            UMethod (name = Companion) [private fun Companion() = UastEmptyExpression]
                    UClass (name = FooAnnotation) [public abstract annotation FooAnnotation {...}]
                        UField (name = Companion) [@null public static final var Companion: test.pkg.FooAnnotation.Companion] : PsiType:Companion
                            UAnnotation (fqName = null) [@null]
                        UClass (name = Direction) [public static final enum Direction {...}]
                            UEnumConstant (name = UP) [@null UP]
                                UAnnotation (fqName = null) [@null]
                                USimpleNameReferenceExpression (identifier = Direction) [Direction]
                            UEnumConstant (name = DOWN) [@null DOWN]
                                UAnnotation (fqName = null) [@null]
                                USimpleNameReferenceExpression (identifier = Direction) [Direction]
                            UEnumConstant (name = LEFT) [@null LEFT]
                                UAnnotation (fqName = null) [@null]
                                USimpleNameReferenceExpression (identifier = Direction) [Direction]
                            UEnumConstant (name = RIGHT) [@null RIGHT]
                                UAnnotation (fqName = null) [@null]
                                USimpleNameReferenceExpression (identifier = Direction) [Direction]
                            UMethod (name = Direction) [private fun Direction() = UastEmptyExpression]
                            UMethod (name = getEntries) [public static fun getEntries() : kotlin.enums.EnumEntries<test.pkg.FooAnnotation.Direction> = UastEmptyExpression] : PsiType:EnumEntries<Direction>
                            UMethod (name = values) [public static fun values() : test.pkg.FooAnnotation.Direction[] = UastEmptyExpression] : PsiType:Direction[]
                            UMethod (name = valueOf) [public static fun valueOf(value: java.lang.String) : test.pkg.FooAnnotation.Direction = UastEmptyExpression] : PsiType:Direction
                                UParameter (name = value) [var value: java.lang.String] : PsiType:String
                        UClass (name = Bar) [public static abstract annotation Bar {...}]
                        UClass (name = Companion) [public static final class Companion {...}]
                            UField (name = bar) [@org.jetbrains.annotations.NotNull private static final var bar: int = 42] : PsiType:int
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                                ULiteralExpression (value = 42) [42] : PsiType:int
                            UMethod (name = foo) [public final fun foo() : int {...}] : PsiType:int
                                UBlockExpression [{...}]
                                    UReturnExpression [return 42]
                                        ULiteralExpression (value = 42) [42] : PsiType:int
                            UMethod (name = getBar) [public final fun getBar() : int = UastEmptyExpression] : PsiType:int
                            UMethod (name = Companion) [private fun Companion() = UastEmptyExpression]
                    UClass (name = Name) [public final class Name {...}]
                        UMethod (name = getS) [public final fun getS() : java.lang.String = UastEmptyExpression] : PsiType:String
                    UClass (name = FooInterface2) [public abstract interface FooInterface2 {...}]
                        UMethod (name = foo) [@kotlin.jvm.JvmDefault...}] : PsiType:int
                            UAnnotation (fqName = kotlin.jvm.JvmDefault) [@kotlin.jvm.JvmDefault]
                            UBlockExpression [{...}]
                                UReturnExpression [return 42]
                                    ULiteralExpression (value = 42) [42] : PsiType:int

                """
          .trimIndent(),
        file.asLogTypes(),
      )
    }
  }

  fun testSuspend() {
    // Regression test for
    // https://youtrack.jetbrains.com/issue/KT-32031:
    // UAST: Method body missing for suspend functions
    val source =
      kotlin(
          """
            package test.pkg
            import android.widget.TextView
            class Test : android.app.Activity {
                private suspend fun setUi(x: Int, y: Int) {
                    val z = x + y
                }
            }
            """
        )
        .indented()

    check(source) { file ->
      assertEquals(
        """
                package test.pkg

                import android.widget.TextView

                public final class Test : android.app.Activity {
                    private final fun setUi(@org.jetbrains.annotations.NotNull x: int, @org.jetbrains.annotations.NotNull y: int, ${'$'}completion: kotlin.coroutines.Continuation<? super kotlin.Unit>) : java.lang.Object {
                        var z: int = x + y
                    }
                    public fun Test() = UastEmptyExpression
                }

                """
          .trimIndent(),
        file.asSourceString().dos2unix(),
      )

      assertEquals(
        """
                UFile (package = test.pkg) [package test.pkg...]
                    UImportStatement (isOnDemand = false) [import android.widget.TextView]
                    UClass (name = Test) [public final class Test : android.app.Activity {...}]
                        UMethod (name = setUi) [private final fun setUi(@org.jetbrains.annotations.NotNull x: int, @org.jetbrains.annotations.NotNull y: int, ${"$"}completion: kotlin.coroutines.Continuation<? super kotlin.Unit>) : java.lang.Object {...}] : PsiType:Object
                            UParameter (name = x) [@org.jetbrains.annotations.NotNull var x: int] : PsiType:int
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            UParameter (name = y) [@org.jetbrains.annotations.NotNull var y: int] : PsiType:int
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            UParameter (name = ${"$"}completion) [var ${"$"}completion: kotlin.coroutines.Continuation<? super kotlin.Unit>] : PsiType:Continuation<? super Unit>
                            UBlockExpression [{...}] : PsiType:void
                                UDeclarationsExpression [var z: int = x + y]
                                    ULocalVariable (name = z) [var z: int = x + y] : PsiType:int
                                        UBinaryExpression (operator = +) [x + y] : PsiType:int
                                            USimpleNameReferenceExpression (identifier = x) [x] : PsiType:int
                                            USimpleNameReferenceExpression (identifier = y) [y] : PsiType:int
                        UMethod (name = Test) [public fun Test() = UastEmptyExpression]

                """
          .trimIndent(),
        file.asLogTypes(),
      )
    }
  }

  fun testReifiedTypes() {
    // Regression test for
    // https://youtrack.jetbrains.com/issue/KT-35610:
    // UAST: Some reified methods nave null returnType
    val source =
      kotlin(
          """
            package test.pkg
            inline fun <T> function1(t: T) { }                  // return type void (PsiPrimitiveType)
            inline fun <T> function2(t: T): T = t               // return type T (PsiClassReferenceType)
            inline fun <reified T> function3(t: T) { }          // return type null
            inline fun <reified T> function4(t: T): T = t       // return type null
            inline fun <reified T> function5(t: T): Int = 42    // return type null
            // Other variations that also have a null return type
            inline fun <reified T : Activity> T.function6(t: T): T = t
            inline fun <reified T> function7(t: T): T = t
            private inline fun <reified T> function8(t: T): T = t
            internal inline fun <reified T> function9(t: T): T = t
            public inline fun <reified T> function10(t: T): T = t
            inline fun <reified T> T.function11(t: T): T = t
            fun <reified T> function12(t: T) { }
            """
        )
        .indented()

    // With the upper bound `T : Activity`, T is not null.
    val nonNull = if (useFirUast()) "@${NotNull::class.java.name} " else ""
    check(source) { file ->
      assertEquals(
        """
                package test.pkg

                public final class TestKt {
                    public static final fun function1(t: T) : void {
                    }
                    public static final fun function2(t: T) : T {
                        return t
                    }
                    public static fun function3(t: T) : void {
                    }
                    public static fun function4(t: T) : T {
                        return t
                    }
                    public static fun function5(t: T) : int {
                        return 42
                    }
                    public static fun function6($nonNull${"$"}this${"$"}function6: T, ${nonNull}t: T) : T {
                        return t
                    }
                    public static fun function7(t: T) : T {
                        return t
                    }
                    private static fun function8(t: T) : T {
                        return t
                    }
                    public static fun function9(t: T) : T {
                        return t
                    }
                    public static fun function10(t: T) : T {
                        return t
                    }
                    public static fun function11(${"$"}this${"$"}function11: T, t: T) : T {
                        return t
                    }
                    public static fun function12(t: T) : void {
                    }
                }

                """
          .trimIndent(),
        file.asSourceString().dos2unix(),
      )
    }
  }

  fun testModifiers() {
    // Regression test for
    // https://youtrack.jetbrains.com/issue/KT-35610:
    // UAST: Some reified methods nave null returnType
    val source =
      kotlin(
          """
            @file:Suppress("all")
            package test.pkg
            class Test {
                inline fun <T> function1(t: T) { }                  // return type void (PsiPrimitiveType)
                inline fun <T> function2(t: T): T = t               // return type T (PsiClassReferenceType)
                inline fun <reified T> function3(t: T) { }          // return type null
                inline fun <reified T> function4(t: T): T = t       // return type null
                inline fun <reified T> function5(t: T): Int = 42    // return type null
                // Other variations that also have a null return type
                inline fun <reified T : Activity> T.function6(t: T): T = t
                inline fun <reified T> function7(t: T): T = t
                private inline fun <reified T> function8(t: T): T = t
                internal inline fun <reified T> function9(t: T): T = t
                public inline fun <reified T> functionA(t: T): T = t
                inline fun <reified T> T.functionB(t: T): T = t
                fun <reified T> functionC(t: T) { }

                suspend fun suspendMethod() { }
                infix fun combine(a: Int): Int { return 0 }
                internal fun myInternal() { }
                operator fun get(index: Int): Int = 0
                @JvmField lateinit var delayed: String
                const val constant = 42
                open fun isOpen() { }
                noinline fun notInlined() { }
                tailrec fun me() { me() }
                inline fun f(crossinline body: () -> Unit) {
                    val f = object: Runnable {
                        override fun run() = body()
                    }
                }
                fun multiarg(vararg arg: String) { }

                // Multiplatform stuff
                external fun fromElsewhere()
                actual fun randomUUID() = "not random"
                expect fun randomUUID(): String

                companion object NamedCompanion { }
            }
            sealed class Sealed
            data class Data(val prop: String)

            interface List<out E> : Collection<E>
            interface Comparator<in T> {
                fun compare(e1: T, e2: T): Int = 0
            }
            """
        )
        .indented()

    check(source) { file ->

      // type parameter lookup methods; these would ideally go in JavaEvaluator
      // but can't yet because they're relying on some patches only available
      // in the kotlin-compiler fork (i.e. KotlinLightTypeParameterBuilder).

      fun hasTypeParameterKeyword(
        element: PsiTypeParameter?,
        keyword: KtModifierKeywordToken,
      ): Boolean {
        val ktOrigin =
          when (element) {
            is KotlinLightTypeParameterBuilder -> element.origin
            else -> element?.unwrapped as? KtTypeParameter ?: return false
          }
        return ktOrigin.hasModifier(keyword)
      }

      fun isReified(element: PsiTypeParameter?): Boolean {
        return hasTypeParameterKeyword(element, KtTokens.REIFIED_KEYWORD)
      }

      fun isInVariance(element: PsiTypeParameter?): Boolean {
        return hasTypeParameterKeyword(element, KtTokens.IN_KEYWORD)
      }

      fun isOutVariance(element: PsiTypeParameter?): Boolean {
        return hasTypeParameterKeyword(element, KtTokens.OUT_KEYWORD)
      }

      val evaluator = DefaultJavaEvaluator(null, null)
      val sb = StringBuilder()
      for (cls in file.classes.sortedBy { it.name }) {
        for (declaration in cls.uastDeclarations) {
          if (declaration is UClass) {
            sb.append("nested class ")
            sb.append(declaration.name).append(":")
            if (evaluator.isCompanion(declaration)) {
              sb.append(" companion")
            }
            sb.append("\n")
          }
        }
        sb.append("class ")
        sb.append(cls.name).append(":")
        if (evaluator.isData(cls)) {
          sb.append(" data")
        }
        if (evaluator.isSealed(cls)) {
          sb.append(" sealed")
        }
        if (evaluator.isCompanion(cls)) {
          sb.append(" companion")
        }
        for (typeParameter in cls.typeParameters) {
          sb.append(" ")
          if (isOutVariance(typeParameter)) {
            sb.append("out ")
          }
          if (isInVariance(typeParameter)) {
            sb.append("in ")
          }
          val parameterName = typeParameter.name ?: "arg"
          sb.append(parameterName.replace('$', '＄'))
        }
        sb.append("\n")
        if (evaluator.isData(cls)) {
          continue
        }
        for (method in cls.methods.sortedBy { it.name }) {
          if (method.isConstructor) {
            continue
          }
          val methodName = method.name.replace('$', '＄')
          sb.append("    method ").append(methodName)
          sb.append("(")
          var first = true
          for (parameter in method.uastParameters) {
            if (first) {
              first = false
            } else {
              sb.append(",")
            }
            if (evaluator.isCrossInline(parameter)) {
              sb.append("crossinline ")
            }
            if (evaluator.isVararg(parameter)) {
              sb.append("vararg ")
            }
            sb.append(parameter.name.replace('$', '＄'))
          }
          sb.append(")")
          sb.append(":")
          if (evaluator.isInline(method)) {
            sb.append(" inline")
          }
          if (evaluator.isNoInline(method)) {
            sb.append(" noinline")
          }
          if (evaluator.isTailRec(method)) {
            sb.append(" tailrec")
          }
          if (evaluator.isSuspend(method)) {
            sb.append(" suspend")
          }
          if (evaluator.isInfix(method)) {
            sb.append(" infix")
          }
          if (evaluator.isInternal(method)) {
            sb.append(" internal")
          }
          if (evaluator.isOperator(method)) {
            sb.append(" operator")
          }
          if (evaluator.isOpen(method)) {
            sb.append(" open")
          }
          if (evaluator.isExpect(method)) {
            sb.append(" expect")
          }
          if (evaluator.isActual(method)) {
            sb.append(" actual")
          }
          if (evaluator.isExternal(method)) {
            sb.append(" external")
          }
          first = true
          for (typeParam in method.typeParameters) {
            if (first) {
              first = false
            } else {
              sb.append(",")
            }

            if (isReified(typeParam)) {
              sb.append(" reified")
            }
            if (isOutVariance(typeParam)) {
              sb.append(" out")
            }
            if (isInVariance(typeParam)) {
              sb.append(" in")
            }
            sb.append(" ")
            sb.append(typeParam.name)
          }

          sb.append("\n")
        }
        for (method in cls.fields.sortedBy { it.name }) {
          sb.append("    field ").append(method.name).append(":")
          if (evaluator.isLateInit(method)) {
            sb.append(" lateinit")
          }
          if (evaluator.isConst(method)) {
            sb.append(" const")
          }
          sb.append("\n")
        }
      }

      // function1 and function2 do not have reified types;
      // the rest do
      assertEquals(
        """
                class Comparator: in T
                    method compare(e1,e2):
                class Data: data
                class List: out E
                class Sealed: sealed
                nested class NamedCompanion: companion
                class Test:
                    method combine(a): infix
                    method f(crossinline body): inline
                    method fromElsewhere(): external
                    method function1(t): inline T
                    method function2(t): inline T
                    method function3(t): inline reified T
                    method function4(t): inline reified T
                    method function5(t): inline reified T
                    method function6(＄this＄function6,t): inline reified T
                    method function7(t): inline reified T
                    method function8(t): inline reified T
                    method function9(t): inline internal reified T
                    method functionA(t): inline reified T
                    method functionB(＄this＄functionB,t): inline reified T
                    method functionC(t): reified T
                    method get(index): operator
                    method isOpen(): open
                    method me(): tailrec
                    method multiarg(vararg arg):
                    method myInternal＄lint_module(): internal
                    method notInlined(): noinline
                    method randomUUID(): actual
                    method randomUUID(): expect
                    method suspendMethod(＄completion): suspend
                    field NamedCompanion:
                    field constant: const
                    field delayed: lateinit
                """
          .trimIndent()
          .trim(),
        sb.toString().trim(),
      )
    }
  }

  fun testKtParameters() {
    // Regression test for
    // https://issuetracker.google.com/134093981
    val source =
      kotlin(
          """
            package test.pkg
            inline class GraphVariables(val set: MutableSet<GraphVariable<*>>) {
                fun <T> variable(name: String, graphType: String, value: T) {
                    this.set.add(GraphVariable(name, graphType, value))
                }
            }
            class GraphVariable<T>(name: String, graphType: String, value: T) {
            }
            """
        )
        .indented()

    check(source) { file ->
      assertEquals(
        """
                package test.pkg

                public final class GraphVariables {
                    public final fun getSet() : java.util.Set<test.pkg.GraphVariable<?>> = UastEmptyExpression
                    public fun variable(@org.jetbrains.annotations.NotNull name: java.lang.String, @org.jetbrains.annotations.NotNull graphType: java.lang.String, value: T) : void {
                        this.set.add(<init>(name, graphType, value))
                    }
                }

                public final class GraphVariable {
                    public fun GraphVariable(@org.jetbrains.annotations.NotNull name: java.lang.String, @org.jetbrains.annotations.NotNull graphType: java.lang.String, value: T) = UastEmptyExpression
                }

                """
          .trimIndent(),
        file.asSourceString().dos2unix(),
      )
    }
  }

  fun testCatchClausesKotlin() {
    // Regression test for
    // https://issuetracker.google.com/140154274
    // and https://youtrack.jetbrains.com/issue/KT-35804
    val source =
      kotlin(
          """
            package test.pkg

            class TryCatchKotlin {
                @java.lang.SuppressWarnings("Something")
                fun catches() {
                    try {
                        catches()
                    } catch (@java.lang.SuppressWarnings("Something") e: Throwable) {
                    }
                }

                fun throws() {
                }
            }
            """
        )
        .indented()

    check(source) { file ->
      assertEquals(
        """
                package test.pkg

                public final class TryCatchKotlin {
                    @java.lang.SuppressWarnings(value = "Something")
                    public final fun catches() : void {
                        try {
                            catches()
                        }
                        catch (@org.jetbrains.annotations.NotNull @java.lang.SuppressWarnings(value = "Something") var e: java.lang.Throwable) {
                        }
                    }
                    public final fun throws() : void {
                    }
                    public fun TryCatchKotlin() = UastEmptyExpression
                }
                """
          .trimIndent()
          .trim(),
        file.asSourceString().dos2unix().trim().replace("\n        \n", "\n"),
      )
    }

    // Java is OK:
    val javaSource =
      java(
          """
            public class TryCatchJava {
                @SuppressWarnings("Something")
                public void test() {
                    try {
                        canThrow();
                    } catch(@SuppressWarnings("Something") Throwable t) {
                    }
                }
                public void canThrow() {
                }
            }
            """
        )
        .indented()

    check(javaSource) { file ->
      assertEquals(
        // The annotations work in Java, as checked by
        // ApiDetectorTest#testConditionalAroundExceptionSuppress
        // However, in pretty printing catch clause parameters are not
        // visited, as described in https://youtrack.jetbrains.com/issue/KT-35803
        """
                public class TryCatchJava {
                    @java.lang.SuppressWarnings(null = "Something")
                    public fun test() : void {
                        try {
                            canThrow()
                        }
                        catch (@java.lang.SuppressWarnings(null = "Something") var t: java.lang.Throwable) {
                        }
                    }
                    public fun canThrow() : void {
                    }
                }
                """
          .trimIndent()
          .trim(),
        file.asSourceString().dos2unix().trim().replace("\n        \n", "\n"),
      )
    }
  }

  fun testSamAst() { // See KT-28272
    val source =
      kotlin(
          """
            //@file:Suppress("RedundantSamConstructor", "MoveLambdaOutsideParentheses", "unused", "UNUSED_VARIABLE")

            package test.pkg

            fun test1() {
                val thread1 = Thread({ println("hello") })
            }

            fun test2() {
                val thread2 = Thread(Runnable { println("hello") })
            }
            """
        )
        .indented()

    check(source) { file ->
      assertEquals(
        """
                UFile (package = test.pkg) [package test.pkg...]
                  UClass (name = TestKt) [public final class TestKt {...}]
                    UMethod (name = test1) [public static final fun test1() : void {...}] : PsiType:void
                      UBlockExpression [{...}] : PsiType:void
                        UDeclarationsExpression [var thread1: java.lang.Thread = <init>({ ...})]
                          ULocalVariable (name = thread1) [var thread1: java.lang.Thread = <init>({ ...})] : PsiType:Thread
                            UCallExpression (kind = UastCallKind(name='constructor_call'), argCount = 1)) [<init>({ ...})] : PsiType:Thread
                              UIdentifier (Identifier (Thread)) [UIdentifier (Identifier (Thread))]
                              USimpleNameReferenceExpression (identifier = <init>, resolvesTo = PsiClass: Thread) [<init>] : PsiType:Thread
                              ULambdaExpression [{ ...}] : PsiType:Function0<? extends Unit>
                                UBlockExpression [{...}]
                                  UReturnExpression [return println("hello")]
                                    UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println("hello")] : PsiType:Unit
                                      UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                      USimpleNameReferenceExpression (identifier = println, resolvesTo = null) [println] : PsiType:Unit
                                      ULiteralExpression (value = "hello") ["hello"] : PsiType:String
                    UMethod (name = test2) [public static final fun test2() : void {...}] : PsiType:void
                      UBlockExpression [{...}] : PsiType:void
                        UDeclarationsExpression [var thread2: java.lang.Thread = <init>(Runnable({ ...}))]
                          ULocalVariable (name = thread2) [var thread2: java.lang.Thread = <init>(Runnable({ ...}))] : PsiType:Thread
                            UCallExpression (kind = UastCallKind(name='constructor_call'), argCount = 1)) [<init>(Runnable({ ...}))] : PsiType:Thread
                              UIdentifier (Identifier (Thread)) [UIdentifier (Identifier (Thread))]
                              USimpleNameReferenceExpression (identifier = <init>, resolvesTo = PsiClass: Thread) [<init>] : PsiType:Thread
                              UCallExpression (kind = UastCallKind(name='constructor_call'), argCount = 1)) [Runnable({ ...})] : PsiType:Runnable
                                UIdentifier (Identifier (Runnable)) [UIdentifier (Identifier (Runnable))]
                                USimpleNameReferenceExpression (identifier = Runnable, resolvesTo = PsiClass: Runnable) [Runnable] : PsiType:Runnable
                                ULambdaExpression [{ ...}] : PsiType:Function0<? extends Unit>
                                  UBlockExpression [{...}]
                                    UReturnExpression [return println("hello")]
                                      UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println("hello")] : PsiType:Unit
                                        UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                        USimpleNameReferenceExpression (identifier = println, resolvesTo = null) [println] : PsiType:Unit
                                        ULiteralExpression (value = "hello") ["hello"] : PsiType:String
                """
          .trimIndent(),
        file.asLogTypes(indent = "  ").trim(),
      )

      try {
        file.accept(
          object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
              val resolved = node.resolve()
              if (resolved == null) {
                throw IllegalStateException("Could not resolve this call: ${node.asSourceString()}")
              }
              return super.visitCallExpression(node)
            }
          }
        )
        fail("Expected unresolved error: see KT-28272")
      } catch (failure: IllegalStateException) {
        assertEquals(
          "Could not resolve this call: Runnable({ \n    return println(\"hello\")\n})",
          failure.message,
        )
      }
    }
  }

  fun testJava11() {
    val source =
      java(
          """
            package test.pkg;
            import java.util.function.IntFunction;
            public class Java11Test {
                // Private methods in interfaces
                public interface MyInterface {
                    private String getHello() {
                        return "hello";
                    }
                    default String getMessage() {
                        return getHello();
                    }
                }
                // Var keyword
                public void varStuff() {
                    var name = "Name";
                    var meaning = 42;
                    for (var line : name.split("\n")) {
                        System.out.println(line);
                    }
                }
                // Lambda vars
                IntFunction<Integer> doubler = (var x) -> x * 2;
            }
            """
        )
        .indented()

    check(
      source,
      javaLanguageLevel = LanguageLevel.JDK_11,
      android = false,
      check = { file ->
        assertEquals(
          """
                    UFile (package = test.pkg) [package test.pkg...]
                      UImportStatement (isOnDemand = false) [import java.util.function.IntFunction]
                      UClass (name = Java11Test) [public class Java11Test {...}]
                        UField (name = doubler) [var doubler: java.util.function.IntFunction<java.lang.Integer> = { var x: int ->...}] : PsiType:IntFunction<Integer>
                          ULambdaExpression [{ var x: int ->...}] : PsiType:<lambda expression>
                            UParameter (name = x) [var x: int] : PsiType:int
                            UBlockExpression [{...}]
                              UReturnExpression [return x * 2]
                                UBinaryExpression (operator = *) [x * 2] : PsiType:int
                                  USimpleNameReferenceExpression (identifier = x) [x] : PsiType:int
                                  ULiteralExpression (value = 2) [2] : PsiType:int
                        UMethod (name = varStuff) [public fun varStuff() : void {...}] : PsiType:void
                          UBlockExpression [{...}]
                            UDeclarationsExpression [var name: java.lang.String = "Name"]
                              ULocalVariable (name = name) [var name: java.lang.String = "Name"] : PsiType:String
                                ULiteralExpression (value = "Name") ["Name"] : PsiType:String
                            UDeclarationsExpression [var meaning: int = 42]
                              ULocalVariable (name = meaning) [var meaning: int = 42] : PsiType:int
                                ULiteralExpression (value = 42) [42] : PsiType:int
                            UForEachExpression [for (line : name.split("\n")) {...}]
                              UQualifiedReferenceExpression [name.split("\n")] : PsiType:String[]
                                USimpleNameReferenceExpression (identifier = name) [name] : PsiType:String
                                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [split("\n")] : PsiType:String[]
                                  UIdentifier (Identifier (split)) [UIdentifier (Identifier (split))]
                                  ULiteralExpression (value = "\n") ["\n"] : PsiType:String
                              UBlockExpression [{...}]
                                UQualifiedReferenceExpression [System.out.println(line)] : PsiType:void
                                  UQualifiedReferenceExpression [System.out] : PsiType:PrintStream
                                    USimpleNameReferenceExpression (identifier = System) [System]
                                    USimpleNameReferenceExpression (identifier = out) [out]
                                  UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println(line)] : PsiType:void
                                    UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                    USimpleNameReferenceExpression (identifier = line) [line] : PsiType:String
                        UClass (name = MyInterface) [public static abstract interface MyInterface {...}]
                          UMethod (name = getHello) [private fun getHello() : java.lang.String {...}] : PsiType:String
                            UBlockExpression [{...}]
                              UReturnExpression [return "hello"]
                                ULiteralExpression (value = "hello") ["hello"] : PsiType:String
                          UMethod (name = getMessage) [public default fun getMessage() : java.lang.String {...}] : PsiType:String
                            UBlockExpression [{...}]
                              UReturnExpression [return getHello()]
                                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 0)) [getHello()] : PsiType:String
                                  UIdentifier (Identifier (getHello)) [UIdentifier (Identifier (getHello))]
                    """
            .trimIndent(),
          file.asLogTypes(indent = "  ").trim(),
        )

        // Make sure that all calls correctly resolve
        file.accept(
          object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
              val resolved = node.resolve()
              assertNotNull(resolved)
              return super.visitCallExpression(node)
            }
          }
        )
      },
    )
  }

  fun test125138962() {
    // Regression test for https://issuetracker.google.com/125138962
    val source =
      kotlin(
          """
            package test.pkg

            class SimpleClass() {
                var foo: Int
                init {
                    @Suppress("foo")
                    foo = android.R.layout.activity_list_item
                }
            }
            """
        )
        .indented()

    check(
      source,
      check = { file ->
        assertEquals(
          """
                package test.pkg

                public final class SimpleClass {
                    @org.jetbrains.annotations.NotNull private var foo: int
                    public final fun getFoo() : int = UastEmptyExpression
                    public final fun setFoo(<set-?>: int) : void = UastEmptyExpression
                    public fun SimpleClass() {
                        {
                            foo = android.R.layout.activity_list_item
                        }
                    }
                }
                    """
            .trimIndent(),
          file.asSourceString().dos2unix().trim(),
        )
      },
    )
  }

  fun testIdea234484() {
    // Regression test for https://youtrack.jetbrains.com/issue/KT-37200
    val source =
      kotlin(
          """
            package test.pkg

            inline fun <reified F> ViewModelContext.viewModelFactory(): F {
                return activity as? F ?: throw IllegalStateException("Boo!")
            }

            sealed class ViewModelContext {
                abstract val activity: Number
            }
            """
        )
        .indented()

    check(
      source,
      check = { file ->
        val newFile = file.sourcePsi.toUElement()
        newFile?.accept(
          object : AbstractUastVisitor() {
            override fun visitLocalVariable(node: ULocalVariable): Boolean {
              val initializerType = node.uastInitializer?.getExpressionType()
              val interfaceType = node.type
              @Suppress("UNUSED_VARIABLE")
              val equals = initializerType == interfaceType // Stack overflow!

              return super.visitLocalVariable(node)
            }
          }
        )
      },
    )
  }

  fun testKt27935() {
    // Regression test for https://youtrack.jetbrains.com/issue/KT-27935
    val source =
      kotlin(
          """
            package test.pkg

            typealias IndexedDistance = Pair<Int, Double>
            typealias Window = (Array<IndexedDistance>) -> List<IndexedDistance>
            class Core(
                    val window: Window
            )
            """
        )
        .indented()

    check(source, check = {})
  }

  fun testKt36275() {
    // Regression test for https://youtrack.jetbrains.com/issue/KT-36275
    val source =
      kotlin(
          """
            package test.pkg

             fun foo() {
                fun bar() {
                }
                bar()
            }
            """
        )
        .indented()

    check(
      source,
      check = { file ->
        // Make sure that all calls correctly resolve
        file.accept(
          object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
              val resolved = node.resolve()
              assertNotNull(resolved)
              return super.visitCallExpression(node)
            }
          }
        )
      },
    )
  }

  fun testKt34187() {
    // Regression test for https://youtrack.jetbrains.com/issue/KT-34187
    val source =
      kotlin(
          """
            package test.pkg

            class Publisher<T> { }

            fun usedAsArrayElement() {
                val a = arrayOfNulls<Publisher<String>>(10)
                a[0] = Publisher()
            }
            """
        )
        .indented()

    check(
      source,
      check = { file ->
        // Make sure that all calls correctly resolve
        file.accept(
          object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
              if (node.isAssignment()) {
                val type = node.leftOperand.getExpressionType()
                assertNotNull("type of ${node.leftOperand.sourcePsi?.text} is null", type)
              }
              return super.visitBinaryExpression(node)
            }
          }
        )
      },
    )
  }

  fun testKt45676() {
    // Regression test for https://youtrack.jetbrains.com/issue/KT-45676,
    // in which backing field annotations were missing their attribute values.
    val source =
      kotlin(
          """
            @Target(AnnotationTarget.FIELD)
            annotation class MyFieldAnnotation(val value: String)

            @MyFieldAnnotation("SomeStringValue")
            var myProperty = 0
            """
        )
        .indented()

    check(source) { file ->
      assertEquals(
        """
                UFile (package = ) [public final class MyFieldAnnotationKt {...]
                  UClass (name = MyFieldAnnotationKt) [public final class MyFieldAnnotationKt {...}]
                    UField (name = myProperty) [@org.jetbrains.annotations.NotNull @MyFieldAnnotation(value = "SomeStringValue") private static var myProperty: int = 0] : PsiType:int
                      UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                      UAnnotation (fqName = MyFieldAnnotation) [@MyFieldAnnotation(value = "SomeStringValue")]
                        UNamedExpression (name = value) [value = "SomeStringValue"]
                          ULiteralExpression (value = "SomeStringValue") ["SomeStringValue"] : PsiType:String
                      ULiteralExpression (value = 0) [0] : PsiType:int
                    UMethod (name = getMyProperty) [public static final fun getMyProperty() : int = UastEmptyExpression] : PsiType:int
                    UMethod (name = setMyProperty) [public static final fun setMyProperty(<set-?>: int) : void = UastEmptyExpression] : PsiType:void
                      UParameter (name = <set-?>) [var <set-?>: int] : PsiType:int
                  UClass (name = MyFieldAnnotation) [public abstract annotation MyFieldAnnotation {...}]
                    UAnnotation (fqName = kotlin.annotation.Target) [@kotlin.annotation.Target(allowedTargets = AnnotationTarget.FIELD)]
                      UNamedExpression (name = allowedTargets) [allowedTargets = AnnotationTarget.FIELD]
                        UQualifiedReferenceExpression [AnnotationTarget.FIELD] : PsiType:AnnotationTarget
                          USimpleNameReferenceExpression (identifier = AnnotationTarget) [AnnotationTarget]
                          USimpleNameReferenceExpression (identifier = FIELD) [FIELD] : PsiType:AnnotationTarget
                    UAnnotationMethod (name = value) [public abstract fun value() : java.lang.String = UastEmptyExpression] : PsiType:String
                """
          .trimIndent(),
        file.asLogTypes(indent = "  ").trim(),
      )
    }
  }

  fun testResolveLambdaVar() { // See KT-46628
    val source =
      kotlin(
          """
            package test.pkg

            fun test1(s: String?) {
                s?.let {
                    println(it)
                }
            }

            fun test2(s: String?) {
                s?.let { it ->
                    println(it)
                }
            }

            fun test3(s: String?) {
                s?.let { t ->
                    println(t)
                }
            }
            """
        )
        .indented()

    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val argument = node.valueArguments.firstOrNull()
            (argument as? UReferenceExpression)?.let {
              val resolved = argument.resolve()
              assertNotNull(
                "Couldn't resolve `${argument.sourcePsi?.text ?: argument.asSourceString()}`",
                resolved,
              )
            }

            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun testSamConstructorCallKind() {
    val source = kotlin("""
            val r = java.lang.Runnable {  }
            """).indented()

    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            assertEquals("Runnable", node.methodName)
            assertEquals(UastCallKind.CONSTRUCTOR_CALL, node.kind)

            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun testCommentOnDataClass() {
    val source =
      kotlin(
          """
            // Single-line comment on data class
            data class DataClass(val id: String)
            """
        )
        .indented()

    check(source) { file ->
      val commentMap: MutableMap<String, MutableSet<UElement>> = mutableMapOf()
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitElement(node: UElement): Boolean {
            node.comments.forEach {
              val boundUElement = commentMap.computeIfAbsent(it.text) { mutableSetOf() }
              boundUElement.add(node)
            }
            return super.visitElement(node)
          }
        }
      )
      assertTrue(commentMap.keys.isNotEmpty())
      commentMap.forEach { (_, uElementSet) -> assertEquals(1, uElementSet.size) }
    }
  }

  fun testTextOfModifierListOfFunction() {
    val source =
      kotlin(
          """
            annotation class MyComposable
            class Test {
                @MyComposable
                fun foo() {
                }
            }
            """
        )
        .indented()

    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitMethod(node: UMethod): Boolean {
            if (node.isConstructor) return super.visitMethod(node)

            val javaPsiModifierList = node.modifierList
            assertTrue(javaPsiModifierList.textOffset > 0)
            assertFalse(javaPsiModifierList.textRange.isEmpty)
            assertEquals(javaPsiModifierList.textOffset, javaPsiModifierList.textRange.startOffset)

            val sourceModifierList = (node.sourcePsi as? KtModifierListOwner)?.modifierList
            assertNotNull(sourceModifierList)
            sourceModifierList!!
            assertTrue(sourceModifierList.textOffset > 0)
            assertFalse(sourceModifierList.textRange.isEmpty)
            assertEquals(sourceModifierList.textOffset, sourceModifierList.textRange.startOffset)

            assertEquals(sourceModifierList.text, javaPsiModifierList.text)
            assertEquals("@MyComposable", sourceModifierList.text)
            return super.visitMethod(node)
          }
        }
      )
    }
  }

  fun testTextOfModifierListOfPropertyAccessor() {
    val source =
      kotlin(
          """
            annotation class MyComposable
            object Test {
                var foo3: Boolean
                    @MyComposable
                    get() = LocalFoo.current

                class LocalFoo {
                  companion object {
                      const val current: Boolean = true
                  }
                }
            }
            """
        )
        .indented()

    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitMethod(node: UMethod): Boolean {
            if (node.sourcePsi !is KtPropertyAccessor) return super.visitMethod(node)

            val javaPsiModifierList = node.modifierList
            assertTrue(javaPsiModifierList.textOffset > 0)
            assertFalse(javaPsiModifierList.textRange.isEmpty)
            assertEquals(javaPsiModifierList.textOffset, javaPsiModifierList.textRange.startOffset)

            val sourceModifierList = (node.sourcePsi as? KtModifierListOwner)?.modifierList
            assertNotNull(sourceModifierList)
            sourceModifierList!!
            assertTrue(sourceModifierList.textOffset > 0)
            assertFalse(sourceModifierList.textRange.isEmpty)
            assertEquals(sourceModifierList.textOffset, sourceModifierList.textRange.startOffset)

            assertEquals(sourceModifierList.text, javaPsiModifierList.text)
            assertEquals("@MyComposable", sourceModifierList.text)
            return super.visitMethod(node)
          }
        }
      )
    }
  }

  fun testConstructorReferences() {
    val source =
      kotlin(
          """
            class Foo(val p : Int)
            class Boo
            data class Bar(val isEnabled: Boolean = true)

            fun test() {
              val x = ::Foo
              x(42)
              val y = ::Boo
              y()
              val z = ::Bar
              z(false)
            }
            """
        )
        .indented()

    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitMethod(node: UMethod): Boolean {
            if (!node.isConstructor) return super.visitMethod(node)

            // Bar should have default arg constructor as per default value in parameter
            // E.g., Bar() // == Bar(true)
            // K2 creates that constructor properly, whereas K1 missed that.
            val expectedTypes = if (useFirUast()) listOf("Boo", "Bar") else listOf("Boo")
            assertTrue(
              node.sourcePsi is KtConstructor<*> ||
                (node.sourcePsi is KtClassOrObject &&
                  node.parameterList.isEmpty &&
                  node.name in expectedTypes)
            )
            return super.visitMethod(node)
          }

          override fun visitCallableReferenceExpression(
            node: UCallableReferenceExpression
          ): Boolean {
            val resolved = node.resolve()
            assertNotNull(resolved)

            // If a class doesn't have its own primary constructor,
            // the reference will be resolved to the class itself.
            assertTrue(
              (resolved as? PsiMethod)?.isConstructor == true ||
                (resolved as? PsiClass)?.constructors?.single()?.isPhysical == false
            )

            return super.visitCallableReferenceExpression(node)
          }
        }
      )
    }
  }

  fun test263887242() {
    val source =
      kotlin(
          """
            inline fun <T> remember(calc: () -> T): T = calc()

            fun test() {
                val x = remember { UnknownClass() }
                val y = remember { 42 }
                val z = remember {
                    val local = UnknownClass()
                    42
                }
            }
            """
        )
        .indented()

    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            if (node.methodName != "remember") return super.visitCallExpression(node)

            // Due to coercion-to-Unit (in FE1.0), a type error is hidden, and Unit is returned.
            // In contrast, we can see that type error in K2.
            val callExpressionType = node.getExpressionType()
            val coerced = if (useFirUast()) "<ErrorType>" else "kotlin.Unit"
            assertTrue(callExpressionType?.canonicalText in listOf(coerced, "int"))

            // We can go deeper into the last expression of the lambda argument.
            val sourcePsi = node.sourcePsi
            if (sourcePsi is KtCallExpression) {
              val tailLambda = sourcePsi.valueArguments.lastOrNull() as? KtLambdaArgument
              val lambda = tailLambda?.getLambdaExpression()
              val lastExp = lambda?.bodyExpression?.statements?.lastOrNull()
              val lastExpType =
                lastExp?.let { it.toUElementOfType<UExpression>()?.getExpressionType() }
              // Since unresolved, the expression type will be actually `null`.
              val isReallyUnit =
                callExpressionType?.canonicalText == "kotlin.Unit" &&
                  callExpressionType == lastExpType
              assertFalse(isReallyUnit)
            }

            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun testKT59564() {
    // Regression test from KT-59564
    val source =
      kotlin(
          """
            fun test(a: Int, b: Int) {
                for (i in a..<b step 1) {
                    println(i)
                }
            }
            """
        )
        .indented()

    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitForEachExpression(node: UForEachExpression): Boolean {
            when (val exp = node.iteratedValue.skipParenthesizedExprDown()) {
              is UBinaryExpression -> {
                assertEquals("kotlin.ranges.IntProgression", exp.getExpressionType()?.canonicalText)
                assertEquals(
                  "kotlin.ranges.IntRange",
                  exp.leftOperand.getExpressionType()?.canonicalText,
                )
              }
            }

            return super.visitForEachExpression(node)
          }
        }
      )
    }
  }

  fun testFindAnnotationOnObjectFunWithJvmStatic() {
    // Regression test from b/296891200
    val source =
      kotlin(
          """
            object ObjectModule {
              @JvmStatic
              fun provideFoo(): String {
                return "Foo"
              }
            }
        """
        )
        .indented()

    val jvmStatic = "kotlin.jvm.JvmStatic"

    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitMethod(node: UMethod): Boolean {
            if (node.isConstructor) return super.visitMethod(node)

            assertTrue(node.hasAnnotation(jvmStatic))
            // Not intuitive...
            // TODO: https://youtrack.jetbrains.com/issue/KTIJ-26803
            assertNull(node.findAnnotation(jvmStatic))
            // Workaround
            val findAnnotation =
              node.findAnnotation(jvmStatic)?.javaPsi
                ?: node.javaPsi.modifierList.findAnnotation(jvmStatic)
            assertNotNull(findAnnotation)
            assertEquals(jvmStatic, findAnnotation!!.qualifiedName)

            return super.visitMethod(node)
          }
        }
      )
    }
  }

  fun testInheritedMethodsInJava() {
    // from b/296638723
    val source =
      java(
        """
          class Parent {
            void foo() {}
          }

          class Test extends Parent {
            void callNoQualifier() {
              foo();
            }

            void callWithThis() {
              this.foo();
            }

            void callWithSuper() {
              super.foo();
            }
          }
        """
      )

    var count = 0
    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val callee = node.resolve()
            assertNotNull(callee)
            val containingClass = callee!!.containingClass
            assertNotNull(containingClass)

            val id = "${containingClass!!.name}#${callee.name}"
            assertEquals("Parent#foo", id)

            val uMethod = callee.toUElement(UMethod::class.java)
            assertNotNull(uMethod)
            assertEquals(callee, uMethod!!.javaPsi)

            count++

            return super.visitCallExpression(node)
          }
        }
      )
    }
    assertEquals(3, count)
  }

  fun testImplicitLambdaParameterInTest() {
    // Example from b/302708854
    val testFiles =
      arrayOf(
        kotlin(
            "src/test/pkg/sub/MyClass.kt",
            """
            package pkg.sub

            class MyClass {
              lateinit var manager: Manager
                private set

              init {
                Manager().use {
                  manager = it
                }
              }
            }
          """,
          )
          .indented(),
        kotlin(
            "src/main/pkg/sub/Manager.kt",
            """
            package pkg.sub
            import java.io.Closeable
            class Manager : Closeable
          """,
          )
          .indented(),
      )

    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitSimpleNameReferenceExpression(
            node: USimpleNameReferenceExpression
          ): Boolean {
            if (node.identifier != "it") return super.visitSimpleNameReferenceExpression(node)

            // No source for implicit lambda parameter.
            // Expect to be resolved to fake PsiParameter used inside ULambdaExpression
            val resolved = node.resolve() as? PsiParameter
            assertEquals("it", resolved?.name)

            return super.visitSimpleNameReferenceExpression(node)
          }
        }
      )
    }
  }

  fun testResolveToInlineInLibrary() {
    val testFiles =
      arrayOf(
        bytecode(
          "libs/lib1.jar",
          kotlin(
            "src/test/Mocking.kt",
            """
                    package test

                    inline fun <reified T : Any> mock(): T = TODO()

                    object Mock {
                      inline fun <reified T : Any> mock(): T = TODO()
                    }
                    """,
          ),
          0x9c8bcf60,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijg4uViEOL0rXTOSSwu9i7hEuRiKUkt
                LgEK5SdnZ+ale5coMWgxAAD10MNROgAAAA==
                """,
          """
                test/Mock.class:
                H4sIAAAAAAAA/2VRS08TURT+7u1rOi22IEIpPqFqi8oAcSWVBAHDmFIT25AQ
                VrftUG87nTEzt43Lrvgh/gPigkQSbXDnjzKemVZ8sDnv853znfPj55evAJ5j
                jSGpLF8ZB26zmwBjyHbEQBi2cNrG20bHaqoEIgzxsnSk2mKIFEuHacQQ1xFF
                giGq3kufIVW5QtmkYI80w2yxVPkfjbKL5fqL6/GtYqlep2y+6ypbOkbVVWbv
                g231LEdZrT3Pc70EbjDsFv/qrSlPOu1NszJp6gx6hqR6zxG2sWudiL6tdlzH
                V16/qVzvQHhdy9scM8jqyGCa+Bdk4aQw3piZDFq5aYdkdfCAoWZWa/Xt6s5e
                GvPQkxTMMSxXXK9tdCzV8IR0fEM4jquEkjQr2Lzat23iMv17rwNLiZZQgmK8
                N4jQ6VkgkoEAjaXR/KMMPHoIb60TzdEwrfMc13l2NNS5FtW+n/LcaLjB19ir
                hMYvP8V5lr/JZyN5vh9d0rXRMBvNsRVK71+eamE6FmBtsHBCnUEPvkPnWu0q
                esK7vqNkzzKdgfRlw7a2/zCgB+64LYshU5GOVe33GpZXF1TDMFNxm8I+FJ4M
                /EkwWZNtR6i+R7Zec/te03otg8TCZMjhtRFYpztGQ/ILwVlJl8iLk86TjlA2
                Fnor5BkBAdKxlXNoZ2RwPJkUB/IpyfS4AEmCQnYaKYrwsHmbopz0VKR8gcwR
                i7JzzHz7B4TeHILMkcWp9SZmQ7ipMRyehdVT0HDraqf5EBRIXYAfnWPuMxbO
                wgDHaiiLtDbwksoXaYPbx4iYuGPirol7uE8mHphYwvIxmI8CHh4j7kP38cgP
                jJSPxz7SvwCvcXQwpwMAAA==
                """,
          """
                test/MockingKt.class:
                H4sIAAAAAAAA/2VSXWsTQRQ9d5PmY5u2aa3apH7WCIkPbiuCYEpBWqWLSQUT
                ApKnSTINk+zOyuxs8DFP/hD/hKCgoY/+KPFujCD6MHfOvefcw53L/Pj55RuA
                p3hI2LQytl47Gk6VHr+2eRChPBEz4QVCj703g4kccjVDyIYsIuzWG61/+SZh
                /7j7/P/6Sb3R7TJbnUY2UNq7iKwfvg9kKLWVo5fGRCaPAiF3rLSyJ4Sz+l8m
                HWt4qKbfWnVPZqGnuNFoEXhn8lIkgT2NdGxNMrSRaQszlabZ6JXgYt1FESVC
                saZql7Xfo5NP2P5j1pZWjIQVPJ0TzjK8D0pDMQ1g7TQFDpMfVIoOGY2OCN5i
                7rpOwXGdMqPFvFrlu+qc04FbWMzLtEePnEPn/Opj4epTziln0rYnbNel1NVd
                Lfrx1PJCT6ORJGy1lJYXSTiQpisGAVd2WtFQBD1hVJqvisWOGmthE8PY7USJ
                GcpXKiUqbxNtVSh7KlasfKF1ZIVVvBccwUEWyweVK1hDjvO7nD1jzPNgI9P8
                iuI7ytJnbHxP34t7HHNMpo33GZeWeB2b2OLsYKnJ83mwRHdQW9rxn2HL7T4y
                PnZ8XPOxi+s+buCmjz1U+qAYVez3kY2xFuNWjNsxcr8Ai+me74gCAAA=
                """,
        ),
        kotlin(
          """
                import test.Mock
                import test.mock as tMock

                class MyClass

                fun test(): Boolean {
                  val instance1 = Mock.mock<MyClass>()
                  val instance2 = tMock<MyClass>()
                  return instance1 == instance2
                }
                """
        ),
      )

    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          var first: Boolean = true

          override fun visitCallExpression(node: UCallExpression): Boolean {
            val resolved = node.resolve()
            assertNotNull(resolved)
            assertEquals("mock", resolved!!.name)
            if (first) {
              assertEquals("Mock", resolved.containingClass?.name)
              first = false
            } else {
              assertEquals("MockingKt", resolved.containingClass?.name)
            }

            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun testAbstractDelegateToInterface() {
    val testFiles =
      arrayOf(
        kotlin(
          "src/main/impl/DynamicFeatureExtension.kt",
          """
            package main.impl

            abstract class DynamicFeatureExtension(
                private val publicExtensionImpl: DynamicFeatureExtensionImpl
            ) : InternalDynamicFeatureExtension by publicExtensionImpl {
                // fun sandbox(action: Action<Sandbox>) // delegate
            }
          """,
        ),
        bytecode(
          "libs/lib1.jar",
          kotlin(
              """
               package pkg.api

               interface Action<T> {
                 fun execute(t: T)
               }
            """
            )
            .indented(),
          0x2326f350,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBihQYtBiAAD1Iry9GAAAAA==
                """,
          """
                pkg/api/Action.class:
                H4sIAAAAAAAA/2VQzU7CQBic3UJbqmJRUEDPBj1YJB6MGiMxMWIwJkK4cFpq
                JUtLS+iWcOyz+BgeTOPRhzJuqxflMt/M5Pv//Hp7B3CKfYLizB1bbMatti14
                4GsgBI3L/nl3whbM8pg/th5HE8cWF1erFoH539OQI9CcpWNHwiGoNFarDgcE
                aqPfz0ip6wbC47714Aj2zASTTel0ocj9SAqFFEBAXOkveaqakj2fENwkcdmg
                VWoksUFNCVmkBtWp/lJN4qOcnsQmadEmva+YSp02cy3VzNfpWRLfHXy8qtRU
                01YteUb37xvkEqRPsrmCoPBjHrsp7/Gxz0Q0l8cZvSCa284t96SoPUW+4FNn
                wEM+8py27weCpWVhOgR5ZHfI56iQT0ZNKgodyi9TUM9iFXsyXsuMgqwxhlA6
                WOtgvYMNFCXFZgcmSkOQEFvYHkILUQ5RCbETQs1wN4T2DWW/IWXjAQAA
                """,
        ),
        bytecode(
          "libs/lib2.jar",
          kotlin(
              "src/my/SandBox.kt",
              """
              package my

              interface Sandbox
            """,
            )
            .indented(),
          0x3dae47f7,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBihQYtBiAAD1Iry9GAAAAA==
                """,
          """
                my/Sandbox.class:
                H4sIAAAAAAAA/0WNQUvDQBCF32y0adeqqVqof8K0xZunehACFUHBS06bZpVt
                kl0w2xJv/V0epGd/lDirB2fgzXsz8M3X98cngGuMCbJ5T5+ULQvXxSBCslZb
                ldbKvqYPxVqvfIyIMFpWztfGpvfaq1J5dUMQzTZiCgUZBAGBKt53JqQpu3JG
                GO93fSkmQoqE3ctkv5uLKYXjnDBc/r9nJBNkSLeuu6p8CG7zttJ3ptaEy8eN
                9abRz6Y1Ra0X1jqvvHG27TENB/grgfNfPcMFzxkjD7l7OaIMcYZ+hgEkWxxl
                GOI4B7U4wWkO0SJpMfoBSLYswx0BAAA=
                """,
        ),
        // To reproduce b/314320270, remove this dependency such that
        // `DynamicFeatureExtension` as a super type of `InternalDynamicFeatureExtension`
        // would be another one in the same package, resulting in circular super type.
        bytecode(
          "libs/api.jar",
          kotlin(
              "src/main/api/DynamicFeatureExtension.kt",
              """
              package main.api

              import my.Sandbox

              interface DynamicFeatureExtension {
                val sandbox: Sandbox
                fun sandbox(action: Sandbox.() -> Unit)
              }
            """,
            )
            .indented(),
          0x7e98aaf4,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBihQYtBiAAD1Iry9GAAAAA==
                """,
          """
                main/api/DynamicFeatureExtension.class:
                H4sIAAAAAAAA/4VRUW8SQRD+doG746z1StVSqi1tjWkf9CjxrUZjokQMVlO0
                MeFpgS054PYadiHwxm/xwR/hgyF99EcZ567QWo1pspmd+fabb2Znfv76/gPA
                M+wwFEMRKF+cBf7riRJh0KpIYYYD+WZspNJBpGwwBq8rRsLvC9XxPzS7smVs
                pBjcjjR1odrNaMywvLdfCyf+PD5k2K1Fg47flaY5oBLaF0pFRhiS1P5RZI6G
                /T6xbL0Q2N2r9SLTp266o9A/HarWBbcy9w4O908YXt7Eev7kzzYW5M8qMIcv
                EoWVBfZeGtEWRlAXPBylaCIsNtnYgIH1CB8HcVQir33AEMymRZfnucu92dSl
                k/hOHPPLMM+d0/xsWuYl9m7b4wVessprnl1YzaVzvJRKrFVyzr9a3Mm+fXyc
                SzipL+ff0oRZhbST9jJxwTLDTu2m9VDz1OvS9dFbIpkFQ27x16shMmwuwEuR
                xeunyZkkQuE/tZ72DMPG8VCZIJRVNQp00OzLV1d7ZcjWg45Kshge/c38KAYi
                lEYOrqW49Wg4aMlK0Kec9XnOyT/aFo0E6Xg14GmGDCz690OK4tsGCHOQJc5m
                zICLLbptdvFIQDGxD7BNd5XQWySx1ECqittVLFdxBx65WKkih9UGmMZd3GvA
                1bivsabhaOQ1MhpWEq5rFDQ2fgNSA6T+SwMAAA==
                """,
        ),
        kotlin(
          "src/main/test/InternalTestExtension.kt",
          """
            package main.test

            interface InternalTestExtension
          """,
        ),
        kotlin(
          "src/main/impl/InternalDynamicFeatureExtension.kt",
          """
            package main.impl

            import pkg.api.Action
            import my.Sandbox
            import main.api.DynamicFeatureExtension
            import main.test.InternalTestExtension

            interface InternalDynamicFeatureExtension : DynamicFeatureExtension, InternalTestExtension {
              fun sandbox(action: Action<Sandbox>)
            }
          """,
        ),
        kotlin(
          "src/main/impl/DynamicFeatureExtensionImpl.kt",
          """
            package main.impl

            abstract class DynamicFeatureExtensionImpl : InternalDynamicFeatureExtension {
              // fun sandbox(action: Action<Sandbox>) // abstract
            }
          """,
        ),
      )
    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitClass(node: UClass): Boolean {
            val delegate = node.javaPsi.methods.find { it.name == "sandbox" }

            // If `DynamicFeatureExtension` in api package is missing,
            // circular super type will bother the compiler frontend.
            // That is, this delegation won't exist: change to assertNull.
            // However, K1 gracefully ignored internal errors, whereas
            // K2 raised ISE that hid the true root cause.
            assertNotNull(delegate)

            return super.visitClass(node)
          }
        }
      )
    }
  }

  fun testRetrievingPsiOfLocalFun() {
    val source =
      kotlin(
          """
          fun target(i: Int) {
            fun localFun() {
              println("hello")
            }
            localFun()
          }
        """
        )
        .indented()

    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val resolved = node.resolve() ?: return super.visitCallExpression(node)
            if (resolved.name == "println") {
              // Ugh... not this one.
              return super.visitCallExpression(node)
            }
            val sourcePsi = node.sourcePsi as? KtElement ?: return super.visitCallExpression(node)
            analyze(sourcePsi) {
              // from AnalysisApiLintUtils.kt
              // val functionSymbol = getFunctionLikeSymbol(sourcePsi)
              val callInfo = sourcePsi.resolveCall() ?: return super.visitCallExpression(node)
              val functionSymbol =
                callInfo.singleFunctionCallOrNull()?.symbol
                  ?: return super.visitCallExpression(node)
              val psi = functionSymbol.psi
              assertEquals("fun localFun() {\n    println(\"hello\")\n  }", psi?.text)
            }

            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun testIncorrectImplicitReturnInLambda() {
    val testFiles =
      arrayOf(
        kotlin(
            """
          import android.content.Context
          import android.widget.Toast
          import kotlinx.coroutines.CompletableDeferred
          import kotlinx.coroutines.async
          import kotlinx.coroutines.coroutineScope
          import org.junit.Assert.assertThrows
          //import org.junit.function.ThrowingRunnable
          import my.junit.function.ThrowingRunnable

          fun test(c: Context, r: Int, d: Int) {
            with(Toast.makeText(c, r, d)) { show() } // 1 // Unit

            // Returning context object
            Toast.makeText(c, r, d).also { println("it") }.show() // 2 // Unit
            Toast.makeText(c, r, d).apply { println("it") }.show() // 3 // Unit
            // Returning lambda result
            with("hello") { Toast.makeText(c, r, d) }.show() // 4 // Toast
            "hello".let { Toast.makeText(c, r, d) }.show() // 5 // Toast
            "hello".run { Toast.makeText(c, r, d) }.show() // 6 // Toast
          }

          fun testContextReturns(c: Context, r: Int, d: Int) {
            val toast1 = Toast.makeText(c, r, d)
            toast1.apply {
              Toast.makeText(c, r, d) // 7 // Unit
            }.show()

            Toast.makeText(c, r, d).also {
              Toast.makeText(c, r, d) // 8 // Unit
            }.show()
          }

          fun testThrowing() {
            assertThrows<RuntimeException>(RuntimeException::class.java) {
              CompletableDeferred<Any?>("later/assertThrows") // 9 // CompletableDeferred<Object>
            }
            assertThrows<RuntimeException>(
              RuntimeException::class.java,
              ThrowingRunnable {
                CompletableDeferred<Any?>("later/ThrowingRunnable") // 10 // void
              }
            )
          }

          suspend fun testLaunchSuggestionWithUnusedAsync(): Unit = coroutineScope { // 11 // Object
            async { "Deferred value" } // 12 // Object
          }
        """
          )
          .indented(),
        java(
            "my/junit/function/ThrowingRunnable.java",
            """
              package my.junit.function;

              public interface ThrowingRunnable {
                void run() throws Throwable;
              }
            """,
          )
          .indented(),
      )

    val expectedCount = 12
    check(*testFiles) { file ->
      var lambdaCount = 0
      var returnCount = 0
      file.accept(
        object : AbstractUastVisitor() {
          var lambdaType: PsiType? = null

          override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
            lambdaCount++
            lambdaType = node.functionalInterfaceType ?: node.getExpressionType()
            return super.visitLambdaExpression(node)
          }

          override fun afterVisitLambdaExpression(node: ULambdaExpression) {
            lambdaType = null
            super.afterVisitLambdaExpression(node)
          }

          override fun visitReturnExpression(node: UReturnExpression): Boolean {
            // Skip an implicit return for body expression, e.g.,
            //   suspend fun test...(): Unit = ...
            if (
              node.uastParent !is UBlockExpression ||
                node.uastParent?.uastParent !is ULambdaExpression
            )
              return super.visitReturnExpression(node)

            returnCount++
            val lambdaReturnType = LambdaUtil.getFunctionalInterfaceReturnType(lambdaType)
            val returnTypeText =
              lambdaReturnType.let { if (it is PsiWildcardType) it.bound else it }?.canonicalText
                ?: "<null>"
            val lambdaReturnsUnit = returnTypeText == "kotlin.Unit" || returnTypeText == "void"
            assertEquals(lambdaReturnsUnit, node.isIncorrectImplicitReturnInLambda())
            return super.visitReturnExpression(node)
          }
        }
      )
      assertEquals(expectedCount, lambdaCount)
      assertEquals(lambdaCount, returnCount)
    }
  }

  fun testReferenceQualifierType() {
    val testFiles =
      arrayOf(
        kotlin(
            """
            import my.math.IntMath

            interface MyInterface

            fun test1() = MyInterface::class
            fun test2(x: MyInterface) = x::class
            fun test3() = Int::class
            fun test4() = Number::toInt
            fun test5(a: Number) = a::toInt
            fun test6() = IntMath::factorial
          """
          )
          .indented(),
        java(
            """
            package my.math;
            public final class IntMath {
              public static int factorial(int n) {
                return 42;
              }
            }
          """
          )
          .indented(),
      )

    val expectedTypes =
      mapOf(
        "test1" to "MyInterface",
        "test2" to "MyInterface",
        "test3" to "java.lang.Integer",
        "test4" to "java.lang.Number",
        "test5" to "java.lang.Number",
        "test6" to "my.math.IntMath",
      )

    var count = 0
    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          var currentMethod: String? = null

          override fun visitMethod(node: UMethod): Boolean {
            currentMethod = node.name
            return super.visitMethod(node)
          }

          override fun afterVisitMethod(node: UMethod) {
            currentMethod = null
            super.afterVisitMethod(node)
          }

          override fun visitClassLiteralExpression(node: UClassLiteralExpression): Boolean {
            count++
            assertEquals(expectedTypes[currentMethod], node.type?.canonicalText)
            return super.visitClassLiteralExpression(node)
          }

          override fun visitCallableReferenceExpression(
            node: UCallableReferenceExpression
          ): Boolean {
            count++
            assertEquals(expectedTypes[currentMethod], node.qualifierType?.canonicalText)
            return super.visitCallableReferenceExpression(node)
          }
        }
      )
    }
    assertEquals(expectedTypes.size, count)
  }
}
