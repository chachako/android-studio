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

import com.android.testutils.MockitoKt.mock
import com.android.tools.firebase.testlab.gradle.services.TestingManager
import com.google.api.services.testing.model.ResultStorage
import com.google.api.services.testing.model.TestDetails
import com.google.api.services.testing.model.TestExecution
import com.google.api.services.testing.model.TestMatrix
import org.gradle.api.logging.Logger
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

class TestMatrixRunProcessTrackerTest {

    @get:Rule
    val mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()

    @Mock
    lateinit var testing: TestingManager

    @Mock
    lateinit var testRunMatrix: TestMatrix

    @Mock
    lateinit var mockResultStorage: ResultStorage

    @Mock
    lateinit var logger: Logger

    lateinit var resultUri: String

    @Before
    fun setup() {
        resultUri = "path/to/the/results/details"

        `when`(mockResultStorage.get("resultsUrl")).thenAnswer { resultUri }
    }

    fun createTestMatrix(
        newState: String,
        progressMessageList: List<String> = listOf(),
        showStorage: Boolean = true
    ) = TestMatrix().apply {
        state = newState
        if (showStorage) {
            resultStorage = mockResultStorage
        }
        testExecutions = listOf(
            TestExecution().apply {
                testDetails = TestDetails().apply {
                    progressMessages = progressMessageList
                }
            }
        )
    }

    fun getMatrixRunTracker(
        projectName: String = "project"
    ): TestMatrixRunProcessTracker =
        TestMatrixRunProcessTracker(
            testing,
            projectName,
            0L,
            logger
        )

    @Test
    fun test_waitForTestResults() {
        lateinit var expectedMatrix: TestMatrix

        `when`(testing.getTestMatrix("project", testRunMatrix)).thenReturn(
            createTestMatrix(
                "FINISHED",
                listOf(
                    "Done. Test time = 23 (secs)",
                    "started results processing. Attempt 1",
                    "Completed results processing. Time taken = 3 (secs)"
                )
            ).apply {
                expectedMatrix = this
            }
        )

        val result = getMatrixRunTracker().waitForTestResults("device", testRunMatrix)

        assertThat(result).isSameInstanceAs(expectedMatrix)

        inOrder(logger).also {
            it.verify(logger).lifecycle("Firebase Testlab Test for device: state FINISHED")
            it.verify(logger).lifecycle(
                "Firebase Testlab Test for device: Done. Test time = 23 (secs)"
            )
            it.verify(logger).lifecycle(
                "Firebase Testlab Test for device: started results processing. Attempt 1"
            )
            it.verify(logger).lifecycle(
                "Firebase Testlab Test for device: Completed results processing. Time taken = 3 (secs)"
            )
            it.verify(logger).lifecycle(
                "Test request for device device has been submitted to Firebase TestLab: path/to/the/results/details"
            )
            it.verify(logger).info("Test execution: FINISHED")
            verifyNoMoreInteractions(logger)
        }
    }

    @Test
    fun test_waitForTestResults_alwaysGivesDetails() {
        lateinit var expectedMatrix: TestMatrix

        `when`(testing.getTestMatrix("project", testRunMatrix)).thenReturn(
            createTestMatrix(
                "FINISHED",
                listOf(
                    "Done. Test time = 23 (secs)",
                    "started results processing. Attempt 1",
                    "Completed results processing. Time taken = 3 (secs)"
                )
            ).apply {
                expectedMatrix = this
            }
        )
        resultUri = "results"

        val result = getMatrixRunTracker().waitForTestResults("device", testRunMatrix)

        assertThat(result).isSameInstanceAs(expectedMatrix)

        inOrder(logger).also {
            inOrder(logger).also {
                it.verify(logger).lifecycle("Firebase Testlab Test for device: state FINISHED")
                it.verify(logger).lifecycle(
                    "Firebase Testlab Test for device: Done. Test time = 23 (secs)"
                )
                it.verify(logger).lifecycle(
                    "Firebase Testlab Test for device: started results processing. Attempt 1"
                )
                it.verify(logger).lifecycle(
                    "Firebase Testlab Test for device: Completed results processing. Time taken = 3 (secs)"
                )
                it.verify(logger).lifecycle(
                    "Test request for device device has been submitted to Firebase TestLab: results/details"
                )
                it.verify(logger).info("Test execution: FINISHED")
                verifyNoMoreInteractions(logger)
            }
        }
    }

