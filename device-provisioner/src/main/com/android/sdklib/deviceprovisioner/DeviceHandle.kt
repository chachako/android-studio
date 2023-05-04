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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * A specific device known to the DeviceProvisioner. It may or may not be connected.
 *
 * This is stateful and has identity: if a device corresponding to this DeviceHandle is connected,
 * disconnected, and reconnected, this DeviceHandle should remain linked to it.
 */
interface DeviceHandle {

  /**
   * A [CoroutineScope] tied to the lifecycle of this [DeviceHandle]: when this [DeviceHandle] goes
   * away, the scope will be cancelled by DeviceProvisioner. (Note that DeviceHandles may or may not
   * continue to exist while disconnected: this scope may be same as the ConnectedDevice's scope, or
   * may outlive it. However, if a plugin removes a [DeviceHandle] from its devices, it should never
   * add the same object back later, since its scope will have been cancelled already.)
   */
  val scope: CoroutineScope

  val state: DeviceState
    get() = stateFlow.value

  val stateFlow: StateFlow<DeviceState>

  /** The DeviceTemplate that this handle originated from, if applicable. */
  val sourceTemplate: DeviceTemplate?
    get() = null

  /** An action that allows activating the device, or null if activation is not supported. */
  val activationAction: ActivationAction?
    get() = null

  /**
   * An action that activates the device with a cold boot, or null if cold boot is not supported.
   */
  val coldBootAction: ColdBootAction?
    get() = null

  /** An action that allows deactivating the device, or null if deactivation is not supported. */
  val deactivationAction: DeactivationAction?
    get() = null

  /** An action that allows editing the device, or null if editing is not supported. */
  val editAction: EditAction?
    get() = null

  /**
   * An action that allows acquiring or extending a reservation for the device, or null if the
   * device doesn't use reservations.
   */
  val reservationAction: ReservationAction?
    get() = null

  /** An action that creates a duplicate of the device (with a different name). */
  val duplicateAction: DuplicateAction?
    get() = null

  /** Wipes the data on the device's filesystem. */
  val wipeDataAction: WipeDataAction?
    get() = null

  /**
   * Shows the device in its source location; depending on the device, this could launch a file
   * system browser to show the location of the device on disk, or it could launch a web browser to
   * show a device definition.
   */
  val showAction: ShowAction?
    get() = null

  val deleteAction: DeleteAction?
    get() = null
}
