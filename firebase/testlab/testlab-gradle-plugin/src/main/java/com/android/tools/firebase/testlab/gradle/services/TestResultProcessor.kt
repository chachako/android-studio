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

import com.android.tools.firebase.testlab.gradle.services.ToolResultsManager.TestCases
import com.google.api.services.toolresults.model.FileReference
import com.google.api.services.toolresults.model.Image
import com.google.api.services.toolresults.model.Step
import com.google.api.services.toolresults.model.TestIssue
import com.google.api.services.toolresults.model.ToolOutputReference
import com.google.testing.platform.proto.api.core.ErrorProto.Error
import com.google.testing.platform.proto.api.core.IssueProto.Issue
import com.google.testing.platform.proto.api.core.LabelProto.Label
import com.google.testing.platform.proto.api.core.PathProto.Path
import com.google.testing.platform.proto.api.core.TestArtifactProto.Artifact
import com.google.testing.platform.proto.api.core.TestArtifactProto.ArtifactType
import com.google.testing.platform.proto.api.core.TestCaseProto
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestStatusProto.TestStatus
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteMetaData
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import org.gradle.api.logging.Logging
import java.io.File

/**
 * Processes the test result form Firebase TestLab and stores the results in the resultsDir.
 *
 * @param directoriesToPull: List of directories to pull
 */
