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
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.ModelContainerV2
import com.android.build.gradle.integration.common.utils.TestFileUtils


internal fun GradleTestProject.getBuildMap() =
    mapOf(
        ModelContainerV2.ROOT_BUILD_ID to ModelContainerV2.BuildInfo(
            name = "kotlinMultiplatform",
            rootDir = projectDir,
            projects = settingsFile.readText().split("\n").mapNotNull {
                it.takeIf { it.startsWith("include ") }?.substringAfter("include ")
                    ?.trim('\'', ':')?.let { it to getSubproject(it).projectDir }
            }
        )
    )


internal fun GradleTestProject.publishLibs(
    publishAndroidLib: Boolean = true,
    publishKmpJvmOnly: Boolean = true,
    publishKmpFirstLib: Boolean = true,
    publishKmpSecondLib: Boolean = true,
    publishKmpLibraryPlugin: Boolean = true
) {
    TestFileUtils.appendToFile(
        settingsFile,
        """
            dependencyResolutionManagement {
                repositories {
                    maven {
                        url 'testRepo'
                    }
                }
            }
        """.trimIndent()
    )

    if (publishKmpSecondLib) {
        TestFileUtils.searchAndReplace(
            getSubproject("kmpFirstLib").ktsBuildFile,
            "project(\":kmpSecondLib\")",
            "\"com.example:kmpSecondLib-android:1.0\""
        )
    }

    if (publishKmpLibraryPlugin) {
        TestFileUtils.searchAndReplace(
            getSubproject("kmpSecondLib").ktsBuildFile,
            "project(\":kmpLibraryPlugin\")",
            "\"com.example:kmpLibraryPlugin:1.0\""
        )

        // TODO(b/290012931): This is a work around gradle matching confusion when trying to select between sources or
        //  aar lib
        TestFileUtils.appendToFile(
            getSubproject("kmpLibraryPlugin").ktsBuildFile,
            """
                afterEvaluate {
                  configurations {
                    getByName("debugSourcesElements").attributes {
                      attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_API))
                    }
                  }
                }
            """.trimIndent()
        )
    }

    if (publishAndroidLib) {
        TestFileUtils.searchAndReplace(
            getSubproject("kmpFirstLib").ktsBuildFile,
            "project(\":androidLib\")",
            "\"com.example:androidLib:1.0\""
        )
    }

    if (publishKmpJvmOnly) {
        TestFileUtils.searchAndReplace(
            getSubproject("kmpFirstLib").ktsBuildFile,
            "project(\":kmpJvmOnly\")",
            "\"com.example:kmpJvmOnly:1.0\""
        )
    }

    if (publishKmpFirstLib) {
        TestFileUtils.searchAndReplace(
            getSubproject("app").ktsBuildFile,
            "project(\":kmpFirstLib\")",
            "\"com.example:kmpFirstLib-android:1.0\""
        )
    }

    val projectsToPublish = listOfNotNull(
        "androidLib".takeIf { publishAndroidLib },
        "kmpJvmOnly".takeIf { publishKmpJvmOnly },
        "kmpLibraryPlugin".takeIf { publishKmpLibraryPlugin },
        "kmpSecondLib".takeIf { publishKmpSecondLib },
        "kmpFirstLib".takeIf { publishKmpFirstLib },
    )

    projectsToPublish.forEach { projectName ->
        TestFileUtils.searchAndReplace(
            getSubproject(projectName).ktsBuildFile,
            "plugins {",
            "plugins {\n  id(\"maven-publish\")"
        )

        TestFileUtils.appendToFile(getSubproject(projectName).ktsBuildFile,
            """
                    group = "com.example"
                    version = "1.0"
                    publishing {
                      repositories {
                        maven {
                          url = uri("../testRepo")
                        }
                      }
                    }
                """.trimIndent()
        )
    }

    if (publishAndroidLib) {
        // set up publishing for android lib
        TestFileUtils.appendToFile(
            getSubproject("androidLib").ktsBuildFile,
            """
                android {
                  publishing {
                    multipleVariants("all") {
                      allVariants()
                    }
                  }
                }

                afterEvaluate {
                  publishing {
                    publications {
                      create<MavenPublication>("all") {
                        from(components["all"])
                      }
                    }
                  }
                }
            """.trimIndent()
        )
    }

    if (publishKmpLibraryPlugin) {
        TestFileUtils.appendToFile(
            getSubproject("kmpLibraryPlugin").ktsBuildFile,
            """
                kotlin {
                    androidTarget { publishAllLibraryVariants() }
                }
            """.trimIndent()
        )
    }

    projectsToPublish.forEach {
        @Suppress("DEPRECATION") // publishing with kmp isn't configuration cache compatible (b/276472789)
        executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
            .run(":$it:publish")
    }
}
