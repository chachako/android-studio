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

package com.android.tools.firebase.testlab.gradle.services

import com.android.testutils.MockitoKt.any
import com.android.tools.firebase.testlab.gradle.services.ToolResultsManager.TestCases
import com.android.tools.firebase.testlab.gradle.services.storage.TestRunStorage
import com.google.api.client.googleapis.util.Utils
import com.google.api.client.json.JsonObjectParser
import com.google.api.services.toolresults.model.Step
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.proto.api.core.TestResultProto
import com.google.testing.platform.proto.api.core.TestStatusProto.TestStatus
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets

class TestResultProcessorTest {

    @get:Rule
    val mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    val parser: JsonObjectParser = JsonObjectParser(Utils.getDefaultJsonFactory())

    @Mock
    lateinit var mockStorage: TestRunStorage

    lateinit var processor: TestResultProcessor

    lateinit var resultsOutDir: File

    lateinit var deviceInfoFile: File

    @Before
    fun setup() {
        processor = TestResultProcessor(listOf())

        `when`(mockStorage.downloadFromStorage(any(), any())).thenAnswer {
            temporaryFolderRule.newFile()
        }

        resultsOutDir = temporaryFolderRule.newFolder("results")

        deviceInfoFile = temporaryFolderRule.newFile("device_info.pb")
    }

    @Test
    fun toUtpResult_testSingleCase() {
        val result = processor.toUtpResult(
            resultsOutDir,
            parseStep(buildStep()),
            null,
            mockStorage,
            deviceInfoFile,
            parseTestCases(testCases())
        )

        // General Validation
        assertThat(result.passed()).isTrue()

        // Validate the test cases
        assertThat(result.testResultList).hasSize(1)
        verifyTestResultsArtifacts(result.testResultList)
        // TODO(b/278583488): add verification for build artifacts.
    }

    @Test
    fun toUtpResult_testMultipleDevices() {
        val result = processor.toUtpResult(
                resultsOutDir,
                parseStep(buildStep()),
                null,
                mockStorage,
                deviceInfoFile,
                parseTestCases(multipleDeviceTestCases())
        )

        // General Validation
        assertThat(result.passed()).isTrue()

        // Validate the test cases
        assertThat(result.testResultList).hasSize(2)
        verifyTestResultsArtifacts(result.testResultList)
    }

    @Test
    fun toUtpResult_testSingleCaseFailure() {
        val result = processor.toUtpResult(
            resultsOutDir,
            parseStep(buildStep(summaryStatus = "failure")),
            null,
            mockStorage,
            deviceInfoFile,
            parseTestCases(testCases(testStatus = "failed"))
        )

        // General Validation
        assertThat(result.passed()).isFalse()

        // Validate the test cases
        assertThat(result.testResultList).hasSize(1)
        assertThat(result.testResultList[0].testStatus).isEqualTo(TestStatus.FAILED)
        verifyTestResultsArtifacts(result.testResultList)
        // TODO(b/278583488): add verification for build artifacts.
    }

    @Test
    fun toUtpResult_testSingleCaseUnspecifiedIsFailure() {
        val result = processor.toUtpResult(
            resultsOutDir,
            parseStep(buildStep(summaryStatus = "success")),
            null,
            mockStorage,
            deviceInfoFile,
            parseTestCases(testCases(testStatus = "unknown"))
        )

        // General Validation
        assertThat(result.passed()).isFalse()

        // Validate the test cases
        assertThat(result.testResultList).hasSize(1)
        assertThat(result.testResultList[0].testStatus)
            .isEqualTo(TestStatus.TEST_STATUS_UNSPECIFIED)
        verifyTestResultsArtifacts(result.testResultList)
        // TODO(b/278583488): add verification for build artifacts.
    }

    @Test
    fun toUtpResult_testSingleCasePlatformErrorCausesFailure() {
        val result = processor.toUtpResult(
            resultsOutDir,
            parseStep(buildStep(summaryStatus = "success")),
            null,
            mockStorage,
            deviceInfoFile,
            parseTestCases(testCases(testStatus = "passed")),
            "NO_SIGNATURE" // Matrix error message will cause the test result to fail.
        )

        // General Validation
        assertThat(result.passed()).isFalse()

        // Validate the test cases
        assertThat(result.testResultList).hasSize(1)
        assertThat(result.testResultList[0].testStatus).isEqualTo(TestStatus.PASSED)
        verifyTestResultsArtifacts(result.testResultList)
        // TODO(b/278583488): add verification for build artifacts.
    }

