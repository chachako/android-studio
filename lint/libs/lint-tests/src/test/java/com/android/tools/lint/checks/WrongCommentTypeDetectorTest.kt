/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.lint.checks

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.client.api.LintBaseline
import com.android.tools.lint.detector.api.Detector
import java.io.File

class WrongCommentTypeDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return WrongCommentTypeDetector()
  }

  fun testDocumentationExample() {
    lint()
      .files(
        kotlin(
            """
            /* Block comment
             * @see tags point to KDoc
             */
            open class ParentClass {
                /** Ok, already KDoc */
                open fun someMethod(arg: Int) { }
                /* Ok, no tags */
                open fun someMethod2(arg: Int) { }
            }
            """
          )
          .indented(),
        java(
            """
            public class Test {
                /* @since 1.5 */ String text;
            }
            """
          )
          .indented(),
      )
      .run()
      .expect(
        """
        src/ParentClass.kt:2: Warning: This block comment looks like it was intended to be a KDoc comment [WrongCommentType]
         * @see tags point to KDoc
           ~~~~~~~~~~~~~~~~~~~~~~~
        src/Test.java:2: Warning: This block comment looks like it was intended to be a javadoc comment [WrongCommentType]
            /* @since 1.5 */ String text;
               ~~~~~~~~~~
        0 errors, 2 warnings
        """
      )
      .expectFixDiffs(
        """
        Autofix for src/ParentClass.kt line 2: Replace with /**:
        @@ -1 +1
        - /* Block comment
        + /** Block comment
        Autofix for src/Test.java line 2: Replace with /**:
        @@ -2 +2
        -     /* @since 1.5 */ String text;
        +     /** @since 1.5 */ String text;
        """
      )
  }

  fun testJavadocLink() {
    lint()
      .files(
        kotlin(
            """
            /* This is a [link](http://wwww.google.com) */
            open class ParentClass
            """
          )
          .indented(),
        java(
            """
            /* This is a {@link ParentClass} */
            public class Test {
            }
            """
          )
          .indented(),
      )
      .run()
      .expect(
        """
        src/ParentClass.kt:1: Warning: This block comment looks like it was intended to be a KDoc comment [WrongCommentType]
        /* This is a [link](http://wwww.google.com) */
                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/Test.java:1: Warning: This block comment looks like it was intended to be a javadoc comment [WrongCommentType]
        /* This is a {@link ParentClass} */
                     ~~~~~~~~~~~~~~~~~~~
        0 errors, 2 warnings
        """
      )
      .expectFixDiffs(
        """
        Autofix for src/ParentClass.kt line 1: Replace with /**:
        @@ -1 +1
        - /* This is a [link](http://wwww.google.com) */
        + /** This is a [link](http://wwww.google.com) */
        Autofix for src/Test.java line 1: Replace with /**:
        @@ -1 +1
        - /* This is a {@link ParentClass} */
        + /** This is a {@link ParentClass} */
        """
      )
  }

  fun testComposeFunction() {
    lint()
      .files(
        kotlin(
            """
             /*
            * Greeting element.
            *
            * @sample DefaultPreview
            */
            fun Greeting(name: String) {
                Text(text = "Hello")
            }
            """
          )
          .indented()
      )
      .run()
      .expect(
        """
        src/test.kt:4: Warning: This block comment looks like it was intended to be a KDoc comment [WrongCommentType]
        * @sample DefaultPreview
          ~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
      .expectFixDiffs(
        """
        Autofix for src/test.kt line 4: Replace with /**:
        @@ -1 +1
        -  /*
        +  /**
        """
      )
  }

  fun testIgnoreNonApi() {
    lint()
      .files(
        kotlin(
            """
            fun test() {
               /* @see Ignore me **/
            }
            """
          )
          .indented()
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expectClean()
  }

  fun testProperties() {
    lint()
      .files(
        kotlin(
            """
            class Test(
                /* @param Comment **/
                var prop: String
            ) {
               /* @property Comment **/
                var prop2: String

                /* @see Comment **/
                companion object
            }
            """
          )
          .indented()
      )
      .run()
      .expect(
        """
        src/Test.kt:2: Warning: This block comment looks like it was intended to be a KDoc comment [WrongCommentType]
            /* @param Comment **/
               ~~~~~~~~~~~~~~
        src/Test.kt:5: Warning: This block comment looks like it was intended to be a KDoc comment [WrongCommentType]
           /* @property Comment **/
              ~~~~~~~~~~~~~~~~~
        src/Test.kt:8: Warning: This block comment looks like it was intended to be a KDoc comment [WrongCommentType]
            /* @see Comment **/
               ~~~~~~~~~~~~
        0 errors, 3 warnings
        """
      )
  }

  fun testSkipPrivate() {
    lint()
      .files(
        kotlin(
            """
            class Test {
                /* @property Comment **/
                private var prop2: String

                /* @param foo Comment **/
                private fun test(foo: String) { }
            }
            """
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testNonJavadoc() {
    // See https://stackoverflow.com/questions/5172841/non-javadoc-meaning
    lint()
      .files(
        java(
            """
            /*
             * (non-Javadoc)
             * @see com.android.ddmlib.IDevice#isOnline()
             */
            class Test {
                /*@Required*/
                public int test;
            }
            """
          )
          .indented(),
        kotlin(
            """
            /*
            Not deprecating this yet: wait until report(Incident) has been available for
            a reasonable number of releases such that third party checks can rely on it
            being present in all lint versions it will be run with. Note that all the
            report methods should be annotated like this, not just this one:
            @Deprecated(
                "Use the new report(Incident) method instead, which is more future proof",
                ReplaceWith(
                    "report(Incident(issue, message, location, null, quickfixData))",
                    "com.android.tools.lint.detector.api.Incident"
                )
            )
            */
            class Context
            """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testTolerateBaselineChanges() {
    val baseline = LintBaseline(ToolsBaseTestLintClient(), File(""))
    assertTrue(
      baseline.sameMessage(
        WrongCommentTypeDetector.ISSUE,
        "This block comment looks like it was intended to be a KDoc comment",
        "This block comment looks like it was intended to be a javadoc comment",
      )
    )

    assertTrue(
      baseline.sameMessage(
        WrongCommentTypeDetector.ISSUE,
        "This block comment looks like it was intended to be a javadoc comment",
        "This block comment looks like it was intended to be a KDoc comment",
      )
    )
  }
}
