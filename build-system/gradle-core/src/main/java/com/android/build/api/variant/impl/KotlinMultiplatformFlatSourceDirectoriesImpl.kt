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

package com.android.build.api.variant.impl

import com.android.build.api.variant.SourceDirectories
import com.android.build.gradle.internal.services.VariantServices
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.util.PatternFilterable
import java.io.File

/**
 * Implementation of [SourceDirectories.Flat] that is read-only, and is populated with a lazy
 * provider from the kotlin compilation sourceSets, since we can't guarantee that the transitive
 * sourceSets will be ready when we start creating the components.
 */
class KotlinMultiplatformFlatSourceDirectoriesImpl(
    name: String,
    private val variantServices: VariantServices,
    variantDslFilters: PatternFilterable?,
    private val compilation: KotlinMultiplatformAndroidCompilation
): FlatSourceDirectoriesImpl(name, variantServices, variantDslFilters), SourceDirectories.Flat {

    private lateinit var kotlinMultiplatformPluginSources: Provider<out Collection<Directory>>
    private lateinit var internalSources : Provider<out Collection<DirectoryEntry>>

    override val all: Provider<out Collection<Directory>>
        get() = kotlinMultiplatformPluginSources

    internal fun initSourcesFromKotlinPlugin(sources: Provider<out Collection<DirectoryEntry>>) {
        internalSources = sources
        kotlinMultiplatformPluginSources = sources.map { directoryEntries ->
            directoryEntries.flatMap { directoryEntry ->
                directoryEntry.asFiles(
                    variantServices.provider {
                        variantServices.projectInfo.projectDirectory
                    }
                ).get()
            }
        }
    }

    override fun addSource(directoryEntry: DirectoryEntry) {
        throw IllegalAccessException("$name sources for kotlin multiplatform android plugin " +
                "are read-only, to append to the $name sources you need to add your sources to " +
                "the compilation named (${compilation.name}).")
    }

    override fun getAsFileTrees(): Provider<List<Provider<List<ConfigurableFileTree>>>> {
        val fileTreeFactory = variantServices.fileTreeFactory()
        return internalSources.map { entries ->
            entries.map { sourceDirectory ->
                sourceDirectory.asFileTree(fileTreeFactory)
            }
        }
    }

    override fun getVariantSources() = internalSources.get().toList()

    override fun variantSourcesForModel(filter: (DirectoryEntry) -> Boolean): List<File> {
        return internalSources.get().filter { filter(it) }.flatMap {
            it.asFiles(
                variantServices.provider {
                    variantServices.projectInfo.projectDirectory
                }
            ).get()
        }.map {
            it.asFile
        }
    }
}
