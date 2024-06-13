/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.perflogger

import com.google.gson.stream.JsonWriter

/** Analyzer used to compare a series of runs from a different metric. */
class UTestAnalyzer private constructor(
    // There are two types of comparisons
    // 1. To compare baseline metric X to metric Y in the same branch
    private val baselineMetric: String?,
    // 2. To compare metric X to the main branch metric X
    private val compareWithMainBranch: Boolean,
    // Following are optional, can be used if needed
    // How many runs will be fetched to be used as the baseline
    private val runInfoQueryLimit: Int = 20,
    // Significance level to be used for the test, i.e. P value
    private val significance: Double = 0.05,
    // How much change from the baseline metric will be considered as significant
    private val relativeShiftValue: Double = 0.1 // 10%
) : Analyzer {

    override fun outputJson(writer: JsonWriter) {
        writer.beginObject()
        writer.name("type").value("UTestAnalyzer")
        if (compareWithMainBranch) {
            writer.name("compareWithMainBranch").value(true)
        } else {
            checkNotNull(baselineMetric) {
                "baselineMetric must be set when not comparing with main branch"
            }
            writer.name("baselineMetric").value(baselineMetric)
        }

        writer.name("runInfoQueryLimit").value(runInfoQueryLimit)
        writer.name("significance").value(significance)
        writer.name("relativeShiftValue").value(relativeShiftValue)
        writer.endObject()
    }

    companion object {
        fun forComparingWithMainBranch() = UTestAnalyzer(
            baselineMetric = null,
            compareWithMainBranch = true
        )

        fun forMetricComparison(
            baselineMetric: String,
        ) = UTestAnalyzer(
            baselineMetric = baselineMetric,
            compareWithMainBranch = false
        )
    }
}
