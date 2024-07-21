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

package com.android.testutils

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Represents the contents of a zip file, with utility methods to make reading/writing zip files
 * easier.
 *
 * Note:
 *   - This class should be used in tests only as it is designed for convenience of use rather than
 *   performance (e.g., it loads all entries in memory at once). For production code, to improve
 *   performance/memory usage, use other utilities such as zipflinger's ZipArchive.
 *   - This class ignores duplicate zip entries.
 */
class ZipContents(

    /**
     * Maps entries' names (Unix-style relative paths) to their contents.
     *
     * Directory entries have names ending with "/" and empty byte array contents, and are often
     * optional (e.g., a zip file containing a `foo/bar.txt` file entry is not required to also
     * contain a `foo` directory entry).
     */
    val entries: Map<String, ByteArray>
) {

    /** Combines this [ZipContents] and [other]'s [ZipContents]. */
    operator fun plus(other: ZipContents) = ZipContents(entries + other.entries)

    /** Writes the contents to a [ByteArray] which can then be directly written to a zip file. */
    fun toByteArray(): ByteArray {
        return ByteArrayOutputStream().run {
            write(this)
            toByteArray()
        }
    }

    /** Writes the contents to the given [zipFile]. */
    fun writeToFile(zipFile: File) {
        write(zipFile.outputStream().buffered())
    }

    private fun write(outputStream: OutputStream) {
        ZipOutputStream(outputStream).use { zipOutputStream ->
            zipOutputStream.apply {
                entries.forEach { (name, contents) ->
                    putNextEntry(ZipEntry(name))
                    write(contents)
                    closeEntry()
                }
            }
        }
    }

    companion object {

        /**
         * Reads [ZipContents] from the given [byteArray], with an optional [filter] to read only
         * the [ZipEntry]s of interest.
         */
        fun fromByteArray(byteArray: ByteArray, filter: ((ZipEntry) -> Boolean)? = null): ZipContents {
            return read(byteArray.inputStream(), filter)
        }

        /**
         * Reads [ZipContents] from the given [zipFile], with an optional [filter] to read only the
         * [ZipEntry]s of interest.
         */
        fun readFromFile(zipFile: File, filter: ((ZipEntry) -> Boolean)? = null): ZipContents {
            return read(zipFile.inputStream().buffered(), filter)
        }

        private fun read(inputStream: InputStream, filter: ((ZipEntry) -> Boolean)? = null): ZipContents {
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(inputStream).use { zipInputStream ->
                while (true) {
                    val zipEntry = zipInputStream.nextEntry ?: break
                    if (filter == null || filter(zipEntry)) {
                        // If there are duplicate entries, keep the first one
                        entries.getOrPut(zipEntry.name) { zipInputStream.readBytes() }
                    }
                }
            }
            return ZipContents(entries)
        }
    }
}
