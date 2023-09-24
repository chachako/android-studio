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

import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.model.MergedManifestModificationTracker
import com.android.tools.rendering.api.IdeaModuleProvider
import com.intellij.facet.FacetManager
import com.intellij.openapi.module.Module
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.VersionedEntityStorageOnStorage
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetConfiguration
import org.jetbrains.android.facet.ResourceFolderManager
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.mockito.Mockito

/**
 * This class represents an Android Studio Module that is a child of the current project.
 * The class is reused for dependency modules that the project has. The minimal setup here is
 * required for a module to be used in screenshot testing.
 */
class ComposeModule(
    private val composeProject: ComposeProject, private val dependencies: Dependencies? = null
) : IdeaModuleProvider {

    var module = Mockito.mock(ModuleBridge::class.java)
    var facet = createFacet()

    init {
        setupModule()
    }

    fun setupModule(): ModuleBridge {
        Mockito.`when`(module.name).thenReturn("")
        Mockito.`when`(module.project)
            .thenReturn(composeProject.lintProject.ideaProject) // Needed by StudioModuleClassLoaderManager

        // Needed by StudioModuleClassLoader
        val id = ModuleId("android")
        Mockito.`when`(module.moduleEntityId).thenReturn(id) // Needed by FacetManager
        val storage = createEntityStorage(id)
        Mockito.`when`(module.entityStorage)
            .thenReturn(VersionedEntityStorageOnStorage(storage.toSnapshot()))

        Mockito.`when`(module.getService(ModuleClassLoaderOverlays::class.java))
            .thenReturn(ModuleClassLoaderOverlays(module))

        // Needed by StudioAndroidModuleInfo::getMinSdkVersion
        val manifestTracker = MergedManifestModificationTracker(module)
        Mockito.`when`(module.getService(MergedManifestModificationTracker::class.java))
            .thenReturn(manifestTracker)

        // Needed by getAppTheme / StudioThemeInfoProvider
        Mockito.`when`(module.getService(MergedManifestManager::class.java))
            .thenReturn(MergedManifestManager(module))

        val resourceFolderManager = ResourceFolderManager(module)
        Mockito.`when`(module.getService(ResourceFolderManager::class.java))
            .thenReturn(resourceFolderManager)

        // Needed by the Manifest merger
        val facetManager = Mockito.mock(FacetManager::class.java)
        Mockito.`when`(facetManager.getFacetByType(AndroidFacet.ID)).thenReturn(facet)
        Mockito.`when`(module.getComponent(FacetManager::class.java)).thenReturn(facetManager)
        return module
    }

    fun createFacet(): AndroidFacet {
        val facetConfig = Mockito.spy(AndroidFacetConfiguration())
        Mockito.`when`(facetConfig.isAppOrFeature).thenReturn(false)
        val facet = Mockito.spy(AndroidFacet(module, "Android", facetConfig))
        Mockito.`when`(facet.module).thenReturn(module)
        return facet
    }

    companion object {

        fun createEntityStorage(id: ModuleId): MutableEntityStorage {
            val storage = MutableEntityStorage.create()
            val entity =
                ModuleEntity.invoke(id.name, listOf(), Mockito.mock(EntitySource::class.java), null)
            storage.addEntity(entity)
            return storage
        }

    }
    override fun getIdeaModule(): Module = module
}
