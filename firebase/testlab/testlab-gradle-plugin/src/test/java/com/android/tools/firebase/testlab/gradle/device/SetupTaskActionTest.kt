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

import com.android.tools.firebase.testlab.gradle.services.TestLabBuildService
import com.google.api.services.testing.model.AndroidDeviceCatalog
import com.google.api.services.testing.model.AndroidModel
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.io.File
import java.lang.IllegalArgumentException

class SetupTaskActionTest {

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    @get:Rule
    val mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()

    lateinit var projectPath: File

    lateinit var project: Project

    lateinit var outDirectory: Directory

    lateinit var setupInput: DeviceSetupInput

    @Mock
    lateinit var testLabBuildService: TestLabBuildService

    @Mock
    lateinit var catalog: AndroidDeviceCatalog

    lateinit var gson: Gson

    @Before
    fun setup() {
        projectPath = temporaryFolderRule.newFolder("testProject")
        project = ProjectBuilder.builder()
            .withName("testProject")
            .withProjectDir(projectPath)
            .build()

        outDirectory = project.layout.buildDirectory.dir("test-dir").get().apply {
            asFile.mkdirs()
        }

        `when`(testLabBuildService.catalog()).thenReturn(catalog)

        setupInput = project.objects.newInstance(DeviceSetupInput::class.java).apply {
            buildService.set(testLabBuildService)
        }

        gson = GsonBuilder()
            .setObjectToNumberStrategy {
                val number = ToNumberPolicy.LONG_OR_DOUBLE.readNumber(it)
                if (number is Long) {
                    number.toInt()
                } else {
                    number
                }
            }
            .create()
    }

    @Test
    fun test_setup_successPath() {
        setupInput.apply {
            device.set("b0q")
            apiLevel.set(33)
            deviceName.set("nameOfDslDevice")
        }

        lateinit var target: AndroidModel

        `when`(catalog.models).thenReturn(
            listOf(
                // no other info is needed to determine this device is sufficient.
                AndroidModel().apply {
                    id = "Pixel 3"
                },
                AndroidModel().apply {
                    id = "b0q"
                    supportedVersionIds = listOf("29", "30", "32", "33")
                    // This is the android Model we expect to be written to file.
                    target = this
                },
                AndroidModel().apply {
                    id = "someOtherDevice"
                }
            )
        )

        SetupTaskAction().setup(setupInput, outDirectory)

        val result = gson.fromJson(
            outDirectory.file("nameOfDslDevice.json").asFile.readText(),
            AndroidModel::class.java
        )

        assertThat(result).isEqualTo(target)
    }

    @Test
    fun test_setup_invalidDeviceId() {
        setupInput.apply {
            device.set("hi")
            apiLevel.set(33)
            deviceName.set("nameOfDslDevice")
        }

        `when`(catalog.models).thenReturn(
            listOf(
                AndroidModel().apply {
                    id = "Pixel3"
                    name = "Pixel 3"
                    supportedVersionIds = listOf("33")
                },
                AndroidModel().apply {
                    id = "b0q"
                    name = "SM-S9080"
                    supportedVersionIds = listOf("29", "30", "32", "33")
                },
                AndroidModel().apply {
                    id = "hello"
                    name = "world"
                    // Should not be included in recommendations
                    supportedVersionIds = listOf("23", "24")
                },
                AndroidModel().apply {
                    id = "someOtherDevice"
                    name = "something"
                    supportedVersionIds =
                        listOf("25", "26", "27", "28", "29", "30", "31", "32", "33")
                }
            )
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            SetupTaskAction().setup(setupInput, outDirectory)
        }

        assertThat(error.message).isEqualTo(
            """
                Device: hi is not a valid input. Available devices for API level 33 are:
                [Pixel3 (Pixel 3), b0q (SM-S9080), someOtherDevice (something)]
            """.trimIndent()
        )
    }

    @Test
    fun test_setup_invalidApiLevel() {
        setupInput.apply {
            device.set("b0q")
            apiLevel.set(28)
            deviceName.set("nameOfDslDevice")
        }

        lateinit var target: AndroidModel

        `when`(catalog.models).thenReturn(
            listOf(
                AndroidModel().apply {
                    id = "Pixel 3"
                },
                AndroidModel().apply {
                    id = "b0q"
                    supportedVersionIds = listOf("29", "30", "32", "33")
                },
                AndroidModel().apply {
                    id = "someOtherDevice"
                }
            )
        )
        val error = assertThrows(IllegalStateException::class.java) {
            SetupTaskAction().setup(setupInput, outDirectory)
        }

        assertThat(error.message).isEqualTo(
            """
                apiLevel: 28 is not supported by device: b0q. Available Api levels are:
                [29, 30, 32, 33]
            """.trimIndent()
        )
    }
}
