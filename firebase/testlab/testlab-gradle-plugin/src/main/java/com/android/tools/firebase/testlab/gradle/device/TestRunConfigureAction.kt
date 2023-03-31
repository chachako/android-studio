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

package com.android.tools.firebase.testlab.gradle.device

import com.android.build.api.instrumentation.manageddevice.DeviceTestRunConfigureAction
import com.android.tools.firebase.testlab.gradle.services.TestLabBuildService
import com.google.firebase.testlab.gradle.ManagedDevice
import javax.inject.Inject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildServiceRegistry

open class TestRunConfigureAction @Inject constructor(
    private val objectFactory: ObjectFactory,
    private val providerFactory: ProviderFactory,
    private val buildServiceRegistry: BuildServiceRegistry,
): DeviceTestRunConfigureAction<ManagedDevice, DeviceTestRunInput> {

    override fun configureTaskInput(deviceDSL: ManagedDevice): DeviceTestRunInput =
        objectFactory.newInstance(DeviceTestRunInput::class.java).apply {
            device.set(deviceDSL.device)
            device.disallowChanges()

            apiLevel.set(deviceDSL.apiLevel)
            apiLevel.disallowChanges()

            orientation.set(deviceDSL.orientation)
            orientation.disallowChanges()

            locale.set(deviceDSL.locale)
            locale.disallowChanges()

            buildService.set(
                TestLabBuildService.RegistrationAction.getBuildService(buildServiceRegistry))
            buildService.disallowChanges()

            numUniformShards.set(
                providerFactory.provider { buildService.get().numUniformShards })
            numUniformShards.disallowChanges()

            extraDeviceFiles.set(providerFactory.provider {
                buildService.get().parameters.extraDeviceFiles.get() })
            extraDeviceFiles.disallowChanges()
        }
}
