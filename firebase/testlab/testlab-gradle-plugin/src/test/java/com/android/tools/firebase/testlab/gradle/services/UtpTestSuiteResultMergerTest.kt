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

package com.android.tools.firebase.testlab.gradle.services

import com.android.tools.firebase.testlab.gradle.UtpTestSuiteResultMerger
import com.google.common.truth.Truth
import com.google.protobuf.TextFormat
import com.google.testing.platform.proto.api.core.TestStatusProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import org.junit.Test

/**
 * Unit tests for [UtpTestSuiteResultMerger].
 */
class UtpTestSuiteResultMergerTest {
    private fun merge(vararg results: TestSuiteResultProto.TestSuiteResult): TestSuiteResultProto.TestSuiteResult {
        val merger = UtpTestSuiteResultMerger()
        results.forEach(merger::merge)
        return merger.result
    }

    private val passedResult = TextFormat.parse("""
            test_suite_meta_data {
              scheduled_test_case_count: 1
            }
            test_status: PASSED
            test_result {
              test_case {
                test_class: "ExamplePassedInstrumentedTest"
                test_package: "com.example.application"
                test_method: "useAppContext"
              }
              test_status: PASSED
            }
        """.trimIndent(), TestSuiteResultProto.TestSuiteResult::class.java)

    private val skippedResult = TextFormat.parse("""
            test_suite_meta_data {
              scheduled_test_case_count: 1
            }
            test_status: SKIPPED
            test_result {
              test_case {
                test_class: "ExampleSkippedInstrumentedTest"
                test_package: "com.example.application"
                test_method: "useAppContext"
              }
              test_status: SKIPPED
            }
        """.trimIndent(), TestSuiteResultProto.TestSuiteResult::class.java)

    private val failedResult = TextFormat.parse("""
            test_suite_meta_data {
              scheduled_test_case_count: 1
            }
            test_status: FAILED
            test_result {
              test_case {
                test_class: "ExampleFailedInstrumentedTest"
                test_package: "com.example.application"
                test_method: "useAppContext"
              }
              test_status: FAILED
            }
        """.trimIndent(), TestSuiteResultProto.TestSuiteResult::class.java)

    @Test
    fun mergeZeroResults() {
        Truth.assertThat(merge()).isEqualTo(TestSuiteResultProto.TestSuiteResult.getDefaultInstance())
    }

    @Test
    fun mergePassedAndFailedResults() {
        val mergedResult = merge(passedResult, failedResult)

        Truth.assertThat(mergedResult.testStatus).isEqualTo(TestStatusProto.TestStatus.FAILED)
        Truth.assertThat(mergedResult.testSuiteMetaData.scheduledTestCaseCount).isEqualTo(2)
        Truth.assertThat(mergedResult.testResultList).hasSize(2)
    }

    @Test
    fun mergePassedAndSkippedResults() {
        val mergedResult = merge(passedResult, skippedResult)

        Truth.assertThat(mergedResult.testStatus).isEqualTo(TestStatusProto.TestStatus.PASSED)
        Truth.assertThat(mergedResult.testSuiteMetaData.scheduledTestCaseCount).isEqualTo(2)
        Truth.assertThat(mergedResult.testResultList).hasSize(2)
    }
}
