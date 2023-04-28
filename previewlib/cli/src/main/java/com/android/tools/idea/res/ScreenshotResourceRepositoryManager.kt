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
import com.android.ide.common.resources.Locale
import com.android.ide.common.resources.ResourceRepository
import com.android.screenshot.cli.ComposeModule
import com.android.screenshot.cli.ComposeProject
import com.android.tools.res.CacheableResourceRepository
import com.android.tools.res.ResourceNamespacing
import com.android.tools.res.ResourceRepositoryManager
import com.google.common.collect.ImmutableList

class ScreenshotResourceRepositoryManager(private val composeProject: ComposeProject, private val composeModule: ComposeModule) : ResourceRepositoryManager {

    override val appResources: CacheableResourceRepository
        get() = ScreenshotResourceRepository(composeProject, composeModule)
    override val projectResources: ResourceRepository
        get() = TODO("Not yet implemented")
    override val namespacing: ResourceNamespacing
        get() = ResourceNamespacing.DISABLED
    override val namespace: ResourceNamespace
        get() = ResourceNamespace.RES_AUTO
    override val localesInProject: ImmutableList<Locale>
        get() = TODO("Not yet implemented")
    override val moduleResources: ResourceRepository
        get() = TODO("Not yet implemented")

    override fun getFrameworkResources(languages: Set<String>): ResourceRepository? {
        TODO("Not yet implemented")
    }

}
