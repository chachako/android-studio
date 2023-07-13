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

package com.android.build.gradle.integration.multiplatform.v2

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test

class KotlinMultiplatformAndroidVitalsTest {

    private val sharedLib = MinimalSubProject.kotlinMultiplatformAndroid("com.shared.android")

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject.builder().subproject(":shared", sharedLib).build()
        )
        .withKotlinGradlePlugin(true)
        .create()

    /**
     * To ensure hooks against the kotlin multiplatform plugin can be invoked eagerly if kmp is
     * applied first.
     */
    @Test
    fun kotlinMultiplatformPluginIsAppliedFirst() {
        val content = project.getSubproject(":shared").buildFile.readText()
        FileUtils.writeToFile(
            project.getSubproject(":shared").buildFile,
            "apply plugin: 'org.jetbrains.kotlin.multiplatform'\n$content"
        )
        // TODO (b/293964676): remove withFailOnWarning(false) once KMP bug is fixed
        project.executor()
            .withFailOnWarning(false)
            .run(":shared:androidPrebuild")
    }
}
