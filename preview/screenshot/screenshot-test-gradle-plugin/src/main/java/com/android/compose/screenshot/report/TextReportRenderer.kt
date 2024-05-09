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

package com.android.compose.screenshot.report

import org.gradle.api.UncheckedIOException
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.Writer

/**
 * Custom TextReportRenderer based on Gradle's TextReportRenderer
 */
abstract class TextReportRenderer<T> {

    /**
     * Renders the report for the given model to a writer.
     */
    @Throws(Exception::class)
    protected abstract fun writeTo(model: T, out: Writer)

    /**
     * Renders the report for the given model to a file.
     */
    open fun writeTo(model: T, file: File) {
        try {
            val parentFile: File = file.getParentFile()
            if (!parentFile.mkdirs() && !parentFile.isDirectory()) {
                throw IOException(String.format("Unable to create directory '%s'", parentFile))
            } else {
                val writer =
                    BufferedWriter(OutputStreamWriter(FileOutputStream(file), "utf-8"))
                writer.use {
                    writeTo(model, it)
                }
            }
        } catch (var8: java.lang.Exception) {
            throw UncheckedIOException(
                String.format(
                    "Could not write to file '%s'.",
                    file
                ), var8
            )
        }
    }
}
