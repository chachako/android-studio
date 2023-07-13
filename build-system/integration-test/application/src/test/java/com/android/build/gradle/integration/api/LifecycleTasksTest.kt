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

package com.android.build.gradle.integration.api

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LifecycleTasksTest {

    @get:Rule
    val tmpDirectory = TemporaryFolder()

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Test
    fun testAddingPreBuildDependent() {
        project.buildFile.appendText(
            """
            abstract class PreBuildCustomTask extends DefaultTask {
                @OutputDirectory
                abstract DirectoryProperty getOutputDirectory();

                @TaskAction
                void run() {
                    println("PreBuildCustomTask ran !")
                }
            }

            def customPreBuildProvider = tasks.register("customPreBuild", PreBuildCustomTask) {
                it.getOutputDirectory().set(new File("build/output"))
            }
            androidComponents {
                onVariants(selector().all(),  { variant ->
                    variant.lifecycleTasks.registerPreBuild(customPreBuildProvider)
                })
            }
        """.trimIndent()
        )

        val buildResult = project.executor().run("preDebugBuild")
        Truth.assertThat(buildResult.didWorkTasks).contains(":customPreBuild")
        Truth.assertThat(buildResult.stdout.findAll("PreBuildCustomTask ran !"))
    }
}
