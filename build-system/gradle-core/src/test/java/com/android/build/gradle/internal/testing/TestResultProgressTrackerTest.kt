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

package com.android.build.gradle.internal.testing

import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Message
import com.google.protobuf.TextFormat
import org.gradle.api.logging.Logger
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.util.Timer

class TestResultProgressTrackerTest  {

    private var capturedDelay: Long? = null
    private var capturedAction: (() -> Unit)? = null

    @get:Rule
    var mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()

    @Mock
    lateinit var mockTimer: Timer

    @Mock
    lateinit var mockLogger: Logger

    @Before
    fun setup() {
        capturedDelay = null
        capturedAction = null
    }

    private fun timerFactory(delay: Long, action: () -> Unit): Timer {

        capturedDelay = delay
        capturedAction = action

        return mockTimer;
    }


    @Test
    fun testSuiteStatus_getStatus() {

        val status = TestResultProgressTracker.TestSuiteStatus("myDevice")

        status.scheduleTests(10)

        assertThat(status.getStatus()).isEqualTo(
            "myDevice Tests 0/10 completed. (0 skipped) (0 failed)"
        )

        status.addCompletedTest()
        assertThat(status.getStatus()).isEqualTo(
            "myDevice Tests 1/10 completed. (0 skipped) (0 failed)"
        )

        status.addSkippedTest()
        assertThat(status.getStatus()).isEqualTo(
            "myDevice Tests 2/10 completed. (1 skipped) (0 failed)"
        )

        status.addFailedTest()
        status.addFailedTest()
        assertThat(status.getStatus()).isEqualTo(
            "myDevice Tests 4/10 completed. (1 skipped) (2 failed)"
        )
    }

    @Test
    fun onTestSuiteStarted_startsTimer() {
        val progressTracker = TestResultProgressTracker(
            "testDevice",
            200L,
            mockLogger,
            ::timerFactory
        )

        assertThat(capturedDelay).isEqualTo(null)

        progressTracker.onTestSuiteStarted(10)

        assertThat(capturedDelay).isEqualTo(200L)
        assertThat(progressTracker.status.getStatus()).isEqualTo(
            "testDevice Tests 0/10 completed. (0 skipped) (0 failed)"
        )
    }

    @Test
    fun onTestSuiteFinished_cancelsTimer() {
        val progressTracker = TestResultProgressTracker(
            "testDevice",
            200L,
            mockLogger,
            ::timerFactory
        )

        progressTracker.onTestSuiteStarted(1)

        verify(mockTimer, never()).cancel()

        progressTracker.onTestSuiteFinished()

        verify(mockTimer, times(1)).cancel()
    }

    @Test
    fun logStatus_verifyTimerAction() {
        val progressTracker = TestResultProgressTracker(
            "testDevice",
            200L,
            mockLogger,
            ::timerFactory
        )

        progressTracker.onTestSuiteStarted(4)

        progressTracker.onTestPassed()

        // should log the test status.
        capturedAction!!.invoke()

        progressTracker.onTestFailed()

        capturedAction!!.invoke()

        progressTracker.onTestSkipped()

        capturedAction!!.invoke()

        progressTracker.onTestPassed()

        capturedAction!!.invoke()

        progressTracker.onTestSuiteFinished()

        inOrder(mockLogger, mockTimer).also {
            it.verify(mockLogger).lifecycle(
                "testDevice Tests 1/4 completed. (0 skipped) (0 failed)"
            )
            it.verify(mockLogger).lifecycle(
                "testDevice Tests 2/4 completed. (0 skipped) (1 failed)"
            )
            it.verify(mockLogger).lifecycle(
                "testDevice Tests 3/4 completed. (1 skipped) (1 failed)"
            )
            it.verify(mockLogger).lifecycle(
                "testDevice Tests 4/4 completed. (1 skipped) (1 failed)"
            )
            it.verify(mockTimer).cancel()
            it.verify(mockLogger).lifecycle(
                "Finished 4 tests on testDevice"
            )
        }
    }
}
