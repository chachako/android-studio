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

package com.android.build.gradle.integration.multiplatform.v2

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

class KotlinMultiplatformAndroidPluginBasicTest {

    @get:Rule
    val project = GradleTestProjectBuilder()
        .fromTestProject("kotlinMultiplatform")
        .create()

    @Test
    fun applyShouldFailIfAnotherAndroidPluginHasBeenAppliedBefore() {
        TestFileUtils.searchAndReplace(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                id("com.android.kotlin.multiplatform.library")
            """.trimIndent(),
            """
                id("com.android.library")
                id("com.android.kotlin.multiplatform.library")
            """.trimIndent()
        )

        val result =
            project.executor()
                .expectFailure().run(":kmpFirstLib:assembleAndroidMain")

        result.assertErrorContains(
            "'com.android.kotlin.multiplatform.library' and 'com.android.library' plugins cannot be applied in the same project."
        )
    }

    @Test
    fun creatingArbitraryCompilationShouldFail() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin {
                    androidLibrary {
                        compilations.create("release")
                    }
                }
            """.trimIndent()
        )

        val result =
            project.executor()
                .expectFailure().run(":kmpFirstLib:assembleAndroidMain")

        Truth.assertThat(result.failureMessage).contains(
            "Kotlin multiplatform android plugin doesn't support creating arbitrary compilations."
        )
    }

    @Test
    fun creatingTwoUnitTestCompilationsShouldFail() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin {
                    androidLibrary {
                        withAndroidTestOnJvm { }
                    }
                }
            """.trimIndent()
        )

        val result =
            project.executor()
                .expectFailure().run(":kmpFirstLib:assembleAndroidMain")

        Truth.assertThat(result.failureMessage).contains(
            "Android tests on jvm has already been enabled, and a corresponding compilation (`testOnJvm`) has already been created."
        )
    }

    @Test
    fun `accessing predefined compilations should succeed`() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin {
                    androidLibrary {
                        afterEvaluate {
                            compilations {
                                val main by getting {
                                }
                                val testOnJvm by getting {
                                }
                                val testOnDevice by getting {
                                }
                            }
                        }
                    }
                }
            """.trimIndent()
        )

        project.executor()
            .run(":kmpFirstLib:androidPrebuild")
    }

    @Test
    fun kmpWithAndroidTestOnly() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpSecondLib").ktsBuildFile,
            """
                kotlin {
                    androidLibrary {
                        withDeviceTest {}
                    }
                }
            """.trimIndent()
        )

        project.executor()
            .run(":kmpSecondLib:androidPrebuild")
    }

    @Test
    fun instrumentedTestAndroidManifestNotRequired() {
        val manifest = FileUtils.join(
            project.getSubproject("kmpFirstLib").projectDir,
            "src",
            "androidTestOnDevice",
            "AndroidManifest.xml"
        ).toPath()

        val deleted = Files.deleteIfExists(manifest)
        Truth.assertThat(deleted).isTrue()
        val result = project.executor().run(":kmpFirstLib:packageAndroidTestOnDevice")

        ScannerSubject.assertThat(result.stderr).doesNotContain(
            "Manifest file does not exist"
        )
    }

    @Test
    fun testComponentNamesForEachAndroidCompilation() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                tasks.register("printAndroidComponents") {
                    doLast {
                        val compilations = kotlin.androidLibrary.compilations
                        compilations.forEach {
                            println(it.componentName)
                        }
                    }
                }
            """.trimIndent())

        val result = project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
            .run(":kmpFirstLib:printAndroidComponents")

        ScannerSubject.assertThat(result.stdout).contains("androidMain")
        ScannerSubject.assertThat(result.stdout).contains("androidTestOnJvm")
        ScannerSubject.assertThat(result.stdout).contains("androidTestOnDevice")
    }
}
