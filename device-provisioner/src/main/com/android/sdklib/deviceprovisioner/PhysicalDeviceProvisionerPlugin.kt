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
import com.android.adblib.deviceProperties
import com.android.adblib.serialNumber
import com.android.adblib.utils.createChildScope
import com.android.annotations.concurrency.GuardedBy
import com.android.sdklib.deviceprovisioner.DeviceState.Connected
import com.android.sdklib.deviceprovisioner.DeviceState.Disconnected
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Plugin providing access to physical devices, connected over USB or Wi-Fi. */
class PhysicalDeviceProvisionerPlugin(
  val scope: CoroutineScope,
  private val deviceIcons: DeviceIcons
) : DeviceProvisionerPlugin {

  companion object {
    const val PLUGIN_ID = "PhysicalDevice"
  }

  override val priority = 0

  /**
   * Index of devices by their serial number. This is the device serial number, i.e. the ro.serialno
   * property, not the adb serial number, which for WiFi devices has extra stuff around it.
   */
  @GuardedBy("devicesMutex") private val devicesBySerial = hashMapOf<String, PhysicalDeviceHandle>()
  /** Lock guarding [devicesBySerial] and the contents of [PhysicalDeviceHandle]. */
  private val devicesMutex = Mutex()

  private val _devices = MutableStateFlow(emptyList<DeviceHandle>())
  override val devices = _devices.asStateFlow()

  override suspend fun claim(device: ConnectedDevice): DeviceHandle? {
    val properties = device.deviceProperties().all().asMap()

    val deviceProperties =
      DeviceProperties.build {
        readAdbSerialNumber(device.serialNumber)
        readCommonProperties(properties)
        populateDeviceInfoProto(PLUGIN_ID, device.serialNumber, properties, randomConnectionId())
        if (connectionType != ConnectionType.WIFI) {
          connectionType = ConnectionType.USB
        }
        resolution = Resolution.readFromDevice(device)
        icon =
          when (deviceType) {
            DeviceType.HANDHELD -> deviceIcons.handheld
            DeviceType.WEAR -> deviceIcons.wear
            DeviceType.TV -> deviceIcons.tv
            DeviceType.AUTOMOTIVE -> deviceIcons.automotive
            else -> deviceIcons.handheld
          }
      }

    val serialNumber = checkNotNull(properties["ro.serialno"]) { "Missing [ro.serialno] property" }

    // We want to be fairly confident this is a physical device. We expect the device to have its
    // ADB serial number be based on their device serial number.
    if (serialNumber != deviceProperties.wearPairingId) {
      return null
    }
    // If a system property says it's virtual, it probably is.
    if (deviceProperties.isVirtual == true) {
      return null
    }

    val newState = Connected(deviceProperties, device)
    val handle =
      devicesMutex.withLock {
        checkNotNull(
            devicesBySerial.compute(serialNumber) { _, handle ->
              when (handle) {
                null ->
                  PhysicalDeviceHandle(
                    serialNumber,
                    scope.createChildScope(isSupervisor = true),
                    newState
                  )
                else ->
                  // The device is already connected by either USB or Wi-Fi, and we got a new
                  // connection via the other interface
                  handle.apply { updateState(device, newState) }
              }
            }
          )
          .also { updateDevices() }
      }

    scope.launch {
      // Update device state on termination.
      device.awaitDisconnection()
      devicesMutex.withLock {
        handle.deviceDisconnected(device)
        if (handle.state !is Connected) {
          handle.scope.cancel()
          devicesBySerial.remove(serialNumber)
          updateDevices()
        }
      }
    }
    return handle
  }

  private fun updateDevices() {
    _devices.value = devicesBySerial.values.toList()
  }
}

/** Handle of a physical device. */
private class PhysicalDeviceHandle(
  private val serialNumber: String,
  override val scope: CoroutineScope,
  initialState: Connected,
) : DeviceHandle {

  override val id =
    DeviceId(PhysicalDeviceProvisionerPlugin.PLUGIN_ID, false, "serial=$serialNumber")

  /**
   * The current state of the device is always equal to either the state of the [usbConnectionFlow]
   * or the [wifiConnectionFlow]. This is updated via [updateState] rather than using Flow.combine
   * so that it occurs synchronously under the devices mutex.
   */
  override val stateFlow = MutableStateFlow<DeviceState>(initialState)
  private val usbConnectionFlow =
    MutableStateFlow<DeviceState?>(
      initialState.takeIf { it.properties.connectionType == ConnectionType.USB }
    )
  private val wifiConnectionFlow =
    MutableStateFlow<DeviceState?>(
      initialState.takeIf { it.properties.connectionType != ConnectionType.USB }
    )

  private fun updateState() {
    stateFlow.value =
      (usbConnectionFlow.value as? Connected)
        ?: (wifiConnectionFlow.value as? Connected)
        ?: usbConnectionFlow.value
        ?: wifiConnectionFlow.value!!
  }

  private fun flowForDevice(device: ConnectedDevice): MutableStateFlow<DeviceState?> =
    if (device.serialNumber == serialNumber) usbConnectionFlow else wifiConnectionFlow

  override suspend fun awaitRelease(device: ConnectedDevice) {
    flowForDevice(device).takeWhile { it?.connectedDevice == device }.collect()
  }

  fun updateState(device: ConnectedDevice, newState: DeviceState) {
    flowForDevice(device).value = newState
    updateState()
  }

  fun deviceDisconnected(device: ConnectedDevice) {
    flowForDevice(device).update { Disconnected(it!!.properties) }
    updateState()
  }
}
