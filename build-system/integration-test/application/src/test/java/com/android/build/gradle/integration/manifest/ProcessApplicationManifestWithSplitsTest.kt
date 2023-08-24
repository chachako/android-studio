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

import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.options.StringOption
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class ProcessApplicationManifestWithSplitsTest(private val abi: String, private val expectedVersion: Int) {
    @get:Rule
    val project = createGradleProject {
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                minSdk = 33
                setUpHelloWorld()
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}_{1}")
        fun parameters() = listOf(
                arrayOf("armeabi-v7a", 2001),
                arrayOf("arm64-v8a", 3001),
                arrayOf("x86", 8001),
                arrayOf("x86_64", 9001)
        )

        private val BUILD_FILE_CONTENT_WITH_ABI_SPECIFIC_VERSIONS =
                """
            android {
              splits {

                // Configures multiple APKs based on ABI.
                abi {

                  // Enables building multiple APKs per ABI.
                  enable true

                  // By default all ABIs are included, so use reset() and include to specify that you only
                  // want APKs for x86 and x86_64.

                  // Resets the list of ABIs for Gradle to create APKs for to none.
                  reset()

                  // Specifies a list of ABIs for Gradle to create APKs for.
                  include "x86_64", "x86", "arm64-v8a", "armeabi-v7a"

                  // Specifies that you don't want to also generate a universal APK that includes all ABIs.
                  universalApk false
                }
              }
          }

          ext.abiCodes = ['armeabi-v7a':2, 'arm64-v8a':3, x86:8, x86_64:9]

          import com.android.build.OutputFile

          android.applicationVariants.all { variant ->
            variant.outputs.each { output ->
              def baseAbiVersionCode =
                      project.ext.abiCodes.get(output.getFilter(OutputFile.ABI))
              if (baseAbiVersionCode != null) {
                output.versionCodeOverride =
                        baseAbiVersionCode * 1000 + variant.versionCode
              }
            }
          }
        """.trimIndent()
    }

    @Test
    fun testAppManifestContainsAbiSpecificVersionCode() {
        project.getSubproject(":app").buildFile.appendText(BUILD_FILE_CONTENT_WITH_ABI_SPECIFIC_VERSIONS)
        val result = project.executor()
                .with(StringOption.IDE_BUILD_TARGET_ABI, abi)
                .run(":app:assembleDebug")
        assertTrue { result.failedTasks.isEmpty()}
        val manifestFile =  project.getSubproject(":app").file("build/intermediates/merged_manifest/debug/AndroidManifest.xml")
        assertThat(manifestFile).contains("android:versionCode=\"$expectedVersion\"")
    }
}
