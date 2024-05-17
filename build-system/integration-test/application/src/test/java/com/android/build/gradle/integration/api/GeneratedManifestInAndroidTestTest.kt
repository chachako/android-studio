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

package com.android.build.gradle.integration.api

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class GeneratedManifestInAndroidTestTest {
    @Rule
    @JvmField
    var project = GradleTestProject.builder().fromTestProject("androidManifestInTest").create()

    fun addManifestGenerationToManifest() {
        project.buildFile.appendText(
            """
            androidComponents {
                onVariants(selector().all(), { variant ->
                    def generateCustomManifestTask = tasks.register("generate${"$"}{variant.name.capitalize()}CustomManifest", GenerateCustomManifestTask)

                    if (variant.androidTest != null) {
                        variant.androidTest.sources.manifests.addGeneratedManifestFile(generateCustomManifestTask, { it.getManifestProperty() })
                    }
                })
            }

            abstract class GenerateCustomManifestTask extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getManifestProperty()

                @TaskAction
                void generateManifest() {
                    String manifest = ""${'"'}
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                            android:versionCode="00000000"
                            android:versionName="0.0.0">

                            <permission
                                android:name="foo.generated.SEND_TEXT"
                                android:description="@string/app_name"
                                android:label="@string/app_name"
                                android:permissionGroup="foo.permission-group.COST_MONEY" />
                        </manifest>
                    ""${'"'}.stripIndent()

                    File manifestFile = manifestProperty.get().asFile
                    manifestFile.write(manifest)
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun testGeneratedManifestIsUsed() {
        addManifestGenerationToManifest()
        project.execute("assembleDebugAndroidTest")
        project.getIntermediateFile(
            "packaged_manifests",
            "debugAndroidTest",
            "processDebugAndroidTestManifest",
            "AndroidManifest.xml"
        ).let {
            Truth.assertThat(it.exists()).isTrue()
            it.readText().let { content ->
                Truth.assertThat(content).contains("android:name=\"foo.generated.SEND_TEXT\"")
                Truth.assertThat(content).contains("android:versionCode=\"00000000")
            }
        }
    }

    @Test
    fun testGeneratedManifestIsUsedAsMainManifest() {

        addManifestGenerationToManifest()
        // delete the main manifest source file and make sure it still merges fine.
        File(project.projectDir, "src/androidTest/AndroidManifest.xml").delete()
        project.execute("assembleDebugAndroidTest")
        project.getIntermediateFile(
            "packaged_manifests",
            "debugAndroidTest",
            "processDebugAndroidTestManifest",
            "AndroidManifest.xml"
        ).let {
            Truth.assertThat(it.exists()).isTrue()
            it.readText().let { content ->
                Truth.assertThat(content).contains("android:name=\"foo.generated.SEND_TEXT\"")
                Truth.assertThat(content).contains("android:versionCode=\"00000000")
            }
        }
    }

    @Test
    fun testNoMainManifestNorOverlays() {

        // delete the main manifest source file and make sure it still merges fine.
        File(project.projectDir, "src/androidTest/AndroidManifest.xml").delete()
        project.execute("assembleDebugAndroidTest")
        project.getIntermediateFile(
            "packaged_manifests",
            "debugAndroidTest",
            "processDebugAndroidTestManifest",
            "AndroidManifest.xml"
        ).let {
            Truth.assertThat(it.exists()).isTrue()
            // the instrumentation manifest file should have been generated correctly.
            it.readText().let { content ->
                Truth.assertThat(content).contains("instrumentation")
            }
        }
    }
}
