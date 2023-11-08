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

package com.android.tools.preview.screenshot.report

import org.gradle.internal.IoActions
import java.io.File
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
        IoActions.writeTextFile(file, "utf-8", object : ErroringAction<Writer>() {
            @Throws(Exception::class)
            override fun doExecute(writer: Writer) {
                writeTo(model, writer)
            }
        })
    }
}
