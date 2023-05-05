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

package com.android.build.gradle.internal.core.dsl.impl.features

import com.android.build.api.dsl.EmulatorControl
import com.android.build.api.dsl.EmulatorSnapshots
import com.android.build.api.dsl.ManagedDevices
import com.android.build.api.variant.AndroidVersion
import com.android.build.gradle.internal.core.dsl.features.AndroidTestOptionsDslInfo
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtensionImpl
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidTestOnDeviceConfigurationImpl
import com.android.builder.model.TestOptions

internal class KmpAndroidTestOptionsDslInfoImpl(
    private val extension: KotlinMultiplatformAndroidExtensionImpl,
): AndroidTestOptionsDslInfo {
    private val testOnDeviceConfig: KotlinMultiplatformAndroidTestOnDeviceConfigurationImpl?
        get() = extension.androidTestOnDeviceConfiguration
    override val animationsDisabled: Boolean
        get() = testOnDeviceConfig?.animationsDisabled ?: false
    override val execution: String
        get() = testOnDeviceConfig?.execution ?: TestOptions.Execution.HOST.name

    override val resultsDir: String?
        get() = null
    override val reportDir: String?
        get() = null
    override val managedDevices: ManagedDevices
        get() = testOnDeviceConfig?.managedDevices
            ?: throw IllegalAccessException("Test on device configuration does not exist")
    override val emulatorControl: EmulatorControl
        get() = testOnDeviceConfig?.emulatorControl
            ?: throw IllegalAccessException("Test on device configuration does not exist")
    override val emulatorSnapshots: EmulatorSnapshots
        get() = testOnDeviceConfig?.emulatorSnapshots
            ?: throw IllegalAccessException("Test on device configuration does not exist")
    override val targetSdkVersion: AndroidVersion?
        get() = null
}
