/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.builder.model.SyncIssue
import org.junit.Rule
import org.junit.Test

class BrokenTestModuleSyncTest {

    val app = MinimalSubProject.app("com.example.app")
            .appendToBuild("android {\n" +
                    "    compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}\n" +
                    "    flavorDimensions 'foo'\n" +
                    "    productFlavors {\n" +
                    "        flavor1 {\n" +
                    "            applicationId 'com.example.android.testing.blueprint.flavor1'\n" +
                    "        }\n" +
                    "\n" +
                    "        flavor2 {\n" +
                    "            applicationId 'com.example.android.testing.blueprint.flavor2'\n" +
                    "        }\n" +
                    "    }\n" +
                    "}\n")

    val test = MinimalSubProject.test("com.example.test")
            .appendToBuild("android {\n" +
                    "    compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}\n" +
                    "    // target the app \n" +
                    "    targetProjectPath ':app'\n" +
                    "    // use the old mechanism to target a flavor which isn't supported anymore \n" +
                    "    targetVariant 'flavor1Debug'\n" +
                    "}\n")

    val testApp =
            MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .subproject(":test", test)
                    .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun checkSync() {
        val modelInfo = project.modelV2().ignoreSyncIssues()
            .fetchModels("debug").container.getProject(":test")
        val syncIssues = modelInfo.issues!!.syncIssues.toList()
        val unresolvedDeps = modelInfo.variantDependencies?.mainArtifact?.unresolvedDependencies
        TruthHelper.assertThat(syncIssues).hasSize(1)
        TruthHelper.assertThat(syncIssues[0].severity).isEqualTo(SyncIssue.SEVERITY_ERROR)
        TruthHelper.assertThat(syncIssues[0].type).isEqualTo(SyncIssue.TYPE_UNRESOLVED_DEPENDENCY)
        TruthHelper.assertThat(syncIssues[0].message).isEqualTo(
                "Unable to resolve dependency for ':test@debug/testTarget': Could not resolve project :app.")

        TruthHelper.assertThat(unresolvedDeps).hasSize(1)
        TruthHelper.assertThat(unresolvedDeps?.get(0)?.name).isEqualTo("project :app")
        TruthHelper.assertThat(unresolvedDeps?.get(0)?.cause).contains(
            "cannot choose between the following variants of project :app:"
        )
    }
}
