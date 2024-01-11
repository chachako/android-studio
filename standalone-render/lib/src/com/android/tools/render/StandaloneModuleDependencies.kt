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

import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.tools.module.ModuleDependencies
import com.android.tools.module.ViewClass

/** Stub version of [ModuleDependencies] for standalone rendering. */
internal class StandaloneModuleDependencies : ModuleDependencies {

    /**
     * Not used in compose rendering.
     * TODO(): Fix it to support appcompat in XML layouts.
     */
    override fun dependsOn(artifactId: GoogleMavenArtifactId): Boolean = false

    /**
     * This is used to know which R-classes to load for [ResourceIdManager]. In the standalone
     * rendering we load resource ids from the apk, not from R-classes, so this is empty.
     */
    override fun getResourcePackageNames(includeExternalLibraries: Boolean): List<String> =
        emptyList()

    /**
     * Not used in compose rendering. This is only used for creating a view from a superclass.
     * TODO(): Fix it to support Custom View/XML layouts or remove from rendering.
     */
    override fun findViewClass(fqcn: String): ViewClass? = null
}
