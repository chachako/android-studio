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

package com.android.tools.firebase.testlab.gradle.device

import com.android.tools.firebase.testlab.gradle.ManagedDeviceImpl
import com.android.tools.firebase.testlab.gradle.ManagedDeviceImpl.Orientation
import com.android.tools.firebase.testlab.gradle.services.TestLabBuildService
import com.android.tools.firebase.testlab.gradle.tasks.ExtraDeviceFilesUploadTask
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.IllegalStateException

class TestRunConfigureActionTest {

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    lateinit var projectPath: File

    lateinit var appProjectPath: File

    lateinit var project: Project

    lateinit var appProject: Project

    @Before
    fun set() {
        projectPath = temporaryFolderRule.newFolder("testProject")
        appProjectPath = projectPath.resolve("app").apply {
            mkdir()
        }

        project = ProjectBuilder.builder()
            .withName("testProject")
            .withProjectDir(projectPath)
            .build()

        appProject = ProjectBuilder.builder()
            .withName("app")
            .withParent(project)
            .withProjectDir(appProjectPath)
            .build()

        appProject.gradle.sharedServices.registerIfAbsent(
            TestLabBuildService.RegistrationAction.getBuildServiceName(
                TestLabBuildService::class.java,
                appProject
            ),
            TestLabBuildService::class.java
        ) { buildServiceSpec ->
            buildServiceSpec.parameters.numUniformShards.set(10)
        }

        appProject.tasks.register(
            "firebaseUploadExtraDeviceFiles",
            ExtraDeviceFilesUploadTask::class.java
        ) { task ->
            task.outputFile.set(appProject.layout.buildDirectory.file("extra_device_files_info"))
        }
    }

    fun <T> assertNoModify(property: Property<T>, value: T) {
        val error = assertThrows(IllegalStateException::class.java) {
            property.set(value)
        }
        assertThat(error.message).isEqualTo(
            "The value for ${property.toString()} cannot be changed any further."
        )
    }

    @Test
    fun test_configureTaskInput() {
        val deviceImpl = ManagedDeviceImpl("someTestDevice").apply {
            device = "b0q"
            apiLevel = 33
            orientation = "portrait"
            locale = "sv_SE"
        }

        TestRunConfigureAction(appProject.objects, appProject.providers, appProject)
            .configureTaskInput(deviceImpl).apply {

                // The ManagedDevice dsl name is handled via AGP to the task.
                assertThat(device.get()).isEqualTo("b0q")
                assertThat(apiLevel.get()).isEqualTo(33)
                assertThat(orientation.get()).isEqualTo(Orientation.PORTRAIT)
                assertThat(locale.get()).isEqualTo("sv_SE")
                assertThat(buildService.get()).isSameInstanceAs(
                    TestLabBuildService.RegistrationAction.getBuildService(appProject).get()
                )
                assertThat(numUniformShards.get()).isEqualTo(10)
                assertThat(extraDeviceUrlsFile.get().asFile).isEqualTo(
                    appProjectPath.resolve("build/extra_device_files_info")
                )

                assertNoModify(device, "Pixel 3")
                assertNoModify(apiLevel, 15)
                assertNoModify(orientation, Orientation.DEFAULT)
                assertNoModify(locale, "en-US")
                assertNoModify(numUniformShards, 4)
                assertNoModify(
                    extraDeviceUrlsFile,
                    appProject.layout.buildDirectory.file("somewhere/else").get())
                assertNoModify(
                    buildService,
                    appProject.gradle.sharedServices.registerIfAbsent(
                        "newService",
                        TestLabBuildService::class.java
                    ) {}.get()
                )
            }
    }
}
