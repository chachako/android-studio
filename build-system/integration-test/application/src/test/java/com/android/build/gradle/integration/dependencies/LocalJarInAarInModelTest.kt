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

package com.android.build.gradle.integration.dependencies

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LocalJarInAarInModelTest : ModelComparator() {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.noBuildFile())
        .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                apply plugin: "com.android.application"
                android {
                    namespace '${HelloWorldApp.NAMESPACE}'
                    compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                    buildToolsVersion "${GradleTestProject.DEFAULT_BUILD_TOOL_VERSION}"
                    defaultConfig {
                        minSdkVersion 4
                    }
                }
                // we need to use this version as later versions don't use
                // internal jars under libs/ anymore
                dependencies {
                    api "com.android.support:support-v4:24.0.0"
                }
            """.trimIndent())
    }

    @Test
    fun `test VariantDependencies model`() {
        val result =
            project.modelV2()
                .ignoreSyncIssues()
                .fetchModels(variantName = "debug")

        with(result).compareVariantDependencies(goldenFile = "app_VariantDependencies")
    }
}
