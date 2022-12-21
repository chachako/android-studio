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

package com.android.tools.lint.client.api

import com.android.SdkConstants.VALUE_TRUE
import com.android.resources.ResourceFolderType
import com.android.tools.lint.checks.AbstractCheckTest
import com.android.tools.lint.checks.ManifestDetector
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.checks.infrastructure.TestResultChecker
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.google.common.truth.Truth.assertThat
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.Locale

class LintDriverCrashTest : AbstractCheckTest() {
    fun testLintDriverError() {
        // Regression test for 34248502
        lint().files(
            xml("res/layout/foo.xml", "<LinearLayout/>"),
            java(
                """
                    package test.pkg;
                    @SuppressWarnings("ALL") class Foo {
                    }
                    """
            )
        )
            .allowSystemErrors(true)
            .allowExceptions(true)
            .issues(CrashingDetector.CRASHING_ISSUE)
            // stack traces will differ between the test modes
            .testModes(TestMode.DEFAULT)
            .run()
            // Checking for manual substrings instead of doing an actual equals check
            // since the stacktrace contains a number of specific line numbers from
            // the lint implementation, including this test, which keeps shifting every
            // time there is an edit
            .check(
                {
                    assertThat(it).contains("Foo.java: Error: Unexpected failure during lint analysis of Foo.java (this is a bug in lint or one of the libraries it depends on)")
                    assertThat(it).contains("The crash seems to involve the detector com.android.tools.lint.client.api.LintDriverCrashTest＄CrashingDetector.")
                    assertThat(it).contains(
                        """
                        The crash seems to involve the detector com.android.tools.lint.client.api.LintDriverCrashTest＄CrashingDetector.
                        You can try disabling it with something like this:
                            android {
                                lint {
                                    disable "_TestCrash"
                                }
                            }
                        """.trimIndent()
                    )

                    // It's not easy to set environment variables from Java once the process is running,
                    // so instead of attempting to set it to true and false in tests, we'll just make this
                    // test adapt to what's set in the environment. On our CI tests, it should not be
                    // set, so the doesNotContain() assertion will be used. For developers on the lint team
                    // it's typically set so the contains() assertion will be used.
                    val suggestion = "You can run with --stacktrace or set environment variable LINT_PRINT_"
                    if (System.getenv("LINT_PRINT_STACKTRACE") == VALUE_TRUE) {
                        assertThat(it).doesNotContain(suggestion)
                    } else {
                        assertThat(it).contains(suggestion)
                    }

                    assertThat(it).contains("ArithmeticException:LintDriverCrashTest＄CrashingDetector＄createUastHandler＄1.visitFile(LintDriverCrashTest.kt:")
                    assertThat(it).contains("1 errors, 0 warnings")
                }
            )
        LintDriver.clearCrashCount()
    }

