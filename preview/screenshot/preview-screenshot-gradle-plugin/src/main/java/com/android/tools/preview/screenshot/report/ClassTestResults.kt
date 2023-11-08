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

import java.util.TreeSet

/**
 * Custom ClassTestResults based on Gradle's ClassTestResults
 */
class ClassTestResults(
    override val name: String,
    private val packageResults: PackageTestResults
) : CompositeTestResults(packageResults) {
    val results: MutableSet<TestResult> = TreeSet()

    override val title: String = String.format("Class %s", name)
    val simpleName: String
        get() {
            val pos = name.lastIndexOf(".")
            return if (pos != -1) {
                name.substring(pos + 1)
            } else name
        }

    fun getPackageResults(): PackageTestResults {
        return packageResults
    }

    fun addTest(
        testName: String,
        duration: Long,
        project: String,
        flavor: String,
        ssImages: ScreenshotTestImages?
    ): TestResult {
        val test = TestResult(
            testName,
            duration,
            project,
            flavor,
            ssImages,
            this
        )
        results.add(test)
        addVariant(project, flavor, test)
        return addTest(test)
    }
}
