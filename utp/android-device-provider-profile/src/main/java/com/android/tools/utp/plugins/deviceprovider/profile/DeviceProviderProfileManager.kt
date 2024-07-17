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

package com.android.tools.utp.plugins.deviceprovider.profile

import com.android.tools.utp.plugins.deviceprovider.profile.proto.DeviceProviderProfileProto
import com.google.common.annotations.VisibleForTesting
import java.io.File
import java.io.FileOutputStream
import java.time.Clock

/**
 * Class that writes profiling information regarding UTP device providers provisioning and
 * releasing devices
 */
class DeviceProviderProfileManager(val targetFile: File) {
    val profileBuilder = DeviceProviderProfileProto.DeviceProviderProfile.newBuilder()

    /**
     * Records the length of time it takes to provision a device, and writes the result to the
     * profile file in case tests are not completed and the device is not properly released by UTP.
     *
     * @param block the runnable which provisions the device
     * @return the result of the execution of [block]
     */
    fun <T: Any> recordDeviceProvision(block: () -> T): T {
        lateinit var result: T
        profileBuilder.deviceProvision = DeviceProviderProfileProto.TimeSpan.newBuilder().apply {
            result = recordSpan(this, block)
        }.build()
        writeToFile()
        return result
    }

    /**
     * Records the length of time it takes to release a device and writes the result to the profile
     * file.
     *
     * @param block the runnable which release the device
     * @return the result of the execution of [block]
     */
    fun <T: Any> recordDeviceRelease(block: () -> T): T {
        lateinit var result: T
        profileBuilder.deviceRelease = DeviceProviderProfileProto.TimeSpan.newBuilder().apply {
            result = recordSpan(this, block)
        }.build()
        writeToFile()
        return result
    }

    /**
     * Records the beginning and end time of the block and writes it to the given span builder.
     *
     * @param spanBuilder the timespan builder to write the timestamps to.
     * @param block the block to be executed and measured.
     *
     * @return the result of the execution of [block]
     */
    private fun <T: Any> recordSpan(
        spanBuilder: DeviceProviderProfileProto.TimeSpan.Builder,
        block: () -> T
    ): T {
        spanBuilder.spanBeginMs = clock.instant().toEpochMilli()
        val result = block.invoke()
        spanBuilder.spanEndMs = clock.instant().toEpochMilli()
        return result
    }

    /**
     * Writes the current state of [profileBuilder] to the profile file.
     */
    private fun writeToFile() {
        FileOutputStream(targetFile).use { writer ->
            profileBuilder.build().writeTo(writer)
        }
    }

    companion object {
        @VisibleForTesting
        var clock: Clock = Clock.systemDefaultZone()

        private val DEFAULT_PROFILE_DIR: String = "profiling"

        fun targetFileForDevice(deviceName: String) = "${deviceName}_profile.pb"

        /**
         * Creates a DeviceProviderProfileManager for the default location in the given output
         * directory.
         */
        fun forOutputDirectory(
            outputDirectory: String, deviceName: String
        ): DeviceProviderProfileManager =
            File(outputDirectory).resolve(DEFAULT_PROFILE_DIR).let { file ->
                file.mkdirs()
                DeviceProviderProfileManager(file.resolve(targetFileForDevice(deviceName)))
            }
    }
}
