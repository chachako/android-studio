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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.v2.ide.SyncIssue
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class ProjectRepoSetupTest {

    @get:Rule
    val project = GradleTestProject.builder().fromTestProject("basic")
            .withDependencyManagementBlock(false)
            .create()

    @Test
    fun testFlatDirWarning() {
        TestFileUtils.appendToFile(
                project.buildFile,
                "repositories{ apply from: \"../commonLocalRepo.gradle\", to: it}"
        )
        TestFileUtils.appendToFile(
                project.buildFile, "repositories { flatDir { dirs \"libs\" } }")
        project.executor().run("clean", "assembleDebug")
        val onlyModel = project.modelV2()
                .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                .fetchModels()
                .container
        Truth.assertThat(onlyModel.getProject().issues!!.syncIssues.stream()
                .map(SyncIssue::message)
                .filter { syncIssues: String ->
                    syncIssues.contains("flatDir")
                }
                .count().toInt())
                .isEqualTo(1)
    }
}
