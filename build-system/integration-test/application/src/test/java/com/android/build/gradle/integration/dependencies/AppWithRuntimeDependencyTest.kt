/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.build.gradle.integration.dependencies

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.v2.ide.GraphItem
import com.android.builder.model.v2.ide.Library
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.generateAarWithContent
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Test for runtime only dependencies. Test project structure: app -> library (implementation) ----
 * library -> library2 (implementation) ---- library -> [guava (implementation) and example aar]
 *
 *
 * The test verifies that the dependency model of app module contains library2, guava and the
 * example aar as runtime only dependencies.
 */
class AppWithRuntimeDependencyTest {

    private val aar = generateAarWithContent(
            "com.example.aar",
            TestInputsGenerator.jarWithEmptyClasses(ImmutableList.of("com/example/MyClass")),
            ImmutableMap.of())
    private val mavenRepo =
            MavenRepoGenerator(listOf(
                    MavenRepoGenerator.Library(
                            "com.example:aar:1",
                            "aar",
                            aar)))
    @get:Rule val project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .withAdditionalMavenRepo(mavenRepo)
            .create()


    @Before
    fun setUp() {
        project.setIncludedProjects("app", "library", "library2")
        TestFileUtils.appendToFile(
                project.getSubproject("app").buildFile,
                """
                            dependencies {
                                implementation project(':library')
                            }
                            """)
        TestFileUtils.appendToFile(
                project.getSubproject("library").buildFile,
                """
                            dependencies {
                                implementation project(':library2')
                                implementation 'com.google.guava:guava:19.0'
                                implementation 'com.example:aar:1'
                            }
                            """)
    }


    @Test
    fun checkRuntimeClasspath() {
        val models = project.modelV2().fetchModels("debug").container

        val app = models.getProject(":app")
        val dependencies = app.variantDependencies!!

        // Verify that app has one AndroidLibrary dependency, :library.
        val compileDeps = dependencies.mainArtifact.compileDependencies.traverse(dependencies.libraries)
        val library = compileDeps.single()

        assertThat(library.projectInfo!!.projectPath).isEqualTo(":library")

        // Verify that app has runtime only dependencies on guava and the example aar.
        val runtimeDeps = dependencies.mainArtifact.runtimeDependencies!!.traverse(dependencies.libraries)
        val runtimeOnlyLibraries = (runtimeDeps.toSet() - compileDeps.toSet()).mapNotNull {
            it.toDependency()
        }

        assertThat(runtimeOnlyLibraries).containsExactly(
                "com.google.guava:guava:19.0",
                "com.example:aar:1"
        ).inOrder()
    }
}

// Returns all dependencies in the graph as library objects
private fun List<GraphItem>.traverse(libraries: Map<String, Library>): List<Library> {
    // Do everything in reverse order since VariantDependencies has the deepest element first
    // in dependency graph.
    val seen = HashSet<String>()
    val queue = ArrayDeque(this.reversed())
    while (queue.isNotEmpty()) {
        val from = queue.removeLast()
        if (seen.add(from.key)) {
            queue.addAll(from.dependencies.reversed())
        }
    }
    //
    return seen.reversed().map {
        libraries[it]!!
    }
}

private fun Library.toDependency() =
        libraryInfo?.let { listOf(it.group, it.name, it.version).joinToString(":") }
