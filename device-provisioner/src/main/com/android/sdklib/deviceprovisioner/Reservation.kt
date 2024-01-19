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
package com.android.sdklib.deviceprovisioner

import java.time.Duration
import java.time.Instant

/** Information about the current device reservation. */
data class Reservation(
  val state: ReservationState,
  /** A short message explaining the current state of the reservation. May be empty. */
  val stateMessage: String,
  /** The time this reservation started; null if we don't know. */
  val startTime: Instant?,
  /**
   * The time this reservation expires; null if there is no definite expiration time. If the
   * reservation is in the COMPLETE or ERROR state, this is the time it entered that state.
   */
  val endTime: Instant?,

  /**
   * Maximum possible duration of the reservation. Current duration might be less than this duration
   * but may not exceed this value. null if there is no maximum duration.
   */
  val maxDuration: Duration?,
)

enum class ReservationState {
  /** The reservation has not started yet, or we are waiting for a device to become available */
  PENDING,

  /** The reservation is active. */
  ACTIVE,

  /** The reservation has expired or been released by the user. */
  COMPLETE,

  /** The reservation could not be made, or terminated due to an error. */
  ERROR,
}
