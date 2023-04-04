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

package com.android.build.gradle.integration.dependencies

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.v2.ide.SyncIssue
import com.android.testutils.MavenRepoGenerator
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LibraryAlignmentWarningTest {
    @JvmField
    @Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestApp(MinimalSubProject.lib())
        .create()

    @Test
    fun `warning issued when runtime classpath is built but libraries constrained`() {
        val model = project.modelV2().ignoreSyncIssues(SyncIssue.SEVERITY_WARNING).fetchModels("debug", dontBuildRuntimeClasspath = true)
        val issueModel = model.container.singleProjectInfo.issues
        Truth.assertThat(issueModel).isNotNull()
        Truth.assertThat(issueModel!!.syncIssues.map {it.message}).containsExactly("""
                You have experimental IDE flag gradle.ide.gradle.skip.runtime.classpath.for.libraries enabled,
                but AGP boolean option ${BooleanOption.EXCLUDE_LIBRARY_COMPONENTS_FROM_CONSTRAINTS.propertyName} is not used.

                Please set below in gradle.properties:

                ${BooleanOption.EXCLUDE_LIBRARY_COMPONENTS_FROM_CONSTRAINTS.propertyName}=true

        """.trimIndent())

    }

    @Test
    fun `warning not issued when flag is set`() {
        project.gradlePropertiesFile.appendText(
            "${BooleanOption.EXCLUDE_LIBRARY_COMPONENTS_FROM_CONSTRAINTS.propertyName}=true"
        )
        project.modelV2()
            // allowing experimental option warning
            .allowOptionWarning(BooleanOption.EXCLUDE_LIBRARY_COMPONENTS_FROM_CONSTRAINTS)
            .fetchModels("debug", dontBuildRuntimeClasspath = true)
    }

    @Test
    fun `warning not issued when runtime classpath is built`() {
        project.modelV2().fetchModels("debug", dontBuildRuntimeClasspath = false)
    }
}
