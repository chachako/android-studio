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
package com.android.sdklib.deviceprovisioner.testing

import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.sdklib.deviceprovisioner.DeviceProvisioner

/** A [FakeAdbServerProviderRule] that also provides a [DeviceProvisioner] */
class DeviceProvisionerRule(
  configure: (FakeAdbServerProvider.() -> FakeAdbServerProvider)? = null
) : FakeAdbServerProviderRule(configure) {

  lateinit var deviceProvisioner: DeviceProvisioner
    private set
  lateinit var deviceProvisionerPlugin: FakeAdbDeviceProvisionerPlugin
    private set

  override fun before() {
    super.before()
    deviceProvisionerPlugin = FakeAdbDeviceProvisionerPlugin(adbSession.scope, fakeAdb)
    deviceProvisioner = DeviceProvisioner.create(adbSession, listOf(deviceProvisionerPlugin))
  }
}