    @Test
    fun test_waitForTestResults_robust() {
        lateinit var expectedMatrix: TestMatrix

        `when`(testing.getTestMatrix("project", testRunMatrix)).thenReturn(
            createTestMatrix("VALIDATING", showStorage = false),
            createTestMatrix("VALIDATING", showStorage = false),
            createTestMatrix("VALIDATING", showStorage = false),
            createTestMatrix("PENDING"),
            createTestMatrix(
                "PENDING",
                listOf(
                    "Starting attempt 1.",
                    "Started logcat recording.",
                    "Preparing device.",
                    "Retrieving Performance Environment information from the device."
                )
            ),
            createTestMatrix(
                "PENDING",
                listOf(
                    "Starting attempt 1.",
                    "Started logcat recording.",
                    "Preparing device.",
                    "Retrieving Performance Environment information from the device.",
                    "Enabled network logging on device.",
                    "Setting up Android test.",
                    "Starting Android test.",
                    "Completed Android test.",
                    "Tearing down Android test.",
                    "Downloading network logs from device.",
                    "Stopped logcat recording.",
                )
            ),
            createTestMatrix(
                "FINISHED",
                listOf(
                    "Starting attempt 1.",
                    "Started logcat recording.",
                    "Preparing device.",
                    "Retrieving Performance Environment information from the device.",
                    "Enabled network logging on device.",
                    "Setting up Android test.",
                    "Starting Android test.",
                    "Completed Android test.",
                    "Tearing down Android test.",
                    "Downloading network logs from device.",
                    "Stopped logcat recording.",
                    "Done. Test time = 32 (secs)",
                    "Starting results processing. Attempt: 1",
                    "Completed results processing. Time taken = 5 (secs)"
                )
            ).apply {
                expectedMatrix = this
            }
        )

        val result = getMatrixRunTracker().waitForTestResults("different", testRunMatrix)

        assertThat(result).isSameInstanceAs(expectedMatrix)

        inOrder(logger).also {
            it.verify(logger).lifecycle("Firebase Testlab Test for different: state VALIDATING")
            it.verify(logger, times(3)).info("Test execution: VALIDATING")

            // first PENDING matrix
            it.verify(logger).lifecycle("Firebase Testlab Test for different: state PENDING")
            it.verify(logger).lifecycle(
                "Test request for device different has been submitted to Firebase TestLab: path/to/the/results/details"
            )
            it.verify(logger).info("Test execution: PENDING")
            it.verify(logger).lifecycle("Firebase Testlab Test for different: Starting attempt 1.")
            it.verify(logger).lifecycle(
                "Firebase Testlab Test for different: Started logcat recording."
            )
            it.verify(logger).lifecycle("Firebase Testlab Test for different: Preparing device.")
            it.verify(logger).lifecycle(
                "Firebase Testlab Test for different: Retrieving Performance Environment information from the device."
            )
            it.verify(logger).info("Test execution: PENDING")

            // second PENDING matrix
            it.verify(logger).lifecycle(
                "Firebase Testlab Test for different: Enabled network logging on device."
            )
            it.verify(logger).lifecycle(
                "Firebase Testlab Test for different: Setting up Android test."
            )
            it.verify(logger).lifecycle(
                "Firebase Testlab Test for different: Starting Android test."
            )
            it.verify(logger).lifecycle(
                "Firebase Testlab Test for different: Completed Android test."
            )
            it.verify(logger).lifecycle(
                "Firebase Testlab Test for different: Tearing down Android test."
            )
            it.verify(logger).lifecycle(
                "Firebase Testlab Test for different: Downloading network logs from device."
            )
            it.verify(logger).lifecycle(
                "Firebase Testlab Test for different: Stopped logcat recording."
            )
            it.verify(logger).info("Test execution: RUNNING")

            // finished matrix
            it.verify(logger).lifecycle("Firebase Testlab Test for different: state FINISHED")
            it.verify(logger).lifecycle(
                "Firebase Testlab Test for different: Done. Test time = 32 (secs)"
            )
            it.verify(logger).lifecycle(
                "Firebase Testlab Test for different: Starting results processing. Attempt: 1"
            )
            it.verify(logger).lifecycle(
                "Firebase Testlab Test for different: Completed results processing. Time taken = 5 (secs)"
            )
            it.verify(logger).info("Test execution: FINISHED")
            verifyNoMoreInteractions(logger)
        }
    }
}
