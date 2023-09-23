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

package com.android.build.gradle.integration.nativebuild

import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.apk.Aar
import com.android.testutils.apk.Apk
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.io.path.pathString

class KotlinMultiplatformAndroidPluginNativeTest {

    @get:Rule
    val project = GradleTestProjectBuilder()
        .fromTestProject("kotlinMultiplatform")
        .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.settingsFile,
            "\ninclude ':nativeLib'"
        )
        val jniKotlinFile = FileUtils.join(
            project.getSubproject("kmpFirstLib").projectDir,
            "src", "androidMain", "kotlin", "com", "example", "nativelib", "Incrementer.kt"
        ).also {
            it.parentFile.mkdirs()
        }
        FileUtils.writeToFile(
            jniKotlinFile,
            // language=kotlin
            """
                package com.example.nativelib

                internal class Jni {
                    external fun nativeGetNextNumber(x: Int): Int
                }

                class Incrementer {
                    private val jni = Jni()
                    fun getNextNumber(x: Int) = jni.nativeGetNextNumber(x)
                }
            """.trimIndent()
        )

        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            // language=kotlin
            """
                kotlin.sourceSets.getByName("androidMain") {
                    dependencies {
                        compileOnly(project(":nativeLib"))
                    }
                }

                kotlin.sourceSets.getByName("androidInstrumentedTest") {
                    dependencies {
                        implementation(project(":nativeLib"))
                    }
                }

                configurations.create("mergeNativeLibs") {
                    isCanBeConsumed = false
                    isCanBeResolved = true
                }

                dependencies {
                    add("mergeNativeLibs", project(":nativeLib"))
                }

                abstract class PackagingTask: Zip() {
                    @get:InputFile
                    abstract val aarFile: RegularFileProperty

                    @get:OutputFile
                    abstract val output: RegularFileProperty
                }

                androidComponents {
                    onVariant {
                        val taskProvider = project.tasks.register("repackageAar", PackagingTask::class.java)

                        it.artifacts.use(
                            taskProvider
                        ).wiredWithFiles(PackagingTask::aarFile, PackagingTask::output)
                            .toTransform(com.android.build.api.artifact.SingleArtifact.AAR)

                        taskProvider.configure {
                            val nativeLibsArtifact =
                                project.configurations.getByName("mergeNativeLibs").incoming.artifactView {
                                    attributes {
                                        attribute(Attribute.of("artifactType", String::class.java), "android-jni")
                                    }
                                }

                            from(nativeLibsArtifact.artifacts.artifactFiles) {
                                includeEmptyDirs = false
                                eachFile {
                                    relativePath = relativePath.prepend("jni")
                                }
                                include { element ->
                                    element.isDirectory || element.name == "libnative_lib.so"
                                }
                            }

                            from(project.zipTree(aarFile))

                            destinationDirectory.fileProvider(output.locationOnly.map { it.asFile.parentFile })
                            archiveFileName.set(output.locationOnly.map { it.asFile.name })
                        }
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun testKmpLibraryAarContents() {
        project.executor()
            .run(":kmpFirstLib:assemble")

        Aar(
            project.getSubproject("kmpFirstLib").getOutputFile(
                "aar",
                "kmpFirstLib.aar"
            )
        ).use { aar ->
            aar.getEntryAsZip("classes.jar").use { classesJar ->
                Truth.assertThat(classesJar.entries.map { it.pathString }).containsExactlyElementsIn(
                    listOf(
                        "/kmp_resource.txt",
                        "/com/example/kmpfirstlib/KmpCommonFirstLibClass.class",
                        "/com/example/kmpfirstlib/KmpAndroidFirstLibClass.class",
                        "/com/example/kmpfirstlib/KmpAndroidFirstLibJavaClass.class",
                        "/com/example/kmpfirstlib/KmpAndroidActivity.class",
                        "/com/example/nativelib/Jni.class",
                        "/com/example/nativelib/Incrementer.class"
                    )
                )
            }

            Truth.assertThat(aar.getEntry("jni/x86/libnative_lib.so")).isNotNull()
        }
    }

    @Test
    fun testKmpLibraryTestApkContents() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin.androidLibrary {
                    packaging.resources.excludes.addAll(listOf(
                        "**/*.java",
                        "junit/**",
                        "LICENSE-junit.txt"
                    ))
                }
            """.trimIndent()
        )

        project.executor()
            .run(":kmpFirstLib:assembleInstrumentedTest")

        val testApk = project.getSubproject("kmpFirstLib").getOutputFile(
            "apk", "androidTest", "main", "kmpFirstLib-androidTest.apk"
        )

        Truth.assertThat(testApk.exists()).isTrue()

        Apk(testApk).use { apk ->
            // all contents
            Truth.assertThat(
                apk.entries.map { it.pathString }.filterNot {
                    it.startsWith("/res") || it.endsWith(".kotlin_builtins") ||
                            it.startsWith("/META-INF") ||
                            (it.startsWith("/classes") && it.endsWith(".dex"))
                }
            ).containsExactlyElementsIn(
                listOf(
                    "/AndroidManifest.xml",
                    "/kmp_resource.txt",
                    "/android_lib_resource.txt",
                    "/lib/x86_64/libnative_lib.so",
                    "/lib/x86/libnative_lib.so",
                    "/lib/armeabi-v7a/libnative_lib.so",
                    "/lib/arm64-v8a/libnative_lib.so"
                )
            )
        }
    }

    @Test
    fun publicationMetadataDoesNotIncludeNativeLib() {
        TestFileUtils.searchAndReplace(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            "plugins {",
            "plugins {\n  id(\"maven-publish\")"
        )

        TestFileUtils.appendToFile(project.getSubproject("kmpFirstLib").ktsBuildFile,
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

        project.executor().run(":kmpFirstLib:publish")


        // Assert that maven metadata and gradle module metadata files have no mention of the native
        // lib dependency.
        Truth.assertThat(
            FileUtils.join(
                project.projectDir, "testRepo", "com", "example", "kmpFirstLib-android", "maven-metadata.xml"
            ).readText()
        ).doesNotContain("native")

        Truth.assertThat(
            FileUtils.join(
                project.projectDir, "testRepo", "com", "example", "kmpFirstLib-android", "1.0", "kmpFirstLib-android-1.0.module"
            ).readText()
        ).doesNotContain("native")
    }
}
