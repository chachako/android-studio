/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.declarative.internal.parsers

import com.android.declarative.internal.IssueLogger
import com.android.declarative.internal.model.ResolvedModuleInfo
import com.android.declarative.internal.toml.forEach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tomlj.TomlParseResult
import java.io.File

class DependenciesResolver(
    private val logger: IssueLogger
) {

    /**
     * Asynchronous function to read all the build.gradle.toml files in a project.
     * Uses the settings.gradle.toml files to find the list of all included projects and
     * parse each project's build.gradle.toml file.
     *
     * @return a [Map] indexed by Gradle's project path and the associated [ResolvedModuleInfo]
     */
    suspend fun readAllProjectsDependencies(
        fileReader: (relativePath: String, fileName: String) -> String?,
        parsedDecl: TomlParseResult
    ): Map<String, ResolvedModuleInfo> {
        val mapOfDependencies = mutableMapOf<String, ResolvedModuleInfo>()
        System.currentTimeMillis().run {
            coroutineScope {
                parsedDecl.forEach("include") { projectPath ->
                    launch {
                        readProjectDependencies(fileReader, projectPath)?.let {
                            synchronized(mapOfDependencies) {
                                mapOfDependencies[projectPath] = it
                            }
                        }
                    }
                }
            }
            println("all project dependencies parsing finished in ${System.currentTimeMillis() - this} ms")
        }
        return mapOfDependencies
    }

    /**
     * Read a project's `build.gradle.toml` file and produced a [ResolvedModuleInfo] if parsing
     * is successful.
     */
    private suspend fun readProjectDependencies(
        fileReader: (relativePath: String, fileName: String) -> String?,
        projectPath: String
    ): ResolvedModuleInfo? =
        withContext(Dispatchers.IO) {
            return@withContext fileReader(projectPath, "build.gradle.toml")?.let {
                val tomlParseResult = DeclarativeFileParser(logger).parseDeclarativeFile(
                    "$projectPath${File.separatorChar}build.gradle.toml",
                    it)
                 ModuleInfoResolver().parse(tomlParseResult, projectPath, logger)
            }
        }
}
