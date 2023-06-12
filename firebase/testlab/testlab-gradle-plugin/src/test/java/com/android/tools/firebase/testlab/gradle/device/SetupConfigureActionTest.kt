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
import com.android.tools.firebase.testlab.gradle.services.TestLabBuildService
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

class SetupConfigureActionTest {

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    lateinit var projectPath: File

    lateinit var appProjectPath: File

    lateinit var project: Project

    lateinit var appProject: Project

    @Before
    fun setup() {
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
        ) {
            // no configure needed for test.
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
        val deviceImpl = ManagedDeviceImpl("testDeviceName").apply {
            device = "Pixel 3"
            apiLevel = 32
            orientation = "portrait"
            locale = "pl-PL"
        }

        SetupConfigureAction(appProject.objects, appProject)
            .configureTaskInput(deviceImpl).apply {

                assertThat(deviceName.get()).isEqualTo("testDeviceName")
                assertThat(device.get()).isEqualTo("Pixel 3")
                assertThat(apiLevel.get()).isEqualTo(32)
                assertThat(buildService.get()).isSameInstanceAs(
                    TestLabBuildService.RegistrationAction.getBuildService(appProject).get()
                )

                assertNoModify(deviceName, "hello")
                assertNoModify(device, "hello")
                assertNoModify(apiLevel, 24)
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
