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

package com.android.tools.firebase.testlab.gradle.services.testrunner

import com.android.build.api.instrumentation.StaticTestData
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.capture
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.firebase.testlab.gradle.UtpTestSuiteResultMerger
import com.android.tools.firebase.testlab.gradle.services.StorageManager
import com.android.tools.firebase.testlab.gradle.services.TestResultProcessor
import com.android.tools.firebase.testlab.gradle.services.TestingManager
import com.android.tools.firebase.testlab.gradle.services.ToolResultsManager
import com.android.tools.firebase.testlab.gradle.services.storage.TestRunStorage
import com.google.api.services.storage.model.StorageObject
import com.google.api.services.testing.model.TestExecution
import com.google.api.services.testing.model.TestMatrix
import com.google.api.services.testing.model.ToolResultsStep
import com.google.api.services.toolresults.model.FileReference
import com.google.api.services.toolresults.model.Step
import com.google.api.services.toolresults.model.TestExecutionStep
import com.google.api.services.toolresults.model.TestSuiteOverview
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.proto.api.core.TestStatusProto.TestStatus
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.io.File
import java.io.OutputStream
import java.util.Locale

class TestRunnerTest {

    @get:Rule
    val mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    @Mock
    lateinit var projectSettings: ProjectSettings

    @Mock
    lateinit var toolResults: ToolResultsManager

    @Mock
    lateinit var testing: TestingManager

    @Mock
    lateinit var storage: StorageManager

    @Mock
    lateinit var mockTestRunStorage: TestRunStorage

    @Mock
    lateinit var testResultProcessor: TestResultProcessor

    @Mock
    lateinit var testMatrixGenerator: TestMatrixGenerator

    @Mock
    lateinit var matrixRunTracker: TestMatrixRunProcessTracker

    @Mock
    lateinit var deviceFileManager: DeviceInfoFileManager

    @Mock
    lateinit var xmlHandler: TestResultsXmlHandler

    @Mock
    lateinit var device: TestDeviceData

    @Mock
    lateinit var staticTestData: StaticTestData

    @Mock
    lateinit var mockGeneratedMatrix: TestMatrix

    @Mock
    lateinit var mockUpdatedMatrix: TestMatrix

    @Mock
    lateinit var mockResultsMatrix: TestMatrix

    // files for test
    lateinit var testApkFile: File
    lateinit var testedApk: File
    lateinit var stubApk: File
    lateinit var fakeDeviceFile: File
    lateinit var resultsOutDir: File

    // Storage objects
    @Mock
    lateinit var mockTestApkStorage: StorageObject
    @Mock
    lateinit var mockTestedApkStorage: StorageObject
    @Mock
    lateinit var mockStubApk: StorageObject

    // Test Execution results
    @Mock
    lateinit var mockTestExecution: TestExecution
    @Mock
    lateinit var toolResultsStep: ToolResultsStep

    @Mock
    lateinit var mockStep: Step

    @Mock
    lateinit var mockTestExecutionStep: TestExecutionStep

    @Mock
    lateinit var mockUtpResult: TestSuiteResult

    @Mock
    lateinit var mockTestSuiteMerger: UtpTestSuiteResultMerger

    @Captor
    lateinit var requestInfoCaptor: ArgumentCaptor<ToolResultsManager.RequestInfo>

