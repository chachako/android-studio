/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** Integration test for running lint analysis per component. */
@RunWith(FilterableParameterized::class)
class LintAnalysisPerComponentTest(private val checkDependencies: Boolean) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "checkDependencies_{0}")
        fun parameters() = listOf(true, false)
    }

    @get:Rule
    val project: GradleTestProject = createGradleTestProject("project")

    @Before
    fun before() {
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "\n${BooleanOption.LINT_ANALYSIS_PER_COMPONENT.propertyName}=true\n"
        )
        if (!checkDependencies) {
            TestFileUtils.searchAndReplace(
                project.getSubproject(":app").buildFile,
                "checkDependencies = true",
                "checkDependencies = false",
            )
        }
    }

    private val slash = File.separator

    @Test
    fun testLintAnalysisPerComponent() {
        project.executor().run(":app:clean", ":app:lintDebug")

        val lintReport = project.file("app/lint-report.txt")

        assertThat(lintReport).contains("app${slash}src${slash}main")
        assertThat(lintReport).contains("app${slash}src${slash}test")
        assertThat(lintReport).contains("app${slash}src${slash}androidTest")
        assertThat(lintReport).contains("app${slash}src${slash}testFixtures")

        assertThat(lintReport).contains("feature${slash}src${slash}main")
        assertThat(lintReport).contains("feature${slash}src${slash}test")
        assertThat(lintReport).contains("feature${slash}src${slash}androidTest")

        if (checkDependencies) {
            assertThat(lintReport).contains("${slash}lib${slash}src${slash}main")
            assertThat(lintReport).contains("${slash}lib${slash}src${slash}test")
            assertThat(lintReport).contains("${slash}lib${slash}src${slash}androidTest")
            assertThat(lintReport).contains("${slash}lib${slash}src${slash}testFixtures")

            assertThat(lintReport).contains("java-lib${slash}src${slash}main")
            assertThat(lintReport).contains("java-lib${slash}src${slash}test")
        } else {
            assertThat(lintReport).doesNotContain("${slash}lib${slash}src${slash}main")
            assertThat(lintReport).doesNotContain("${slash}lib${slash}src${slash}test")
            assertThat(lintReport).doesNotContain("${slash}lib${slash}src${slash}androidTest")
            assertThat(lintReport).doesNotContain("${slash}lib${slash}src${slash}testFixtures")

            assertThat(lintReport).doesNotContain("java-lib${slash}src${slash}main")
            assertThat(lintReport).doesNotContain("java-lib${slash}src${slash}test")
        }
    }
}
