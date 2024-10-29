/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture.project.builder

import com.android.build.gradle.integration.common.fixture.testprojects.DependenciesBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.DependenciesBuilderImpl
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.SourceFile
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

interface GradleProjectDefinition {
    val path: String

    val plugins: MutableList<PluginType>

    var group: String?
    var version: String?

    fun layout(action: GradleProjectLayout.() -> Unit)

    /**
     * Configures dependencies of the project
     */
    fun dependencies(action: DependenciesBuilder.() -> Unit)

    /**
     * Wraps a library binary with a module
     */
    fun wrap(library: ByteArray, fileName: String)
}

internal open class GradleProjectDefinitionImpl(override val path: String): GradleProjectDefinition {

    override val plugins = mutableListOf<PluginType>()
    override var group: String? = null
    override var version: String? = null

    private val dependencies: DependenciesBuilderImpl = DependenciesBuilderImpl()

    override fun dependencies(action: DependenciesBuilder.() -> Unit) {
        action(dependencies)
    }

    override fun wrap(library: ByteArray, fileName: String) {
        throw RuntimeException("todo")
    }

    override fun layout(action: GradleProjectLayout.() -> Unit) {
        throw RuntimeException("todo")
    }

    internal fun writeSubProject(
        location: Path,
        writerProvider: WriterProvider
    ) {
        write(location, listOf(), isRoot = false, writerProvider)
    }

    internal fun writeRoot(
        location: Path,
        rootPlugins: Collection<PluginType>,
        writerProvider: WriterProvider
    ) {
        write(location, rootPlugins, isRoot = true, writerProvider)
    }

    private fun write(
        location: Path,
        rootPlugins: Collection<PluginType>,
        isRoot: Boolean,
        writerProvider: WriterProvider
    ) {
        location.createDirectories()

        writerProvider.getBuildWriter().apply {
            block("plugins") {
                // write the plugins used by this project
                for (plugin in plugins.toSet()) {
                    // this is used by this project, but we have to display a version if this is
                    // the root project only.
                    pluginId(plugin.id, if (isRoot) plugin.version else null)
                }

                // write the plugins used by the other projects (only for root project)
                if (rootPlugins.isNotEmpty()) {
                    val remainingPlugins = rootPlugins - plugins.toSet()

                    // we can exclude plugin with no versions
                    for (plugin in remainingPlugins.filter { it.version != null }) {
                        // always write the version is available.
                        // don't apply these
                        pluginId(plugin.id, plugin.version, apply = false)
                    }
                }
            }

            dependencies.write(this, location)
        }.also {
            val file = location.resolve(it.buildFileName)
            file.writeText(it.toString())
        }

        // write the rest of the content.
        // FIXME
    }
}
