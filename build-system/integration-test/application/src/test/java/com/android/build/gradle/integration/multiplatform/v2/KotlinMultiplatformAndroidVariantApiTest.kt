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

import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.apk.Aar
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class KotlinMultiplatformAndroidVariantApiTest {
    @get:Rule
    val project = GradleTestProjectBuilder()
        .fromTestProject("kotlinMultiplatform")
        .create()

    @Test
    fun testInstrumentedTestDependencySubstitution() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                androidComponents {
                    onVariant { variant ->
                        variant.nestedComponents.forEach { component ->
                            println(variant.name + ":" + component.name)
                        }
                    }
                }
            """.trimIndent()
        )

        val result = project.executor().run(":kmpFirstLib:assemble")

        ScannerSubject.assertThat(result.stdout).contains("androidMain:androidUnitTest")
        ScannerSubject.assertThat(result.stdout).contains("androidMain:androidInstrumentedTest")
    }

    @Test
    fun testAddGeneratedAssets() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            // language=kotlin
            """
                kotlin.androidLibrary {
                    experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
                }

                abstract class CreateAssets: DefaultTask() {

                    @get:OutputDirectory
                    abstract val outputFolder: DirectoryProperty

                    @TaskAction
                    fun taskAction() {
                        val outputFile = File(outputFolder.asFile.get(), "asset.txt")
                        outputFile.parentFile.mkdirs()
                        outputFile.writeText("foo")
                    }
                }

                androidComponents {
                    onVariant { variant ->
                        val createAssetsTaskProvider =
                            project.tasks.register<CreateAssets>("${'$'}{variant.name}AddAssets") {
                            outputFolder.set(
                                File(project.layout.buildDirectory.asFile.get(), "assets/gen")
                            )
                        }
                        variant.sources
                            .assets
                            ?.addGeneratedSourceDirectory(
                                createAssetsTaskProvider,
                                CreateAssets::outputFolder
                            )
                    }
                }
            """.trimIndent()
        )

        project.executor().run(":kmpFirstLib:assemble")

        val aarFile = project.getSubproject("kmpFirstLib").getOutputFile("aar", "kmpFirstLib.aar")
        @Suppress("PathAsIterable")
        Aar(aarFile).use { aar -> assertThat(aar.getEntry("assets/asset.txt")).isNotNull() }
    }
}
