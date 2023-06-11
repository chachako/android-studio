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
    lateinit var finishedMatrix: TestMatrix

    @Mock
    lateinit var mockResultStorage: ResultStorage

    @Mock
    lateinit var logger: Logger

    lateinit var resultUri: String

    @Before
    fun setup() {
        resultUri = "path/to/the/results/details"

        finishedMatrix.apply {
            `when`(state).thenReturn("FINISHED")
            `when`(resultStorage).thenReturn(mockResultStorage)
        }

        `when`(mockResultStorage.get("resultsUrl")).thenAnswer { resultUri }
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
        `when`(testing.getTestMatrix("project", testRunMatrix)).thenReturn(finishedMatrix)

        val result = getMatrixRunTracker().waitForTestResults("device", testRunMatrix)

        assertThat(result).isSameInstanceAs(finishedMatrix)

        inOrder(logger).also {
            it.verify(logger).lifecycle("Firebase TestLab Test execution state: FINISHED")
            it.verify(logger).lifecycle(
                "Test request for device device has been submitted to Firebase TestLab: path/to/the/results/details"
            )
            it.verify(logger).info("Test execution: FINISHED")
            verifyNoMoreInteractions(logger)
        }
    }

    @Test
    fun test_waitForTestResults_alwaysGivesDetails() {
        `when`(testing.getTestMatrix("project", testRunMatrix)).thenReturn(finishedMatrix)
        resultUri = "results"

        val result = getMatrixRunTracker().waitForTestResults("device", testRunMatrix)

        assertThat(result).isSameInstanceAs(finishedMatrix)

        inOrder(logger).also {
            it.verify(logger).lifecycle("Firebase TestLab Test execution state: FINISHED")
            // "/details" will be appended to the url
            it.verify(logger).lifecycle(
                "Test request for device device has been submitted to Firebase TestLab: results/details"
            )
            it.verify(logger).info("Test execution: FINISHED")
            verifyNoMoreInteractions(logger)
        }
    }

    @Test
    fun test_waitForTestResults_robust() {
        val validatingMatrix = mock<TestMatrix>().apply {
            `when`(state).thenReturn("VALIDATING")
        }
        val pendingMatrix = mock<TestMatrix>().apply {
            `when`(state).thenReturn("PENDING")
        }
        // Although, how TestMatrix doesn't have a "running" state
        // instead it is based on test cases, it is shown here to represent when the url
        // starts becoming available.
        val runningMatrix = mock<TestMatrix>().apply {
            `when`(state).thenReturn("RUNNING")
            `when`(resultStorage).thenReturn(mockResultStorage)
        }

        `when`(testing.getTestMatrix("project", testRunMatrix)).thenReturn(
            validatingMatrix,
            validatingMatrix,
            validatingMatrix,
            pendingMatrix,
            runningMatrix,
            runningMatrix,
            finishedMatrix
        )

        val result = getMatrixRunTracker().waitForTestResults("different", testRunMatrix)

        assertThat(result).isSameInstanceAs(finishedMatrix)

        inOrder(logger).also {
            it.verify(logger).lifecycle("Firebase TestLab Test execution state: VALIDATING")
            it.verify(logger, times(3)).info("Test execution: VALIDATING")

            it.verify(logger).lifecycle("Firebase TestLab Test execution state: PENDING")
            it.verify(logger).info("Test execution: PENDING")

            it.verify(logger).lifecycle("Firebase TestLab Test execution state: RUNNING")
            it.verify(logger).lifecycle(
                "Test request for device different has been submitted to Firebase TestLab: path/to/the/results/details"
            )
            it.verify(logger, times(2)).info("Test execution: RUNNING")

            it.verify(logger).lifecycle("Firebase TestLab Test execution state: FINISHED")
            it.verify(logger).info("Test execution: FINISHED")
            verifyNoMoreInteractions(logger)
        }
    }
}
