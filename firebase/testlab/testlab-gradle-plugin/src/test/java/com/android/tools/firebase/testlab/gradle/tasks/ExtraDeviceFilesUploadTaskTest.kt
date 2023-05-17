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

package com.android.tools.firebase.testlab.gradle.tasks

import com.android.tools.firebase.testlab.gradle.services.TestLabBuildService
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argThat
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.google.api.services.storage.model.StorageObject
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import java.io.File
import java.lang.IllegalStateException
import java.nio.charset.StandardCharsets

private const val tab = "\t"

class ExtraDeviceFilesUploadTaskTest {

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    @get:Rule
    val mockitoRule = MockitoJUnit.rule()

    lateinit var projectPath: File

    lateinit var project: Project

    lateinit var task: ExtraDeviceFilesUploadTask

    lateinit var outputFile: File

    @Mock
    lateinit var mockBuildService: TestLabBuildService

    @Before
    fun setup() {
        projectPath = temporaryFolderRule.newFolder("project")
        project = ProjectBuilder.builder().withProjectDir(projectPath).build()

        task = project.tasks.register(
            "firebaseUploadExtraDeviceFiles",
            ExtraDeviceFilesUploadTask::class.java
        ).get()

        outputFile = temporaryFolderRule.newFile("output")

        task.projectPath.set("my_gradle_module")
        task.buildService.set(mockBuildService)
        task.outputFile.set(outputFile)
    }

    @Test
    fun taskAction_noExtraFiles() {
        task.extraFiles.set(mapOf())

        task.validateOrUploadExtraFiles()

        verifyNoInteractions(mockBuildService)

        assertThat(outputFile.readText(StandardCharsets.UTF_8)).isEmpty()
    }

    @Test
    fun taskAction_SimpleExtraFiles() {
        val testFile = temporaryFolderRule.newFile("testFile")
        task.extraFiles.set(
            mapOf(
                "devicePath1" to testFile.path,
                "devicePath2" to "gs://bucket/testPath"
            )
        )

        val localUploadedStorageObject: StorageObject = mock<StorageObject>().apply {
            `when`(this.bucket).thenReturn("project-bucket")
            `when`(this.name).thenReturn("my_gradle_module/hash-testFile")
            `when`(this.md5Hash).thenReturn("aabbcc")
        }
        val cloudStorageObject: StorageObject = mock<StorageObject>().apply {
            `when`(this.bucket).thenReturn("bucket")
            `when`(this.name).thenReturn("testPath")
            `when`(this.md5Hash).thenReturn("ddeeff")
        }

        `when`(mockBuildService.uploadSharedFile(
            eq("my_gradle_module"),
            argThat { file ->
                file.path == testFile.path
            },
            any()
        )).thenReturn(localUploadedStorageObject)

        `when`(mockBuildService.getStorageObject(eq("gs://bucket/testPath"))).thenReturn(
            cloudStorageObject
        )

        task.validateOrUploadExtraFiles()

        val expectedResult =
            """
                devicePath1${tab}gs://project-bucket/my_gradle_module/hash-testFile${tab}aabbcc
                devicePath2${tab}gs://bucket/testPath${tab}ddeeff
            """.trimIndent()

        assertThat(outputFile.readText(StandardCharsets.UTF_8)).isEqualTo(expectedResult)

        // file order should not affect output.
        task.extraFiles.set(
            mapOf(
                "devicePath2" to "gs://bucket/testPath",
                "devicePath1" to testFile.path
            )
        )

        task.validateOrUploadExtraFiles()

        assertThat(outputFile.readText(StandardCharsets.UTF_8)).isEqualTo(expectedResult)
    }

    @Test
    fun taskAction_testFailures() {
        val folder = temporaryFolderRule.newFolder("folder-to-upload")

        task.extraFiles.set(
            mapOf(
                "devicePath1" to folder.path,
                "devicePath2" to "not/a/file",
                "devicePath3" to "gs://bucket/testPath"
            )
        )

        val error = assertThrows(IllegalStateException::class.java) {
            task.validateOrUploadExtraFiles()
        }

        assertThat(error.message).isEqualTo(
            """
                Could not upload extraDeviceFiles for Firebase Test Lab.
                    Local file path: ${folder.path} must be a file. Cannot be uploaded.
                    Local file path: not/a/file does not exist. Cannot upload file.
                    Google Storage link: gs://bucket/testPath does not reference a valid Storage Object.

            """.trimIndent()
        )
    }
}
