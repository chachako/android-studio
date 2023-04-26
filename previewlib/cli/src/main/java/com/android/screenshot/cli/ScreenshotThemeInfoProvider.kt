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

import com.android.resources.ScreenSize
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.devices.Device
import com.android.tools.idea.configurations.ThemeInfoProvider
import com.android.tools.idea.model.AndroidManifestIndex
import com.android.tools.idea.model.AndroidManifestRawText
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.FileBasedIndex
import java.util.Objects

class ScreenshotThemeInfoProvider(private val composeModule: ComposeModule, private val composeProject: ComposeProject) : ThemeInfoProvider {

    override val appThemeName: String?
        get() {
            // TODO: This should be returned from the manifest.
            return ""
        }
    override val allActivityThemeNames: Set<String>
        get() = TODO("Not yet implemented")

    override fun getThemeNameForActivity(activityFqcn: String): String? {
        TODO("Not yet implemented")
    }

    override fun getDefaultTheme(
        renderingTarget: IAndroidTarget?,
        screenSize: ScreenSize?,
        device: Device?
    ): String {
        TODO("Not yet implemented")
    }
}
