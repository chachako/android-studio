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
import com.android.declarative.internal.model.DependencyInfo.Alias
import com.android.declarative.internal.model.DependencyInfo
import com.android.declarative.internal.model.DependencyType
import com.android.declarative.internal.model.DependencyInfo.ExtensionFunction
import com.android.declarative.internal.model.DependencyInfo.Files
import com.android.declarative.internal.model.DependencyInfo.Maven
import com.android.declarative.internal.model.DependencyInfo.Notation
import com.android.declarative.internal.toml.InvalidTomlException
import com.android.declarative.internal.toml.safeGetString
import org.tomlj.TomlArray
import org.tomlj.TomlPosition
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
                is TomlArray -> parseTomlArrayDeclaration(
                    dependencyArray = value,
                    configurationName = key,
                    dependencies,
                    tomlTable.inputPositionOf(key)
                )
                is TomlTable -> issueLogger.raiseError(
                    InvalidTomlException(
                        tomlTable.inputPositionOf(key),
                        """
                            Dependencies cannot be expressed with a Toml table, use an array instead
                            Example:
                                [dependencies]
                                testImplementation = [
                                    { notation = "libs.junit" },
                                ]
                        """.trimIndent())
                )
                else -> issueLogger.raiseError(
                    InvalidTomlException(
                        tomlTable.inputPositionOf(key),
                        """
                            You must use a Toml array to express dependencies.
                            Example:
                                [dependencies]
                                testImplementation = [
                                    { notation = "libs.junit" },
                                ]
                        """.trimIndent())
                )
            }
        }
        return dependencies.toList()
    }

    private fun parseTomlArrayDeclaration(
        dependencyArray: TomlArray,
        configurationName: String,
        dependencies: MutableList<DependencyInfo>,
        position: TomlPosition?,
    ) {
        if (dependencyArray.isEmpty) {
            issueLogger.logger.warning(
                "Warning: ${position ?: ""} : Empty $configurationName dependencies declaration"
            )
            return
        }
        for (i in 0..dependencyArray.size() - 1 ) {
            when(val dependency = dependencyArray.get(i)) {
                is TomlTable -> parseDependencyDeclaration(
                    depTable = dependency,
                    key = "",
                    configurationName = configurationName,
                    dependencies = dependencies
                )
                is TomlArray -> issueLogger.raiseError(
                    dependencyArray.inputPositionOf(i),
                    "Incorrect Toml array dependency declaration, a dependency is declared " +
                            "as a Toml table, for instance : { notation = 'com.foo:bar:1.2.3' }")
                is String -> parseString(dependency, configurationName, dependencies)
                else -> issueLogger.raiseError(
                    dependencyArray.inputPositionOf(i),
                    "Incorrect dependency declaration, a dependency is declared " +
                            "as a Toml table, for instance : { notation = 'com.foo:bar:1.2.3' }")
            }
        }
    }

    private fun parseString(
        value: String,
        configurationName: String,
        dependencies: MutableList<DependencyInfo>,
    ) {
        when(value) {
            "alias" -> {
                issueLogger.logger.info("Adding ALIAS dependency : ${value} to $configurationName")
                dependencies.add(
                    Alias(
                        configurationName,
                        value
                    )

                )
            } else -> parseStringDeclaration(
                notation = value,
                configurationName = configurationName,
                dependencies = dependencies
            )
        }
    }

    private fun parseDependencyDeclaration(
        depTable: TomlTable,
        key: String,
        configurationName: String,
        dependencies: MutableList<DependencyInfo>,
    ) {
        depTable.getString("project")?.let { notation ->
            issueLogger.logger.info("Adding project dependency : $notation to $configurationName")
            dependencies.add(
                Notation(
                    DependencyType.PROJECT,
                    configurationName,
                    notation)
            )
            return
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
            issueLogger.logger.info("Adding files dependency $files to $configurationName")
            dependencies.add(
                Files(
                    configurationName,
                    fileCollection,
                )
            )
            return
        }
        depTable.getString("notation")?.let { notation ->
            parseStringDeclaration(notation, configurationName, dependencies)
            return
        }
        depTable.getString("name")?.let { name ->
            dependencies.add(
                Maven(
                    configurationName,
                    depTable.safeGetString("group"),
                    name,
                    depTable.safeGetString("version"),
                )
            )
            return
        }
        depTable.getString("extension")?.let { extension ->
            dependencies.add(
                ExtensionFunction(
                    configurationName,
                    extension,
                    mapOf("module" to depTable.safeGetString("module")),
                )
            )
        }
        if (key.isNotEmpty() && depTable.size() <= 1) {
            // if there are no other key, we assume a project dependency, using the key as the project coordinates.
            // lib1 = { configuration = "testImplementation" }
            // will be like testImplementation { project(":lib1") }
            dependencies.add(
                Notation(
                    DependencyType.PROJECT,
                    configurationName,
                    ":$key"
                )
            )
        }
    }

    private fun parseStringDeclaration(
        notation: String,
        configurationName: String,
        dependencies: MutableList<DependencyInfo>,
    ) {
        dependencies.add(
            Notation(
                DependencyType.NOTATION,
                configurationName,
                notation
            )
        )
    }
}
