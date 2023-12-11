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

import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceList
import com.android.adblib.deviceInfo
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.CoroutineTestUtils
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DeviceInfo
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.junit.Test

class PhysicalDeviceProvisionerPluginTest : DeviceProvisionerTestFixture() {
  private val plugin = PhysicalDeviceProvisionerPlugin(fakeSession.scope, deviceIcons)
  private val provisioner =
    DeviceProvisioner.create(fakeSession.scope, fakeSession, listOf(plugin), deviceIcons)

  @Test
  fun physicalUsbWiFiProperties() {
    val channel = Channel<List<DeviceHandle>>(1)
    fakeSession.scope.launch { provisioner.devices.collect { channel.send(it) } }

    CoroutineTestUtils.runBlockingWithTimeout {
      setDevices(SerialNumbers.PHYSICAL1_USB, SerialNumbers.PHYSICAL2_WIFI)

      // The plugin adds the devices one at a time, so there are two events here
      val handles =
        channel.receiveUntilPassing { handles ->
          assertThat(handles).hasSize(2)
          handles
        }

      val handlesByType = handles.associateBy { it.state.properties.connectionType }

      assertThat(handlesByType).hasSize(2)
      val usbHandle = checkNotNull(handlesByType[ConnectionType.USB])
      assertThat(usbHandle.state.connectedDevice?.serialNumber)
        .isEqualTo(SerialNumbers.PHYSICAL1_USB)
      usbHandle.state.properties.apply {
        assertThat(wearPairingId).isEqualTo(SerialNumbers.PHYSICAL1_USB)
        assertThat(disambiguator).isEqualTo(SerialNumbers.PHYSICAL1_USB)
        assertThat(deviceInfoProto.mdnsConnectionType)
          .isEqualTo(DeviceInfo.MdnsConnectionType.MDNS_NONE)
        checkPhysicalDeviceProperties()
      }
      usbHandle.id.apply {
        assertThat(pluginId).isEqualTo(PhysicalDeviceProvisionerPlugin.PLUGIN_ID)
        assertThat(isTemplate).isFalse()
        assertThat(identifier).isEqualTo("serial=${SerialNumbers.PHYSICAL1_USB}")
      }

      val wifiHandle = checkNotNull(handlesByType[ConnectionType.WIFI])
      assertThat(wifiHandle.state.connectedDevice?.serialNumber)
        .isEqualTo(SerialNumbers.PHYSICAL2_WIFI)
      wifiHandle.state.properties.apply {
        assertThat(wearPairingId).isEqualTo(SerialNumbers.PHYSICAL2_USB)
        assertThat(disambiguator).isEqualTo(SerialNumbers.PHYSICAL2_USB)
        assertThat(deviceInfoProto.mdnsConnectionType)
          .isEqualTo(DeviceInfo.MdnsConnectionType.MDNS_AUTO_CONNECT_TLS)
        checkPhysicalDeviceProperties()
      }
      wifiHandle.id.apply {
        assertThat(pluginId).isEqualTo(PhysicalDeviceProvisionerPlugin.PLUGIN_ID)
        assertThat(isTemplate).isFalse()
        assertThat(identifier).isEqualTo("serial=${SerialNumbers.PHYSICAL2_USB}")
      }
    }
  }

