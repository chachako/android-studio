/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.lint.helpers

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.infrastructure.TestFiles.bytecode
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.parse
import com.android.tools.lint.checks.infrastructure.use
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import kotlin.test.assertNotNull
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Most of the evaluator is tested indirectly via all the lint unit tests; this covers some
 * additional specific scenarios.
 */
class DefaultJavaEvaluatorTest {
  @get:Rule val temporaryFolder = TemporaryFolder()

  // Regression test for https://groups.google.com/d/msg/lint-dev/BaRimyf40tI/DpkOjMMEAQAJ
  @Test
  fun lookUpAnnotationsOnUastModifierLists() {
    lint()
      .files(
        java(
            """
                    package foo;
                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    public class MyTest {
                        public void myTest(@Override int something) { }
                    }"""
          )
          .indented()
      )
      .sdkHome(TestUtils.getSdk().toFile())
      .issues(TestAnnotationLookupDetector.ISSUE)
      .run()
      .expectClean()
  }

  @Test
  fun testCallAssignmentRanges() {
    @Suppress("RemoveRedundantCallsOfConversionMethods")
    lint()
      .files(
        java(
            """
                package foo;
                public class Bean {
                    public String getFoo() { return ""; }
                    public void setFoo(String foo) {
                    }
                }
                """
          )
          .indented(),
        kotlin(
            """
                package foo
                fun test(s: String) {
                    val bean = Bean()
                    bean.setFoo("value")
                    bean.foo = "value"
                    bean.foo = "valu" + 'e'
                    bean.foo = ""${"\""}value""${"\""}
                    bean.foo = s.toString()
                    bean.foo = s
                }
                """
          )
          .indented(),
      )
      .sdkHome(TestUtils.getSdk().toFile())
      .issues(TestAnnotationLookupDetector.ISSUE)
      .run()
      .expect(
        """
                src/foo/test.kt:4: Warning: Error with arguments but no receiver [_Order]
                    bean.setFoo("value")
                         ~~~~~~~~~~~~~~~
                src/foo/test.kt:4: Warning: Error with receiver and arguments [_Order]
                    bean.setFoo("value")
                    ~~~~~~~~~~~~~~~~~~~~
                src/foo/test.kt:4: Warning: Error with receiver and no arguments [_Order]
                    bean.setFoo("value")
                    ~~~~~~~~~~~
                src/foo/test.kt:5: Warning: Error with arguments but no receiver [_Order]
                    bean.foo = "value"
                         ~~~~~~~~~~~~~
                src/foo/test.kt:5: Warning: Error with receiver and arguments [_Order]
                    bean.foo = "value"
                    ~~~~~~~~~~~~~~~~~~
                src/foo/test.kt:5: Warning: Error with receiver and no arguments [_Order]
                    bean.foo = "value"
                    ~~~~~~~~
                src/foo/test.kt:6: Warning: Error with arguments but no receiver [_Order]
                    bean.foo = "valu" + 'e'
                         ~~~~~~~~~~~~~~~~~~
                src/foo/test.kt:6: Warning: Error with receiver and arguments [_Order]
                    bean.foo = "valu" + 'e'
                    ~~~~~~~~~~~~~~~~~~~~~~~
                src/foo/test.kt:6: Warning: Error with receiver and no arguments [_Order]
                    bean.foo = "valu" + 'e'
                    ~~~~~~~~
                src/foo/test.kt:7: Warning: Error with arguments but no receiver [_Order]
                    bean.foo = ""${'"'}value""${'"'}
                         ~~~~~~~~~~~~~~~~~
                src/foo/test.kt:7: Warning: Error with receiver and arguments [_Order]
                    bean.foo = ""${'"'}value""${'"'}
                    ~~~~~~~~~~~~~~~~~~~~~~
                src/foo/test.kt:7: Warning: Error with receiver and no arguments [_Order]
                    bean.foo = ""${'"'}value""${'"'}
                    ~~~~~~~~
                src/foo/test.kt:8: Warning: Error with arguments but no receiver [_Order]
                    bean.foo = s.toString()
                         ~~~~~~~~~~~~~~~~~~
                src/foo/test.kt:8: Warning: Error with receiver and arguments [_Order]
                    bean.foo = s.toString()
                    ~~~~~~~~~~~~~~~~~~~~~~~
                src/foo/test.kt:8: Warning: Error with receiver and no arguments [_Order]
                    bean.foo = s.toString()
                    ~~~~~~~~
                src/foo/test.kt:9: Warning: Error with arguments but no receiver [_Order]
                    bean.foo = s
                         ~~~~~~~
                src/foo/test.kt:9: Warning: Error with receiver and arguments [_Order]
                    bean.foo = s
                    ~~~~~~~~~~~~
                src/foo/test.kt:9: Warning: Error with receiver and no arguments [_Order]
                    bean.foo = s
                    ~~~~~~~~
                0 errors, 18 warnings
                """
      )
  }

