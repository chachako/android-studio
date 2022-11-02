/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.lint

import com.android.tools.lint.client.api.GradleVisitor
import com.android.tools.lint.detector.api.DefaultPosition
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Location.Companion.create
import org.tomlj.Toml
import org.tomlj.TomlParseResult
import org.tomlj.TomlPosition
import org.tomlj.TomlTable

/** Gradle visitor for Kotlin Script files. */
class TomlGradleVisitor : GradleVisitor() {
    private val LIBRARIES_KEY = "libraries"
    private val VERSIONS_KEY = "versions"
    override fun getStartOffset(context: GradleContext, cookie: Any): Int {
        val location = cookie as Location
        return location.start!!.offset
    }

    override fun createLocation(context: GradleContext, cookie: Any): Location = cookie as Location

    private fun toVisitorPosition(context: GradleContext, position: TomlPosition, offsets: List<Int>): Location {
        val line = position.line() - 1
        val column = position.column() - 1
        val offsetStart = if (line == 0) column else offsets[line - 1] + position.column()
        val offsetEnd = offsets[line]
        val start = DefaultPosition(line, column, offsetStart)
        val endColumn = if (line == 0) offsets[0] - 1 else offsets[line] - offsets[line - 1]
        val end = DefaultPosition(line, endColumn, offsetEnd)
        return create(context.file, start, end)
    }

    private fun getOffsets(context: GradleContext): List<Int> {
        val result = mutableListOf<Int>()
        val source = context.getContents().toString()

        var index = 0
        do {
            index = source.indexOf("\n", index + 1)
            if (index > 0) result.add(index)
        } while (index >= 0)

        if (index < source.length - 1) result.add(source.length - 1)
        return result
    }

    override fun visitBuildScript(context: GradleContext, detectors: List<GradleScanner>) {
        val sequence: CharSequence = context.getContents() ?: return
        val source = sequence.toString()
        val result: TomlParseResult = Toml.parse(source)

        val librariesTable: TomlTable? = result.getTable(LIBRARIES_KEY)
        val tomlContext = TomlGradleVisitorContext(context, detectors, result, getOffsets(context))
        librariesTable?.let { parseLibraries(it, tomlContext) }
    }

    private fun parseLibraries(librariesTable: TomlTable, tomlContext: TomlGradleVisitorContext) {
        with(tomlContext) {
            librariesTable.entrySet().forEach { (key, lib) ->
                when (lib) {
                    is String -> {
                        val position = librariesTable.inputPositionOf(key)!!
                        val location = toVisitorPosition(context, position, offsets)
                        callDetectors(tomlContext, lib, location)
                    }

                    is TomlTable -> {
                        resolveLibrary(lib, key, tomlContext)?.let { (libraryString, location) ->
                            callDetectors(tomlContext, libraryString, location)
                        }
                    }
                }
            }
        }
    }

    private fun wrapWithApostrophe(value: String): String = "'$value'"

    private fun resolveLibrary(lib: TomlTable, alias: String, tomlContext: TomlGradleVisitorContext): Pair<String, Location>? {
        val strictlyKey = "strictly"
        val version = lib.get("version")
        val builder = extractModule(lib)
        if (builder.isNotEmpty())
            when (version) {
                is String -> {
                    val location = extractLocation("$LIBRARIES_KEY.$alias", tomlContext.result, tomlContext)
                    return Pair(builder.append(":$version").toString(), location)
                }

                is TomlTable -> {
                    version.getString("ref")?.let{ ref ->
                        val path = "$VERSIONS_KEY.$ref"
                        val versionElement = tomlContext.result.get(path)
                        val location = extractLocation(path, tomlContext.result, tomlContext)
                        when (versionElement) {
                            is String ->
                                return Pair(builder.append(":$versionElement").toString(), location)
                            is TomlTable ->
                                versionElement.get(strictlyKey)?.let { return Pair(builder.append(":$it!!").toString(), location) }
                            else -> null
                        }
                    }
                    version.getString(strictlyKey)?.let { strictly ->
                        val location = extractLocation("$LIBRARIES_KEY.$alias", tomlContext.result, tomlContext)
                        return Pair(builder.append(":$strictly!!").toString(), location)
                    }
                }
            }
        return null
    }

    private fun extractModule(lib: TomlTable): StringBuilder{
        val group = lib.getString("group")
        val name = lib.getString("name")
        val module = lib.getString("module")
        return StringBuilder(module?.let { "$module" } ?: ("$group:$name"))
    }

    private fun extractLocation(path: String, tomlTable: TomlTable, tomlContext: TomlGradleVisitorContext): Location {
        val position = tomlTable.inputPositionOf(path)!!
        return toVisitorPosition(tomlContext.context, position, tomlContext.offsets)
    }

    // Method adds parameters that expects gradle detector. Making dependency looks like coming from gradle file
    private fun callDetectors(tomlContext: TomlGradleVisitorContext, value: String, location: Any) {
        tomlContext.detectors.forEach {
            it.checkDslPropertyAssignment(
                tomlContext.context,
                "",
                wrapWithApostrophe(value),
                "dependencies",
                null,
                location,
                location,
                location
            )
        }
    }
}

internal data class TomlGradleVisitorContext(
    val context: GradleContext,
    val detectors: List<GradleScanner>,
    val result: TomlParseResult,
    val offsets: List<Int>
)
