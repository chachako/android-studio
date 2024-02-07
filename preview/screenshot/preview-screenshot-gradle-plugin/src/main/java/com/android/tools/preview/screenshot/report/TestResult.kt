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

package com.android.tools.preview.screenshot.report

import org.gradle.api.tasks.testing.TestResult.ResultType

/**
 * Custom test result based on Gradle's TestResult
 */
class TestResult(
    val name: String,
    override val duration: Long,
    val project: String,
    private val flavor: String,
    var screenshotImages: ScreenshotTestImages?,
    val classResults: ClassTestResults) : TestResultModel(), Comparable<TestResult> {


    val failures: MutableList<TestFailure> = ArrayList()
    private var ignored = false

    val id: Any
        get() = name

    override val title = String.format("Test %s", name)

    override fun getResultType(): ResultType {
        if (ignored) {
            return ResultType.SKIPPED
        }
        return if (failures.isEmpty()) ResultType.SUCCESS else ResultType.FAILURE
    }

    override fun getFormattedDuration(): String {
        return if (ignored) "-" else super.getFormattedDuration()
    }

    fun addFailure(
        message: String, stackTrace: String, projectName: String, flavorName: String
    ) {
        classResults.failed(this, projectName, flavorName)
        failures.add(
            TestFailure(
                message,
                stackTrace,
                null
            )
        )
    }

    fun ignored(projectName: String, flavorName: String) {
        ignored = true
        classResults.skipped(projectName, flavorName)
    }

    override fun compareTo(other: TestResult): Int {
        var diff: Int = classResults.name.compareTo(other.classResults.name)
        if (diff != 0) {
            return diff
        }
        diff = name.compareTo(other.name)
        if (diff != 0) {
            return diff
        }
        diff = flavor.compareTo(other.flavor)
        if (diff != 0) {
            return diff
        }
        val thisIdentity = System.identityHashCode(this)
        val otherIdentity = System.identityHashCode(other)
        return thisIdentity.compareTo(otherIdentity)
    }

    data class TestFailure(val message: String, val stackTrace: String?, val exceptionType: String?)

}
