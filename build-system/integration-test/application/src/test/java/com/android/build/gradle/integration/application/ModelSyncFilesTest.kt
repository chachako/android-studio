/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.builder.model.SyncIssue
import com.android.builder.model.v2.ide.Variant
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class ModelSyncFilesTest {

    @get:Rule
    var project = GradleTestProject.builder().fromTestApp(HelloWorldApp.forPlugin("com.android.application")).create()

    @Test
    fun testApplicationIdNotSetByTask() {
        val (variant, syncIssues) = getAppVariant()
        assertThat(syncIssues.map { it.message }).isEmpty()
        assertThat(variant.mainArtifact.applicationId).isEqualTo("com.example.helloworld")
    }

    @Test
    fun testAppIdListModelWithCustomizedAppId() {
        addCustomizationToBuildFile()
        val (variant, syncIssues) = getAppVariant()
        assertThat(syncIssues.map { it.message }).containsExactly(APPLICATION_ID_FROM_TASK_UNSUPPORTED)
        assertThat(variant.mainArtifact.applicationId).isEqualTo("")
    }

    private fun addCustomizationToBuildFile() {
        project.buildFile.appendText("""
        abstract class ApplicationIdProducerTask extends DefaultTask {

            @OutputFile
            abstract RegularFileProperty getOutputFile()

            @TaskAction
            void taskAction() {
                getOutputFile().get().getAsFile().write("set.from.task." + name)
            }
        }

        androidComponents {
            // b/176931684
            // disable androidTest as it forces the applicationId resolution
            beforeVariants(selector().withBuildType("debug")) { variantBuilder ->
                variantBuilder.enableAndroidTest = false
            }
            onVariants(selector().withBuildType("debug")) { variant ->
                TaskProvider appIdProducer = tasks.register(variant.name + "AppIdProducerTask", ApplicationIdProducerTask.class) { task ->
                    File outputDir = new File(getBuildDir(), task.name)
                    outputDir.mkdirs()
                    task.getOutputFile().set(new File(outputDir, "appId.txt"))

                }
                variant.setApplicationId(appIdProducer.flatMap { task ->
                        task.getOutputFile().map { it.getAsFile().text }
                })
            }
        }""")
    }

    private fun getAppVariant(): Pair<Variant, Collection<com.android.builder.model.v2.ide.SyncIssue>> {
        val projectModel = project.modelV2()
                .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                .fetchModels()
                .container
                .getProject()
        val variant = (projectModel
                .androidProject
                ?.variants
                ?.first { variant -> variant.name == "debug" }
                ?: throw RuntimeException("could not find AndroidProject model"))
        val syncIssues = projectModel.issues?.syncIssues ?: emptySet()
        return variant to syncIssues
    }

    companion object {
        private val APPLICATION_ID_FROM_TASK_UNSUPPORTED = """
            Failed to read applicationId for debug.
            Setting the application ID to the output of a task in the variant api is not supported
            """.trimIndent()
    }
}
