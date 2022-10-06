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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Regression test for b/220190972 to check that lint baseline files are relocatable.
 */
class LintRelocatableBaselineTest {

    private val app =
        MinimalSubProject.app()
            .appendToBuild(
                """

                    android {
                        lint {
                            abortOnError = false
                            checkDependencies = true
                            absolutePaths = true
                            baseline = file('lint-baseline.xml')
                        }
                    }
                """.trimIndent()
            )

    private val lib =
        MinimalSubProject.lib()
            .appendToBuild(
                """

                    android {
                        lint {
                            absolutePaths = true
                        }
                    }
                """.trimIndent()
            )
            .withFile(
                "src/main/res/values/strings.xml",
                """
                    <resources>
                        <string name="unused">I am unused!</string>
                    </resources>
                """.trimIndent()
            )

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .subproject(":lib", lib)
                    .dependency(app, lib)
                    .build()
            ).create()

    @Test
    fun testLintBaselineRelocatable() {
        // Set user.home system property to project directory. Lint should use a relative path
        // instead of the $HOME path variable in the line baseline file.
        project.executor()
            .withArgument("-Duser.home=${project.projectDir.absolutePath}")
            .run(":app:updateLintBaseline")
        val lintBaselineFile = File(project.getSubproject("app").projectDir, "lint-baseline.xml")
        assertThat(lintBaselineFile).doesNotContain("HOME")
        assertThat(lintBaselineFile).contains("../lib/src/main/res/values/strings.xml")
    }
}