    private fun buildStep(
        summaryStatus: String = "success"
    ): String = """
        {
            "completionTime":{
                "nanos":577000000,
                "seconds":"1681521628"
            },
            "creationTime":{
                "nanos":596000000,
                "seconds":"1681521425"
            },
            "description":"all targets",
            "dimensionValue":[
                {"key":"Model","value":"Pixel2"},
                {"key":"Version","value":"30"},
                {"key":"Locale","value":"en_US"},
                {"key":"Orientation","value":"portrait"}
            ],
            "name":"Instrumentation test",
            "outcome":{"summary":"$summaryStatus"},
            "runDuration":{
                "nanos":981000000,
                "seconds":"202"
            },
            "state":"complete",
            "stepId":"test_step_id",
            "testExecutionStep":{
                "testIssues":[
                    {
                        "errorMessage":"Test is compatible with Android Test Orchestrator.",
                        "severity":"suggestion",
                        "type":"compatibleWithOrchestrator",
                        "category":"common"
                    }
                ],
                "testSuiteOverviews":[
                    {
                        "totalCount":1,
                        "xmlSource":{
                            "fileUri":"gs://test-lab/some_long_url/test_result_1.xml"
                        },
                        "elapsedTime":{
                            "nanos":69000000
                        }
                    }
                ],
                "testTiming":{
                    "testProcessDuration":{"seconds":"2"}
                },
                "toolExecution":{
                    "toolLogs":[
                        {"fileUri":"gs://test-lab/some_long_url/logcat"}
                    ],
                    "toolOutputs":[
                        {
                            "output":{
                                "fileUri":"gs://test-lab/some_long_url/test_result_1.xml"
                            }
                        },
                        {
                            "output":{
                                "fileUri":"gs://test-lab/some_long_url/instrumentation.results"
                            }
                        },
                        {
                            "output":{
                                "fileUri":"gs://test-lab/some_long_url/test_cases/0000_logcat"
                            },
                            "testCase":{
                                "className":"com.example.ftltest.ExampleInstrumentedTest",
                                "name":"useAppContext"
                            }
                        }
                    ]
                }
            }
        }
    """.trimIndent()

    private fun testCases(
        testStatus: String? = null
    ) = """
        {
            "testCases": [
                {
                    ${if (testStatus != null) "\"status\":\"$testStatus\"," else ""}
                    "testCaseReference": {
                        "name": "useAppContext",
                        "className": "com.example.ftltest.ExampleInstrumentedTest"
                    },
                    "testCaseId": "1",
                    "toolOutputs": [
                        {
                            "output": {
                                "fileUri": "gs://test-lab/some_long_url/test_cases/0000_logcat"
                            }
                        }
                    ],
                    "startTime": {
                        "seconds": "1681752153",
                        "nanos": 985000000
                    },
                    "endTime": {
                        "seconds": "1681752154",
                        "nanos": 24000000
                    },
                    "elapsedTime": {
                        "nanos": 39000000
                    }
                }
            ]
        }
    """.trimIndent()

    private fun multipleDeviceTestCases(
        testStatus: String? = null
    ) = """
        {
            "testCases": [
                {
                    ${if (testStatus != null) "\"status\":\"$testStatus\"," else ""}
                    "testCaseReference": {
                        "name": "useAppContext",
                        "className": "com.example.ftltest.ExampleInstrumentedTest"
                    },
                    "testCaseId": "1",
                    "toolOutputs": [
                        {
                            "output": {
                                "fileUri": "gs://test-lab/some_long_url/test_cases/0000_logcat"
                            }
                        }
                    ],
                    "startTime": {
                        "seconds": "1681752153",
                        "nanos": 985000000
                    },
                    "endTime": {
                        "seconds": "1681752154",
                        "nanos": 24000000
                    },
                    "elapsedTime": {
                        "nanos": 39000000
                    }
                },
                {
                    ${if (testStatus != null) "\"status\":\"$testStatus\"," else ""}
                    "testCaseReference": {
                        "name": "useAppContext",
                        "className": "com.example.ftltest.ExampleInstrumentedTest"
                    },
                    "testCaseId": "1",
                    "toolOutputs": [
                        {
                            "output": {
                                "fileUri": "gs://test-lab/some_long_url/test_cases/0001_logcat"
                            }
                        }
                    ],
                    "startTime": {
                        "seconds": "1681752423",
                        "nanos": 985000000
                    },
                    "endTime": {
                        "seconds": "1681752154",
                        "nanos": 24000000
                    },
                    "elapsedTime": {
                        "nanos": 39000000
                    }
                }
            ]
        }
    """.trimIndent()

    private fun parseStep(json: String): Step =
        ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8)).use { stream ->
            parser.parseAndClose<Step>(
                stream, StandardCharsets.UTF_8, Step::class.java
            )
        }

    private fun parseTestCases(json: String): TestCases =
        ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8)).use { stream ->
            parser.parseAndClose<TestCases>(
                stream, StandardCharsets.UTF_8, TestCases::class.java
            )
        }

    private fun verifyTestResultsArtifacts(testResultList: List<TestResultProto.TestResult>) {
        // Validate outputArtifact for logcat message and device info per test case
        testResultList.forEach { testResult ->
            val case = testResult.testCase
            assertThat(case.testPackage).isEqualTo("com.example.ftltest")
            assertThat(case.testClass).isEqualTo("ExampleInstrumentedTest")

            val artifactList = testResult.outputArtifactList
            assert(artifactList.any{ it.label.label == "device-info" && it.destinationPath != null })
            assert(artifactList.any{ it.label.label == "logcat" && it.destinationPath != null })
        }
    }
}
