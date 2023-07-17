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

package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.internal.tasks.ExtractVersionControlInfoTask
import com.android.build.gradle.options.BooleanOption
import com.android.builder.internal.packaging.IncrementalPackager
import com.android.testutils.truth.ZipFileSubject
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.stream.Collectors
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.io.path.inputStream

/**
 * Integration tests for [ExtractVersionControlInfoTask]
 */
class ExtractVersionControlInfoTest {

    private val app = MinimalSubProject.app("com.example.app")
    private val lib = MinimalSubProject.lib("com.example.lib")

    @JvmField
    @Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .subproject(":lib", lib)
                    .dependency(app, lib)
                    .build()
            ).create()

    @Test
    fun gitHeadContainsRef() {
        val sha = "d34c1b0dff6f7ea51493b9df746188e8d7840a13"
        FileUtils.createFile(project.file(".git/HEAD"),
            """
                ref: refs/heads/branchName
            """.trimIndent())

        FileUtils.createFile(project.file(".git/refs/heads/branchName"), sha)

        project.executor()
            .with(BooleanOption.ENABLE_VCS_INFO, true)
            .run(":app:assembleDebug", ":app:makeApkFromBundleForDebug")

        val apkFile =
            project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG).file.toFile()
        validateApkFileContents(apkFile, sha)
        validateBundleApkFileContents(sha)
    }

    @Test
    fun gitHeadContainsSha() {
        val sha = "40cde1bb54e7895b717a55931e4421cbab41234e"
        FileUtils.createFile(project.file(".git/HEAD"), sha)

        project.executor()
            .with(BooleanOption.ENABLE_VCS_INFO, true)
            .run(":app:assembleDebug", ":app:makeApkFromBundleForDebug")

        val apkFile =
            project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG).file.toFile()
        validateApkFileContents(apkFile, sha)
        validateBundleApkFileContents(sha)
    }

    @Test
    fun headFileMissing() {
        val result = project.executor()
            .with(BooleanOption.ENABLE_VCS_INFO, true)
            .run(":app:assembleDebug")

        ScannerSubject.assertThat(result.stdout).contains(
            "When setting ${BooleanOption.ENABLE_VCS_INFO.propertyName} to true, the project " +
            "must be initialized with Git. The file '.git/HEAD' in the project root is " +
            "missing, so the version control metadata will not be included in the APK.")
    }

    @Test
    fun branchFileMissing() {
        FileUtils.createFile(project.file(".git/HEAD"),
            """
                ref: refs/heads/branchName
            """.trimIndent())

        val result = project.executor()
            .with(BooleanOption.ENABLE_VCS_INFO, true)
            .run(":app:assembleDebug")

        ScannerSubject.assertThat(result.stdout).contains(
            "When setting ${BooleanOption.ENABLE_VCS_INFO.propertyName} to true, the project " +
            "must be initialized with Git. The file '.git/refs/heads/branchName' in the project " +
            "root is missing, so the version control metadata will not be included in the APK.")
    }

    private fun validateBundleApkFileContents(sha: String) {
        val bundleApksFile = project.getSubproject("app")
            .getIntermediateFile("apks_from_bundle", "debug", "bundle.apks")

        val baseMasterApk: ByteArray
        ZipInputStream(bundleApksFile.inputStream().buffered()).use {
            while (true) {
                val entry = it.nextEntry ?: throw AssertionError("Base apk not found")
                if (entry.name == "splits/base-master.apk") {
                    baseMasterApk = it.readAllBytes()
                    break
                }
            }
        }
        val vcInfo: ByteArray
        ZipInputStream(baseMasterApk.inputStream()).use {
            while (true) {
                val entry = it.nextEntry ?: throw AssertionError("VC info file not found")
                if (entry.name.startsWith(IncrementalPackager.VERSION_CONTROL_INFO_ENTRY_PATH)) {
                    vcInfo = it.readAllBytes()
                    break
                }
            }
        }

        Truth.assertThat(vcInfo.decodeToString()).isEqualTo(
            """
                repositories {
                  system: GIT
                  local_root_path: "${'$'}PROJECT_DIR"
                  revision: "$sha"
                }

            """.trimIndent()
        )
    }

    private fun validateApkFileContents(compressedFile: File, sha: String) {
        ZipFile(compressedFile).use { zip ->
            val vcFile = zip.getEntry(IncrementalPackager.VERSION_CONTROL_INFO_ENTRY_PATH)
            Truth.assertThat(vcFile).isNotNull()
            val vcFileReader = BufferedReader(InputStreamReader(zip.getInputStream(vcFile)))
            val contents = vcFileReader.lines().collect(Collectors.joining("\n"))
            Truth.assertThat(contents).isEqualTo(
                """
                    repositories {
                      system: GIT
                      local_root_path: "${'$'}PROJECT_DIR"
                      revision: "$sha"
                    }
                """.trimIndent()
            )
        }
    }
}
