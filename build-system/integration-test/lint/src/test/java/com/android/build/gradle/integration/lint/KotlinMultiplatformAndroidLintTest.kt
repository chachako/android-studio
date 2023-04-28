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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class KotlinMultiplatformAndroidLintTest {
    @Suppress("DEPRECATION") // kmp doesn't support configuration caching for now (b/276472789)
    @get:Rule
    val project = GradleTestProjectBuilder()
        .fromTestProject("kotlinMultiplatform")
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
        .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin {
                    androidExperimental {
                        onMainCompilation {
                            compilerOptions.configure {
                                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
                            }
                        }

                        options {
                            isTestMultiDexEnabled = true
                             lint {
                                disable += "GradleDependency" // such that we don't flag newly available Kotlin versions etc
                                textReport = true
                                abortOnError = true
                            }
                        }
                    }
                }
            """.trimIndent())
    }

    @Test
    fun `test lint reports error when calling desugared apis in androidMain sourceset`() {
        TestFileUtils.addMethod(
            FileUtils.join(
                project.getSubproject("kmpFirstLib").projectDir,
                "src", "androidMain", "kotlin", "com", "example", "kmpfirstlib", "KmpAndroidFirstLibClass.kt"
            ),
            """
                fun desugaringTestUsingDate() {
                    val date = LocalDate.now().month.name
                }
            """.trimIndent())

        // Run twice to catch issues with configuration caching
        project.executor().expectFailure().run(":kmpFirstLib:clean", ":kmpFirstLib:lintAndroidMain")
        project.executor().expectFailure().run(":kmpFirstLib:clean", ":kmpFirstLib:lintAndroidMain")

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
        TestFileUtils.addMethod(
            FileUtils.join(
                project.getSubproject("kmpFirstLib").projectDir,
                "src", "commonMain", "kotlin", "com", "example", "kmpfirstlib", "KmpCommonFirstLibClass.kt"
            ),
            """
                fun desugaringTestUsingDate() {
                    val date = LocalDate.now().month.name
                }
            """.trimIndent())

        // Run twice to catch issues with configuration caching
        project.executor().expectFailure().run(":kmpFirstLib:clean", ":kmpFirstLib:lintAndroidMain")
        project.executor().expectFailure().run(":kmpFirstLib:clean", ":kmpFirstLib:lintAndroidMain")

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
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin {
                    androidExperimental {
                        options {
                            lint {
                                checkTestSources = true
                            }
                        }
                    }
                }
            """.trimIndent())

        TestFileUtils.addMethod(
            FileUtils.join(
                project.getSubproject("kmpFirstLib").projectDir,
                "src", "androidUnitTest", "kotlin", "com", "example", "kmpfirstlib", "KmpAndroidFirstLibClassTest.kt"
            ),
            """
                @Test
                fun desugaringTestUsingDate() {
                    val date = LocalDate.now().month.name
                }
            """.trimIndent())

        // Run twice to catch issues with configuration caching
        project.executor().expectFailure().run(":kmpFirstLib:clean", ":kmpFirstLib:lintAndroidMain")
        project.executor().expectFailure().run(":kmpFirstLib:clean", ":kmpFirstLib:lintAndroidMain")

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
                    }
                }
            """.trimIndent())

        TestFileUtils.addMethod(
            FileUtils.join(
                project.getSubproject("kmpFirstLib").projectDir,
                "src", "androidMain", "kotlin", "com", "example", "kmpfirstlib", "KmpAndroidFirstLibClass.kt"
            ),
            """
                fun desugaringTestUsingDate() {
                    val date = LocalDate.now().month.name
                }
            """.trimIndent())

        // Run twice to catch issues with configuration caching
        project.executor().expectFailure().run(":app:clean", ":app:lintDebug")
        project.executor().expectFailure().run(":app:clean", ":app:lintDebug")

        val reportFile =
            File(project.getSubproject("app").buildDir, "reports/lint-results-debug.txt")

        PathSubject.assertThat(reportFile).exists()
        PathSubject.assertThat(reportFile).containsAllOf(
            "Error: Call requires API level 26 (current min is 24): java.time.LocalDate#getMonth [NewApi]",
            "Error: Call requires API level 26 (current min is 24): java.time.LocalDate#now [NewApi]"
        )
    }
}
