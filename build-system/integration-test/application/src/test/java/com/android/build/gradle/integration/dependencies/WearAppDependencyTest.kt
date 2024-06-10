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

package com.android.build.gradle.integration.dependencies

import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.v2.ide.SyncIssue
import com.google.common.collect.Iterables
import org.junit.Rule
import org.junit.Test

class WearAppDependencyTest {

    @Rule
    @JvmField
    val project = builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Test
    fun exitsWithSyncIssue() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
            dependencies {
                releaseWearApp "androidx.appcompat:appcompat:1.6.1"
            }
            """.trimIndent()
        )
        val syncIssues = project.modelV2()
            .ignoreSyncIssues()
            .fetchModels()
            .container
            .getProject()
            .issues?.syncIssues!!
        TruthHelper.assertThat(syncIssues).hasSize(1)
        val issue = Iterables.getOnlyElement(syncIssues)
        TruthHelper.assertThat(issue.type).isEqualTo(SyncIssue.TYPE_GENERIC)
        TruthHelper.assertThat(issue.severity).isEqualTo(SyncIssue.SEVERITY_WARNING)
        TruthHelper.assertThat(issue.message).contains("releaseWearApp configuration is deprecated and planned to be removed in AGP 9.0. Please do not add any dependencies to it.")
    }
}
