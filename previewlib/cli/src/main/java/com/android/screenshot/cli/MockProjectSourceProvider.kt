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

import com.android.tools.idea.projectsystem.IdeaSourceProvider
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.util.toVirtualFile
import com.android.tools.lint.detector.api.Project
import com.intellij.openapi.vfs.VirtualFile

class MockProjectSourceProvider(private val project: Project, ) : NamedIdeaSourceProvider {
    override val name: String get() = project.name
    override val scopeType: ScopeType
        get() = ScopeType.MAIN
    override val manifestFileUrls: Iterable<String>
        get() = project.manifestFiles.map{it.absolutePath}.asIterable()
    override val manifestFiles: Iterable<VirtualFile>
        get() = project.manifestFiles.map{it.toVirtualFile()!!}.asIterable()
    override val manifestDirectoryUrls: Iterable<String>
        get() = project.manifestFiles.map{it.parentFile.absolutePath}.asIterable()
    override val manifestDirectories: Iterable<VirtualFile>
        get() = project.manifestFiles.map{it.parentFile.toVirtualFile()!!}.asIterable()
    override val javaDirectoryUrls: Iterable<String>
        get() = project.javaSourceFolders.map{it.parentFile.absolutePath}.asIterable()
    override val javaDirectories: Iterable<VirtualFile>
        get() = project.javaSourceFolders.map{it.toVirtualFile()!!}.asIterable()
    override val kotlinDirectoryUrls: Iterable<String>
        get() = project.javaSourceFolders.map{it.absolutePath}.asIterable()
    override val kotlinDirectories: Iterable<VirtualFile>
       get() = project.javaSourceFolders.map{it.toVirtualFile()!!}.asIterable()
    override val resourcesDirectoryUrls: Iterable<String>
        get() = project.resourceFolders.map{it.absolutePath}.asIterable()
    override val resourcesDirectories: Iterable<VirtualFile>
       get() = project.resourceFolders.map{it.parentFile.toVirtualFile()!!}.asIterable()
    override val aidlDirectoryUrls: Iterable<String>
        get() = emptyList()
    override val aidlDirectories: Iterable<VirtualFile>
       get() = emptyList()
    override val renderscriptDirectoryUrls: Iterable<String>
        get() = emptyList()
    override val renderscriptDirectories: Iterable<VirtualFile>
       get() = emptyList()
    override val jniLibsDirectoryUrls: Iterable<String>
        get() = emptyList()
    override val jniLibsDirectories: Iterable<VirtualFile>
       get() = emptyList()
    override val resDirectoryUrls: Iterable<String>
        get() = project.resourceFolders.map{it.absolutePath}.asIterable()
    override val resDirectories: Iterable<VirtualFile>
       get() = project.resourceFolders.map{it.toVirtualFile()!!}.asIterable()
    override val assetsDirectoryUrls: Iterable<String>
        get() = project.assetFolders.map{it.absolutePath}.asIterable()
    override val assetsDirectories: Iterable<VirtualFile>
       get() = project.assetFolders.map{it.toVirtualFile()!!}.asIterable()
    override val shadersDirectoryUrls: Iterable<String>
        get() = emptyList()
    override val shadersDirectories: Iterable<VirtualFile>
       get() = emptyList()
    override val mlModelsDirectoryUrls: Iterable<String>
        get() = emptyList()
    override val mlModelsDirectories: Iterable<VirtualFile>
       get() = emptyList()
    override val baselineProfileDirectoryUrls: Iterable<String>
        get() = emptyList()
    override val baselineProfileDirectories: Iterable<VirtualFile>
       get() = emptyList()
    override val custom: Map<String, IdeaSourceProvider.Custom>
        get() = TODO("Not yet implemented")
}
