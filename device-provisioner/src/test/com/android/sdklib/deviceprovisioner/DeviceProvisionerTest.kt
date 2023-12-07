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
import com.android.adblib.DeviceInfo
import com.android.adblib.DeviceList
import com.android.adblib.DevicePropertyNames
import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState
import com.android.adblib.deviceInfo
import com.android.adblib.serialNumber
import com.android.adblib.testing.FakeAdbSession
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.sdklib.deviceprovisioner.DeviceState.Connected
import com.android.sdklib.deviceprovisioner.DeviceState.Disconnected
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DeviceInfo.DeviceType
import com.google.wireless.android.sdk.stats.DeviceInfo.MdnsConnectionType
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.junit.Test

/**
 * Tests the [DeviceProvisioner] and its two basic plugins, [PhysicalDeviceProvisionerPlugin] and
 * [DefaultProvisionerPlugin]
 */
class DeviceProvisionerTest {
  private val fakeSession = FakeAdbSession()

  private val deviceIcons =
    DeviceIcons(EmptyIcon.DEFAULT, EmptyIcon.DEFAULT, EmptyIcon.DEFAULT, EmptyIcon.DEFAULT)
  private val plugin = PhysicalDeviceProvisionerPlugin(fakeSession.scope, deviceIcons)
  private val provisioner =
    DeviceProvisioner.create(fakeSession.scope, fakeSession, listOf(plugin), deviceIcons)

  private object SerialNumbers {
    const val PHYSICAL1_USB = "X1058A"
    const val PHYSICAL2_USB = "X1BQ704RX2B"
    const val PHYSICAL2_WIFI = "adb-X1BQ704RX2B-VQ4ADB._adb-tls-connect._tcp."
    const val EMULATOR = "emulator-5554"

    val ALL = listOf(PHYSICAL1_USB, PHYSICAL2_USB, PHYSICAL2_WIFI, EMULATOR)
  }

  init {
    fakeSession.deviceServices.configureDeviceProperties(
      DeviceSelector.fromSerialNumber(SerialNumbers.PHYSICAL1_USB),
      mapOf(
        "ro.serialno" to SerialNumbers.PHYSICAL1_USB,
        DevicePropertyNames.RO_BUILD_VERSION_SDK to "31",
        DevicePropertyNames.RO_PRODUCT_MANUFACTURER to "Google",
        DevicePropertyNames.RO_PRODUCT_MODEL to "Pixel 6",
        DevicePropertyNames.RO_SF_LCD_DENSITY to "320",
      )
    )
    fakeSession.deviceServices.configureDeviceProperties(
      DeviceSelector.fromSerialNumber(SerialNumbers.PHYSICAL2_USB),
      mapOf(
        "ro.serialno" to SerialNumbers.PHYSICAL2_USB,
        DevicePropertyNames.RO_BUILD_VERSION_SDK to "31",
        DevicePropertyNames.RO_PRODUCT_MANUFACTURER to "Google",
        DevicePropertyNames.RO_PRODUCT_MODEL to "Pixel 6",
        DevicePropertyNames.RO_SF_LCD_DENSITY to "320",
      )
    )
    fakeSession.deviceServices.configureDeviceProperties(
      DeviceSelector.fromSerialNumber(SerialNumbers.PHYSICAL2_WIFI),
      mapOf(
        "ro.serialno" to SerialNumbers.PHYSICAL2_USB,
        DevicePropertyNames.RO_BUILD_VERSION_SDK to "31",
        DevicePropertyNames.RO_PRODUCT_MANUFACTURER to "Google",
        DevicePropertyNames.RO_PRODUCT_MODEL to "Pixel 6",
        DevicePropertyNames.RO_SF_LCD_DENSITY to "320",
      )
    )
    fakeSession.deviceServices.configureDeviceProperties(
      DeviceSelector.fromSerialNumber(SerialNumbers.EMULATOR),
      mapOf(
        "ro.serialno" to "EMULATOR31X3X7X0",
        DevicePropertyNames.RO_BUILD_VERSION_SDK to "31",
        DevicePropertyNames.RO_PRODUCT_MANUFACTURER to "Google",
        DevicePropertyNames.RO_PRODUCT_MODEL to "sdk_goog3_x86_64",
        DevicePropertyNames.RO_SF_LCD_DENSITY to "320",
      )
    )
    for (serial in SerialNumbers.ALL) {
      fakeSession.deviceServices.configureShellCommand(
        DeviceSelector.fromSerialNumber(serial),
        command = "wm size",
        stdout = "Physical size: 2000x1500\n"
      )
    }
  }

