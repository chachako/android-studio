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

package com.android.tools.render.configuration

import com.android.resources.ScreenSize
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.devices.Device
import com.android.tools.configurations.Configuration
import com.android.tools.configurations.ThemeInfoProvider

private const val DEFAULT_APP_THEME = "@android:style/Theme.Material.Light"

/**
 * A simplified [ThemeInfoProvider] reduced to a single hard-coded theme. This is sufficient for the
 * compose preview, but might need improvement to fully support other cases (e.g. xml layout
 * rendering).
 */
internal class StandaloneThemeInfoProvider : ThemeInfoProvider {
    override val appThemeName: String = DEFAULT_APP_THEME
    override val allActivityThemeNames: Set<String> = emptySet()

    override fun getThemeNameForActivity(activityFqcn: String): String? = null

    override fun getDeviceDefaultTheme(
        renderingTarget: IAndroidTarget?,
        screenSize: ScreenSize?,
        device: Device?
    ): String = appThemeName

    override fun getDefaultTheme(configuration: Configuration): String = appThemeName
}
