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

package com.android.tools.render

import com.android.tools.module.AndroidModuleInfo
import com.android.tools.module.ModuleDependencies
import com.android.tools.module.ModuleKey
import com.android.tools.rendering.ModuleRenderContext
import com.android.tools.rendering.RenderTask
import com.android.tools.rendering.api.EnvironmentContext
import com.android.tools.rendering.api.RenderModelManifest
import com.android.tools.rendering.api.RenderModelModule
import com.android.tools.res.AssetFileOpener
import com.android.tools.res.AssetRepositoryBase
import com.android.tools.res.ResourceRepositoryManager
import com.android.tools.res.ids.ResourceIdManager
import com.android.tools.sdk.AndroidPlatform
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import java.io.FileInputStream
import java.io.InputStream
import java.lang.ref.WeakReference

/** [RenderModelModule] for standalone rendering. */
internal class StandaloneRenderModelModule(
    override val resourceRepositoryManager: ResourceRepositoryManager,
    override val info: AndroidModuleInfo,
    override val androidPlatform: AndroidPlatform,
    override val moduleKey: ModuleKey,
    override val dependencies: ModuleDependencies,
    override val project: Project,
    override val resourcePackage: String,
    override val environment: EnvironmentContext,
    override val resourceIdManager: ResourceIdManager,
) : RenderModelModule {
    override val assetRepository = AssetRepositoryBase(object : AssetFileOpener {
        private fun getInputStream(path: String): InputStream {
            return FileInputStream(path)
        }

        override fun openAssetFile(path: String): InputStream = getInputStream(path)

        override fun openNonAssetFile(path: String): InputStream = getInputStream(path)
    })
    override val manifest: RenderModelManifest? = null
    override val isDisposed: Boolean = false
    override val name: String = "Fake Module"
    private val renderContext = StandaloneRenderContext()
    override fun createModuleRenderContext(weakRenderTask: WeakReference<RenderTask>): ModuleRenderContext {
        return renderContext
    }

    override fun dispose() { }

    override fun getIdeaModule(): Module {
        throw UnsupportedOperationException("Should not be called in standalone rendering")
    }
}
