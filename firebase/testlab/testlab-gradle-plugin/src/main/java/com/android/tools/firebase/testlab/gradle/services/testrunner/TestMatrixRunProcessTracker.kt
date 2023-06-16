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

import com.android.tools.firebase.testlab.gradle.services.TestingManager
import com.google.api.services.testing.model.TestMatrix
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging


class TestMatrixRunProcessTracker(
    private val testingManager: TestingManager,
    private val projectName: String,
    @VisibleForTesting
    private val checkTestStateWaitMs: Long = DEFAULT_CHECK_STATE_WAIT_MS,
    @VisibleForTesting
    private val logger: Logger = DEFAULT_LOGGER
) {

    companion object {
        const val DEFAULT_CHECK_STATE_WAIT_MS = 10 * 1000L

        val DEFAULT_LOGGER = Logging.getLogger(TestMatrixRunProcessTracker::class.java)
    }

    fun waitForTestResults(deviceName: String, testRunMatrix: TestMatrix): TestMatrix {
        var previousTestMatrixState = ""
        // Since, test matrices from FTL will never say they've entered the "RUNNING" state,
        // we check the progress messages to determine when the tests start running.
        var actualTestMatrixState = ""
        var printResultsUrl = true
        var currentProgressStatus = 0
        while (true) {
            val latestTestMatrix =
                testingManager.getTestMatrix(projectName, testRunMatrix)
            if (previousTestMatrixState != latestTestMatrix.state) {
                previousTestMatrixState = latestTestMatrix.state
                actualTestMatrixState = latestTestMatrix.state
                lifecycleExecution("state $actualTestMatrixState", deviceName)
            }
            latestTestMatrix.testExecutions.firstOrNull()?.testDetails?.apply {
                while (currentProgressStatus < progressMessages.size) {
                    val message = progressMessages[currentProgressStatus++]
                    if (message == "Starting Android test.") {
                        actualTestMatrixState = "RUNNING"
                    }
                    lifecycleExecution("$message", deviceName)
                }
            }
            if (printResultsUrl) {
                val resultsUrl = latestTestMatrix.resultStorage?.get("resultsUrl") as String?
                if (!resultsUrl.isNullOrBlank()) {
                    val resultDetailsUrl = if (resultsUrl.endsWith("details")) {
                        resultsUrl
                    } else {
                        "$resultsUrl/details"
                    }
                    logger.lifecycle(
                        "Test request for device $deviceName has been submitted to " +
                                "Firebase TestLab: $resultDetailsUrl")
                    printResultsUrl = false
                }
            }
            logger.info("Test execution: ${actualTestMatrixState}")
            val testFinished = when (latestTestMatrix.state) {
                "VALIDATING", "PENDING", "RUNNING" -> false
                else -> true
            }
            if (testFinished) {
                return latestTestMatrix
            }
            Thread.sleep(checkTestStateWaitMs)
        }
    }

    private fun lifecycleExecution(message: String, device: String) =
        logger.lifecycle("Firebase Testlab Test for $device: $message")
}
