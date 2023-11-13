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

package com.android.build.gradle.integration.library

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class FlavorlibTest : ModelComparator() {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("flavorlib")
        .create()

    @Before
    fun setUp() {
        project.execute("clean", "assembleDebug");
    }

    @Test
    fun `test libs debug VariantDependencies model`() {
        val result = project.modelV2().fetchModels(variantName = "debug")

        with(result).compareVariantDependencies(
            projectAction = { getProject(":lib1") }, goldenFile = "lib1_DebugVariantDependencies"
        )
        with(result).compareVariantDependencies(
            projectAction = { getProject(":lib2") }, goldenFile = "lib2_DebugVariantDependencies"
        )
    }

    @Test
    fun `test libs release VariantDependencies model`() {
        val result = project.modelV2().fetchModels(variantName = "release")

        with(result).compareVariantDependencies(
            projectAction = { getProject(":lib1") }, goldenFile = "lib1_ReleaseVariantDependencies"
        )
        with(result).compareVariantDependencies(
            projectAction = { getProject(":lib2") }, goldenFile = "lib2_ReleaseVariantDependencies"
        )
    }

    @Test
    fun lint() {
        project.executor().run("lint")
    }

    @Test
    fun report() {
        project.executor().run("androidDependencies", "signingReport")
    }

    @Test
    fun checkExplodedAar() {
        val intermediates: File =
            FileUtils.join(project.projectDir, "app", "build", "intermediates")
        assertThat(intermediates).isDirectory()
        assertThat(File(intermediates, "exploded-aar")).doesNotExist()
    }
}
