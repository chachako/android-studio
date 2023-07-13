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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.integration.common.truth.forEachLine
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption.LINT_ANALYSIS_PER_COMPONENT
import com.android.build.gradle.options.BooleanOption.LINT_USE_K2_UAST
import com.android.build.gradle.options.StringOption.LINT_RESERVED_MEMORY_PER_TASK
import com.android.testutils.truth.PathSubject
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class KotlinMultiplatformAndroidLintTest(private val lintAnalysisPerComponent: Boolean) {
    @get:Rule
    val project = GradleTestProjectBuilder()
        .fromTestProject("kotlinMultiplatform")
        .create()

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "lintAnalysisPerComponent_{0}")
        fun params() = listOf(true, false)

        const val byteOrderMark = "\ufeff"
    }

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin {
                    androidLibrary {
                        compilations.all {
                            compilerOptions.configure {
                                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
                            }
                        }

                        lint {
                            enable += "ByteOrderMark"
                            textReport = true
                            abortOnError = false
                        }
                    }
                }
            """.trimIndent())
    }

    @Test
    fun `test lint reports error when calling desugared apis in androidMain sourceset`() {
        Assume.assumeTrue(lintAnalysisPerComponent)
        addNewApiIssuesToKmpFirstLib(addAndroidMainIssues = true)

        getExecutor().run(":kmpFirstLib:clean", ":kmpFirstLib:lintAndroidMain")

        val reportFile =
            File(project.getSubproject("kmpFirstLib").buildDir, "reports/lint-results-androidMain.txt")

        PathSubject.assertThat(reportFile).exists()
        PathSubject.assertThat(reportFile).containsAllOf(
            "Error: Call requires API level 26 (current min is 22): java.time.LocalDate#getMonth [NewApi]",
            "Error: Call requires API level 26 (current min is 22): java.time.LocalDate#now [NewApi]"
        )
    }

    @Test
    fun `test lint reports error when calling desugared apis in commonMain sourceset`() {
        Assume.assumeTrue(lintAnalysisPerComponent)
        addNewApiIssuesToKmpFirstLib(addCommonMainIssues = true)

        getExecutor().run(":kmpFirstLib:clean", ":kmpFirstLib:lintAndroidMain")

        val reportFile =
            File(project.getSubproject("kmpFirstLib").buildDir, "reports/lint-results-androidMain.txt")

        PathSubject.assertThat(reportFile).exists()
        PathSubject.assertThat(reportFile).containsAllOf(
            "Error: Call requires API level 26 (current min is 22): java.time.LocalDate#getMonth [NewApi]",
            "Error: Call requires API level 26 (current min is 22): java.time.LocalDate#now [NewApi]"
        )
    }

    @Test
    fun `test lint reports error when calling desugared apis in android unitTest sourceset`() {
        Assume.assumeTrue(lintAnalysisPerComponent)
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin {
                    androidLibrary {
                        lint {
                            checkTestSources = true
                        }
                    }
                }
            """.trimIndent())

        addNewApiIssuesToKmpFirstLib(addAndroidUnitTestIssues = true)

        getExecutor().run(":kmpFirstLib:clean", ":kmpFirstLib:lintAndroidMain")

        val reportFile =
            File(project.getSubproject("kmpFirstLib").buildDir, "reports/lint-results-androidMain.txt")

        PathSubject.assertThat(reportFile).exists()
        PathSubject.assertThat(reportFile).containsAllOf(
            "Error: Call requires API level 26 (current min is 22): java.time.LocalDate#getMonth [NewApi]",
            "Error: Call requires API level 26 (current min is 22): java.time.LocalDate#now [NewApi]"
        )
    }

    @Test
    fun `test app checkDependencies works`() {
        TestFileUtils.appendToFile(
            project.getSubproject("app").ktsBuildFile,
            """
                android {
                    defaultConfig {
                        minSdk = 24
                    }
                    lint {
                        checkDependencies = true
                        textReport = true
                        abortOnError = false
                    }
                }
            """.trimIndent())

        // Change dependency on kmpJvmOnly to api dependency; lint won't report issues otherwise.
        TestFileUtils.searchAndReplace(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            "implementation(project(\":kmpJvmOnly\"))",
            "api(project(\":kmpJvmOnly\"))"
        )

        addNewApiIssuesToKmpFirstLib(addAndroidMainIssues = true)

        // Add ByteOrderMark to kmpJvmOnly code
        TestFileUtils.addMethod(
            FileUtils.join(
                project.getSubproject("kmpJvmOnly").projectDir,
                "src",
                "jvmMain",
                "kotlin",
                "com",
                "example",
                "kmpjvmonly",
                "KmpJvmOnlyLibClass.kt"
            ),
            //language=kotlin
            """
                fun getByteOrderMark(): String {
                    return "$byteOrderMark"
                }
            """.trimIndent()
        )

        getExecutor().run(":app:clean", ":app:lintDebug")

        val reportFile =
            File(project.getSubproject("app").buildDir, "reports/lint-results-debug.txt")

        PathSubject.assertThat(reportFile).exists()
        PathSubject.assertThat(reportFile).containsAllOf(
            "Error: Call requires API level 26 (current min is 24): java.time.LocalDate#getMonth [NewApi]",
            "Error: Call requires API level 26 (current min is 24): java.time.LocalDate#now [NewApi]",
            "Found byte-order-mark in the middle of a file [ByteOrderMark]"
        )
    }

    @Test
    fun `test running lint on kmpJvmOnly`() {
        Assume.assumeTrue(lintAnalysisPerComponent)
        TestFileUtils.appendToFile(
            project.getSubproject("kmpJvmOnly").ktsBuildFile,
            """
                lint {
                    enable += "ByteOrderMark"
                    textReport = true
                    abortOnError = false
                }
            """.trimIndent())

        // Add ByteOrderMark to kmpJvmOnly code
        TestFileUtils.addMethod(
            FileUtils.join(
                project.getSubproject("kmpJvmOnly").projectDir,
                "src",
                "jvmMain",
                "kotlin",
                "com",
                "example",
                "kmpjvmonly",
                "KmpJvmOnlyLibClass.kt"
            ),
            //language=kotlin
            """
                fun getByteOrderMark(): String {
                    return "$byteOrderMark"
                }
            """.trimIndent()
        )

        getExecutor().run(":kmpJvmOnly:clean", ":kmpJvmOnly:lint")

        val reportFile =
            File(project.getSubproject("kmpJvmOnly").buildDir, "reports/lint-results.txt")

        PathSubject.assertThat(reportFile).exists()
        PathSubject.assertThat(reportFile)
            .contains("Found byte-order-mark in the middle of a file [ByteOrderMark]")
    }

    @Test
    fun `test running lint on kmpJvmOnly with multiple jvm targets`() {
        Assume.assumeTrue(lintAnalysisPerComponent)
        // Modify kmpJvmOnly to have 2 jvm targets, "jvm" and "desktop"
        TestFileUtils.searchAndReplace(
            project.getSubproject("kmpJvmOnly").ktsBuildFile,
            "jvm()",
            """
                jvm() {
                    attributes.attribute(
                        Attribute.of("com.example.foo", String::class.java),
                        "jvm"
                    )
                }
                jvm("desktop") {
                    attributes.attribute(
                        Attribute.of("com.example.foo", String::class.java),
                        "desktop"
                    )
                }
            """.trimIndent()
        )
        TestFileUtils.appendToFile(
            project.getSubproject("kmpJvmOnly").ktsBuildFile,
            """
                lint {
                    enable += "ByteOrderMark"
                    textReport = true
                    abortOnError = false
                }
            """.trimIndent()
        )

        // Add a desktopMain source file with ByteOrderMark issue
        val jvmClassFile =
            FileUtils.join(
                project.getSubproject("kmpJvmOnly").projectDir,
                "src",
                "desktopMain",
                "kotlin",
                "com",
                "example",
                "Foo.kt"
            )
        jvmClassFile.parentFile.mkdirs()
        TestFileUtils.appendToFile(
            jvmClassFile,
            //language=kotlin
            """
                package com.example

                fun getByteOrderMark(): String {
                    return "$byteOrderMark"
                }
            """.trimIndent()
        )

        // Add ByteOrderMark to jvmMain source file
        TestFileUtils.addMethod(
            FileUtils.join(
                project.getSubproject("kmpJvmOnly").projectDir,
                "src",
                "jvmMain",
                "kotlin",
                "com",
                "example",
                "kmpjvmonly",
                "KmpJvmOnlyLibClass.kt"
            ),
            //language=kotlin
            """
                fun getByteOrderMark(): String {
                    return "$byteOrderMark"
                }
            """.trimIndent()
        )


        getExecutor().run(":kmpJvmOnly:clean", ":kmpJvmOnly:lint")

        val reportFile =
            File(project.getSubproject("kmpJvmOnly").buildDir, "reports/lint-results.txt")

        PathSubject.assertThat(reportFile).exists()
        PathSubject.assertThat(reportFile)
            .contains("Foo.kt:5: Error: Found byte-order-mark in the middle of a file [ByteOrderMark]")
        PathSubject.assertThat(reportFile)
            .contains("KmpJvmOnlyLibClass.kt:9: Error: Found byte-order-mark in the middle of a file [ByteOrderMark]")
    }

    @Test
    fun `test running lint on app with dependency with multiple jvm targets`() {
        // Modify kmpJvmOnly to have 2 jvm targets, "jvm" and "desktop"
        TestFileUtils.searchAndReplace(
            project.getSubproject("kmpJvmOnly").ktsBuildFile,
            "jvm()",
            """
                jvm() {
                    attributes.attribute(
                        Attribute.of("com.example.foo", String::class.java),
                        "jvm"
                    )
                }
                jvm("desktop") {
                    attributes.attribute(
                        Attribute.of("com.example.foo", String::class.java),
                        "desktop"
                    )
                }
            """.trimIndent()
        )

        // Set "com.example.foo" attribute to "jvm" in kmpFirstLib and app because otherwise there
        // will be ambiguity about which artifacts to consume from kmpJvmOnly
        val jvmAttributeBlurb =
            """
                val jvmAttribute = Attribute.of("com.example.foo", String::class.java)

                dependencies {
                    attributesSchema {
                        attribute(jvmAttribute)
                    }
                }

                configurations.all {
                    if (isCanBeResolved) {
                        attributes {
                            attribute(jvmAttribute, "jvm")
                        }
                    }
                }
            """.trimIndent()
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            jvmAttributeBlurb
        )
        TestFileUtils.appendToFile(
            project.getSubproject("app").ktsBuildFile,
            jvmAttributeBlurb
        )

        TestFileUtils.appendToFile(
            project.getSubproject("kmpJvmOnly").ktsBuildFile,
            """
                lint {
                    enable += "ByteOrderMark"
                    textReport = true
                    abortOnError = false
                }
            """.trimIndent()
        )
        TestFileUtils.appendToFile(
            project.getSubproject("app").ktsBuildFile,
            """
                android {
                    defaultConfig {
                        minSdk = 24
                    }
                    lint {
                        checkDependencies = true
                        textReport = true
                        abortOnError = false
                    }
                }
            """.trimIndent())

        // Add ByteOrderMark to jvmMain source file
        TestFileUtils.addMethod(
            FileUtils.join(
                project.getSubproject("kmpJvmOnly").projectDir,
                "src",
                "jvmMain",
                "kotlin",
                "com",
                "example",
                "kmpjvmonly",
                "KmpJvmOnlyLibClass.kt"
            ),
            //language=kotlin
            """
                fun getByteOrderMark(): String {
                    return "$byteOrderMark"
                }
            """.trimIndent()
        )

        getExecutor().run(":app:clean", ":app:lintDebug")

        val reportFile =
            File(project.getSubproject("app").buildDir, "reports/lint-results-debug.txt")

        PathSubject.assertThat(reportFile).exists()
        PathSubject.assertThat(reportFile)
            .contains("KmpJvmOnlyLibClass.kt:9: Error: Found byte-order-mark in the middle of a file [ByteOrderMark]")
    }

    @Test
    fun `test running lint on kmpJvmOnly with java`() {
        Assume.assumeTrue(lintAnalysisPerComponent)
        TestFileUtils.searchAndReplace(
            project.getSubproject("kmpJvmOnly").ktsBuildFile,
            "jvm()",
            """
                jvm {
                    withJava()
                }
            """.trimIndent()

        )
        TestFileUtils.appendToFile(
            project.getSubproject("kmpJvmOnly").ktsBuildFile,
            """
                lint {
                    enable += "ByteOrderMark"
                    textReport = true
                    abortOnError = false
                }
            """.trimIndent())

        val javaFile =
            FileUtils.join(
                project.getSubproject("kmpJvmOnly").projectDir,
                "src",
                "jvmMain",
                "java",
                "com",
                "example",
                "Foo.java"
            )
        javaFile.parentFile.mkdirs()
        TestFileUtils.appendToFile(
            javaFile,
            //language=java
            """
                package com.example;

                public class Foo {
                    private String foo = "$byteOrderMark";
                }
            """.trimIndent()
        )

        getExecutor().run(":kmpJvmOnly:clean", ":kmpJvmOnly:lint")

        val reportFile =
            File(project.getSubproject("kmpJvmOnly").buildDir, "reports/lint-results.txt")

        PathSubject.assertThat(reportFile).exists()
        PathSubject.assertThat(reportFile)
            .contains("Found byte-order-mark in the middle of a file [ByteOrderMark]")
    }

    @Test
    fun `test running lint on project with jvm and android targets`() {
        Assume.assumeTrue(lintAnalysisPerComponent)
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin {
                    jvm()
                }
                lint {
                    enable += "ByteOrderMark"
                    textReport = true
                    abortOnError = false
                }
            """.trimIndent())

        addNewApiIssuesToKmpFirstLib(addAndroidMainIssues = true)

        val jvmClassFile =
            FileUtils.join(
                project.getSubproject("kmpFirstLib").projectDir,
                "src",
                "jvmMain",
                "kotlin",
                "com",
                "example",
                "Foo.kt"
            )
        jvmClassFile.parentFile.mkdirs()
        TestFileUtils.appendToFile(
            jvmClassFile,
            //language=kotlin
            """
                package com.example

                fun getByteOrderMark(): String {
                    return "$byteOrderMark"
                }
            """.trimIndent()
        )

        getExecutor().run(":kmpFirstLib:clean", ":kmpFirstLib:lint")

        val jvmReportFile =
            File(project.getSubproject("kmpFirstLib").buildDir, "reports/lint-results.txt")
        PathSubject.assertThat(jvmReportFile).exists()
        PathSubject.assertThat(jvmReportFile)
            .contains("Found byte-order-mark in the middle of a file [ByteOrderMark]")

        val androidrReportFile =
            File(
                project.getSubproject("kmpFirstLib").buildDir,
                "reports/lint-results-androidMain.txt"
            )
        PathSubject.assertThat(androidrReportFile).exists()
        PathSubject.assertThat(androidrReportFile).containsAllOf(
            "Error: Call requires API level 26 (current min is 22): java.time.LocalDate#getMonth [NewApi]",
            "Error: Call requires API level 26 (current min is 22): java.time.LocalDate#now [NewApi]"
        )
    }

    @Test
    fun `test updating lint baselines on project with jvm and android targets`() {
        Assume.assumeTrue(lintAnalysisPerComponent)
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin {
                    jvm()
                    androidLibrary {
                        lint {
                            baseline = file("android-lint-baseline.xml")
                        }
                    }
                }
                lint {
                    enable += "ByteOrderMark"
                    textReport = true
                    abortOnError = false
                    baseline = file("jvm-lint-baseline.xml")
                }
            """.trimIndent())

        addNewApiIssuesToKmpFirstLib(addAndroidMainIssues = true)

        val jvmClassFile =
            FileUtils.join(
                project.getSubproject("kmpFirstLib").projectDir,
                "src",
                "jvmMain",
                "kotlin",
                "com",
                "example",
                "Foo.kt"
            )
        jvmClassFile.parentFile.mkdirs()
        TestFileUtils.appendToFile(
            jvmClassFile,
            //language=kotlin
            """
                package com.example

                fun getByteOrderMark(): String {
                    return "$byteOrderMark"
                }
            """.trimIndent()
        )

        val jvmBaselineFile =
            File(project.getSubproject("kmpFirstLib").projectDir, "jvm-lint-baseline.xml")
        val androidBaselineFile =
            File(project.getSubproject("kmpFirstLib").projectDir, "android-lint-baseline.xml")
        PathSubject.assertThat(jvmBaselineFile).doesNotExist()
        PathSubject.assertThat(androidBaselineFile).doesNotExist()

        getExecutor().run(":kmpFirstLib:clean", ":kmpFirstLib:updateLintBaseline")

        PathSubject.assertThat(jvmBaselineFile).contains("ByteOrderMark")
        PathSubject.assertThat(jvmBaselineFile).doesNotContain("NewApi")
        PathSubject.assertThat(androidBaselineFile).contains("NewApi")
        PathSubject.assertThat(androidBaselineFile).doesNotContain("ByteOrderMark")
    }

    @Test
    fun `test running lint on app with KMP dependency with jvm and android targets`() {
        TestFileUtils.appendToFile(
            project.getSubproject("app").ktsBuildFile,
            """
                android {
                    defaultConfig {
                        minSdk = 24
                    }
                    lint {
                        checkDependencies = true
                        textReport = true
                        abortOnError = false
                    }
                }
            """.trimIndent())

        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin {
                    jvm()
                }
                lint {
                    enable += "ByteOrderMark"
                    textReport = true
                    abortOnError = false
                }
            """.trimIndent())

        addNewApiIssuesToKmpFirstLib(addAndroidMainIssues = true)

        val jvmClassFile =
            FileUtils.join(
                project.getSubproject("kmpFirstLib").projectDir,
                "src",
                "jvmMain",
                "kotlin",
                "com",
                "example",
                "Foo.kt"
            )
        jvmClassFile.parentFile.mkdirs()
        TestFileUtils.appendToFile(
            jvmClassFile,
            //language=kotlin
            """
                package com.example

                fun getByteOrderMark(): String {
                    return "$byteOrderMark"
                }
            """.trimIndent()
        )

        getExecutor().run(":app:clean", ":app:lintDebug")

        val reportFile =
            File(project.getSubproject("app").buildDir, "reports/lint-results-debug.txt")

        PathSubject.assertThat(reportFile).exists()
        PathSubject.assertThat(reportFile).containsAllOf(
            "Error: Call requires API level 26 (current min is 24): java.time.LocalDate#getMonth [NewApi]",
            "Error: Call requires API level 26 (current min is 24): java.time.LocalDate#now [NewApi]"
        )
        // We don't expect to see non-Android lint issues in downstream lint reports.
        PathSubject.assertThat(reportFile).doesNotContain(
            "Found byte-order-mark in the middle of a file [ByteOrderMark]"
        )
    }

    @Test
    fun `test no lint tasks if lint plugin not applied`() {
        Assume.assumeTrue(lintAnalysisPerComponent)
        getExecutor().run("kmpFirstLib:tasks").stdout.use {
            assertThat(it).contains("lint -")
            assertThat(it).contains("lintAndroidMain")
            assertThat(it).contains("lintFix")
            assertThat(it).contains("lintJvm")
        }
        TestFileUtils.searchAndReplace(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            "id(\"com.android.lint\")",
            "",
        )
        getExecutor().run("kmpFirstLib:tasks").stdout.use { scanner ->
            scanner.forEachLine {
                Truth.assertThat(it).doesNotContain("lint -")
                Truth.assertThat(it).doesNotContain("lintAndroidMain")
                Truth.assertThat(it).doesNotContain("lintFix")
                Truth.assertThat(it).doesNotContain("lintJvm")
            }
        }
    }

    @Test
    fun `test K2 UAST`() {
        TestFileUtils.appendToFile(
            project.getSubproject("app").ktsBuildFile,
            """
                android {
                    defaultConfig {
                        minSdk = 24
                    }
                    lint {
                        checkDependencies = true
                        textReport = true
                        abortOnError = false
                    }
                }
            """.trimIndent())

        addNewApiIssuesToKmpFirstLib(addCommonMainIssues = true)

        getExecutor().with(LINT_USE_K2_UAST, true).run(":app:clean", ":app:lintDebug")

        val reportFile =
            File(project.getSubproject("app").buildDir, "reports/lint-results-debug.txt")

        PathSubject.assertThat(reportFile).exists()
        PathSubject.assertThat(reportFile).containsAllOf(
            "Error: Call requires API level 26 (current min is 24): java.time.LocalDate#getMonth [NewApi]",
            "Error: Call requires API level 26 (current min is 24): java.time.LocalDate#now [NewApi]"
        )
        PathSubject.assertThat(reportFile).doesNotContain("[LintError]")
    }

    private fun addNewApiIssuesToKmpFirstLib(
        addCommonMainIssues: Boolean = false,
        addAndroidMainIssues: Boolean = false,
        addAndroidUnitTestIssues: Boolean = false
    ) {
        if (addCommonMainIssues) {
            TestFileUtils.addMethod(
                FileUtils.join(
                    project.getSubproject("kmpFirstLib").projectDir,
                    "src",
                    "commonMain",
                    "kotlin",
                    "com",
                    "example",
                    "kmpfirstlib",
                    "KmpCommonFirstLibClass.kt"
                ),
                """
                fun desugaringTestUsingDate() {
                    val date = LocalDate.now().month.name
                }
            """.trimIndent())
        }
        if (addAndroidMainIssues) {
            TestFileUtils.addMethod(
                FileUtils.join(
                    project.getSubproject("kmpFirstLib").projectDir,
                    "src",
                    "androidMain",
                    "kotlin",
                    "com",
                    "example",
                    "kmpfirstlib",
                    "KmpAndroidFirstLibClass.kt"
                ),
                """
                fun desugaringTestUsingDate() {
                    val date = LocalDate.now().month.name
                }
            """.trimIndent())
        }
        if (addAndroidUnitTestIssues) {

            TestFileUtils.addMethod(
                FileUtils.join(
                    project.getSubproject("kmpFirstLib").projectDir,
                    "src",
                    "androidUnitTest",
                    "kotlin",
                    "com",
                    "example",
                    "kmpfirstlib",
                    "KmpAndroidFirstLibClassTest.kt"
                ),
                """
                @Test
                fun desugaringTestUsingDate() {
                    val date = LocalDate.now().month.name
                }
            """.trimIndent())
        }
    }

    private fun getExecutor(): GradleTaskExecutor {
        // Set LINT_RESERVED_MEMORY_PER_TASK to "256M" to reduce time required to run the tests
        // TODO (b/293964676): remove withFailOnWarning(false) once KMP bug is fixed
        return project.executor()
            .with(LINT_ANALYSIS_PER_COMPONENT, lintAnalysisPerComponent)
            .with(LINT_RESERVED_MEMORY_PER_TASK, "256M")
            .withFailOnWarning(false)
    }
}
