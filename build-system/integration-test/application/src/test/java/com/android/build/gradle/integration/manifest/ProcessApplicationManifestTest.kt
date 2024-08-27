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

package com.android.build.gradle.integration.manifest

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ApkSubject.getManifestContent
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class ProcessApplicationManifestTest {

    private val app =
        MinimalSubProject.app()
            .appendToBuild(
                """
                    android {
                        packaging {
                            jniLibs {
                                useLegacyPackaging false
                            }
                        }
                    }
                """.trimIndent()
            )
    private val lib =
        MinimalSubProject.lib()
            .withFile(
                "src/main/AndroidManifest.xml",
                """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                        <application android:extractNativeLibs="true"/>
                    </manifest>
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
            ).create()

    @Test
    fun testDependencyExtractNativeLibsIsNotMerged() {
        val result1 = project.executor().run(":app:processDebugManifest")

        val manifestFile =
            project.getSubproject(":app")
                .file("build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml")
        assertThat(manifestFile).exists()
        assertThat(manifestFile).contains("android:extractNativeLibs=\"false\"")

        val expectedWarning =
            "android:extractNativeLibs is set to true in a dependency's AndroidManifest.xml"
        ScannerSubject.assertThat(result1.stdout).contains(expectedWarning)

        // Check that no warning message if it's suppressed
        val result2 =
            project.executor()
                .with(BooleanOption.SUPPRESS_EXTRACT_NATIVE_LIBS_WARNINGS, true)
                .run("clean", ":app:processDebugManifest")
        ScannerSubject.assertThat(result2.stdout).doesNotContain(expectedWarning)
    }

    @Test
    fun testLibraryManifestContainsTargetSdkVersionFromOptions() {
        project.getSubproject(":lib").buildFile.appendText("""
            android {
                testOptions {
                    targetSdk = 22
                    unitTests {
                        includeAndroidResources = true
                    }
                }
            }
        """.trimIndent())
        val result = project.executor().run(":lib:processReleaseUnitTestManifest")
        assertTrue { result.failedTasks.isEmpty()}
        val manifestFile =  project.getSubproject(":lib").file("build/intermediates/packaged_manifests/releaseUnitTest/processReleaseUnitTestManifest/AndroidManifest.xml")
        assertThat(manifestFile).contains("android:targetSdkVersion=\"22\"")
    }

    @Test
    fun testApplicationDeviceTestManifestDoesNotContainDebuggableFlag() {
        // The manifest shouldn't contain android:debuggable if we set the testBuildType to release.
        project.getSubproject(":app").buildFile.appendText("\n\nandroid.testBuildType \"release\"\n\n")
        project.executor().run("assembleReleaseAndroidTest")
        val releaseManifestContent =
            getManifestContent(project.getSubproject(":app").getApk(GradleTestProject.ApkType.ANDROIDTEST_RELEASE).file)
        assertManifestContentDoesNotContainString(releaseManifestContent, "android:debuggable")
    }

    @Test
    fun testApplicationDeviceTestManifestDoesContainDebuggableFlagWhenRequested() {
        // The manifest shouldn't contain android:debuggable if we set the testBuildType to release.
        project.getSubproject(":app").buildFile.appendText("""
            android {
                testBuildType = "release"
            }
            androidComponents {
                beforeVariants(selector().withBuildType("release"), { variantBuilder ->
                    variantBuilder.deviceTests.get("AndroidTest").debuggable = true
                })

                onVariants(selector().withBuildType("release"), { variant ->
                    if (!variant.deviceTests.get("AndroidTest").debuggable) {
                        throw new RuntimeException("DeviceTest.debuggable value not set to true")
                    }
                })
            }
        """.trimIndent())
        project.executor().run("assembleReleaseAndroidTest")
        val releaseManifestContent =
            getManifestContent(project.getSubproject(":app").getApk(GradleTestProject.ApkType.ANDROIDTEST_RELEASE).file)
        assertManifestContentContainsString(releaseManifestContent, "android:debuggable")
    }

    private fun assertManifestContentContainsString(
        manifestContent: Iterable<String>,
        stringToAssert: String
    ) {
        manifestContent.forEach { if (it.trim().contains(stringToAssert)) return }
        fail("Cannot find $stringToAssert in ${manifestContent.joinToString(separator = "\n")}")
    }

    private fun assertManifestContentDoesNotContainString(
        manifestContent: Iterable<String>,
        stringToAssert: String
    ) {
        manifestContent.forEach {
            if (it.trim().contains(stringToAssert)) {
                fail("$stringToAssert found in ${manifestContent.joinToString(separator = "\n")}")
            }
        }
    }
}
