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
import com.android.declarative.internal.model.DependencyInfo
import com.android.declarative.internal.model.DependencyType
import com.android.declarative.internal.model.FilesDependencyInfo
import com.android.declarative.internal.model.MavenDependencyInfo
import com.android.declarative.internal.model.NotationDependencyInfo
import com.android.declarative.internal.toml.safeGetString
import org.tomlj.TomlArray
import org.tomlj.TomlTable

/**
 * Parses the 'dependencies' toml table and creates a [List] of [DependencyInfo] for the
 * module.
 *
 * TODO: make this a Parser subtype ?
 */
@Suppress("UnstableApiUsage")
class DependencyParser(
    private val issueLogger: IssueLogger,
) {
    fun parseToml(tomlTable: TomlTable): List<DependencyInfo> {
        val dependencies = mutableListOf<DependencyInfo>()
        tomlTable.keySet().sorted().forEach { key ->
            when(val value = tomlTable.get(key)) {
                is TomlTable -> parseTomlTableDeclaration(
                    depTable = value,
                    key = key,
                    dependencies = dependencies
                )
                is String -> parseStringDeclaration(
                    notation = value,
                    configurationName = "implementation",
                    dependencies = dependencies
                )
            }
        }
        return dependencies.toList()
    }

    private fun parseTomlTableDeclaration(
        depTable: TomlTable,
        key: String,
        dependencies: MutableList<DependencyInfo>
    ) {
        if (depTable.contains("configuration")) {
            parseDependencyDeclaration(depTable, key, dependencies)
            return
        }
        depTable.keySet().sorted().forEach { element ->
            when(val value = depTable.get(element)) {
                is TomlTable -> {
                    if (value.contains("configuration")) {
                        parseDependencyDeclaration(
                            depTable = value,
                            key = element,
                            dependencies = dependencies
                        )
                    } else {
                        parseDependencyDeclaration(
                            depTable = value,
                            key = element,
                            configurationName = key,
                            dependencies = dependencies
                        )
                    }
                }
                is String -> parseStringDeclaration(
                    notation = value,
                    configurationName = key,
                    dependencies = dependencies
                )
            }
        }
    }

    private fun parseDependencyDeclaration(
        depTable: TomlTable,
        key: String,
        dependencies: MutableList<DependencyInfo>) {
        // todo: figure out ClientModule
        issueLogger.expect(
            depTable.isString("configuration"),
            { "`configuration` is expected to be String, got ${depTable.get("configuration")}" },
            depTable.getString("configuration"),
            { "`configuration` must be provided" },
        ) { configurationName ->
            parseDependencyDeclaration(depTable, key, configurationName, dependencies)
        }
    }

    private fun parseDependencyDeclaration(
        depTable: TomlTable,
        key: String,
        configurationName: String,
        dependencies: MutableList<DependencyInfo>,
    ) {
        if (depTable.size() <= 1) {
            // if there are no other key, we assume a project dependency, using the key as the project coordinates.
            // lib1 = { configuration = "testImplementation" }
            // will be like testImplementation { project(":lib1") }
            dependencies.add(
                NotationDependencyInfo(
                    DependencyType.PROJECT,
                    configurationName,
                    ":$key")
            )
        } else {
            depTable.getString("project")?.let { notation ->
                dependencies.add(
                    NotationDependencyInfo(
                        DependencyType.PROJECT,
                        configurationName,
                        notation)
                )
            }
            depTable.get("files")?.let { files ->
                val fileCollection = mutableListOf<String>()
                when(files) {
                    is TomlArray -> {
                        for (i in 0 until files.size()) {
                            fileCollection.add(files.getString(i))
                        }
                    }
                    is String -> {
                        fileCollection.add(files)
                    }
                }
                println("adding files dependency $files to $configurationName")
                dependencies.add(
                    FilesDependencyInfo(
                        configurationName,
                        fileCollection,
                    )
                )
            }
            depTable.getString("notation")?.let { notation ->
                parseStringDeclaration(notation, configurationName, dependencies)
            }
            depTable.getString("name")?.let { name ->
                dependencies.add(
                    MavenDependencyInfo(
                        configurationName,
                        depTable.safeGetString("group"),
                        name,
                        depTable.safeGetString("version"),
                    )
                )
            }
        }
    }

    private fun parseStringDeclaration(
        notation: String,
        configurationName: String,
        dependencies: MutableList<DependencyInfo>,
    ) {
        dependencies.add(
            NotationDependencyInfo(
                DependencyType.NOTATION,
                configurationName,
                notation
            )
        )
    }
}