  @Test
  fun twoConnectionsToTheSameDevice() {
    val channel = Channel<List<DeviceHandle>>(1)
    fakeSession.scope.launch { provisioner.devices.collect { channel.send(it) } }

    CoroutineTestUtils.runBlockingWithTimeout {
      setDevices(SerialNumbers.PHYSICAL2_USB)
      val handle1 =
        channel.receiveUntilPassing { handles ->
          assertThat(handles).hasSize(1)
          handles[0]
        }
      // We also want to update whenever the state changes.
      fakeSession.scope.launch {
        handle1.stateFlow.collect { channel.send(provisioner.devices.value) }
      }
      channel.drainFor(100.milliseconds)

      // Add a Wi-Fi connection to the same device.
      setDevices(SerialNumbers.PHYSICAL2_USB, SerialNumbers.PHYSICAL2_WIFI)
      // Device handles and their states didn't change.
      assertThat(channel.drainFor(100.milliseconds)).isEqualTo(0)
      assertThat(provisioner.devices.value).containsExactly(handle1)
      assertThat(handle1.state.properties.connectionType).isEqualTo(ConnectionType.USB)

      // Disconnect Wi-Fi.
      setDevices(SerialNumbers.PHYSICAL2_USB)
      assertThat(channel.drainFor(100.milliseconds)).isEqualTo(0)
      assertThat(provisioner.devices.value).containsExactly(handle1)
      assertThat(handle1.state.properties.connectionType).isEqualTo(ConnectionType.USB)
      // The handle is still connected.
      assertThat(handle1.state).isInstanceOf(DeviceState.Connected::class.java)

      // Connect Wi-Fi again.
      setDevices(SerialNumbers.PHYSICAL2_USB, SerialNumbers.PHYSICAL2_WIFI)
      assertThat(channel.drainFor(100.milliseconds)).isEqualTo(0)
      // Device handles and their states didn't change.
      assertThat(channel.drainFor(100.milliseconds)).isEqualTo(0)
      assertThat(provisioner.devices.value).containsExactly(handle1)
      assertThat(handle1.state.properties.connectionType).isEqualTo(ConnectionType.USB)

      // Disconnect USB.
      setDevices(SerialNumbers.PHYSICAL2_WIFI)
      val handle2 =
        channel.receiveUntilPassing { handles ->
          assertThat(handles).hasSize(1)
          assertThat(handles[0].state.properties.connectionType).isEqualTo(ConnectionType.WIFI)
          handles[0]
        }
      assertThat(handle2).isEqualTo(handle1)
      assertThat(provisioner.devices.value).containsExactly(handle1)

      // Reconnect USB.
      setDevices(SerialNumbers.PHYSICAL2_WIFI, SerialNumbers.PHYSICAL2_USB)
      val handle3 =
        channel.receiveUntilPassing { handles ->
          assertThat(handles).hasSize(1)
          assertThat(handles[0].state.properties.connectionType).isEqualTo(ConnectionType.USB)
          handles[0]
        }
      assertThat(handle3).isEqualTo(handle1)
      assertThat(provisioner.devices.value).containsExactly(handle1)

      // Disconnect USB.
      setDevices(SerialNumbers.PHYSICAL2_WIFI)
      val handle4 =
        channel.receiveUntilPassing { handles ->
          assertThat(handles).hasSize(1)
          assertThat(handles[0].state.properties.connectionType).isEqualTo(ConnectionType.WIFI)
          handles[0]
        }
      assertThat(handle4).isEqualTo(handle1)
      assertThat(provisioner.devices.value).containsExactly(handle1)

      // Disconnect completely.
      setDevices()
      channel.receiveUntilPassing { handles -> assertThat(handles).isEmpty() }
    }
  }

  @Test
  fun physicalDeviceMaintainsIdentityOnReconnection() {
    val channel = Channel<List<DeviceHandle>>(1)
    fakeSession.scope.launch { provisioner.devices.collect { channel.send(it) } }

    CoroutineTestUtils.runBlockingWithTimeout {
      setDevices(SerialNumbers.PHYSICAL1_USB)

      val originalHandle =
        channel.receiveUntilPassing { handles ->
          assertThat(handles).hasSize(1)

          val handle = handles[0]
          assertThat(handle.state).isInstanceOf(DeviceState.Connected::class.java)

          handle
        }

      // We also want to update whenever the state changes
      fakeSession.scope.launch {
        originalHandle.stateFlow.collect { channel.send(provisioner.devices.value) }
      }

      setDevices()

      channel.receiveUntilPassing { handles ->
        assertThat(originalHandle.state.connectedDevice).isNull()
        assertThat(handles).isEmpty()
      }

      setDevices(SerialNumbers.PHYSICAL1_USB)

      channel.receiveUntilPassing { handles ->
        assertThat(handles).hasSize(1)

        val handle = handles[0]
        assertThat(handle.id).isEqualTo(originalHandle.id)
        assertThat(handle).isNotSameAs(originalHandle)
        assertThat(handle.state).isInstanceOf(DeviceState.Connected::class.java)
      }
    }
  }

  @Test
  fun unauthorizedPhysicalDevice() {
    val handles = Channel<List<DeviceHandle>>(1)
    val unclaimedDevices = Channel<List<ConnectedDevice>>(1)
    fakeSession.scope.launch { provisioner.devices.collect { handles.send(it) } }
    fakeSession.scope.launch { provisioner.unclaimedDevices.collect { unclaimedDevices.send(it) } }

    CoroutineTestUtils.runBlockingWithTimeout {
      // Show the device as unauthorized
      fakeSession.hostServices.devices =
        DeviceList(
          listOf(
            com.android.adblib.DeviceInfo(
              SerialNumbers.PHYSICAL1_USB,
              com.android.adblib.DeviceState.UNAUTHORIZED
            )
          ),
          emptyList()
        )

      unclaimedDevices.receiveUntilPassing { devices ->
        assertThat(devices).hasSize(1)

        val device = devices[0]
        assertThat(device.deviceInfo.deviceState)
          .isEqualTo(com.android.adblib.DeviceState.UNAUTHORIZED)
      }

      // Now show the device as online
      setDevices(SerialNumbers.PHYSICAL1_USB)

      unclaimedDevices.receiveUntilPassing { devices -> assertThat(devices).isEmpty() }

      handles.receiveUntilPassing { handles ->
        assertThat(handles).hasSize(1)

        val handle = handles[0]
        assertThat(handle.state).isInstanceOf(DeviceState.Connected::class.java)
        assertThat(handle.state.properties.connectionType).isEqualTo(ConnectionType.USB)
      }
    }
  }

  private fun DeviceProperties.checkPhysicalDeviceProperties() {
    checkPixel6lDeviceProperties()
    assertThat(deviceInfoProto.deviceProvisionerId)
      .isEqualTo(PhysicalDeviceProvisionerPlugin.PLUGIN_ID)
  }
}
