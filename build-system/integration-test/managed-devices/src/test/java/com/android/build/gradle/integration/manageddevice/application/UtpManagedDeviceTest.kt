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

/**
 * An integration test for Gradle Managed Device.
 */
class UtpManagedDeviceTest : UtpTestBase() {

    @get:Rule
    val customAndroidSdkRule = CustomAndroidSdkRule()

    companion object {
        private const val DSL_DEVICE_NAME = "device1"

        private const val OUTPUTS = "build/outputs"
        private const val TEST_ADDITIONAL_OUTPUT = "$OUTPUTS/managed_device_android_test_additional_output/debug/$DSL_DEVICE_NAME"
        private const val TEST_RESULTS = "$OUTPUTS/androidTest-results/managedDevice/debug"
        private const val TEST_RESULT_XML = "$TEST_RESULTS/$DSL_DEVICE_NAME/TEST-$DSL_DEVICE_NAME-_"
        private const val LOGCAT = "$TEST_RESULTS/$DSL_DEVICE_NAME/logcat-com.example.android.kotlin.ExampleInstrumentedTest-useAppContext.txt"
        private const val TEST_RESULT_PB = "$TEST_RESULTS/$DSL_DEVICE_NAME/test-result.pb"
        private const val AGGREGATED_TEST_RESULT_PB = "$TEST_RESULTS/test-result.pb"

        private const val REPORTS = "build/reports"
        private const val TEST_REPORT = "$REPORTS/androidTests/managedDevice/debug/$DSL_DEVICE_NAME/com.example.android.kotlin.html"
        private const val TEST_COV_XML = "$REPORTS/coverage/androidTest/debug/managedDevice/report.xml"
    }

    override val deviceName: String = "dev29_default_x86_64_Pixel_2"

    override val executor: GradleTaskExecutor
        get() = customAndroidSdkRule.run { project.executorWithCustomAndroidSdk() }

    override fun selectModule(moduleName: String) {
        project.getSubproject(moduleName).addManagedDevice(DSL_DEVICE_NAME)
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
