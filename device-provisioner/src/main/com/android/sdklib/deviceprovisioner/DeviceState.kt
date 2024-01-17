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

import com.android.adblib.ConnectedDevice
import com.android.adblib.deviceInfo
import com.android.adblib.deviceProperties
import com.google.common.base.Stopwatch
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/** Identifies the ADB connection state of a provisionable device and its characteristics. */
sealed interface DeviceState {

  val properties: DeviceProperties

  /** Information about the current remote device reservation, if any. */
  val reservation: Reservation?

  /** The [ConnectedDevice] provided by adblib, if we are connected. */
  val connectedDevice: ConnectedDevice?
    get() = null

  /**
   * Indicates that the device is in the process of changing state; generally, this would correspond
   * to a spinner in the UI.
   */
  val isTransitioning: Boolean

  /**
   * Indicates that the device is ready for normal use. While being in the Connected state means we
   * have a ConnectedDevice and can see the device via ADB, it may not be fully operational. For
   * example, we may be waiting for authorization, or waiting for the device to complete booting. If
   * this is false, more details of the status should be present in "status".
   */
  val isReady: Boolean

  /**
   * A very short, user-visible summary of the state of the device, e.g. "Offline", "Starting up",
   * "Connecting", "Connected".
   */
  val status: String

  /**
   * If present, there is a problem with the device. Some operations may be unavailable. The
   * [RepairDeviceAction] may be able to resolve it.
   */
  val error: DeviceError?

  fun isOnline(): Boolean =
    connectedDevice?.deviceInfo?.deviceState == com.android.adblib.DeviceState.ONLINE

  data class Disconnected(
    override val properties: DeviceProperties,
    override val isTransitioning: Boolean,
    override val status: String,
    override val reservation: Reservation? = null,
    override val error: DeviceError? = null,
  ) : DeviceState {
    constructor(properties: DeviceProperties) : this(properties, false, "Offline")

    override val isReady: Boolean
      get() = false
  }

  /**
   * The state of a device that is connected to ADB. The device may not be usable yet; most clients
   * will want to wait for it to be [ready][isReady].
   */
  data class Connected(
    override val properties: DeviceProperties,
    override val isTransitioning: Boolean,
    override val isReady: Boolean,
    override val status: String,
    override val connectedDevice: ConnectedDevice,
    override val reservation: Reservation? = null,
    override val error: DeviceError? = null,
  ) : DeviceState {
    constructor(
      properties: DeviceProperties,
      connectedDevice: ConnectedDevice,
      adbDeviceState: com.android.adblib.DeviceState = connectedDevice.deviceInfo.deviceState,
      reservation: Reservation? = null,
    ) : this(
      properties,
      isTransitioning = false,
      isReady = adbDeviceState == com.android.adblib.DeviceState.ONLINE,
      adbDeviceState.displayString(),
      connectedDevice,
      reservation
    )
  }
}

/** A problem with a device. */
interface DeviceError {
  enum class Severity {
    INFO,
    WARNING,
    ERROR
  }

  val severity: Severity

  /** User-visible representation of the device error. */
  val message: String
}

fun com.android.adblib.DeviceState.displayString() =
  when (this) {
    com.android.adblib.DeviceState.ONLINE -> "Connected"
    else -> state.substring(0, 1).uppercase() + state.substring(1)
  }

inline fun <R> DeviceState.ifOnline(block: (ConnectedDevice) -> R): R? =
  connectedDevice?.let { connectedDevice ->
    when (connectedDevice.deviceInfo.deviceState) {
      com.android.adblib.DeviceState.ONLINE -> block(connectedDevice)
      else -> null
    }
  }

suspend fun DeviceHandle.awaitReady(): DeviceState.Connected =
  stateFlow.first { it is DeviceState.Connected && it.isReady } as DeviceState.Connected

class TimeoutTracker(private val duration: Duration) {
  private val stopwatch = Stopwatch.createStarted()

  fun isTimedOut() = stopwatch.elapsed() >= duration
}

suspend fun com.android.adblib.DeviceProperties.isBootComplete() =
  all().find { it.name == "dev.bootcomplete" }?.value == "1"

data class BootStatus(val isBooted: Boolean)

fun ConnectedDevice.bootStatusFlow() = flow {
  if (deviceProperties().isBootComplete()) {
    emit(BootStatus(true))
  } else {
    emit(BootStatus(false))
    do {
      delay(1.seconds)
    } while (!deviceProperties().isBootComplete())
    emit(BootStatus(true))
  }
}
