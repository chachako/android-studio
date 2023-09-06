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

@file:JvmName("AnalyticsSettingsUtils")

package com.android.build.gradle.internal.utils

import com.android.tools.analytics.AnalyticsSettings
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Class providing configuration-cache compatible way to check if the analytics.settings file
 * exists.
 */
abstract class AnalyticsSettingsFileExists : ValueSource<Boolean, AnalyticsSettingsFileExists.Params> {
    override fun obtain(): Boolean =
        parameters.analyticsSettingsFile.get().asFile.exists()

    interface Params: ValueSourceParameters {
        val analyticsSettingsFile: RegularFileProperty
    }
}

/**
 * Configuration-cache compatible way to check if the analytics.settings file exists.
 */
fun analyticsSettingsFileExists(providerFactory: ProviderFactory): Provider<Boolean> =
    providerFactory.of(AnalyticsSettingsFileExists::class.java) {
        it.parameters.analyticsSettingsFile.set(AnalyticsSettings.settingsFileLocation)
    }
