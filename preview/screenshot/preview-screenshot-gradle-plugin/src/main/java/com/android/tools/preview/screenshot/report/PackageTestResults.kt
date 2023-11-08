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

import java.util.TreeMap

/**
 * Custom PackageTestResults based on Gradle's PackageTestResults
 */
class PackageTestResults(
    override var name: String = DEFAULT_PACKAGE,
    model: AllTestResults?
) : CompositeTestResults(model) {

    private val classes: MutableMap<String, ClassTestResults?> =
        TreeMap<String, ClassTestResults?>()
    override val title: String
        get() = if (name == DEFAULT_PACKAGE) "Default package" else String.format(
            "Package %s",
            name
        )

    fun getClasses(): Collection<ClassTestResults?> {
        return classes.values
    }

    fun addTest(
        className: String,
        testName: String,
        duration: Long,
        project: String,
        flavor: String,
        ssImages: ScreenshotTestImages?
    ): TestResult {
        val classResults: ClassTestResults = addClass(className)
        val testResult: TestResult = addTest(
            classResults.addTest(testName, duration, project, flavor, ssImages)
        )
        addVariant(project, flavor, testResult)
        return testResult
    }

    fun addClass(className: String): ClassTestResults {
        var classResults: ClassTestResults? =
            classes[className]
        if (classResults == null) {
            classResults =
                ClassTestResults(className, this)
            classes[className] = classResults
        }
        return classResults
    }

    companion object {

        private const val DEFAULT_PACKAGE = "default-package"
    }
}
