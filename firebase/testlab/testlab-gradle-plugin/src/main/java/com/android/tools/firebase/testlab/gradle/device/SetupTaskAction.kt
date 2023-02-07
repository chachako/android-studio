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

package com.android.tools.firebase.testlab.gradle.device

import com.android.build.api.instrumentation.manageddevice.DeviceSetupTaskAction
import com.google.gson.Gson
import org.gradle.api.file.Directory

open class SetupTaskAction : DeviceSetupTaskAction<DeviceSetupInput> {

    override fun setup(setupInput: DeviceSetupInput, outputDir: Directory) {
        val deviceId = setupInput.device.get()
        val apiLevel = setupInput.apiLevel.get()

        val ftlDeviceModel = setupInput.buildService.get().catalog().models.firstOrNull {
            it.id == deviceId
        }
        requireNotNull(ftlDeviceModel) {
            val availableDeviceNames = setupInput.buildService.get().catalog().models
                .filter {
                    it.supportedVersionIds?.contains(apiLevel.toString()) == true
                }
                .joinToString(", ") {
                    "${it.id} (${it.name})"
                }
            "Device: $deviceId is not a valid input. Available devices for API level $apiLevel " +
            "are: [$availableDeviceNames]"
        }

        if (!ftlDeviceModel.supportedVersionIds.contains(apiLevel.toString())) {
            error("""
                apiLevel: $apiLevel is not supported by device: $deviceId. Available Api levels are:
                ${ftlDeviceModel.supportedVersionIds}
            """.trimIndent())
        }

        outputDir.file("${setupInput.deviceName.get()}.json").asFile
                .writeText(Gson().toJson(ftlDeviceModel))
    }
}
