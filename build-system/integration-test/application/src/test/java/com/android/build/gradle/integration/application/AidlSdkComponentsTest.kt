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
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_BUILD_TOOL_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_COMPILE_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import org.junit.Rule
import org.junit.Test

class AidlSdkComponentsTest {
    @JvmField
    @Rule
    val project = GradleTestProject.builder().fromTestApp(
        MinimalSubProject.app("com.example.app")
    ).withPluginManagementBlock(true).create()

    @Test
    fun testAidlTools() {
        project.buildFile.delete()

        project.file("build.gradle.kts").writeText("""
            apply(from = "../commonHeader.gradle")
            plugins {
                id("com.android.application")
            }

            android {
                namespace = "com.example.app"
                compileSdk = $DEFAULT_COMPILE_SDK_VERSION
                buildToolsVersion = "$DEFAULT_BUILD_TOOL_VERSION"
            }

            abstract class AidlTask : DefaultTask() {

                @get:Nested
                abstract val aidlInput: Property<com.android.build.api.variant.Aidl>

                @TaskAction
                fun execute() {
                    val aidlBinary = aidlInput.get().executable.get().asFile
                    val aidlFramework = aidlInput.get().framework.get().asFile
                    val version = aidlInput.get().version.get()

                    if (aidlBinary.exists().not())
                        throw GradleException("executable file missing")
                    if (aidlFramework.exists().not())
                        throw GradleException("framework file missing")
                    if (aidlFramework.path.contains("$DEFAULT_COMPILE_SDK_VERSION").not())
                        throw GradleException("wrong framework file")
                    if (version != "$DEFAULT_BUILD_TOOL_VERSION")
                        throw GradleException("version mismatch")

                }
            }

            val taskProvider = tasks.register<AidlTask>("getAidlTools") {
                this.aidlInput.set(androidComponents.sdkComponents.aidl)
            }

        """.trimIndent())

        project.executor().run("getAidlTools")
    }
}
