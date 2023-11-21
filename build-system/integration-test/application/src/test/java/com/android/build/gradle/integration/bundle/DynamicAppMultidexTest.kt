/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.bundle

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.builder.errors.IssueReporter
import com.android.builder.model.SyncIssue
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class DynamicAppMultidexTest {

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("dynamicApp")
        .create()

    @Test
    fun testSyncWarning() {
        project.getSubproject("feature1").buildFile.appendText(
            "android.buildTypes.debug.multiDexEnabled true"
        )

        val container = project.modelV2().ignoreSyncIssues().fetchModels().container
        val syncIssues = container.getProject(":feature1").issues?.syncIssues!!

        Truth.assertThat(syncIssues.size).isEqualTo(1)
        Truth.assertThat(syncIssues.first().severity)
            .isEqualTo(IssueReporter.Severity.WARNING.severity)
        Truth.assertThat(syncIssues.first().type).isEqualTo(SyncIssue.TYPE_GENERIC)
        Truth.assertThat(syncIssues.first().data).isNull()
        Truth.assertThat(syncIssues.first().message).isEqualTo(
            "Native multidex is always used for dynamic features. Please remove " +
                    "'multiDexEnabled true|false' from your build.gradle file."
        )
    }

    @Test
    fun testSettingMultiDexThroughVariantAPI() {
        project.getSubproject("feature1").buildFile.appendText(
                """
                androidComponents {
                    beforeVariants(selector().withBuildType("debug"), { debugVariantBuilder ->
                        debugVariantBuilder.enableMultiDex = true
                    })
                    onVariants(selector().withBuildType("debug"), { debugVariant ->
                        if (!debugVariant.dexing.isMultiDexEnabled) {
                            throw new RuntimeException("Dexing is not enabled as expected !")
                        }
                    })
                }
                """.trimIndent()
        )
        project.execute("assembleDebug")
    }
}
