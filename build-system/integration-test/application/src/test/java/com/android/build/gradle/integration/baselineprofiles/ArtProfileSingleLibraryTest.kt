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
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.testutils.apk.Zip
import com.android.tools.profgen.ArtProfile
import com.android.tools.profgen.HumanReadableProfile
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.io.path.inputStream

class ArtProfileSingleLibraryTest {

    companion object {
        const val aarEntryName = SdkConstants.FN_ART_PROFILE
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
            } ?: fail("Entry $entryName is null")
        }
    }

    @get:Rule
    val tempFolder = TemporaryFolder()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(HelloWorldLibraryApp.create()).create()

    @Test
    fun testSingleLibraryArtProfileMerging() {
        testSingleLibraryWithOptionalApplicationArtProfileMerging(false)
    }

    @Test
    fun testSingleLibraryWithApplicationArtProfilesMerging() {
        testSingleLibraryWithOptionalApplicationArtProfileMerging(true)
    }

    @Test
    fun testSingleLibraryWithGeneratedSourceAndApplicationArtProfiles() {
        testSingleLibraryWithOptionalApplicationArtProfileMerging(
            addApplicationProfile = true,
            useGeneratedSource = true
        )
    }

    private fun testSingleLibraryWithOptionalApplicationArtProfileMerging(
        addApplicationProfile: Boolean,
        useGeneratedSource: Boolean = false
    ) {

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

        val applicationFileContent =
            """
                    HSPLcom/google/Foo;->appMethod(II)I
                    HSPLcom/google/Foo;->appMethod-name-with-hyphens(II)I
                """.trimIndent()

        if (addApplicationProfile) {
            val appAndroidAssets = app.mainSrcDir.parentFile
            appAndroidAssets.mkdir()

            File(
                appAndroidAssets,
                SdkConstants.FN_ART_PROFILE
            ).writeText(
                applicationFileContent
            )
        }

        val library = project.getSubproject(":lib")
        val androidAssets = library.mainSrcDir.parentFile
        androidAssets.mkdir()
        val libraryFileContent =
            """
                HSPLcom/google/Foo;->method(II)I
                HSPLcom/google/Foo;->method-name-with-hyphens(II)I
            """.trimIndent()

        if (useGeneratedSource) {
            TestFileUtils.appendToFile(
                library.buildFile,
                """
                    android.sourceSets {
                        getByName("main") {
                            baselineProfiles.srcDir("src/release/generated/baselineProfiles")
                        }
                    }
                """.trimIndent()
            )
            FileUtils.createFile(
                library.file("src/release/generated/baselineProfiles/baseline-prof.txt"),
                libraryFileContent
            )
        }

        File(androidAssets, SdkConstants.FN_ART_PROFILE).writeText(libraryFileContent)

        val result = project.executor()
                .run(":lib:bundleReleaseAar", ":app:assembleRelease", ":app:makeApkFromBundleForRelease")
        Truth.assertThat(result.failedTasks).isEmpty()

        val libFile = FileUtils.join(
            project.getSubproject(":lib").buildDir,
            SdkConstants.FD_INTERMEDIATES,
            InternalArtifactType.LIBRARY_ART_PROFILE.getFolderName(),
            "release",
            "prepareReleaseArtProfile",
            SdkConstants.FN_ART_PROFILE
        )

        val expectedLibraryContent = if (useGeneratedSource) {
            "$libraryFileContent\n$libraryFileContent"
        } else {
            libraryFileContent
        }

        Truth.assertThat(libFile.readText()).isEqualTo(expectedLibraryContent)

        // check packaging.
        project.getSubproject(":lib").getAar("release") {
            checkAndroidArtifact(tempFolder, it, aarEntryName) { fileContent ->
                Truth.assertThat(fileContent).isEqualTo(expectedLibraryContent.toByteArray())
            }
        }

        val mergedFile = FileUtils.join(
                project.getSubproject(":app").buildDir,
                SdkConstants.FD_INTERMEDIATES,
                InternalArtifactType.MERGED_ART_PROFILE.getFolderName(),
                "release",
                "mergeReleaseArtProfile",
                SdkConstants.FN_ART_PROFILE,
        )
        val expectedContent = if (addApplicationProfile) {
            "$expectedLibraryContent\n$applicationFileContent"
        } else expectedLibraryContent

        Truth.assertThat(mergedFile.readText()).isEqualTo(expectedContent)
        Truth.assertThat(
                HumanReadableProfile(mergedFile) {
                    fail(it)
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

        // check contents of dex metadata properties file produced in CompileArtProfileTask
        val dexMetadataProperties = FileUtils.join(
            project.getSubproject(":app").buildDir,
            SdkConstants.FD_INTERMEDIATES,
            InternalArtifactType.DEX_METADATA_DIRECTORY.getFolderName(),
            "release",
            "compileReleaseArtProfile",
            SdkConstants.FN_DEX_METADATA_PROP
        )

        Truth.assertThat(dexMetadataProperties.readText()).isEqualTo(
            """
                31=0/.dm
                2147483647=0/.dm
                28=1/.dm
                29=1/.dm
                30=1/.dm
            """.trimIndent()
        )

        // check packaging.
        project.getSubproject(":app").getApk(GradleTestProject.ApkType.RELEASE).also {
            checkAndroidArtifact(tempFolder, it, apkEntryName) { fileContent ->
                Truth.assertThat(ArtProfile(ByteArrayInputStream(fileContent))).isNotNull()
            }
            JarFile(it.file.toFile()).use { jarFile ->
                val artProfileEntry = jarFile.getEntry(
                    "${SdkConstants.FN_BINART_ART_PROFILE_FOLDER_IN_APK}/${SdkConstants.FN_BINARY_ART_PROFILE}")
                Truth.assertThat(artProfileEntry.method).isEqualTo(ZipEntry.STORED)

                val artProfileMetadataEntry = jarFile.getEntry(
                    "${SdkConstants.FN_BINART_ART_PROFILE_FOLDER_IN_APK}/${SdkConstants.FN_BINARY_ART_PROFILE_METADATA}")
                Truth.assertThat(artProfileMetadataEntry.method).isEqualTo(ZipEntry.STORED)
            }
        }

        val bundleApks = project.getSubproject(":app").getIntermediateFile("apks_from_bundle", "release", "makeApkFromBundleForRelease", "bundle.apks").toPath()

        val base: ByteArray
        ZipInputStream(bundleApks.inputStream().buffered()).use {
            while (true) {
                val entry = it.nextEntry ?: throw AssertionError("Base apk not found")
                if (entry.name == "splits/base-master.apk") {
                    base = it.readAllBytes()
                    break
                }
            }
        }
        val found = mutableSetOf<String>()
        ZipInputStream(base.inputStream()).use {
            while (true) {
                val entry = it.nextEntry ?: break
                if (entry.name.startsWith("assets/dexopt/baseline.prof")) {
                    found += entry.name
                    Truth.assertThat(entry.method).isEqualTo(ZipEntry.STORED)
                }
            }
        }
        Truth.assertThat(found).containsExactly("assets/dexopt/baseline.prof", "assets/dexopt/baseline.profm")
    }
}
