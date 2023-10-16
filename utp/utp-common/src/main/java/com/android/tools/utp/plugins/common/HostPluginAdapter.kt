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

import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.plugin.HostPlugin
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult

/**
 * Adapter class for UTP plugins that need to update test results.
 *
 * After upgrading to the new UTP Core dependencies, unit testing UTP host plugins became
 * difficult because afterEach and afterAll do not return the updated test result.
 * Instead we need to send the updated test result through context.events.sendTestResultUpdate.
 *
 * In unit test we need to verify that a certain test result is sent through sendTestResultUpdate.
 * However, since sendTestResultUpdate is an extension function of Events class and does not allow
 * nullable input parameters, we cannot inject argument captor and argument matcher to the mock
 * due to Mockito limitations. Although we can do this through mockk framework, having multiple
 * Mock frameworks in the same project is bad in the long run.
 */
abstract class HostPluginAdapter : HostPlugin {

    abstract fun afterEachWithReturn(testResult: TestResult, deviceController: DeviceController, cancelled: Boolean = false): TestResult

    final override fun afterEach(testResult: TestResult, deviceController: DeviceController, cancelled: Boolean) {
        afterEachWithReturn(testResult, deviceController, cancelled)
    }

    abstract fun afterAllWithReturn(testSuiteResult: TestSuiteResult, deviceController: DeviceController, cancelled: Boolean = false): TestSuiteResult

    final override fun afterAll(testSuiteResult: TestSuiteResult, deviceController: DeviceController, cancelled: Boolean) {
        afterAllWithReturn(testSuiteResult, deviceController, cancelled)
    }
}

