/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture.testprojects

import com.android.build.gradle.integration.common.fixture.project.builder.BuildWriter
import com.android.build.gradle.integration.common.fixture.project.builder.GroovyBuildWriter
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import com.android.utils.FileUtils
import java.io.File
import java.nio.file.Path

class DependenciesBuilderImpl() : DependenciesBuilder {
    private val dependencies = mutableListOf<Pair<String, Any>>()

    val externalLibraries: List<MavenRepoGenerator.Library>
        get() = dependencies
            .map { it.second }
            .filterIsInstance(MavenRepoGenerator.Library::class.java)

    override fun clear() {
        dependencies.clear()
    }

    override fun implementation(dependency: Any) {
        dependencies.add("implementation" to dependency)
    }

    override fun api(dependency: Any) {
        dependencies.add("api" to dependency)
    }

    override fun compileOnly(dependency: Any) {
        dependencies.add("compileOnly" to dependency)
    }

    override fun compileOnlyApi(dependency: Any) {
        dependencies.add("compileOnlyApi" to dependency)
    }

    override fun runtimeOnly(dependency: Any) {
        dependencies.add("runtimeOnly" to dependency)
    }

    override fun testImplementation(dependency: Any) {
        dependencies.add("testImplementation" to dependency)
    }

    override fun testRuntimeOnly(dependency: Any) {
        dependencies.add("testRuntimeOnly" to dependency)
    }

    override fun androidTestImplementation(dependency: Any) {
        dependencies.add("androidTestImplementation" to dependency)
    }

    override fun include(dependency: Any) {
        dependencies.add("include" to dependency)
    }

    override fun requiredSdk(dependency: Any) {
        dependencies.add("requiredSdk" to dependency)
    }

    override fun optionalSdk(dependency: Any) {
        dependencies.add("optionalSdk" to dependency)
    }

    override fun lintPublish(dependency: Any) {
        dependencies.add("lintPublish" to dependency)
    }

    override fun lintChecks(dependency: Any) {
        dependencies.add("lintChecks" to dependency)
    }

    override fun localJar(action: LocalJarBuilder.() -> Unit): LocalJarBuilder =
            LocalJarBuilderImpl().also { action(it) }

    override fun project(path: String, testFixtures: Boolean, configuration: String?): ProjectDependencyBuilder =
            ProjectDependencyBuilderImpl(path, testFixtures, configuration)

    override fun externalLibrary(path: String, testFixtures: Boolean): ExternalDependencyBuilder =
            ExternalDependencyBuilderImpl(path, testFixtures)

    override fun coreLibraryDesugaring(dependency: Any) {
        dependencies.add("coreLibraryDesugaring" to dependency)
    }

    override fun ksp(dependency: Any) {
        dependencies.add("ksp" to dependency)
    }

    fun writeBuildFile(sb: StringBuilder, projectDir: File) {
        sb.append("\ndependencies {\n")
        for ((scope, dependency) in dependencies) {
            when (dependency) {
                is String -> sb.append("$scope '$dependency'\n")
                is ExternalDependencyBuilder -> {
                    if (dependency.testFixtures) {
                        sb.append("$scope testFixtures('${dependency.coordinate}')\n")
                    } else {
                        sb.append("$scope '${dependency.coordinate}'\n")
                    }
                }
                is ProjectDependencyBuilder -> {
                    val projectStr = dependency.configuration?.let { configName ->
                        "project(path: '${dependency.path}', configuration: '$configName')"
                    } ?: "project('${dependency.path}')"

                    val dependencyStr = if (dependency.testFixtures) {
                        "testFixtures($projectStr)"
                    } else {
                        projectStr
                    }

                    sb.append("$scope $dependencyStr\n")
                }
                is MavenRepoGenerator.Library -> sb.append("$scope '${dependency.mavenCoordinate}'\n")
                is LocalJarBuilderImpl -> {
                    val path = createLocalJar(dependency, projectDir)
                    sb.append("$scope files('$path')\n")
                }
                else -> throw RuntimeException("unsupported dependency type: ${dependency.javaClass}")
            }
        }

        sb.append("}\n")
    }

    fun write(buildWriter: BuildWriter, projectLocation: Path) {
        buildWriter.apply {
            block("dependencies") {
                for ((scope, dependency) in dependencies) {
                    when (dependency) {
                        is String -> dependency(scope, dependency)
                        is ExternalDependencyBuilder -> {
                            if (dependency.testFixtures) {
                                dependency(scope, rawMethod("testFixtures", dependency.coordinate))
                            } else {
                                dependency(scope, dependency.coordinate)
                            }
                        }

                        is ProjectDependencyBuilder -> {
                            val projectNotation = dependency.configuration?.let { configName ->
                                rawMethod("project", listOf(
                                    "path" to dependency.path,
                                    "configuration" to configName
                                ))
                            } ?: rawMethod("project", dependency.path)

                            val dep = if (dependency.testFixtures) {
                                rawMethod("testFixtures", projectNotation)
                            } else {
                                projectNotation
                            }

                            dependency(scope, dep)
                        }

                        is MavenRepoGenerator.Library -> dependency(scope, dependency.mavenCoordinate.toString())
                        is LocalJarBuilderImpl -> {
                            val path = createLocalJar(dependency, projectLocation.toFile())
                            dependency(scope, rawMethod("files", path))
                        }

                        else -> throw RuntimeException("unsupported dependency type: ${dependency.javaClass}")
                    }
                }
            }
        }
    }

    private fun createLocalJar(
        localJarSpec : LocalJarBuilderImpl,
        projectDir: File,
    ): String {
        val libsFolder = File(projectDir, "libs")
        FileUtils.mkdirs(libsFolder)
        val jarFile = File(libsFolder, localJarSpec.name)
        TestInputsGenerator.jarWithEmptyClasses(jarFile.toPath(), localJarSpec.classNames)

        return "libs/${localJarSpec.name}"
    }
}

private class LocalJarBuilderImpl(
    override var name: String = "foo.jar"
): LocalJarBuilder {
    val classNames = mutableSetOf<String>()

    override fun addClass(className: String) {
        classNames.add(className)
    }
}

private class ProjectDependencyBuilderImpl(
    override val path: String,
    override val testFixtures: Boolean,
    override val configuration: String? = null
): ProjectDependencyBuilder

private class ExternalDependencyBuilderImpl(
    override val coordinate: String,
    override val testFixtures: Boolean
): ExternalDependencyBuilder
