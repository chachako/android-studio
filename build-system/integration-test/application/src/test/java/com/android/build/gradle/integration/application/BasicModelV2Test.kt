/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.builder.model.v2.ide.SyncIssue
import com.android.builder.model.v2.models.ModelBuilderParameter
import org.junit.Rule
import org.junit.Test

class BasicModelV2Test: ModelComparator() {
    @get:Rule
    val project = builder()
        .fromTestProject("basic")
        .create()

    @Test
    fun `test models`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        with(result).compareBasicAndroidProject(goldenFile = "basicAndroidProject")
        with(result).compareAndroidProject(goldenFile = "testProject")
        with(result).compareAndroidDsl(goldenFile = "AndroidDsl")
        with(result).compareVariantDependencies(goldenFile = "testDep")
    }

    @Test
    fun `test models no java runtime classpath`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug", parameterMutator = { it.buildOnlyTestRuntimeClasspaths(buildUnitTestsRuntime = false, buildScreenshotTestsRuntime = false) })

        with(result).compareBasicAndroidProject(goldenFile = "basicAndroidProject")
        with(result).compareAndroidProject(goldenFile = "testProject")
        with(result).compareAndroidDsl(goldenFile = "AndroidDsl")
        with(result).compareVariantDependencies(goldenFile = "testDepAndroidRuntime")
    }

    @Test
    fun `test models no main runtime classpath, but screenshot and unit tests are present`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug", parameterMutator = { it.buildOnlyTestRuntimeClasspaths(buildUnitTestsRuntime = true, buildScreenshotTestsRuntime = true) })

        with(result).compareBasicAndroidProject(goldenFile = "basicAndroidProject")
        with(result).compareAndroidProject(goldenFile = "testProject")
        with(result).compareAndroidDsl(goldenFile = "AndroidDsl")
        with(result).compareVariantDependencies(goldenFile = "testDepAndroidRuntimeWithScreenShotAndUnit")
    }
}


fun ModelBuilderParameter.buildOnlyTestRuntimeClasspaths(
    buildUnitTestsRuntime: Boolean,
    buildScreenshotTestsRuntime: Boolean
) {
    dontBuildRuntimeClasspath = true
    dontBuildUnitTestRuntimeClasspath = !buildUnitTestsRuntime
    dontBuildScreenshotTestRuntimeClasspath = !buildScreenshotTestsRuntime
    dontBuildAndroidTestRuntimeClasspath = false
    dontBuildTestFixtureRuntimeClasspath = true
    dontBuildHostTestRuntimeClasspath = mapOf("UnitTest" to dontBuildUnitTestRuntimeClasspath, "ScreenshotTest" to dontBuildScreenshotTestRuntimeClasspath)
}
