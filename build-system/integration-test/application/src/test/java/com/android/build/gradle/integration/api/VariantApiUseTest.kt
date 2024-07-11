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

package com.android.build.gradle.integration.api

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class VariantApiUseTest {
    @get: Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Test
    fun testToTransformFilesWithName() {
        project.buildFile.appendText(
            """
    android {
        flavorDimensions "version"
        productFlavors {
            free {
                dimension "version"
                // ...
            }
            paid {
                dimension "version"
                // ...
            }
        }
    }
    int beforeVariantsInvocations = 0
    int onVariantsInvocations = 0
    androidComponents {
        beforeVariants(selector().all(), { variantBuilder ->
            beforeVariantsInvocations += 1
        })
        onVariants(selector().all(), { variant ->
            if (onVariantsInvocations == 0 && beforeVariantsInvocations != 4) {
                throw RuntimeExceptions("Expected 4 invocations of beforeVariants before onVariants is called, got " + beforeVariantsInvocations)
            }
            onVariantsInvocations +=1
            println("onVariant ${'$'}{variant.name} called")
        })
    }
            """.trimIndent()
        )

        project.executor().run("tasks")
    }


}
