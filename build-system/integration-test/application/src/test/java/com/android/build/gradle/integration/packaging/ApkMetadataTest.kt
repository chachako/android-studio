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

package com.android.build.gradle.integration.packaging

import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.gradle.integration.common.fixture.DEFAULT_MIN_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.DEBUG
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkMetadataTest {

    @get:Rule
    val project = EmptyActivityProjectBuilder().build()

    @get:Rule
    val tmpDir = TemporaryFolder()

    @Test
    fun testApkMetadataContents() {
        project.execute("assembleDebug")

        val apkFile = project.getSubproject("app").getApkAsFile(DEBUG)
        val apkMetadataFile = apkFile.parentFile.resolve(BuiltArtifactsImpl.METADATA_FILE_NAME)
        assertThat(apkMetadataFile.readText()).isEqualTo(
            """
            {
              "version": 3,
              "artifactType": {
                "type": "APK",
                "kind": "Directory"
              },
              "applicationId": "com.example.myapplication",
              "variantName": "debug",
              "elements": [
                {
                  "type": "SINGLE",
                  "filters": [],
                  "attributes": [],
                  "versionCode": 0,
                  "versionName": "",
                  "outputFile": "app-debug.apk"
                }
              ],
              "elementType": "File",
              "minSdkVersionForDexing": $DEFAULT_MIN_SDK_VERSION
            }
            """.trimIndent()
        )
    }
}
