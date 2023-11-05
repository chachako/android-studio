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

package com.android.build.gradle.integration.connected.application

import com.android.SdkConstants
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.connected.utils.getEmulator
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.ide.common.build.GenericBuiltArtifactsLoader
import com.android.utils.FileUtils
import com.android.utils.NullLogger
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class InstallProfilesPerDeviceApiConnectedTest {
    companion object {
        @JvmField @ClassRule
        val emulator = getEmulator()
    }

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder().fromTestProject("basic").create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(project.buildFile,
            """
                android {
                    defaultConfig {
                        minSdkVersion = 28
                        targetSdkVersion = 28
                    }

                    signingConfigs {
                        myConfig {
                            storeFile file("debug.keystore")
                            storePassword "android"
                            keyAlias "androiddebugkey"
                            keyPassword "android"
                        }
                    }

                    buildTypes {
                        release {
                            signingConfig signingConfigs.myConfig
                        }
                    }
                }
            """.trimIndent()
        )

        val mainBaselineProfileFileContent =
            """
                HSPLcom/google/Foo;->mainMethod(II)I
                HSPLcom/google/Foo;->mainMethod-name-with-hyphens(II)I
            """.trimIndent()

        val releaseBaselineProfileFileContent =
            """
                HSPLcom/google/Foo;->releaseMethod(II)I
                HSPLcom/google/Foo;->releaseMethod-name-with-hyphens(II)I
            """.trimIndent()

        FileUtils.createFile(
            project.file("src/main/baselineProfiles/file.txt"),
            mainBaselineProfileFileContent
        )
        FileUtils.createFile(
            project.file("src/release/baselineProfiles/file.txt"),
            releaseBaselineProfileFileContent
        )
    }

    @Test
    fun `install base baseline profile`() {
        project.execute("assembleRelease", "installRelease")

        val dexMetadataProperties = FileUtils.join(
            project.buildDir,
            SdkConstants.FD_INTERMEDIATES,
            InternalArtifactType.DEX_METADATA_DIRECTORY.getFolderName(),
            "release",
            "compileReleaseArtProfile",
            SdkConstants.FN_DEX_METADATA_PROP
        )

        Truth.assertThat(dexMetadataProperties.readText()).isEqualTo(
            """
                31=0/.dm
                32=0/.dm
                33=0/.dm
                34=0/.dm
                28=1/.dm
                29=1/.dm
                30=1/.dm
            """.trimIndent()
        )

        ScannerSubject.assertThat(project.buildResult.stdout)
            .contains("Installing APK 'basic-release.apk, basic-release.dm'")

        // Validate that renamed baseline profile file is present
        val renamedBaselineProfile = FileUtils.join(
            project.buildDir,
            SdkConstants.FD_OUTPUTS,
            SdkConstants.EXT_ANDROID_PACKAGE,
            "release",
            "baselineProfiles",
            "0",
            "basic-release.dm"
        )
        Truth.assertThat(renamedBaselineProfile.exists()).isTrue()

        // Validate that baseline profile is in app metadata file
        val appMetadataJson = FileUtils.join(
            project.buildDir,
            SdkConstants.FD_OUTPUTS,
            SdkConstants.EXT_ANDROID_PACKAGE,
            "release",
            BuiltArtifactsImpl.METADATA_FILE_NAME
        )
        Truth.assertThat(appMetadataJson.readText()).contains("baselineProfiles")
        Truth.assertThat(appMetadataJson.readText()).contains("basic-release.dm")

        val builtArtifacts = GenericBuiltArtifactsLoader.loadFromFile(appMetadataJson, NullLogger())
        val baselineProfileFile =
            builtArtifacts?.baselineProfiles?.lastOrNull()?.baselineProfiles?.firstOrNull()
        Truth.assertThat(baselineProfileFile).isEqualTo(renamedBaselineProfile)
    }

    @Test
    fun `install baseline profile with splits`() {
        TestFileUtils.appendToFile(project.buildFile,
            """
                android.splits {
                    abi {
                      enable true
                      reset()
                      include "x86", "x86_64"
                      universalApk false
                    }
                }
            """.trimIndent()
        )

        project.execute("assembleRelease", "installRelease")

        val dexMetadataProperties = FileUtils.join(
            project.buildDir,
            SdkConstants.FD_INTERMEDIATES,
            InternalArtifactType.DEX_METADATA_DIRECTORY.getFolderName(),
            "release",
            "compileReleaseArtProfile",
            SdkConstants.FN_DEX_METADATA_PROP
        )

        Truth.assertThat(dexMetadataProperties.readText()).isEqualTo(
            """
                31=0/.dm
                32=0/.dm
                33=0/.dm
                34=0/.dm
                28=1/.dm
                29=1/.dm
                30=1/.dm
            """.trimIndent()
        )

        ScannerSubject.assertThat(project.buildResult.stdout)
            .contains("Installing APK 'basic-x86_64-release.apk, basic-x86_64-release.dm'")

        // Validate that renamed baseline profile file is present
        val renamedBaselineProfile = FileUtils.join(
            project.buildDir,
            SdkConstants.FD_OUTPUTS,
            SdkConstants.EXT_ANDROID_PACKAGE,
            "release",
            "baselineProfiles",
            "0",
            "basic-x86_64-release.dm"
        )
        Truth.assertThat(renamedBaselineProfile.exists()).isTrue()

        // Validate that baseline profile is in app metadata file
        val appMetadataJson = FileUtils.join(
            project.buildDir,
            SdkConstants.FD_OUTPUTS,
            SdkConstants.EXT_ANDROID_PACKAGE,
            "release",
            BuiltArtifactsImpl.METADATA_FILE_NAME
        )
        Truth.assertThat(appMetadataJson.readText()).contains("baselineProfiles")
        Truth.assertThat(appMetadataJson.readText()).contains("basic-x86-release.dm")
        Truth.assertThat(appMetadataJson.readText()).contains("basic-x86_64-release.dm")

        val builtArtifacts = GenericBuiltArtifactsLoader.loadFromFile(appMetadataJson, NullLogger())
        val baselineProfileFile =
            builtArtifacts?.baselineProfiles?.lastOrNull()?.baselineProfiles?.firstOrNull()
        Truth.assertThat(baselineProfileFile).isEqualTo(renamedBaselineProfile)
    }
}
