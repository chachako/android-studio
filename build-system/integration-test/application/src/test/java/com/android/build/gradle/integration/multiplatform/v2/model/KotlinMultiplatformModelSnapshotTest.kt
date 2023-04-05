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

package com.android.build.gradle.integration.multiplatform.v2.model

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.fixture.ModelContainerV2
import com.android.build.gradle.integration.common.fixture.model.BaseModelComparator
import org.junit.Rule
import org.junit.Test

class KotlinMultiplatformModelSnapshotTest: BaseModelComparator {
    @Suppress("DEPRECATION") // kmp doesn't support configuration caching for now (b/276472789)
    @get:Rule
    val project = GradleTestProjectBuilder()
        .fromTestProject("kotlinMultiplatform")
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
        .create()

    @Test
    fun testModels() {
        val buildMap = mapOf(
            ModelContainerV2.ROOT_BUILD_ID to ModelContainerV2.BuildInfo(
                name = "kotlinMultiplatform",
                rootDir = project.projectDir,
                projects = project.settingsFile.readText().split("\n").mapNotNull {
                    it.takeIf { it.startsWith("include ") }?.substringAfter("include ")
                        ?.trim('\'', ':')?.let { it to project.getSubproject(it).projectDir }
                }
            )
        )

        val comparator = KmpModelComparator(
            project = project,
            testClass = this
        )

        comparator.fetchAndCompareModelForProject(
            projectPath = ":kmpFirstLib",
            buildMap = buildMap
        )

        comparator.fetchAndCompareModelForProject(
            projectPath = ":kmpSecondLib",
            buildMap = buildMap
        )
    }
}
