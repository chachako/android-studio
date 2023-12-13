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

package com.android.build.gradle.integration.testing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.options.StringOption
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class JacocoVersionTest {

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder().fromTestProject("unitTesting").create()

    @Test
    fun setJacocoPluginExtensionVersionForUnitTest() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                apply plugin: 'jacoco'
                android.buildTypes.debug.enableUnitTestCoverage true

                task jacocoTestReport(
                    type: JacocoReport,
                    dependsOn: ['testDebugUnitTest', 'createDebugUnitTestCoverageReport']
                ) {
                    executionData.setFrom(
                        files([
                            "${'$'}{buildDir}/outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"
                        ])
                    )
                }
            """.trimIndent()
        )

        // AGP default Jacoco plugin version
        val result = project.executor().run("jacocoTestReport")
        ScannerSubject.assertThat(result.stdout).contains("The Jacoco plugin extension version " +
            "'0.8.9' is not currently available in the Android Gradle Plugin. " +
            "Setting the version to ${JacocoOptions.DEFAULT_VERSION}"
        )
        val generatedJacocoReport = FileUtils.join(
            project.buildDir, "reports", "jacoco", "jacocoTestReport", "html", "index.html"
        )
        val generatedCoverageReport = FileUtils.join(
            project.buildDir, "reports", "coverage", "test", "debug", "index.html"
        )
        var generatedJacocoReportHtml = generatedJacocoReport.readLines().joinToString("\n")
        var generatedCoverageReportHtml = generatedCoverageReport.readLines().joinToString("\n")
        Truth.assertThat(generatedJacocoReportHtml).contains("JaCoCo</a> 0.8.8")
        Truth.assertThat(generatedCoverageReportHtml).contains("JaCoCo</a> 0.8.8")

        // Test Jacoco DSL
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                jacoco.toolVersion = "0.8.7"
            """.trimIndent()
        )
        project.execute("jacocoTestReport")
        generatedJacocoReportHtml = generatedJacocoReport.readLines().joinToString("\n")
        generatedCoverageReportHtml = generatedCoverageReport.readLines().joinToString("\n")
        Truth.assertThat(generatedJacocoReportHtml).contains("JaCoCo</a> 0.8.7")
        Truth.assertThat(generatedCoverageReportHtml).contains("JaCoCo</a> 0.8.7")

        // Test Android DSL
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                android.testCoverage.jacocoVersion = "0.8.8"
            """.trimIndent()
        )
        project.execute("jacocoTestReport")
        generatedJacocoReportHtml = generatedJacocoReport.readLines().joinToString("\n")
        generatedCoverageReportHtml = generatedCoverageReport.readLines().joinToString("\n")
        Truth.assertThat(generatedJacocoReportHtml).contains("JaCoCo</a> 0.8.8")
        Truth.assertThat(generatedCoverageReportHtml).contains("JaCoCo</a> 0.8.8")

        // Test StringOption
        project.executor()
            .with(StringOption.JACOCO_TOOL_VERSION, "0.8.7")
            .run("jacocoTestReport")
        generatedJacocoReportHtml = generatedJacocoReport.readLines().joinToString("\n")
        generatedCoverageReportHtml = generatedCoverageReport.readLines().joinToString("\n")
        Truth.assertThat(generatedJacocoReportHtml).contains("JaCoCo</a> 0.8.7")
        Truth.assertThat(generatedCoverageReportHtml).contains("JaCoCo</a> 0.8.7")
    }
}
