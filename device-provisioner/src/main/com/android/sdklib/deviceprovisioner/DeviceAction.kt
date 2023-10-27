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

import com.android.sdklib.deviceprovisioner.DeviceAction.DefaultPresentation
import java.time.Duration
import java.time.Instant
import javax.swing.Icon
import kotlinx.coroutines.flow.StateFlow

/**
 * An action that a user might invoke on a device or a device provisioner. The actual action methods
 * are defined on subinterfaces, since their signatures may vary.
 *
 * TODO: These did not end up varying much; we perhaps don't need a distinct interface for every
 *   action type
 */
interface DeviceAction {
  val presentation: StateFlow<Presentation>

  data class Presentation(val label: String, val icon: Icon, val enabled: Boolean)

  /** Returns the appropriate element of DefaultPresentation for this class. */
  fun DefaultPresentation.fromContext(): Presentation

  /**
   * A default value for [Presentation] for all the action types.
   *
   * This primarily exists to allow icons to be defined / loaded by higher-level modules and
   * injected into plugin implementations in tools/base.
   */
  interface DefaultPresentation {
    val createDeviceAction: Presentation
    val createDeviceTemplateAction: Presentation
    val activationAction: Presentation
    val coldBootAction: Presentation
    val bootSnapshotAction: Presentation
    val deactivationAction: Presentation
    val editAction: Presentation
    val showAction: Presentation
    val wipeDataAction: Presentation
    val duplicateAction: Presentation
    val deleteAction: Presentation
    val editTemplateAction: Presentation
    val reservationAction: Presentation
    val templateActivationAction: Presentation
    val repairDeviceAction: Presentation
  }
}

interface CreateDeviceAction : DeviceAction {
  /**
   * Creates a device, based on input from the user.
   *
   * If creation is successful, this should have the side effect of adding the device to the
   * provisioner's list of devices.
   */
  suspend fun create()

  override fun DefaultPresentation.fromContext() = createDeviceAction
}

interface CreateDeviceTemplateAction : DeviceAction {
  /**
   * Creates a device template, based on input from the user.
   *
   * If creation is successful, this should have the side effect of adding the device to the
   * provisioner's list of templates.
   */
  suspend fun create()

  override fun DefaultPresentation.fromContext() = createDeviceTemplateAction
}

interface ActivationAction : DeviceAction {
  suspend fun activate()

  override fun DefaultPresentation.fromContext() = activationAction
}

interface ColdBootAction : DeviceAction {
  suspend fun activate()

  override fun DefaultPresentation.fromContext() = coldBootAction
}

interface BootSnapshotAction : DeviceAction {
  suspend fun activate(snapshot: Snapshot)

  suspend fun snapshots(): List<Snapshot>

  override fun DefaultPresentation.fromContext() = bootSnapshotAction
}

interface Snapshot {
  val name: String
}

interface DeactivationAction : DeviceAction {
  suspend fun deactivate()

  override fun DefaultPresentation.fromContext() = deactivationAction
}

interface EditAction : DeviceAction {
  suspend fun edit()

  override fun DefaultPresentation.fromContext() = editAction
}

interface EditTemplateAction : DeviceAction {
  /**
   * Invokes a UI to make edits to the template. If the edits are accepted, returns the new template
   * that was created.
   */
  suspend fun edit(): DeviceTemplate?

  override fun DefaultPresentation.fromContext() = editTemplateAction
}

interface DuplicateAction : DeviceAction {
  suspend fun duplicate()

  override fun DefaultPresentation.fromContext() = duplicateAction
}

interface WipeDataAction : DeviceAction {

  /** Wipes the user data and snapshots of the device. */
  suspend fun wipeData()

  override fun DefaultPresentation.fromContext() = wipeDataAction
}

interface ShowAction : DeviceAction {
  suspend fun show()

  override fun DefaultPresentation.fromContext() = showAction
}

/** Deletes the given device from any persistent storage. */
interface DeleteAction : DeviceAction {
  /**
   * Deletes the device; this can mean deleting a device from disk, or simply removing it from a
   * list of remembered devices.
   */
  suspend fun delete()

  override fun DefaultPresentation.fromContext() = deleteAction
}

interface ReservationAction : DeviceAction {
  /**
   * Attempts to reserve the device for the given duration. If there is already an active
   * reservation, this will attempt to extend the reservation for the given duration.
   *
   * If the operation is successful, the new state should be reflected in the device's
   * [Reservation]. If we fail to update an active reservation, but the reservation remains active,
   * its [ReservationState] should remain ACTIVE, and a [DeviceActionException] should be thrown.
   *
   * If we fail to reserve a device, a [DeviceActionException] should be thrown, with a
   * user-appropriate message. Also, the device's [ReservationState] should be set to FAILED.
   *
   * @return the new end time of the reservation
   */
  suspend fun reserve(duration: Duration): Instant

  /**
   * Attempts to end the reservation.
   *
   * If the operation is successful, the new state should change to [ReservationState.COMPLETE].
   * Otherwise, a [DeviceActionException] should be thrown with a user-appropriate message.
   */
  suspend fun endReservation()

  override fun DefaultPresentation.fromContext() = reservationAction
}

interface TemplateActivationAction : DeviceAction {
  /**
   * Attempts to activate an instance of the template. If a duration is passed, it may be used to
   * determine the initial length of the device reservation, if applicable. If duration is null, a
   * default duration value will be used if needed.
   *
   * If the operation is successful, the resulting device should be returned.
   *
   * If we fail to activate a device, a [DeviceActionException] should be thrown, with a
   * user-appropriate message. An underlying exception may be passed as the cause.
   */
  suspend fun activate(duration: Duration? = null): DeviceHandle

  /** Indicates if the duration argument is relevant. */
  val durationUsed: Boolean

  override fun DefaultPresentation.fromContext() = templateActivationAction
}

interface RepairDeviceAction : DeviceAction {

  /** Attempts to repair the error. If the error is repaired, the DeviceState should change. */
  suspend fun repair()

  override fun DefaultPresentation.fromContext() = repairDeviceAction
}

/**
 * Indicates a failure in performing a device action, with a user-relevant cause.
 *
 * This is intended for the same types of errors that checked exceptions are used for in Java:
 * unpreventable errors due to external conditions (lack of resources, I/O errors, etc.)
 *
 * It should *not* be used to wrap programming errors (NullPointerException,
 * IllegalArgumentException, etc.): these are generally not user-relevant and should be allowed to
 * propagate to the Studio error handler.
 *
 * @param message a user-visible statement of what went wrong
 * @param cause the underlying exception; not displayed to user
 */
open class DeviceActionException(message: String, cause: Throwable? = null) :
  Exception(message, cause)

/**
 * Indicates that the device action was called when it is not enabled. This may be unavoidable due
 * to race conditions; callers should recover gracefully. Callers should strongly consider supplying
 * a more natural error message.
 */
class DeviceActionDisabledException(message: String) : DeviceActionException(message) {
  constructor(
    action: DeviceAction
  ) : this("The \"${action.presentation.value.label}\" action is unavailable.")
}
