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

package com.android.build.gradle.integration.common.fixture

import com.android.build.gradle.integration.common.fixture.ConfigurationCacheReportParser.Error
import com.android.build.gradle.integration.common.fixture.ConfigurationCacheReportParser.ErrorType
import java.io.File

/**
 * Test to ensure that we do not access more file at configuration time that we already do.
 * Eventually, we should trace most of these usages and remove access during configuration phase.
 *
 * If you need to add a new exception to the list, please check with Configuration cache engineers
 * to make sure it is unavoidable.
 */
class ConfigurationCacheReportChecker {

    // List of know issues in AGP and other plugins tested in our integration tests.
    val listOfKnownIssues = mapOf(
        ErrorType.File to listOf(
            Error.file(
                location = "com.android.build.gradle.internal.GradleLocalPropertiesFactory",
                name = "local.properties"
            ),
            Error.file(
                location = "com.android.build.gradle.internal.SdkParsingUtilsKt",
                name = "package.xml"
            ),
            Error.file(
                location = "com.android.build.gradle.internal.SdkParsingUtilsKt",
                name = "package.xml"
            ),
            Error.file(
                location = "com.android.io.CancellableFileIo",
                name = "optional.json"
            ),
            Error.file(
                location = "com.android.build.gradle.internal.SdkParsingUtilsKt",
                name = "package.xml"
            ),
            Error.file(
                location = "com.android.io.CancellableFileIo",
                name = "build.prop"
            ),
            Error.file(
                location = "com.android.build.gradle.integration.bundle.AssetPackBundleTest",
                name = ".knownPackages"
            ),

            // NDK
            Error.file(
                location = "bazel test //tools/base/build-system/integration-test/native:NdkBuildVariantApiTest",
                name = "source.properties"
            ),
            Error.file(
                location = "bazel test //tools/base/build-system/integration-test/native:NdkBuildBuildSettingsTest",
                name = "BuildSettings.json"
            ),
            Error.file(
                location = "bazel test //tools/base/build-system/integration-test/native:PrefabPublishingTest",
                name = "Application.mk"
            ),
            Error.file(
                location = "SdkAutoDownloadTest",
                name = "local-sdk-for-test"
            ),
            Error.file(
                location = "SdkAutoDownloadTest",
                name = "licenses"
            ),
            Error.file(
                location = "SdkAutoDownloadTest",
                name = "tools"
            ),
            Error.file(
                location = "SdkAutoDownloadTest",
                name = "android-sdk-preview-license"
            ),
            Error.file(
                location = "SdkAutoDownloadTest",
                name = "repositories.cfg"
            ),
            Error.file(
                location = "SdkAutoDownloadTest",
                name = "android-sdk-license"
            ),
            Error.file(
                location = "bazel test //tools/base/build-system/integration-test/native:CmakeTargetsTest",
                name = "CMakeSettings.json"
            ),
        ),
        ErrorType.FileSystemEntry to listOf(
            Error.fileSystemEntry(
                location = "com.android.build.gradle.tasks.ProcessApplicationManifest\$CreationAction\$configure\$4",
                name = "AndroidManifest.xml"
            ),
            Error.fileSystemEntry(
                location = "com.android.build.gradle.tasks.ProcessTestManifest\$CreationAction\$configure\$1",
                name = "AndroidManifest.xml"
            ),
            Error.fileSystemEntry(
                location = "com.android.build.gradle.tasks.ProcessLibraryManifest\$CreationAction\$configure\$2",
                name = "AndroidManifest.xml"
            ),
            Error.fileSystemEntry(
                location = "com.android.build.gradle.tasks.ProcessTestManifest\$CreationAction\$configure\$1",
                name = "AndroidManifest.xml"
            ),
            Error.fileSystemEntry(
                location = "android_prefs_root/analytics.settings",
                name = "analytics.settings"
            ),
            Error.fileSystemEntry(
                location = "AGP Plugin",
                name = "android_prefs_root"
            ),
            Error.fileSystemEntry(
                location = "android_prefs_root/.android",
                name = ".android"
            ),
            Error.fileSystemEntry(
                location = "AGP Plugin",
                name = "structured-log"
            ),
            Error.fileSystemEntry(
                location = "com.android.build.gradle.internal.GradleLocalPropertiesFactory",
                name = "local.properties"
            ),

            // SDK Loader reading too early
            Error.fileSystemEntry(
                location = "com.android.build.gradle.internal.SdkLocator\$SdkLocationSource prebuilts/studio/sdk/darwin",
                name = "darwin"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "aapt"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "aapt2"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "aidl"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "llvm-rs-cc"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "include"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "clang-include"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "dexdump"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "bcc_compat"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "arm-linux-androideabi-ld"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "aarch64-linux-android-ld"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "i686-linux-android-ld"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "x86_64-linux-android-ld"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "mipsel-linux-android-ld"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "lld"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "zipalign"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "jack.jar"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "jill.jar"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "jack-jacoco-reporter.jar"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "jack-coverage-plugin.jar"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "split-select"
            ),
            Error.fileSystemEntry(
                location = "com.android.build.gradle.internal.SdkParsingUtilsKt",
                name = "package.xml"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "optional.json"
            ),
            Error.fileSystemEntry(
                location = "com.android.build.gradle.internal.PlatformComponents\$Companion",
                name = "api-versions.xml"
            ),
            Error.fileSystemEntry(
                location = "com.android.build.gradle.internal.PlatformComponents\$Companion",
                name = "core-for-system-modules.jar"
            ),
            Error.fileSystemEntry(
                location = "com.android.io.CancellableFileIo",
                name = "core-lambda-stubs.jar"
            ),
            Error.fileSystemEntry(
                location = "com.android.build.gradle.internal.PlatformToolsComponents\$Companion",
                name = "package.xml"
            ),
            Error.fileSystemEntry(
                location = "com.android.build.gradle.internal.SupportToolsComponents\$Companion",
                name = "package.xml"
            ),
            Error.fileSystemEntry(
                location = "com.android.build.gradle.internal.SdkParsingUtilsKt",
                name = "package.xml"
            ),
            Error.fileSystemEntry(
                location = "com.android.build.gradle.internal.EmulatorComponents\$Companion",
                name = "package.xml"
            ),
            Error.fileSystemEntry(
                location = "plugin 'com.android.internal.application'",
                name = "android_prefs_root/uid.txt"
            ),
            Error.fileSystemEntry(
                location = "com.android.build.gradle.integration.bundle.AssetPackBundleTest",
                name = "build.prop"
            ),
            Error.fileSystemEntry(
                location = "com.android.build.gradle.integration.bundle.AssetPackBundleTest",
                name = "optional"
            ),
            Error.fileSystemEntry(
                location = "com.android.build.gradle.integration.bundle.AssetPackBundleTest",
                name = "skins"
            ),
            Error.fileSystemEntry(
                location = "com.android.build.gradle.integration.bundle.AssetPackBundleTest",
                name = "add-ons"
            ),
            Error.fileSystemEntry(
                location = "com.android.build.gradle.integration.bundle.AssetPackBundleTest",
                name = ".knownPackages"
            ),
            Error.fileSystemEntry(
                location = "com.android.build.gradle.integration.bundle.AssetPackBundleTest",
                name = "platforms"
            ),
            Error.fileSystemEntry(
                location = "com.android.build.gradle.integration.bundle.AssetPackBundleTest",
                name = "platform-tools"
            ),
            Error.fileSystemEntry(
                location = "com.android.build.gradle.integration.bundle.AssetPackBundleTest",
                name = "build-tools"
            ),
            Error.fileSystemEntry(
                location = "com.android.build.gradle.integration.bundle.AssetPackBundleTest",
                name = "android-TiramisuPrivacySandbox"
            ),
            Error.fileSystemEntry(
                location = "com.android.build.gradle.integration.bundle.AssetPackBundleTest",
                name = "addon-google_apis-google-24"
            ),

            // NDK
            Error.fileSystemEntry(
                location = "bazel test //tools/base/build-system/integration-test/native:NdkBuildVariantApiTest",
                name = "build.ninja"
            ),
            Error.fileSystemEntry(
                location = "bazel test //tools/base/build-system/integration-test/native:NdkBuildVariantApiTest",
                name = "build.ninja.txt"
            ),
            Error.fileSystemEntry(
                location = "bazel test //tools/base/build-system/integration-test/native:NdkBuildVariantApiTest",
                name = "source.properties"
            ),
            Error.fileSystemEntry(
                location = "bazel test //tools/base/build-system/integration-test/native:NdkBuildVariantApiTest",
                name = "platforms.json"
            ),
            Error.fileSystemEntry(
                location = "bazel test //tools/base/build-system/integration-test/native:NdkBuildVariantApiTest",
                name = "BuildSettings.json"
            ),
            Error.fileSystemEntry(
                location = "bazel test //tools/base/build-system/integration-test/native:NdkBuildVariantApiTest",
                name = "Application.mk"
            ),
            Error.fileSystemEntry(
                location = "bazel test //tools/base/build-system/integration-test/native:NdkBuildVariantApiTest",
                name = "abis.json"
            ),
            Error.fileSystemEntry(
                location = "bazel test //tools/base/build-system/integration-test/native:NdkBuildVariantApiTest",
                name = "Android.mk"
            ),
            Error.fileSystemEntry(
                location = "bazel test //tools/base/build-system/integration-test/native:NdkBuildVariantApiTest",
                name = "ndk"
            ),
            Error.fileSystemEntry(
                location = "bazel test //tools/base/build-system/integration-test/native:NdkBuildVariantApiTest",
                name = "build/intermediates/cxx"
            ),
            Error.fileSystemEntry(
                location = "bazel test //tools/base/build-system/integration-test/native:CmakeTargetsTest",
                name = "CMakeSettings.json"
            ),

            // kotlin-android
            Error.fileSystemEntry(
                location = "plugin 'kotlin-android'",
                name = ".disable"
            ),

            // safeargs plugin
            Error.fileSystemEntry(
                location = "androidx.navigation.safeargs.gradle.SafeArgsPlugin",
                name = "navigation"
            ),

            // Probably TESTS RELATED LOOKUPS
            Error.fileSystemEntry(
                location = "build file 'build.gradle'",
                name = "build.gradle"
            ),

            // Confirmed test lookups
            Error.fileSystemEntry(
                location = "plugin 'com.example.apiuser.example-plugin'",
                name = "linux"
            ),

            Error.fileSystemEntry(
                location = "bazel test //tools/base/build-system/integration-test/application:agp-version-consistency-tests",
                name = "xerces.properties"
            ),
            Error.fileSystemEntry(
                location = "SdkAutoDownloadTest",
                name = "local-sdk-for-test"
            ),
            Error.fileSystemEntry(
                location = "SdkAutoDownloadTest",
                name = "licenses"
            ),
            Error.fileSystemEntry(
                location = "SdkAutoDownloadTest",
                name = "tools"
            ),
            Error.fileSystemEntry(
                location = "SdkAutoDownloadTest",
                name = "android-sdk-preview-license"
            ),
            Error.file(
                location = "SdkAutoDownloadTest",
                name = "repositories.cfg"
            ),
            Error.fileSystemEntry(
                location = "SdkAutoDownloadTest",
                name = "android-sdk-license"
            ),
            Error.fileSystemEntry(
                location = "SdkAutoDownloadTest",
                name = "uid.txt"
            ),
        ),
        ErrorType.ValueFromCustomSource to listOf(
            Error.valueFromCustomSource(
                location = "AGP Plugin",
                name = "FakeDependencyJarCreator"
            ),
            Error.valueFromCustomSource(
                location = "AGP Plugin",
                name = "AndroidDirectoryCreator"
            ),
        )
    )

    /**
     * Check the report file for any errors that was not previously discovered
     * and fail the test if new errors have been reported.
     */
    fun checkReport(reportFile: File) {
        val errorsAndWarnings =
            ConfigurationCacheReportParser(reportFile).getErrorsAndWarnings()
        if (errorsAndWarnings.isEmpty()) {
            println("No Configuration Cache issues found in $reportFile")
        } else {
            val unknownErrors = errorsAndWarnings
                .filterNot { error ->
                    listOfKnownIssues.get(error.type)?.contains(error) ?: false
                }
            if (unknownErrors.isNotEmpty()) {
                System.err.println("In report : $reportFile")
                unknownErrors.forEach { error ->
                    if (listOfKnownIssues.get(error.type)?.contains(error) ?: false) {
                        System.err.println("Known Issue : $error")
                    } else {
                        System.err.println("Error : $error")
                    }
                }
                throw RuntimeException("Configuration Cache report contains error(s)")
            }
        }
    }
}
