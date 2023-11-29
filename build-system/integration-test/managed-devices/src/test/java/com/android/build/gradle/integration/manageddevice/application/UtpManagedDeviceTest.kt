/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.integration.manageddevice.application

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.manageddevice.utils.CustomAndroidSdkRule
import com.android.build.gradle.integration.manageddevice.utils.addManagedDevice
import com.android.build.gradle.integration.utp.UtpTestBase
import org.junit.Rule
import java.io.File

/**
 * An integration test for Gradle Managed Device.
 */
class UtpManagedDeviceTest : UtpTestBase() {

    @get:Rule
    val customAndroidSdkRule = CustomAndroidSdkRule()

    companion object {
        private const val TEST_RESULT_XML = "build/outputs/androidTest-results/managedDevice/device1/TEST-device1-_"
        private const val LOGCAT = "build/outputs/androidTest-results/managedDevice/device1/logcat-com.example.android.kotlin.ExampleInstrumentedTest-useAppContext.txt"
        private const val TEST_REPORT = "build/reports/androidTests/managedDevice/device1/com.example.android.kotlin.html"
        private const val TEST_RESULT_PB = "build/outputs/androidTest-results/managedDevice/device1/test-result.pb"
        private const val AGGREGATED_TEST_RESULT_PB = "build/outputs/androidTest-results/managedDevice/test-result.pb"
        private const val TEST_COV_XML = "build/reports/coverage/androidTest/debug/managedDevice/report.xml"
        private const val TEST_ADDITIONAL_OUTPUT = "build/outputs/managed_device_android_test_additional_output/device1"
    }

    private lateinit var sdkImageSource: File
    private lateinit var userHomeDirectory: File
    private lateinit var localPrefDirectory: File

    override val deviceName: String = "dev29_default_x86_Pixel_2"

    override val executor: GradleTaskExecutor
        get() = customAndroidSdkRule.run { project.executorWithCustomAndroidSdk() }

    override fun selectModule(moduleName: String) {
        project.getSubproject(moduleName).addManagedDevice("device1")
        testTaskName = ":${moduleName}:allDevicesCheck"
        testResultXmlPath = "${moduleName}/$TEST_RESULT_XML$moduleName-.xml"
        testReportPath = "${moduleName}/$TEST_REPORT"
        testResultPbPath = "${moduleName}/$TEST_RESULT_PB"
        aggTestResultPbPath = "${moduleName}/$AGGREGATED_TEST_RESULT_PB"
        testCoverageXmlPath = "${moduleName}/$TEST_COV_XML"
        testLogcatPath = "${moduleName}/$LOGCAT"
        testAdditionalOutputPath = "${moduleName}/${TEST_ADDITIONAL_OUTPUT}"
    }
}
