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

package com.android.build.gradle.integration.common.fixture

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.File
import java.lang.IllegalStateException
import java.nio.charset.Charset

/**
 * Parse the HTML configuration cache report file and extract all errors
 *
 * The file format is not defined or specified by Gradle, therefore this parser
 * is based on experimentation and should be modified accordingly as Gradle
 * evolves the HTML or JSON file format.
 *
 * The file format is as follow :
 * ```
 * <some html tags>
 * // begin-report-data
 * Serialized JSON Objects
 * // end-report-data
 * <some more html tags>
 * ```
 *
 */
class ConfigurationCacheReportParser(
    val reportFile: File
) {
    /**
     * Defines the different ErrorTypes found in the configuration cache report.
     *
     * The list was obtained by looking at examples and could be incomplete.
     */
    enum class ErrorType(val text: String) {
        DirectoryContent("directory content"),
        EnvironmentVariable("environment variable"),
        FileSystemEntry("file system entry"),
        SystemProperty("system property"),
        ValueFromCustomSource("value from custom source"),
        File("file"),
        Unknown("Unknown");

        companion object {
            fun makeErrorType(errorText: String): ErrorType {
                ErrorType.values().forEach {
                    if (it.text == errorText) return it
                }
                return Unknown
            }
        }
    }

    /**
     * Internal representation of an error extracted from the Gradle's configuration cache report.
     *
     * @param element raw json data extracted from the report containing the error details.
     * @param type error type
     * @param name file name or path accessed
     */
    data class Error(
        val element: String,
        val type: ErrorType,
        val name: String,
    ) {
        companion object {
            fun file(
                location: String,
                name: String,
            ) = Error(location, ErrorType.File, name)
            fun fileSystemEntry(
                location: String,
                name: String,
            ) = Error(location, ErrorType.FileSystemEntry, name)

            fun environmentVariable(
                location: String,
                name: String,
            ) = Error(location, ErrorType.EnvironmentVariable, name)

            fun systemProperty(
                location: String,
                name: String,
            ) = Error(location, ErrorType.SystemProperty, name)

            fun valueFromCustomSource(
                location: String,
                name: String,
            ) = Error(location, ErrorType.ValueFromCustomSource, name)
        }
        class Builder() {
            var element: String? = null
            var type: ErrorType? = null
            var name: String? = null

            fun build(): Error =
                Error(
                    element ?: throw IllegalStateException("Element not provided"),
                    type ?: throw IllegalStateException("ErrorType not provided"),
                    name ?: throw IllegalStateException("name/description not provided")
                )
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Error

            if (type != other.type) return false
            return name == other.name
        }

        override fun hashCode(): Int {
            var result = type.hashCode()
            result = 31 * result + name.hashCode()
            return result
        }

        override fun toString(): String {
            return "Error(type=$type, name='$name', element='$element')"
        }
    }

    /**
     * Extract all relevant and interesting errors (I/O File access) from
     * the Gradle's configuration cache report.
     *
     * The format of the file is not documented and most likely subject to
     * change over time.
     */
    fun getErrorsAndWarnings(): List<Error> {
        extractReportData(
            reportFile.readText(
                charset = Charset.defaultCharset()
            )
        ).run {
            if (isEmpty()) return emptyList()
            val reportData = JsonParser.parseString(this)

            val errorBuilder = Error.Builder()
            val listBuilder = mutableListOf<Error>()
            reportData.asJsonObject.get("diagnostics")
                .asJsonArray
                .map(JsonElement::getAsJsonObject)
                .forEach { element ->
                    element.get("trace")
                        .asJsonArray
                        .map(JsonElement::getAsJsonObject)
                        .forEach { trace ->
                            errorBuilder.element = element.toString()
                        }

                    if (element.has("input")) {
                        element.get("input")
                            .asJsonArray
                            .map(JsonElement::getAsJsonObject)
                            .forEach { input ->
                                if (!input.has("name")) {
                                    println("Unknown diagnostic record : $element")
                                    return@forEach
                                }
                                if (input.has("text")) {
                                    errorBuilder.type =
                                        ErrorType.makeErrorType(
                                            input.get("text").asString.trim()
                                        )
                                }
                                var name = input.get("name").asString
                                // there are some files that are accessed only in Bazel that seems to be
                                // dependent on the NDK version, filed b/290404113 to handle this separately
                                if (name.contains(
                                        "runfiles/__main__/prebuilts/studio/sdk/linux/ndk/")
                                    // ndk version in the pat like `25.1.8937392`
                                    || name.contains(Regex("^[\\d\\.]*$"))
                                    || name.contains(Regex("^android-\\d*$"))
                                    || name.contains("build/intermediates/cxx")
                                    || name.contains("build/intermediates/prefab_package_header_only")
                                ) {
                                    return@forEach
                                } //                                        }
                                errorBuilder.name = name.substring(name.lastIndexOf("/") + 1)
                            }
                        // We only care far about I/O failures. Access to system properties
                        // and environment variables are not relevant at this point.
                        if (errorBuilder.type == ErrorType.FileSystemEntry
                            || errorBuilder.type == ErrorType.File) {

                            val error = errorBuilder.build()
                            listBuilder.add(error)

                        }
                } else {
                    println("Unknown diagnostic record : $element")
                }
            }
            return listBuilder.toList()
        }
    }

    /**
     * Extract the json data from the html page.
     *
     * The html page file format is not published by Gradle and is subject to change.
     * As this time the format is as follow
     * ```
     * <html>....
     * // begin-report-data
     * Serialized JSON
     * // end-report-data
     * ```
     */
    private fun extractReportData(htmlPage: String): String =
        StringBuilder().also {
            var inReportData = false
            htmlPage.reader().readLines().forEach { line ->
                if (line.equals("// end-report-data")) {
                    inReportData = false
                }
                if (inReportData) it.append(line)
                if (line.equals("// begin-report-data")) {
                    inReportData = true
                }
            }
        }.toString()
}
