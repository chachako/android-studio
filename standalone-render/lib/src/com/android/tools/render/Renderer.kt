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

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.SessionParams
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceFolderType
import com.android.sdklib.AndroidVersion
import com.android.tools.configurations.Configuration
import com.android.tools.fonts.DownloadableFontCacheService
import com.android.tools.module.ModuleKey
import com.android.tools.render.configuration.StandaloneConfigurationModelModule
import com.android.tools.render.configuration.StandaloneConfigurationSettings
import com.android.tools.render.environment.StandaloneEnvironmentContext
import com.android.tools.render.framework.StandaloneFramework
import com.android.tools.rendering.RenderLogger
import com.android.tools.rendering.RenderResult
import com.android.tools.rendering.RenderService
import com.android.tools.rendering.classloading.ModuleClassLoaderManager
import com.android.tools.rendering.parsers.RenderXmlFileSnapshot
import com.android.tools.res.FrameworkResourceRepositoryManager
import com.android.tools.res.LocalResourceRepository
import com.android.tools.res.apk.ApkResourceRepository
import com.android.tools.res.ids.apk.ApkResourceIdManager
import com.android.tools.sdk.AndroidPlatform
import com.android.tools.sdk.AndroidSdkData
import com.google.common.annotations.VisibleForTesting
import java.nio.file.Path
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** The main entry point to invoke rendering. */
fun render(
    sdkPath: String,
    resourceApkPath: String?,
    packageName: String,
    classPath: List<String>,
    layoutlibPath: String,
    renderRequests: Sequence<RenderRequest>,
    onRenderResult: (RenderRequest, Int, RenderResult) -> Unit,
) = renderImpl(
    sdkPath,
    resourceApkPath,
    packageName,
    classPath,
    layoutlibPath,
    renderRequests,
    onRenderResult,
    false
)

@VisibleForTesting
fun renderForTest(
    sdkPath: String,
    resourceApkPath: String?,
    packageName: String,
    classPath: List<String>,
    layoutlibPath: String,
    renderRequests: Sequence<RenderRequest>,
    onRenderResult: (RenderRequest, Int, RenderResult) -> Unit,
) = renderImpl(
    sdkPath,
    resourceApkPath,
    packageName,
    classPath,
    layoutlibPath,
    renderRequests,
    onRenderResult,
    true
)

internal fun renderImpl(
    sdkPath: String,
    resourceApkPath: String?,
    packageName: String,
    classPath: List<String>,
    layoutlibPath: String,
    renderRequests: Sequence<RenderRequest>,
    onRenderResult: (RenderRequest, Int, RenderResult) -> Unit,
    isForTest: Boolean,
) {
    // Warmup TimeZone.getDefault so that it works inside rendering not triggering security
    TimeZone.getDefault()

    StandaloneFramework(!isForTest).use { framework ->
        framework.registerService(FrameworkResourceRepositoryManager::class.java, FrameworkResourceRepositoryManager())
        framework.registerService(DownloadableFontCacheService::class.java, StandaloneFontCacheService(sdkPath))

        val resourceIdManager = ApkResourceIdManager()
        resourceApkPath?.let {
            resourceIdManager.loadApkResources(it)
        }

        val resourcesRepo =
            if (resourceApkPath != null)
                ApkResourceRepository(resourceApkPath, resourceIdManager::findById)
            else
                LocalResourceRepository.EmptyRepository<Path>(ResourceNamespace.RES_AUTO)

        val androidVersion = AndroidVersion(33)
        val androidTarget = StandaloneAndroidTarget(androidVersion)
        val androidModuleInfo = StandaloneModuleInfo(packageName, androidVersion)

        val androidSdkData = AndroidSdkData.getSdkData(sdkPath) ?: throw InvalidSdkException(sdkPath)

        val androidPlatform = AndroidPlatform(androidSdkData, androidTarget)

        val resourceRepositoryManager = StandaloneResourceRepositoryManager(resourcesRepo)

        val moduleClassLoaderManager = StandaloneModuleClassLoaderManager(classPath)

        framework.registerService(ModuleClassLoaderManager::class.java, moduleClassLoaderManager)

        val environment =
            StandaloneEnvironmentContext(
                framework.disposable,
                framework.project,
                moduleClassLoaderManager
            )
        val moduleDependencies = StandaloneModuleDependencies()
        val moduleKey = ModuleKey()

        val configModule = StandaloneConfigurationModelModule(
            resourceRepositoryManager,
            androidModuleInfo,
            androidPlatform,
            moduleKey,
            moduleDependencies,
            framework.project,
            packageName,
            environment.layoutlibContext,
            layoutlibPath,
        )

        val configurationSettings =
            StandaloneConfigurationSettings(
                configModule,
                framework.project,
                androidTarget
            )

        val configuration = Configuration.create(configurationSettings, FolderConfiguration())

        val module = StandaloneRenderModelModule(
            resourceRepositoryManager,
            androidModuleInfo,
            androidPlatform,
            moduleKey,
            moduleDependencies,
            framework.project,
            packageName,
            environment,
            resourceIdManager,
        )

        renderRequests.forEach { request ->
            request.configurationModifier(configuration)
            request.xmlLayoutsProvider().forEachIndexed { i, layout ->
                val logger = RenderLogger()
                val result = try {
                    val renderTask = RenderService { }.taskBuilder(module, configuration, logger)
                        .disableDecorations()
                        .also {
                            if (isForTest) {
                                it.disableSecurityManager()
                            }
                        }
                        .withRenderingMode(SessionParams.RenderingMode.SHRINK)
                        .build().get()

                    val xmlFile =
                        RenderXmlFileSnapshot(
                            framework.project,
                            "layout.xml",
                            ResourceFolderType.LAYOUT,
                            layout
                        )

                    renderTask.setXmlFile(xmlFile)
                    renderTask.render().get(100, TimeUnit.SECONDS)
                } catch (t: Throwable) {
                    RenderResult.createRenderTaskErrorResult(
                        module,
                        { throw UnsupportedOperationException() },
                        t,
                        logger
                    )
                }
                try {
                    onRenderResult(request, i, result)
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
        }

        try {
            RenderService.shutdownRenderExecutor()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
}
