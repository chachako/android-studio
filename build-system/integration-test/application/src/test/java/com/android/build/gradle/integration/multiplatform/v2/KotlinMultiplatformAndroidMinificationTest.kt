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
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.apk.Aar
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.io.path.pathString

class KotlinMultiplatformAndroidMinificationTest {

    @Suppress("DEPRECATION") // kmp doesn't support configuration caching for now (b/276472789)
    @get:Rule
    val project = GradleTestProjectBuilder()
        .fromTestProject("kotlinMultiplatform")
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
        .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin.androidLibrary {
                    optimization {
                        minify = true
                        consumerKeepRules.files.add(
                            File(project.projectDir, "consumer-proguard-rules.pro")
                        )
                        keepRules.file("proguard-rules.pro")
                        consumerKeepRules.publish = true
                    }
                }
            """.trimIndent()
        )

        TestFileUtils.appendToFile(
            project.getSubproject("app").ktsBuildFile,
            """
                android {
                    buildTypes {
                        getByName("debug") {
                            isMinifyEnabled = true
                            isShrinkResources = true
                            proguardFiles(
                                getDefaultProguardFile("proguard-android-optimize.txt"),
                                "proguard-rules.pro"
                            )
                        }
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun testKmpLibClassesAreMinified() {
        project.executor().run(":kmpFirstLib:assemble")

        Aar(
            project.getSubproject("kmpFirstLib").getOutputFile(
                "aar",
                "kmpFirstLib.aar"
            )
        ).use { aar ->
            aar.getEntryAsZip("classes.jar").use { classesJar ->
                Truth.assertThat(classesJar.entries.map { it.pathString })
                    .containsExactly(
                        "/kmp_resource.txt",
                        "/com/example/kmpfirstlib/KmpAndroidActivity.class"
                    )
            }
        }
    }

    @Test
    fun testAppClassesAreMinified() {
        project.executor().run(":app:assembleDebug")

        project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            // only the main activity is left
            Truth.assertThat(apk.mainDexFile.get().classes.keys).containsExactly(
                "Lcom/example/kmpfirstlib/KmpAndroidActivity;"
            )
        }
    }

    @Test
    fun testProguardRulesInKmpLib() {
        FileUtils.writeToFile(
            project.getSubproject("kmpFirstLib").file("proguard-rules.pro"),
            """
                -keep public class com.example.kmpfirstlib.KmpAndroidFirstLibClass {
                    java.lang.String callCommonLibClass();
                    java.lang.String callAndroidLibClass();
                 }
            """.trimIndent()
        )

        project.executor().run(":kmpFirstLib:assemble")

        Aar(
            project.getSubproject("kmpFirstLib").getOutputFile(
                "aar",
                "kmpFirstLib.aar"
            )
        ).use { aar ->
            aar.getEntryAsZip("classes.jar").use { classesJar ->
                Truth.assertThat(classesJar.entries.map { it.pathString })
                    // code is optimized by default, and so the invocations to classes from common
                    // and androidLib are replaced by a literal string and removed.
                    .containsExactly(
                        "/kmp_resource.txt",
                        "/com/example/kmpfirstlib/KmpAndroidActivity.class",
                        "/com/example/kmpfirstlib/KmpAndroidFirstLibClass.class",
                    )
            }
        }
    }

    @Test
    fun testProguardRulesInApp() {
        FileUtils.writeToFile(
            project.getSubproject("app").file("proguard-rules.pro"),
            """
                -keep public class com.example.app.AndroidApp { *; }
            """.trimIndent()
        )

        project.executor().run(":app:assembleDebug")

        project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            Truth.assertThat(apk.mainDexFile.get().classes.keys).containsExactly(
                "Lcom/example/androidlib/AndroidLib;",
                "Lcom/example/app/AndroidApp;",
                "Lcom/example/kmpfirstlib/KmpAndroidActivity;",
                "Lcom/example/kmpfirstlib/KmpAndroidFirstLibClass;",
                "Lcom/example/kmpfirstlib/KmpCommonFirstLibClass;",
                "Lcom/example/kmpsecondlib/KmpAndroidSecondLibClass;",
                "Lcom/example/kmpsecondlib/KmpCommonSecondLibClass;",
                "Lcom/example/kmpjvmonly/KmpCommonJvmOnlyLibClass;",
                "Lcom/example/kmpjvmonly/KmpJvmOnlyLibClass;",
                "Lkotlin/jvm/internal/Intrinsics;"
            )
        }
    }

    @Test
    fun testConsumerProguardRulesFromKmpLib() {
        FileUtils.writeToFile(
            project.getSubproject("kmpFirstLib").file("consumer-proguard-rules.pro"),
            """
                -keep public class com.example.kmpfirstlib.KmpAndroidFirstLibClass {
                    java.lang.String callCommonLibClass();
                    java.lang.String callKmpSecondLibClass();
                 }
            """.trimIndent()
        )

        project.executor().run(":app:assembleDebug")

        project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            Truth.assertThat(apk.mainDexFile.get().classes.keys).containsExactly(
                "Lcom/example/kmpfirstlib/KmpAndroidActivity;",
                "Lcom/example/kmpfirstlib/KmpAndroidFirstLibClass;",
                "Lcom/example/kmpfirstlib/KmpCommonFirstLibClass;",
                "Lcom/example/kmpsecondlib/KmpAndroidSecondLibClass;",
                "Lcom/example/kmpsecondlib/KmpCommonSecondLibClass;"
            )
        }
    }

    @Test
    fun `test disabling consumer proguard rules from kmp lib`() {
        TestFileUtils.searchAndReplace(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            "consumerKeepRules.publish = true",
            "consumerKeepRules.publish = false"
        )
        FileUtils.writeToFile(
            project.getSubproject("kmpFirstLib").file("consumer-proguard-rules.pro"),
            """
                -keep public class com.example.kmpfirstlib.KmpAndroidFirstLibClass {
                    java.lang.String callCommonLibClass();
                    java.lang.String callKmpSecondLibClass();
                 }
            """.trimIndent()
        )

        project.executor().run(":app:assembleDebug")

        project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            Truth.assertThat(apk.mainDexFile.get().classes.keys).containsExactly(
                "Lcom/example/kmpfirstlib/KmpAndroidActivity;"
            )
        }
    }
}
