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
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatNativeLib
import com.android.build.gradle.integration.common.utils.ZipHelper
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Test behavior of PackagingOptions.jniLibs.keepDebugSymbols */
class KeepDebugSymbolsTest {

    private val app =
        MinimalSubProject.app("com.example.app")
            .appendToBuild(
                """
                    android {
                        packagingOptions.jniLibs.keepDebugSymbols.add('**/appDslDoNotStrip1.so')
                        packagingOptions {
                            jniLibs {
                                keepDebugSymbols += '**/appDslDoNotStrip2.so'
                            }
                        }
                    }
                    androidComponents {
                        onVariants(selector().withName('debug')) {
                            packaging.jniLibs.keepDebugSymbols.add('**/appDebugDoNotStrip.so')
                            androidTest?.packaging
                                ?.jniLibs
                                ?.keepDebugSymbols
                                ?.add('**/androidTestDoNotStrip.so')
                        }
                        onVariants(selector().withName('release')) {
                            packaging.jniLibs.keepDebugSymbols.add('**/appReleaseDoNotStrip.so')
                        }
                    }
                    """.trimIndent()
            )

    private val lib =
        MinimalSubProject.lib("com.example.lib")
            .appendToBuild(
                """
                    android {
                        packagingOptions.jniLibs.keepDebugSymbols.add('**/libDslDoNotStrip.so')
                    }
                    androidComponents {
                        onVariants(selector().withName('debug')) {
                            packaging.jniLibs.keepDebugSymbols.add('**/libDebugDoNotStrip.so')
                            androidTest?.packaging
                                ?.jniLibs
                                ?.keepDebugSymbols
                                ?.add('**/androidTestDoNotStrip.so')
                        }
                        onVariants(selector().withName('release')) {
                            packaging.jniLibs.keepDebugSymbols.add('**/libReleaseDoNotStrip.so')
                        }
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
                    .dependency(app, lib)
                    .build()
            )
            .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
            .create()

    @Before
    fun before() {
        // add native libs to app
        val appSubproject = project.getSubproject("app")
        createAbiFile(appSubproject, "main", "appStrip.so")
        createAbiFile(appSubproject, "main", "appDslDoNotStrip1.so")
        createAbiFile(appSubproject, "main", "appDslDoNotStrip2.so")
        createAbiFile(appSubproject, "main", "appDebugDoNotStrip.so")
        createAbiFile(appSubproject, "main", "appReleaseDoNotStrip.so")
        createAbiFile(appSubproject, "androidTest", "androidTestStrip.so")
        createAbiFile(appSubproject, "androidTest", "androidTestDoNotStrip.so")

        // add native libs to lib
        val libSubproject = project.getSubproject("lib")
        createAbiFile(libSubproject, "main", "libStrip.so")
        createAbiFile(libSubproject, "main", "libDslDoNotStrip.so")
        createAbiFile(libSubproject, "main", "libDebugDoNotStrip.so")
        createAbiFile(libSubproject, "main", "libReleaseDoNotStrip.so")
        createAbiFile(libSubproject, "androidTest", "androidTestStrip.so")
        createAbiFile(libSubproject, "androidTest", "androidTestDoNotStrip.so")
    }

    @Test
    fun testKeepDebugSymbolsForApp() {
        val appSubproject = project.getSubproject("app")

        project.executor()
            .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
            .run(":app:assembleDebug", ":app:assembleRelease", ":app:assembleDebugAndroidTest")

        val debugApk = appSubproject.getApk(GradleTestProject.ApkType.DEBUG)
        assertThatApk(debugApk).contains("lib/x86/appStrip.so")
        assertThatApk(debugApk).contains("lib/x86/appDslDoNotStrip1.so")
        assertThatApk(debugApk).contains("lib/x86/appDslDoNotStrip2.so")
        assertThatApk(debugApk).contains("lib/x86/appDebugDoNotStrip.so")
        assertThatApk(debugApk).contains("lib/x86/appReleaseDoNotStrip.so")
        assertThatApk(debugApk).contains("lib/x86/libStrip.so")
        assertThatApk(debugApk).contains("lib/x86/libDslDoNotStrip.so")
        assertThatApk(debugApk).contains("lib/x86/libDebugDoNotStrip.so")
        assertThatApk(debugApk).contains("lib/x86/libReleaseDoNotStrip.so")
        assertThatApk(debugApk).doesNotContain("lib/x86/androidTestStrip.so")
        assertThatApk(debugApk).doesNotContain("lib/x86/androidTestDoNotStrip.so")
        assertThatNativeLib(
            ZipHelper.extractFile(debugApk, "lib/x86/appStrip.so")
        ).isStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(debugApk, "lib/x86/appDslDoNotStrip1.so")
        ).isNotStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(debugApk, "lib/x86/appDslDoNotStrip2.so")
        ).isNotStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(debugApk, "lib/x86/appDebugDoNotStrip.so")
        ).isNotStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(debugApk, "lib/x86/appReleaseDoNotStrip.so")
        ).isStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(debugApk, "lib/x86/libStrip.so")
        ).isStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(debugApk, "lib/x86/libDslDoNotStrip.so")
        ).isStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(debugApk, "lib/x86/libDebugDoNotStrip.so")
        ).isStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(debugApk, "lib/x86/libReleaseDoNotStrip.so")
        ).isStripped()

        val releaseApk = appSubproject.getApk(GradleTestProject.ApkType.RELEASE)
        assertThatApk(releaseApk).contains("lib/x86/appStrip.so")
        assertThatApk(releaseApk).contains("lib/x86/appDslDoNotStrip1.so")
        assertThatApk(releaseApk).contains("lib/x86/appDslDoNotStrip2.so")
        assertThatApk(releaseApk).contains("lib/x86/appDebugDoNotStrip.so")
        assertThatApk(releaseApk).contains("lib/x86/appReleaseDoNotStrip.so")
        assertThatApk(releaseApk).contains("lib/x86/libStrip.so")
        assertThatApk(releaseApk).contains("lib/x86/libDslDoNotStrip.so")
        assertThatApk(releaseApk).contains("lib/x86/libDebugDoNotStrip.so")
        assertThatApk(releaseApk).contains("lib/x86/libReleaseDoNotStrip.so")
        assertThatApk(releaseApk).doesNotContain("lib/x86/androidTestStrip.so")
        assertThatApk(releaseApk).doesNotContain("lib/x86/androidTestDoNotStrip.so")
        assertThatNativeLib(
            ZipHelper.extractFile(releaseApk, "lib/x86/appStrip.so")
        ).isStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(releaseApk, "lib/x86/appDslDoNotStrip1.so")
        ).isNotStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(releaseApk, "lib/x86/appDslDoNotStrip2.so")
        ).isNotStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(releaseApk, "lib/x86/appDebugDoNotStrip.so")
        ).isStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(releaseApk, "lib/x86/appReleaseDoNotStrip.so")
        ).isNotStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(releaseApk, "lib/x86/libStrip.so")
        ).isStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(releaseApk, "lib/x86/libDslDoNotStrip.so")
        ).isStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(releaseApk, "lib/x86/libDebugDoNotStrip.so")
        ).isStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(releaseApk, "lib/x86/libReleaseDoNotStrip.so")
        ).isStripped()

        val appAndroidTestDebugApk =
            appSubproject.getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG)
        assertThatApk(appAndroidTestDebugApk).contains("lib/x86/androidTestStrip.so")
        assertThatApk(appAndroidTestDebugApk).contains("lib/x86/androidTestDoNotStrip.so")
        assertThatApk(appAndroidTestDebugApk).doesNotContain("lib/x86/appStrip.so")
        assertThatApk(appAndroidTestDebugApk).doesNotContain("lib/x86/appDslDoNotStrip1.so")
        assertThatApk(appAndroidTestDebugApk).doesNotContain("lib/x86/appDslDoNotStrip2.so")
        assertThatApk(appAndroidTestDebugApk).doesNotContain("lib/x86/appDebugDoNotStrip.so")
        assertThatApk(appAndroidTestDebugApk).doesNotContain("lib/x86/appReleaseDoNotStrip.so")
        assertThatApk(appAndroidTestDebugApk).doesNotContain("lib/x86/libStrip.so")
        assertThatApk(appAndroidTestDebugApk).doesNotContain("lib/x86/libDslDoNotStrip.so")
        assertThatApk(appAndroidTestDebugApk).doesNotContain("lib/x86/libDebugDoNotStrip.so")
        assertThatApk(appAndroidTestDebugApk).doesNotContain("lib/x86/libReleaseDoNotStrip.so")
        assertThatNativeLib(
            ZipHelper.extractFile(appAndroidTestDebugApk, "lib/x86/androidTestStrip.so")
        ).isStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(appAndroidTestDebugApk, "lib/x86/androidTestDoNotStrip.so")
        ).isNotStripped()
    }

    @Test
    fun testKeepDebugSymbolsForLib() {
        val libSubproject = project.getSubproject("lib")

        project.executor()
            .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
            .run(":lib:assembleDebug", ":lib:assembleRelease", ":lib:assembleDebugAndroidTest")

        libSubproject.getAar("debug") {
            assertThatAar(it).contains("jni/x86/libStrip.so")
            assertThatAar(it).contains("jni/x86/libDslDoNotStrip.so")
            assertThatAar(it).contains("jni/x86/libDebugDoNotStrip.so")
            assertThatAar(it).contains("jni/x86/libReleaseDoNotStrip.so")
            assertThatAar(it).doesNotContain("jni/x86/appStrip.so")
            assertThatAar(it).doesNotContain("jni/x86/appDslDoNotStrip1.so")
            assertThatAar(it).doesNotContain("jni/x86/appDslDoNotStrip2.so")
            assertThatAar(it).doesNotContain("jni/x86/appDebugDoNotStrip.so")
            assertThatAar(it).doesNotContain("jni/x86/appReleaseDoNotStrip.so")
            assertThatAar(it).doesNotContain("jni/x86/androidTestStrip.so")
            assertThatAar(it).doesNotContain("jni/x86/androidTestDoNotStrip.so")
            assertThatNativeLib(
                ZipHelper.extractFile(it, "jni/x86/libStrip.so")
            ).isStripped()
            assertThatNativeLib(
                ZipHelper.extractFile(it, "jni/x86/libDslDoNotStrip.so")
            ).isNotStripped()
            assertThatNativeLib(
                ZipHelper.extractFile(it, "jni/x86/libDebugDoNotStrip.so")
            ).isNotStripped()
            assertThatNativeLib(
                ZipHelper.extractFile(it, "jni/x86/libReleaseDoNotStrip.so")
            ).isStripped()
        }

        libSubproject.getAar("release") {
            assertThatAar(it).contains("jni/x86/libStrip.so")
            assertThatAar(it).contains("jni/x86/libDslDoNotStrip.so")
            assertThatAar(it).contains("jni/x86/libDebugDoNotStrip.so")
            assertThatAar(it).contains("jni/x86/libReleaseDoNotStrip.so")
            assertThatAar(it).doesNotContain("jni/x86/appStrip.so")
            assertThatAar(it).doesNotContain("jni/x86/appDslDoNotStrip1.so")
            assertThatAar(it).doesNotContain("jni/x86/appDslDoNotStrip2.so")
            assertThatAar(it).doesNotContain("jni/x86/appDebugDoNotStrip.so")
            assertThatAar(it).doesNotContain("jni/x86/appReleaseDoNotStrip.so")
            assertThatAar(it).doesNotContain("jni/x86/androidTestStrip.so")
            assertThatAar(it).doesNotContain("jni/x86/androidTestDoNotStrip.so")
            assertThatNativeLib(
                ZipHelper.extractFile(it, "jni/x86/libStrip.so")
            ).isStripped()
            assertThatNativeLib(
                ZipHelper.extractFile(it, "jni/x86/libDslDoNotStrip.so")
            ).isNotStripped()
            assertThatNativeLib(
                ZipHelper.extractFile(it, "jni/x86/libDebugDoNotStrip.so")
            ).isStripped()
            assertThatNativeLib(
                ZipHelper.extractFile(it, "jni/x86/libReleaseDoNotStrip.so")
            ).isNotStripped()
        }

        val libAndroidTestDebugApk =
            libSubproject.getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG)
        assertThatApk(libAndroidTestDebugApk).contains("lib/x86/libStrip.so")
        assertThatApk(libAndroidTestDebugApk).contains("lib/x86/libDslDoNotStrip.so")
        assertThatApk(libAndroidTestDebugApk).contains("lib/x86/libDebugDoNotStrip.so")
        assertThatApk(libAndroidTestDebugApk).contains("lib/x86/libReleaseDoNotStrip.so")
        assertThatApk(libAndroidTestDebugApk).contains("lib/x86/androidTestStrip.so")
        assertThatApk(libAndroidTestDebugApk).contains("lib/x86/androidTestDoNotStrip.so")
        assertThatApk(libAndroidTestDebugApk).doesNotContain("lib/x86/appStrip.so")
        assertThatApk(libAndroidTestDebugApk).doesNotContain("lib/x86/appDslDoNotStrip1.so")
        assertThatApk(libAndroidTestDebugApk).doesNotContain("lib/x86/appDslDoNotStrip2.so")
        assertThatApk(libAndroidTestDebugApk).doesNotContain("lib/x86/appDebugDoNotStrip.so")
        assertThatApk(libAndroidTestDebugApk).doesNotContain("lib/x86/appReleaseDoNotStrip.so")
        assertThatNativeLib(
            ZipHelper.extractFile(libAndroidTestDebugApk, "lib/x86/libStrip.so")
        ).isStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(libAndroidTestDebugApk, "lib/x86/libDslDoNotStrip.so")
        ).isNotStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(libAndroidTestDebugApk, "lib/x86/libDebugDoNotStrip.so")
        ).isStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(libAndroidTestDebugApk, "lib/x86/libReleaseDoNotStrip.so")
        ).isStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(libAndroidTestDebugApk, "lib/x86/androidTestStrip.so")
        ).isStripped()
        assertThatNativeLib(
            ZipHelper.extractFile(libAndroidTestDebugApk, "lib/x86/androidTestDoNotStrip.so")
        ).isNotStripped()
    }

    private fun createAbiFile(
        gradleTestProject: GradleTestProject,
        srcDirName: String,
        libName: String
    ) {
        val abiFolder =
            FileUtils.join(gradleTestProject.projectDir, "src", srcDirName, "jniLibs", "x86")
        FileUtils.mkdirs(abiFolder)
        KeepDebugSymbolsTest::class.java.getResourceAsStream(
            "/nativeLibs/unstripped.so"
        ).use { inputStream ->
            File(abiFolder, libName).outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }
}
