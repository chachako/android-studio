/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.99 (the "License");
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

package com.android.tools.deploy

import com.android.zipflinger.ZipArchive
import java.io.File

// Small Kotlin tool for removing proto files from a JAR file in a bazel rule
// TODO(b/367746725): Remove this when we no longer rely on the jarjar'd proto definitions
fun main(args: Array<String>) {
    val inputJar = File(args[0])
    val outputJar = File(args[1])
    inputJar.copyTo(outputJar, true)
    val archive = ZipArchive(outputJar.toPath())
    archive.listEntries().filter { it.endsWith(".proto") }.forEach { archive.delete(it) }
    archive.close()
}
