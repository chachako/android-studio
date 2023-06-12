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

import com.android.build.api.instrumentation.manageddevice.DeviceTestRunParameters
import com.android.build.api.instrumentation.StaticTestData
import com.android.build.api.instrumentation.manageddevice.TestRunData
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.firebase.testlab.gradle.ManagedDeviceImpl.Orientation
import com.android.tools.firebase.testlab.gradle.services.FtlTestRunResult
import com.android.tools.firebase.testlab.gradle.services.TestLabBuildService
import com.android.tools.firebase.testlab.gradle.tasks.ExtraDeviceFilesManager
import com.google.api.services.storage.model.StorageObject
import com.google.api.services.testing.model.AndroidModel
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.io.File
import java.util.Locale

class TestRunTaskActionTest {

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    @get:Rule
    val mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()

    @Mock
    lateinit var parameters: DeviceTestRunParameters<DeviceTestRunInput>

    @Mock
    lateinit var mockTestRunData: TestRunData

    @Mock
    lateinit var testLabBuildService: TestLabBuildService

    @Mock
    lateinit var mockTestData: StaticTestData

    lateinit var setupResultProperty: DirectoryProperty

    lateinit var projectPath: File

    lateinit var modelFile: File

    lateinit var extraFileUrls: RegularFile

    lateinit var outputDir: Directory

    lateinit var project: Project

    lateinit var model: AndroidModel

    lateinit var testResults: List<FtlTestRunResult>

    @Before
    fun setup() {
        projectPath = temporaryFolderRule.newFolder("testProject")
        project = ProjectBuilder.builder()
            .withName("testProject")
            .withProjectDir(projectPath)
            .build()

        mockTestRunData.apply {
            `when`(deviceName).thenReturn("myManagedDevice")
            `when`(testData).thenReturn(mockTestData)
            `when`(outputDirectory).thenReturn(
                project.layout.buildDirectory.dir("taskOutput").get().apply {
                    outputDir = this
                }
            )
            `when`(projectPath).thenReturn(":")
            `when`(variantName).thenReturn("debug")
        }

        model = AndroidModel().apply {
            id = ("Pixel3")
            supportedVersionIds = listOf("33")
        }

        // create the setup result folder
        val setupResultDir = project.layout.buildDirectory.dir("setupOutput").get().apply {
            asFile.mkdirs()
            file("myManagedDevice.json").asFile.writeText(Gson().toJson(model))
        }


        setupResultProperty = project.objects.directoryProperty().apply {
            set(setupResultDir)
        }

        // Create the extra device files urls file
        extraFileUrls = project.layout.buildDirectory.file("urls").get().also {
            ExtraDeviceFilesManager().apply {
                add("location1", getMockStorage("bucket", "path", "112233"))
                add("location2", getMockStorage("bucket", "other/path", "445566"))
            }.toFile(it.asFile)
        }

        val input = project.objects.newInstance(DeviceTestRunInput::class.java).apply {
            device.set("Pixel3")
            apiLevel.set(33)
            locale.set("fr-CA")
            orientation.set(Orientation.DEFAULT)
            buildService.set(testLabBuildService)
            extraDeviceUrlsFile.set(extraFileUrls)
        }

        parameters.apply {
            `when`(setupResult).thenReturn(setupResultProperty)
            `when`(testRunData).thenReturn(mockTestRunData)
            `when`(deviceInput).thenReturn(input)
        }

        `when`(testLabBuildService.runTestsOnDevice(
            deviceName = any(),
            deviceId = any(),
            deviceApiLevel = any(),
            deviceLocale = any(),
            deviceOrientation = any(),
            ftlDeviceModel = any(),
            testData = any(),
            resultsOutDir = any(),
            projectPath = any(),
            variantName = any(),
            extraDeviceFileUrls = any()
        )).thenAnswer {
            // allows test to update the answer as needed before the test run
            testResults
        }

        testResults = listOf(
            FtlTestRunResult(true, null)
        )
    }

    fun getMockStorage(storageBucket: String, path: String, hash: String): StorageObject =
        mock<StorageObject>().apply {
            `when`(bucket).thenReturn(storageBucket)
            `when`(name).thenReturn(path)
            `when`(md5Hash).thenReturn(hash)
        }

    @Test
    fun test_runTests() {
        assertThat(TestRunTaskAction().runTests(parameters)).isTrue()

        verify(testLabBuildService).runTestsOnDevice(
            eq("myManagedDevice"),
            eq("Pixel3"),
            eq(33),
            eq(Locale.CANADA_FRENCH),
            eq(Orientation.DEFAULT),
            eq(model),
            eq(mockTestData),
            eq(outputDir.asFile),
            eq(":"),
            eq("debug"),
            eq(
                mapOf(
                    "location1" to "gs://bucket/path",
                    "location2" to "gs://bucket/other/path"
                )
            )
        )
    }

    @Test
    fun test_runTests_failure() {
        testResults = listOf(
            FtlTestRunResult(true, null),
            FtlTestRunResult(true, null),
            FtlTestRunResult(false, null),
            FtlTestRunResult(true, null)
        )

        assertThat(TestRunTaskAction().runTests(parameters)).isFalse()

        verify(testLabBuildService).runTestsOnDevice(
            eq("myManagedDevice"),
            eq("Pixel3"),
            eq(33),
            eq(Locale.CANADA_FRENCH),
            eq(Orientation.DEFAULT),
            eq(model),
            eq(mockTestData),
            eq(outputDir.asFile),
            eq(":"),
            eq("debug"),
            eq(
                mapOf(
                    "location1" to "gs://bucket/path",
                    "location2" to "gs://bucket/other/path"
                )
            )
        )
    }
}
