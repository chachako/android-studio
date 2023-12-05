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

package com.android.build.gradle.integration.sources


import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldAppWithJavaLibs
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.android.testutils.truth.ZipFileSubject
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.File

class SourcesJarTest {

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(HelloWorldLibraryApp.create())
            .create()

    /** Regression test for http://b/214428179.*/
    @Test
    fun testAddingKotlinSourcesInJavaSources() {
        project.getSubproject(":lib").also { libProject ->
            libProject.buildFile.appendText(
        """
            android {
                publishing {
                    singleVariant("release") {
                        withSourcesJar()
                        withJavadocJar()
                    }
                }
            }

            abstract class KotlinGenerator extends DefaultTask {
                @OutputDirectory
                abstract DirectoryProperty getOutputDirectory();

                @TaskAction
                void run() {
                    def outputFile = new File(getOutputDirectory().get().getAsFile(), "SomeSource.kt")
                    new FileWriter(outputFile).with {
                    	write("Some Kotlin code\n")
                    	flush()
                    }
                }
            }

            def writeKotlinTask = tasks.register("createKotlinSources", KotlinGenerator.class)
            androidComponents {
                onVariants(selector().all(),  { variant ->
                    // register it under java source code.
                    variant.sources.java.addGeneratedSourceDirectory(writeKotlinTask, KotlinGenerator::getOutputDirectory)
                })
            }
        """.trimIndent()
            )

            val result = libProject.executor().run("sourceReleaseJar")
            val sourceJar = File(
                libProject.buildDir,
                "intermediates/source_jar/release/release-sources.jar"
            )
            Truth.assertThat(sourceJar.exists()).isTrue()
            ZipFileSubject.assertThat(sourceJar) {
                it.contains("SomeSource.kt")
            }
        }
    }

    @Test
    fun testAddingKotlinSourcesInKotlinSources() {
        project.getSubproject(":lib").also { libProject ->
            libProject.buildFile.appendText(
                """
            android {
                publishing {
                    singleVariant("release") {
                        withSourcesJar()
                        withJavadocJar()
                    }
                }
            }

            abstract class KotlinGenerator extends DefaultTask {
                @OutputDirectory
                abstract DirectoryProperty getOutputDirectory();

                @TaskAction
                void run() {
                    def outputFile = new File(getOutputDirectory().get().getAsFile(), "SomeSource.kt")
                    new FileWriter(outputFile).with {
                    	write("Some Kotlin code\n")
                    	flush()
                    }
                }
            }

            def writeKotlinTask = tasks.register("createKotlinSources", KotlinGenerator.class)
            androidComponents {
                onVariants(selector().all(),  { variant ->
                    // register it under java source code.
                    variant.sources.kotlin.addGeneratedSourceDirectory(writeKotlinTask, KotlinGenerator::getOutputDirectory)
                })
            }
        """.trimIndent()
            )

            val result = libProject.executor().run("sourceReleaseJar")
            val sourceJar = File(
                libProject.buildDir,
                "intermediates/source_jar/release/release-sources.jar"
            )
            Truth.assertThat(sourceJar.exists()).isTrue()
            ZipFileSubject.assertThat(sourceJar) {
                it.contains("SomeSource.kt")
            }
        }
    }

    @Test
    fun testAddingJavaSources() {
        project.getSubproject(":lib").also { libProject ->
            libProject.buildFile.appendText(
                """
            android {
                publishing {
                    singleVariant("release") {
                        withSourcesJar()
                        withJavadocJar()
                    }
                }
            }

            abstract class JavaGenerator extends DefaultTask {
                @OutputDirectory
                abstract DirectoryProperty getOutputDirectory();

                @TaskAction
                void run() {
                    def outputFile = new File(getOutputDirectory().get().getAsFile(), "SomeSource.java")
                    new FileWriter(outputFile).with {
                    	write("Some Kotlin code\n")
                    	flush()
                    }
                }
            }

            def writeKotlinTask = tasks.register("createJavaSources", JavaGenerator.class)
            androidComponents {
                onVariants(selector().all(),  { variant ->
                    // register it under java source code.
                    variant.sources.java.addGeneratedSourceDirectory(writeKotlinTask, JavaGenerator::getOutputDirectory)
                })
            }
        """.trimIndent()
            )

            val result = libProject.executor().run("sourceReleaseJar")
            val sourceJar = File(
                libProject.buildDir,
                "intermediates/source_jar/release/release-sources.jar"
            )
            Truth.assertThat(sourceJar.exists()).isTrue()
            ZipFileSubject.assertThat(sourceJar) {
                it.contains("SomeSource.java")
            }
        }
    }
}
