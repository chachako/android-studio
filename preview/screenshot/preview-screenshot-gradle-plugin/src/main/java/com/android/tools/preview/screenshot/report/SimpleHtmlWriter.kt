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

import org.gradle.internal.xml.SimpleMarkupWriter
import java.io.IOException
import java.io.Writer

/**
 *
 * A streaming HTML writer.
 */
class SimpleHtmlWriter @JvmOverloads constructor(writer: Writer?, indent: String? = null) :
    SimpleMarkupWriter(writer, indent) {

    init {
        writeHtmlHeader()
    }

    @Throws(IOException::class)
    private fun writeHtmlHeader() {
        writeRaw("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">")
    }
}
