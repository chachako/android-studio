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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.dsl.ModulePropertyKey.OptionalBoolean
import com.android.build.gradle.options.OptionalBooleanOption
import org.junit.Rule
import org.junit.Test

/** Integration test checking for alignment of language version and UAST used by lint. */
class LintAlignUastWithLanguageVersionTest {

    @get:Rule
    val project: GradleTestProject = createGradleTestProject()

    /**
     * Test default behavior
     */
    @Test
    fun testDefaultBehavior() {

        addLanguageVersionsToProject(
            appExpectedLanguageVersion = "2.0",
            libExpectedLanguageVersion = "2.0",
            featureExpectedLanguageVersion = "2.0",
            javaLibExpectedLanguageVersion = null,
            kotlinLibExpectedLanguageVersion = "2.0",
            kmpAndroidLibExpectedLanguageVersion = "2.0",
            kmpJvmLibExpectedLanguageVersion = "2.0",
            appExpectedUseK2Uast = true,
            libExpectedUseK2Uast = true,
            featureExpectedUseK2Uast = true,
            javaLibExpectedUseK2Uast = false,
            kotlinLibExpectedUseK2Uast = true,
            kmpAndroidLibExpectedUseK2Uast = true,
            kmpJvmLibExpectedUseK2Uast = true
        )

        project.executor().run("clean", "lint")
        val result = project.executor().run("clean", "lint")
        result.assertConfigurationCacheHit()
    }

    /**
     * Test kotlin language version 1.9.
     */
    @Test
    fun testOldLanguageVersion() {

        addLanguageVersionsToProject(
            appExpectedLanguageVersion = "1.9",
            libExpectedLanguageVersion = "1.9",
            featureExpectedLanguageVersion = "1.9",
            javaLibExpectedLanguageVersion = null,
            kotlinLibExpectedLanguageVersion = "1.9",
            kmpAndroidLibExpectedLanguageVersion = "1.9",
            kmpJvmLibExpectedLanguageVersion = "1.9",
            appExpectedUseK2Uast = false,
            libExpectedUseK2Uast = false,
            featureExpectedUseK2Uast = false,
            javaLibExpectedUseK2Uast = false,
            kotlinLibExpectedUseK2Uast = false,
            kmpAndroidLibExpectedUseK2Uast = false,
            kmpJvmLibExpectedUseK2Uast = false,
            sourceSetsLanguageVersion = "1.9"
        )

        project.executor().run("clean", "lint")
    }

    /**
     * Test incrementing kotlin language version.
     */
    @Test
    fun testKotlinExperimentalTryNext() {

        addLanguageVersionsToProject(
            appExpectedLanguageVersion = "2.1",
            libExpectedLanguageVersion = "2.1",
            featureExpectedLanguageVersion = "2.1",
            javaLibExpectedLanguageVersion = null,
            kotlinLibExpectedLanguageVersion = "2.1",
            kmpAndroidLibExpectedLanguageVersion = "2.1",
            kmpJvmLibExpectedLanguageVersion = "2.1",
            appExpectedUseK2Uast = true,
            libExpectedUseK2Uast = true,
            featureExpectedUseK2Uast = true,
            javaLibExpectedUseK2Uast = false,
            kotlinLibExpectedUseK2Uast = true,
            kmpAndroidLibExpectedUseK2Uast = true,
            kmpJvmLibExpectedUseK2Uast = true,
        )

        project.executor()
            .withArgument("-Pkotlin.experimental.tryNext=true")
            .run("clean", "lint")
    }

    /**
     * Test enabling K2 UAST for all modules
     */
    @Test
    fun testUseK2UastGlobally() {

        addLanguageVersionsToProject(
            appExpectedLanguageVersion = "1.9",
            libExpectedLanguageVersion = "1.9",
            featureExpectedLanguageVersion = "1.9",
            javaLibExpectedLanguageVersion = null,
            kotlinLibExpectedLanguageVersion = "1.9",
            kmpAndroidLibExpectedLanguageVersion = "1.9",
            kmpJvmLibExpectedLanguageVersion = "1.9",
            appExpectedUseK2Uast = true,
            libExpectedUseK2Uast = true,
            featureExpectedUseK2Uast = true,
            javaLibExpectedUseK2Uast = true,
            kotlinLibExpectedUseK2Uast = true,
            kmpAndroidLibExpectedUseK2Uast = true,
            kmpJvmLibExpectedUseK2Uast = true,
            sourceSetsLanguageVersion = "1.9"
        )

        project.executor()
            .with(OptionalBooleanOption.LINT_USE_K2_UAST, true)
            .run("clean", "lint")
    }