    @Before
    fun setup() {
        testApkFile = temporaryFolderRule.newFile("test_apk")
        testedApk = temporaryFolderRule.newFile("tested_apk")
        stubApk = temporaryFolderRule.newFile("stub")
        resultsOutDir = temporaryFolderRule.newFolder("out")
        fakeDeviceFile = temporaryFolderRule.newFile("device_file")

        projectSettings.apply {
            `when`(testHistoryName).thenReturn("test_history")
            `when`(name).thenReturn("my_project")
            `when`(storageBucket).thenReturn("bucket_name")
            `when`(stubAppApk).thenReturn(stubApk)
        }

        device.apply {
            `when`(name).thenReturn("device_name")
            `when`(apiLevel).thenReturn(28)
            `when`(locale).thenReturn(Locale.US)
        }

        staticTestData.apply {
            `when`(testApk).thenReturn(testApkFile)
            `when`(testedApkFinder).thenReturn { _ ->
                listOf(testedApk)
            }
        }

        storage.apply {
            `when`(retrieveOrUploadSharedFile(eq(testApkFile), any(), any(), any()))
                .thenReturn(mockTestApkStorage)
            `when`(retrieveOrUploadSharedFile(eq(testedApk), any(), any(), any()))
                .thenReturn(mockTestedApkStorage)
            `when`(retrieveOrUploadSharedFile(eq(stubApk), any(), any(), any()))
                .thenReturn(mockStubApk)
            `when`(testRunStorage(any(), any(), any())).thenReturn(mockTestRunStorage)
        }

        toolResults.apply {
            `when`(getOrCreateHistory(any(), any())).thenReturn("history_id")
        }

        // Test Matrices
        `when`(mockResultsMatrix.testExecutions).thenReturn(
            listOf(mockTestExecution)
        )
        `when`(testMatrixGenerator.createTestMatrix(
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )).thenReturn(mockGeneratedMatrix)

        `when`(testing.createTestMatrixRun(any(), any(), any())).thenReturn(mockUpdatedMatrix)

        `when`(matrixRunTracker.waitForTestResults(any(), any())).thenReturn(mockResultsMatrix)

        `when`(deviceFileManager.createFile(any(), any())).thenReturn(fakeDeviceFile)

        toolResultsStep.apply {
            `when`(projectId).thenReturn("project_id")
            `when`(historyId).thenReturn("history_id")
            `when`(executionId).thenReturn("esecution_id")
            `when`(stepId).thenReturn("stepId")
        }

        `when`(mockTestExecution.toolResultsStep).thenReturn(toolResultsStep)

        `when`(mockTestExecutionStep.testSuiteOverviews).thenReturn(listOf())
        `when`(mockStep.testExecutionStep).thenReturn(mockTestExecutionStep)

        `when`(toolResults.requestStep(any())).thenReturn(mockStep)

        mockUtpResult.apply {
            `when`(testStatus).thenReturn(TestStatus.PASSED)
            `when`(testResultList).thenReturn(listOf())
            `when`(hasPlatformError()).thenReturn(false)
        }

        testResultProcessor.apply {
            `when`(toUtpResult(
                    any(),
                    any(),
                    Mockito.any(), // null allowed
                    any(),
                    any(),
                    Mockito.any(), // null allowed
                    Mockito.any() // null allowed
                )
            ).thenReturn(mockUtpResult)
            `when`(toUtpResult(Mockito.any())).thenReturn(mockUtpResult)
        }

        `when`(mockTestSuiteMerger.result).thenReturn(mockUtpResult)
    }

    private fun getTestRunner(): TestRunner {
        return TestRunner(
            projectSettings,
            toolResults,
            testing,
            storage,
            testResultProcessor,
            testMatrixGenerator,
            matrixRunTracker,
            deviceFileManager,
            { mockTestSuiteMerger },
            { xmlHandler })
    }

    private fun verifyTestRun(
        expectedHistory: String = "test_history",
        expectedBucket: String = "bucket_name",
        // Allows us to test for atypical storage upload
        basicVerifyStorage: Boolean = true,
        // Allows us to test for atypical tool results handling
        basicVerifyToolResults: Boolean = true,
        // Allows us to test for atypical test result processing
        basicVerifyTestResultProcessor: Boolean = true,
        // Allows us to test for atypical result merger behavior
        basicVerifyResultMerger: Boolean = true,
        // Allows us to test for atypical test suite verification,
        basicVerifyTestResult: Boolean = true,
        // Allows us to test for atypical xml handler behavior.
        basicVerifySuiteOverview: Boolean = true,
        // Allows for different testedApk, such as the stub apk
        expectedAppApkStorageObject: StorageObject = mockTestedApkStorage,
        expectedIsStub: Boolean = false,
    ) {

        if (basicVerifyToolResults) {
            // ensure tool results
            inOrder(toolResults).also {
                // ensure history is created
                it.verify(toolResults).getOrCreateHistory("my_project", expectedHistory)

                // This will happen after the test is run in "handle test results"
                it.verify(toolResults).requestStep(capture(requestInfoCaptor))
                val requestInfo = requestInfoCaptor.getValue()
                it.verify(toolResults).requestThumbnails(requestInfo)
                it.verify(toolResults).requestTestCases(requestInfo)
                verifyNoMoreInteractions(toolResults)
            }
        }

        if (basicVerifyStorage) {
            // ensure testRunStorage creation and shared file upload.
            inOrder(storage).also {
                it.verify(storage).testRunStorage(any(), eq(expectedBucket), eq("history_id"))
                it.verify(storage).retrieveOrUploadSharedFile(
                    testApkFile,
                    expectedBucket,
                    "project",
                    "variant-test_apk"
                )
                it.verify(storage).retrieveOrUploadSharedFile(
                    testedApk,
                    expectedBucket,
                    "project",
                    "variant-tested_apk"
                )
                verifyNoMoreInteractions(storage)
            }
        }

        verify(testMatrixGenerator).createTestMatrix(
            device,
            staticTestData,
            mockTestRunStorage,
            mockTestApkStorage,
            expectedAppApkStorageObject,
            expectedIsStub
        )
        verifyNoMoreInteractions(testMatrixGenerator)

        verify(testing).createTestMatrixRun(
            eq("my_project"),
            eq(mockGeneratedMatrix),
            any()
        )
        verifyNoMoreInteractions(testing)

        verify(matrixRunTracker).waitForTestResults(
            "device_name",
            mockUpdatedMatrix
        )
        verifyNoMoreInteractions(matrixRunTracker)

        // device file should only every be created once (per run)
        verify(deviceFileManager).createFile(resultsOutDir, device)
        verifyNoMoreInteractions(deviceFileManager)

        if (basicVerifyTestResultProcessor) {
            verify(testResultProcessor).toUtpResult(
                resultsOutDir = resultsOutDir,
                executionStep = mockStep,
                thumbnails = null,
                testRunStorage = mockTestRunStorage,
                deviceInfoFile = fakeDeviceFile,
                testCases = null,
                invalidMatrixDetails = null
            )
            verifyNoMoreInteractions(testResultProcessor)
        }

        if (basicVerifyResultMerger) {
            inOrder(mockTestSuiteMerger).also {
                it.verify(mockTestSuiteMerger).merge(mockUtpResult)
                it.verify(mockTestSuiteMerger).result
                verifyNoMoreInteractions(mockTestSuiteMerger)
            }
        }

        if (basicVerifyTestResult) {
            verify(mockUtpResult).testStatus
            verify(mockUtpResult).testResultList
            verify(mockUtpResult).hasPlatformError()
            verify(mockUtpResult).writeTo(any<OutputStream>())
            verifyNoMoreInteractions(mockUtpResult)
        }

        if (basicVerifySuiteOverview) {
            verifyNoInteractions(mockTestRunStorage)
            verifyNoInteractions(xmlHandler)
        }
    }

