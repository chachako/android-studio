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

import com.android.sdklib.IAndroidTarget
import com.android.tools.configurations.ConfigurationModelModule
import com.android.tools.configurations.ThemeInfoProvider
import com.android.tools.layoutlib.LayoutlibContext
import com.android.tools.module.AndroidModuleInfo
import com.android.tools.module.ModuleDependencies
import com.android.tools.module.ModuleKey
import com.android.tools.res.ResourceRepositoryManager
import com.android.tools.sdk.AndroidPlatform
import com.android.tools.sdk.CompatibilityRenderTarget
import com.android.tools.sdk.EmbeddedRenderTarget
import com.intellij.openapi.project.Project

/** [ConfigurationModelModule] for standalone rendering.*/
internal class StandaloneConfigurationModelModule(
    override val resourceRepositoryManager: ResourceRepositoryManager,
    override val androidModuleInfo: AndroidModuleInfo,
    override val androidPlatform: AndroidPlatform,
    override val moduleKey: ModuleKey,
    override val dependencies: ModuleDependencies,
    override val project: Project,
    override val resourcePackage: String,
    override val layoutlibContext: LayoutlibContext,
    private val layoutlibPath: String,
) : ConfigurationModelModule {
    override val themeInfoProvider: ThemeInfoProvider = StandaloneThemeInfoProvider()
    override val name: String = "Fake Module"
    override fun getCompatibilityTarget(target: IAndroidTarget): CompatibilityRenderTarget {
        return EmbeddedRenderTarget.getCompatibilityTarget(target) { layoutlibPath }
    }
    override fun dispose() { }
}
