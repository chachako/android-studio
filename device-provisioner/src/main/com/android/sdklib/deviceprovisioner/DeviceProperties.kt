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

import com.android.adblib.DevicePropertyNames.RO_BUILD_CHARACTERISTICS
import com.android.adblib.DevicePropertyNames.RO_BUILD_VERSION_CODENAME
import com.android.adblib.DevicePropertyNames.RO_BUILD_VERSION_RELEASE
import com.android.adblib.DevicePropertyNames.RO_BUILD_VERSION_SDK
import com.android.adblib.DevicePropertyNames.RO_MANUFACTURER
import com.android.adblib.DevicePropertyNames.RO_MODEL
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_CPU_ABI
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_MANUFACTURER
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_MODEL
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi

/**
 * Stores various properties about a device that are generally stable.
 *
 * This is designed to be extended by subclasses through composition.
 */
interface DeviceProperties {

  val model: String?
  val manufacturer: String?
  val abi: Abi?
  /**
   * The Android API level. May include a codename if not a release version.
   *
   * This should be set for any device we can read properties from; if this is null, the device is
   * probably offline.
   */
  val androidVersion: AndroidVersion?
  /** The user-visible version of Android, like "7.1" or "11". */
  val androidRelease: String?
  /** The class of hardware of the device, e.g. handheld, TV, auto. */
  val deviceType: DeviceType?

  /**
   * If true, the device is running on emulated / virtualized hardware; if false, it is running on
   * native hardware.
   */
  val isVirtual: Boolean?

  /**
   * A string ideally unique to the device instance (e.g. serial number or emulator console port),
   * used for disambiguating this device from others with similar properties.
   */
  val disambiguator: String?

  /** Default implementation of device title; may be overridden. */
  val title: String
    get() {
      return when {
        manufacturer.isNullOrBlank() -> model ?: "Unknown"
        model.isNullOrBlank() -> "$manufacturer Device"
        else -> "$manufacturer $model"
      }
    }

  companion object {
    /** Builds a basic DeviceProperties instance with no additional fields. */
    fun build(block: Builder.() -> Unit): DeviceProperties = Builder().apply(block).buildBase()
  }

  open class Builder {

    var manufacturer: String? = null
    var model: String? = null
    var abi: Abi? = null
    var androidVersion: AndroidVersion? = null
    var androidRelease: String? = null
    var disambiguator: String? = null
    var deviceType: DeviceType? = null
    var isVirtual: Boolean? = null

    fun readCommonProperties(properties: Map<String, String>) {
      manufacturer = properties[RO_PRODUCT_MANUFACTURER] ?: properties[RO_MANUFACTURER]
      model = properties[RO_PRODUCT_MODEL] ?: properties[RO_MODEL]
      androidVersion =
        properties[RO_BUILD_VERSION_SDK]?.toIntOrNull()?.let { sdk ->
          AndroidVersion(sdk, properties[RO_BUILD_VERSION_CODENAME])
        }
      abi = properties[RO_PRODUCT_CPU_ABI]?.let { Abi.getEnum(it) }
      androidRelease = properties[RO_BUILD_VERSION_RELEASE]
      val characteristics = (properties[RO_BUILD_CHARACTERISTICS] ?: "").split(",")
      deviceType =
        when {
          characteristics.contains("watch") -> DeviceType.WEAR
          characteristics.contains("tv") -> DeviceType.TV
          characteristics.contains("automotive") -> DeviceType.AUTOMOTIVE
          else -> DeviceType.HANDHELD
        }
      isVirtual = properties["ro.kernel.qemu"] != null
    }

    fun buildBase(): DeviceProperties =
      Impl(
        manufacturer = manufacturer,
        model = model,
        androidVersion = androidVersion,
        abi = abi,
        androidRelease = androidRelease,
        disambiguator = disambiguator,
        deviceType = deviceType,
        isVirtual = isVirtual,
      )
  }

  class Impl(
    override val manufacturer: String?,
    override val model: String?,
    override val androidVersion: AndroidVersion?,
    override val abi: Abi?,
    override val androidRelease: String?,
    override val disambiguator: String?,
    override val deviceType: DeviceType?,
    override val isVirtual: Boolean?,
  ) : DeviceProperties
}

/**
 * The category of hardware of a device. Only variations that require different releases of Android
 * are represented, not minor differences like phone / tablet / foldable.
 */
enum class DeviceType {
  /** Handheld devices, e.g. phone, tablet, foldable. */
  HANDHELD,
  WEAR,
  TV,
  AUTOMOTIVE
}
