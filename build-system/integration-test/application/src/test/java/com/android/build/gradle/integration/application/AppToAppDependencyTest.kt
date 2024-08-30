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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.app.ManifestFileBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import org.junit.Rule
import org.junit.Test

/**
 * Ensure clear errors for application modules depending on other application modules.
 */
class AppToAppDependencyTest {

    @get:Rule
    val project = createGradleProject {
        subProject(":appA") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                namespace = "com.example.appa"
                defaultCompileSdk()
            }
            addFile("src/main/AndroidManifest.xml",
                with(ManifestFileBuilder()) {
                    build()
                }
            )
            dependencies {
                implementation(project(":appB"))
            }
        }
        subProject(":appB") {
            plugins.add(PluginType.ANDROID_APP)
            val appNamespace = "com.example.appb"
            android {
                namespace = namespace
                defaultCompileSdk()
            }
            addFile("src/main/AndroidManifest.xml",
                with(ManifestFileBuilder()) {
                    build()
                }
            )
            dependencies {
            }
        }

        withKotlinPlugin = true
    }

    @Test
    fun build() {
        val failure = project.executor().expectFailure().run("assembleDebug")
        failure.assertErrorContains(
            "This application (com.example.appa) is not configured to use dynamic features."
        )
    }
}