    /**
     * Test enabling K2 UAST for a single module (the app module)
     */
    @Test
    fun testUseK2UastInAppOnly() {

        addLanguageVersionsToProject(
            appExpectedLanguageVersion = "1.9",
            libExpectedLanguageVersion = "1.9",
            featureExpectedLanguageVersion = "1.9",
            javaLibExpectedLanguageVersion = null,
            kotlinLibExpectedLanguageVersion = "1.9",
            kmpAndroidLibExpectedLanguageVersion = "1.9",
            kmpJvmLibExpectedLanguageVersion = "1.9",
            appExpectedUseK2Uast = true,
            libExpectedUseK2Uast = false,
            featureExpectedUseK2Uast = false,
            javaLibExpectedUseK2Uast = false,
            kotlinLibExpectedUseK2Uast = false,
            kmpAndroidLibExpectedUseK2Uast = false,
            kmpJvmLibExpectedUseK2Uast = false,
            sourceSetsLanguageVersion = "1.9"
        )

        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile,
            """
                android {
                    experimentalProperties["${OptionalBoolean.LINT_USE_K2_UAST.key}"] = true
                }
            """.trimIndent()
        )
        project.executor().run("clean", "lint")
    }

    /**
     * Test enabling K2 UAST for a single module (the lib module)
     */
    @Test
    fun testUseK2UastInLibOnly() {

        addLanguageVersionsToProject(
            appExpectedLanguageVersion = "1.9",
            libExpectedLanguageVersion = "1.9",
            featureExpectedLanguageVersion = "1.9",
            javaLibExpectedLanguageVersion = null,
            kotlinLibExpectedLanguageVersion = "1.9",
            kmpAndroidLibExpectedLanguageVersion = "1.9",
            kmpJvmLibExpectedLanguageVersion = "1.9",
            appExpectedUseK2Uast = false,
            libExpectedUseK2Uast = true,
            featureExpectedUseK2Uast = false,
            javaLibExpectedUseK2Uast = false,
            kotlinLibExpectedUseK2Uast = false,
            kmpAndroidLibExpectedUseK2Uast = false,
            kmpJvmLibExpectedUseK2Uast = false,
            sourceSetsLanguageVersion = "1.9"
        )

        TestFileUtils.appendToFile(
            project.getSubproject(":lib").buildFile,
            """
                android {
                    experimentalProperties["${OptionalBoolean.LINT_USE_K2_UAST.key}"] = true
                }
            """.trimIndent()
        )
        project.executor().run("clean", "lint")
    }

    /**
     * Test disabling K2 UAST for all modules
     */
    @Test
    fun testDisableK2UastGlobally() {

        addLanguageVersionsToProject(
            appExpectedLanguageVersion = "2.0",
            libExpectedLanguageVersion = "2.0",
            featureExpectedLanguageVersion = "2.0",
            javaLibExpectedLanguageVersion = null,
            kotlinLibExpectedLanguageVersion = "2.0",
            kmpAndroidLibExpectedLanguageVersion = "2.0",
            kmpJvmLibExpectedLanguageVersion = "2.0",
            appExpectedUseK2Uast = false,
            libExpectedUseK2Uast = false,
            featureExpectedUseK2Uast = false,
            javaLibExpectedUseK2Uast = false,
            kotlinLibExpectedUseK2Uast = false,
            kmpAndroidLibExpectedUseK2Uast = false,
            kmpJvmLibExpectedUseK2Uast = false,
        )

        project.executor()
            .with(OptionalBooleanOption.LINT_USE_K2_UAST, false)
            .run("clean", "lint")
    }

    /**
     * Test disabling K2 UAST for a single module (the app module)
     */
    @Test
    fun testDisableK2UastInAppOnly() {

        addLanguageVersionsToProject(
            appExpectedLanguageVersion = "2.0",
            libExpectedLanguageVersion = "2.0",
            featureExpectedLanguageVersion = "2.0",
            javaLibExpectedLanguageVersion = null,
            kotlinLibExpectedLanguageVersion = "2.0",
            kmpAndroidLibExpectedLanguageVersion = "2.0",
            kmpJvmLibExpectedLanguageVersion = "2.0",
            appExpectedUseK2Uast = false,
            libExpectedUseK2Uast = true,
            featureExpectedUseK2Uast = true,
            javaLibExpectedUseK2Uast = false,
            kotlinLibExpectedUseK2Uast = true,
            kmpAndroidLibExpectedUseK2Uast = true,
            kmpJvmLibExpectedUseK2Uast = true,
        )

        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile,
            """
                android {
                    experimentalProperties["${OptionalBoolean.LINT_USE_K2_UAST.key}"] = false
                }
            """.trimIndent()
        )
        project.executor().run("clean", "lint")
    }

    /**
     * Test disabling K2 UAST for a single module (the lib module)
     */
    @Test
    fun testDisableK2UastInLibOnly() {

        addLanguageVersionsToProject(
            appExpectedLanguageVersion = "2.0",
            libExpectedLanguageVersion = "2.0",
            featureExpectedLanguageVersion = "2.0",
            javaLibExpectedLanguageVersion = null,
            kotlinLibExpectedLanguageVersion = "2.0",
            kmpAndroidLibExpectedLanguageVersion = "2.0",
            kmpJvmLibExpectedLanguageVersion = "2.0",
            appExpectedUseK2Uast = true,
            libExpectedUseK2Uast = false,
            featureExpectedUseK2Uast = true,
            javaLibExpectedUseK2Uast = false,
            kotlinLibExpectedUseK2Uast = true,
            kmpAndroidLibExpectedUseK2Uast = true,
            kmpJvmLibExpectedUseK2Uast = true,
        )

        TestFileUtils.appendToFile(
            project.getSubproject(":lib").buildFile,
            """
                android {
                    experimentalProperties["${OptionalBoolean.LINT_USE_K2_UAST.key}"] = false
                }
            """.trimIndent()
        )
        project.executor().run("clean", "lint")
    }

    companion object {

        /**
         * Creates a multi-module android project
         */
        private fun createGradleTestProject(): GradleTestProject {

            val app =
                MinimalSubProject.app()
                    .appendToBuild(
                        // language=groovy
                        """
                            apply plugin: "kotlin-android"

                            android {
                                dynamicFeatures = [":feature"]
                                lint {
                                    checkDependencies = true
                                    textOutput = file("lint-report.txt")
                                    checkAllWarnings = true
                                    checkTestSources = true
                                }
                            }

                            kotlin {
                                jvmToolchain(17)
                            }
                        """.trimIndent()
                    )
                    .withFile(
                        "src/main/kotlin/com/example/ExampleClass.kt",
                        // language=kotlin
                        """
                            package com.example

                            class ExampleClass
                        """.trimIndent()
                    )

            val lib =
                MinimalSubProject.lib()
                    .appendToBuild(
                        // language=groovy
                        """
                            apply plugin: "kotlin-android"

                            android {
                                lint {
                                    checkAllWarnings = true
                                    checkTestSources = true
                                }
                            }
                        """.trimIndent()
                    )

            val feature =
                MinimalSubProject.dynamicFeature("com.example.feature")
                    .appendToBuild(
                        // language=groovy
                        """
                            apply plugin: "kotlin-android"

                            android {
                                lint {
                                    checkAllWarnings = true
                                    checkTestSources = true
                                }
                            }
                        """.trimIndent()
                    )

            val javaLib =
                MinimalSubProject.javaLibrary()
                    .appendToBuild(
                        // language=groovy
                        """
                            apply plugin: "com.android.lint"

                            lint {
                                checkAllWarnings = true
                                checkTestSources = true
                            }
                        """.trimIndent()
                    )

            val kotlinLib =
                MinimalSubProject.javaLibrary()
                    .appendToBuild(
                        // language=groovy
                        """
                            apply plugin: "com.android.lint"
                            apply plugin: "kotlin"

                            lint {
                                checkAllWarnings = true
                                checkTestSources = true
                            }
                        """.trimIndent()
                    )

            // kmpAndroidLib is a KMP library with only an android target (no jvm targets)
            val kmpAndroidLib =
                MinimalSubProject.kotlinMultiplatformAndroid("com.example.kmp")
                    .appendToBuild(
                        // language=groovy
                        """
                            apply plugin: "com.android.lint"
                        """.trimIndent()
                    )

            // kmpJvmLib is a KMP library with only a jvm target (no android target)
            val kmpJvmLib =
                MinimalSubProject.kotlinMultiplatformJvmOnly()
                    .appendToBuild(
                        // language=groovy
                        """
                            apply plugin: "com.android.lint"

                            kotlin {
                                jvm()
                            }
                        """.trimIndent()
                    )

            return GradleTestProject.builder()
                .fromTestApp(
                    MultiModuleTestProject.builder()
                        .subproject(":app", app)
                        .subproject(":lib", lib)
                        .subproject(":feature", feature)
                        .subproject(":java-lib", javaLib)
                        .subproject(":kotlin-lib", kotlinLib)
                        .subproject(":kmp-android-lib", kmpAndroidLib)
                        .subproject(":kmp-jvm-lib", kmpJvmLib)
                        .dependency(app, lib)
                        .dependency(feature, app)
                        .dependency(app, javaLib)
                        .dependency(app, kotlinLib)
                        .dependency(app, kmpAndroidLib)
                        .dependency(app, kmpJvmLib)
                        .build()
                )
                .withKotlinGradlePlugin(true)
                .create()
        }
    }

    /**
     * Adds source set language versions and expected language versions to [project]
     */
    private fun addLanguageVersionsToProject(
        appExpectedLanguageVersion: String?,
        libExpectedLanguageVersion: String?,
        featureExpectedLanguageVersion: String?,
        javaLibExpectedLanguageVersion: String?,
        kotlinLibExpectedLanguageVersion: String?,
        kmpAndroidLibExpectedLanguageVersion: String?,
        kmpJvmLibExpectedLanguageVersion: String?,
        appExpectedUseK2Uast: Boolean,
        libExpectedUseK2Uast: Boolean,
        featureExpectedUseK2Uast: Boolean,
        javaLibExpectedUseK2Uast: Boolean,
        kotlinLibExpectedUseK2Uast: Boolean,
        kmpAndroidLibExpectedUseK2Uast: Boolean,
        kmpJvmLibExpectedUseK2Uast: Boolean,
        sourceSetsLanguageVersion: String? = null
    ) {
        sourceSetsLanguageVersion?.let {
            val kotlinSubProjectNames =
                listOf("app", "lib", "feature", "kotlin-lib", "kmp-android-lib", "kmp-jvm-lib")
            for (subprojectName in kotlinSubProjectNames) {
                TestFileUtils.appendToFile(
                    project.getSubproject(subprojectName).buildFile,
                    """
                        kotlin {
                           sourceSets.all {
                               languageSettings {
                                   languageVersion = "$it"
                               }
                           }
                        }
                    """.trimIndent()
                )
            }
        }

        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            // language=groovy
            """
               afterEvaluate {
                    tasks.named("lintAnalyzeDebug") {
                        doLast {
                            def languageVersion = it.uastInputs.kotlinLanguageVersion ?: "null"
                            if (languageVersion != "${appExpectedLanguageVersion ?: "null"}") {
                                throw new RuntimeException(
                                    "Unexpected app language version: " + languageVersion
                                )
                            }
                            if (it.uastInputs.useK2Uast != $appExpectedUseK2Uast) {
                                throw new RuntimeException(
                                    "Unexpected app useK2Uast: " + it.uastInputs.useK2Uast
                                )
                            }
                        }
                    }
               }
            """.trimIndent()
        )

        TestFileUtils.appendToFile(
            project.getSubproject("lib").buildFile,
            // language=groovy
            """
               afterEvaluate {
                    tasks.named("lintAnalyzeDebug") {
                        doLast {
                            def languageVersion = it.uastInputs.kotlinLanguageVersion ?: "null"
                            if (languageVersion != "${libExpectedLanguageVersion ?: "null"}") {
                                throw new RuntimeException(
                                    "Unexpected lib language version: " + languageVersion
                                )
                            }
                            if (it.uastInputs.useK2Uast != $libExpectedUseK2Uast) {
                                throw new RuntimeException(
                                    "Unexpected lib useK2Uast: " + it.uastInputs.useK2Uast
                                )
                            }
                        }
                    }
               }
            """.trimIndent()
        )

        TestFileUtils.appendToFile(
            project.getSubproject("feature").buildFile,
            // language=groovy
            """
               afterEvaluate {
                    tasks.named("lintAnalyzeDebug") {
                        doLast {
                            def languageVersion = it.uastInputs.kotlinLanguageVersion ?: "null"
                            if (languageVersion != "${featureExpectedLanguageVersion ?: "null"}") {
                                throw new RuntimeException(
                                    "Unexpected feature language version: " + languageVersion
                                )
                            }
                            if (it.uastInputs.useK2Uast != $featureExpectedUseK2Uast) {
                                throw new RuntimeException(
                                    "Unexpected feature useK2Uast: " + it.uastInputs.useK2Uast
                                )
                            }
                        }
                    }
               }
            """.trimIndent()
        )

        TestFileUtils.appendToFile(
            project.getSubproject("java-lib").buildFile,
            // language=groovy
            """
               afterEvaluate {
                    tasks.named("lintAnalyzeJvmMain") {
                        doLast {
                            def languageVersion = it.uastInputs.kotlinLanguageVersion ?: "null"
                            if (languageVersion != "${javaLibExpectedLanguageVersion ?: "null"}") {
                                throw new RuntimeException(
                                    "Unexpected java-lib language version: " + languageVersion
                                )
                            }
                            if (it.uastInputs.useK2Uast != $javaLibExpectedUseK2Uast) {
                                throw new RuntimeException(
                                    "Unexpected java-lib useK2Uast: " + it.uastInputs.useK2Uast
                                )
                            }
                        }
                    }
               }
            """.trimIndent()
        )

        TestFileUtils.appendToFile(
            project.getSubproject("kotlin-lib").buildFile,
            // language=groovy
            """
               afterEvaluate {
                    tasks.named("lintAnalyzeJvmMain") {
                        doLast {
                            def languageVersion = it.uastInputs.kotlinLanguageVersion ?: "null"
                            if (languageVersion != "${kotlinLibExpectedLanguageVersion ?: "null"}") {
                                throw new RuntimeException(
                                    "Unexpected kotlin-lib language version: " + languageVersion
                                )
                            }
                            if (it.uastInputs.useK2Uast != $kotlinLibExpectedUseK2Uast) {
                                throw new RuntimeException(
                                    "Unexpected kotlin-lib useK2Uast: " + it.uastInputs.useK2Uast
                                )
                            }
                        }
                    }
               }
            """.trimIndent()
        )

        TestFileUtils.appendToFile(
            project.getSubproject("kmp-android-lib").buildFile,
            // language=groovy
            """
               afterEvaluate {
                    tasks.named("lintAnalyzeAndroidMain") {
                        doLast {
                            def languageVersion = it.uastInputs.kotlinLanguageVersion ?: "null"
                            if (languageVersion != "${kmpAndroidLibExpectedLanguageVersion ?: "null"}") {
                                throw new RuntimeException(
                                    "Unexpected kmp-android-lib language version: " + languageVersion
                                )
                            }
                            if (it.uastInputs.useK2Uast != $kmpAndroidLibExpectedUseK2Uast) {
                                throw new RuntimeException(
                                    "Unexpected kmp-android-lib useK2Uast: " + it.uastInputs.useK2Uast
                                )
                            }
                        }
                    }
               }
            """.trimIndent()
        )

        TestFileUtils.appendToFile(
            project.getSubproject("kmp-jvm-lib").buildFile,
            // language=groovy
            """
               afterEvaluate {
                    tasks.named("lintAnalyzeJvmMain") {
                        doLast {
                            def languageVersion = it.uastInputs.kotlinLanguageVersion ?: "null"
                            if (languageVersion != "${kmpJvmLibExpectedLanguageVersion ?: "null"}") {
                                throw new RuntimeException(
                                    "Unexpected kmp-jvm-lib language version: " + languageVersion
                                )
                            }
                            if (it.uastInputs.useK2Uast != $kmpJvmLibExpectedUseK2Uast) {
                                throw new RuntimeException(
                                    "Unexpected kmp-jvm-lib useK2Uast: " + it.uastInputs.useK2Uast
                                )
                            }
                        }
                    }
               }
            """.trimIndent()
        )

    }
}
