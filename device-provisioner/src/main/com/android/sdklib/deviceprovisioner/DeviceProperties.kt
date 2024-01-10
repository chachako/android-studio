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

import com.android.adblib.AdbFailResponseException
import com.android.adblib.ConnectedDevice
import com.android.adblib.DevicePropertyNames.RO_BUILD_CHARACTERISTICS
import com.android.adblib.DevicePropertyNames.RO_BUILD_TAGS
import com.android.adblib.DevicePropertyNames.RO_BUILD_TYPE
import com.android.adblib.DevicePropertyNames.RO_BUILD_VERSION_RELEASE
import com.android.adblib.DevicePropertyNames.RO_KERNEL_QEMU
import com.android.adblib.DevicePropertyNames.RO_MANUFACTURER
import com.android.adblib.DevicePropertyNames.RO_MODEL
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_CPU_ABI
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_CPU_ABI2
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_CPU_ABILIST
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_MANUFACTURER
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_MODEL
import com.android.adblib.DevicePropertyNames.RO_SF_LCD_DENSITY
import com.android.adblib.ShellCommandOutputElement
import com.android.adblib.adbLogger
import com.android.adblib.selector
import com.android.adblib.shellAsLines
import com.android.resources.Density
import com.android.sdklib.AndroidVersion
import com.android.sdklib.AndroidVersionUtil
import com.android.sdklib.devices.Abi
import com.android.tools.analytics.Anonymizer
import com.android.tools.analytics.CommonMetricsData
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.DeviceInfo
import java.nio.channels.InterruptedByTimeoutException
import java.time.Duration
import java.util.concurrent.TimeoutException
import javax.swing.Icon
import kotlin.math.ceil
import kotlinx.coroutines.flow.first

/**
 * Stores various properties about a device that are generally stable.
 *
 * This is designed to be extended by subclasses through composition.
 */
interface DeviceProperties {

  val model: String?
  val manufacturer: String?
  val primaryAbi: Abi?
    get() = abiList.firstOrNull()

    /**
     * The ABI that should be used to build and deploy, instead of supported ABIs in [abiList].
     * Can be null if no preferred ABI is desired.
     */
  val preferredAbi: String?
  val abiList: List<Abi>

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
   * If true, the device is connected over the network via a proxy that mediates access; if false,
   * the device is connected directly to the local machine.
   */
  val isRemote: Boolean?

  /** If true, the device allows debugging. */
  val isDebuggable: Boolean?

  /** Icon used to represent this device in UIs */
  val icon: Icon

  /** The actual screen resolution of the device in pixels (not adjusted to "dp"). */
  val resolution: Resolution?

  /** The screen resolution in device-independent pixels ("dp"). */
  val resolutionDp: Resolution?
    get() {
      val resolution = resolution ?: return null
      val density = density ?: return null
      return Resolution(
        width = ceil(Density.DEFAULT_DENSITY.toDouble() * resolution.width / density).toInt(),
        height = ceil(Density.DEFAULT_DENSITY.toDouble() * resolution.height / density).toInt(),
      )
    }

  /**
   * The pixel density (in dpi) of the device, adjusted to fit one of the standard buckets in
   * [com.android.resources.Density].
   */
  val density: Int?

  /**
   * A string ideally unique to the device instance (e.g. serial number or emulator console port),
   * used for disambiguating this device from others with similar properties.
   */
  val disambiguator: String?

  /**
   * The ID used by the WearPairingManager for this device (in the PairingDevice.deviceId field).
   * This must be kept in sync with WearPairingManager's IDevice.getDeviceID extension function.
   * This is a stopgap until WearPairingManager is ported to adblib / DeviceProvisioner, and should
   * not be used except for interfacing with WearPairingManager.
   */
  val wearPairingId: String?

  /** The type of connection to the device, if known. */
  val connectionType: ConnectionType?

  /** Default implementation of device title; may be overridden. */
  val title: String
    get() {
      return when {
        manufacturer.isNullOrBlank() -> model ?: "Unknown"
        model.isNullOrBlank() -> "$manufacturer Device"
        else -> "$manufacturer $model"
      }
    }

  /** A DeviceInfo proto for use in AndroidStudioEvent to describe the device in metrics. */
  val deviceInfoProto: DeviceInfo

  companion object {
    /** Builds a basic DeviceProperties instance with no additional fields. */
    inline fun build(block: Builder.() -> Unit): DeviceProperties =
      Builder().apply(block).buildBase()

    /** Builds a basic DeviceProperties instance for testing; some validation is skipped. */
    @VisibleForTesting
    inline fun buildForTest(block: Builder.() -> Unit): DeviceProperties =
      Builder().apply(block).buildBaseForTest()
  }

  open class Builder {
    var manufacturer: String? = null
    var model: String? = null
    var abiList: List<Abi> = emptyList()
    var preferredAbi: String? = null
    var androidVersion: AndroidVersion? = null
    var androidRelease: String? = null
    var disambiguator: String? = null
    var deviceType: DeviceType? = null
    var isVirtual: Boolean? = null
    var isRemote: Boolean? = null
    var isDebuggable: Boolean? = null
    var wearPairingId: String? = null
    var resolution: Resolution? = null
    var density: Int? = null
    var icon: Icon? = null
    var connectionType: ConnectionType? = null
    var deviceInfoProto: DeviceInfo.Builder = DeviceInfo.newBuilder()