  private fun setDevices(vararg serialNumber: String) {
    fakeSession.hostServices.devices =
      DeviceList(serialNumber.map { DeviceInfo(it, DeviceState.ONLINE) }, emptyList())
  }

  @Test
  fun physicalUsbWiFiProperties() {
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
      val usbHandle = checkNotNull(handlesByType[ConnectionType.USB])
      assertThat(usbHandle.state.connectedDevice?.serialNumber)
        .isEqualTo(SerialNumbers.PHYSICAL1_USB)
      usbHandle.state.properties.apply {
        assertThat(wearPairingId).isEqualTo(SerialNumbers.PHYSICAL1_USB)
        assertThat(disambiguator).isEqualTo(SerialNumbers.PHYSICAL1_USB)
        assertThat(deviceInfoProto.mdnsConnectionType).isEqualTo(MdnsConnectionType.MDNS_NONE)
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
          .isEqualTo(MdnsConnectionType.MDNS_AUTO_CONNECT_TLS)
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

    runBlockingWithTimeout {
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
      assertThat(handle1.state).isInstanceOf(Connected::class.java)

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

  private fun DeviceProperties.checkPhysicalDeviceProperties() {
    assertThat(resolution).isEqualTo(Resolution(2000, 1500))
    assertThat(resolutionDp).isEqualTo(Resolution(1000, 750))

    deviceInfoProto.apply {
      assertThat(manufacturer).isEqualTo("Google")
      assertThat(model).isEqualTo("Pixel 6")
      assertThat(deviceType).isEqualTo(DeviceType.LOCAL_PHYSICAL)
      assertThat(deviceProvisionerId).isEqualTo(PhysicalDeviceProvisionerPlugin.PLUGIN_ID)
    }
  }

  @Test
  fun physicalDeviceMaintainsIdentityOnReconnection() {
    val channel = Channel<List<DeviceHandle>>(1)
    fakeSession.scope.launch { provisioner.devices.collect { channel.send(it) } }

    runBlockingWithTimeout {
      setDevices(SerialNumbers.PHYSICAL1_USB)

      val originalHandle =
        channel.receiveUntilPassing { handles ->
          assertThat(handles).hasSize(1)

          val handle = handles[0]
          assertThat(handle.state).isInstanceOf(Connected::class.java)

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
        assertThat(handle.state).isInstanceOf(Connected::class.java)
      }
    }
  }

  @Test
  fun unauthorizedPhysicalDevice() {
    val handles = Channel<List<DeviceHandle>>(1)
    val unclaimedDevices = Channel<List<ConnectedDevice>>(1)
    fakeSession.scope.launch { provisioner.devices.collect { handles.send(it) } }
    fakeSession.scope.launch { provisioner.unclaimedDevices.collect { unclaimedDevices.send(it) } }

    runBlockingWithTimeout {
      // Show the device as unauthorized
      fakeSession.hostServices.devices =
        DeviceList(
          listOf(DeviceInfo(SerialNumbers.PHYSICAL1_USB, DeviceState.UNAUTHORIZED)),
          emptyList()
        )

      unclaimedDevices.receiveUntilPassing { devices ->
        assertThat(devices).hasSize(1)

        val device = devices[0]
        assertThat(device.deviceInfo.deviceState).isEqualTo(DeviceState.UNAUTHORIZED)
      }

      // Now show the device as online
      setDevices(SerialNumbers.PHYSICAL1_USB)

      unclaimedDevices.receiveUntilPassing { devices -> assertThat(devices).isEmpty() }

      handles.receiveUntilPassing { handles ->
        assertThat(handles).hasSize(1)

        val handle = handles[0]
        assertThat(handle.state).isInstanceOf(Connected::class.java)
        assertThat(handle.state.properties.connectionType).isEqualTo(ConnectionType.USB)
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
          // assertThat(handle).isInstanceOf(DefaultDeviceHandle::class.java)
          assertThat(handle.state).isInstanceOf(Connected::class.java)

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
      yieldUntil { originalHandle.scope.coroutineContext.job.isCancelled }

      // We get two messages on the channel, one for the device becoming disconnected, and one
      // for the device list changing. We don't know what order they will occur in, but it
      // doesn't matter; just check the state after the second.
      channel.receiveUntilPassing { handles ->
        assertThat(handles).isEmpty()
        assertThat(originalHandle.state).isInstanceOf(Disconnected::class.java)
      }

      setDevices(SerialNumbers.EMULATOR)

      channel.receiveUntilPassing { handles ->
        assertThat(handles).hasSize(1)

        val handle = handles[0]
        assertThat(handle.id).isNotEqualTo(originalHandle.id)
        assertThat(handle).isNotSameAs(originalHandle)
        assertThat(handle.state).isInstanceOf(Connected::class.java)
      }
    }
  }

  @Test
  fun findConnectedDeviceHandle() {
    runBlockingWithTimeout {
      setDevices(SerialNumbers.PHYSICAL2_WIFI, SerialNumbers.EMULATOR)

      val emulator =
        async(Dispatchers.IO) {
          provisioner.findConnectedDeviceHandle(
            DeviceSelector.fromSerialNumber(SerialNumbers.EMULATOR),
            Duration.ofSeconds(5)
          )
        }

      val handle = emulator.await()
      assertThat(handle?.state?.connectedDevice?.serialNumber).isEqualTo(SerialNumbers.EMULATOR)
    }
  }

  /**
   * If there are exceptions during claim, then we should wait until the next time the device comes
   * online, then try to offer it again.
   */
  @Test
  fun deviceErrorDuringClaim() {
    runBlockingWithTimeout {
      val channel = Channel<List<DeviceHandle>>(1)
      fakeSession.scope.launch { provisioner.devices.collect { channel.send(it) } }

      // Make an error occur for both plugins
      fakeSession.deviceServices.shellNumTimeouts = 2
      setDevices(SerialNumbers.PHYSICAL1_USB)
      fakeSession.hostServices.devices =
        DeviceList(listOf(DeviceInfo(SerialNumbers.PHYSICAL1_USB, DeviceState.ONLINE)), emptyList())

      yieldUntil {
        fakeSession.host.loggerFactory.logEntries.any {
          it.message == "Device ${SerialNumbers.PHYSICAL1_USB} not claimed by any provisioner"
        }
      }

      fakeSession.hostServices.devices =
        DeviceList(
          listOf(DeviceInfo(SerialNumbers.PHYSICAL1_USB, DeviceState.OFFLINE)),
          emptyList()
        )

      yieldUntil {
        fakeSession.host.loggerFactory.logEntries.any {
          it.message == "Device ${SerialNumbers.PHYSICAL1_USB} is offline"
        }
      }

      fakeSession.hostServices.devices =
        DeviceList(listOf(DeviceInfo(SerialNumbers.PHYSICAL1_USB, DeviceState.ONLINE)), emptyList())
      channel.receiveUntilPassing { devices -> assertThat(devices).hasSize(1) }
    }
  }
}
