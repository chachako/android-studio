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

package com.android.build.gradle.integration.multiplatform.v2

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.DESUGAR_DEPENDENCY_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.apk.Apk
import com.android.testutils.truth.DexClassSubject
import com.android.testutils.truth.DexSubject
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class KotlinMultiplatformAndroidDexingTest {
    @Suppress("DEPRECATION") // kmp doesn't support configuration caching for now (b/276472789)
    @get:Rule
    val project = GradleTestProjectBuilder()
        .fromTestProject("kotlinMultiplatform")
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
        .create()

    @Before
    fun setUp() {
        TestFileUtils.searchAndReplace(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                withAndroidTestOnDevice(compilationName = "instrumentedTest")
            """.trimIndent(),
            """
                withAndroidTestOnDevice(compilationName = "instrumentedTest") {
                    multidex.enable = true
                    multidex.mainDexKeepRules.files.add (
                        File(project.projectDir, "dex-rules.pro")
                    )
                }
            """.trimIndent()
        )

        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin {
                    androidLibrary {
                        compilations.all {
                            compilerOptions.configure {
                                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
                            }
                        }

                        isCoreLibraryDesugaringEnabled = true
                    }
                }
                dependencies {
                    add("coreLibraryDesugaring", "com.android.tools:desugar_jdk_libs:$DESUGAR_DEPENDENCY_VERSION")
                }
            """.trimIndent())

        TestFileUtils.addMethod(
            FileUtils.join(
                project.getSubproject("kmpFirstLib").projectDir,
                "src", "androidMain", "kotlin", "com", "example", "kmpfirstlib", "KmpAndroidActivity.kt"
            ),
            """
                fun getText(): String {
                    val collection = java.util.Arrays.asList("first", "second", "third")
                    val streamOfCollection = collection.stream()
                    return streamOfCollection.findFirst().get()
                }
            """.trimIndent())
    }

    @Test
    fun testDesugaringForInstrumentedTestApk() {
        project.executor().run(":kmpFirstLib:assembleInstrumentedTest")
        val testApk = project.getSubproject("kmpFirstLib").getOutputFile(
            "apk", "androidTest", "main", "kmpFirstLib-androidTest.apk"
        )

        Truth.assertThat(testApk.exists()).isTrue()

        Apk(testApk).use { apk ->
            DexClassSubject.assertThat(apk.getClass("Lcom/example/kmpfirstlib/KmpAndroidActivity;"))
                .hasMethodThatInvokes("getText", "Lj$/util/stream/Stream;->findFirst()Lj$/util/Optional;")
        }
    }

    @Test
    fun testLegacyMultiDexWithKeepRules() {
        TestFileUtils.appendToFile(
            project.getSubproject("androidLib").ktsBuildFile,
            """
                android.defaultConfig.minSdk = 20
            """.trimIndent()
        )

        TestFileUtils.searchAndReplace(
            project.getSubproject("kmpSecondLib").ktsBuildFile,
            "minSdk = 22",
            "minSdk = 20"
        )
        TestFileUtils.searchAndReplace(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            "minSdk = 22",
            """
                minSdk = 20
            """.trimIndent()
        )

        FileUtils.writeToFile(
            FileUtils.join(project.getSubproject("kmpFirstLib").projectDir, "dex-rules.pro"),
            """
                -keep class com.example.kmpfirstlib.KmpAndroidActivity { *; }
            """.trimIndent()
        )

        project.executor().run(":kmpFirstLib:assembleInstrumentedTest")
        val testApk = project.getSubproject("kmpFirstLib").getOutputFile(
            "apk", "androidTest", "main", "kmpFirstLib-androidTest.apk"
        )

        Truth.assertThat(testApk.exists()).isTrue()

        Apk(testApk).use { apk ->
            DexClassSubject.assertThat(apk.getClass("Lcom/example/kmpfirstlib/KmpAndroidActivity;"))
                .hasMethodThatInvokes("getText", "Lj$/util/stream/Stream;->findFirst()Lj$/util/Optional;")

            DexSubject.assertThat(
                apk.allDexes.find { it.classes.containsKey( "Lcom/example/kmpfirstlib/KmpAndroidFirstLibClass;") }
            ).doesNotContainClasses("Lcom/example/kmpfirstlib/KmpAndroidActivity;")

            DexSubject.assertThat(
                apk.mainDexFile.get()
            ).containsClass("Lcom/example/kmpfirstlib/KmpAndroidActivity;")
        }
    }
}
