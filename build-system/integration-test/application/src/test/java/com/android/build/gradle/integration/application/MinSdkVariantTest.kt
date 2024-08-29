/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import org.junit.Rule
import org.junit.Test

class MinSdkVariantTest {

    @get:Rule
    val appProject: GradleTestProject = builder()
        .withName("application")
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Test
    fun testMinSdkValuesFromVariantAPI() {
        appProject.buildFile.appendText("""
            android {
                defaultConfig {
                    minSdkVersion 12
                }
                flavorDimensions "color"
                productFlavors {
                    red {
                        dimension = "color"
                    }
                    blue {
                        dimension = "color"
                        minSdkVersion 19
                    }
                }
            }

            androidComponents {
                onVariants(selector().withFlavor(new kotlin.Pair("color", "red")), { variant ->
                    if (variant.minSdk.api != 12)
                        throw new RuntimeException("Invalid minSdk version, expected 12, got ${'$'}{variant.minSdk.api}")
                    if (variant.buildType == "debug" && variant.deviceTests.get("AndroidTest").minSdk.api != 12)
                        throw new RuntimeException("Invalid device test minSdk version, expected 12, got ${'$'}{variant.deviceTests.get("AndroidTest").minSdk.api}")
                })
                onVariants(selector().withFlavor(new kotlin.Pair("color", "blue")), { variant ->
                    if (variant.minSdk.api != 19)
                        throw new RuntimeException("Invalid minSdk version, expected 19, got ${'$'}{variant.minSdk.api}")
                    if (variant.buildType == "debug" && variant.deviceTests.get("AndroidTest").minSdk.api != 19)
                        throw new RuntimeException("Invalid device test minSdk version, expected 12, got ${'$'}{variant.deviceTests.get("AndroidTest").minSdk.api}")
                })
            }
        """.trimIndent())

        appProject.executor().run("tasks")
    }
}
