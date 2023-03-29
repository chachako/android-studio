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

import com.android.adblib.DeviceInfo
import com.android.adblib.DeviceList
import com.android.adblib.DevicePropertyNames
import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState
import com.android.adblib.serialNumber
import com.android.adblib.testing.FakeAdbSession
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.sdklib.deviceprovisioner.DeviceState.Connected
import com.android.sdklib.deviceprovisioner.DeviceState.Disconnected
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.junit.Test

/**
 * Tests the [DeviceProvisioner] and its two basic plugins, [PhysicalDeviceProvisionerPlugin] and
 * [DefaultProvisionerPlugin]
 */
class DeviceProvisionerTest {
  val fakeSession = FakeAdbSession()

  val plugin = PhysicalDeviceProvisionerPlugin(fakeSession.scope)
  val provisioner = DeviceProvisioner.create(fakeSession.scope, fakeSession, listOf(plugin))

  object SerialNumbers {
    val physicalUsb = "X1058A"
    val physicalWifi = "adb-X1BQ704RX2B-VQ4ADB._adb-tls-connect._tcp."
    val emulator = "emulator-5554"
  }

  init {
    fakeSession.deviceServices.configureDeviceProperties(
      DeviceSelector.fromSerialNumber(SerialNumbers.physicalUsb),
      mapOf(
        "ro.serialno" to SerialNumbers.physicalUsb,
        DevicePropertyNames.RO_BUILD_VERSION_SDK to "31",
        DevicePropertyNames.RO_PRODUCT_MANUFACTURER to "Google",
        DevicePropertyNames.RO_PRODUCT_MODEL to "Pixel 6"
      )
    )
    fakeSession.deviceServices.configureDeviceProperties(
      DeviceSelector.fromSerialNumber(SerialNumbers.physicalWifi),
      mapOf(
        "ro.serialno" to "X1BQ704RX2B",
        DevicePropertyNames.RO_BUILD_VERSION_SDK to "31",
        DevicePropertyNames.RO_PRODUCT_MANUFACTURER to "Google",
        DevicePropertyNames.RO_PRODUCT_MODEL to "Pixel 6"
      )
    )
    fakeSession.deviceServices.configureDeviceProperties(
      DeviceSelector.fromSerialNumber(SerialNumbers.emulator),
      mapOf(
        "ro.serialno" to "EMULATOR31X3X7X0",
        DevicePropertyNames.RO_BUILD_VERSION_SDK to "31",
        DevicePropertyNames.RO_PRODUCT_MANUFACTURER to "Google",
        DevicePropertyNames.RO_PRODUCT_MODEL to "sdk_goog3_x86_64"
      )
    )
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
      setDevices(SerialNumbers.physicalUsb, SerialNumbers.physicalWifi)

      // The plugin adds the devices one at a time, so there are two events here
      channel.receiveUntilPassing { handles ->
        assertThat(handles).hasSize(2)

        val handlesByType =
          handles.associateBy { (it.state.properties as PhysicalDeviceProperties).connectionType }

        assertThat(handlesByType).hasSize(2)
        val usbHandle = checkNotNull(handlesByType[ConnectionType.USB])
        assertThat(usbHandle.state.connectedDevice?.serialNumber)
          .isEqualTo(SerialNumbers.physicalUsb)
        assertThat(usbHandle.state.properties.wearPairingId).isEqualTo(SerialNumbers.physicalUsb)

        val wifiHandle = checkNotNull(handlesByType[ConnectionType.WIFI])
        assertThat(wifiHandle.state.connectedDevice?.serialNumber)
          .isEqualTo(SerialNumbers.physicalWifi)
        assertThat(wifiHandle.state.properties.wearPairingId).isEqualTo("X1BQ704RX2B")
      }
    }
  }

  @Test
  fun physicalDeviceMaintainsIdentityOnReconnection() {
    val channel = Channel<List<DeviceHandle>>(1)
    fakeSession.scope.launch { provisioner.devices.collect { channel.send(it) } }

    runBlockingWithTimeout {
      setDevices(SerialNumbers.physicalUsb)

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
        assertThat(handles).hasSize(1)

        val handle = handles[0]
        assertThat(handle).isSameAs(originalHandle)
        assertThat(handle.state).isInstanceOf(Disconnected::class.java)
      }

      setDevices(SerialNumbers.physicalUsb)

      channel.receiveUntilPassing { handles ->
        assertThat(handles).hasSize(1)

        val handle = handles[0]
        assertThat(handle).isSameAs(originalHandle)
        assertThat(handle.state).isInstanceOf(Connected::class.java)
      }
    }
  }

  @Test
  fun unauthorizedPhysicalDevice() {
    val channel = Channel<List<DeviceHandle>>(1)
    fakeSession.scope.launch { provisioner.devices.collect { channel.send(it) } }

    runBlockingWithTimeout {
      // Show the device as unauthorized
      fakeSession.hostServices.devices =
        DeviceList(
          listOf(DeviceInfo(SerialNumbers.physicalUsb, DeviceState.UNAUTHORIZED)),
          emptyList()
        )

      val offlineHandle =
        channel.receiveUntilPassing { handles ->
          assertThat(handles).hasSize(1)

          val handle = handles[0]
          assertThat(handle.state).isInstanceOf(Connected::class.java)
          assertThat(handle.state.properties.title)
            .isEqualTo("Unknown device (${SerialNumbers.physicalUsb})")

          handle
        }

      // Now show the device as online
      setDevices(SerialNumbers.physicalUsb)

      // Verify that we never see the offline handle as online
      val states = async {
        offlineHandle.stateFlow
          .flatMapLatest {
            when (val device = it.connectedDevice) {
              null -> flowOf(null)
              else -> device.deviceInfoFlow.map { info -> info.deviceState }
            }
          }
          .takeWhile { it != null }
          .toList()
      }

      assertThat(states.await()).doesNotContain(DeviceState.ONLINE)

      channel.receiveUntilPassing { handles ->
        assertThat(handles).hasSize(1)

        val handle = handles[0]
        assertThat(handle).isNotSameAs(offlineHandle)
        assertThat(handle.state).isInstanceOf(Connected::class.java)
        assertThat(handle.state.properties).isInstanceOf(PhysicalDeviceProperties::class.java)
      }
    }
  }

  @Test
  fun defaultDeviceIsDistinctOnReconnection() {
    val channel = Channel<List<DeviceHandle>>(1)
    fakeSession.scope.launch { provisioner.devices.collect { channel.send(it) } }

    runBlockingWithTimeout {
      setDevices(SerialNumbers.emulator)

      val originalHandle =
        channel.receiveUntilPassing { handles ->
          assertThat(handles).hasSize(1)

          val handle = handles[0]
          // assertThat(handle).isInstanceOf(DefaultDeviceHandle::class.java)
          assertThat(handle.state).isInstanceOf(Connected::class.java)

          handle
        }

      // Now we also want to update whenever the state changes
      fakeSession.scope.launch {
        originalHandle.stateFlow.collect { channel.send(provisioner.devices.value) }
      }

      setDevices()

      // We get two messages on the channel, one for the device becoming disconnected, and one
      // for the device list changing. We don't know what order they will occur in, but it
      // doesn't matter; just check the state after the second.
      channel.receiveUntilPassing { handles ->
        assertThat(handles).isEmpty()
        assertThat(originalHandle.state).isInstanceOf(Disconnected::class.java)
        assertThat(originalHandle.scope.coroutineContext.job.isCancelled).isTrue()
      }

      setDevices(SerialNumbers.emulator)

      channel.receiveUntilPassing { handles ->
        assertThat(handles).hasSize(1)

        val handle = handles[0]
        assertThat(handle).isNotSameAs(originalHandle)
        assertThat(handle.state).isInstanceOf(Connected::class.java)
      }
    }
  }

  @Test
  fun findConnectedDeviceHandle() {
    runBlockingWithTimeout {
      setDevices(SerialNumbers.physicalWifi, SerialNumbers.emulator)

      val emulator =
        async(Dispatchers.IO) {
          provisioner.findConnectedDeviceHandle(
            DeviceSelector.fromSerialNumber(SerialNumbers.emulator),
            Duration.ofSeconds(5)
          )
        }

      val handle = emulator.await()
      assertThat(handle?.state?.connectedDevice?.serialNumber).isEqualTo(SerialNumbers.emulator)
    }
  }
}
