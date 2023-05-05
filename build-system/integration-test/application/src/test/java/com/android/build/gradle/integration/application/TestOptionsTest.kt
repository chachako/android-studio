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
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.truth.ScannerSubject
import org.junit.Rule
import org.junit.Test

class TestOptionsTest {
    @JvmField @Rule
    var project:GradleTestProject = builder()
    .fromTestProject("basic")
    .create()

    @Test
    fun testApplicationBuildFailedWhenSetTestOptionsTargetSdk() {
        project.buildFile.appendText("""
            android {
                testOptions {
                    targetSdk = 22
                    unitTests {
                        includeAndroidResources = true
                    }
                }
            }
        """.trimIndent())
        val result = project.executor().expectFailure().run("assemble")
        result.stderr.use {
            ScannerSubject.assertThat(it)
                .contains("targetSdk is set as 22 in testOptions for non library module")
        }
    }
}
