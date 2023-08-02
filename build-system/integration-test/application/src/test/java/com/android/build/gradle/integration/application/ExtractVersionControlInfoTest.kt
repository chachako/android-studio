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
import com.android.build.gradle.integration.common.fixture.LoggingLevel
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.tasks.ExtractVersionControlInfoTask
import com.android.build.gradle.options.BooleanOption
import com.android.builder.internal.packaging.IncrementalPackager
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.stream.Collectors
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

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

    private val includeVcsDsl =
        """
            android.buildTypes.debug.vcsInfo.include true
        """.trimIndent()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(project.getSubproject("app").buildFile, includeVcsDsl)
    }

    @Test
    fun gitHeadContainsRef() {
        val sha = "d34c1b0dff6f7ea51493b9df746188e8d7840a13"
        FileUtils.createFile(project.file(".git/HEAD"),
            """
                ref: refs/heads/branchName
            """.trimIndent())
        FileUtils.createFile(project.file(".git/refs/heads/branchName"), sha)

        project.executor().run(":app:assembleDebug", ":app:makeApkFromBundleForDebug")

        val apkFile =
            project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG).file.toFile()
        validateApkFileContents(apkFile, sha)
        validateBundleApkFileContents(sha)
    }

    @Test
    fun gitHeadContainsSha() {
        val sha = "40cde1bb54e7895b717a55931e4421cbab41234e"
        FileUtils.createFile(project.file(".git/HEAD"), sha)

        project.executor().run(":app:assembleDebug", ":app:makeApkFromBundleForDebug")

        val apkFile =
            project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG).file.toFile()
        validateApkFileContents(apkFile, sha)
        validateBundleApkFileContents(sha)
    }

    @Test
    fun gitNotInitialized() {
        var result = project.executor().expectFailure().run(":app:assembleDebug")

        val gitMissingMessage = "When VCS tagging is enabled (which is by default in " +
            "release builds), the root project must be initialized with Git. " +
            "Currently, Git is the only supported VCS for this feature."

        ScannerSubject.assertThat(result.stderr).contains(gitMissingMessage)

        // Task runs by default in the release variant when the feature is not explicitly set.
        // In this case, the error should be logged only, and the build should not fail.
        result = project.executor()
            .withLoggingLevel(LoggingLevel.DEBUG)
            .run(":app:assembleRelease", ":app:makeApkFromBundleForRelease")
        Truth.assertThat(result.didWorkTasks).contains(":app:extractReleaseVersionControlInfo")
        ScannerSubject.assertThat(result.stdout).contains(gitMissingMessage)
        val apkFile = project.getSubproject("app")
            .getApk(GradleTestProject.ApkType.RELEASE).file.toFile()
        validateErrorInApkFileContents(apkFile, "NO_SUPPORTED_VCS_FOUND")
        validateErrorInBundleApkFileContents("NO_SUPPORTED_VCS_FOUND")
    }

    @Test
    fun headFileMissing() {
        FileUtils.mkdirs(project.file(".git"))
        var result = project.executor().expectFailure().run(":app:assembleDebug")

        val fileMissingMessage =
            "When VCS tagging is enabled (which is by default in release builds), the project " +
            "must be initialized with Git. The file '.git/HEAD' in the project root is " +
            "missing, so the version control metadata cannot be included in the APK."

        ScannerSubject.assertThat(result.stderr).contains(fileMissingMessage)

        // Task runs by default in the release variant when the feature is not explicitly set.
        // In this case, the error should be logged only, and the build should not fail.
        result = project.executor()
            .withLoggingLevel(LoggingLevel.DEBUG)
            .run(":app:assembleRelease", ":app:makeApkFromBundleForRelease")
        Truth.assertThat(result.didWorkTasks).contains(":app:extractReleaseVersionControlInfo")
        ScannerSubject.assertThat(result.stdout).contains(fileMissingMessage)
        val apkFile = project.getSubproject("app")
            .getApk(GradleTestProject.ApkType.RELEASE).file.toFile()
        validateErrorInApkFileContents(apkFile, "NO_VALID_GIT_FOUND")
        validateErrorInBundleApkFileContents("NO_VALID_GIT_FOUND")
    }

    @Test
    fun branchFileMissing() {
        FileUtils.createFile(project.file(".git/HEAD"),
            """
                ref: refs/heads/branchName
            """.trimIndent())

        var result = project.executor()
            .expectFailure()
            .run(":app:assembleDebug")

        val fileMissingMessage =
            "When VCS tagging is enabled (which is by default in release builds), the project " +
            "must be initialized with Git. The file '.git/refs/heads/branchName' in the project " +
            "root is missing, so the version control metadata cannot be included in the APK."

        ScannerSubject.assertThat(result.stderr).contains(fileMissingMessage)

        // Task runs by default in the release variant when the feature is not explicitly set.
        // In this case, the error should be logged only, and the build should not fail.
        result = project.executor()
            .withLoggingLevel(LoggingLevel.DEBUG)
            .run(":app:assembleRelease", ":app:makeApkFromBundleForRelease")
        Truth.assertThat(result.didWorkTasks).contains(":app:extractReleaseVersionControlInfo")
        ScannerSubject.assertThat(result.stdout).contains(fileMissingMessage)
        val apkFile = project.getSubproject("app")
            .getApk(GradleTestProject.ApkType.RELEASE).file.toFile()
        validateErrorInApkFileContents(apkFile, "NO_VALID_GIT_FOUND")
        validateErrorInBundleApkFileContents("NO_VALID_GIT_FOUND")
    }

    @Test
    fun testDefaultBehavior() {
        TestFileUtils.searchAndReplace(
            project.getSubproject("app").buildFile, includeVcsDsl, "")

        val sha = "40cde1bb54e7895b717a55931e4421cbab41234e"
        FileUtils.createFile(project.file(".git/HEAD"), sha)

        // When DSL is not specified, debug build should not enable feature
        var result =
            project.executor().run(":app:assembleDebug", ":app:makeApkFromBundleForDebug")
        Truth.assertThat(result.didWorkTasks)
            .doesNotContain(":app:extractDebugVersionControlInfo")

        result = project.executor()
            .run(":app:assembleRelease", ":app:makeApkFromBundleForRelease")
        Truth.assertThat(result.didWorkTasks).contains(":app:extractReleaseVersionControlInfo")
        val apkFile = project.getSubproject("app")
            .getApk(GradleTestProject.ApkType.RELEASE).file.toFile()
        validateApkFileContents(apkFile, sha)
        validateBundleApkFileContents(sha, "release")
    }

    @Test
    fun testFalseBehavior() {
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            """
                android.buildTypes.release.vcsInfo.include = false
            """.trimIndent()
        )

        val result = project.executor()
            .run(":app:assembleRelease", ":app:makeApkFromBundleForRelease")
        Truth.assertThat(result.didWorkTasks)
            .doesNotContain(":app:extractReleaseVersionControlInfo")
    }

    @Test
    fun testKotlinDsl() {
        project.getSubproject("app").buildFile.delete()

        project.getSubproject("app").file("build.gradle.kts").writeText(
                // language=kotlin
            """
                plugins {
                    id("com.android.application")
                }

                android {
                    namespace = "com.example.app"
                    compileSdkVersion(${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION})
                    defaultConfig {
                        minSdkVersion(14)
                        versionCode = 1
                    }

                    buildTypes {
                        debug {
                            vcsInfo {
                                include = true
                            }
                        }
                    }
                }
            """.trimIndent()
        )

        val sha = "40cde1bb54e7895b717a55931e4421cbab41234e"
        FileUtils.createFile(project.file(".git/HEAD"), sha)

        val result = project.executor()
            .run(":app:assembleDebug", ":app:makeApkFromBundleForDebug")
        Truth.assertThat(result.didWorkTasks).contains(":app:extractDebugVersionControlInfo")
        val apkFile = project.getSubproject("app")
            .getApk(GradleTestProject.ApkType.DEBUG).file.toFile()
        validateApkFileContents(apkFile, sha)
        validateBundleApkFileContents(sha)
    }

    @Test
    fun validateOldFlagIsDeprecated() {
        val result = project.executor()
                .with(BooleanOption.ENABLE_VCS_INFO, true)
                .expectFailure()
                .run(":app:assembleDebug")

        ScannerSubject.assertThat(result.stderr).contains(
            "The option 'android.enableVcsInfo' is deprecated.")
    }

    private fun validateBundleApkFileContents(sha: String, variant: String = "debug") {
        val vcInfo = getBundleApkFileContents(variant)

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

    private fun validateErrorInBundleApkFileContents(errorMessage: String) {
        val vcInfo = getBundleApkFileContents("release")
        Truth.assertThat(vcInfo.decodeToString()).contains(errorMessage)
    }

    private fun getBundleApkFileContents(variant: String = "debug"): ByteArray {
        val bundleTaskName =
            if (variant == "debug") "makeApkFromBundleForDebug" else "makeApkFromBundleForRelease"
        val bundleApksFile = project.getSubproject("app")
            .getIntermediateFile("apks_from_bundle", variant, bundleTaskName, "bundle.apks")

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

        return vcInfo
    }

    private fun validateApkFileContents(compressedFile: File, sha: String) {
        val vcInfo = getApkFileContents(compressedFile)
        Truth.assertThat(vcInfo).isEqualTo(
            """
                repositories {
                  system: GIT
                  local_root_path: "${'$'}PROJECT_DIR"
                  revision: "$sha"
                }
            """.trimIndent()
        )
    }

    private fun validateErrorInApkFileContents(compressedFile: File, errorMessage: String) {
        val vcInfo = getApkFileContents(compressedFile)
        Truth.assertThat(vcInfo).contains(errorMessage)
    }

    private fun getApkFileContents(compressedFile: File): String {
        val contents = ZipFile(compressedFile).use { zip ->
            val vcFile = zip.getEntry(IncrementalPackager.VERSION_CONTROL_INFO_ENTRY_PATH)
            Truth.assertThat(vcFile).isNotNull()
            val vcFileReader = BufferedReader(InputStreamReader(zip.getInputStream(vcFile)))
            vcFileReader.lines().collect(Collectors.joining("\n"))
        }

        return contents
    }
}
