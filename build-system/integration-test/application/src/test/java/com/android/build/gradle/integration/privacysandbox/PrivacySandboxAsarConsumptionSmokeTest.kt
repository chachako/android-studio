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
import com.android.builder.model.v2.ide.LibraryType
import com.android.testutils.MavenRepoGenerator
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
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
                compileSdk = 34

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
            .addGradleProperties("${BooleanOption.USE_ANDROID_X.propertyName}=true")
            .create()


    @Test
    fun testDependencyWithoutSupportEnabled() {
        project.getSubproject(":example-app").buildFile.appendText("""
            android.privacySandbox.enable = false
        """.trimIndent())
        val result = project.executor().expectFailure().run(":example-app:assembleDebug")
        assertThat(result.stderr).contains("Dependency com.example:externalasar:1 is an Android Privacy Sandbox SDK library")

        val models = project.modelV2().fetchModels(variantName = "debug")
        val variantDependencies = models.container.getProject(":example-app").variantDependencies ?: error("Expected variant dependencies model to build")
        val compileDependencies = variantDependencies.mainArtifact.compileDependencies
        assertThat(compileDependencies).hasSize(1)
        val library = variantDependencies.libraries[compileDependencies.single().key] ?: error("Inconsistent model: failed to load compile library")
        assertThat(library.type).isEqualTo(LibraryType.NO_ARTIFACT_FILE)
        assertThat(library.libraryInfo?.name).isEqualTo("externalasar")
    }

}
