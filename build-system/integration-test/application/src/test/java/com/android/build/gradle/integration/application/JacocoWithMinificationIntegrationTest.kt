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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.DexClassSubject
import com.android.testutils.truth.DexSubject
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class JacocoWithMinificationIntegrationTest {

    @get:Rule
    val project = builder()
        .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Before
    fun setUpBuildFile() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                android {
                  buildTypes {
                    debug {
                      testCoverage {
                        enableAndroidTestCoverage = true
                      }
                      minifyEnabled = true
                      proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro"
                      )
                    }

                    release {
                      testCoverage {
                        enableAndroidTestCoverage = true
                      }
                      postprocessing {
                        removeUnusedCode true
                        optimizeCode true
                        obfuscate false
                      }
                      proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro"
                      )
                    }
                  }
                }
            """.trimIndent()
        )


        TestFileUtils.searchAndReplace(
            FileUtils.join(
                project.projectDir,
                "src", "main", "kotlin", "com", "example", "helloworld", "HelloWorld.kt"
            ),
            "override fun onCreate(savedInstanceState: Bundle?) {",
            """
                fun unusedMethod() { }
                override fun onCreate(savedInstanceState: Bundle?) {
            """.trimIndent()
        )
    }

    /**
     * Regression test for b/283015405.
     */
    @Test
    fun checkApk() {
        project.executor().run("assembleDebug")

        project.getApk(GradleTestProject.ApkType.DEBUG).use {
            DexSubject.assertThat(it.mainDexFile.get())
                .containsClass("Lcom/example/helloworld/HelloWorld;")
            DexClassSubject.assertThat(
                it.mainDexFile.get().classes["Lcom/example/helloworld/HelloWorld;"]
            ).hasMethod("\$jacocoInit")
            DexClassSubject.assertThat(
                it.mainDexFile.get().classes["Lcom/example/helloworld/HelloWorld;"]
            ).doesNotHaveMethod("unusedMethod")
        }

        TestFileUtils.appendToFile(
            project.buildFile,
            "android.testBuildType = 'release'"
        )

        project.executor().run("assembleRelease")

        project.getApk(GradleTestProject.ApkType.RELEASE).use {
            DexSubject.assertThat(it.mainDexFile.get())
                .containsClass("Lcom/example/helloworld/HelloWorld;")
            DexClassSubject.assertThat(
                it.mainDexFile.get().classes["Lcom/example/helloworld/HelloWorld;"]
            ).hasField("\$jacocoData")
            DexClassSubject.assertThat(
                it.mainDexFile.get().classes["Lcom/example/helloworld/HelloWorld;"]
            ).doesNotHaveMethod("unusedMethod")
        }
    }
}
