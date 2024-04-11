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

package com.android.build.gradle.integration.lint

import com.android.Version
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.forEachLine
import com.android.build.gradle.internal.dsl.ModulePropertyKey.OptionalBoolean
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.OptionalBooleanOption
import com.android.build.gradle.options.StringOption.LINT_RESERVED_MEMORY_PER_TASK
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/**
 * Regression test for b/337848180.
 *
 * Creates a project with many modules (with android.lint.useK2Uast set to true for half of them),
 * and then runs lint on all modules.
 */
@RunWith(FilterableParameterized::class)
class LintWithAndWithoutK2UastTest(private val runLintInProcess: Boolean) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "runLintInProcess_{0}")
        fun parameters() = listOf(true, false)
    }

    private val multiModuleTestProject: MultiModuleTestProject
        get() {
            val builder = MultiModuleTestProject.builder()
            for (i in 1..10) {
                val lib =
                    MinimalSubProject.lib()
                        .withFile(
                            "src/main/kotlin/Example.kt",
                            """
                                package com.example
                                class Example
                                """.trimIndent()
                        )
                if (i % 2 == 0) {
                    lib.appendToBuild(
                        """
                            android {
                                experimentalProperties["${OptionalBoolean.LINT_USE_K2_UAST.key}"] = true
                            }
                            """.trimIndent()
                    )
                }
                builder.subproject(":lib$i", lib)
            }
            return builder.build()
        }


    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(multiModuleTestProject)
            .addGradleProperties("${OptionalBooleanOption.LINT_USE_K2_UAST.propertyName}=false")
            .create()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun testLintWithAndWithoutK2Uast() {
        project.executor()
            .with(BooleanOption.RUN_LINT_IN_PROCESS, runLintInProcess)
            .with(LINT_RESERVED_MEMORY_PER_TASK, "256M")
            .run("clean", "lint")
    }

    /**
     * Modify the project to use 3 different class loaders
     */
    @Test
    fun testLintWithAndWithoutK2Uast_multipleClassLoaders() {
        // do not add any buildscript dependencies, those are added per project
        project.buildFile.writeText(
            """
            apply from: "../commonHeader.gradle"
        """.trimIndent()
        )
        // Create 3 random jars. Each module will have one of the jars added to its buildscript
        // classpath.
        val jarFiles: MutableList<File> = mutableListOf()
        for (i in 1..3) {
            val name = "jar$i"
            val customJar = temporaryFolder.newFile(name)
            JarOutputStream(FileOutputStream(customJar)).use {
                it.putNextEntry(JarEntry(name))
                it.write(name.toByteArray())
                it.closeEntry()
            }
            jarFiles.add(customJar)
        }
        for (i in 1..10) {
            project.getSubproject("lib$i").buildFile.also {
                val currentBuild = it.readText()
                it.writeText(
                    """
                        |buildscript {
                        |  apply from: "../../commonBuildScript.gradle"
                        |  dependencies {
                        |    classpath 'com.android.tools.build:gradle:${Version.ANDROID_GRADLE_PLUGIN_VERSION}'
                        |    classpath files('${jarFiles[i % 3].invariantSeparatorsPath}')
                        |  }
                        |}
                        |println("Class loader for AGP API = " + com.android.build.api.dsl.LibraryExtension.class.getClassLoader().hashCode())
                        |$currentBuild
                        """.trimMargin()
                )
            }
        }

        val result =
            project.executor()
                .with(BooleanOption.RUN_LINT_IN_PROCESS, runLintInProcess)
                .with(LINT_RESERVED_MEMORY_PER_TASK, "256M")
                .run("clean", "lint")

        // Verify class loader setup
        val taskLogs = mutableSetOf<String>()
        result.stdout.forEachLine {
            if (it.startsWith("Class loader for AGP API = ")) taskLogs.add(it)
        }
        assertThat(taskLogs).named("Log lines that should contain different class loader hashes")
            .hasSize(3)
    }
}
