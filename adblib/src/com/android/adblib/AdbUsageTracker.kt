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
package com.android.adblib

interface AdbUsageTracker {

    /** Log data about the usage of an adblib feature */
    fun logUsage(event: Event)

    data class Event(
        // Info about the connected device
        val deviceInfo: DeviceInfo?,

        // Info about `JdwpProcessPropertiesCollector` success/failure
        val jdwpProcessPropertiesCollector: JdwpProcessPropertiesCollectorEvent?,
    )

    data class DeviceInfo(
        val serialNumber: String,
        val buildTags:String,
        val buildType:String,
        val buildVersionRelease: String,
        val buildApiLevelFull: String,
        val cpuAbi: String,
        val manufacturer: String,
        val model: String,
        val allCharacteristics: List<String>
    ) {

        companion object {

            suspend fun createFrom(device: ConnectedDevice): DeviceInfo {
                val properties = try {
                    if (device.isOnline) device.deviceProperties().allReadonly() else emptyMap()
                } catch (t: Throwable) {
                    emptyMap()
                }

                return DeviceInfo(
                    serialNumber = device.serialNumber,
                    buildTags = properties[DevicePropertyNames.RO_BUILD_TAGS] ?: "",
                    buildType = properties[DevicePropertyNames.RO_BUILD_TYPE] ?: "",
                    buildVersionRelease = properties[DevicePropertyNames.RO_BUILD_VERSION_RELEASE] ?: "",
                    buildApiLevelFull = properties[DevicePropertyNames.RO_BUILD_VERSION_SDK] ?: "",
                    cpuAbi = properties[DevicePropertyNames.RO_PRODUCT_CPU_ABI] ?: "",
                    manufacturer = properties[DevicePropertyNames.RO_PRODUCT_MANUFACTURER] ?: "",
                    model = properties[DevicePropertyNames.RO_PRODUCT_MODEL] ?: "",
                    allCharacteristics = (properties[DevicePropertyNames.RO_BUILD_CHARACTERISTICS]
                        ?: "").split(",")
                )
            }
        }
    }

    enum class JdwpProcessPropertiesCollectorFailureType {
        NO_RESPONSE,
        CLOSED_CHANNEL_EXCEPTION,
        CONNECTION_CLOSED_ERROR,
        IO_EXCEPTION,
        OTHER_ERROR,
    }

    data class JdwpProcessPropertiesCollectorEvent(
        val isSuccess: Boolean,
        val failureType: JdwpProcessPropertiesCollectorFailureType? = null,
        val previouslyFailedCount: Int,
        val previousFailureType: JdwpProcessPropertiesCollectorFailureType? = null
    )
}

internal class NoopAdbUsageTracker : AdbUsageTracker {

    override fun logUsage(event: AdbUsageTracker.Event) {
        // Do nothing
    }
}
