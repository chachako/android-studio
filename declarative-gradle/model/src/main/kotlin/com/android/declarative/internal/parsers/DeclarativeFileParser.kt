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
import org.tomlj.Toml
import org.tomlj.TomlParseResult
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * Parser for declarative build files. Reads `build.gradle.toml` files and produce a
 * [TomlParseResult] instance.
 */
class DeclarativeFileParser(
    val issueLogger: IssueLogger? = null,
) {
    fun  parseDeclarativeFile(buildFile: Path): TomlParseResult =
        _parseDeclarativeFile(
            buildFile.pathString,
        ) {
            Toml.parse(buildFile)
        }

    fun parseDeclarativeFile(
        buildFileLocation: String,
        buildFileContent: String): TomlParseResult =
        _parseDeclarativeFile(
            buildFileLocation,
        ) {
            Toml.parse(buildFileContent)
        }


    private fun _parseDeclarativeFile(
         location: String,
         parser: () -> TomlParseResult
    ): TomlParseResult {
        val result =  parser()
        issueLogger?.run {
            result.errors().forEach { error ->
                raiseError(
                    """
                    Error parsing $location
                    ${error.position().line()}:${error.position().column()} ${error.message}
                    """.trimIndent()
                )
            }
            return result
        }
        if (result.errors().isNotEmpty()) {
            result.errors().first { error ->
                throw RuntimeException("${error.position().line()}:${error.position().column()} ${error.message}")
            }
        }

        return result
    }
}
