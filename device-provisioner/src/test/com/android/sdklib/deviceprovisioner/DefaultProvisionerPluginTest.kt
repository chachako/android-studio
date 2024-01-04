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

import com.android.adblib.serialNumber
import com.android.adblib.testingutils.CoroutineTestUtils
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DeviceInfo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.junit.Test

class DefaultProvisionerPluginTest : DeviceProvisionerTestFixture() {
  private val provisioner =
    DeviceProvisioner.create(fakeSession.scope, fakeSession, listOf(), deviceIcons)

  @Test
  fun defaultUsbWiFiProperties() {
    val channel = Channel<List<DeviceHandle>>(1)
    fakeSession.scope.launch { provisioner.devices.collect { channel.send(it) } }

    runBlockingWithTimeout {
      setDevices(SerialNumbers.PHYSICAL1_USB, SerialNumbers.PHYSICAL2_WIFI)

      // The plugin adds the devices one at a time, so there are two events here
      val handles =
        channel.receiveUntilPassing { handles ->
          assertThat(handles).hasSize(2)
          handles
        }

      val handlesByType = handles.associateBy { it.state.properties.connectionType }

      assertThat(handlesByType).hasSize(2)
      // We detect WiFi devices by their distinctive serial numbers, but unlike for physical
      // devices, we make no assumptions that other devices are connected to USB.
      val handle1 = checkNotNull(handlesByType[null])
      assertThat(handle1.state.connectedDevice?.serialNumber).isEqualTo(SerialNumbers.PHYSICAL1_USB)
      handle1.state.properties.apply {
        assertThat(wearPairingId).isEqualTo(SerialNumbers.PHYSICAL1_USB)
        assertThat(disambiguator).isEqualTo(SerialNumbers.PHYSICAL1_USB)
        assertThat(deviceInfoProto.mdnsConnectionType)
          .isEqualTo(DeviceInfo.MdnsConnectionType.MDNS_NONE)
        checkPhysicalDeviceProperties()
      }
      handle1.id.apply {
        assertThat(pluginId).isEqualTo(DefaultProvisionerPlugin.PLUGIN_ID)
        assertThat(isTemplate).isFalse()
        assertThat(identifier).startsWith("serial=${SerialNumbers.PHYSICAL1_USB};connection=")
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
        assertThat(pluginId).isEqualTo(DefaultProvisionerPlugin.PLUGIN_ID)
        assertThat(isTemplate).isFalse()
        assertThat(identifier).startsWith("serial=${SerialNumbers.PHYSICAL2_USB};connection=")
      }
    }
  }

  @Test
  fun defaultDeviceIsDistinctOnReconnection() {
    val channel = Channel<List<DeviceHandle>>(1)
    fakeSession.scope.launch { provisioner.devices.collect { channel.send(it) } }

    runBlockingWithTimeout {
      setDevices(SerialNumbers.EMULATOR)

      val originalHandle =
        channel.receiveUntilPassing { handles ->
          assertThat(handles).hasSize(1)

          val handle = handles[0]
          assertThat(handle.state).isInstanceOf(DeviceState.Connected::class.java)

          handle
        }
      originalHandle.state.properties.apply {
        assertThat(wearPairingId).isEqualTo(SerialNumbers.EMULATOR)
        assertThat(disambiguator).isEqualTo(SerialNumbers.EMULATOR)
      }

      // Now we also want to update whenever the state changes
      fakeSession.scope.launch {
        originalHandle.stateFlow.collect { channel.send(provisioner.devices.value) }
      }

      setDevices()

      // Check this first, since changes to it don't result in a new message on `channel`
      CoroutineTestUtils.yieldUntil { originalHandle.scope.coroutineContext.job.isCancelled }

      // We get two messages on the channel, one for the device becoming disconnected, and one
      // for the device list changing. We don't know what order they will occur in, but it
      // doesn't matter; just check the state after the second.
      channel.receiveUntilPassing { handles ->
        assertThat(handles).isEmpty()
        assertThat(originalHandle.state).isInstanceOf(DeviceState.Disconnected::class.java)
      }

      setDevices(SerialNumbers.EMULATOR)

      channel.receiveUntilPassing { handles ->
        assertThat(handles).hasSize(1)

        val handle = handles[0]
        assertThat(handle.id).isNotEqualTo(originalHandle.id)
        assertThat(handle).isNotSameAs(originalHandle)
        assertThat(handle.state).isInstanceOf(DeviceState.Connected::class.java)
      }
    }
  }

  @Test
  fun isReadyWhenOnlineAndBooted() {
    val channel = Channel<List<DeviceHandle>>(1)
    fakeSession.scope.launch { provisioner.devices.collect { channel.send(it) } }

    runBlockingWithTimeout {
      setDevices(SerialNumbers.EMULATOR)

      val handle =
        channel.receiveUntilPassing { handles ->
          assertThat(handles).hasSize(1)

          val handle = handles[0]
          assertThat(handle.state).isInstanceOf(DeviceState.Connected::class.java)

          handle
        }

      assertThat(handle.state.isReady).isFalse()

      setBootComplete(SerialNumbers.EMULATOR)

      // Should not time out
      handle.awaitReady()

      assertThat(handle.state.isReady).isTrue()

      // Simulate going offline briefly
      setDevices(SerialNumbers.EMULATOR, state = com.android.adblib.DeviceState.OFFLINE)

      // Should not time out
      handle.stateFlow.takeWhile { it.isReady }

      setDevices(SerialNumbers.EMULATOR)

      // Should not time out
      handle.awaitReady()
    }
  }

  private fun DeviceProperties.checkPhysicalDeviceProperties() {
    checkPixel6lDeviceProperties()
    assertThat(deviceInfoProto.deviceProvisionerId).isEqualTo(DefaultProvisionerPlugin.PLUGIN_ID)
  }
}
