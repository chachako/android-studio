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

package com.android.build.gradle.integration.application

import com.android.SdkConstants
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.integration.common.fixture.GradleBuildResult
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.testutils.truth.DexClassSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class JacocoScopedArtifactsTest {
    @JvmField
    @Rule
    val project: GradleTestProject = builder().fromTestApp(MinimalSubProject.app("com.example.app"))
        .withPluginManagementBlock(true).create()

    @Before
    fun setUp() {
        project.buildFile.delete()

        project.file("build.gradle.kts").writeText(
                // language=kotlin
            """
                import com.android.build.api.variant.ScopedArtifacts
                import com.android.build.api.artifact.ScopedArtifact
                import java.util.jar.JarEntry
                import java.util.jar.JarFile
                import java.util.jar.JarOutputStream
                import java.util.jar.JarInputStream

                apply(from = "../commonHeader.gradle")
                plugins {
                    id("com.android.application")
                }

                android {
                    namespace = "com.example.app"
                    compileSdkVersion(${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION})
                    buildTypes {
                        debug {
                            enableAndroidTestCoverage = true
                        }
                    }
                }

                abstract class ValidateScopedArtifactsTask : DefaultTask() {
                    @get:InputFiles
                    abstract val directories: ListProperty<Directory>

                    @get:InputFiles
                    abstract val jars: ListProperty<RegularFile>

                    @TaskAction
                    fun action() {
                        val locations = directories.get().map { it.toString() } + jars.get().map { it.toString() }
                        System.out.println("Scoped artifacts: " + locations.joinToString(File.pathSeparator))
                    }
                }

                abstract class TransformScopedArtifactsTask : DefaultTask() {
                    @get:InputFiles
                    abstract val directories: ListProperty<Directory>

                    @get:InputFiles
                    abstract val jars: ListProperty<RegularFile>

                    @get:OutputFile
                    abstract val outputJar: RegularFileProperty

                    @TaskAction
                    fun action() {
                        val locations = directories.get().map { it.toString() } + jars.get().map { it.toString() }
                        System.out.println("Scoped artifacts: " + locations.joinToString(File.pathSeparator))
                        val jarEntries = mutableSetOf<String>()
                        JarOutputStream(outputJar.get().asFile.outputStream().buffered()).use { out ->
                            for (jar in jars.get()) {
                                val inputStream = JarInputStream(jar.asFile.inputStream().buffered())
                                while (true) {
                                    val entry = inputStream.nextEntry ?: break
                                    if (!jarEntries.contains(entry.name)) {
                                        jarEntries.add(entry.name)
                                        out.putNextEntry(JarEntry(entry.name))
                                        inputStream.copyTo(out)
                                    }
                                }
                            }
                            for (dir in directories.get()) {
                                val root = dir.asFile
                                val dir = root.walk().forEach { file ->
                                    if (file.isFile()) {
                                        out.putNextEntry(JarEntry(root.toPath().relativize(file.toPath()).toString()))
                                        file.inputStream().use { it.copyTo(out) }
                                    }
                                }
                            }
                        }
                    }
                }

                dependencies {
                    implementation("com.google.guava:guava:19.0")
                }
            """.trimIndent()
        )

        FileUtils.createFile(project.file("src/main/java/com/example/app/Example.java"),
            """
                package com.example.app;

                public class Example {
                }
            """.trimIndent())
    }

    private fun `add reader`(scope: ScopedArtifacts.Scope) {
        project.file("build.gradle.kts").appendText(
        """

              androidComponents {
                    onVariants { variant ->
                        val taskProvider =
                            project.tasks.register<ValidateScopedArtifactsTask>("${'$'}{variant.name}ValidateScopedArtifacts")
                        taskProvider.configure {
                            group = "example"
                        }
                        variant.artifacts.forScope(ScopedArtifacts.Scope.${scope.name})
                            .use(taskProvider)
                            .toGet(
                                ScopedArtifact.CLASSES,
                                ValidateScopedArtifactsTask::jars,
                                ValidateScopedArtifactsTask::directories
                            )
                    }
                }
        """.trimIndent())

    }

    private fun `add transformation`(scope: ScopedArtifacts.Scope) {
        project.file("build.gradle.kts").appendText(
                """

               androidComponents {
                    onVariants { variant ->
                        val taskProvider =
                            project.tasks.register<TransformScopedArtifactsTask>("${'$'}{variant.name}TransformScopedArtifacts")
                        taskProvider.configure {
                            group = "example"
                        }
                        variant.artifacts.forScope(ScopedArtifacts.Scope.${scope.name})
                            .use(taskProvider)
                            .toTransform(
                                ScopedArtifact.CLASSES,
                                TransformScopedArtifactsTask::jars,
                                TransformScopedArtifactsTask::directories,
                                TransformScopedArtifactsTask::outputJar,
                            )
                    }
                }
        """.trimIndent())

    }

    @Test
    fun validateProjectClassesRead() {
        `add reader`(ScopedArtifacts.Scope.PROJECT)
        validateClassesBeforeAndAfterInstrumentation()
    }

    @Test
    fun validateAllClassesRead() {
        `add reader`(ScopedArtifacts.Scope.ALL)
        validateClassesBeforeAndAfterInstrumentation()
    }

    @Test
    fun validateProjectClassesInstrumentation() {
        `add transformation`(ScopedArtifacts.Scope.PROJECT)
        val result = project.executor().run("assembleDebug", "debugTransformScopedArtifacts")
        validateScopedClassesAreNotInstrumented(result)
        validateApkIsInstrumented()
    }

    @Test
    fun validateAllClassesInstrumentation() {
        `add transformation`(ScopedArtifacts.Scope.ALL)
        val result = project.executor().run("assembleDebug", "debugTransformScopedArtifacts")
        validateScopedClassesAreNotInstrumented(result)
        validateApkIsInstrumented()
    }

    private fun validateApkIsInstrumented() {
        project.getApk(GradleTestProject.ApkType.DEBUG).use { mainApk ->
            assertThat(mainApk.getClass("Lcom/example/app/Example;")).hasField("\$jacocoData")
            assertThat(mainApk.getClass("Lcom/google/common/collect/ImmutableList;")).hasField("\$jacocoData")
        }
    }

    private fun validateClassesBeforeAndAfterInstrumentation() {
        val result = project.executor().run("assembleDebug", "debugValidateScopedArtifacts")
        validateScopedClassesAreNotInstrumented(result)
        validateApkIsInstrumented()

        val classesFolder = FileUtils.join(
            project.buildDir,
            SdkConstants.FD_INTERMEDIATES,
            "classes/debug/jacocoDebug"
        )

        val dirFile = FileUtils.join(classesFolder, "dirs/com/example/app/Example.class")
        validateDirectoryClass(jacocoPresent = true, dirFile)

        val jarFile = FileUtils.join(classesFolder, "jars/0.jar")
        validateJarClass(jacocoPresent = true, jarFile)
    }

    private fun validateScopedClassesAreNotInstrumented(buildResult: GradleBuildResult) {
        ScannerSubject.assertThat(buildResult.stdout).contains("Scoped artifacts: ")
        var artifacts = ""
        val output = buildResult.stdout
        while (output.hasNextLine()) {
            val line = output.nextLine()
            if (line.contains("Scoped artifacts: ")) {
                artifacts = line.substringAfter("Scoped artifacts: ")
            }
        }
        Truth.assertThat(artifacts).isNotEmpty()
        val artifactPaths = artifacts.split(File.pathSeparator)
        artifactPaths.forEach { path ->
            if (path.endsWith("R.jar")) {
                validateJarClass(jacocoPresent = false, File(path))
            } else if (path.endsWith("classes")) {
                val dirFile = FileUtils.join(File(path), "com/example/app/Example.class")
                validateDirectoryClass(jacocoPresent = false, dirFile)
            }
        }
    }

    private fun validateDirectoryClass(jacocoPresent: Boolean, classFile: File) {
        val classReader = ClassReader(classFile.readBytes())
        val classNode = ClassNode(Opcodes.ASM7)
        classReader.accept(classNode, 0)
        if (jacocoPresent) {
            Truth.assertThat(classNode.fields[0].name).isEqualTo("\$jacocoData")
        } else {
            Truth.assertThat(classNode.fields).isEmpty()
        }
    }

    private fun validateJarClass(jacocoPresent: Boolean, classFile: File) {
        val zipFile = ZipFile(classFile)
        try {
            val entry: ZipEntry = zipFile.getEntry("com/example/app/R.class")
            TruthHelper.assertThat(entry).named("R.class entry").isNotNull()
            val classReader = ClassReader(zipFile.getInputStream(entry))
            val classNode = ClassNode(Opcodes.ASM7)
            classReader.accept(classNode, 0)
            if (jacocoPresent) {
                Truth.assertThat(classNode.fields[0].name).isEqualTo("\$jacocoData")
            } else {
                Truth.assertThat(classNode.fields).isEmpty()
            }
        } catch (exception: IOException) {
            error(exception)
        }
    }
}
