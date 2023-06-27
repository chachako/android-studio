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
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Integration tests for the lint models when [BooleanOption.LINT_ANALYSIS_PER_COMPONENT] is false.
 * See [LintModelPerComponentIntegrationTest] for similar tests when it is true.
 */
class LintModelIntegrationTest {

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestProject("lintKotlin")
            .dontOutputLogOnFailure()
            .create()

    /**
     * Test lint report models when [BooleanOption.LINT_ANALYSIS_PER_COMPONENT] is false. See
     * [LintModelPerComponentIntegrationTest] for similar test when it is true.
     */
    @Test
    fun checkLintReportModels() {
        // Check lint runs correctly before asserting about the model.
        project.executor()
            .with(BooleanOption.LINT_ANALYSIS_PER_COMPONENT, false)
            .expectFailure()
            .run("clean", ":app:lintDebug")
        project.executor()
            .with(BooleanOption.LINT_ANALYSIS_PER_COMPONENT, false)
            .expectFailure()
            .run(":app:clean", ":app:lintDebug")
        val lintResults = project.file("app/build/reports/lint-results.txt")
        assertThat(lintResults).contains("9 errors, 4 warnings")

        checkLintModels(
            project = project,
            lintModelDir = project.getSubproject("app").intermediatesDir.toPath()
                .resolve("lint_report_lint_model/debug/generateDebugLintReportModel"),
            modelSnapshotResourceRelativePath = "kotlinmodel/app/lintReportDebug",
            "debug-androidTestArtifact-dependencies.xml",
            "debug-androidTestArtifact-libraries.xml",
            "debug-artifact-dependencies.xml",
            "debug-artifact-libraries.xml",
            "debug-testArtifact-dependencies.xml",
            "debug-testArtifact-libraries.xml",
            "debug.xml",
            "module.xml",
        )
    }

    /**
     * Test lint analysis models when [BooleanOption.LINT_ANALYSIS_PER_COMPONENT] is false. See
     * [LintModelPerComponentIntegrationTest] for similar test when it is true.
     */
    @Test
    fun checkLintAnalysisModels() {
        project.executor()
            .with(BooleanOption.LINT_ANALYSIS_PER_COMPONENT, false)
            .expectFailure()
            .run("clean", ":app:lintDebug")

        checkLintModels(
            project = project,
            lintModelDir = project.getSubproject("app").intermediatesDir.toPath()
                .resolve("incremental/lintAnalyzeDebug"),
            modelSnapshotResourceRelativePath = "kotlinmodel/app/lintAnalyzeDebug",
            "debug-androidTestArtifact-dependencies.xml",
            "debug-androidTestArtifact-libraries.xml",
            "debug-artifact-dependencies.xml",
            "debug-artifact-libraries.xml",
            "debug-testArtifact-dependencies.xml",
            "debug-testArtifact-libraries.xml",
            "debug.xml",
            "module.xml",
        )
    }

    /**
     * Test library lint report models when [BooleanOption.LINT_ANALYSIS_PER_COMPONENT] is false.
     * See [LintModelPerComponentIntegrationTest] for similar test when it is true.
     */
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
        project.executor()
            .with(BooleanOption.LINT_ANALYSIS_PER_COMPONENT, false)
            .run("clean", ":library:lintDebug")

        checkLintModels(
            project = project,
            lintModelDir = project.getSubproject("library").intermediatesDir.toPath()
                .resolve("lint_report_lint_model/debug/generateDebugLintReportModel"),
            modelSnapshotResourceRelativePath = "kotlinmodel/library/lintDebug",
            "debug-androidTestArtifact-dependencies.xml",
            "debug-androidTestArtifact-libraries.xml",
            "debug-artifact-dependencies.xml",
            "debug-artifact-libraries.xml",
            "debug-testArtifact-dependencies.xml",
            "debug-testArtifact-libraries.xml",
            "debug.xml",
            "module.xml",
        )
    }
}
