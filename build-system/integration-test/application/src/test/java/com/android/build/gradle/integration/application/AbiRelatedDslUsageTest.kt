/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.NDK_WITH_RISCV_ABI
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.checkSingleIssue
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.testutils.AssumeUtil
import com.android.zipflinger.ZipArchive
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class AbiRelatedDslUsageTest {
    @get:Rule
    var project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.noBuildFile())
            .create()

    @Test
    fun incorrectSupportedAbisAndSplitsInformation() {
        testSupportedAbisAndSplitsInformation(false)
    }

    @Test
    fun correctSupportedAbisAndSplitsInformation() {
        testSupportedAbisAndSplitsInformation(true)
    }

    private fun testSupportedAbisAndSplitsInformation(isUniversalApkRequested: Boolean) {
        TestFileUtils.appendToFile(project.buildFile, "\n" +
                "apply plugin: 'com.android.application'\n" +
                "android {\n" +
                "    namespace \"${HelloWorldApp.NAMESPACE}\"\n" +
                "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n" +
                "    buildToolsVersion '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "'\n" +
                "    defaultConfig {\n" +
                "        applicationId \"demo.bug\"\n" +
                "        minSdkVersion 21\n" +
                "        //noinspection ExpiringTargetSdkVersion,ExpiredTargetSdkVersion\n" +
                "        targetSdkVersion 27\n" +
                "        versionCode 1\n" +
                "        versionName \"1.0\"\n" +
                "        testInstrumentationRunner \"android.support.test.runner.AndroidJUnitRunner\"\n" +
                "        ndk {\n" +
                "            abiFilters \"x86\"\n" +
                "        }\n" +
                "      }\n" +
                "      splits {\n" +
                "        abi {\n" +
                "            enable true\n" +
                "            reset()\n" +
                "            include 'armeabi-v7a'\n" +
                "            universalApk $isUniversalApkRequested \n" +
                "        }\n" +
                "      }\n" +
                "}\n")

        // Query the model to get the incorrect DSL declaration.
        val result = project.modelV2().ignoreSyncIssues().fetchModels()

        val rootBuild = result.container.getProject(":")
        val issues = rootBuild.issues?.syncIssues ?: throw RuntimeException("Missing issues model")

        if (isUniversalApkRequested) {
            assertThat(issues).isEmpty()
        } else {
            issues.checkSingleIssue(
                type = com.android.builder.model.v2.ide.SyncIssue.TYPE_GENERIC,
                severity = com.android.builder.model.v2.ide.SyncIssue.SEVERITY_ERROR,
                message = "Conflicting configuration : 'x86' in ndk abiFilters cannot be present when splits abi filters are set : armeabi-v7a"
            )
        }
    }

    @Test
    fun renderScriptWithRiscvAbi() {
        AssumeUtil.assumeIsLinux()
        TestFileUtils.appendToFile(project.buildFile,
                """
                    apply plugin: 'com.android.application'
                    android {
                        namespace "${HelloWorldApp.NAMESPACE}"
                        compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                        ndkVersion '$NDK_WITH_RISCV_ABI'
                        buildToolsVersion '${GradleTestProject.DEFAULT_BUILD_TOOL_VERSION}'
                        defaultConfig {
                            applicationId "demo.bug"
                            minSdkVersion 21
                            targetSdkVersion 27
                            versionCode 1
                            versionName "1.0"
                            ndk {
                                abiFilters "riscv64"
                            }
                        }
                    }
                """.trimMargin())
        validateRenderScriptRiscvIssueExist()
    }

    @Test
    fun renderScriptWithRiscvAbiInSplits() {
        AssumeUtil.assumeIsLinux()
        TestFileUtils.appendToFile(project.buildFile,
            """
                    apply plugin: 'com.android.application'
                    android {
                        namespace "${HelloWorldApp.NAMESPACE}"
                        compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                        ndkVersion '$NDK_WITH_RISCV_ABI'
                        buildToolsVersion '${GradleTestProject.DEFAULT_BUILD_TOOL_VERSION}'
                        defaultConfig {
                            applicationId "demo.bug"
                            minSdkVersion 21
                            targetSdkVersion 27
                            versionCode 1
                            versionName "1.0"
                            splits {
                                abi {
                                    enable true
                                    reset()
                                    include 'riscv64'
                                }
                            }
                        }
                    }
                """.trimIndent())
        validateRenderScriptRiscvIssueExist()
    }

    @Test
    fun renderScriptWithRiscvAbiWithTargetAbiBuild() {
        AssumeUtil.assumeIsLinux()
        TestFileUtils.appendToFile(project.buildFile,
            """
                    apply plugin: 'com.android.application'
                    android {
                        namespace "${HelloWorldApp.NAMESPACE}"
                        compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                        ndkVersion '$NDK_WITH_RISCV_ABI'
                        buildToolsVersion '${GradleTestProject.DEFAULT_BUILD_TOOL_VERSION}'
                        defaultConfig {
                            applicationId "demo.bug"
                            minSdkVersion 21
                            targetSdkVersion 27
                            versionCode 1
                            versionName "1.0"
                            ndk {
                                abiFilters "x86", "armeabi-v7a", "riscv64"
                            }

                        }
                }
                """.trimIndent())
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "${BooleanOption.BUILD_ONLY_TARGET_ABI.propertyName}=true"
        )
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "${StringOption.IDE_BUILD_TARGET_ABI.propertyName}=riscv64"
        )
        validateRenderScriptRiscvIssueExist()
    }

    private fun validateRenderScriptRiscvIssueExist() {
        TestFileUtils.appendToFile(
            project.buildFile,
            "\nandroid.buildFeatures.renderScript true\n"
        )

        // Query the model to get the incorrect ABI target.
        val result = project.modelV2()
            .ignoreSyncIssues()
            .fetchModels()

        val rootBuild = result.container.getProject(":")
        val issues = rootBuild.issues?.syncIssues ?: throw RuntimeException("Missing issues model")

        issues.checkSingleIssue(
            type = com.android.builder.model.v2.ide.SyncIssue.TYPE_GENERIC,
            severity = com.android.builder.model.v2.ide.SyncIssue.SEVERITY_ERROR,
            message = "Project ${project.name} uses RenderScript. Cannot build for ABIs: [riscv64] because RenderScript is not supported on Riscv."
        )
    }

    @Test
    fun incorrectAbiRequestedWithNdkFilters() {
        TestFileUtils.appendToFile(project.buildFile, "\n" +
                "apply plugin: 'com.android.application'\n" +
                "android {\n" +
                "    namespace \"${HelloWorldApp.NAMESPACE}\"\n" +
                "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n" +
                "    buildToolsVersion '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "'\n" +
                "    defaultConfig {\n" +
                "        applicationId \"demo.bug\"\n" +
                "        minSdkVersion 21\n" +
                "        //noinspection ExpiringTargetSdkVersion,ExpiredTargetSdkVersion\n" +
                "        targetSdkVersion 27\n" +
                "        versionCode 1\n" +
                "        versionName \"1.0\"\n" +
                "        ndk {\n" +
                "            abiFilters \"x86\"\n" +
                "        }\n" +
                "      }\n" +
                "}\n")

        // Query the model to get the incorrect ABI target.
        val result = project.modelV2()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "mips")
            .ignoreSyncIssues()
            .fetchModels()

        val rootBuild = result.container.getProject(":")
        val issues = rootBuild.issues?.syncIssues ?: throw RuntimeException("Missing issues model")

        issues.checkSingleIssue(
            type = com.android.builder.model.v2.ide.SyncIssue.TYPE_GENERIC,
            severity = com.android.builder.model.v2.ide.SyncIssue.SEVERITY_WARNING,
            message = "Cannot build selected target ABI: mips, supported ABIs are: x86"
        )
    }

    @Test
    fun incorrectAbiRequestedWithSplits() {
        TestFileUtils.appendToFile(project.buildFile, "\n" +
                "apply plugin: 'com.android.application'\n" +
                "android {\n" +
                "    namespace \"${HelloWorldApp.NAMESPACE}\"\n" +
                "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n" +
                "    buildToolsVersion '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "'\n" +
                "    defaultConfig {\n" +
                "        applicationId \"demo.bug\"\n" +
                "        minSdkVersion 21\n" +
                "        //noinspection ExpiringTargetSdkVersion,ExpiredTargetSdkVersion\n" +
                "        targetSdkVersion 27\n" +
                "        versionCode 1\n" +
                "        versionName \"1.0\"\n" +
                "      }\n" +
                "      splits {\n" +
                "        abi {\n" +
                "            enable true\n" +
                "            reset()\n" +
                "            include 'armeabi-v7a'\n" +
                "            universalApk false\n" +
                "        }\n" +
                "      }\n" +
                "}\n")

        // Query the model to get the incorrect ABI target.
        val result = project.modelV2()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "x86")
            .ignoreSyncIssues()
            .fetchModels()

        val rootBuild = result.container.getProject(":")
        val issues = rootBuild.issues?.syncIssues ?: throw RuntimeException("Missing issues model")

        issues.checkSingleIssue(
            type = com.android.builder.model.v2.ide.SyncIssue.TYPE_GENERIC,
            severity = com.android.builder.model.v2.ide.SyncIssue.SEVERITY_WARNING,
            message = "Cannot build selected target ABI: x86, supported ABIs are: FilterConfiguration(filterType=ABI, identifier=armeabi-v7a)"
        )
    }

    // b/124109638
    @Test
    fun testAbiFiltersInheritance() {
        // Add placeholder .so files
        project.file("src/main/jniLibs/x86/foo.so").also {
            it.parentFile.mkdirs()
            it.writeText("foo")
        }
        project.file("src/main/jniLibs/x86_64/foo.so").also {
            it.parentFile.mkdirs()
            it.writeText("foo")
        }
        project.file("src/main/jniLibs/armeabi-v7a/foo.so").also {
            it.parentFile.mkdirs()
            it.writeText("foo")
        }
        project.file("src/main/jniLibs/arm64-v8a/foo.so").also {
            it.parentFile.mkdirs()
            it.writeText("foo")
        }

        TestFileUtils.appendToFile(
            project.buildFile,
            // language=groovy
            """
                apply plugin: "com.android.application"
                android {
                    namespace "${HelloWorldApp.NAMESPACE}"
                    compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                    defaultConfig {
                        minSdkVersion 21
                        targetSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                        ndk {
                            abiFilters "x86"
                        }
                    }
                    buildTypes {
                        debug {
                            ndk {
                                abiFilters "x86_64"
                            }
                        }
                        foo {
                            initWith(buildTypes.debug)
                            ndk {
                                abiFilters "armeabi-v7a"
                            }
                        }
                        bar {
                            initWith(buildTypes.debug)
                            ndk {
                                abiFilters.clear()
                                abiFilters "armeabi-v7a"
                            }
                        }
                    }
                }
            """.trimIndent()
        )

        // Build all variants and check APKs for expected .so file entries
        project.executor().run("assembleRelease", "assembleDebug", "assembleFoo", "assembleBar")

        project.getApk(GradleTestProject.ApkType.RELEASE).use { apk ->
            val entryMap = ZipArchive.listEntries(apk.file)
            assertThat(entryMap).containsKey("lib/x86/foo.so")
            assertThat(entryMap).doesNotContainKey("lib/x86_64/foo.so")
            assertThat(entryMap).doesNotContainKey("lib/armeabi-v7a/foo.so")
            assertThat(entryMap).doesNotContainKey("lib/arm64-v8a/foo.so")
        }

        project.getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            val entryMap = ZipArchive.listEntries(apk.file)
            assertThat(entryMap).containsKey("lib/x86/foo.so")
            assertThat(entryMap).containsKey("lib/x86_64/foo.so")
            assertThat(entryMap).doesNotContainKey("lib/armeabi-v7a/foo.so")
            assertThat(entryMap).doesNotContainKey("lib/arm64-v8a/foo.so")
        }

        project.getApk(GradleTestProject.ApkType.of("foo", isSigned = true)).use { apk ->
            val entryMap = ZipArchive.listEntries(apk.file)
            assertThat(entryMap).containsKey("lib/x86/foo.so")
            assertThat(entryMap).containsKey("lib/x86_64/foo.so")
            assertThat(entryMap).containsKey("lib/armeabi-v7a/foo.so")
            assertThat(entryMap).doesNotContainKey("lib/arm64-v8a/foo.so")
        }

        project.getApk(GradleTestProject.ApkType.of("bar", isSigned = true)).use { apk ->
            val entryMap = ZipArchive.listEntries(apk.file)
            // Still contains x86 ABI because calling abiFilters.clear() only clears the buildType's
            // set of ABI filters, not the defaultConfig's ABI filters.
            assertThat(entryMap).containsKey("lib/x86/foo.so")
            assertThat(entryMap).doesNotContainKey("lib/x86_64/foo.so")
            assertThat(entryMap).containsKey("lib/armeabi-v7a/foo.so")
            assertThat(entryMap).doesNotContainKey("lib/arm64-v8a/foo.so")
        }
    }
}
