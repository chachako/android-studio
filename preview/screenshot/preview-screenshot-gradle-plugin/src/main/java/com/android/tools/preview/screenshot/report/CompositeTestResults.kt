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
import java.math.BigDecimal
import java.util.TreeMap
import java.util.TreeSet
import java.util.function.BiFunction

/**
 * Custom CompositeTestResults based on Gradle's CompositeTestResults
 */
abstract class CompositeTestResults protected constructor(private val parent: CompositeTestResults?) : TestResultModel() {
    var testCount = 0
        private set
    val failures: MutableSet<TestResult> =
        TreeSet<TestResult>()
    var skipCount = 0
    override var duration: Long = 0
    private val variants: MutableMap<String, VariantTestResults?> = TreeMap<String, VariantTestResults?>()
    private val standardOutput: MutableMap<String, java.lang.StringBuilder> = TreeMap()
    private val standardError: MutableMap<String, java.lang.StringBuilder> = TreeMap()

    fun getFilename(): String? {
        return name
    }

    abstract val name: String?
    val failureCount: Int
        get() = failures.size
    override fun getFormattedDuration(): String {
        return if (testCount == 0) "-" else super.getFormattedDuration()
    }

    override fun getResultType(): ResultType {
        return if (failures.isEmpty()) ResultType.SUCCESS else ResultType.FAILURE
    }

    val formattedSuccessRate: String
        get() {
            val successRate = successRate ?: return "-"
            return "$successRate%"
        }
    val successRate: Number?
        get() {
            if (testCount == 0 || testCount == skipCount) {
                return null
            }
            val tests = BigDecimal.valueOf((testCount - skipCount).toLong())
            val successful = BigDecimal.valueOf((testCount - failureCount - skipCount).toLong())
            return successful.divide(
                tests, 2,
                BigDecimal.ROUND_DOWN
            ).multiply(BigDecimal.valueOf(100)).toInt()
        }

    fun failed(
        failedTest: TestResult,
        projectName: String,
        flavorName: String
    ) {
        failures.add(failedTest)
        if (parent != null) {
            parent.failed(failedTest, projectName, flavorName)
        }
        val key: String =
            getVariantKey(
                projectName,
                flavorName
            )
        val variantResults: VariantTestResults? = variants[key]
        if (variantResults != null) {
            variantResults.failed(failedTest, projectName, flavorName)
        }
    }

    fun skipped(projectName: String, flavorName: String) {
        skipCount++
        if (parent != null) {
            parent.skipped(projectName, flavorName)
        }
        val key: String =
            getVariantKey(
                projectName,
                flavorName
            )
        val variantResults: VariantTestResults? = variants[key]
        if (variantResults != null) {
            variantResults.skipped(projectName, flavorName)
        }
    }

    protected fun addTest(test: TestResult): TestResult {
        testCount++
        duration += test.duration
        return test
    }

    fun addVariant(
        projectName: String,
        flavorName: String,
        testResult: TestResult
    ) {
        val key: String =
            getVariantKey(
                projectName,
                flavorName
            )
        var variantResults: VariantTestResults? = variants[key]
        if (variantResults == null) {
            variantResults = VariantTestResults(key, null)
            variants[key] = variantResults
        }
        variantResults.addTest(testResult)
    }

    companion object {

        private fun getVariantKey(projectName: String, flavorName: String): String {
            return if (flavorName.equals("main", ignoreCase = true)) {
                projectName
            } else "$projectName:$flavorName"
        }
    }
}
