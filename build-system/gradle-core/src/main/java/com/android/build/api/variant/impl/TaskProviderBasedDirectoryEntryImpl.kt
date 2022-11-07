/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.internal.scope.getDirectories
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.util.PatternFilterable

/**
 * Implementation of [DirectoryEntry] based on [Provider] of [Directory] with embedded task
 * dependency. The [TaskProvider] is also provided to the constructor as creating a
 * [ConfigurableFileTree] from a [Provider] is not enough, and the [ConfigurableFileTree.builtBy]
 * must be explicitly called.
 */
class TaskProviderBasedDirectoryEntryImpl(
    override val name: String,
    val directoryProvider: Provider<Directory>,
    private val storage: ConfigurableFileCollection,
    override val isGenerated: Boolean = true,
    override val isUserAdded: Boolean = false,
    override val shouldBeAddedToIdeModel: Boolean = false,
): DirectoryEntry {

    init {
        storage.setFrom(directoryProvider)
    }

    /**
     * Filters cannot be set on task provided source folders, tasks should just not create extra
     * sources that would require filtering.
     */
    override val filter: PatternFilterable? = null
    override fun asFiles(
      projectDir: Provider<Directory>
    ): Provider<out Collection<Directory>> =
        storage.getDirectories(projectDir.get())

    override fun asFileTree(
        fileTreeCreator: () -> ConfigurableFileTree,
        projectDir: Provider<Directory>,
    ): Provider<List<ConfigurableFileTree>> =
        projectDir.map {
            listOf(fileTreeCreator().setDir(directoryProvider).builtBy(directoryProvider))
        }
}