class TestResultProcessor(
    private val directoriesToPull: List<String>
) {

    private val logger = Logging.getLogger(this.javaClass)

    /**
     * Creates a valid TestSuiteResult for a successfully run test.
     *
     * @param executionStep: the step containing all information regarding the test run. Retrieved
     * from [ToolResults]
     * @param thumbnails list of all thumbnails associated with this test run
     * @param testRunStorage the storage handler associated with this test run. Used to download
     *     output artifacts for test results
     * @param testCases: list of test cases manually called from FTL Api
     */
    fun toUtpResult(
        resultsOutDir: File,
        executionStep: Step,
        thumbnails: List<Image>?,
        testRunStorage: TestRunStorage,
        deviceInfoFile: File,
        testCases: TestCases?,
        invalidMatrixDetails: String? = null
    ) : TestSuiteResult =
        TestSuiteResult.newBuilder().apply {

            testStatus = testStatusFromSummary(executionStep.outcome.summary)

            // add Artifacts and Data from the test suit overviews.
            executionStep.testExecutionStep.testSuiteOverviews.also { overviews ->
                testSuiteMetaData = TestSuiteMetaData.newBuilder().apply {
                    testSuiteName = executionStep.name
                    scheduledTestCaseCount = overviews?.sumOf {
                        it?.totalCount ?: 0
                    } ?: 0
                }.build()

                overviews?.get(0)?.xmlSource?.also { xml ->
                    addTestResultXmlFileArtifact(this, xml)
                }
            }

            // add Artifacts from toolExecution
            executionStep.testExecutionStep.toolExecution?.also { toolExecution ->
                addToolLogArtifacts(this, toolExecution.toolLogs ?: listOf())

                addToolOutputArtifacts(
                    this,
                    toolExecution.toolOutputs ?: listOf(),
                    testRunStorage,
                    resultsOutDir)
            }

            // add thumbnail artifacts
            thumbnails?.also {
                addThumbnailOutputArtifacts(this, it)
            }

            // add testIssues data
            executionStep.testExecutionStep?.testIssues?.also { issues ->
                addTestIssues(this, issues)
            }

            // add Test Case data.
            testCases?.also { cases ->
                addTestCaseArtifacts(
                    this,
                    cases,
                    testRunStorage,
                    resultsOutDir,
                    deviceInfoFile)
            }

            // Lastly add invalid matrix data, if any.
            invalidMatrixDetails?.also { details ->
                addInvalidMatrixDetails(this, details)
            }
        }.build()

    /**
     * Creates a TestSuiteResult from an invalid matrix detail
     */
    fun toUtpResult(
        invalidMatrixDetails: String?
    ): TestSuiteResult =
        TestSuiteResult.newBuilder().apply {
            invalidMatrixDetails?.also { details ->
                addInvalidMatrixDetails(this, details)
            }
        }.build()

    private fun addTestResultXmlFileArtifact(
        builder: TestSuiteResult.Builder,
        xmlSource: FileReference
    ) {
        builder.apply {
            xmlSource.fileUri?.also { path ->
                if (path.isNotBlank()) {
                    addOutputArtifact(
                        label = "firebase.xmlSource",
                        path = path
                    )
                }
            }
        }
    }

    private fun addToolLogArtifacts(
        builder: TestSuiteResult.Builder,
        logs: List<FileReference>
    ) {
        builder.apply {
            logs.forEach { log ->
                addOutputArtifact(
                    label = "firebase.toolLog",
                    path = log.fileUri,
                    mimeType = "text/plain"
                )
            }
        }
    }

    private fun addToolOutputArtifacts(
        builder: TestSuiteResult.Builder,
        toolOutputs: List<ToolOutputReference>,
        testRunStorage: TestRunStorage,
        resultsOutDir: File
    ) {
        builder.apply {
            toolOutputs.forEach { toolOutput ->
                val fileUri = toolOutput.output?.fileUri
                if (fileUri != null) {
                    // If the outputs has test cases, this will be handled separately.
                    if (toolOutput.testCase == null) {
                        addOutputArtifact(
                            label = "firebase.toolOutput",
                            path = toolOutput.output.fileUri
                        )
                    }
                    // Need to download the output if it is requested by the dsl.
                    val shouldDownload = directoriesToPull.any { directory ->
                        fileUri.contains(directory)
                    }
                    if (shouldDownload) {
                        val downloadedFile = testRunStorage.downloadFromStorage(fileUri) {
                            File(resultsOutDir, it)
                        } ?: return@forEach
                        addOutputArtifact(
                            label = "firebase.toolOutput",
                            path = downloadedFile.path
                        )
                    }
                }
            }
        }
    }

    private fun addThumbnailOutputArtifacts(
        builder: TestSuiteResult.Builder,
        thumbnails: List<Image>
    ) {
        builder.apply {
            thumbnails.forEach { thumbnail ->
                addOutputArtifact(
                    label = "firebase.thumbnail",
                    path = thumbnail.sourceImage.output.fileUri,
                    mimeType = "image/jpeg"
                )
            }
        }
    }

    private fun addTestIssues(
        builder: TestSuiteResult.Builder,
        testIssues: List<TestIssue>
    ) {
        builder.apply {
            testIssues.forEach { issue ->
                addIssue(Issue.newBuilder().apply {
                    message = issue.errorMessage
                    name = issue["type"]?.toString()
                    namespace = Label.newBuilder().apply {
                        label = "firebase.issue"
                        namespace = "android"
                    }.build()
                    severity = when (issue["severity"]) {
                        "info" -> Issue.Severity.INFO
                        "suggestion" -> Issue.Severity.SUGGESTION
                        "warning" -> Issue.Severity.WARNING
                        "severe" -> Issue.Severity.SEVERE
                        else -> Issue.Severity.SEVERITY_UNSPECIFIED
                    }
                    code = issue["type"]?.toString()?.hashCode() ?: 0
                }.build())
            }
        }
    }

    private fun addTestCaseArtifacts(
        builder: TestSuiteResult.Builder,
        testCases: TestCases,
        testRunStorage: TestRunStorage,
        resultsOutDir: File,
        deviceInfoFile: File
    ) {
        builder.apply {
            testCases.testCases?.forEach { case ->
                addTestResult(TestResult.newBuilder().apply {
                    val qualifiedName = case.testCaseReference?.className
                    val testMethod = case.testCaseReference?.name
                    if (testMethod != null || qualifiedName != null) {
                        testCase = TestCaseProto.TestCase.newBuilder().apply {
                            if (qualifiedName != null) {
                                testClass = qualifiedName.split(".")?.last()
                                testPackage = qualifiedName.dropLast(testClass.length + 1)
                            }
                            this.testMethod = testMethod
                            startTimeBuilder.apply {
                                seconds = case.startTime?.seconds?.toLong() ?: 0
                                nanos = case.startTime?.nanos?.toInt() ?: 0
                            }
                            endTimeBuilder.apply {
                                seconds = case.endTime?.seconds?.toLong() ?: 0
                                nanos = case.endTime?.nanos?.toInt() ?: 0
                            }
                        }.build()
                        testStatus = testStatusFromTestCase(case.status)

                        if (testStatus == TestStatus.FAILED || testStatus == TestStatus.ERROR) {
                            error = Error.newBuilder().apply {
                                case.stackTraces?.get(0)?.exception?.also { trace ->
                                    stackTrace = trace
                                }
                            }.build()
                        }

                        // download the logcat files.
                        case.toolOutputs?.asSequence()?.map { output ->
                            output.output?.fileUri ?: ""
                        }?.filter { uri ->
                            uri.endsWith("logcat")
                        }?.forEach { uri ->
                            testRunStorage.downloadFromStorage(uri) {
                                File(resultsOutDir, it)
                            }?.also { file ->
                                addOutputArtifact(
                                    label = "logcat",
                                    path = file.path
                                )
                            }
                        }

                        // add device info for the test result.
                        addOutputArtifact(
                            label = "device-info",
                            path = deviceInfoFile.path
                        )
                    }
                }.build())
            }
        }
    }

    private fun addInvalidMatrixDetails(
        builder: TestSuiteResult.Builder,
        invalidMatrixDetails: String
    ) {
        if (invalidMatrixDetails.isNotBlank()) {
            builder.apply {
                platformErrorBuilder.addErrorsBuilder().apply {
                    summaryBuilder.apply {
                        errorName = invalidMatrixDetails
                        errorCode = invalidMatrixDetails.hashCode()
                        errorMessage =
                            getInvalidMatrixDetailsErrorMessage(invalidMatrixDetails)
                        namespaceBuilder.apply {
                            label = "firebase.invalidMatrixDetails"
                            namespace = "android"
                        }
                    }
                    logger.warn(summaryBuilder.errorMessage)
                }
            }
        }
    }

    private fun testStatusFromSummary(firebaseTestSummary: String?) = when (firebaseTestSummary) {
        "success" -> TestStatus.PASSED
        "failure" -> TestStatus.FAILED
        "skipped" -> TestStatus.SKIPPED
        else -> TestStatus.TEST_STATUS_UNSPECIFIED
    }

    private fun testStatusFromTestCase(caseStatus: String?): TestStatus {
        if (caseStatus == null) {
            return TestStatus.PASSED
        }
        return when (caseStatus) {
            null -> TestStatus.PASSED
            "passed" -> TestStatus.PASSED
            "failed" -> TestStatus.FAILED
            "error" -> TestStatus.ERROR
            "skipped" -> TestStatus.SKIPPED
            else -> TestStatus.TEST_STATUS_UNSPECIFIED
        }
    }
}

private fun TestSuiteResult.Builder.addOutputArtifact(
    label: String,
    path: String,
    mimeType: String? = null
) = addOutputArtifact(
    Artifact.newBuilder().apply {
        this.label = Label.newBuilder().apply {
            this.label = label
            namespace = "android"
        }.build()
        sourcePath = Path.newBuilder().apply {
            this.path = path
        }.build()
        type = ArtifactType.TEST_DATA
        mimeType?.also {
            this.mimeType = it
        }
    }.build()
)

internal fun TestSuiteResult.passed(): Boolean {
    val suitePassed = testStatus.isPassedOrSkipped()
    val allTestsPass = testResultList.all { case ->
        case.testStatus.isPassedOrSkipped()
    }
    return suitePassed && allTestsPass && !hasPlatformError()
}

private fun TestStatus.isPassedOrSkipped(): Boolean {
    return when (this) {
        TestStatus.PASSED,
        TestStatus.IGNORED,
        TestStatus.SKIPPED -> true
        else -> false
    }
}
