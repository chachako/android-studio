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

package com.android.build.gradle.integration.baselineprofiles

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.testutils.apk.Zip
import com.android.tools.profgen.ArtProfile
import com.android.tools.profgen.HumanReadableProfile
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile
import java.util.zip.ZipEntry

class ArtProfileBaselineTest {

    companion object {
        const val apkEntryName = "${SdkConstants.FN_BINART_ART_PROFILE_FOLDER_IN_APK}/${SdkConstants.FN_BINARY_ART_PROFILE}"

        fun checkAndroidArtifact(
                tempFolder: TemporaryFolder,
                target: Zip,
                entryName: String,
                expected: (ByteArray) -> Unit) {
            target.getEntry(entryName)?.let {
                val tempFile = tempFolder.newFile()
                Files.newInputStream(it).use { inputStream ->
                    Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                expected(tempFile.readBytes())
            } ?: Assert.fail("Entry $entryName is null")
        }
    }

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val app =
        HelloWorldApp.forPluginWithNamespace("com.android.application", "com.example.app")

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject(":app", app)
                .build()
        ).create()

    @Test
    fun testMultipleLibraryArtProfileMerging() {
        testMultipleLibrariesAndOptionalApplicationArtProfileMerging(false)
    }

    @Test
    fun testMultipleLibraryWithApplicationArtProfileMerging() {
        testMultipleLibrariesAndOptionalApplicationArtProfileMerging(true)
    }

    private fun testMultipleLibrariesAndOptionalApplicationArtProfileMerging(addOldBaselineProfile: Boolean) {
        val app = project.getSubproject(":app").also {
            it.buildFile.appendText(
                """
                        android {
                            defaultConfig {
                                minSdkVersion = 28
                            }
                        }
                    """.trimIndent()
            )
        }

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
            project.file("app/src/main/baselineProfiles/file.txt"), mainBaselineProfileFileContent
        )
        FileUtils.createFile(
            project.file("app/src/release/baselineProfiles/file.txt"),
            releaseBaselineProfileFileContent
        )

        val oldBaselineProfileFileContent =
            """
                    HSPLcom/google/Foo;->appMethod(II)I
                    HSPLcom/google/Foo;->appMethod-name-with-hyphens(II)I
                """.trimIndent()

        if (addOldBaselineProfile) {
            val appMainFolder = app.mainSrcDir.parentFile
            appMainFolder.mkdir()

            File(appMainFolder, SdkConstants.FN_ART_PROFILE).writeText(
                oldBaselineProfileFileContent
            )
        }

        val result = project.executor()
            .run(
                ":app:assembleRelease",
                ":app:bundleRelease",
            )

        Truth.assertThat(result.failedTasks).isEmpty()

        val mergedFile = FileUtils.join(
            project.getSubproject(":app").buildDir,
            SdkConstants.FD_INTERMEDIATES,
            InternalArtifactType.MERGED_ART_PROFILE.getFolderName(),
            "release",
            "mergeReleaseArtProfile",
            SdkConstants.FN_ART_PROFILE,
        )

        val expectedContent = if (addOldBaselineProfile) {
            "$mainBaselineProfileFileContent\n$releaseBaselineProfileFileContent\n$oldBaselineProfileFileContent"
        } else {
            "$mainBaselineProfileFileContent\n$releaseBaselineProfileFileContent"
        }

        Truth.assertThat(mergedFile.readText()).isEqualTo(expectedContent)
        Truth.assertThat(
            HumanReadableProfile(mergedFile) {
                Assert.fail(it)
            }
        ).isNotNull()

        val binaryProfile = FileUtils.join(
            project.getSubproject(":app").buildDir,
            SdkConstants.FD_INTERMEDIATES,
            InternalArtifactType.BINARY_ART_PROFILE.getFolderName(),
            "release",
            "compileReleaseArtProfile",
            SdkConstants.FN_BINARY_ART_PROFILE,
        )
        Truth.assertThat(
            ArtProfile(ByteArrayInputStream(binaryProfile.readBytes()))
        ).isNotNull()

        val binaryProfileMetadata = FileUtils.join(
            project.getSubproject(":app").buildDir,
            SdkConstants.FD_INTERMEDIATES,
            InternalArtifactType.BINARY_ART_PROFILE_METADATA.getFolderName(),
            "release",
            "compileReleaseArtProfile",
            SdkConstants.FN_BINARY_ART_PROFILE_METADATA,
        )

        Truth.assertThat(binaryProfileMetadata.exists())

        // check packaging.
        project.getSubproject(":app").getApk(GradleTestProject.ApkType.RELEASE).also {
            checkAndroidArtifact(
                tempFolder,
                it,
                apkEntryName
            ) { fileContent ->
                Truth.assertThat(ArtProfile(ByteArrayInputStream(fileContent))).isNotNull()
            }
            JarFile(it.file.toFile()).use { jarFile ->
                val artProfileEntry = jarFile.getEntry(
                    "${SdkConstants.FN_BINART_ART_PROFILE_FOLDER_IN_APK}/${SdkConstants.FN_BINARY_ART_PROFILE}"
                )
                Truth.assertThat(artProfileEntry.method).isEqualTo(ZipEntry.STORED)

                val artProfileMetadataEntry = jarFile.getEntry(
                    "${SdkConstants.FN_BINART_ART_PROFILE_FOLDER_IN_APK}/${SdkConstants.FN_BINARY_ART_PROFILE_METADATA}"
                )
                Truth.assertThat(artProfileMetadataEntry.method).isEqualTo(ZipEntry.STORED)
            }
        }
    }
}
