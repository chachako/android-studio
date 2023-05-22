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

package com.android.tools.firebase.testlab.gradle.device

import com.android.build.api.instrumentation.manageddevice.DeviceTestRunParameters
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunTaskAction
import com.android.tools.firebase.testlab.gradle.tasks.ExtraDeviceFilesManager
import com.google.api.services.testing.model.AndroidModel
import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import java.util.Locale

open class TestRunTaskAction: DeviceTestRunTaskAction<DeviceTestRunInput> {

    override fun runTests(params: DeviceTestRunParameters<DeviceTestRunInput>): Boolean {
         val gson = GsonBuilder()
            .setObjectToNumberStrategy {
                val number = ToNumberPolicy.LONG_OR_DOUBLE.readNumber(it)
                if (number is Long) {
                    number.toInt()
                } else {
                    number
                }
            }
            .create()

        val ftlDeviceModel = gson.fromJson(
            params.setupResult.file("${params.testRunData.deviceName}.json")
                .get().asFile.readText(),
            AndroidModel::class.java
        )

        val extraDeviceFileUrls = ExtraDeviceFilesManager(
            params.deviceInput.extraDeviceUrlsFile.get().asFile
        ).devicePathsToUrls()


        val results = params.deviceInput.buildService.get().runTestsOnDevice(
            params.testRunData.deviceName,
            params.deviceInput.device.get(),
            params.deviceInput.apiLevel.get(),
            Locale.forLanguageTag(params.deviceInput.locale.get()),
            params.deviceInput.orientation.get(),
            ftlDeviceModel,
            params.testRunData.testData,
            params.testRunData.outputDirectory.asFile,
            params.testRunData.projectPath,
            params.testRunData.variantName,
            extraDeviceFileUrls
        )
        return results.all { it.testPassed }
    }
}
