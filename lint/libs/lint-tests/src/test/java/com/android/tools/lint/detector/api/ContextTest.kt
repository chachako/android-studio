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

import com.android.tools.lint.checks.AbstractCheckTest
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Context.Companion.isSuppressedWithComment
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.getValueIfStringLiteral

class ContextTest : AbstractCheckTest() {
  fun testSuppressFileAnnotation() {
    // Regression test for https://issuetracker.google.com/116838536
    lint()
      .files(
        kotlin(
            """
                @file:Suppress("unused", "_TestIssueId")
                package test.pkg

                class MyTest {
                    val s: String = "/sdcard/mydir"
                }
                """
          )
          .indented(),
        gradle(""),
      )
      .issues(TEST_ISSUE)
      .run()
      .expectClean()
  }

  fun testSuppressLine() {
    // Issue id
    assertFalse(isSuppressedWithComment("", TEST_ISSUE))
    assertFalse(isSuppressedWithComment("A_TestIssueId", TEST_ISSUE))
    assertFalse(isSuppressedWithComment("_TestIssueIdB", TEST_ISSUE))
    assertTrue(isSuppressedWithComment("_TestIssueId", TEST_ISSUE))
    assertTrue(isSuppressedWithComment("_TestIssueIdFooBar,_TestIssueId", TEST_ISSUE))
    assertTrue(isSuppressedWithComment("/**@noinspection _TestIssueId*/", TEST_ISSUE))
    assertTrue(isSuppressedWithComment("[@noinspection _TestIssueId]", TEST_ISSUE))
    assertTrue(isSuppressedWithComment("<!--noinspection _TestIssueId-->", TEST_ISSUE))
    assertTrue(isSuppressedWithComment(" _TestIssueId ", TEST_ISSUE))
    assertTrue(isSuppressedWithComment(" _testissueid ", TEST_ISSUE))
    assertTrue(isSuppressedWithComment("A, _TestIssueId", TEST_ISSUE))
    assertTrue(isSuppressedWithComment("_TestIssueId, B", TEST_ISSUE))
    assertTrue(isSuppressedWithComment("A, _TestIssueId, B", TEST_ISSUE))

    // Category
    assertFalse(isSuppressedWithComment("AMessages", TEST_ISSUE))
    assertFalse(isSuppressedWithComment("MessagesB", TEST_ISSUE))
    assertTrue(isSuppressedWithComment("Messages", TEST_ISSUE))
    assertTrue(isSuppressedWithComment(" Messages ", TEST_ISSUE))
    assertTrue(isSuppressedWithComment("Correctness", TEST_ISSUE))
    assertTrue(isSuppressedWithComment("Correctness:Messages", TEST_ISSUE))
    assertTrue(isSuppressedWithComment("A,Messages", TEST_ISSUE))
    assertTrue(isSuppressedWithComment("Messages,B", TEST_ISSUE))
    assertTrue(isSuppressedWithComment("A, Messages, B", TEST_ISSUE))
  }

  fun testSuppressObjectAnnotation() {
    // Regression test for https://issuetracker.google.com/116838536
    lint()
      .files(
        kotlin(
            """
                package test.pkg
                import android.annotation.SuppressLint
                @SuppressLint("_TestIssueId")
                object TestClass1 {
                    const val s: String = "/sdcard/mydir"
                }"""
          )
          .indented(),
        gradle(""),
      )
      .issues(TEST_ISSUE)
      .run()
      .expectClean()
  }

  fun testSuppressCompanionObjectAnnotation() {
    // Regression test for b/293334438
    lint()
      .files(
        kotlin(
            """
                package test.pkg
                import android.annotation.SuppressLint

                class MyClass {
                  @SuppressLint("_TestIssueId")
                  companion object {
                      const val s: String = "/sdcard/mydir"
                  }
                }
                """
          )
          .indented(),
        gradle(""),
      )
      .issues(TEST_ISSUE)
      .run()
      .expectClean()
  }

  fun testSuppressPropertyAnnotation() {
    // Regression test for b/296288411
    lint()
      .files(
        kotlin(
            """
                package test.pkg
                import android.annotation.SuppressLint

                class MyClass {
                  @SuppressLint("_TestIssueId") val s: String get() = {
                    class TestClass1 {
                      const val s: String = "/sdcard/mydir"
                    }
                    TestClass1().s
                  }
                }
                """
          )
          .indented(),
        gradle(""),
      )
      .issues(TEST_ISSUE)
      .run()
      .expectClean()
  }

