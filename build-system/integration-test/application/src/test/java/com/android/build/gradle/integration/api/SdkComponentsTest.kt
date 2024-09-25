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
import com.android.build.gradle.integration.common.fixture.app.HelloWorldAppKts
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class SdkComponentsTest {
    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(HelloWorldAppKts.forPlugin("com.android.application"))
            .create()

    /**
     * Ensure that SdkComponents' sdkDirectory and ndkDirectory APIs are functional.
     */
    @Test
    fun testAccessToSdkAndNdkDirectories() {
        project.ktsBuildFile.appendText(
            """
            abstract class PrintDirectories: DefaultTask() {
                @get:InputFiles
                abstract val sdkDirectory: DirectoryProperty

                @get:InputFiles
                abstract val ndkDirectory: DirectoryProperty

                @TaskAction
                fun run() {
                    println("SDK directory: ${'$'}{sdkDirectory.get()}")
                    println("NDK directory: ${'$'}{ndkDirectory.get()}")
                }
            }
            tasks.register<PrintDirectories>("printDirectories") {
                sdkDirectory.set(androidComponents.sdkComponents.sdkDirectory)
                ndkDirectory.set(androidComponents.sdkComponents.ndkDirectory)
            }
            """.trimIndent()
        )

        val result = project.executor().run("printDirectories")
        Truth.assertThat(result.didWorkTasks).contains(":printDirectories")
        Truth.assertThat(result.stdout.findAll("SDK directory").count()).isEqualTo(1)
        Truth.assertThat(result.stdout.findAll("NDK directory").count()).isEqualTo(1)
    }
}
