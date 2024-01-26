/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.integration.ndk

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Test behavior of Packaging.jniLibs.testOnly */
class JniLibsTestOnlyTest {

    private val app =
        MinimalSubProject.app("com.example.app")
            .appendToBuild(
                """
                    android {
                        packaging {
                            jniLibs {
                                testOnly += "**/appDslTestOnly.so"
                            }
                        }
                    }
                    androidComponents {
                        onVariants(selector().all()) {
                            packaging.jniLibs.testOnly.add("**/appVariantTestOnly.so")
                        }
                    }
                    """.trimIndent()
            )

    private val lib =
        MinimalSubProject.lib("com.example.lib")
            .appendToBuild(
                """
                    android {
                        packaging {
                            jniLibs {
                                testOnly += "**/libDslTestOnly.so"
                            }
                        }
                    }
                    androidComponents {
                        onVariants(selector().all()) {
                            packaging.jniLibs.testOnly.add("**/libVariantTestOnly.so")
                        }
                    }
                    """.trimIndent()
            )

    private val appTest =
        MinimalSubProject.test("com.example.apptest")
            .appendToBuild(
                """
                    android {
                        targetProjectPath ':app'
                    }
                """.trimIndent()
            )

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .subproject(":lib", lib)
                    .subproject(":appTest", appTest)
                    .dependency(app, lib)
                    .dependency(appTest, app)
                    .build()
            )
            .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
            .create()

    @Before
    fun before() {
        // add native libs to app
        val appSubproject = project.getSubproject("app")
        createAbiFile(appSubproject, "main", "appMain.so")
        createAbiFile(appSubproject, "main", "appDslTestOnly.so")
        createAbiFile(appSubproject, "main", "appVariantTestOnly.so")

        // add native libs to lib
        val libSubproject = project.getSubproject("lib")
        createAbiFile(libSubproject, "main", "libMain.so")
        createAbiFile(libSubproject, "main", "libDslTestOnly.so")
        createAbiFile(libSubproject, "main", "libVariantTestOnly.so")
    }

    @Test
    fun testTestOnlyForApp() {
        val appSubproject = project.getSubproject("app")
        val appTestSubproject = project.getSubproject("appTest")

        project.executor()
            .run(":app:assembleDebug", ":app:assembleDebugAndroidTest", ":appTest:assembleDebug")

        val apk = appSubproject.getApk(GradleTestProject.ApkType.DEBUG)
        assertThatApk(apk).contains("lib/x86/appMain.so")
        assertThatApk(apk).contains("lib/x86/libMain.so")
        assertThatApk(apk).doesNotContain("lib/x86/appDslTestOnly.so")
        assertThatApk(apk).doesNotContain("lib/x86/appVariantTestOnly.so")
        assertThatApk(apk).doesNotContain("lib/x86/libDslTestOnly.so")
        assertThatApk(apk).doesNotContain("lib/x86/libVariantTestOnly.so")

        val androidTestApk =
            appSubproject.getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG)
        assertThatApk(androidTestApk).contains("lib/x86/appDslTestOnly.so")
        assertThatApk(androidTestApk).contains("lib/x86/appVariantTestOnly.so")
        assertThatApk(androidTestApk).doesNotContain("lib/x86/appMain.so")
        assertThatApk(androidTestApk).doesNotContain("lib/x86/libMain.so")
        assertThatApk(androidTestApk).doesNotContain("lib/x86/libDslTestOnly.so")
        assertThatApk(androidTestApk).doesNotContain("lib/x86/libVariantTestOnly.so")

        val appTestApk = appTestSubproject.getApk(GradleTestProject.ApkType.DEBUG)
        assertThatApk(appTestApk).contains("lib/x86/appDslTestOnly.so")
        assertThatApk(appTestApk).contains("lib/x86/appVariantTestOnly.so")
        assertThatApk(appTestApk).doesNotContain("lib/x86/appMain.so")
        assertThatApk(appTestApk).doesNotContain("lib/x86/libMain.so")
        assertThatApk(appTestApk).doesNotContain("lib/x86/libDslTestOnly.so")
        assertThatApk(appTestApk).doesNotContain("lib/x86/libVariantTestOnly.so")
    }

    @Test
    fun testTestOnlyForLib() {
        val libSubproject = project.getSubproject("lib")

        project.executor().run(":lib:assembleDebug", ":lib:assembleDebugAndroidTest")

        libSubproject.getAar("debug") {
            assertThatAar(it).contains("jni/x86/libMain.so")
            assertThatAar(it).doesNotContain("jni/x86/libDslTestOnly.so")
            assertThatAar(it).doesNotContain("jni/x86/libVariantTestOnly.so")
        }

        val androidTestApk =
            libSubproject.getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG)
        assertThatApk(androidTestApk).contains("lib/x86/libMain.so")
        assertThatApk(androidTestApk).contains("lib/x86/libDslTestOnly.so")
        assertThatApk(androidTestApk).contains("lib/x86/libVariantTestOnly.so")
    }

    private fun createAbiFile(
        gradleTestProject: GradleTestProject,
        srcDirName: String,
        libName: String
    ) {
        val abiFolder =
            FileUtils.join(gradleTestProject.projectDir, "src", srcDirName, "jniLibs", "x86")
        FileUtils.mkdirs(abiFolder)
        JniLibsTestOnlyTest::class.java.getResourceAsStream(
            "/nativeLibs/unstripped.so"
        ).use { inputStream ->
            File(abiFolder, libName).outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }
}
