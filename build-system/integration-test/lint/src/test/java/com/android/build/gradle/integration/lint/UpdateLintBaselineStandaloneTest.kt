/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.truth.GradleTaskSubject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.Version
import com.android.testutils.truth.PathSubject
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Test for updating lint baselines with the standalone plugin using the updateLintBaseline task.
 */
class UpdateLintBaselineStandaloneTest {

    @get:Rule
    val project = builder().fromTestProject("lintStandalone").create()

    @Test
    fun checkUpdateLintBaseline() {
        // This test runs updateLintBaseline in 7 scenarios:
        //   (1)  when there is no existing baseline,
        //   (2)  when there is already a correct existing baseline, and there are no changes since
        //        the last run,
        //   (3)  when there is already a correct existing baseline, and there was a clean since the
        //        last run,
        //   (4)  when there is already a correct existing baseline with old lint/AGP versions,
        //   (5)  when there is already an incorrect existing baseline,
        //   (6)  when a user runs updateLintBase and lint at the same time.
        //   (7)  when there is no baseline file specified.

        TestFileUtils.appendToFile(
            project.buildFile,
            """
                lint {
                   baseline = file('lint-baseline.xml')
                }
            """.trimIndent()
        )

        // First test the case when there is no existing baseline.
        val baselineFile = File(project.projectDir, "lint-baseline.xml")
        PathSubject.assertThat(baselineFile).doesNotExist()
        val result1 = project.executor().run("updateLintBaseline")
        GradleTaskSubject.assertThat(result1.getTask(":lintAnalyzeJvm")).didWork()
        GradleTaskSubject.assertThat(result1.getTask(":updateLintBaselineJvm")).didWork()
        PathSubject.assertThat(baselineFile).exists()

        val baselineFileContents = baselineFile.readBytes()

        // Then test the case when there is already a correct existing baseline, and there are no
        // changes since the last run. updateLintBaseline should still do work because it is never
        // UP-TO-DATE.
        val result2 = project.executor().run("updateLintBaseline")
        GradleTaskSubject.assertThat(result2.getTask(":lintAnalyzeJvm")).wasUpToDate()
        GradleTaskSubject.assertThat(result2.getTask(":updateLintBaselineJvm")).didWork()
        PathSubject.assertThat(baselineFile).exists()
        assertThat(baselineFile.readBytes()).isEqualTo(baselineFileContents)

        // Then test the case when there is already a correct existing baseline, and there was a
        // clean since the last run.
        project.executor().run("clean")
        PathSubject.assertThat(baselineFile).exists()
        assertThat(baselineFile.readBytes()).isEqualTo(baselineFileContents)
        val result3 = project.executor().run("updateLintBaseline")
        GradleTaskSubject.assertThat(result3.getTask(":lintAnalyzeJvm")).didWork()
        GradleTaskSubject.assertThat(result3.getTask(":updateLintBaselineJvm")).didWork()
        PathSubject.assertThat(baselineFile).exists()
        assertThat(baselineFile.readBytes()).isEqualTo(baselineFileContents)

        // Then test the case when there is already a correct existing baseline from a previous
        // AGP/lint version. In this case, the new baseline file should not change (b/248338457).
        TestFileUtils.searchAndReplace(baselineFile, Version.ANDROID_GRADLE_PLUGIN_VERSION, "7.3.0")
        val previousVersionsBaselineFileContents = baselineFile.readBytes()
        assertThat(previousVersionsBaselineFileContents).isNotEqualTo(baselineFileContents)
        val result4 = project.executor().run("updateLintBaseline")
        GradleTaskSubject.assertThat(result4.getTask(":lintAnalyzeJvm")).wasUpToDate()
        GradleTaskSubject.assertThat(result4.getTask(":updateLintBaselineJvm")).didWork()
        PathSubject.assertThat(baselineFile).exists()
        assertThat(baselineFile.readBytes()).isEqualTo(previousVersionsBaselineFileContents)

        // Then test the case when there is already an incorrect existing baseline.
        baselineFile.writeText("invalid")
        assertThat(baselineFile.readBytes()).isNotEqualTo(baselineFileContents)
        val result5 = project.executor().run("updateLintBaseline")
        GradleTaskSubject.assertThat(result5.getTask(":lintAnalyzeJvm")).wasUpToDate()
        GradleTaskSubject.assertThat(result5.getTask(":updateLintBaselineJvm")).didWork()
        PathSubject.assertThat(baselineFile).exists()
        assertThat(baselineFile.readBytes()).isEqualTo(baselineFileContents)

        // Then test the case when a user runs updateLintBaseline and lint at the same time.
        val result6 = project.executor().run("updateLintBaseline", "lint")
        ScannerSubject.assertThat(result6.stdout).doesNotContain("Gradle detected a problem")
        GradleTaskSubject.assertThat(result6.getTask(":lintAnalyzeJvm")).wasUpToDate()
        GradleTaskSubject.assertThat(result6.getTask(":updateLintBaselineJvm")).didWork()
        PathSubject.assertThat(baselineFile).exists()
        assertThat(baselineFile.readBytes()).isEqualTo(baselineFileContents)

        // Then test the case when there is no baseline file specified.
        assertThat(baselineFile.delete()).isTrue()
        TestFileUtils.searchAndReplace(
            project.buildFile,
            "baseline = file('lint-baseline.xml')",
            ""
        )
        val result7 = project.executor().run(":updateLintBaseline")
        GradleTaskSubject.assertThat(result7.getTask(":lintAnalyzeJvm")).wasUpToDate()
        GradleTaskSubject.assertThat(result7.getTask(":updateLintBaselineJvm")).didWork()
        PathSubject.assertThat(baselineFile).doesNotExist()
        ScannerSubject.assertThat(result7.stdout)
            .contains(
                """
                No baseline file is specified, so no baseline file will be created.

                Please specify a baseline file in the build.gradle file like so:

                ```
                lint {
                    baseline = file("lint-baseline.xml")
                }
                ```
                """.trimIndent()
            )
    }

    @Test
    fun testMissingBaselineIsEmptyBaseline() {
        // Test that android.experimental.lint.missingBaselineIsEmptyBaseline has the desired
        // effects when running the updateLintBaseline and lint tasks.
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                lint {
                    baseline = file('lint-baseline.xml')
                    disable 'UseValueOf', 'JavaPluginLanguageLevel'
                }
            """.trimIndent()
        )

        // First run updateLintBaseline without the boolean flag and check that an empty baseline
        // file is written.
        val baselineFile = File(project.projectDir, "lint-baseline.xml")
        PathSubject.assertThat(baselineFile).doesNotExist()
        project.executor().run("updateLintBaseline")
        PathSubject.assertThat(baselineFile).exists()
        PathSubject.assertThat(baselineFile).doesNotContain("</issue>")

        // Then run updateLinBaseline with the boolean flag and check that the baseline file is
        // deleted.
        project.executor()
            .with(BooleanOption.MISSING_LINT_BASELINE_IS_EMPTY_BASELINE, true)
            .run("updateLintBaseline")
        PathSubject.assertThat(baselineFile).doesNotExist()

        // Finally, run lint with the boolean flag and without a baseline file when there is an
        // issue, in which case the build should fail.
        TestFileUtils.searchAndReplace(project.buildFile, "disable", "error")
        val result = project.executor()
            .with(BooleanOption.MISSING_LINT_BASELINE_IS_EMPTY_BASELINE, true)
            .expectFailure()
            .run("lint")
        ScannerSubject.assertThat(result.stdout).contains("UseValueOf")
    }
}
