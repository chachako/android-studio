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

package com.android.build.gradle.integration.privacysandbox

import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.MavenRepoGenerator
import org.junit.Rule
import org.junit.Test

class PrivacySandboxAsarConsumptionSmokeTest {

    private val mavenRepo = MavenRepoGenerator(listOf(
            MavenRepoGenerator.Library(
                    "com.example:externalasar:1",
                    "asar",
                    byteArrayOf(),
            )
    ))

    @get:Rule
    val project = createGradleProjectBuilder {
        subProject(":example-app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                defaultCompileSdk()
                minSdk = 14
                namespace = "com.example.privacysandboxsdk.consumer"
                compileSdkPreview = "TiramisuPrivacySandbox"

            }
            dependencies {
                implementation("com.example:externalasar:1")
            }
        }
        rootProject {
            useNewPluginsDsl = true
        }
    }
            .withAdditionalMavenRepo(mavenRepo)
            .addGradleProperties("${BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT.propertyName}=true")
            .addGradleProperties("${BooleanOption.USE_ANDROID_X.propertyName}=true")
            .create()


    @Test
    fun testDependencyWithoutSupportEnabled() {
        project.getSubproject(":example-app").buildFile.appendText("""
            android.privacySandbox {
                enable = false
            }
        """.trimIndent())
        val result = project.executor().expectFailure().run(":example-app:assembleDebug")
        assertThat(result.stderr).contains("Dependency com.example:externalasar:1 is an Android Privacy Sandbox SDK library")
    }

}
