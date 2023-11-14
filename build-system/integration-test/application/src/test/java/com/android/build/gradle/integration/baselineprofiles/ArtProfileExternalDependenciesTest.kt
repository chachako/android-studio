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

package com.android.build.gradle.integration.baselineprofiles

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ArtProfileExternalDependenciesTest {
    private val activityDependency = "androidx.activity:activity-compose:1.5.1"
    private val fragmentDependency = "androidx.fragment:fragment:1.4.1"
    private val baselineProfileContent =
        """
            HSPLcom/google/Foo;->mainMethod(II)I
            HSPLcom/google/Foo;->mainMethod-name-with-hyphens(II)I
        """.trimIndent()

    private val app =
        HelloWorldApp.forPluginWithNamespace("com.android.application", "com.example.app")

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject(":app", app)
                .build()
        ).create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(project.getSubproject("app").buildFile,
            """
                android {
                    defaultConfig {
                        minSdkVersion = 28
                    }
                }

                dependencies {
                    implementation '$activityDependency'
                    implementation '$fragmentDependency'
                }
            """.trimIndent()
        )

        TestFileUtils.appendToFile(project.file("gradle.properties"),
            """
                android.useAndroidX=true
            """.trimIndent()
        )

        FileUtils.createFile(
            project.file("app/src/main/baselineProfiles/file.txt"), baselineProfileContent)
    }

    @Test
    fun testIgnoreFrom() {
        project.executor().run("assembleRelease")

        val mergedFile = FileUtils.join(
            project.getSubproject("app").buildDir,
            SdkConstants.FD_INTERMEDIATES,
            InternalArtifactType.MERGED_ART_PROFILE.getFolderName(),
            "release",
            "mergeReleaseArtProfile",
            SdkConstants.FN_ART_PROFILE)

        Truth.assertThat(mergedFile.readText()).contains(baselineProfileContent)
        Truth.assertThat(mergedFile.readText()).contains("HSPLandroidx/compose/")
        Truth.assertThat(mergedFile.readText()).contains("HSPLandroidx/fragment/")

        TestFileUtils.appendToFile(project.getSubproject("app").buildFile,
            """

                android {
                    buildTypes {
                        release {
                            optimization {
                                baselineProfile {
                                    ignoreFrom += '$fragmentDependency'
                                }
                            }
                        }
                    }
                }
            """.trimIndent()
        )

        project.executor().run("assembleRelease")

        Truth.assertThat(mergedFile.readText()).contains(baselineProfileContent)
        Truth.assertThat(mergedFile.readText()).contains("HSPLandroidx/compose/")
        Truth.assertThat(mergedFile.readText()).doesNotContain("HSPLandroidx/fragment/")
    }

    @Test
    fun testIgnoreFromAllExternalDependencies() {
        TestFileUtils.appendToFile(project.getSubproject("app").buildFile,
            """

                android {
                    buildTypes {
                        release {
                            optimization {
                                baselineProfile {
                                    ignoreFromAllExternalDependencies true
                                }
                            }
                        }
                    }
                }
            """.trimIndent()
        )

        project.executor().run("assembleRelease")

        val mergedFile = FileUtils.join(
            project.getSubproject("app").buildDir,
            SdkConstants.FD_INTERMEDIATES,
            InternalArtifactType.MERGED_ART_PROFILE.getFolderName(),
            "release",
            "mergeReleaseArtProfile",
            SdkConstants.FN_ART_PROFILE)

        Truth.assertThat(mergedFile.readText()).contains(baselineProfileContent)
        Truth.assertThat(mergedFile.readText()).doesNotContain("HSPLandroidx/compose/")
        Truth.assertThat(mergedFile.readText()).doesNotContain("HSPLandroidx/fragment/")
    }

    @Test
    fun testIgnoreFromDependencyNotFound() {
        TestFileUtils.appendToFile(project.getSubproject("app").buildFile,
            """

                android {
                    buildTypes {
                        release {
                            optimization {
                                baselineProfile {
                                    ignoreFrom += "Unknown Dependency 1"
                                    ignoreFrom += "Unknown Dependency 2"
                                }
                            }
                        }
                    }
                }
            """.trimIndent()
        )

        val result = project.executor().run("assembleRelease")
        result.stdout.use {
            ScannerSubject.assertThat(it).contains(
                "Baseline profiles from [Unknown Dependency 1, Unknown Dependency 2] " +
                        "are specified to be ignored")
        }
    }
}