    /** Uses the ADB serial number to determine if the device is WiFi-connected. */
    fun readAdbSerialNumber(adbSerialNumber: String) {
      SerialNumberAndMdnsConnectionType.fromAdbSerialNumber(adbSerialNumber).let {
        wearPairingId = it.serialNumber
        deviceInfoProto.mdnsConnectionType = it.mdnsConnectionType
        when (it.mdnsConnectionType) {
          DeviceInfo.MdnsConnectionType.MDNS_AUTO_CONNECT_UNENCRYPTED,
          DeviceInfo.MdnsConnectionType.MDNS_AUTO_CONNECT_TLS ->
            connectionType = ConnectionType.WIFI
          else -> {}
        }
      }
    }

    fun readCommonProperties(properties: Map<String, String>) {
      manufacturer = properties[RO_PRODUCT_MANUFACTURER] ?: properties[RO_MANUFACTURER]
      model = properties[RO_PRODUCT_MODEL] ?: properties[RO_MODEL]
      androidVersion = AndroidVersionUtil.androidVersionFromDeviceProperties(properties)

      // Try abilist first (implemented in L onwards); otherwise, fall back to abi and abi2.
      val abiStrings =
        properties[RO_PRODUCT_CPU_ABILIST]?.split(",")
          ?: listOfNotNull(properties[RO_PRODUCT_CPU_ABI], properties[RO_PRODUCT_CPU_ABI2])
      abiList = abiStrings.mapNotNull { Abi.getEnum(it) }

      androidRelease = properties[RO_BUILD_VERSION_RELEASE]
      val characteristics = (properties[RO_BUILD_CHARACTERISTICS] ?: "").split(",")
      deviceType =
        when {
          characteristics.contains("watch") -> DeviceType.WEAR
          characteristics.contains("tv") -> DeviceType.TV
          characteristics.contains("automotive") -> DeviceType.AUTOMOTIVE
          else -> DeviceType.HANDHELD
        }
      isVirtual = properties[RO_KERNEL_QEMU] == "1"
      isDebuggable = properties[RO_BUILD_TYPE] in setOf("userdebug", "eng")
      density = properties[RO_SF_LCD_DENSITY]?.toIntOrNull()
    }

    /**
     * Fills in the DeviceInfo proto based on previously-assigned properties. If the device is
     * online, the serial number and system properties may be used to fill in additional fields that
     * are only used in logging.
     */
    fun populateDeviceInfoProto(
      pluginId: String,
      serialNumber: String?,
      properties: Map<String, String>,
      connectionId: String,
    ) {
      deviceInfoProto.anonymizedSerialNumber = Anonymizer.anonymize(serialNumber) ?: ""
      deviceInfoProto.buildTags = properties[RO_BUILD_TAGS] ?: ""
      deviceInfoProto.buildType = properties[RO_BUILD_TYPE] ?: ""
      deviceInfoProto.buildVersionRelease = androidRelease ?: ""
      deviceInfoProto.cpuAbi =
        CommonMetricsData.applicationBinaryInterfaceFromString(abiList.firstOrNull()?.toString())
      deviceInfoProto.manufacturer = manufacturer ?: ""
      deviceInfoProto.model = model ?: ""
      deviceInfoProto.deviceType =
        when {
          isRemote == true && isVirtual == true -> DeviceInfo.DeviceType.CLOUD_EMULATOR
          isRemote == true -> DeviceInfo.DeviceType.CLOUD_PHYSICAL
          isVirtual == true -> DeviceInfo.DeviceType.LOCAL_EMULATOR
          isVirtual == false -> DeviceInfo.DeviceType.LOCAL_PHYSICAL
          else -> DeviceInfo.DeviceType.UNKNOWN_DEVICE_TYPE
        }
      deviceInfoProto.buildApiLevelFull = androidVersion?.apiStringWithExtension ?: ""
      properties[RO_BUILD_CHARACTERISTICS]?.let {
        deviceInfoProto.addAllCharacteristics(it.split(","))
      }
      deviceInfoProto.deviceProvisionerId = pluginId
      deviceInfoProto.connectionId = connectionId
    }

    /** Generates a random hex string with [length] characters */
    fun randomConnectionId(length: Int = 8): String = buildString {
      // 0 - 9 and a - f
      val intRange = 0 until 16
      repeat(length) { append(intRange.random().toString(Character.MAX_RADIX)) }
    }

    fun buildBase(): DeviceProperties {
      check(deviceInfoProto.deviceProvisionerId.isNotEmpty()) {
        "populateDeviceInfoProto was not invoked"
      }
      return buildBaseWithoutChecks()
    }

    @VisibleForTesting fun buildBaseForTest() = buildBaseWithoutChecks()

