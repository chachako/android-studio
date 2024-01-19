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
import com.android.sdklib.deviceprovisioner.DeviceState.Connected
import com.android.sdklib.deviceprovisioner.DeviceState.Disconnected
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Plugin which provides handles for devices when no other plugin claims them. This will offer a
 * handle for any device, but offers no operations on that device and does not have any memory of
 * devices.
 */
class DefaultProvisionerPlugin(val scope: CoroutineScope, private val defaultIcons: DeviceIcons) :
  DeviceProvisionerPlugin {
  companion object {
    const val PLUGIN_ID = "Default"
  }

  override val priority: Int = Int.MIN_VALUE

  private val _devices = MutableStateFlow<List<DefaultDeviceHandle>>(emptyList())
  override val devices: StateFlow<List<DeviceHandle>> = _devices.asStateFlow()

  override suspend fun claim(device: ConnectedDevice): DeviceHandle {
    val properties = device.deviceProperties().all().asMap()
    val deviceProperties =
      DeviceProperties.build {
        readAdbSerialNumber(device.serialNumber)
        disambiguator = wearPairingId
        readCommonProperties(properties)
        populateDeviceInfoProto(PLUGIN_ID, device.serialNumber, properties, randomConnectionId())
        icon = defaultIcons.iconForDeviceType(deviceType)
        resolution = Resolution.readFromDevice(device)
      }
    val handle =
      DefaultDeviceHandle.create(
        scope.createChildScope(isSupervisor = true),
        Connected(deviceProperties, device),
      )

    _devices.update { it + handle }

    scope.launch {
      handle.stateFlow.takeWhile { it is Connected }.collect()
      _devices.update { it - handle }
      handle.scope.cancel()
    }

    return handle
  }

  private class DefaultDeviceHandle
  private constructor(
    override val scope: CoroutineScope,
    override val stateFlow: StateFlow<DeviceState>,
  ) : DeviceHandle {
    companion object {
      suspend fun create(scope: CoroutineScope, baseState: Connected): DefaultDeviceHandle =
        DefaultDeviceHandle(
          scope,
          baseState.connectedDevice.deviceInfoFlow
            .map { it.deviceState }
            .distinctUntilChanged()
            .flatMapLatest { deviceState ->
              when (deviceState) {
                com.android.adblib.DeviceState.ONLINE ->
                  baseState.connectedDevice.bootStatusFlow().map { bootStatus ->
                    baseState.applyBootStatus(bootStatus)
                  }
                com.android.adblib.DeviceState.DISCONNECTED ->
                  flowOf(Disconnected(baseState.properties))
                else ->
                  flowOf(baseState.copy(isReady = false, status = deviceState.displayString()))
              }
            }
            .stateIn(scope),
        )
    }

    override val id =
      DeviceId(
        PLUGIN_ID,
        false,
        "serial=${state.properties.wearPairingId!!};connection=${state.properties.deviceInfoProto.connectionId}",
      )
  }
}

private fun DeviceState.Connected.applyBootStatus(bootStatus: BootStatus) =
  copy(
    isTransitioning = !bootStatus.isBooted,
    isReady = bootStatus.isBooted,
    status = if (bootStatus.isBooted) status else "Booting",
  )
