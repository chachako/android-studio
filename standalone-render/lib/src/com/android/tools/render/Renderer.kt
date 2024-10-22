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
import com.android.tools.module.ModuleKey
import com.android.tools.render.configuration.StandaloneConfigurationModelModule
import com.android.tools.render.configuration.StandaloneConfigurationSettings
import com.android.tools.render.environment.StandaloneEnvironmentContext
import com.android.tools.render.framework.IJFramework
import com.android.tools.rendering.RenderLogger
import com.android.tools.rendering.RenderResult
import com.android.tools.rendering.RenderService
import com.android.tools.rendering.api.RenderModelModule
import com.android.tools.rendering.classloading.ModuleClassLoaderManager
import com.android.tools.rendering.parsers.RenderXmlFileSnapshot
import com.android.tools.res.LocalResourceRepository
import com.android.tools.res.SingleRepoResourceRepositoryManager
import com.android.tools.res.apk.ApkResourceRepository
import com.android.tools.res.ids.apk.ApkResourceIdManager
import com.android.tools.sdk.AndroidPlatform
import com.android.tools.sdk.AndroidSdkData
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.ReadAndWriteScope
import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.application.ReadResult
import com.intellij.openapi.application.ReadWriteActionSupport
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import java.io.Closeable
import java.io.File
import java.nio.file.Path
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * A renderer that can handle [RenderRequest].
 */
class Renderer(
    fontsPath: String?,
    resourceApkPath: String?,
    namespace: String,
    classPath: List<String>,
    projectClassPath: List<String>,
    layoutlibPath: String,
    private val renderTaskConfigurator: RenderService.RenderTaskBuilder.() -> Unit = {},
) : Closeable {

    private val project: Project = IJFramework.createProject()
    private val baseConfiguration: Configuration
    val module: StandaloneRenderModelModule
    private val renderService: RenderService

    init {
        TimeZone.getDefault()

        val moduleClassLoaderManager = StandaloneModuleClassLoaderManager(classPath, projectClassPath)

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

        IJFramework.registerService(
            ModuleClassLoaderManager::class.java, moduleClassLoaderManager, project)

        IJFramework.registerService(
            ReadWriteActionSupport::class.java, object: ReadWriteActionSupport {
                override fun committedDocumentsConstraint(project: Project): ReadConstraint =
                    ReadConstraint.withDocumentsCommitted(project)

                override fun <X, E : Throwable> computeCancellable(action: ThrowableComputable<X, E>): X = ReadAction.compute(action)

                override suspend fun <X> executeReadAction(
                    constraints: List<ReadConstraint>,
                    undispatched: Boolean,
                    blocking: Boolean,
                    action: () -> X
                ): X {
                    throw UnsupportedOperationException()
                }

                override suspend fun <X> executeReadAndWriteAction(
                    constraints: Array<out ReadConstraint>,
                    action: ReadAndWriteScope.() -> ReadResult<X>
                ): X {
                    throw UnsupportedOperationException()
                }

                override fun smartModeConstraint(project: Project): ReadConstraint =
                    ReadConstraint.inSmartMode(project)

            }, project
        )

        val environment =
            StandaloneEnvironmentContext(
                project,
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

        baseConfiguration = Configuration.create(configurationSettings, FolderConfiguration())

        module = StandaloneRenderModelModule(
            resourceRepositoryManager,
            androidModuleInfo,
            androidPlatform,
            moduleKey,
            moduleDependencies,
            project,
            namespace,
            environment,
            resourceIdManager,
        )

        renderService = RenderService {
            it.apply {
                disableDecorations()
                withRenderingMode(SessionParams.RenderingMode.SHRINK)
                renderTaskConfigurator()
            }
        }
        Disposer.register(project, renderService)
    }

    /**
     * Renders xml layouts provided by [request].
     */
    fun render(request: RenderRequest): Sequence<Pair<Configuration, RenderResult>> {
        return request.xmlLayoutsProvider().map {
            val configuration = baseConfiguration.clone()
            request.configurationModifier(configuration)
            configuration to render(configuration, it)
        }
    }

    private fun render(configuration: Configuration, xmlLayout: String): RenderResult {
        val disposable = Disposer.newDisposable()
        val logger = RenderLogger()
        return try {
            val renderTask =
                renderService.taskBuilder(module, configuration, logger).build().get()
                    ?: return RenderResult.createRenderTaskErrorResult(
                        module,
                        { throw NotImplementedError("PsiFile supplier is not supported") },
                        null,
                        logger
                    )

            Disposer.register(disposable) {
                renderTask.dispose()
            }

            val xmlFile =
                RenderXmlFileSnapshot(
                    project,
                    "layout.xml",
                    ResourceFolderType.LAYOUT,
                    xmlLayout
                )

            renderTask.setXmlFile(xmlFile)

            renderTask.render().get(100, TimeUnit.SECONDS)
        } catch (t: Throwable) {
            RenderResult.createRenderTaskErrorResult(
                module,
                { throw NotImplementedError("PsiFile supplier is not supported") },
                t,
                logger
            )
        } finally {
            Disposer.dispose(disposable)
        }
    }

    override fun close() {
        Disposer.dispose(project)
    }
}
