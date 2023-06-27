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
package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.DESUGAR_DEPENDENCY_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Integration test for the lint models when running lint analysis per component.  */
class LintModelPerComponentIntegrationTest {

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestProject("lintKotlin")
            .dontOutputLogOnFailure()
            .create()

    @Before
    fun before() {
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            """
                ${BooleanOption.LINT_ANALYSIS_PER_COMPONENT.propertyName}=true
                ${BooleanOption.ENABLE_TEST_FIXTURES.propertyName}=true
            """.trimIndent()
        )
    }

    @Test
    fun checkLintReportModels() {
        // Check lint runs correctly before asserting about the model.
        project.executor().expectFailure().run("clean", ":app:lintDebug")
        val lintResults = project.file("app/build/reports/lint-results.txt")
        assertThat(lintResults).contains("9 errors, 4 warnings")

        checkLintModels(
            project = project,
            lintModelDir = project.getSubproject("app").intermediatesDir.toPath()
                .resolve("lint_report_lint_model/debug/generateDebugLintReportModel"),
            modelSnapshotResourceRelativePath = "perComponent/app/lintReportDebug",
            "debug-artifact-dependencies.xml",
            "debug-artifact-libraries.xml",
            "debug.xml",
            "module.xml",
        )
    }


    @Test
    fun checkLintAnalysisModels_mainVariant() {
        project.executor().run("clean", ":app:lintAnalyzeDebug")

        checkLintModels(
            project = project,
            lintModelDir = project.getSubproject("app").intermediatesDir.toPath()
                .resolve("incremental/lintAnalyzeDebug"),
            modelSnapshotResourceRelativePath = "perComponent/app/lintAnalyzeDebug",
            "debug-artifact-dependencies.xml",
            "debug-artifact-libraries.xml",
            "debug.xml",
            "module.xml",
        )
    }

    @Test
    fun checkLintAnalysisModels_androidTest() {
        project.executor().run("clean", ":app:lintAnalyzeDebugAndroidTest")

        checkLintModels(
            project = project,
            lintModelDir = project.getSubproject("app").intermediatesDir.toPath()
                .resolve("incremental/lintAnalyzeDebugAndroidTest"),
            modelSnapshotResourceRelativePath = "perComponent/app/lintAnalyzeDebugAndroidTest",
            "debug-artifact-dependencies.xml",
            "debug-artifact-libraries.xml",
            "debug.xml",
            "module.xml",
        )
    }

    @Test
    fun checkLintAnalysisModels_unitTest() {
        project.executor().run("clean", ":app:lintAnalyzeDebugUnitTest")

        checkLintModels(
            project = project,
            lintModelDir = project.getSubproject("app").intermediatesDir.toPath()
                .resolve("incremental/lintAnalyzeDebugUnitTest"),
            modelSnapshotResourceRelativePath = "perComponent/app/lintAnalyzeDebugUnitTest",
            "debug-artifact-dependencies.xml",
            "debug-artifact-libraries.xml",
            "debug.xml",
            "module.xml",
        )
    }

    @Test
    fun checkLintAnalysisModels_testFixtures() {
        project.executor().run("clean", ":app:lintAnalyzeDebugTestFixtures")

        checkLintModels(
            project = project,
            lintModelDir = project.getSubproject("app").intermediatesDir.toPath()
                .resolve("incremental/lintAnalyzeDebugTestFixtures"),
            modelSnapshotResourceRelativePath = "perComponent/app/lintAnalyzeDebugTestFixtures",
            "debug-artifact-dependencies.xml",
            "debug-artifact-libraries.xml",
            "debug.xml",
            "module.xml",
        )
    }

    @Test
    fun checkLibraryLintModels() {
        // Enable core library desugaring in library module as regression test for b/260755411
        TestFileUtils.appendToFile(
            project.getSubproject("library").buildFile,
            """
                android {
                    compileOptions {
                        coreLibraryDesugaringEnabled true
                    }
                    dependencies {
                        coreLibraryDesugaring "com.android.tools:desugar_jdk_libs:$DESUGAR_DEPENDENCY_VERSION"
                    }
                }
            """.trimIndent()
        )
        TestFileUtils.searchAndReplace(
            project.getSubproject("library").buildFile,
            "minSdkVersion 15",
            "minSdkVersion 24"
        )

        // Check lint runs correctly before asserting about the model.
        project.executor().run("clean", ":library:lintDebug")

        checkLintModels(
            project = project,
            lintModelDir = project.getSubproject("library").intermediatesDir.toPath()
                .resolve("lint_report_lint_model/debug/generateDebugLintReportModel"),
            modelSnapshotResourceRelativePath = "perComponent/library/lintReportDebug",
            "debug-artifact-dependencies.xml",
            "debug-artifact-libraries.xml",
            "debug.xml",
            "module.xml",
        )
    }

    @Test
    fun checkLintModelsForShrinkable() {
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            """
                android {
                    buildTypes {
                        debug {
                            minifyEnabled true
                        }
                    }
                }
            """.trimIndent()
        )
        // Check lint runs correctly before asserting about the model.
        project.executor().expectFailure().run(":app:clean", ":app:lintDebug")
        val lintResults = project.file("app/build/reports/lint-results.txt")
        assertThat(lintResults).contains("9 errors, 4 warnings")

        val lintModelDir =
            project.getSubproject("app")
                .intermediatesDir.toPath()
                .resolve("lint_report_lint_model/debug/generateDebugLintReportModel")
                .toFile()

        val projectModelFile = File(lintModelDir, "module.xml")
        assertThat(projectModelFile).isFile()
        Truth.assertThat(
            Files.readAllLines(projectModelFile.toPath())
                .map { applyReplacements(it, createReplacements(project)) }
                .none { it.contains("neverShrinking") }
        ).isTrue()

        val variantModelFile = File(lintModelDir, "debug.xml")
        assertThat(variantModelFile).isFile()
        Truth.assertThat(
            Files.readAllLines(variantModelFile.toPath())
                .map { applyReplacements(it, createReplacements(project)) }
                .any { it.contains("shrinking=\"true\"") }
        ).isTrue()
    }

    @Test
    fun checkLintModelAbsorbTargetSdk() {
        TestFileUtils.appendToFile(
            project.getSubproject("library").buildFile,
            """
                android {
                    defaultConfig {
                        targetSdk 2
                    }
                    lint {
                        targetSdk 1
                    }
                }
            """.trimIndent()
        )
        project.executor().expectFailure().run(":library:clean", ":library:lintDebug")
        val model = project.file("library/build/intermediates/incremental/lintAnalyzeDebug/debug.xml")
        assertThat(model).contains("targetSdkVersion=\"1\"")
    }

    @Test
    fun checkLintModelTargetSdkFailForApplication() {
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            """
                android {
                    defaultConfig {
                        targetSdk 16
                    }
                    lint {
                        targetSdk 15
                    }
                }
            """.trimIndent()
        )
        val result = project.executor().expectFailure().run(":app:tasks")
        ScannerSubject.assertThat(result.stderr)
            .contains("lint.targetSdk (15) for non library is smaller than android.targetSdk (16) for variants debug, release. "
                    + "Please change the values such that lint.targetSdk is greater than or equal to android.targetSdk.")
    }

    @Test
    fun checkLintModelTargetSdkHigherForApplication() {
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            """
                android {
                    defaultConfig {
                        targetSdk 16
                    }
                    lint {
                        targetSdk 17
                    }
                }
            """.trimIndent()
        )
        project.executor().run(":app:tasks")
    }

    @Test
    @Ignore("b/287470576")
    fun checkLintOutputPrioritizeTargetSdkParameter() {
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            """
                android {
                    defaultConfig {
                        targetSdk 2
                    }
                    lint {
                        targetSdk 1
                    }
                }
            """.trimIndent()
        )
        project.executor().expectFailure().run(":app:clean", ":app:lintDebug")
        val lintResults = project.file("app/build/reports/lint-results.txt")
        assertThat(lintResults).contains("10 errors, 4 warnings")
        assertThat(lintResults).contains("targetSdk 1")
    }
}