  @Test
  fun testStatic() {
    val (contexts, disposable) =
      parse(
        temporaryFolder = temporaryFolder,
        sdkHome = TestUtils.getSdk().toFile(),
        testFiles =
          arrayOf(
            java(
                """
                    package foo;
                    public class Foo {
                        public static String staticMethodJava() { return ""; }
                        public String instanceMethodJava() { return ""; }
                    }
                    """
              )
              .indented(),
            kotlin(
                """
                    @file:JvmName("Tedt")
                    package foo
                    fun staticMethodTopLevel() {
                    }
                    """
              )
              .indented(),
            kotlin(
                """
                    package foo
                    class Bar {
                        fun instanceMethodKotlin(): String { return "" }
                        companion object {
                            fun instanceMethodCompanionObject(): String { return "" }
                            @JvmStatic
                            fun staticMethodCompanionObject(): String { return "" }
                        }
                    }
                    """
              )
              .indented(),
          ),
      )

    var methodCount = 0
    for (context in contexts) {
      val file = context.uastFile ?: continue
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitMethod(node: UMethod): Boolean {
            methodCount++
            if (node.isConstructor) {
              assertFalse(context.evaluator.isStatic(node))
            } else {
              val name = node.name
              assertTrue(name, name.startsWith("instance") || name.startsWith("static"))
              val expectStatic = name.startsWith("static")
              val isStatic = context.evaluator.isStatic(node)
              assertEquals("Incorrect isStatic value for method $name", expectStatic, isStatic)
            }
            return super.visitMethod(node)
          }
        }
      )
    }
    assertEquals(9, methodCount)
    Disposer.dispose(disposable)
  }

  class TestAnnotationLookupDetector : Detector(), SourceCodeScanner {
    companion object Issues {
      val ISSUE =
        Issue.create(
          "_Order",
          "Sample test detector summary",
          "Sample test detector explanation",
          Category.CORRECTNESS,
          6,
          Severity.WARNING,
          Implementation(TestAnnotationLookupDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
      listOf(UMethod::class.java, UVariable::class.java, UCallExpression::class.java)

    class AnnotationOrderVisitor(private val context: JavaContext) : UElementHandler() {
      override fun visitVariable(node: UVariable) {
        processAnnotations(node)
      }

      override fun visitMethod(node: UMethod) {
        processAnnotations(node)
      }

      override fun visitCallExpression(node: UCallExpression) {
        // Also test location ranges for assignments here
        val methodName = node.methodName ?: node.methodIdentifier?.name
        if (methodName == "setFoo") {
          context.report(
            ISSUE,
            node,
            context.getCallLocation(node, false, true),
            "Error with arguments but no receiver",
          )
          context.report(
            ISSUE,
            node,
            context.getCallLocation(node, true, true),
            "Error with receiver and arguments",
          )
          context.report(
            ISSUE,
            node,
            context.getCallLocation(node, true, false),
            "Error with receiver and no arguments",
          )
        }
      }

      @Suppress("DEPRECATION")
      private fun processAnnotations(modifierListOwner: PsiModifierListOwner) {
        context.evaluator.findAnnotationInHierarchy(modifierListOwner, "org.foo.bar")
        context.evaluator.findAnnotation(modifierListOwner, "org.foo.bar")
        context.evaluator.getAnnotation(modifierListOwner, "org.foo.bar")
        context.evaluator.getAllAnnotations(modifierListOwner, true).mapNotNull {
          it.qualifiedName?.split(".")?.lastOrNull()
        }
        // This detector doesn't actually report anything; the regression test
        // ensures that the above calls don't crash
        context.evaluator.getAnnotations(modifierListOwner, true).mapNotNull {
          it.qualifiedName?.split(".")?.lastOrNull()
        }
      }
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
      return AnnotationOrderVisitor(context)
    }
  }

  @Test
  fun testFieldPosition() {
    // Regression test for https://groups.google.com/g/lint-dev/c/yWcp7gv83_8
    lint()
      .files(
        kotlin(
            """
                    package test.pkg
                    class FakeDetectorProofKT {
                        val myTestString = ""
                    }
                """
          )
          .indented()
      )
      .sdkHome(TestUtils.getSdk().toFile())
      .issues(RangeTestDetector.ISSUE)
      .run()
      .expect(
        """
                src/test/pkg/FakeDetectorProofKT.kt:3: Error: Fake issue [_MyFakeIssueId]
                    val myTestString = ""
                    ~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
      )
  }

  @Suppress("UnstableApiUsage")
  class RangeTestDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
      return listOf<Class<out UElement>>(UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
      return object : UElementHandler() {
        override fun visitClass(node: UClass) {
          for (field in node.fields) {
            if (field.name.contains("test", ignoreCase = true)) {
              context.report(ISSUE, node, context.getLocation(field), "Fake issue")
            }
          }
        }
      }
    }

    companion object {
      @JvmField
      val ISSUE =
        Issue.create(
          "_MyFakeIssueId",
          "Fake issue description",
          "Fake issue explanation",
          Category.CORRECTNESS,
          6,
          Severity.ERROR,
          Implementation(RangeTestDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )
    }
  }

  @Test
  fun test200186871() {
    // Regression test for http://b/200186871
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                object Test {
                    fun test() {
                        TargetClass.foo()
                        OtherClass.foo()
                    }
                }

                object TargetClass {
                    fun foo() {}
                }

                object OtherClass {
                    fun foo() {}
                }
                """
          )
          .indented()
      )
      .sdkHome(TestUtils.getSdk().toFile())
      .issues(MethodMatchesDetector.ISSUE)
      .run()
      .expect(
        """
                src/test/pkg/Test.kt:11: Error: Found reference to test.pkg.TargetClass.foo [_FakeIssueId]
                    fun foo() {}
                        ~~~
                1 errors, 0 warnings
                """
      )
  }

  @Suppress("RedundantSuspendModifier")
  @Test
  fun testIsSuspend() {
    // Regression test for b/274945683
    listOf(
        kotlin(
            """
            package test.pkg
            suspend fun test() {
                isNotSuspend1()
                isSuspend1()
                isSuspend2()
                isSuspend3(0)
            }
            """
          )
          .indented(),
        bytecode(
          "libs/lib.jar",
          kotlin(
              "src/test/pkg/suspends.kt",
              """
              package test.pkg

              fun isNotSuspend1(): Int = 0
              suspend fun isSuspend1() { }
              suspend fun isSuspend2(): Int = 0
              suspend fun isSuspend3(int: Int): List<String> = emptyList()
              """,
            )
            .indented(),
          0x14f5ac5d,
          """
          META-INF/main.kotlin_module:
          H4sIAAAAAAAA/2NgYGBmYGBgBGIOBijgkuPiKEktLtEryE4X4gouLS5IzUsp
          9i4RYgsBinqXKDFoMQAATQJerzgAAAA=
          """,
          """
          test/pkg/SuspendsKt.class:
          H4sIAAAAAAAA/51TS08TURT+7kyfw6s8RFtAUEBbFaa8FAWNiBpHazUW2eBm
          WibNhekMmbkluCMm+kNcuzCuiAtDcOePMp47DFLKy9i095ye853vvO799fv7
          DwDTuM/QLSxf6BvrVb1U9zcsZ9V/IeJgDKk1c9PUbdOp6q/Ka1aFrCpDG/eL
          rgihEwxqNmcwaNw/NE1nC+uusLmjV1zPrQvuWL6+6Dqk1E3BXWcuV2jmnmN4
          fF7Y/NgB4K3DxdyDk2lGCq5X1dcsUfZM7vi66TiuCOJ9vVi3bbNsWwQbPgvm
          CokkVEtDwjhaGRJGsbS0UFx8QpM4Uk0r2tGRRBtSFDVScWsbtiXZGIbOm0bj
          +CYZnv/DHA77NhxhVS3vtGmMHqda26zpnKI8x7T1R+4Wd6px9DLEyu4WsTH0
          Zo3cCRlacQlpDReRaSx4imEma/zXwt+dG3fQKblsvcB9Md/AUxIelU6Nn9L6
          0F9u2yZLsNnFQ13e8kGGpFXbEO8lN0NXNteUj5q+gqsahjBMV53L6TC67p0H
          db+0hLlqCpPSKbVNld4Uk0dCHiDsulQUcm5xqeVJky/k4+52v7a7rSmpxL5Q
          NCWhkhyiXzvpSiaW2t3OKHmW6UwpJNVne58e/txhu9t7n2NKKkLmqHQ3mwdT
          sUyiK9Kl5BP5+GQslTwO0vY+KBHKkZbF0H2jOlv88OmPr1OHkUV31WLoKNA+
          ivVa2fKW5KOh+RTcimkvmx6X/0NjssSrjinqHul9b+q0vJplOJvc5+ReOHxV
          9DKbva9Nz6xZdBOPwLSSW/cq1lMu2dNhzPIxPkxAQUTOmc40oohRIzP0L0NS
          fhT1S7CE23TGAptKuDgShJe4cbkgGf0NnV8lvgEZwR06W0O9C90kZwNMEndD
          lEbyHv3ikqaFlDR6/pLrIXlU3UFfM3u0gT16hP3CWez9GAjZZ8mmyNJ2MNJM
          HgvIe/f9GMW1cEQyDQvTXD6WJqkEs2oJ3HPBOY15kgWyXqcqsytQDeQM3DBw
          E7cMjGHcoDbzK2ByF5MrdIsQ9THlIx6cCR89PvoDnb6jPgb+ADtWbP14BgAA
          """,
        ),
      )
      .use(temporaryFolder, TestUtils.getSdk().toFile()) { context ->
        val evaluator = context.evaluator
        context.uastFile?.accept(
          object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
              val method = node.resolve()
              assertNotNull("Couldn't resolve ${node.sourcePsi?.text}")
              checkResolve(method!!)
              return super.visitCallExpression(node)
            }

            private fun checkResolve(method: PsiMethod) {
              val name = method.name
              if (name.startsWith("isSuspend")) {
                assertTrue("Expected $name to return isSuspend=true", evaluator.isSuspend(method))
              } else if (name.startsWith("isNotSuspend")) {
                assertFalse("Expected $name to return isSuspend=false", evaluator.isSuspend(method))
              }
            }
          }
        )
      }
  }

  @Test
  fun testGetMethodDescriptor() {
    val sb = StringBuilder()
    listOf(
        java(
            """
            import java.util.List;
            class Test {
              void test() { }
              int test(int a, int[] b, int[][] c, boolean d, float e, double f, long g, short h, char z) { return -1; }
              List<char[]> test2(List<List<String>> list) { return null; }
              class Inner {
                Inner(int i) { }
                void inner() { }
              }
            }
            """
          )
          .indented()
      )
      .use(temporaryFolder, TestUtils.getSdk().toFile()) { context ->
        val evaluator = context.evaluator
        context.uastFile?.accept(
          object : AbstractUastVisitor() {
            private fun addMethod(node: PsiMethod) {
              val text = node.text
              sb.append(text.substringBefore('{').trim()).append(":\n")
              sb.append("simple: ")
              sb
                .append(
                  evaluator.getMethodDescription(
                    method = node,
                    includeName = false,
                    includeReturn = false,
                  )
                )
                .append("\n")
              sb.append("full:   ")
              sb
                .append(
                  evaluator.getMethodDescription(
                    method = node,
                    includeName = true,
                    includeReturn = true,
                  )
                )
                .append("\n\n")
            }

            override fun visitMethod(node: UMethod): Boolean {
              addMethod(node.javaPsi)
              return super.visitMethod(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
              node.resolve()?.let { addMethod(it) }
              return super.visitCallExpression(node)
            }
          }
        )
      }
    assertEquals(
      """
            void test():
            simple: ()
            full:   test()V

            int test(int a, int[] b, int[][] c, boolean d, float e, double f, long g, short h, char z):
            simple: (I[I[[IZFDJSC)
            full:   test(I[I[[IZFDJSC)I

            List<char[]> test2(List<List<String>> list):
            simple: (Ljava.util.List;)
            full:   test2(Ljava.util.List;)Ljava.util.List;

            Inner(int i):
            simple: (LTest;I)
            full:   <init>(LTest;I)V

            void inner():
            simple: ()
            full:   inner()V
            """
        .trimIndent()
        .trim(),
      sb.toString().trim(),
    )
  }

  class MethodMatchesDetector : Detector(), Detector.UastScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("foo")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
      if (context.evaluator.methodMatches(method, "test.pkg.TargetClass", false)) {
        context.report(
          ISSUE,
          context.getNameLocation(method),
          "Found reference to `test.pkg.TargetClass.foo`",
        )
      }
    }

    companion object {
      @JvmField
      val ISSUE =
        Issue.create(
          "_FakeIssueId",
          "Fake description",
          "Fake explanation",
          Category.CORRECTNESS,
          6,
          Severity.ERROR,
          Implementation(MethodMatchesDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )
    }
  }
}
