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

import com.android.tools.concurrency.AndroidIoManager
import com.android.tools.fonts.DownloadableFontCacheService
import com.android.tools.idea.concurrency.StudioIoManager
import com.android.tools.idea.diagnostics.crash.ExceptionDataCollection
import com.android.tools.idea.diagnostics.crash.ExceptionDataConfiguration
import com.android.tools.idea.diagnostics.crash.ExceptionDataConfigurationImpl
import com.android.tools.idea.diagnostics.crash.StudioCrashReporter
import com.android.tools.idea.flags.StudioFlagSettings
import com.android.tools.idea.fonts.StudioDownloadableFontCacheService
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.android.tools.idea.res.AarResourceRepositoryCache
import com.android.tools.idea.res.ResourceFolderRepositoryFileCache
import com.android.tools.idea.res.ResourceFolderRepositoryFileCacheImpl
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.rendering.classloading.ModuleClassLoaderManager
import com.android.tools.res.FrameworkResourceRepositoryManager
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.core.CoreFileTypeRegistry
import com.intellij.facet.FacetTypeRegistry
import com.intellij.facet.impl.FacetTypeRegistryImpl
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.ide.plugins.PluginUtil
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.lang.LanguageASTFactory
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.xml.XMLLanguage
import com.intellij.lang.xml.XMLParserDefinition
import com.intellij.lang.xml.XmlASTFactory
import com.intellij.openapi.application.AsyncExecutionService
import com.intellij.openapi.application.impl.AsyncExecutionServiceImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.ProjectJdkTableImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils
import com.intellij.util.ReflectionUtil
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.kotlin.utils.PathUtil
import org.mockito.Mockito
import java.nio.file.Paths

/**
 * This class helps register all application level services needed for the screenshot standalone tool.
 * The services are registered here as the [CoreApplicationEnvironment] does not enumerate or expose
 * a way to load the services from the plugin.xml
 */
class ComposeApplication(private val applicationManager: CoreApplicationEnvironment, private val dependencies: Dependencies) {

    init {
        setupCoreApplication()
    }

    fun setupCoreApplication() {
        // Ordered by load requirements
        applicationManager.registerApplicationService(
            EmbeddedDistributionPaths::class.java,
            EmbeddedDistributionPaths()
        )
        applicationManager.registerApplicationService(
            StudioFlagSettings::class.java,
            StudioFlagSettings()
        )
        applicationManager.registerApplicationService(
            AndroidIoManager::class.java,
            StudioIoManager()
        )
        applicationManager.registerApplicationService(
            ModuleClassLoaderManager::class.java,
            ScreenshotModuleClassLoaderManager(dependencies)
        )
        applicationManager.registerApplicationService(
            FacetTypeRegistry::class.java,
            FacetTypeRegistryImpl()
        )
        // Needed by AndroidManifestIndexQueryUtils for Min/MaxSdk
        applicationManager.registerApplicationService(PluginUtil::class.java, object : PluginUtil {
            override fun getCallerPlugin(stackFrameCount: Int): PluginId? {
                val aClass = ReflectionUtil.getCallerClass(stackFrameCount + 1) ?: return null
                val classLoader = aClass.classLoader
                return if (classLoader is PluginAwareClassLoader) (classLoader as PluginAwareClassLoader).pluginId else null
            }

            override fun findPluginId(t: Throwable): PluginId {
                return PluginId.getId(t.javaClass.name)
            }

        })

        // Needed by LibraryResourceClassLoader
        applicationManager.registerApplicationService(
            AarResourceRepositoryCache::class.java,
            ScreenshotAarResourceRepository()
        )
        applicationManager.registerApplicationService(
            AsyncExecutionService::class.java,
            AsyncExecutionServiceImpl()
        )
        applicationManager.registerApplicationService(
            ResourceFolderRepositoryFileCache::class.java,
            ResourceFolderRepositoryFileCacheImpl()
        )

        val fileTimeRegistry = FileTypeRegistry.getInstance() as CoreFileTypeRegistry
        fileTimeRegistry.registerFileType(
            XmlFileType.INSTANCE,
            "xml"
        ) //xml is needed for the compose xml wrapper thing
        applicationManager.registerApplicationService(
            FileBasedIndex::class.java,
            ScreenshotFileBasedIndex()
        ) //Resets the CoreFileTypeRegistry

        // Needed for file traversal
        FileTypeRegistry.setInstanceSupplier { fileTimeRegistry } // Reset

        //Needed by AndroidTargetData/RenderService
        applicationManager.registerApplicationService(
            FrameworkResourceRepositoryManager::class.java,
            FrameworkResourceRepositoryManager()
        )

        // Needed by RenderService
        applicationManager.registerApplicationService(
            StudioCrashReporter::class.java, Mockito.mock(
                StudioCrashReporter::class.java
            )
        )
        applicationManager.registerApplicationService(
            ProjectJdkTable::class.java,
            ProjectJdkTableImpl()
        )
        applicationManager.registerApplicationService(AndroidSdks::class.java, AndroidSdks())
        applicationManager.registerApplicationService(
            DownloadableFileService::class.java,
            DownloadableFileServiceImpl()
        )
        applicationManager.registerApplicationService(
            DownloadableFontCacheService::class.java,
            StudioDownloadableFontCacheService()
        )
        applicationManager.registerApplicationService(
            ExceptionDataConfiguration::class.java,
            ExceptionDataConfigurationImpl()
        )
        applicationManager.registerApplicationService(
            ExceptionDataCollection::class.java,
            ExceptionDataCollection()
        )

        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            XMLLanguage.INSTANCE,
            XMLParserDefinition()
        )
        LanguageASTFactory.INSTANCE.addExplicitExtension(XMLLanguage.INSTANCE, XmlASTFactory())
    }

    companion object {

        fun setupEnvVars(homePath: String?) {
            //HACK
            val relative = "../../../../../../"; //ROOT_FROM_UNBUNDLED_SDK;
            val baseDir = PathUtil.getResourcePathForClass(this::class.java).absolutePath //tools/
            val srcPath = Paths.get(baseDir, relative).normalize().toString();
            setPaths(homePath, srcPath, baseDir)

            Registry.get("platform.projectModel.workspace.model.file.index").setValue(false)
            Registry.get("gradle.report.recently.saved.paths").setValue("")
            Registry.get("kotlin.gradle.testing.enabled").setValue(false)
            System.setProperty("idea.platform.prefix", PlatformUtils.GATEWAY_PREFIX)
        }

        private fun setPaths(homePath: String?, srcPath: String, baseDir: String) {
            System.setProperty("idea.home.path", homePath ?: "${srcPath}/tools/base")
            System.setProperty("idea.application.info.value", (homePath ?: baseDir) + "/META-INF/ApplicationInfo.xml")
        }
    }

}
