/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.firebase.testlab.gradle

import com.google.testing.platform.proto.api.core.PlatformErrorProto
import com.google.testing.platform.proto.api.core.TestStatusProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto

/**
 * Merges multiple test suite results into a single test suite result proto message.
 */
// TODO: This class is copied from com.android.build.gradle.internal.testing.utp package. Consider
//   to move this class into UTP library to share the same code.
class UtpTestSuiteResultMerger {
    private val builder: TestSuiteResultProto.TestSuiteResult.Builder = TestSuiteResultProto.TestSuiteResult.newBuilder()

    /**
     * Returns the merged test suite result.
     */
    val result: TestSuiteResultProto.TestSuiteResult
        get() = builder.build()

    /**
     * Merges a given test suite result.
     */
    fun merge(testSuiteResult: TestSuiteResultProto.TestSuiteResult) {
        mergeTestSuiteMetaData(testSuiteResult.testSuiteMetaData)
        mergeTestStatus(testSuiteResult.testStatus)
        builder.addAllTestResult(testSuiteResult.testResultList)
        mergePlatformError(testSuiteResult.platformError)
        builder.addAllOutputArtifact(testSuiteResult.outputArtifactList)
        builder.addAllIssue(testSuiteResult.issueList)
    }

    private fun mergePlatformError(platformError: PlatformErrorProto.PlatformError) {
        builder.platformErrorBuilder.addAllErrors(platformError.errorsList)
    }

    private fun mergeTestSuiteMetaData(metadata: TestSuiteResultProto.TestSuiteMetaData) {
        metadata.testSuiteName.let {
            if (it.isNotBlank()) {
                builder.testSuiteMetaDataBuilder.testSuiteName = it
            }
        }
        builder.testSuiteMetaDataBuilder.scheduledTestCaseCount += metadata.scheduledTestCaseCount
    }

    private fun mergeTestStatus(testStatus: TestStatusProto.TestStatus) {
        builder.testStatus = when(builder.testStatus) {
            TestStatusProto.TestStatus.TEST_STATUS_UNSPECIFIED,
            TestStatusProto.TestStatus.UNRECOGNIZED,
            TestStatusProto.TestStatus.SKIPPED,
            TestStatusProto.TestStatus.IGNORED,
            TestStatusProto.TestStatus.IN_PROGRESS,
            TestStatusProto.TestStatus.STARTED -> {
                testStatus
            }
            TestStatusProto.TestStatus.PASSED -> {
                when(testStatus) {
                    TestStatusProto.TestStatus.TEST_STATUS_UNSPECIFIED,
                    TestStatusProto.TestStatus.UNRECOGNIZED,
                    TestStatusProto.TestStatus.SKIPPED,
                    TestStatusProto.TestStatus.IGNORED,
                    TestStatusProto.TestStatus.STARTED-> {
                        builder.testStatus
                    }
                    else -> {
                        testStatus
                    }
                }
            }
            TestStatusProto.TestStatus.FAILED -> {
                builder.testStatus
            }
            TestStatusProto.TestStatus.ERROR,
            TestStatusProto.TestStatus.ABORTED,
            TestStatusProto.TestStatus.CANCELLED -> {
                testStatus
            }
        }
    }
}