    fun testErrorThrownInSuperClass() {
        // Regression test for 263289356
        lint().files(
            xml("res/layout/foo.xml", "<LinearLayout/>"),
            java(
                """
                    package test.pkg;
                    @SuppressWarnings("ALL") class Foo {
                    }
                    """
            )
        )
            .allowSystemErrors(true)
            .allowExceptions(true)
            //.detector(CrashingInheritorDetector())
            .issues(CrashingInheritorDetector.CRASHING_ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .check(
                {
                    assertThat(it).contains("Foo.java: Error: Unexpected failure during lint analysis of Foo.java (this is a bug in lint or one of the libraries it depends on)")
                    assertThat(it).contains("The crash seems to involve the detector com.android.tools.lint.client.api.LintDriverCrashTest＄CrashingInheritorDetector.")
                    assertThat(it).contains(
                        """
                        The crash seems to involve the detector com.android.tools.lint.client.api.LintDriverCrashTest＄CrashingInheritorDetector.
                        You can try disabling it with something like this:
                            android {
                                lint {
                                    disable "_TestCrashInheritor"
                                }
                            }
                        """.trimIndent()
                    )
                }
            )
        LintDriver.clearCrashCount()
    }

    fun testSavePartialResults() {
        // Regression test for https://issuetracker.google.com/192484319
        // If a detector crashes, that should not invalidate any other results from the module

        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg" android:versionName="1.0">
                    <uses-sdk android:minSdkVersion="10" android:targetSdkVersion="31" />
                    <uses-sdk android:minSdkVersion="10" android:targetSdkVersion="31" />
                </manifest>
                """
            ).indented(),

            // Deliberately crashing lint check
            *JarFileIssueRegistryTest.lintApiStubs,

        )
            .allowSystemErrors(true)
            .allowExceptions(true)
            .testModes(TestMode.PARTIAL)
            .issues(CrashingDetector.CRASHING_ISSUE, ManifestDetector.MULTIPLE_USES_SDK)
            .run()
            .check(
                {
                    assertThat(it).contains("Unexpected failure during lint analysis")
                    assertThat(it).contains("(this is a bug in lint or one of the libraries it depends on)")
                    assertThat(it).contains("The crash seems to involve the detector com.android.tools.lint.client.api.LintDriverCrashTest＄CrashingDetector.")
                    assertThat(it).contains(
                        """
    AndroidManifest.xml:4: Error: There should only be a single <uses-sdk> element in the manifest: merge these together [MultipleUsesSdk]
        <uses-sdk android:minSdkVersion="10" android:targetSdkVersion="31" />
         ~~~~~~~~
        AndroidManifest.xml:3: Also appears here
        <uses-sdk android:minSdkVersion="10" android:targetSdkVersion="31" />
         ~~~~~~~~
                        """.trimIndent()
                    )
                }
            )

        // Make sure we really had a crash during that analysis
        assertTrue(LintDriver.crashCount > 0)

        LintDriver.clearCrashCount()
    }

    fun testLinkageError() {
        // Regression test for 34248502
        lint().files(
            java(
                """
                    package test.pkg;
                    @SuppressWarnings("ALL") class Foo {
                    }
                    """
            )
        )
            .allowSystemErrors(true)
            .allowExceptions(true)
            .issues(LinkageErrorDetector.LINKAGE_ERROR)
            .run()
            .expect(
                """
                    src/test/pkg/Foo.java: Error: Lint crashed because it is being invoked with the wrong version of Guava
                    (the Android version instead of the JRE version, which is required in the
                    Gradle plugin).

                    This usually happens when projects incorrectly install a dependency resolution
                    strategy in all configurations instead of just the compile and run
                    configurations.

                    See https://issuetracker.google.com/71991293 for more information and the
                    proper way to install a dependency resolution strategy.

                    (Note that this breaks a lot of lint analysis so this report is incomplete.) [LintError]
                    1 errors, 0 warnings"""
            )
        LintDriver.clearCrashCount()
    }

    fun testInitializationError() {
        // Regression test for 261757191
        lint().files(
            java(
                """
                    package test.pkg;
                    @SuppressWarnings("ALL") class Foo {
                    }
                    """
            )
        )
            .allowSystemErrors(true)
            .allowExceptions(true)
            .testModes(TestMode.DEFAULT)
            .issues(BrokenInitializationDetector.BROKEN_INIT)
            .run()
            .check(
                TestResultChecker {
                    assertThat(it).contains("app: Error: Can't initialize detector com.android.tools.lint.client.api.LintDriverCrashTest＄BrokenInitializationDetector.")
                    assertThat(it).contains("Unexpected failure during lint analysis (this is a bug in lint or one of the libraries it depends on)")
                    assertThat(it).contains("Stack: InvocationTargetException:NativeConstructorAccessorImpl.newInstance0(NativeConstructorAccessorImpl.java:")

                    // It's not easy to set environment variables from Java once the process is running,
                    // so instead of attempting to set it to true and false in tests, we'll just make this
                    // test adapt to what's set in the environment. On our CI tests, it should not be
                    // set, so the doesNotContain() assertion will be used. For developers on the lint team
                    // it's typically set so the contains() assertion will be used.
                    val suggestion = "You can run with --stacktrace or set environment variable LINT_PRINT_"
                    if (System.getenv("LINT_PRINT_STACKTRACE") == VALUE_TRUE) {
                        assertThat(it).doesNotContain(suggestion)
                    } else {
                        assertThat(it).contains(suggestion)
                    }

                    assertThat(it).contains("1 errors, 0 warnings")
                }
            )

        LintDriver.clearCrashCount()
    }

    fun testUnitTestErrors() {
        // Regression test for https://issuetracker.google.com/74058591
        // Make sure the test itself fails with an error, not just an exception pretty printed
        // into the output as used to be the case
        try {
            lint().files(
                java(
                    """
                        package test.pkg;
                        @SuppressWarnings("ALL") class Foo {
                        }
                        """
                )
            )
                .allowSystemErrors(true)
                .allowExceptions(false)
                .issues(LinkageErrorDetector.LINKAGE_ERROR)
                .run()
                .expect(
                    "<doesn't matter, we shouldn't get this far>"
                )
            fail("Expected LinkageError to be thrown")
        } catch (e: LinkageError) {
            // OK
            LintDriver.clearCrashCount()
        }
    }

    override fun getIssues(): List<Issue> = listOf(
        CrashingDetector.CRASHING_ISSUE,
        DisposedThrowingDetector.DISPOSED_ISSUE, LinkageErrorDetector.LINKAGE_ERROR
    )

    override fun getDetector(): Detector = CrashingDetector()

    open class CrashingDetector : Detector(), SourceCodeScanner {

        override fun getApplicableUastTypes(): List<Class<out UElement>> =
            listOf(UFile::class.java)

        override fun createUastHandler(context: JavaContext): UElementHandler =
            object : UElementHandler() {
                override fun visitFile(node: UFile) {
                    @Suppress("DIVISION_BY_ZERO", "UNUSED_VARIABLE") // Intentional crash
                    val x = 1 / 0
                    super.visitFile(node)
                }
            }

        companion object {
            @Suppress("LintImplTextFormat")
            val CRASHING_ISSUE = Issue
                .create(
                    "_TestCrash", "test", "test", Category.LINT, 10, Severity.FATAL,
                    Implementation(CrashingDetector::class.java, Scope.JAVA_FILE_SCOPE)
                )
        }
    }

    class CrashingInheritorDetector : CrashingDetector() {

        override fun createUastHandler(context: JavaContext): UElementHandler {
            val superHandler = super.createUastHandler(context)

            return object : UElementHandler() {
                override fun visitFile(node: UFile) = superHandler.visitFile(node)
            }
        }

        companion object {
            @Suppress("LintImplTextFormat")
            val CRASHING_ISSUE = Issue
                .create(
                    "_TestCrashInheritor", "test", "test", Category.LINT, 10, Severity.FATAL,
                    Implementation(CrashingInheritorDetector::class.java, Scope.JAVA_FILE_SCOPE)
                )
        }
    }

    // Regression test for https://issuetracker.google.com/123835101

    fun testHalfUppercaseColor2() {
        lint()
            .files(
                xml(
                    "res/drawable/drawable.xml",
                    """
          <vector xmlns:android="http://schemas.android.com/apk/res/android"
              android:height="800dp"
              android:viewportHeight="800"
              android:viewportWidth="800"
              android:width="800dp">
            <path
                android:fillColor="#ffe000"
                android:pathData="M644.161,530.032 L644.161,529.032
          C644.161,522.469,638.821,517.129,632.258,517.129 L24.807,517.129 L24.807,282.871
          L775.194,282.871 L775.194,517.129 L683.872,517.129
          C677.309,517.129,671.969,522.469,671.969,529.032 L671.969,530.032
          L644.161,530.032 Z"/>
            <path
                android:fillColor="#fff000"
                android:pathData="M644.161,530.032 L644.161,529.032
          C644.161,522.469,638.821,517.129,632.258,517.129 L24.807,517.129 L24.807,282.871
          L775.194,282.871 L775.194,517.129 L683.872,517.129
          C677.309,517.129,671.969,522.469,671.969,529.032 L671.969,530.032
          L644.161,530.032 Z"/>
            <path
                android:fillColor="#ffe000"
                android:pathData="M683.871,516.129 L774.193,516.129 L774.193,283.871 L25.807,283.871
          L25.807,516.129 L632.258,516.129
          C639.384,516.129,645.161,521.906,645.161,529.032 L670.968,529.032
          C670.968,521.906,676.745,516.129,683.871,516.129 Z"/>
          </vector>"""
                ).indented()
            )
            .issues(ColorCasingDetector.ISSUE_COLOR_CASING)
            .run()
            .expect(
                """
                res/drawable/drawable.xml:7: Warning: Should be using uppercase letters [_ColorCasing]
                      android:fillColor="#ffe000"
                                         ~~~~~~~
                res/drawable/drawable.xml:14: Warning: Should be using uppercase letters [_ColorCasing]
                      android:fillColor="#fff000"
                                         ~~~~~~~
                res/drawable/drawable.xml:21: Warning: Should be using uppercase letters [_ColorCasing]
                      android:fillColor="#ffe000"
                                         ~~~~~~~
                0 errors, 3 warnings
                """
            )
            .expectFixDiffs(
                """
                Autofix for res/drawable/drawable.xml line 7: Convert to uppercase:
                @@ -7 +7
                -       android:fillColor="#ffe000"
                +       android:fillColor="#FFE000"
                Autofix for res/drawable/drawable.xml line 14: Convert to uppercase:
                @@ -14 +14
                -       android:fillColor="#fff000"
                +       android:fillColor="#FFF000"
                Autofix for res/drawable/drawable.xml line 21: Convert to uppercase:
                @@ -21 +21
                -       android:fillColor="#ffe000"
                +       android:fillColor="#FFE000"
                """
            )
    }

    fun testAbsolutePaths() {
        if (isWindows()) {
            // This check does not run on Windows: the test infrastructure regexp only looks for unix paths
            return
        }

        lint()
            .files(
                xml(
                    "res/drawable/drawable.xml",
                    "<test/>"
                ).indented()
            )
            .issues(AbsPathTestDetector.ABS_PATH_ISSUE)
            .stripRoot(false)
            .run()
            .expect(
                """
                Found absolute path
                    TESTROOT/default/app/res/drawable/drawable.xml
                in a reported error message; this is discouraged because absolute
                paths do not play well with baselines, shared HTML reports, remote
                caching, etc. If you really want this, you can set the property
                `lint().allowAbsolutePathsInMessages(true)`.

                Error message was: `found error in TESTROOT/default/app/res/drawable/drawable.xml!`
                """,
                java.lang.AssertionError::class.java
            )

        // Allowing absolute paths
        lint()
            .files(
                xml(
                    "res/drawable/drawable.xml",
                    "<test/>"
                ).indented()
            )
            .issues(AbsPathTestDetector.ABS_PATH_ISSUE)
            .stripRoot(false)
            .allowAbsolutePathsInMessages(true)
            .run()
            .expectCount(1, Severity.WARNING)
    }

    // Invalid detector which includes absolute paths in error messages which should not be done
    class AbsPathTestDetector : ResourceXmlDetector() {
        override fun appliesTo(folderType: ResourceFolderType) = true
        override fun afterCheckFile(context: Context) {
            context.report(ABS_PATH_ISSUE, Location.create(context.file), "found error in " + context.file + "!")
        }

        companion object {
            val ABS_PATH_ISSUE = Issue.create(
                "_AbsPath",
                "Sample",
                "Sample",
                Category.CORRECTNESS, 5, Severity.WARNING,
                Implementation(AbsPathTestDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
            )
        }
    }

    class ColorCasingDetector : ResourceXmlDetector() {
        override fun appliesTo(folderType: ResourceFolderType) = true
        override fun getApplicableElements(): List<String> = ALL
        override fun visitElement(context: XmlContext, element: Element) {
            element.attributes()
                .filter { it.nodeValue.matches(COLOR_REGEX) }
                .filter { it.nodeValue.any { c -> c.isLowerCase() } }
                .forEach {
                    val fix = fix()
                        .name("Convert to uppercase")
                        .replace()
                        // .range(context.getValueLocation(it as Attr))
                        .text(it.nodeValue)
                        .with(it.nodeValue.uppercase(Locale.US))
                        .autoFix()
                        .build()

                    context.report(
                        ISSUE_COLOR_CASING, it, context.getValueLocation(it as Attr),
                        "Should be using uppercase letters", fix
                    )
                }
        }

        companion object {
            val COLOR_REGEX = Regex("#[a-fA-F\\d]{3,8}")

            @Suppress("LintImplTextFormat")
            val ISSUE_COLOR_CASING = Issue.create(
                "_ColorCasing",
                "Raw colors should be defined with uppercase letters.",
                "Colors should have uppercase letters. #FF0099 is valid while #ff0099 isn't since the ff should be written in uppercase.",
                Category.CORRECTNESS, 5, Severity.WARNING,
                Implementation(ColorCasingDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
            )

            internal fun Node.attributes() = (0 until attributes.length).map { attributes.item(it) }
        }
    }

    class DisposedThrowingDetector : LayoutDetector(), XmlScanner {

        override fun getApplicableElements(): Collection<String> {
            return arrayListOf("LinearLayout")
        }

        override fun visitElement(context: XmlContext, element: Element) {
            throw AssertionError("Already disposed: $this")
        }

        companion object {
            @Suppress("LintImplTextFormat")
            val DISPOSED_ISSUE = Issue.create(
                "_TestDisposed", "test", "test", Category.LINT,
                10, Severity.FATAL,
                Implementation(
                    DisposedThrowingDetector::class.java,
                    Scope.RESOURCE_FILE_SCOPE
                )
            )
        }
    }

    class LinkageErrorDetector : Detector(), SourceCodeScanner {

        override fun getApplicableUastTypes(): List<Class<out UElement>> =
            listOf(UFile::class.java)

        override fun createUastHandler(context: JavaContext): UElementHandler =
            object : UElementHandler() {
                override fun visitFile(node: UFile) {
                    throw LinkageError(
                        "loader constraint violation: when resolving field " +
                            "\"QUALIFIER_SPLITTER\" the class loader (instance of " +
                            "com/android/tools/lint/gradle/api/DelegatingClassLoader) of the " +
                            "referring class, " +
                            "com/android/ide/common/resources/configuration/FolderConfiguration, " +
                            "and the class loader (instance of " +
                            "org/gradle/internal/classloader/VisitableURLClassLoader) for the " +
                            "field's resolved type, com/google/common/base/Splitter, have " +
                            "different Class objects for that type"
                    )
                }
            }

        companion object {
            @Suppress("LintImplTextFormat")
            val LINKAGE_ERROR = Issue
                .create(
                    "_LinkageCrash", "test", "test", Category.LINT, 10, Severity.FATAL,
                    Implementation(LinkageErrorDetector::class.java, Scope.JAVA_FILE_SCOPE)
                )
        }
    }

    class BrokenInitializationDetector : Detector(), SourceCodeScanner {
        init {
            error("Intentional breakage")
        }

        override fun getApplicableUastTypes(): List<Class<out UElement>> =
            listOf(UFile::class.java)

        override fun createUastHandler(context: JavaContext): UElementHandler =
            object : UElementHandler() {
                override fun visitFile(node: UFile) {
                }
            }

        companion object {
            @Suppress("LintImplTextFormat")
            val BROKEN_INIT = Issue
                .create(
                    "_InitCrash", "test", "test", Category.LINT, 10, Severity.FATAL,
                    Implementation(BrokenInitializationDetector::class.java, Scope.JAVA_FILE_SCOPE)
                )
        }
    }
}
