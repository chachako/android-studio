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

import com.android.annotations.TestOnly
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.SessionParams
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceFolderType
import com.android.sdklib.AndroidVersion
import com.android.tools.configurations.Configuration
import com.android.tools.module.ModuleKey
import com.android.tools.render.StandaloneModuleClassLoaderManager.Companion.CLASSES_TRACKER_KEY
import com.android.tools.render.configuration.StandaloneConfigurationModelModule
import com.android.tools.render.configuration.StandaloneConfigurationSettings
import com.android.tools.render.environment.StandaloneEnvironmentContext
import com.android.tools.render.framework.IJFramework
import com.android.tools.render.framework.StandaloneFramework
import com.android.tools.rendering.RenderLogger
import com.android.tools.rendering.RenderResult
import com.android.tools.rendering.RenderService
import com.android.tools.rendering.api.RenderModelModule
import com.android.tools.rendering.classloading.ClassesTracker
import com.android.tools.rendering.classloading.ModuleClassLoaderManager
import com.android.tools.rendering.parsers.RenderXmlFileSnapshot
import com.android.tools.res.LocalResourceRepository
import com.android.tools.res.SingleRepoResourceRepositoryManager
import com.android.tools.res.apk.ApkResourceRepository
import com.android.tools.res.ids.apk.ApkResourceIdManager
import com.android.tools.sdk.AndroidPlatform
import com.android.tools.sdk.AndroidSdkData
import com.intellij.openapi.util.Disposer
import java.io.Closeable
import java.io.File
import java.nio.file.Path
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** The main entry point to invoke rendering. */
class Renderer private constructor(
    fontsPath: String?,
    resourceApkPath: String?,
    namespace: String,
    classPath: List<String>,
    projectClassPath: List<String>,
    layoutlibPath: String,
    private val isForTest: Boolean,
) : Closeable {
    private val framework: IJFramework
    private val configuration: Configuration
    private val module: RenderModelModule

    init {
        TimeZone.getDefault()

        val moduleClassLoaderManager = StandaloneModuleClassLoaderManager(classPath, projectClassPath)

        framework = StandaloneFramework(stopExecutor = false)

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
        val androidModuleInfo = StandaloneModuleInfo(namespace, androidVersion)

        val androidSdkData = AndroidSdkData.getSdkDataWithoutValidityCheck(File(""))

        val androidPlatform = AndroidPlatform(androidSdkData, androidTarget)

        val resourceRepositoryManager = SingleRepoResourceRepositoryManager(resourcesRepo)

        framework.registerService(ModuleClassLoaderManager::class.java, moduleClassLoaderManager)

        val environment =
            StandaloneEnvironmentContext(
                framework.project,
                moduleClassLoaderManager,
                StandaloneFontCacheService(fontsPath)
            )
        val moduleDependencies = StandaloneModuleDependencies()
        val moduleKey = ModuleKey()

        val configModule = StandaloneConfigurationModelModule(
            resourceRepositoryManager,
            androidModuleInfo,
            androidPlatform,
            moduleKey,
            moduleDependencies,
            namespace,
            environment.layoutlibContext,
            layoutlibPath,
        )

        val configurationSettings =
            StandaloneConfigurationSettings(
                configModule,
                androidTarget
            )

        configuration = Configuration.create(configurationSettings, FolderConfiguration())

        module = StandaloneRenderModelModule(
            resourceRepositoryManager,
            androidModuleInfo,
            androidPlatform,
            moduleKey,
            moduleDependencies,
            framework.project,
            namespace,
            environment,
            resourceIdManager,
        )
    }

    /**
     * Renders xml layouts defined by [renderRequests] calling [onRenderResult] for every render
     * execution.
     *
     * * [onRenderResult] callback receives 4 arguments:
     * * [RenderRequest] the request that triggered the rendering.
     * * [Int] id of the result to distinguish them fot the cases where a single [RenderRequest]
     *   triggers several renderings, this could happen when e.g. a Composable is parameterized with
     *   a ParameterProvider.
     * * [RenderResult] an instance of the render result containing extended information about the
     *   rendering, including errors if any.
     * * [Set]<[String]> paths to all the project (from user code, not from library dependencies)
     *   classes that were used during the rendering.
     */
    fun render(
        renderRequests: Sequence<RenderRequest>,
        onRenderResult: (RenderRequest, Int, RenderResult, Set<String>) -> Unit,
    ) {
        renderRequests.forEach { request ->
            request.configurationModifier(configuration)
            request.xmlLayoutsProvider().forEachIndexed { i, layout ->
                val disposable = Disposer.newDisposable()
                val logger = RenderLogger()
                var usedPaths = emptySet<String>()
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
                    Disposer.register(disposable) {
                        renderTask.dispose()
                    }

                    val xmlFile =
                        RenderXmlFileSnapshot(
                            framework.project,
                            "layout.xml",
                            ResourceFolderType.LAYOUT,
                            layout
                        )

                    renderTask.setXmlFile(xmlFile)
                    val result = renderTask.render().get(100, TimeUnit.SECONDS)
                    val classesToPaths = (renderTask.classLoader as StandaloneModuleClassLoaderManager.DefaultModuleClassLoader).classesToPaths
                    usedPaths =
                        ClassesTracker
                            .getClasses(CLASSES_TRACKER_KEY)
                            .mapNotNull { classesToPaths[it.replace("/", ".")] }
                            .toSet()
                    result
                } catch (t: Throwable) {
                    RenderResult.createRenderTaskErrorResult(
                        module,
                        { throw UnsupportedOperationException() },
                        t,
                        logger
                    )
                } finally {
                    ClassesTracker.clear(CLASSES_TRACKER_KEY)
                    Disposer.dispose(disposable)
                }
                try {
                    onRenderResult(request, i, result, usedPaths)
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
        }
    }

    override fun close() {
        framework.close()
    }

    companion object {
        @JvmStatic
        fun createRenderer(
            fontsPath: String?,
            resourceApkPath: String?,
            namespace: String,
            classPath: List<String>,
            projectClassPath: List<String>,
            layoutlibPath: String,
        ) = Renderer(fontsPath, resourceApkPath, namespace, classPath, projectClassPath, layoutlibPath, false)

        @TestOnly
        @JvmStatic
        fun createTestRenderer(
            fontsPath: String?,
            resourceApkPath: String?,
            namespace: String,
            classPath: List<String>,
            layoutlibPath: String,
        ) = Renderer(fontsPath, resourceApkPath, namespace, classPath, emptyList(), layoutlibPath, true)
    }
}
