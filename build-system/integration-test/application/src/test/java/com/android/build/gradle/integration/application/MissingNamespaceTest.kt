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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.SyncIssue
import org.junit.Rule
import org.junit.Test
import com.google.common.truth.Truth.assertThat
import org.junit.Before

class MissingNamespaceTest {

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.library"))
            .create()

    @Before
    fun before() {
        TestFileUtils.searchAndReplace(
            project.buildFile,
            "namespace \"${HelloWorldApp.NAMESPACE}\"",
            ""
        )
    }

    /**
     * Tests the sync finishes successfully.
     */
    @Test
    fun testSyncIsSuccessful() {
        // Sync should complete successfully
        val modelContainer =
            project.modelV2().ignoreSyncIssues().fetchModels().container.getProject()
        val syncIssues = modelContainer.issues?.syncIssues!!

        val namespaceNotSetSyncIssues =
            syncIssues.filter { it.type == SyncIssue.TYPE_NAMESPACE_NOT_SET }
        assertThat(namespaceNotSetSyncIssues).hasSize(1)
        assertThat(namespaceNotSetSyncIssues.elementAt(0).message).startsWith(
            "Namespace not specified. Specify a namespace in the module's build file:"
        )

        assertThat(modelContainer.androidProject?.namespace).isEqualTo("missing.namespace")
    }

    /**
     * Tests that missing namespace breaks the regular build.
     */
    @Test
    fun testRegularBuildBreaks() {
        project.executor().expectFailure().run("assembleDebug")
    }
}
