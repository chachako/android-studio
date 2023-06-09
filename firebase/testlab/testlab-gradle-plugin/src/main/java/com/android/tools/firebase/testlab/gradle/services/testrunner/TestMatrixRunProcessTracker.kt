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
import org.gradle.api.logging.Logging


class TestMatrixRunProcessTracker(
    private val testingManager: TestingManager,
    private val projectName: String,
    private val checkTestStateWaitMs: Long = DEFAULT_CHECK_STATE_WAIT_MS
) {

    private val logger = Logging.getLogger(this.javaClass)

    companion object {
        const val DEFAULT_CHECK_STATE_WAIT_MS = 10 * 1000L
    }

    fun waitForTestResults(deviceName: String, testRunMatrix: TestMatrix): TestMatrix {
        var previousTestMatrixState = ""
        var printResultsUrl = true
        while (true) {
            val latestTestMatrix =
                testingManager.getTestMatrix(projectName, testRunMatrix)
            if (previousTestMatrixState != latestTestMatrix.state) {
                previousTestMatrixState = latestTestMatrix.state
                logger.lifecycle("Firebase TestLab Test execution state: $previousTestMatrixState")
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
            val testFinished = when (latestTestMatrix.state) {
                "VALIDATING", "PENDING", "RUNNING" -> false
                else -> true
            }
            logger.info("Test execution: ${latestTestMatrix.state}")
            if (testFinished) {
                return latestTestMatrix
            }
            Thread.sleep(checkTestStateWaitMs)
        }
    }
}
