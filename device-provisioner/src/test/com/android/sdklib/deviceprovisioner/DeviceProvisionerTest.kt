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
import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.junit.Test

/** Tests generic functionality of the [DeviceProvisioner]. */
class DeviceProvisionerTest : DeviceProvisionerTestFixture() {

  private val plugin = PhysicalDeviceProvisionerPlugin(fakeSession.scope, deviceIcons)
  private val provisioner =
    DeviceProvisioner.create(fakeSession.scope, fakeSession, listOf(plugin), deviceIcons)

  @Test
  fun findConnectedDeviceHandle() {
    runBlockingWithTimeout {
      setDevices(SerialNumbers.PHYSICAL2_WIFI, SerialNumbers.EMULATOR)

      val emulator =
        async(Dispatchers.IO) {
          provisioner.findConnectedDeviceHandle(
            DeviceSelector.fromSerialNumber(SerialNumbers.EMULATOR),
            Duration.ofSeconds(5),
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
          emptyList(),
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
