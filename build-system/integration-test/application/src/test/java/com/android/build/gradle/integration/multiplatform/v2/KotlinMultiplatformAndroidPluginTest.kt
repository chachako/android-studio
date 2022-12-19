/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.integration.multiplatform.v2

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.apk.Aar
import com.android.testutils.apk.Apk
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.io.path.pathString
import kotlin.io.path.readText

@RunWith(Parameterized::class)
class KotlinMultiplatformAndroidPluginTest(private val publishLibs: Boolean) {

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "publishLibs={0}")
        fun getOptions() = listOf(false, true)
    }

    @Suppress("DEPRECATION") // kmp doesn't support configuration caching for now (b/276472789)
    @get:Rule
    val project = GradleTestProjectBuilder()
        .fromTestProject("kotlinMultiplatform")
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
        .create()

    @Before
    fun setUpProject() {
        if (!publishLibs) {
            return
        }

        TestFileUtils.appendToFile(
            project.settingsFile,
            """
                dependencyResolutionManagement {
                    repositories {
                        maven {
                            url 'testRepo'
                        }
                    }
                }
            """.trimIndent()
        )

        TestFileUtils.searchAndReplace(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            "project(\":kmpSecondLib\")",
            "\"com.example:kmpSecondLib-android:1.0\""
        )

        TestFileUtils.searchAndReplace(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            "project(\":androidLib\")",
            "\"com.example:androidLib:1.0\""
        )

        TestFileUtils.searchAndReplace(
            project.getSubproject("app").ktsBuildFile,
            "project(\":kmpFirstLib\")",
            "\"com.example:kmpFirstLib-android:1.0\""
        )

        listOf("androidLib", "kmpFirstLib", "kmpSecondLib").forEach { projectName ->
            TestFileUtils.searchAndReplace(
                project.getSubproject(projectName).ktsBuildFile,
                "plugins {",
                "plugins {\n  id(\"maven-publish\")"
            )

            TestFileUtils.appendToFile(project.getSubproject(projectName).ktsBuildFile,
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
        }

        // set up publishing for android lib
        TestFileUtils.appendToFile(
            project.getSubproject("androidLib").ktsBuildFile,
            """
                android {
                  publishing {
                    multipleVariants("all") {
                      allVariants()
                    }
                  }
                }

                afterEvaluate {
                  publishing {
                    publications {
                      create<MavenPublication>("all") {
                        from(components["all"])
                      }
                    }
                  }
                }
            """.trimIndent()
        )

        project.executor().run(":androidLib:publish")
        project.executor().run(":kmpSecondLib:publish")
        project.executor().run(":kmpFirstLib:publish")
    }

    @Test
    fun testRunningUnitTests() {
        project.executor().run(":kmpFirstLib:testKotlinAndroidTest")

        assertWithMessage(
            "Running kmp unit tests should run common tests as well"
        ).that(
            FileUtils.join(
                project.getSubproject("kmpFirstLib").buildDir,
                "reports",
                "tests",
                "testKotlinAndroidTest",
                "classes"
            ).listFiles()!!.map { it.name }
        ).containsExactly(
            "com.example.kmpfirstlib.KmpAndroidFirstLibClassTest.html",
            "com.example.kmpfirstlib.KmpCommonFirstLibClassTest.html"
        )

        project.executor().run(":app:testDebugUnitTest")
    }

    @Test
    fun testAppApkContents() {
        project.executor().run(":app:assembleDebug")

        project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            // classes from commonMain are packaged
            assertThatApk(apk).hasClass("Lcom/example/kmpfirstlib/KmpCommonFirstLibClass;")
            assertThatApk(apk).hasClass("Lcom/example/kmpsecondlib/KmpCommonSecondLibClass;")

            // classes from androidMain are packaged
            assertThatApk(apk).hasClass("Lcom/example/kmpfirstlib/KmpAndroidFirstLibClass;")
            assertThatApk(apk).hasClass("Lcom/example/kmpsecondlib/KmpAndroidSecondLibClass;")

            // transitive deps are packaged
            assertThatApk(apk).hasClass("Lcom/example/androidlib/AndroidLib;")
            assertThatApk(apk).hasClass("Lcom/example/app/AndroidApp;")

            val manifestContents = ApkSubject.getManifestContent(apk.file).joinToString("\n")
            assertThat(manifestContents).contains(
                "com.example.kmpfirstlib.KmpAndroidActivity"
            )

            assertThat(apk.getEntry("kmp_resource.txt").readText()).isEqualTo(
                "kmp resource\n"
            )

            assertThat(apk.getEntry("android_lib_resource.txt").readText()).isEqualTo(
                "android lib resource\n"
            )
        }
    }

    @Test
    fun testKmpLibraryAarContents() {
        project.executor().run(":kmpFirstLib:assemble")

        Aar(
            project.getSubproject("kmpFirstLib").getOutputFile(
                "aar",
                "kmpFirstLib.aar"
            )
        ).use { aar ->

            assertThat(aar.getEntry("R.txt")).isNotNull()

            aar.getEntryAsZip("classes.jar").use { classesJar ->
                assertThat(classesJar.entries.map { it.pathString }).containsExactlyElementsIn(
                    listOf(
                        "/kmp_resource.txt",
                        "/com/example/kmpfirstlib/KmpCommonFirstLibClass.class",
                        "/com/example/kmpfirstlib/KmpAndroidFirstLibClass.class",
                        "/com/example/kmpfirstlib/KmpAndroidActivity.class",
                    )
                )

                assertThat(classesJar.getEntry("kmp_resource.txt").readText()).isEqualTo(
                    "kmp resource\n"
                )
            }

            assertThat(aar.androidManifestContentsAsString).contains("uses-sdk android:minSdkVersion=\"22\"")
            assertThat(aar.androidManifestContentsAsString).contains("package=\"com.example.kmpfirstlib\"")

            assertThat(
                aar.getEntry("META-INF/com/android/build/gradle/aar-metadata.properties").readText()
            ).contains("minAndroidGradlePluginVersion=7.2.0")
         }
    }

    @Test
    fun testKmpLibraryTestApkContents() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                android.packagingOptions.resources.excludes.addAll(listOf(
                    "**/*.java",
                    "junit/**",
                    "LICENSE-junit.txt"
                ))
            """.trimIndent()
        )

        project.executor().run(":kmpFirstLib:assembleInstrumentedTest")

        val testApk = project.getSubproject("kmpFirstLib").getOutputFile(
            "apk", "androidTest", "main", "kmpFirstLib-androidTest.apk"
        )

        assertThat(testApk.exists()).isTrue()

        Apk(testApk).use { apk ->
            // Test apk should be signed by debug signing config
            assertThatApk(apk).containsApkSigningBlock()

            assertThatApk(apk).hasApplicationId("com.example.kmpfirstlib.test")

            // classes from commonMain are packaged
            assertThatApk(apk).hasClass("Lcom/example/kmpfirstlib/KmpCommonFirstLibClass;")
            assertThatApk(apk).hasClass("Lcom/example/kmpsecondlib/KmpCommonSecondLibClass;")

            // classes from androidMain are packaged
            assertThatApk(apk).hasClass("Lcom/example/kmpfirstlib/KmpAndroidFirstLibClass;")
            assertThatApk(apk).hasClass("Lcom/example/kmpsecondlib/KmpAndroidSecondLibClass;")

            // classes from library dependencies are packaged
            assertThatApk(apk).hasClass("Lcom/example/androidlib/AndroidLib;")
            assertThatApk(apk).hasClass("Landroidx/test/core/app/ActivityScenario;")

            // resources from dependencies are packaged
            assertThatApk(apk).contains("resources.arsc")

            val manifestContents = ApkSubject.getManifestContent(apk.file).joinToString("\n")
            assertThat(manifestContents).contains(
                "com.example.kmpfirstlib.KmpAndroidActivity"
            )

            assertThat(apk.getEntry("kmp_resource.txt").readText()).isEqualTo(
                "kmp resource\n"
            )

            assertThat(apk.getEntry("android_lib_resource.txt").readText()).isEqualTo(
                "android lib resource\n"
            )

            // all contents
            assertThat(
                apk.entries.map { it.pathString }.filterNot {
                    it.startsWith("/res") || it.endsWith(".kotlin_builtins") ||
                            it.startsWith("/META-INF")
                }
            ).containsExactlyElementsIn(
                listOf(
                    "/AndroidManifest.xml",
                    "/kmp_resource.txt",
                    "/android_lib_resource.txt",
                    "/classes.dex"
                )
            )
        }
    }
}
