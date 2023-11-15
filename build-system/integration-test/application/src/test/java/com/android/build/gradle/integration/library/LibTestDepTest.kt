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
import java.io.File

class LibTestDepTest : ModelComparator() {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("libTestDep")
        .create()

    @Test
    fun `test VariantDependencies model`() {
        val result =
            project.modelV2()
                .ignoreSyncIssues()
                .fetchModels(variantName = "debug")

        with(result).compareVariantDependencies(goldenFile = "lib_VariantDependencies")
    }

    @Test
    fun lint() {
        project.executor().run("lint")
    }

    @Test
    fun checkDebugAndReleaseOutputHaveDifferentNames() {
        project.execute("clean", "assembleDebug")
        val debugOutput = getOutputFile()
        project.execute("clean", "assembleRelease")
        val releaseOutput = getOutputFile()
        TruthHelper.assertThat(debugOutput.getName()).isNotEqualTo(releaseOutput.getName())
    }

    private fun getOutputFile(): File {
        val outputs = File(project.buildDir, "outputs/aar").listFiles()
        TruthHelper.assertThat(outputs).isNotNull()
        TruthHelper.assertThat(outputs).hasLength(1)
        return outputs?.get(0)!!
    }
}
