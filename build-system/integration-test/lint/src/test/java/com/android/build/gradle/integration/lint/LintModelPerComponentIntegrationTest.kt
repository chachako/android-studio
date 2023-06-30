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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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
}
