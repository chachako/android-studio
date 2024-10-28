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
import com.android.build.gradle.integration.common.truth.TruthHelper
import org.junit.Rule
import org.junit.Test

class LocalJarsTest : ModelComparator() {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("localJars")
        .create()

    @Test
    fun `test VariantDependencies model`() {
        val result = project.modelV2()
            .fetchModels(variantName = "release")

        with(result).compareVariantDependencies(
            projectAction = { getProject(":baseLibrary") }, goldenFile = "baseLibrary_VariantDependencies"
        )
    }

    @Test
    fun lint() {
        project.executor().run("lint")
    }

    @Test
    fun checkBuildResult() {
        val result = project.executor().run("clean", "assembleDebug")
        TruthHelper.assertThat(result.getTask(":baseLibrary:noop"))
            .ranBefore(":baseLibrary:copyDebugJniLibsProjectAndLocalJars")
    }
}
