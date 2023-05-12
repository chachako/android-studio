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

import com.android.adblib.DeviceInfo
import com.android.adblib.DeviceList
import com.android.adblib.DevicePropertyNames
import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState
import com.android.adblib.testing.FakeAdbSession
import com.android.sdklib.AndroidVersion
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.repository.IdDisplay
import com.android.sdklib.repository.targets.SystemImage
import java.nio.file.Path

class FakeAvdManager(val session: FakeAdbSession) : LocalEmulatorProvisionerPlugin.AvdManager {
  val avds = mutableListOf<AvdInfo>()
  val runningDevices = mutableSetOf<FakeEmulatorConsole>()
  var avdIndex = 1

  override suspend fun rescanAvds(): List<AvdInfo> = synchronized(avds) { avds.toList() }

  override suspend fun createAvd(): Boolean {
    createAvd(makeAvdInfo(avdIndex++))
    return true
  }

  fun createAvd(avdInfo: AvdInfo) {
    synchronized(avds) { avds += avdInfo }
  }

  fun makeAvdInfo(
    index: Int,
    androidVersion: AndroidVersion = LocalEmulatorProvisionerPluginTest.API_LEVEL,
    hasPlayStore: Boolean = true,
    avdStatus: AvdInfo.AvdStatus = AvdInfo.AvdStatus.OK,
    tag: IdDisplay = SystemImage.DEFAULT_TAG,
  ): AvdInfo {
    val basePath = Path.of("/tmp/fake_avds/$index")
    return AvdInfo(
      "fake_avd_$index",
      basePath.resolve("config.ini"),
      basePath,
      null,
      mapOf(
        AvdManager.AVD_INI_DEVICE_MANUFACTURER to LocalEmulatorProvisionerPluginTest.MANUFACTURER,
        AvdManager.AVD_INI_DEVICE_NAME to LocalEmulatorProvisionerPluginTest.MODEL,
        AvdManager.AVD_INI_ANDROID_API to androidVersion.apiStringWithoutExtension,
        AvdManager.AVD_INI_ABI_TYPE to LocalEmulatorProvisionerPluginTest.ABI.toString(),
        AvdManager.AVD_INI_DISPLAY_NAME to "Fake Device $index",
        AvdManager.AVD_INI_PLAYSTORE_ENABLED to hasPlayStore.toString(),
        AvdManager.AVD_INI_TAG_ID to tag.id,
        AvdManager.AVD_INI_TAG_DISPLAY to tag.display,
      ),
      avdStatus
    )
  }

  override suspend fun editAvd(avdInfo: AvdInfo): Boolean =
    synchronized(avds) {
      avds.remove(avdInfo)
      avds += makeAvdInfo(avdIndex++)
      return true
    }

  override suspend fun startAvd(avdInfo: AvdInfo, coldBoot: Boolean) {
    val device =
      FakeEmulatorConsole(avdInfo.name, avdInfo.dataFolderPath.toString()) { doStopAvd(avdInfo) }
    val selector = DeviceSelector.fromSerialNumber("emulator-${device.port}")
    session.deviceServices.configureDeviceProperties(
      selector,
      mapOf(
        "ro.serialno" to "EMULATOR31X3X7X0",
        DevicePropertyNames.RO_BUILD_VERSION_SDK to
          LocalEmulatorProvisionerPluginTest.API_LEVEL.apiString,
        DevicePropertyNames.RO_BUILD_VERSION_RELEASE to LocalEmulatorProvisionerPluginTest.RELEASE,
        DevicePropertyNames.RO_PRODUCT_MANUFACTURER to
          LocalEmulatorProvisionerPluginTest.MANUFACTURER,
        DevicePropertyNames.RO_PRODUCT_MODEL to LocalEmulatorProvisionerPluginTest.MODEL,
        DevicePropertyNames.RO_PRODUCT_CPU_ABI to LocalEmulatorProvisionerPluginTest.ABI.toString()
      )
    )
    session.deviceServices.configureShellCommand(
      selector,
      command = "wm size",
      stdout = "Physical size: 1024x768\n"
    )
    device.start()
    runningDevices += device
    updateDevices()
  }

  override suspend fun stopAvd(avdInfo: AvdInfo) {
    doStopAvd(avdInfo)
  }

  override suspend fun showOnDisk(avdInfo: AvdInfo) {
    // no-op
  }

  override suspend fun duplicateAvd(avdInfo: AvdInfo) {
    // not used
  }

  override suspend fun wipeData(avdInfo: AvdInfo) {
    // not used
  }

  private fun doStopAvd(avdInfo: AvdInfo) {
    runningDevices.removeIf { it.avdPath == avdInfo.dataFolderPath.toString() }
    updateDevices()
  }

  override suspend fun deleteAvd(avdInfo: AvdInfo) {
    synchronized(avds) { avds.remove(avdInfo) }
  }

  override suspend fun downloadAvdSystemImage(avdInfo: AvdInfo) {
    avds[avds.indexOf(avdInfo)] =
      AvdInfo(
        avdInfo.name,
        avdInfo.iniFile,
        avdInfo.dataFolderPath,
        avdInfo.systemImage,
        avdInfo.properties,
        AvdInfo.AvdStatus.OK
      )
  }

  fun close() {
    runningDevices.forEach(FakeEmulatorConsole::close)
  }

  private fun updateDevices() {
    session.hostServices.devices =
      DeviceList(
        runningDevices.map { DeviceInfo("emulator-${it.port}", DeviceState.ONLINE) },
        emptyList()
      )
  }
}