    @Test
    fun test_runTests_simple() {
        val runner =  getTestRunner()

        val result = runner.runTests(
            device,
            staticTestData,
            resultsOutDir,
            "project",
            "variant"
        )

        verifyTestRun()

        assertThat(result).hasSize(1)
        assertThat(result[0].testPassed).isTrue()
        assertThat(result[0].resultsProto).isEqualTo(mockUtpResult)
    }

    @Test
    fun test_runTests_stubApk() {
        // When we don't supply a tested apk, a stub should be supplied.
        `when`(staticTestData.testedApkFinder).thenReturn { listOf() }

        val runner = getTestRunner()

        val result = runner.runTests(
            device,
            staticTestData,
            resultsOutDir,
            "project",
            "variant"
        )

        verifyTestRun(
            basicVerifyStorage = false,
            expectedAppApkStorageObject = mockStubApk,
            expectedIsStub = true
        )

        // verify storage manually:
        inOrder(storage).also {
            it.verify(storage).testRunStorage(any(), eq("bucket_name"), eq("history_id"))
            it.verify(storage).retrieveOrUploadSharedFile(
                testApkFile,
                "bucket_name",
                "project",
                "variant-test_apk"
            )
            it.verify(storage).retrieveOrUploadSharedFile(
                stubApk,
                "bucket_name",
                "shared",
                "stub"
            )
            verifyNoMoreInteractions(storage)
        }

        assertThat(result).hasSize(1)
        assertThat(result[0].testPassed).isTrue()
        assertThat(result[0].resultsProto).isEqualTo(mockUtpResult)
    }

    @Test
    fun test_runTests_testMatrixFailure() {
        // when the test ends, and there is no tool results. We have a matrix failure.
        `when`(mockTestExecution.toolResultsStep).thenReturn(null)
        `when`(mockResultsMatrix.invalidMatrixDetails).thenReturn("MALFORMED_APK")
        // This will cause the test result processor to create a failed test result.
        `when`(mockUtpResult.hasPlatformError()).thenReturn(true)

        val runner = getTestRunner()

        val result = runner.runTests(
            device,
            staticTestData,
            resultsOutDir,
            "project",
            "variant"
        )

        verifyTestRun(
            basicVerifyToolResults = false,
            basicVerifyTestResultProcessor = false
        )

        // no tool results step, means no calls to tool results to get test results.
        verify(toolResults).getOrCreateHistory("my_project", "test_history")
        verifyNoMoreInteractions(toolResults)

        verify(testResultProcessor).toUtpResult("MALFORMED_APK")
        verifyNoMoreInteractions(testResultProcessor)

        assertThat(result).hasSize(1)
        assertThat(result[0].testPassed).isFalse()
        assertThat(result[0].resultsProto).isEqualTo(mockUtpResult)
    }

    @Test
    fun test_runTests_testSuiteOverview() {
        val mockReference = mock<FileReference>().also {
            `when`(it.fileUri).thenReturn("some/long/fileUri")
        }
        val testSuiteOverview = mock<TestSuiteOverview>().also {
            `when`(it.xmlSource).thenReturn(mockReference)
            `when`(mockTestExecutionStep.testSuiteOverviews).thenReturn(listOf(it))
        }
        val fakeOverviewDownload = temporaryFolderRule.newFile("overview").also {
            `when`(mockTestRunStorage.downloadFromStorage(any(), any())).thenReturn(it)
        }

        val runner = getTestRunner()

        val result = runner.runTests(
            device,
            staticTestData,
            resultsOutDir,
            "project",
            "variant"
        )

        verifyTestRun(
            basicVerifySuiteOverview = false
        )

        verify(mockTestRunStorage).downloadFromStorage(eq("some/long/fileUri"), any())
        verifyNoMoreInteractions(mockTestRunStorage)

        verify(xmlHandler).updateXml(fakeOverviewDownload, "project", "variant")
        verifyNoMoreInteractions(xmlHandler)
    }
}
