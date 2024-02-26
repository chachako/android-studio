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

package com.android.build.gradle.integration.nativebuild

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.NDK_WITH_RISCV_ABI
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.AssumeUtil
import com.android.testutils.apk.Zip
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class NdkRiscvBuildTest {

    @Rule
    @JvmField
    val project = builder()
        .fromTestApp(HelloWorldJniApp.builder().withCmake().build())
        .setSideBySideNdkVersion(NDK_WITH_RISCV_ABI)
        .create()

    private fun setupBuildFile(isLibrary: Boolean = false) {
        TestFileUtils.appendToFile(
            project.buildFile,
            """apply plugin: ${if (isLibrary) "\"com.android.library\"" else "\"com.android.application\""}
                android {
                    namespace "com.example.hellojni"
                    compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                    ndkVersion '$NDK_WITH_RISCV_ABI'
                    buildToolsVersion '${GradleTestProject.DEFAULT_BUILD_TOOL_VERSION}'
                    defaultConfig {
                        minSdk 21
                    }
                    externalNativeBuild {
                        cmake {
                            path 'CMakeLists.txt'
                        }
                    }
                    splits {
                        abi {
                            enable true
                            universalApk true
                            reset()
                            include 'x86', 'riscv64'
                        }
                    }
                }
            """.trimMargin())
    }

    @Test
    fun checkMultiApkBuild() {
        AssumeUtil.assumeIsLinux()
        setupBuildFile()
        project.execute("clean", "assembleDebug")
        TruthHelper.assertThat(project.getApkByFileName(GradleTestProject.ApkType.DEBUG, "project-riscv64-debug.apk"))
            .contains("lib/riscv64/libhello-jni.so")
        TruthHelper.assertThat(project.getApkByFileName(GradleTestProject.ApkType.DEBUG, "project-universal-debug.apk"))
            .contains("lib/riscv64/libhello-jni.so")
    }

    @Test
    fun checkLibraryBuild() {
        AssumeUtil.assumeIsLinux()
        setupBuildFile(isLibrary = true)
        project.execute("clean", "assembleDebug")
        project.withAar("debug") {
            Zip(file).use { aar ->
                Truth.assertThat(aar.entries.map { it.toString() })
                    .contains("/jni/riscv64/libhello-jni.so")
            }
        }
     }

    @Test
    fun checkBundleBuild() {
        AssumeUtil.assumeIsLinux()
        setupBuildFile()
        project.execute("clean", "bundleDebug")
        val bundleFile = project.getBundle(GradleTestProject.ApkType.DEBUG).file.toFile()
        TruthHelper.assertThat(bundleFile.exists()).isTrue()
        Zip(bundleFile).use { zip ->
            Truth.assertThat(zip.entries.map { it.toString() })
                    .contains("/base/lib/riscv64/libhello-jni.so")
        }
    }

}
