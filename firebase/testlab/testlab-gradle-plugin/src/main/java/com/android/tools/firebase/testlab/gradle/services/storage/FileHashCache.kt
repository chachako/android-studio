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

package com.android.tools.firebase.testlab.gradle.services.storage

import com.google.common.hash.Hashing
import com.google.common.io.Files.asByteSource
import java.io.File
import java.nio.file.attribute.FileTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.getLastModifiedTime

class FileHashCache(
    private val hashingFunction: (File) -> String = { file ->
        asByteSource(file).hash(Hashing.sha256()).toString()
    }
) {
    private val fileLocks: ConcurrentHashMap<String, Any> = ConcurrentHashMap()

    private val hashingCache: MutableMap<String, MutableMap<FileTime, String>> = mutableMapOf()

    fun retrieveOrGenerateHash(file: File): String {
        val filePath = file.absolutePath
        val lastModified = file.toPath().getLastModifiedTime()

        return retrieveHash(filePath, lastModified) ?:
            synchronized(fileLocks.computeIfAbsent(filePath) { Any() }) {
                computeHash(file, filePath, lastModified)
            }
    }

    private fun retrieveHash(filePath: String, lastModified: FileTime): String? =
        hashingCache[filePath]?.get(lastModified)

    private fun computeHash(file: File, filePath: String, lastModified: FileTime): String =
        hashingCache.getOrPut(filePath) { mutableMapOf() }.getOrPut(lastModified) {
            hashingFunction(file)
        }
}
