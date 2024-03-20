/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.Version
import com.android.build.gradle.integration.common.fixture.GradleBuildResult
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.forEachLine
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.StringOption
import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class DifferentProjectClassLoadersTest {
    @JvmField
    @Rule
    val project = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject(
                mapOf(
                    "androidLib1" to MinimalSubProject.lib("com.example.androidLib1"),
                    "androidLib2" to MinimalSubProject.lib("com.example.androidLib2")
                )
            )
        ).create()

    @JvmField
    @Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        // do not add any buildscript dependencies, those are added per project
        project.buildFile.writeText(
            """
            apply from: "../commonHeader.gradle"
        """.trimIndent()
        )
        addDirectClasspath("androidLib1")
        addDirectClasspath("androidLib2")
    }

    /** Regression test for b/154388196. */
    @Test
    fun testCleanBuild() {
        val result = project.executor().run("assembleDebug")
        verifyClassLoaderSetup(result)
    }

    /**
     * Test lint because we register the [LintParallelBuildService] once instead of registering one
     * per classloader.
     */
    @Test
    fun testLint() {
        val result = project.executor().run("lintDebug")
        verifyClassLoaderSetup(result)
    }

    @Test
    fun testAttributionFile() {
        fun setUpDummyTask(taskName: String): String =
            """
                task $taskName {
                    doLast {
                        // do nothing
                    }
                }

                afterEvaluate { project ->
                    android.libraryVariants.all { variant ->
                        def assembleTask = tasks.getByPath("assemble${"$"}{variant.name.capitalize()}")
                        assembleTask.dependsOn $taskName
                    }
                }
                """.trimIndent()
        TestFileUtils.appendToFile(
            project.getSubproject("androidLib1").buildFile,
            setUpDummyTask("firstLibTask")
        )
        TestFileUtils.appendToFile(
            project.getSubproject("androidLib2").buildFile,
            setUpDummyTask("secondLibTask")
        )

        val attributionDir = temporaryFolder.newFolder()
        val result = project.executor()
            .with(StringOption.IDE_ATTRIBUTION_FILE_LOCATION, attributionDir.absolutePath)
            .run("assembleDebug")
        verifyClassLoaderSetup(result)

        val attributionData = AndroidGradlePluginAttributionData.load(attributionDir)!!
        assertThat(attributionData.taskNameToTaskInfoMap.keys).contains("firstLibTask")
        assertThat(attributionData.taskNameToTaskInfoMap.keys).contains("secondLibTask")
    }

    private fun addDirectClasspath(name: String) {
        project.getSubproject(name).buildFile.also {
            val currentBuild = it.readText()
            val customJar = temporaryFolder.newFile(name)
            JarOutputStream(FileOutputStream(customJar)).use {
                it.putNextEntry(JarEntry(name))
                it.write(name.toByteArray())
                it.closeEntry()
            }
            it.writeText(
                """
                |buildscript {
                |  apply from: "../../commonBuildScript.gradle"
                |  dependencies {
                |    classpath 'com.android.tools.build:gradle:${Version.ANDROID_GRADLE_PLUGIN_VERSION}'
                |    classpath files('${customJar.invariantSeparatorsPath}')
                |  }
                |}
                |println("Class loader for AGP API = " + com.android.build.api.dsl.LibraryExtension.class.getClassLoader().hashCode())
                |$currentBuild
            """.trimMargin()
            )
        }
    }

    private fun verifyClassLoaderSetup(result: GradleBuildResult) {
        val taskLogs = mutableSetOf<String>()
        result.stdout.forEachLine {
            if (it.startsWith("Class loader for AGP API = ")) taskLogs.add(it)
        }
        assertThat(taskLogs).named("Log lines that should contain different class loader hashes")
            .hasSize(2)
    }
}
