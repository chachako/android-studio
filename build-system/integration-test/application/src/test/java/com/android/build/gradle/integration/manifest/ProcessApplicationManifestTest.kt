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
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test

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
                .file("build/intermediates/merged_manifests/debug/AndroidManifest.xml")
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
}
