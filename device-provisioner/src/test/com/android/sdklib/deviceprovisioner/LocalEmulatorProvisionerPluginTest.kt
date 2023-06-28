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

import com.android.adblib.testing.FakeAdbSession
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.DeviceState.Connected
import com.android.sdklib.deviceprovisioner.DeviceState.Disconnected
import com.android.sdklib.devices.Abi
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.repository.targets.SystemImage
import com.google.common.truth.Truth.assertThat
import java.nio.file.Path
import java.time.Duration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Test

class LocalEmulatorProvisionerPluginTest {

  val session = FakeAdbSession()
  private val avdManager = FakeAvdManager(session)
  private val deviceIcons =
    DeviceIcons(EmptyIcon.DEFAULT, EmptyIcon.DEFAULT, EmptyIcon.DEFAULT, EmptyIcon.DEFAULT)
  val plugin =
    LocalEmulatorProvisionerPlugin(
      session.scope,
      session,
      avdManager,
      deviceIcons,
      TestDefaultDeviceActionPresentation,
      Duration.ofMillis(100),
    )
  private val provisioner = DeviceProvisioner.create(session, listOf(plugin), deviceIcons)

  @After
  fun tearDown() {
    avdManager.close()
    session.close()
  }

  @Test
  fun offlineDevices(): Unit = runBlockingWithTimeout {
    avdManager.createAvd()
    avdManager.createAvd()

    yieldUntil { provisioner.devices.value.size == 2 }
    val devices = provisioner.devices.value
    assertThat(devices.map { it.state.properties.title })
      .containsExactly("Fake Device 1", "Fake Device 2")
    checkProperties(devices[0].state.properties as LocalEmulatorProperties)
  }

  @Test
  fun removeOfflineDevice(): Unit = runBlockingWithTimeout {
    val n = 20
    repeat(n) { avdManager.createAvd() }
    val avds = avdManager.rescanAvds()
    yieldUntil { provisioner.devices.value.size == n }

    avdManager.deleteAvd(avds[0])

    yieldUntil { provisioner.devices.value.size == n - 1 }
    val displayNames = provisioner.devices.value.map { it.state.properties.title }
    assertThat(displayNames).doesNotContain(avds[0].displayName)
    assertThat(displayNames).contains(avds[1].displayName)
  }

  @Test
  fun removedOfflineDeviceScopeIsCancelled(): Unit = runBlockingWithTimeout {
    avdManager.createAvd()
    yieldUntil { provisioner.devices.value.size == 1 }

    val handle = provisioner.devices.value[0]
    val job = handle.scope.launch { handle.stateFlow.collect {} }

    avdManager.deleteAvd(avdManager.avds[0])

    yieldUntil { provisioner.devices.value.isEmpty() }
    job.join()
  }

  @Test
  fun startAndStopDevice(): Unit = runBlockingWithTimeout {
    avdManager.createAvd()

    yieldUntil { provisioner.devices.value.size == 1 }

    val handle = provisioner.devices.value[0]
    handle.activationAction?.activate()

    assertThat(handle.state.connectedDevice).isNotNull()

    assertThat(provisioner.devices.value.map { it.state.properties.title })
      .containsExactly("Fake Device 1")
    val properties = provisioner.devices.value[0].state.properties as LocalEmulatorProperties
    checkProperties(properties)
    assertThat(properties.androidRelease).isEqualTo("11")

    handle.deactivationAction?.deactivate()

    assertThat(handle.state.connectedDevice).isNull()

    assertThat(handle.state).isInstanceOf(Disconnected::class.java)
    assertThat(provisioner.devices.value.map { it.state.properties.title })
      .containsExactly("Fake Device 1")
  }

  @Test
  fun coldBootDevice(): Unit = runBlockingWithTimeout {
    avdManager.createAvd()

    yieldUntil { provisioner.devices.value.size == 1 }

    val handle = provisioner.devices.value[0]
    handle.coldBootAction?.activate()

    assertThat(handle.state.connectedDevice).isNotNull()
  }

  @Test
  fun isPairable() {
    val api29WithPlay = avdManager.makeAvdInfo(1, AndroidVersion(29), hasPlayStore = true)
    val api31NoPlay = avdManager.makeAvdInfo(2, AndroidVersion(29), hasPlayStore = false)
    val api30WithPlay = avdManager.makeAvdInfo(3, AndroidVersion(30), hasPlayStore = true)
    fun build(info: AvdInfo) = LocalEmulatorProperties.build(info) { icon = EmptyIcon.DEFAULT }
    assertThat(build(api29WithPlay).wearPairingId).isNull()
    assertThat(build(api31NoPlay).wearPairingId).isNull()
    assertThat(build(api30WithPlay).wearPairingId).isNotNull()
  }

