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

package com.android.build.gradle.integration.r8

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.TestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class ShrinkLibraryTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(getMultiModuleProject())
        .create()

    private lateinit var libraryA: GradleTestProject
    private lateinit var libraryB: GradleTestProject

    @Before
    fun setUp() {
        libraryA = project.getSubproject(LIBRARY_A)
        libraryB = project.getSubproject(LIBRARY_B)

        TestFileUtils.appendToFile(libraryA.buildFile,
            """
                android {
                    buildTypes {
                        debug {
                            minifyEnabled true
                            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
                        }
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun testLambdaStubOnBootclasspath() {
        TestFileUtils.searchAndReplace(
            FileUtils.join(libraryA.mainSrcDir, "com/example/$LIBRARY_A/HelloWorld.java"),
            "// onCreate",
            """
                new java.util.ArrayList<Integer>().forEach( (n) -> System.out.println(n));
            """.trimIndent()
        )

        compileWithJava8Target(libraryA.buildFile)
        libraryA.executor().run("assembleDebug")
    }

    // Regression test for b/282544776
    @Test
    fun testExternalDependencyRClassConsideredAsClasspath() {
        TestFileUtils.appendToFile(
            libraryA.buildFile,
            "dependencies { implementation 'com.google.android.material:material:1.8.0' }"
        )
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "android.useAndroidX=true"
        )
        TestFileUtils.searchAndReplace(
            FileUtils.join(libraryA.mainSrcDir, "com/example/$LIBRARY_A/HelloWorld.java"),
            "// onCreate",
            """
                int[] a = com.google.android.material.R.styleable.ActionBar;
            """.trimIndent()
        )

        libraryA.executor().run("assembleDebug")
    }

    // Regression test for b/282544776
    @Test
    fun testProjectDependencyRClassConsideredAsClasspath() {
        TestFileUtils.appendToFile(
            libraryA.buildFile,
            "dependencies { implementation project(':$LIBRARY_B') }"
        )

        TestFileUtils.searchAndReplace(
            FileUtils.join(libraryA.mainSrcDir, "com/example/$LIBRARY_A/HelloWorld.java"),
            "// onCreate",
            "int foo = com.example.$LIBRARY_B.R.string.app_name;"
        )

        libraryA.executor().run("assembleDebug")
    }

    private fun compileWithJava8Target(buildFile: File) {
        TestFileUtils.appendToFile(buildFile,
            """
                android {
                    compileOptions {
                        sourceCompatibility JavaVersion.VERSION_1_8
                        targetCompatibility JavaVersion.VERSION_1_8
                    }
                }
            """.trimIndent()
        )
    }

    private fun getMultiModuleProject(): TestProject {
        val libraryA = HelloWorldApp.forPluginWithNamespace(
            "com.android.library", "com.example.$LIBRARY_A")
        val libraryB = HelloWorldApp.forPluginWithNamespace(
            "com.android.library", "com.example.$LIBRARY_B")
        return  MultiModuleTestProject.builder()
            .subproject(LIBRARY_A, libraryA)
            .subproject(LIBRARY_B, libraryB)
            .build()
    }

    companion object {
        const val LIBRARY_A = "libraryA"
        const val LIBRARY_B = "libraryB"
    }
}
