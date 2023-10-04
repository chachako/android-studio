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
import com.android.ide.common.resources.Locale
import com.android.ide.common.resources.ResourceRepository
import com.android.tools.res.CacheableResourceRepository
import com.android.tools.res.ResourceNamespacing
import com.android.tools.res.ResourceRepositoryManager
import com.google.common.collect.ImmutableList

/**
 * [ResourceRepositoryManager] wrapping a single [CacheableResourceRepository] containing all the
 * resources.
 */
internal class StandaloneResourceRepositoryManager(
    resourcesRepo: CacheableResourceRepository
) : ResourceRepositoryManager {
    override val appResources: CacheableResourceRepository = resourcesRepo
    override val projectResources: ResourceRepository = resourcesRepo
    // TODO(): Support namespaced resources
    override val namespacing: ResourceNamespacing = ResourceNamespacing.DISABLED
    override val namespace: ResourceNamespace = ResourceNamespace.RES_AUTO

    // Not used in the standalone rendering.
    override val localesInProject: ImmutableList<Locale> = ImmutableList.of()
    override val moduleResources: ResourceRepository = resourcesRepo

    // Not used in the standalone rendering.
    override fun getFrameworkResources(languages: Set<String>): ResourceRepository? = null
}
