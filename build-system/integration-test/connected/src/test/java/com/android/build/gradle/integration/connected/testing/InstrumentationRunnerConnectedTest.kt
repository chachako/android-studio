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

package com.android.build.gradle.integration.connected.testing

import com.android.build.gradle.integration.common.fixture.GradleBuildResult
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.connected.utils.getEmulator
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class InstrumentationRunnerConnectedTest {

    companion object {
        @get:ClassRule
        @get:JvmStatic
        val emulator = getEmulator()
    }

    @get:Rule
    val project = builder().fromTestProject("separateTestModule").create()

    @Before
    fun setUp() {
        // fail fast if no response
        project.addAdbTimeout()
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll")
    }

    @Test
    fun validateTestInstrumentationRunnerArgumentsPerFlavor() {
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            """
                android {
                    defaultConfig {
                        testInstrumentationRunnerArguments(value: 'default', size: 'small')
                    }

                    flavorDimensions 'foo'
                    productFlavors {
                        f1 {}

                        f2  {
                            testInstrumentationRunnerArgument 'value', 'f2'
                        }

                        f3  {
                            testInstrumentationRunnerArguments['otherValue'] = 'f3'
                        }

                        f4  {
                            testInstrumentationRunnerArguments(otherValue: 'f4.1')
                            testInstrumentationRunnerArguments = [otherValue: 'f4.2']
                        }
                    }
                }
            """.trimIndent()
        )

        var result = project.executor().run(":app:connectedF1DebugAndroidTest")
        checkArgsInOutput(
            f2ArgPresent = false, f3ArgPresent = false, f4ArgPresent = false, result)

        result = project.executor().run(":app:connectedF2DebugAndroidTest")
        checkArgsInOutput(
            f2ArgPresent = true, f3ArgPresent = false, f4ArgPresent = false, result)

        result = project.executor().run(":app:connectedF3DebugAndroidTest")
        checkArgsInOutput(
            f2ArgPresent = false, f3ArgPresent = true, f4ArgPresent = false, result)

        result = project.executor().run(":app:connectedF4DebugAndroidTest")
        checkArgsInOutput(
            f2ArgPresent = false, f3ArgPresent = false, f4ArgPresent = true, result)
    }

    private fun checkArgsInOutput(
        f2ArgPresent: Boolean,
        f3ArgPresent: Boolean,
        f4ArgPresent: Boolean,
        result: GradleBuildResult
    ) {
        assertThat(result.stdout).contains("key: \"size\"\nvalue: \"small\"")
        assertThat(result.stdout).doesNotContain("key: \"otherValue\"\nvalue: \"f4.1\"")

        val f2String = "key: \"value\"\nvalue: \"f2\""
        if (f2ArgPresent) {
            assertThat(result.stdout).contains(f2String)
        } else {
            assertThat(result.stdout).doesNotContain(f2String)
            assertThat(result.stdout).contains("key: \"value\"\nvalue: \"default\"")
        }

        val f3String = "key: \"otherValue\"\nvalue: \"f3\""
        if (f3ArgPresent) assertThat(result.stdout).contains(f3String)
        else assertThat(result.stdout).doesNotContain(f3String)

        val f4String = "key: \"otherValue\"\nvalue: \"f4.2\""
        if (f4ArgPresent) assertThat(result.stdout).contains(f4String)
        else assertThat(result.stdout).doesNotContain(f4String)
    }

    @Test
    fun validateTestVariantInstrumentationRunnerArguments() {
        TestFileUtils.appendToFile(
            project.getSubproject("test").buildFile,
            """
                androidComponents {
                    onVariants(selector().all(), { variant ->
                        variant.instrumentationRunnerArguments.put('testKey', 'testValue')
                    })
                }
            """.trimIndent()
        )

        val result = project.executor().run(":test:connectedCheck")
        assertThat(result.stdout).contains("key: \"testKey\"\nvalue: \"testValue\"")
    }
}
