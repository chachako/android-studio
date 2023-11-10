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
import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import org.junit.Rule
import org.junit.Test

class MultiProjectTest : ModelComparator() {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("multiproject")
        .create()

    @Test
    fun `check models`() {
        val result = project.modelV2()
            .fetchModels(variantName = "release")

        with(result).compareBasicAndroidProject(
            projectAction = { getProject(":baseLibrary") },
            goldenFile = "baseLibrary_BasicAndroidProject"
        )
        with(result).compareAndroidProject(
            projectAction = { getProject(":baseLibrary") },
            goldenFile = "baseLibrary_AndroidProject"
        )
        with(result).compareAndroidDsl(
            projectAction = { getProject(":baseLibrary") },
            goldenFile = "baseLibrary_AndroidDsl"
        )
        with(result).compareVariantDependencies(
            projectAction = { getProject(":baseLibrary") },
            goldenFile = "baseLibrary_VariantDependencies"
        )
    }
}
