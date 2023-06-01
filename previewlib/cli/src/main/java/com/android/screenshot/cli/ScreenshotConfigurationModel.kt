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

import com.android.sdklib.IAndroidTarget
import com.android.tools.configurations.ThemeInfoProvider
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.configurations.ConfigurationModelModule
import com.android.tools.idea.configurations.ConfigurationStateManager
import com.android.tools.idea.configurations.StudioConfigurationStateManager
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.module.AndroidModuleInfo
import com.android.tools.module.ModuleDependencies
import com.android.tools.idea.res.ScreenshotResourceRepositoryManager
import com.android.tools.res.ResourceRepositoryManager
import com.android.tools.layoutlib.LayoutlibContext
import com.android.tools.module.ModuleKey
import com.android.tools.module.ModuleKeyManager
import com.android.tools.sdk.AndroidPlatform
import com.android.tools.sdk.AndroidSdkData
import com.android.tools.sdk.CompatibilityRenderTarget
import com.intellij.openapi.project.Project
import org.jetbrains.android.sdk.StudioEmbeddedRenderTarget

class ScreenshotConfigurationModel(private val composeProject: ComposeProject,
    private val composeModule: ComposeModule,
    private val sdkPath: String,
    private val sysDependencies: Dependencies): ConfigurationModelModule {
    override val androidPlatform: AndroidPlatform?
        get() = AndroidPlatform(AndroidSdkData.getSdkData(sdkPath)!!, StudioEmbeddedRenderTarget.getCompatibilityTarget(composeProject.lintProject.buildTarget!!))
    override val resourceRepositoryManager: ResourceRepositoryManager?
        get() = ScreenshotResourceRepositoryManager(composeProject, composeModule)
    override val configurationStateManager: ConfigurationStateManager
        get() = StudioConfigurationStateManager.get(composeProject.lintProject.ideaProject!!)
    override val themeInfoProvider: ThemeInfoProvider
        get() = ScreenshotThemeInfoProvider(androidModuleInfo, composeModule)
    override val layoutlibContext: LayoutlibContext
        get() = ScreenshotEnvironmentContext(composeProject, sysDependencies).layoutlibContext
    override val androidModuleInfo: AndroidModuleInfo?
        get() = ScreenshotAndroidModuleInfo(composeProject)
    override val project: Project
        get() = composeProject.lintProject.ideaProject!!
    override val name: String
        get() = ""
    override val dependencies: ModuleDependencies
        get() = TODO("Not yet implemented")
    override val moduleKey: ModuleKey
        get() = ModuleKeyManager.getKey(composeModule.module)
    override val resourcePackage: String?
        get() = composeModule.module.getModuleSystem().getPackageName()
    override fun getCompatibilityTarget(target: IAndroidTarget): CompatibilityRenderTarget =
        StudioEmbeddedRenderTarget.getCompatibilityTarget(target)

    override fun dispose() {

    }
}
fun createConfigManager(composeProject: ComposeProject,composeModule: ComposeModule,sdkPath: String, deps: Dependencies): ConfigurationManager {
    return object : ConfigurationManager(composeModule.module, ScreenshotConfigurationModel(composeProject, composeModule, sdkPath, deps)) {}
}
