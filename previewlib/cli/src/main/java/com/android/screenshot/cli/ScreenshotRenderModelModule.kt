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

import com.android.ide.common.rendering.api.AssetRepository
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.rendering.RenderMergedManifest
import com.android.tools.idea.rendering.StudioModuleDependencies
import com.android.tools.idea.res.AssetRepositoryImpl
import com.android.tools.idea.res.ScreenshotResourceIdManager
import com.android.tools.idea.res.ScreenshotResourceRepositoryManager
import com.android.tools.lint.model.LintModelModule
import com.android.tools.module.AndroidModuleInfo
import com.android.tools.module.ModuleDependencies
import com.android.tools.rendering.ModuleKey
import com.android.tools.rendering.ModuleKeyManager
import com.android.tools.rendering.api.EnvironmentContext
import com.android.tools.rendering.api.RenderModelManifest
import com.android.tools.rendering.api.RenderModelModule
import com.android.tools.res.ResourceRepositoryManager
import com.android.tools.res.ids.ResourceIdManager
import com.android.tools.sdk.AndroidPlatform
import com.android.tools.sdk.AndroidSdkData
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import org.jetbrains.android.sdk.StudioEmbeddedRenderTarget
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ScreenshotRenderModelModule(
    val composeApp: ComposeApplication,
    val composeProject: ComposeProject,
    val composeModule: ComposeModule,
    val sdkPath: String,
    val rootLintModule: LintModelModule,
    val sysDependencies: Dependencies
) :
    RenderModelModule {

    private val resourceManager = ScreenshotResourceIdManager(composeProject, composeModule, rootLintModule)
    private val LOG = Logger.getInstance(ScreenshotRenderModelModule::class.java)
    private val environmentContext = ScreenshotEnvironmentContext(composeProject, sysDependencies)

    override val manifest: RenderModelManifest?
        get() {
            try {
                return RenderMergedManifest(
                    MergedManifestManager.getMergedManifest(composeModule.module)
                        .get(1, TimeUnit.SECONDS)
                )
            } catch (e: InterruptedException) {
                throw ProcessCanceledException(e)
            } catch (e: TimeoutException) {
                LOG.warn(e)
            } catch (e: ExecutionException) {
                when (val cause = e.cause) {
                    is ProcessCanceledException -> throw cause
                    else -> LOG.error(e)
                }
            }

            return null
        }
    override val resourceIdManager: ResourceIdManager
        get() = resourceManager
    override val moduleKey: ModuleKey
        get() = ModuleKeyManager.getKey(composeModule.module)
    override val resourcePackage: String?
        get() = composeModule.module.getModuleSystem().getPackageName()

    override val dependencies: ModuleDependencies
        get() = StudioModuleDependencies(composeModule.module)
    override val project: Project
        get() = composeProject.lintProject.ideaProject!!
    override val isDisposed: Boolean
        get() = false
    override val name: String
        get() = composeModule.module.name
    override val environment: EnvironmentContext
        get() = environmentContext

    override fun getIdeaModule(): Module {
        return composeModule.module
    }

    override val assetRepository: AssetRepository?
        get() = AssetRepositoryImpl(composeModule.facet)
    override val resourceRepositoryManager: ResourceRepositoryManager
        get() = ScreenshotResourceRepositoryManager(composeProject, composeModule)
    override val info: AndroidModuleInfo
        get() = ScreenshotAndroidModuleInfo(composeProject)
    override val androidPlatform: AndroidPlatform?
        get() = AndroidPlatform(
            AndroidSdkData.getSdkData(sdkPath)!!,
            StudioEmbeddedRenderTarget.getCompatibilityTarget(composeProject.lintProject.buildTarget!!)
        )

    override fun dispose() {}
}
