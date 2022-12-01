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

package com.android.build.gradle.internal.testing

import com.android.build.api.dsl.Device
import com.android.build.api.instrumentation.manageddevice.DeviceSetupInput
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunInput
import com.android.build.api.instrumentation.manageddevice.ManagedDeviceDslRegistration
import com.android.build.api.instrumentation.manageddevice.ManagedDeviceSetupFactory
import com.android.build.api.instrumentation.manageddevice.ManagedDeviceTestRunFactory

/**
 * Implementation class of the Custom Managed Device Registry
 */
class CustomManagedDeviceRegistry :
    com.android.build.api.instrumentation.manageddevice.CustomManagedDeviceRegistry {

    override fun <DeviceT: Device, TestRunInputT: DeviceTestRunInput> registerCustomDeviceType(
        dsl: ManagedDeviceDslRegistration<DeviceT>,
        testRunFactory: ManagedDeviceTestRunFactory<DeviceT, TestRunInputT>
    ) {
        // TODO(b/261078280) add implementation to register Custom Devices
    }

    override fun <
            DeviceT: Device,
            SetupInputT: DeviceSetupInput,
            TestRunInputT: DeviceTestRunInput
    > registerCustomDeviceType(
        dsl: ManagedDeviceDslRegistration<DeviceT>,
        setupFactory: ManagedDeviceSetupFactory<DeviceT, SetupInputT>,
        testRunFactory: ManagedDeviceTestRunFactory<DeviceT, TestRunInputT>
    ) {
        // TODO(b/261078280) add implementation to register Custom Devices
    }
}
