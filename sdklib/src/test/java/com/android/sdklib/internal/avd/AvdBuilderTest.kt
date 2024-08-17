/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.sdklib.internal.avd

import com.android.prefs.AbstractAndroidLocations
import com.android.repository.testframework.FakeProgressIndicator
import com.android.resources.ScreenOrientation
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.file.createInMemoryFileSystem
import com.android.testutils.file.recordExistingFile
import com.android.testutils.file.someRoot
import com.android.utils.NullLogger
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.full.memberProperties
import org.junit.Test

class AvdBuilderTest {

  private val fileSystem = createInMemoryFileSystem()
  val root = fileSystem.someRoot
  val prefsRoot = root.resolve("android")
  val avdFolder = prefsRoot.resolve(AbstractAndroidLocations.FOLDER_AVD)
  val sdkHandler = AndroidSdkHandler(root.resolve("sdk"), prefsRoot)
  val deviceManager = DeviceManager.createInstance(sdkHandler, NullLogger.getLogger())
  val avdManager =
    AvdManager.createInstance(sdkHandler, avdFolder, deviceManager, NullLogger.getLogger())

  private fun createPixel8Builder(): AvdBuilder {
    val pixel8 = deviceManager.getDevice("pixel_8", "Google")!!
    return avdManager.createAvdBuilder(pixel8)
  }

  @Test
  fun createAvdBuilder() {
    val avdBuilder = createPixel8Builder()

    with(avdBuilder) {
      assertThat(displayName).isEqualTo("Pixel 8")
      assertThat(systemImage).isNull()
      assertThat(sdCard).isNull()
      assertThat(skin).isNotNull()
      assertThat(showDeviceFrame).isTrue()
      assertThat(screenOrientation).isEqualTo(ScreenOrientation.PORTRAIT)

      assertThat(cpuCoreCount).isEqualTo(1)
      assertThat(ram).isEqualTo(EmulatedProperties.MAX_DEFAULT_RAM_SIZE)
      assertThat(vmHeap.size).isGreaterThan(0)
      assertThat(internalStorage).isEqualTo(EmulatedProperties.DEFAULT_INTERNAL_STORAGE)

      assertThat(frontCamera).isEqualTo(AvdCamera.NONE)
      assertThat(backCamera).isEqualTo(AvdCamera.NONE)

      assertThat(gpuMode).isEqualTo(GpuMode.OFF)
      assertThat(enableKeyboard).isTrue()

      assertThat(networkLatency).isEqualTo(AvdNetworkLatency.NONE)
      assertThat(networkSpeed).isEqualTo(AvdNetworkSpeed.FULL)

      assertThat(bootMode).isEqualTo(QuickBoot)
    }
  }

  @Test
  fun gpuMode() {
    val builder = createPixel8Builder()

    builder.gpuMode = GpuMode.OFF
    assertThat(builder.configProperties()).containsEntry(ConfigKey.GPU_EMULATION, "no")

    builder.gpuMode = GpuMode.AUTO
    assertThat(builder.configProperties()).containsEntry(ConfigKey.GPU_EMULATION, "yes")
  }

  @Test
  fun createForExistingDevice() {
    recordPlayStoreSysImg33ext4(root)

    val systemImages = sdkHandler.getSystemImageManager(FakeProgressIndicator()).getImages()
    val android33ext4 = systemImages.first()

    val pixel8 = deviceManager.getDevice("pixel_8", "Google")!!
    val avdBuilder = avdManager.createAvdBuilder(pixel8)
    avdBuilder.metadataIniPath = avdFolder.resolve("Pixel_8.ini")
    avdBuilder.avdFolder = avdFolder.resolve("Pixel_8.avd")
    avdBuilder.systemImage = android33ext4

    val createdAvd = avdManager.createAvd(avdBuilder)
    checkNotNull(createdAvd)

    avdManager.reloadAvds()
    assertThat(avdManager.allAvds).hasLength(1)

    val avd = avdManager.allAvds[0]
    val builderFromDisk = AvdBuilder.createForExistingDevice(pixel8, avd)

    assertThat(avdBuilder.systemImage).isEqualTo(builderFromDisk.systemImage)
    assertThat(avdBuilder.skin).isEqualTo(builderFromDisk.skin)
    assertThat(avdBuilder.sdCard).isEqualTo(builderFromDisk.sdCard)
    assertThat(avdBuilder.androidVersion).isEqualTo(builderFromDisk.androidVersion)

    for (property in AvdBuilder::class.memberProperties) {
      assertWithMessage(property.name)
        .that(property.get(builderFromDisk))
        .isEqualTo(property.get(avdBuilder))
    }
  }

  @Throws(IOException::class)
  private fun recordPlayStoreSysImg33ext4(root: Path) {
    root
      .resolve("sdk/system-images/android-33-ext4/google_apis_playstore/x86_64/system.img")
      .recordExistingFile()
    Files.createDirectories(
      root.resolve("sdk/system-images/android-33-ext4/google_apis_playstore/x86_64/data")
    )
    root
      .resolve("sdk/system-images/android-33-ext4/google_apis_playstore/x86_64/package.xml")
      .recordExistingFile(
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<sys-img:sdk-sys-img xmlns:sys-img="http://schemas.android.com/sdk/android/repo/sys-img2/03"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <localPackage path="system-images;android-33-ext4;google_apis_playstore;x86_64" obsolete="false">
    <type-details xsi:type="sys-img:sysImgDetailsType">
      <api-level>33</api-level>
      <extension-level>4</extension-level>
      <base-extension>false</base-extension>
      <tag>
        <id>google_apis_playstore</id>
        <display>Google Play</display>
      </tag>
      <vendor>
        <id>google</id>
        <display>Google Inc.</display>
      </vendor>
      <abi>x86_64</abi>
    </type-details>
    <revision>
      <major>9</major>
    </revision>
    <display-name>Google APIs with Playstore Intel x86 Atom System Image</display-name>
  </localPackage>
</sys-img:sdk-sys-img>
"""
      )
  }
}
