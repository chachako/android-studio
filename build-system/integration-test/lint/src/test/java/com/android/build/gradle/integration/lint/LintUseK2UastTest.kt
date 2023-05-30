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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Integration test for running lint with --XuseK2Uast flag. */
class LintUseK2UastTest {

    @get:Rule
    val project: GradleTestProject = createGradleTestProject("project")

    private val slash = File.separator

    @Test
    fun testUseK2Uast() {
        project.executor()
            .with(BooleanOption.LINT_USE_K2_UAST, true)
            .run(":app:clean", ":app:lintDebug")

        val lintReport = project.file("app/lint-report.txt")

        assertThat(lintReport).exists()

        assertThat(lintReport).contains("app${slash}src${slash}main")
        assertThat(lintReport).contains("app${slash}src${slash}test")
        assertThat(lintReport).contains("app${slash}src${slash}androidTest")
        assertThat(lintReport).contains("app${slash}src${slash}testFixtures")

        assertThat(lintReport).contains("feature${slash}src${slash}main")
        assertThat(lintReport).contains("feature${slash}src${slash}test")
        assertThat(lintReport).contains("feature${slash}src${slash}androidTest")

        assertThat(lintReport).contains("${slash}lib${slash}src${slash}main")
        assertThat(lintReport).contains("${slash}lib${slash}src${slash}test")
        assertThat(lintReport).contains("${slash}lib${slash}src${slash}androidTest")
        assertThat(lintReport).contains("${slash}lib${slash}src${slash}testFixtures")

        assertThat(lintReport).contains("java-lib${slash}src${slash}main")
        assertThat(lintReport).contains("java-lib${slash}src${slash}test")
    }
}
