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
package com.android.screenshot.cli

import com.android.SdkConstants
import com.android.resources.ScreenSize
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.devices.Device
import com.android.tools.configurations.Configuration
import com.android.tools.configurations.DefaultThemeProvider
import com.android.tools.configurations.ThemeInfoProvider
import com.android.tools.dom.ActivityAttributesSnapshot
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.module.AndroidModuleInfo

class ScreenshotThemeInfoProvider(
    private val moduleInfo: AndroidModuleInfo?,
    private val composeModule: ComposeModule): ThemeInfoProvider {

    override val appThemeName: String?
        get() = MergedManifestManager.getMergedManifest(composeModule.module).get().manifestTheme
    override val allActivityThemeNames: Set<String>
        get() {
            val manifest = MergedManifestManager.getMergedManifest(composeModule.module).get()
            return manifest.activityAttributesMap.values.asSequence()
                .mapNotNull(ActivityAttributesSnapshot::getTheme)
                .toSet()
        }

    override fun getThemeNameForActivity(activityFqcn: String): String? {
        val manifest = MergedManifestManager.getMergedManifest(composeModule.module).get()
        return manifest.getActivityAttributes(activityFqcn)
            ?.theme
            ?.takeIf { it.startsWith(SdkConstants.PREFIX_RESOURCE_REF) }
    }

    override fun getDeviceDefaultTheme(
        renderingTarget: IAndroidTarget?,
        screenSize: ScreenSize?,
        device: Device?
    ): String = com.android.tools.idea.configurations.getDeviceDefaultTheme(
        moduleInfo,
        renderingTarget,
        screenSize,
        device
    )

    override fun getDefaultTheme(configuration: Configuration): String =
        DefaultThemeProvider.computeDefaultThemeForConfiguration(configuration)
}
