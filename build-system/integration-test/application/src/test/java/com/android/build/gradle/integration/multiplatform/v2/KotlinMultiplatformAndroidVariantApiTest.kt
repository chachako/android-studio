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

import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Rule
import org.junit.Test

class KotlinMultiplatformAndroidVariantApiTest {
    @get:Rule
    val project = GradleTestProjectBuilder()
        .fromTestProject("kotlinMultiplatform")
        .create()

    @Test
    fun testInstrumentedTestDependencySubstitution() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                androidComponents {
                    onVariant { variant ->
                        variant.nestedComponents.forEach { component ->
                            println(variant.name + ":" + component.name)
                        }
                    }
                }
            """.trimIndent()
        )

        val result = project.executor().run(":kmpFirstLib:assemble")

        ScannerSubject.assertThat(result.stdout).contains("androidMain:androidUnitTest")
        ScannerSubject.assertThat(result.stdout).contains("androidMain:androidInstrumentedTest")
    }
}
