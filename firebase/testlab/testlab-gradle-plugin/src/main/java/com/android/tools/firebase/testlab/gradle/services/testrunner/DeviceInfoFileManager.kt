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

package com.android.tools.firebase.testlab.gradle.services.testrunner

import com.android.tools.utp.plugins.host.device.info.proto.AndroidTestDeviceInfoProto
import java.io.File
import java.io.FileOutputStream

/**
 * Handles the creation of device info files to be used by the test results proto.
 */
class DeviceInfoFileManager() {

    fun createFile(
        resultsOutDir: File,
        device: TestDeviceData
    ): File {
        val deviceInfoFile = File(resultsOutDir, "device-info.pb")
        val androidTestDeviceInfo = AndroidTestDeviceInfoProto.AndroidTestDeviceInfo.newBuilder()
            .setName(device.name)
            .setApiLevel(device.apiLevel.toString())
            .setGradleDslDeviceName(device.name)
            .setModel(device.deviceId)
            .build()
        FileOutputStream(deviceInfoFile).use {
            androidTestDeviceInfo.writeTo(it)
        }
        return deviceInfoFile
    }
}
