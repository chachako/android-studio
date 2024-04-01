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
package com.android.adblib.tools

// Replace everything not in A-Z, a-z, '_', '-', or '.' to '_'
private val fileNameCleaner = "[^A-Za-z0-9\\-_\\.-]".toRegex()

// If the install strategy involves invoking a shell command, the apk name should be escaped. We use
// an allow list because users are creating exotic apk name using' ' character or
// even '(', ')'.
internal fun sanitizeApkName(filename : String) : String =
    fileNameCleaner.replace(filename, "_")

