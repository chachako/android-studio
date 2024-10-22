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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.testing.utp.TEST_RESULT_PB_FILE_NAME
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import java.io.File
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.logging.Logger
import org.gradle.api.services.BuildServiceRegistration
import org.gradle.api.specs.Spec
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers.RETURNS_DEEP_STUBS
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.kotlin.UseConstructor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.junit.MockitoJUnit

/**
 * Unit tests for [ManagedDeviceInstrumentationTestResultAggregationTask].
 */
class ManagedDeviceInstrumentationTestResultAggregationTaskTest {
    @get:Rule
    var temporaryFolderRule = TemporaryFolder()

    private val creationConfig: InstrumentedTestCreationConfig = mock(defaultAnswer = RETURNS_DEEP_STUBS)

    @Before
    fun setUpMocks() {
        whenever(creationConfig.computeTaskNameInternal(any(), any())).then {
            val prefix = it.getArgument<String>(0)
            val suffix = it.getArgument<String>(1)
            "${prefix}AndroidDebugTest${suffix}"
        }
        whenever(creationConfig.name).thenReturn("AndroidDebugTest")
        whenever(creationConfig.services.buildServiceRegistry
               .registrations.getByName(any()))
            .thenReturn(
                mock<BuildServiceRegistration<*, *>>(defaultAnswer = RETURNS_DEEP_STUBS))
    }

    @Test
    fun creationTask() {
        val rootResultsDir = temporaryFolderRule.newFolder("rootResultsDir")
        val pixel3Dir = File(rootResultsDir, "Pixel3").apply { mkdirs() }
        val action = ManagedDeviceInstrumentationTestResultAggregationTask.CreationAction(
            creationConfig,
            listOf(File(pixel3Dir, TEST_RESULT_PB_FILE_NAME)),
            File(rootResultsDir, TEST_RESULT_PB_FILE_NAME),
            temporaryFolderRule.newFolder("testReportOutputDir"),
        )

        assertThat(action.name)
            .isEqualTo("mergeAndroidDebugTestTestResultProtos")
        assertThat(action.type)
            .isEqualTo(ManagedDeviceInstrumentationTestResultAggregationTask::class.java)
    }

    @Test
    fun configureTaskByCreationTask() {
        val rootResultsDir = temporaryFolderRule.newFolder("rootResultsDir")
        val pixel3Dir = File(rootResultsDir, "Pixel3").apply { mkdirs() }
        val action = ManagedDeviceInstrumentationTestResultAggregationTask.CreationAction(
            creationConfig,
            listOf(File(pixel3Dir, TEST_RESULT_PB_FILE_NAME)),
            File(rootResultsDir, TEST_RESULT_PB_FILE_NAME),
            temporaryFolderRule.newFolder("testReportOutputDir"),
        )
        val task = mock<ManagedDeviceInstrumentationTestResultAggregationTask>(defaultAnswer = RETURNS_DEEP_STUBS)

        whenever(task.project.buildDir).thenReturn(File("buildDir"))

        action.configure(task)

        verify(task.inputTestResultProtos).from(eq(listOf(File(pixel3Dir, "test-result.pb"))))
    }

    @Test
    fun taskAction() {
        val task = mock<ManagedDeviceInstrumentationTestResultAggregationTask>(defaultAnswer = CALLS_REAL_METHODS)
        whenever(task.analyticsService).thenReturn(mock())
        doReturn("path").whenever(task).path
        doReturn(mock<TaskOutputsInternal>(defaultAnswer = RETURNS_DEEP_STUBS))
            .whenever(task).outputs
        doReturn(mock<Logger>()).whenever(task).logger

        val inputFiles = mock<FakeConfigurableFileCollection>(
            defaultAnswer = CALLS_REAL_METHODS,
            useConstructor = UseConstructor.withArguments(arrayOf(createResultProto(), createResultProto())),
        )
        doReturn(inputFiles).whenever(inputFiles).filter(any<Spec<File>>())
        whenever(inputFiles.isEmpty).thenReturn(false)
        doReturn(inputFiles).whenever(task).inputTestResultProtos

        val outputFile = temporaryFolderRule.newFile()
        val outputFileProperty = mock<RegularFileProperty>(defaultAnswer = RETURNS_DEEP_STUBS)
        whenever(outputFileProperty.get().asFile).thenReturn(outputFile)
        doReturn(outputFileProperty).whenever(task).outputTestResultProto

        val testReportOutputDir = temporaryFolderRule.newFolder()
        val testReportOutputDirProperty = mock<DirectoryProperty>(defaultAnswer = RETURNS_DEEP_STUBS)
        whenever(testReportOutputDirProperty.get().asFile).thenReturn(testReportOutputDir)
        doReturn(testReportOutputDirProperty).whenever(task).outputTestReportHtmlDir

        task.taskAction()

        val mergedResult = TestSuiteResult.parseFrom(outputFile.inputStream())
        assertThat(mergedResult.testSuiteMetaData.scheduledTestCaseCount)
            .isEqualTo(2)
    }

    private fun createResultProto(): File {
        val protoFile = temporaryFolderRule.newFile()
        TestSuiteResult.newBuilder().apply {
            testSuiteMetaDataBuilder.apply {
                scheduledTestCaseCount = 1
            }
        }.build().writeTo(protoFile.outputStream())
        return protoFile
    }
}
