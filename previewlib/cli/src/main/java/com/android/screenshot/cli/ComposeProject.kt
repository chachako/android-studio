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

import com.android.tools.configurations.ConfigurationStateManager
import com.android.tools.idea.configurations.StudioConfigurationStateManager
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.projectsystem.AndroidProjectSystemProvider
import com.android.tools.idea.projectsystem.EP_NAME
import com.android.tools.idea.projectsystem.ProjectSyncModificationTracker
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.res.AndroidFileChangeListener
import com.android.tools.idea.res.ResourceFolderRegistry
import com.android.tools.idea.res.SampleDataListener
import com.android.tools.res.ResourceClassRegistry
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.mock.MockComponentManager
import com.intellij.openapi.roots.ModuleExtension
import com.intellij.openapi.roots.ModuleRootManagerEx
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.startup.StartupManager
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.IdeVirtualFileUrlManagerImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleDependencyIndexImpl
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageOnStorage
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking

/**
 * This class represents an IdeaProject. This class does not directly depend on modules, many of
 * the extensions and services setup by the project are required for modules to be configured
 * and used properly.
 */
class ComposeProject(val lintProject: com.android.tools.lint.detector.api.Project, private val fileIndex: ScreenshotProjectFileIndex) {

    lateinit var defaultProjectSystem: ScreenshotProjectSystem
    val virtualFileUrlManager = IdeVirtualFileUrlManagerImpl()

    init {
        registerProjectExtensions()
        setupProjectServices()
    }

    fun registerProjectExtensions() {
        val ideaProject = lintProject.ideaProject!!
        // Register project extension points
        CoreApplicationEnvironment.registerExtensionPoint(
            ideaProject.extensionArea,
            EP_NAME,
            AndroidProjectSystemProvider::class.java
        )
        // Needed by StudioConfigurationModelModule / ConfigurationManager::getOrCreateInstance
        CoreApplicationEnvironment.registerExtensionPoint(
            ideaProject.extensionArea,
            ModuleRootManagerEx.MODULE_EXTENSION_NAME.name,
            ModuleExtension::class.java
        )
        // Needed by ProjectFileIndex / findModuleForPsiElement
        CoreApplicationEnvironment.registerExtensionPoint(
            ideaProject.extensionArea,
            DirectoryIndexExcludePolicy.EP_NAME.name,
            DirectoryIndexExcludePolicy::class.java
        )

        defaultProjectSystem = ScreenshotProjectSystem(this)
        EP_NAME.getPoint(ideaProject).registerExtension(defaultProjectSystem, ideaProject)
    }

    fun setupProjectServices() {
        val ideaProject = lintProject.ideaProject!!
        val componentManager = ideaProject as MockComponentManager
        componentManager.registerService(
            ProjectSystemService::class.java,
            ProjectSystemService(ideaProject)
        )

        // Required by ModuleRootComponentBridge
        TODO("Incompatible with IntelliJ 2023.2") /*
        componentManager.registerService(VirtualFileUrlManager::class.java, virtualFileUrlManager)
        */

        // Required by StudioModuleClassLoader
        componentManager.registerService(
            ProjectSyncModificationTracker::class.java,
            ProjectSyncModificationTracker(ideaProject)
        )
        componentManager.registerService(
            FastPreviewManager::class.java,
            FastPreviewManager(ideaProject)
        )

        // Needed by FacetManager
        componentManager.registerService(
            WorkspaceModel::class.java, WorkspaceModelI(
                VersionedEntityStorageOnStorage(
                    ComposeModule.createEntityStorage(
                        ModuleId("android")
                    ).toSnapshot()
                )
            )
        )

        // Needed by MergedManifest
        componentManager.registerService(
            StartupManager::class.java,
            StartupManagerImpl(ideaProject, ideaProject.getCoroutineScope())
        )

        // Needed by StudioConfigurationModelModule
        componentManager.registerService(
            ConfigurationStateManager::class.java,
            StudioConfigurationStateManager()
        )

        // Needed by LibraryResourceClassLoader
        componentManager.registerService(ResourceClassRegistry::class.java, ResourceClassRegistry())
        componentManager.registerService(
            ResourceFolderRegistry::class.java,
            ResourceFolderRegistry(ideaProject)
        )
        componentManager.registerService(
            ModuleDependencyIndex::class.java,
            ModuleDependencyIndexImpl(ideaProject)
        )

        PluginManagerCore.isUnitTestMode = true // Required by ResourceFolderRegistry
        PluginManagerCore.scheduleDescriptorLoading(GlobalScope)
        val plugin = runBlocking { PluginManagerCore.getInitPluginFuture().await() }
        PluginManagerCore.setPluginSet(plugin)
        componentManager.registerService(
            SampleDataListener.Subscriber::class.java,
            SampleDataListener.Subscriber(ideaProject)
        )
        componentManager.registerService(
            AndroidFileChangeListener::class.java,
            AndroidFileChangeListener()
        )

        // Needed for XmlExtension/DirectoryIndexIml
        componentManager.registerService(
            WorkspaceFileIndex::class.java,
            ScreenshotWorkspaceFileIndex()
        )

        // Needed for XmlExtension (findModuleForPsiElement)
        //val directoryIndex = DirectoryIndexImpl(ideaProject)
        //componentManager.registerService(DirectoryIndex::class.java, directoryIndex)
        componentManager.registerService(
            ProjectFileIndex::class.java,
            fileIndex
        )
    }
}
