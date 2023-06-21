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
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.common.utils.getVariantByName
import com.android.builder.model.AndroidProject
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.generateAarWithContent
import com.android.utils.PathUtils
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Iterables
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.stream.Collectors

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
    fun checkRuntimeClasspathWithLevel1Model() {
        val models = project.model()
                .level(AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD)
                .fetchAndroidProjects()
                .onlyModelMap
        val appDebug = models[":app"]!!.getVariantByName("debug")
        val deps = appDebug.mainArtifact.dependencies

        // Verify that app has one AndroidLibrary dependency, :library.
        val libs = deps.libraries
        TruthHelper.assertThat(libs).named("app android library deps count").hasSize(1)
        TruthHelper.assertThat(Iterables.getOnlyElement(libs).project)
                .named("app android library deps path")
                .isEqualTo(":library")

        // Verify that app doesn't have module dependency.
        TruthHelper.assertThat(deps.javaModules).named("app module dependency count").isEmpty()

        // Verify that app doesn't have JavaLibrary dependency.
        TruthHelper.assertThat(deps.javaLibraries).named("app java dependency count").isEmpty()

        // Verify that app has runtime only dependencies on guava and the aar.
        val runtimeOnlyClasses = deps.runtimeOnlyClasses
                .stream()
                .map { file: File -> PathUtils.toSystemIndependentPath(file.toPath()) }
                .collect(Collectors.toList())
        TruthHelper.assertThat(runtimeOnlyClasses).hasSize(2)
        // Verify the order of the artifacts too.
        TruthHelper.assertThat(runtimeOnlyClasses[0]).endsWith("/guava-19.0.jar")
        TruthHelper.assertThat(runtimeOnlyClasses[1]).endsWith("/aar-1/jars/classes.jar")
    }
}