  @Test
  fun isActivatable() = runBlockingWithTimeout {
    avdManager.createAvd()

    yieldUntil { provisioner.devices.value.size == 1 }

    val handle = provisioner.devices.value[0]
    val activationAction = handle.activationAction!!

    activationAction.presentation.takeWhile { !it.enabled }.collect()

    avdManager.avds[0] = avdManager.makeAvdInfo(1, avdStatus = AvdStatus.ERROR_IMAGE_MISSING)

    // The action should become disabled.
    yieldUntil { activationAction.presentation.value.enabled == false }
  }

  @Test
  fun repair() = runBlockingWithTimeout {
    avdManager.createAvd(avdManager.makeAvdInfo(1, avdStatus = AvdStatus.ERROR_IMAGE_MISSING))

    yieldUntil { provisioner.devices.value.size == 1 }

    val handle = provisioner.devices.value[0]
    val activationAction = handle.activationAction!!
    val repairAction = handle.repairDeviceAction!!

    repairAction.presentation.takeWhile { !it.enabled }.collect()

    repairAction.repair()

    // Should become possible to activate the device
    activationAction.presentation.takeWhile { !it.enabled }.collect()
    repairAction.presentation.takeWhile { it.enabled }.collect()
  }

  @Test
  fun tvDeviceType() = runBlockingWithTimeout {
    avdManager.createAvd(avdManager.makeAvdInfo(1, tag = SystemImage.GOOGLE_TV_TAG))

    yieldUntil { provisioner.devices.value.size == 1 }

    val handle = provisioner.devices.value[0]
    assertThat(handle.state.properties.deviceType).isEqualTo(DeviceType.TV)

    handle.activationAction?.activate()

    assertThat(handle.state.properties.deviceType).isEqualTo(DeviceType.TV)
  }

  @Test
  fun editDevice() = runBlockingWithTimeout {
    avdManager.createAvd(avdManager.makeAvdInfo(1, tag = SystemImage.GOOGLE_TV_TAG))

    val channel = Channel<DeviceState>()

    yieldUntil { provisioner.devices.value.size == 1 }

    val handle = provisioner.devices.value[0]
    val name = handle.state.properties.title

    val job = launch { handle.stateFlow.collect { channel.send(it) } }

    // Editing the device adds "Edited" to its name
    handle.editAction?.edit()

    channel.receiveUntilPassing { newState ->
      assertThat(newState.properties.title).isEqualTo("$name Edited")
    }

    // Editing the device while it's online puts it in AvdChangedError state
    handle.activationAction?.activate()
    channel.receiveUntilPassing { newState ->
      assertThat(newState).isInstanceOf(Connected::class.java)
    }
    handle.editAction?.edit()

    channel.receiveUntilPassing { newState ->
      assertThat(newState.error).isEqualTo(AvdChangedError)
      assertThat(newState.properties.title).isEqualTo("$name Edited")
    }

    // Deactivating the device causes the prior edit to take effect
    handle.deactivationAction?.deactivate()

    channel.receiveUntilPassing { newState ->
      assertThat(newState.error).isNull()
      assertThat(newState.properties.title).isEqualTo("$name Edited Edited")
    }

    job.cancel()
  }

  private fun checkProperties(properties: LocalEmulatorProperties) {
    assertThat(properties.manufacturer).isEqualTo(MANUFACTURER)
    assertThat(properties.avdConfigProperties[AvdManager.AVD_INI_DEVICE_MANUFACTURER])
      .isEqualTo(MANUFACTURER)
    assertThat(properties.model).isEqualTo(MODEL)
    assertThat(properties.androidVersion).isEqualTo(API_LEVEL)
    assertThat(properties.abi).isEqualTo(ABI)
    assertThat(properties.avdName).startsWith("fake_avd_")
    assertThat(properties.displayName).startsWith("Fake Device")
    assertThat(Path.of(properties.wearPairingId!!).parent).isEqualTo(Path.of("/tmp/fake_avds"))
  }

  companion object {
    const val MANUFACTURER = "Google"
    const val MODEL = "Pixel 6"
    val API_LEVEL = AndroidVersion(31)
    val ABI = Abi.ARM64_V8A
    const val RELEASE = "11"
  }
}
