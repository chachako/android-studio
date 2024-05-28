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
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.dependency.ShrinkerVersion
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class R8VersionCheckTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject("lib", MinimalSubProject.lib("com.example.lib"))
                .build()
        )
        .create()

    @Test
    fun `test warning when project contains R8 version lower than AGP`() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                buildscript {
                    dependencies {
                        classpath 'com.android.tools:r8:8.2.47'
                    }
                }
                """.trimIndent()
        )

        val syncIssueMessages = project.syncIssueMessages
        assertThat(syncIssueMessages).hasSize(1)

        val issueMessage = syncIssueMessages.first()
        assertThat(issueMessage).isEqualTo("Your project includes version 8.2.47 of R8, " +
                "while Android Gradle Plugin was shipped with ${ShrinkerVersion.R8.asString()}. " +
                "This can lead to unexpected issues.")
    }
}

val GradleTestProject.syncIssueMessages: List<String>
    get() {
        val model = modelV2().ignoreSyncIssues().fetchModels().container.getProject()
        return model.issues?.syncIssues?.map { it.message }?.toList().orEmpty()
    }
