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

package com.android.build.gradle.integration.common.fixture.project.builder

import com.android.Version
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

class GradleBuildDefinitionTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun testSettingsWithSingleProject() {
        val folder = writeBuild {
            subProject(":app") { }
        }

        checkFile(
            folder.resolve("settings.gradle"),
            "settings file presence",
            """
                pluginManagement {
                  repositories {
                  }
                }
                plugins {
                }
                dependencyResolutionManagement {
                  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                  repositories {
                  }
                }
                include(':app')

            """.trimIndent())
    }

    @Test
    fun testSettingsWithIncludedBuild() {
        val folder = writeBuild {
            includedBuild("build-logic") { }
        }

        checkFile(
            folder.resolve("settings.gradle"),
            "settings file presence",
            """
                pluginManagement {
                  repositories {
                  }
                }
                plugins {
                }
                dependencyResolutionManagement {
                  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                  repositories {
                  }
                }
                includeBuild('build-logic')

            """.trimIndent())
    }

    @Test
    fun testRepositories() {
        val repo = temporaryFolder.newFolder().toPath()
        val folder = writeBuild(listOf(repo)) { }

        val repoUri = repo.toUri().toString()

        checkFile(
            folder.resolve("settings.gradle"),
            "settings file presence",
            """
                pluginManagement {
                  repositories {
                    maven {
                      url = uri('$repoUri')
                      metadataSources {
                        mavenPom()
                        artifact()
                      }
                    }
                  }
                }
                plugins {
                }
                dependencyResolutionManagement {
                  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                  repositories {
                    maven {
                      url = uri('$repoUri')
                      metadataSources {
                        mavenPom()
                        artifact()
                      }
                    }
                  }
                }

            """.trimIndent()
        )
    }

    @Test
    fun testPlugins() {
        val folder = writeBuild {
            subProject(":app") {
                plugins.add(PluginType.ANDROID_APP)
            }
            subProject(":library") {
                plugins.add(PluginType.JAVA_LIBRARY)
            }
        }

        checkFile(
            folder.resolve("build.gradle"),
            "root build file presence",
            """
                plugins {
                  id('com.android.application') version '${Version.ANDROID_GRADLE_PLUGIN_VERSION}' apply false
                }
                dependencies {
                }

            """.trimIndent()
        )

        checkFile(
            folder.resolve("app/build.gradle"),
            "app build file presence",
            """
                plugins {
                  id('com.android.application')
                }
                dependencies {
                }

            """.trimIndent()
        )

        checkFile(
            folder.resolve("library/build.gradle"),
            "library build file presence",
            """
                plugins {
                  id('java-library')
                }
                dependencies {
                }

            """.trimIndent()
        )
    }

    @Test
    fun testDependencies() {
        val folder = writeBuild {
            subProject(":app") {
                dependencies {
                    api(project(":library"))
                }
            }
            subProject(":library") { }
        }

        checkFile(
            folder.resolve("app/build.gradle"),
            "app build file presence",
            """
                plugins {
                }
                dependencies {
                  api(project(':library'))
                }

            """.trimIndent()
        )
    }

    private fun checkFile(
        file: Path,
        named: String,
        content: String
    ) {
        Truth.assertThat(file.isRegularFile()).named(named).isTrue()
        Truth.assertThat(file.readText()).isEqualTo(content)
    }

    private fun writeBuild(repositories: List<Path> = listOf(), action: GradleBuildDefinition.() -> Unit): Path {
        val build = GradleBuildDefinitionImpl("root").also {
            action(it)
        }

        val folder = temporaryFolder.newFolder().toPath()

        build.write(
            location = folder,
            repositories = repositories,
            writerProvider = object : WriterProvider {
                override fun getBuildWriter() = GroovyBuildWriter()
            }
        )

        return folder
    }
}
