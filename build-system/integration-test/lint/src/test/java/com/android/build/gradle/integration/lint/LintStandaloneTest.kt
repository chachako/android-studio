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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test for the standalone lint plugin.
 *
 * <p>Tip: To execute just this test run:
 *
 * <pre>
 *     $ cd tools
 *     $ ./gradlew :base:build-system:integration-test:lint:test --tests=LintStandaloneTest
 * </pre>
 */
@RunWith(FilterableParameterized::class)
class LintStandaloneTest(
    private val runLintInProcess: Boolean,
    private val lintAnalysisPerComponent: Boolean
) {

    companion object {
        @Parameterized.Parameters(name = "runLintInProcess_{0}_lintAnalysisPerComponent_{1}")
        @JvmStatic
        fun params() = listOf(
            arrayOf(true, true),
            arrayOf(true, false),
            arrayOf(false, false),
        )
    }

    @get:Rule
    val project =
        GradleTestProject.builder().fromTestProject("lintStandalone").create()

    @Test
    fun checkStandaloneLint() {
        getExecutor().run( ":lint")

        val file = project.file("lint-results.txt")
        assertThat(file).exists()
        assertThat(file).contains("MyClass.java:5: Warning: Use Boolean.valueOf(true) instead")
        assertThat(file).contains("build.gradle:4: Warning: no Java language level directives")
        assertThat(file).contains("0 errors, 3 warnings")

        // Check that lint copies the result to the new location if that is changed
        // But the analysis itself is not re-run in the new integration
        TestFileUtils.searchAndReplace(
            project.buildFile,
            "textOutput file(\"lint-results.txt\")",
            "textOutput file(\"lint-results2.txt\")"
        )
        // Run twice to catch issues with configuration caching
        getExecutor().run(":lint")
        getExecutor().run(":lint")
        project.buildResult.assertConfigurationCacheHit()
        val secondFile = project.file("lint-results2.txt")
        assertThat(secondFile).exists()
        assertThat(secondFile).contains("MyClass.java:5: Warning: Use Boolean.valueOf(true) instead")
        assertThat(secondFile).contains("build.gradle:4: Warning: no Java language level directives")
        assertThat(secondFile).contains("0 errors, 3 warnings")
    }

    @Test
    fun checkStandaloneLintFailure()  {
        TestFileUtils.appendToFile(
            project.buildFile,
            "\n\nlintOptions.error 'UseValueOf'\n\n"
        )
        getExecutor().expectFailure().run( ":lint")
        ScannerSubject.assertThat(project.buildResult.stderr)
            .contains("Lint found errors in the project; aborting build.")
        assertThat(project.buildResult.failedTasks).contains(":lintJvm")
        assertThat(project.buildResult.didWorkTasks).contains(":lintReportJvm")
        assertThat(project.buildResult.failedTasks).doesNotContain(":lintReportJvm")
    }

    /**
     * Regression test for b/253219347
     */
    @Test
    fun checkAddedSrcDirFromBuildDirectory() {
        TestFileUtils.appendToFile(
            project.buildFile,
            // language=groovy
            """

                def fooTask = tasks.register("foo", com.example.FooTask.class) {
                    getOutputDir().set(project.layout.buildDirectory.dir("fooOut"))
                }

                java {
                    sourceSets {
                        main {
                            resources.srcDir(fooTask.map { it.getOutputDir().get().getAsFile() })
                        }
                    }
                }
            """.trimIndent()
        )

        val result = getExecutor().run(":foo", ":lint")
        ScannerSubject.assertThat(result.stdout).doesNotContain("Gradle detected a problem")
        ScannerSubject.assertThat(result.stderr).doesNotContain("Gradle detected a problem")
    }

    @Test
    fun checkK2Uast() {
        getExecutor().with(BooleanOption.LINT_USE_K2_UAST, true).run( ":lint")

        val file = project.file("lint-results.txt")
        assertThat(file).exists()
        assertThat(file).contains("build.gradle:4: Warning: no Java language level directives")
        assertThat(file).contains("MyClass.java:5: Warning: Use Boolean.valueOf(true) instead")
        assertThat(file).contains("0 errors, 3 warnings")
    }

    /**
     * Regression test for b/294385251
     */
    @Test
    fun checkDuplicatePlatformClasses() {
        TestFileUtils.searchAndReplace(
            project.buildFile,
            "ignoreTestSources true",
            "ignoreTestSources false"
        )

        // We expect no DuplicatePlatformClasses issue for testImplementation dependency.
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                lint {
                    enable 'DuplicatePlatformClasses'
                }
                dependencies {
                    testImplementation 'commons-logging:commons-logging:1.2'
                }
            """.trimIndent()
        )
        getExecutor().run(":lint")
        val lintReport = project.file("lint-results.txt")
        assertThat(lintReport).exists()
        assertThat(lintReport).doesNotContain("DuplicatePlatformClasses")

        // We expect a DuplicatePlatformClasses issue for implementation dependency.
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                dependencies {
                    implementation 'commons-logging:commons-logging:1.2'
                }
            """.trimIndent()
        )
        getExecutor().expectFailure().run(":lint")
        assertThat(lintReport).exists()
        assertThat(lintReport).contains("DuplicatePlatformClasses")
    }

    private fun getExecutor(): GradleTaskExecutor =
        project.executor()
            .with(BooleanOption.RUN_LINT_IN_PROCESS, runLintInProcess)
            .with(BooleanOption.LINT_ANALYSIS_PER_COMPONENT, lintAnalysisPerComponent)

}