  fun testKotlinSuppressionAnnotationsWithPsiScope() {
    // Regression test for b/274787712
    // When a PsiElement is passed as the scope for an incident, LintDriver behaves differently.
    // In particular, it needs to implement the same logic for both Kotlin and Java PSI, which it
    // was not doing when b/274787712 was reported.
    lint()
      .files(
        kotlin(
            """
                package test.pkg
                import android.annotation.SuppressLint

                class MyClass {
                  @SuppressLint("_PsiTestIssueId")
                  const val s: String = "/sdcard/mydir"
                }
                """
          )
          .indented(),
        gradle(""),
      )
      .issues(PSI_TEST_ISSUE)
      .run()
      .expectClean()
  }

  fun testMultilineReporter() {
    // Test to make sure that when the argument to string is indented and/or has line continuations
    // (\) the
    // message is properly processed.
    lint()
      .files(
        kotlin(
            """
            fun method() {
               method() // ERROR
            }
            """
          )
          .indented()
      )
      .issues(MultiLineReporter.ISSUE)
      .run()
      .expect(
        """
        src/test.kt:2: Warning: Error message indented and split across multiple lines. [_MultilineReporter]
           method() // ERROR
           ~~~~~~~~
        0 errors, 1 warnings
        """
      )
  }

  class MultiLineReporter : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames() = listOf("method")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
      context.report(
        Incident(
          ISSUE,
          node,
          context.getLocation(node),
          """
          Error message indented and split across \
          multiple lines.
          """,
        )
      )
    }

    companion object {
      val ISSUE =
        Issue.create(
          "_MultilineReporter",
          "Not applicable",
          "Not applicable",
          Category.MESSAGES,
          5,
          Severity.WARNING,
          Implementation(MultiLineReporter::class.java, Scope.JAVA_FILE_SCOPE),
        )
    }
  }

  // TODO(b/293581088): UAST of Kotlin strings (PSI: KtStringTemplateExpression) with 1 child is
  //  somewhat broken until "kotlin.uast.force.uinjectionhost" defaults to true.
  fun ignoreTestLocationOfKotlinString() {
    val tripleQuotes = "\"\"\""
    lint()
      .files(
        kotlin(
            """
                package com.example

                class MyClass {
                  fun foo(arg1: String) {

                  }

                  fun bar() {
                    val a = 5

                    foo(arg1 = "hello")
                    foo(arg1 = "hello" + " world")
                    foo(arg1 = "")
                    foo(arg1 = "＄{a}")
                    foo(arg1 = "＄{a} ")
                    foo(arg1 = ${tripleQuotes}hello${tripleQuotes})
                  }
                }
                """
          )
          .indented()
      )
      .issues(ReportsArgumentDetector.ISSUE)
      .run()
      .expect(
        """
          src/com/example/MyClass.kt:11: Warning: Argument to foo [_UReportsArgumentIssue]
              foo(arg1 = "hello")
                         ~~~~~~~
          src/com/example/MyClass.kt:12: Warning: Argument to foo [_UReportsArgumentIssue]
              foo(arg1 = "hello" + " world")
                         ~~~~~~~~~~~~~~~~~~
          src/com/example/MyClass.kt:13: Warning: Argument to foo [_UReportsArgumentIssue]
              foo(arg1 = "")
                         ~~
          src/com/example/MyClass.kt:14: Warning: Argument to foo [_UReportsArgumentIssue]
              foo(arg1 = "＄{a}")
                         ~~~~~~
          src/com/example/MyClass.kt:15: Warning: Argument to foo [_UReportsArgumentIssue]
              foo(arg1 = "＄{a} ")
                         ~~~~~~~
          src/com/example/MyClass.kt:16: Warning: Argument to foo [_UReportsArgumentIssue]
              foo(arg1 = ${tripleQuotes}hello${tripleQuotes})
                         ~~~~~~~~~~~
          0 errors, 6 warnings
          """
      )
  }

  class ReportsArgumentDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames() = listOf("foo")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
      val arg = node.getArgumentForParameter(0)
      context.report(
        Incident(
          ISSUE,
          "Argument to foo",
          context.getLocation(node.getArgumentForParameter(0)),
          arg,
        )
      )
    }

    companion object {
      val ISSUE =
        Issue.create(
          "_UReportsArgumentIssue",
          "Not applicable",
          "Not applicable",
          Category.MESSAGES,
          5,
          Severity.WARNING,
          Implementation(ReportsArgumentDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )
    }
  }

  fun testSuppressKotlinViaGradleContext() {
    // The ReportsUElementFromGradleContextDetector stores the call to foo() (a UElement) in a field
    // and then reports a Gradle element, but passes the UElement as the scope (so that the user
    // could suppress the warning by adding an annotation to the foo() call). It is unclear whether
    // this is really a good idea, but there are detectors out there that already do this, so lint
    // should at least not fail. This would previously cause a ClassCastException because the scope
    // was assumed to be a Gradle element, and was unsafely cast.
    // Regression test for b/296986527 and b/293517205

    lint()
      .files(
        kotlin(
            """
                package test.pkg

                class MyClass {
                  fun foo() {
                  }
                  fun bar() {
                    foo()
                  }
                }
                """
          )
          .indented(),
        gradle(
            """
          android {
            defaultConfig {
              applicationId "com.android.tools.test"
            }
          }
          """
          )
          .indented(),
      )
      .issues(ReportsUElementFromGradleContextDetector.ISSUE)
      .run()
      .expect(
        """
          build.gradle:3: Warning: Bad [_UElementIssue]
              applicationId "com.android.tools.test"
                            ~~~~~~~~~~~~~~~~~~~~~~~~
          0 errors, 1 warnings
          """
      )
  }

  class ReportsUElementFromGradleContextDetector : Detector(), SourceCodeScanner, GradleScanner {
    // See testSuppressKotlinViaGradleContext.

    var element: UElement? = null

    override fun getApplicableMethodNames() = listOf("foo")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
      element = node
    }

    override fun checkDslPropertyAssignment(
      context: GradleContext,
      property: String,
      value: String,
      parent: String,
      parentParent: String?,
      propertyCookie: Any,
      valueCookie: Any,
      statementCookie: Any,
    ) {
      context.report(Incident(ISSUE, element!!, context.getLocation(valueCookie), "Bad"))
    }

    companion object {
      val ISSUE =
        Issue.create(
          "_UElementIssue",
          "Not applicable",
          "Not applicable",
          Category.MESSAGES,
          5,
          Severity.WARNING,
          Implementation(
            ReportsUElementFromGradleContextDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE),
          ),
        )
    }
  }

  override fun getDetector(): Detector = NoLocationNodeDetector()

  override fun getIssues(): List<Issue> = listOf(TEST_ISSUE, PSI_TEST_ISSUE)

  // Detector which reproduces problem in issue https://issuetracker.google.com/116838536
  class NoLocationNodeDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
      listOf(ULiteralExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? =
      object : UElementHandler() {
        override fun visitLiteralExpression(node: ULiteralExpression) {
          val s = node.getValueIfStringLiteral()
          if (s != null && s.startsWith("/sdcard/")) {
            val message = """Sample error message"""
            val location = context.getLocation(node)
            // Note: We're calling
            //    context.report(Issue, Location, String)
            // NOT:
            //    context.report(Issue, UElement, Location, String)
            // to test that we suppress based on stashed location
            // source element from above; this tests issue 116838536
            context.report(TEST_ISSUE, location, message)

            // If we pass a PsiElement as the scope of an incident, LintDriver will use a
            // PSI-specific code path to deduce suppressions. We need to test this path
            // explicitly, so tests may choose to look for this issue.
            // See LintDriver.isSuppressedLocally,
            // and LintDriver.isSuppressed(context: JavaContext?, issue: Issue, scope:
            // PsiElement?)
            context.report(PSI_TEST_ISSUE, scope = node.sourcePsi, location, message)
          }
        }
      }
  }

  companion object {
    val TEST_ISSUE =
      Issue.create(
        "_TestIssueId",
        "Not applicable",
        "Not applicable",
        Category.MESSAGES,
        5,
        Severity.WARNING,
        Implementation(NoLocationNodeDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )

    val PSI_TEST_ISSUE =
      Issue.create(
        "_PsiTestIssueId",
        "Not applicable",
        "Not applicable",
        Category.MESSAGES,
        5,
        Severity.WARNING,
        Implementation(NoLocationNodeDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }
}
