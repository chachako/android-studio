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
package com.android.sdklib.deviceprovisioner

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A DeviceTemplate contains the information necessary to activate / lease a device from a
 * provisioner. In contrast to a DeviceHandle, it does not refer to a specific device: each
 * activation produces a different device.
 *
 * In contrast to DeviceHandle, a template's [properties] never change. However, a template can use
 * [stateFlow] to indicate availability of the template or error conditions.
 */
interface DeviceTemplate {
  val id: DeviceId

  val properties: DeviceProperties

  val stateFlow: StateFlow<TemplateState>
    get() = MutableStateFlow(TemplateState(null)).asStateFlow()

  val state: TemplateState
    get() = stateFlow.value

  /**
   * An action that instantiates the template as a specific device. This may involve obtaining a
   * reservation or creating a device.
   */
  val activationAction: TemplateActivationAction

  val editAction: EditTemplateAction?
}

/**
 * The dynamic state of the template. Templates may not necessarily have any dynamic state, but it
 * can be used to indicate device availability or error conditions.
 *
 * Fields of this class should be immutable data fields (with equality defined appropriately).
 */
data class TemplateState(val error: DeviceError?)
