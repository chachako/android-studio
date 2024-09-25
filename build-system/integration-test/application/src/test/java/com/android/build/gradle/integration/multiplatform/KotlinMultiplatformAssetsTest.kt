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

package com.android.build.gradle.integration.multiplatform

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.apk.Aar
import com.android.testutils.truth.PathSubject
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.io.path.readText

class KotlinMultiplatformAssetsTest {
    @get:Rule
    val project = GradleTestProjectBuilder()
        .fromTestProject("kotlinMultiplatform")
        .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin.androidLibrary {
                    experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
                }
            """.trimIndent()
        )

        FileUtils.writeToFile(
            project.getSubproject("kmpFirstLib").file("src/androidMain/assets/something.json"),
            """
                {
                  "id": 123,
                  "name": "Example Item",
                  "value": 42.5
                }
            """.trimIndent()
        )
    }

    @Test
    fun testKmpLibraryAssetPackageTasksNotExecutedWhenResourcesDisabled() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin.androidLibrary {
                    experimentalProperties["android.experimental.kmp.enableAndroidResources"] = false
                }
            """.trimIndent()
        )

        val result = project.executor().run(":kmpFirstLib:assemble")
        Truth.assertThat(result.didWorkTasks).doesNotContain(
            listOf(
                ":kmpFirstLib:packageAndroidMainAssets"
            )
        )
    }

    @Test
    fun testKmpLibraryAssetPackageTasksExecuted() {
        val result = project.executor().run(":kmpFirstLib:assemble")
        Truth.assertThat(result.didWorkTasks).containsAtLeastElementsIn(
            listOf(
                ":kmpFirstLib:packageAndroidMainAssets"
            )
        )

        Aar(
            project.getSubproject("kmpFirstLib")
                .getOutputFile("aar", "kmpFirstLib.aar")
        ).use { aar ->
            PathSubject.assertThat(aar.getEntry("assets/something.json")).isNotNull()

            val content = aar.getEntry("assets/something.json")
            Truth.assertThat(content.readText()).isEqualTo(
                """
                   {
                     "id": 123,
                     "name": "Example Item",
                     "value": 42.5
                   }
                """.trimIndent()
            )
        }
    }

    @Test
    fun testAppConsumingKmpLibrary() {
        project.executor().run(":app:assembleDebug")

        project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            Truth.assertThat(apk.getEntry("assets/something.json").readText()).isEqualTo(
                """
                   {
                     "id": 123,
                     "name": "Example Item",
                     "value": 42.5
                   }
                """.trimIndent()
            )
        }
    }
}
