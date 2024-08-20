/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class ProguardFilesTest {

    private val app =
        MinimalSubProject.app("com.example.baseModule")
            .appendToBuild(
                """
                    android {
                        buildTypes {
                            release {
                                postprocessing {
                                    removeUnusedCode true
                                    optimizeCode true
                                    obfuscate true
                                    removeUnusedResources true
                                    proguardFiles file("proguard-rules.pro")
                                }
                            }
                            secondRelease {
                                initWith(release)
                            }
                        }
                    }""".trimIndent()
            ).withFile(
                "proguard-rules.pro",
                "-dontwarn com.google.apps.SuppressViolation"
            )

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(app).create()

    // Regression test for b/307784512
    @Test
    fun testBuildTypeInitWith() {
        project.execute("assembleRelease", "assembleSecondRelease")
        val releaseConfigurationFile = project.getOutputFile(
            "mapping",
            "release",
            "configuration.txt"
        )
        Truth.assertThat(releaseConfigurationFile.readText()).contains("SuppressViolation")
        val secondReleaseConfigurationFile = project.getOutputFile(
            "mapping",
            "secondRelease",
            "configuration.txt"
        )
        Truth.assertThat(secondReleaseConfigurationFile.readText()).contains("SuppressViolation")
    }
}
