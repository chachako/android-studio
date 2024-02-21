/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.application.taskstates

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleBuildResult
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.Scanner

/**
 * Verifies that the GRADLE_MAANAGED_DEVICE_INCLUDE_MANAGED_DEVICES_IN_REPORTING property
 * works correctly with the managed device tasks.
 */
@RunWith(JUnit4::class)
class ReportingTaskStatesTest {

    @get:Rule
    var project = EmptyActivityProjectBuilder().build()

    lateinit var appProject: GradleTestProject

    @Before
    fun setup() {
        project.buildFile.appendText("""
            apply plugin: 'android-reporting'
        """)
        appProject = project.getSubproject("app")
        appProject.buildFile.appendText("""
            android {
                testOptions {
                    managedDevices {
                        allDevices {
                            device1 (com.android.build.api.dsl.ManagedVirtualDevice) {
                                device = "Pixel 2"
                                apiLevel = 29
                                systemImageSource = "aosp"
                            }
                        }
                    }
                    execution = "ANDROIDX_TEST_ORCHESTRATOR"
                }
            }
        """)
    }

    private fun runReportingTaskDryRun(): GradleBuildResult =
        project.executor()
            .withArgument("--dry-run")
            .run("mergeAndroidReports")

    private fun findTask(taskName: String, scanner: Scanner): Boolean {
        while (scanner.hasNextLine()) {
            if (scanner.nextLine().contains(taskName)) {
                return true
            }
        }
        return false
    }

    @Test
    fun managedDevicesNotIncludedWithoutProperty() {
        project.gradlePropertiesFile.appendText(
            "\nandroid.experimental.testOptions.managedDevices.includeInMergedReport=false\n"
        )
        runReportingTaskDryRun().stdout.use { scanner ->
            assertThat(findTask("app:device1DebugAndroidTest", scanner)).isFalse()
        }
    }

    @Test
    fun managedDevicesIncludedWithProperty() {
        project.gradlePropertiesFile.appendText(
            "\nandroid.experimental.testOptions.managedDevices.includeInMergedReport=true\n"
        )
        val result = runReportingTaskDryRun()

        runReportingTaskDryRun().stdout.use { scanner ->
            assertThat(findTask("app:device1DebugAndroidTest", scanner)).isTrue()
        }
    }
}
