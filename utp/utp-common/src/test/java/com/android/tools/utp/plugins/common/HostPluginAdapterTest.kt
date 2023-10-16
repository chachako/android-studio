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

package com.android.tools.utp.plugins.common

import com.google.testing.platform.api.context.Context
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.proto.api.core.TestCaseProto
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class HostPluginAdapterTest {

    class TestHostPlugin : HostPluginAdapter() {
        override fun configure(context: Context) = Unit

        override fun beforeEach(
                testCase: TestCaseProto.TestCase?,
                deviceController: DeviceController
        ) = Unit

        override fun beforeAll(deviceController: DeviceController) = Unit

        override fun afterEachWithReturn(
                testResult: TestResult,
                deviceController: DeviceController,
                cancelled: Boolean
        ): TestResult {
            deviceController.getDevice()
            return testResult
        }

        override fun afterAllWithReturn(
                testSuiteResult: TestSuiteResult,
                deviceController: DeviceController,
                cancelled: Boolean
        ): TestSuiteResult {
            deviceController.getDevice()
            return testSuiteResult
        }

        override fun canRun(): Boolean = true
    }

    private val mockPlugin = TestHostPlugin()

    private val mockTestResult = TestResult.newBuilder().build()

    private val mockTestSuiteResult = TestSuiteResult.newBuilder().build()

    private val mockDeviceController= mock(DeviceController::class.java)

    @Test
    fun testFunWithReturnIsCalled() {
        mockPlugin.afterEach(mockTestResult, mockDeviceController)
        verify(mockDeviceController, times(1)).getDevice()
        mockPlugin.afterAll(mockTestSuiteResult, mockDeviceController)
        verify(mockDeviceController, times(2)).getDevice()
    }
}

