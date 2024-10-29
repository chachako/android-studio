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

import com.android.build.api.dsl.SettingsExtension
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import java.nio.file.Path
import kotlin.io.path.writeText

interface GradleSettingsDefinition {

    val plugins: MutableList<PluginType>

    val android: SettingsExtension
    /**
     * Configures the android section of the project.
     *
     * This will fails if no android plugins were added.
     */
    fun android(action: SettingsExtension.() -> Unit)
}

internal class GradleSettingsDefinitionImpl: GradleSettingsDefinition {

    override val plugins = mutableListOf<PluginType>()

    override val android: SettingsExtension
        get() = throw RuntimeException("todo")


    override fun android(action: SettingsExtension.() -> Unit) {
        if (!plugins.contains(PluginType.ANDROID_SETTINGS)) {
            throw RuntimeException("Settings must contain ANDROID_SETTINGS to configure the android extension")
        }
        action(android)
    }

    internal fun write(
        location: Path,
        repositories: Collection<Path>,
        includedBuildNames: Collection<String>,
        subProjectPaths: Collection<String>,
        writerProvider: WriterProvider
    ) {
        writerProvider.getBuildWriter().apply {
            block("pluginManagement") {
                block("repositories") {
                    for (repository in repositories) {
                        mavenSnippet(repository)
                    }
                }
            }

            block("plugins") {
                for (plugin in plugins.toSet()) {
                    pluginId(plugin.id, plugin.version)
                }
            }

            block("dependencyResolutionManagement") {
                method("repositoriesMode.set", rawString("RepositoriesMode.FAIL_ON_PROJECT_REPOS"))
                block("repositories") {
                    for (repository in repositories) {
                        mavenSnippet(repository)
                    }

                }
            }

            for (build in includedBuildNames) {
                method("includeBuild", build)
            }

            if (plugins.contains(PluginType.ANDROID_SETTINGS)) {

            }

            for (project in subProjectPaths) {
                method("include", project)
            }
        }.also {
            val file = location.resolve(it.settingsFileName)
            file.writeText(it.toString())
        }

    }
}

private fun BuildWriter.mavenSnippet(repo: Path) {
    block("maven") {
        set("url", rawMethod("uri", repo.toUri().toString()))
        block("metadataSources") {
            method("mavenPom")
            method("artifact")
        }
    }
}
