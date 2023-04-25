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

package com.android.build.gradle.integration.analytics

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Rule
import org.junit.Test

class AnalyticsConfigureOnDemandTest {

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject("app", MinimalSubProject.app())
                    .subproject("library", MinimalSubProject.lib())
                    .build()
            )
            .enableProfileOutput()
            .create()

    // Regression test for b//270731522
    @Test
    fun testNoWarningIfConfigOnDemandEnabled() {
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            """
                configurations {
                    customConfiguration
                }

                dependencies {
                    customConfiguration project(':library')
                }
                abstract class ResolutionTask extends DefaultTask {
                    @Internal
                    abstract ConfigurableFileCollection getDeps()

                    @TaskAction
                    def resolve() {
                        getDeps().files
                    }
                }

                tasks.register('resolution', ResolutionTask).configure {
                    getDeps().from(project.configurations.getByName("customConfiguration").incoming
                    .artifactView { lenient(true) }.files)
                }
            """.trimIndent()
        )
        val result = project.executor()
            .withArgument("-Dorg.gradle.configureondemand=true")
            .run(":app:resolution")
        ScannerSubject.assertThat(result.stdout).doesNotContain(
            "GradleBuildProject.Builder should not be accessed")
    }
}
