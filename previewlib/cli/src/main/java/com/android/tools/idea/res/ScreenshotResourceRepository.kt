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
package com.android.tools.idea.res

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.SingleNamespaceResourceRepository
import com.android.screenshot.cli.ComposeModule
import com.android.screenshot.cli.ComposeProject
import com.android.tools.idea.util.toVirtualFile
import com.android.tools.lint.model.LintModelAndroidLibrary

class ScreenshotResourceRepository(private val composeProject: ComposeProject, private val composeModule: ComposeModule) : MultiResourceRepository("ScreenshotTesting"),
                                                                                                                           SingleNamespaceResourceRepository {

    val folderResource =
        (composeProject.lintProject.resourceFolders.map{it.toVirtualFile()} +
         composeProject.lintProject.buildVariant!!.mainArtifact.dependencies.packageDependencies.getAllLibraries().filterIsInstance<LintModelAndroidLibrary>().mapNotNull { it.resFolder.toVirtualFile() }).mapNotNull {
            ResourceFolderRegistry(composeProject.lintProject.ideaProject!!).get(composeModule.facet, it!!)
        }

    init {
        setChildren(folderResource.toMutableList(), listOf(), listOf())
    }
    override fun getNamespaces(): MutableSet<ResourceNamespace> = mutableSetOf(composeProject.lintProject.resourceNamespace)


    override fun getLeafResourceRepositories(): MutableCollection<SingleNamespaceResourceRepository> {
        TODO("Not yet implemented")
    }

    override fun getNamespace(): ResourceNamespace = composeProject.lintProject.resourceNamespace

    override fun getPackageName(): String? = composeProject.lintProject.`package`
}
