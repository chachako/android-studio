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

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceVisitor
import com.android.ide.common.resources.SingleNamespaceResourceRepository
import com.android.resources.ResourceType
import com.android.tools.idea.res.LocalResourceRepository
import com.google.common.collect.ListMultimap
import com.intellij.openapi.vfs.VirtualFile

class ScreenshotResourceRepository(private val composeProject: ComposeProject) : LocalResourceRepository("Screenshot Repo") {
    override fun accept(visitor: ResourceVisitor): ResourceVisitor.VisitResult {
        return ResourceVisitor.VisitResult.CONTINUE
    }

    override fun getNamespaces(): MutableSet<ResourceNamespace> = mutableSetOf(composeProject.lintProject.resourceNamespace)


    override fun getLeafResourceRepositories(): MutableCollection<SingleNamespaceResourceRepository> {
        TODO("Not yet implemented")
    }

    override fun getMap(
        namespace: ResourceNamespace,
        resourceType: ResourceType
    ): ListMultimap<String, ResourceItem>? {
        // TODO: The map is how resources are mapped to values.
        return null
    }

    override fun computeResourceDirs(): MutableSet<VirtualFile> {
        TODO("Not yet implemented")
    }
}
