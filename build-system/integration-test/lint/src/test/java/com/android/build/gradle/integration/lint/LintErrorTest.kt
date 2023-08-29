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

import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Rule
import org.junit.Test

class LintErrorTest {

    @get:Rule
    val project =
        builder().fromTestApp(MinimalSubProject.app("com.example.app"))
            .withHeap("100m")
            .create()

    /**
     * Regression test for b/297095583. An OutOfMemoryError when running lint should result in a
     * build failure instead of a LintError being added to the lint baseline file.
     */
    @Test
    fun testOutOfMemoryErrorCausesBuildFailureWhenUpdatingLintBaseline() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                android {
                    lint {
                        baseline = file('lint-baseline.xml')
                    }
                }
            """.trimIndent()
        )
        val result = project.executor().expectFailure().run("updateLintBaseline")
        ScannerSubject.assertThat(result.stderr).contains("Java heap space")
    }
}
