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
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import org.junit.Rule
import org.junit.Test

class GenerateTestConfigTest {

    @get:Rule
    val project =
            GradleTestProject.builder()
                    .fromTestApp(MinimalSubProject.app("com.example.app"))
                    .create()

    // Regression test for b/293547829
    @Test
    fun testAbiSplitEnabledWithIncludeAndroidResource() {
        project.buildFile.appendText("""
            android.testOptions.unitTests.includeAndroidResources = true
            android {
                splits {
                    abi {
                        enable true
                        reset()
                        include "arm64-v8a"
                    }
                }
            }
        """.trimIndent())
        project.executor().run(":generateDebugUnitTestConfig")
    }

    // Regression test for b/293547829
    @Test
    fun testAbiSplitDisabledWithIncludeAndroidResource() {
        project.buildFile.appendText("""
            android.testOptions.unitTests.includeAndroidResources = true
            android {
                splits {
                    abi {
                        enable false
                        reset()
                        include "arm64-v8a"
                    }
                }
            }
        """.trimIndent())
        project.executor().run(":generateDebugUnitTestConfig")
    }
}