    private fun buildBaseWithoutChecks(): DeviceProperties =
      Impl(
        manufacturer = manufacturer,
        model = model,
        androidVersion = androidVersion,
        abiList = abiList,
        preferredAbi = preferredAbi,
        androidRelease = androidRelease,
        disambiguator = disambiguator,
        deviceType = deviceType,
        isVirtual = isVirtual,
        isRemote = isRemote,
        isDebuggable = isDebuggable,
        wearPairingId = wearPairingId,
        resolution = resolution,
        density = density,
        icon = checkNotNull(icon),
        connectionType = connectionType,
        deviceInfoProto = deviceInfoProto.build(),
      )
  }

  class Impl(
    override val manufacturer: String?,
    override val model: String?,
    override val androidVersion: AndroidVersion?,
    override val abiList: List<Abi>,
    override val preferredAbi: String?,
    override val androidRelease: String?,
    override val disambiguator: String?,
    override val deviceType: DeviceType?,
    override val isVirtual: Boolean?,
    override val isRemote: Boolean?,
    override val isDebuggable: Boolean?,
    override val wearPairingId: String?,
    override val resolution: Resolution?,
    override val density: Int?,
    override val icon: Icon,
    override val connectionType: ConnectionType?,
    override val deviceInfoProto: DeviceInfo,
  ) : DeviceProperties
}

/**
 * The category of hardware of a device. Only variations that require different releases of Android
 * are represented, not minor differences like phone / tablet / foldable.
 */
enum class DeviceType(val stringValue: String) {
  /** Handheld devices, e.g. phone, tablet, foldable. */
  HANDHELD("Phone and Tablet"),
  WEAR("Wear"),
  TV("TV"),
  AUTOMOTIVE("Automotive");

  override fun toString() = stringValue
}

enum class ConnectionType {
  USB,
  WIFI,
  NETWORK
}

data class DeviceIcons(val handheld: Icon, val wear: Icon, val tv: Icon, val automotive: Icon) {
  fun iconForDeviceType(type: DeviceType?) =
    when (type) {
      DeviceType.TV -> tv
      DeviceType.AUTOMOTIVE -> automotive
      DeviceType.WEAR -> wear
      else -> handheld
    }
}

data class Resolution(val width: Int, val height: Int) {
  override fun toString() = "$width × $height"

  companion object {
    private val REGEX = Regex("Physical size: (\\d+)x(\\d+)")

    private fun parseWmSizeOutput(output: String): Resolution? =
      REGEX.matchEntire(output)?.let { result ->
        Resolution(result.groupValues[1].toInt(), result.groupValues[2].toInt())
      }

    suspend fun readFromDevice(device: ConnectedDevice): Resolution? =
      runCatching {
          val shellOutput =
            device.session.deviceServices
              .shellAsLines(device.selector, "wm size", commandTimeout = Duration.ofSeconds(5))
              .first()
          when (shellOutput) {
            is ShellCommandOutputElement.StdoutLine -> parseWmSizeOutput(shellOutput.contents)
            else -> {
              adbLogger(device.session)
                .warn("Failed to read device resolution successfully: $shellOutput")
              null
            }
          }
        }
        .onFailure { e ->
          when (e) {
            is AdbFailResponseException ->
              adbLogger(device.session).warn(e, "Failed to read device resolution")
            is TimeoutException,
            is InterruptedByTimeoutException ->
              adbLogger(device.session).warn(e, "Timeout reading device resolution")
            else -> adbLogger(device.session).error(e, "Reading device resolution")
          }
        }
        .getOrNull()
  }
}

internal class SerialNumberAndMdnsConnectionType(
  val serialNumber: String,
  val mdnsConnectionType: DeviceInfo.MdnsConnectionType,
) {
  companion object {
    private const val ADB_MDNS_SERVICE_NAME = "adb"
    private const val ADB_MDNS_TLS_SERVICE_NAME = "adb-tls-connect"

    private val MDNS_AUTO_CONNECT_REGEX =
      Regex("adb-(.*)-.*\\._(${ADB_MDNS_SERVICE_NAME}|$ADB_MDNS_TLS_SERVICE_NAME)\\._tcp\\.?")

    /**
     * Parses an ADB serial number, extracting the device serial number and mDNS connection type
     * from it. Non-mDNS connections return the serial number unchanged with MDNS_NONE for
     * mdnsConnectionType.
     */
    fun fromAdbSerialNumber(adbSerialNumber: String): SerialNumberAndMdnsConnectionType =
      MDNS_AUTO_CONNECT_REGEX.matchEntire(adbSerialNumber)?.let {
        SerialNumberAndMdnsConnectionType(
          it.groupValues[1],
          when (it.groupValues[2]) {
            ADB_MDNS_TLS_SERVICE_NAME -> DeviceInfo.MdnsConnectionType.MDNS_AUTO_CONNECT_TLS
            ADB_MDNS_SERVICE_NAME -> DeviceInfo.MdnsConnectionType.MDNS_AUTO_CONNECT_UNENCRYPTED
            else -> DeviceInfo.MdnsConnectionType.MDNS_NONE
          },
        )
      }
        ?: SerialNumberAndMdnsConnectionType(
          adbSerialNumber,
          DeviceInfo.MdnsConnectionType.MDNS_NONE,
        )
  }
}
