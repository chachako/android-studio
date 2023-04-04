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

package com.android.tools.firebase.testlab.gradle.device

import com.android.build.api.instrumentation.manageddevice.DeviceSetupConfigureAction
import com.android.tools.firebase.testlab.gradle.services.TestLabBuildService
import com.google.firebase.testlab.gradle.ManagedDevice
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class SetupConfigureAction @Inject constructor(
    private val objectFactory: ObjectFactory,
    private val project: Project,
) : DeviceSetupConfigureAction<ManagedDevice, DeviceSetupInput> {

    override fun configureTaskInput(deviceDsl: ManagedDevice): DeviceSetupInput {
        return objectFactory.newInstance(DeviceSetupInput::class.java).apply {
            deviceName.set(deviceDsl.name)
            deviceName.disallowChanges()

            device.set(deviceDsl.device)
            device.disallowChanges()

            apiLevel.set(deviceDsl.apiLevel)
            apiLevel.disallowChanges()

            buildService.set(TestLabBuildService.RegistrationAction.getBuildService(project))
            buildService.disallowChanges()
        }
    }
}
