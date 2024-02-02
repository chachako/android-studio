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

abstract class TestResultModel {
    abstract val duration: Long
    abstract val title: String

    val statusClass: String
        get() = when (getResultType()) {
            ResultType.SUCCESS -> "success"
            ResultType.FAILURE -> "failures"
            ResultType.SKIPPED -> "skipped"
            else -> throw IllegalStateException()
        }

    abstract fun getResultType(): ResultType

    open fun getFormattedDuration(): String {
        return DURATION_FORMATTER.format(duration)
    }

    fun getFormattedResultType(): String {
        return when (getResultType()) {
            ResultType.SUCCESS -> "passed"
            ResultType.FAILURE -> "failed"
            ResultType.SKIPPED -> "ignored"
            else -> throw IllegalStateException()
        }
    }

    companion object {
        val DURATION_FORMATTER: DurationFormatter = DurationFormatter()
    }
}
