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
package com.android.screenshot.cli.util

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.io.PosixFilePermissionsUtil
import java.io.File
import java.io.FilenameFilter
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.util.Enumeration
import java.util.function.BiConsumer
import java.util.function.Predicate
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

abstract class Decompressor  //<editor-fold desc="Internal interface">
protected constructor() {

    class Zip(file: Path) : Decompressor() {

        //<editor-fold desc="Implementation">
        private val mySource: Path = file
        private var myZip: ZipFile? = null
        private var myEntries: Enumeration<out ZipEntry>? = null
        private var myEntry: ZipEntry? = null

        @Throws(IOException::class)
        override fun openStream() {
            myZip = ZipFile(mySource.toFile())
            myEntries = myZip!!.entries()
        }

        override fun nextEntry(): Entry? {
            myEntry = if (myEntries!!.hasMoreElements()) myEntries!!.nextElement() else null
            return if (myEntry == null) null else Entry(myEntry!!.name, myEntry!!.isDirectory)
        }

        @Throws(IOException::class)
        override fun openEntryStream(entry: Entry?): InputStream {
            return myZip!!.getInputStream(myEntry)
        }

        @Throws(IOException::class)
        override fun closeEntryStream(stream: InputStream?) {
            stream?.close()
        }

        @Throws(IOException::class)
        override fun closeStream() {
            myZip!!.close()
        }
    }

    class Entry internal constructor(name: String, type: Type, mode: Int, linkTarget: String?) {
        enum class Type {
            FILE,
            DIR,
            SYMLINK
        }

        /** An entry name (separators converted to '/' and trimmed); handle with care  */
        val name: String
        val type: Type

        /** Depending on the source, could be POSIX permissions, DOS attributes, or just `0`  */
        val mode: Int
        val linkTarget: String?

        internal constructor(name: String, isDirectory: Boolean) : this(
            name,
            if (isDirectory) Type.DIR else Type.FILE,
            0,
            null
        )

        init {
            var name = name
            name = name.trim { it <= ' ' }.replace('\\', '/')
            var s = 0
            var e = name.length - 1
            while (s < e && name[s] == '/') s++
            while (e >= s && name[e] == '/') e--
            this.name = name.substring(s, e + 1)
            this.type = type
            this.mode = mode
            this.linkTarget = linkTarget
        }

        companion object {

            const val DOS_READ_ONLY = 1
            const val DOS_HIDDEN = 2
        }
    }

    private var myFilter: Predicate<in Entry?>? = null
    private var myPathPrefix: List<String>? = null
    private var myOverwrite = true
    private var myAllowEscapingSymlinks = true
    private var myPostProcessor: BiConsumer<in Entry?, in Path>? = null
    fun filter(filter: Predicate<in String?>?): Decompressor {
        myFilter = if (filter != null) Predicate { e: Entry? ->
            filter.test(
                if (e!!.type == Entry.Type.DIR) e.name + '/' else e.name
            )
        } else null
        return this
    }

    fun overwrite(overwrite: Boolean): Decompressor {
        myOverwrite = overwrite
        return this
    }

    /**
     * Extracts only items whose path starts with the normalized prefix of `prefix + '/'`.
     * Paths are normalized before comparison.
     * The prefix test is applied after [.filter] predicate is tested.
     * Some entries may clash, so use [.overwrite] to control it.
     * Some items with a path that does not start from the prefix could be ignored.
     *
     * @param prefix a prefix to remove from every archive entry path
     * @return self
     */
    @Throws(IOException::class)
    fun removePrefixPath(prefix: String?): Decompressor {
        myPathPrefix = if (prefix != null) normalizePathAndSplit(prefix) else null
        return this
    }

    @Throws(IOException::class)
    fun extract(outputDir: Path) {
        openStream()
        try {
            var entry: Entry?
            while (nextEntry().also { entry = it } != null) {
                if (myFilter != null && !myFilter!!.test(entry)) {
                    continue
                }
                if (myPathPrefix != null) {
                    entry = mapPathPrefix(entry, myPathPrefix!!)
                    if (entry == null) continue
                }
                val outputFile = entryFile(outputDir, entry!!.name)
                when (entry!!.type) {
                    Entry.Type.DIR -> Files.createDirectories(outputFile)
                    Entry.Type.FILE -> if (myOverwrite || !Files.exists(outputFile)) {
                        val inputStream = openEntryStream(entry)
                        try {
                            Files.createDirectories(outputFile.parent)
                            Files.newOutputStream(outputFile).use { outputStream ->
                                StreamUtil.copy(
                                    inputStream,
                                    outputStream
                                )
                            }
                            if (entry!!.mode != 0) {
                                setAttributes(entry!!.mode, outputFile)
                            }
                        } finally {
                            closeEntryStream(inputStream)
                        }
                    }

                    Entry.Type.SYMLINK -> {
                        if (entry!!.linkTarget == null || entry!!.linkTarget!!.isEmpty()) {
                            throw IOException("Invalid symlink entry: " + entry!!.name + " (empty target)")
                        }
                        if (!myAllowEscapingSymlinks) {
                            verifySymlinkTarget(
                                entry!!.name,
                                entry!!.linkTarget,
                                outputDir,
                                outputFile
                            )
                        }
                        if (myOverwrite || !Files.exists(outputFile, LinkOption.NOFOLLOW_LINKS)) {
                            try {
                                val outputTarget = Paths.get(
                                    entry!!.linkTarget
                                )
                                Files.createDirectories(outputFile.parent)
                                Files.deleteIfExists(outputFile)
                                Files.createSymbolicLink(outputFile, outputTarget)
                            } catch (e: InvalidPathException) {
                                throw IOException(
                                    "Invalid symlink entry: " + entry!!.name + " -> " + entry!!.linkTarget,
                                    e
                                )
                            }
                        }
                    }
                }
                if (myPostProcessor != null) {
                    myPostProcessor!!.accept(entry, outputFile)
                }
            }
        } finally {
            closeStream()
        }
    }

    @Throws(IOException::class)
    protected abstract fun openStream()
    @Throws(IOException::class)
    protected abstract fun nextEntry(): Entry?
    @Throws(IOException::class)
    protected abstract fun openEntryStream(entry: Entry?): InputStream
    @Throws(IOException::class)
    protected abstract fun closeEntryStream(stream: InputStream?)
    @Throws(IOException::class)
    protected abstract fun closeStream()

    companion object {

        @Throws(IOException::class)
        private fun verifySymlinkTarget(
            entryName: String,
            linkTarget: String?,
            outputDir: Path,
            outputFile: Path
        ) {
            try {
                val outputTarget = Paths.get(linkTarget)
                if (outputTarget.isAbsolute) {
                    throw IOException("Invalid symlink (absolute path): $entryName -> $linkTarget")
                }
                val linkTargetNormalized = outputFile.parent.resolve(outputTarget).normalize()
                if (!linkTargetNormalized.startsWith(outputDir.normalize())) {
                    throw IOException("Invalid symlink (points outside of output directory): $entryName -> $linkTarget")
                }
            } catch (e: InvalidPathException) {
                throw IOException(
                    "Failed to verify symlink entry scope: $entryName -> $linkTarget",
                    e
                )
            }
        }

        @Throws(IOException::class)
        private fun mapPathPrefix(e: Entry?, prefix: List<String>): Entry? {
            val ourPathSplit = normalizePathAndSplit(
                e!!.name
            )
            if (prefix.size >= ourPathSplit.size || ourPathSplit.subList(
                    0,
                    prefix.size
                ) != prefix
            ) {
                return null
            }
            val newName =
                java.lang.String.join("/", ourPathSplit.subList(prefix.size, ourPathSplit.size))
            return Entry(newName, e.type, e.mode, e.linkTarget)
        }

        @Throws(IOException::class)
        private fun normalizePathAndSplit(path: String): List<String> {
            ensureValidPath(path)
            val canonicalPath = FileUtilRt.toCanonicalPath(path, '/', true)
            return FileUtilRt.splitPath(StringUtil.trimLeading(canonicalPath, '/'), '/')
        }

        @Throws(IOException::class)
        private fun setAttributes(mode: Int, outputFile: Path) {
            if (SystemInfo.isWindows) {
                val attrs = Files.getFileAttributeView(
                    outputFile,
                    DosFileAttributeView::class.java
                )
                if (attrs != null) {
                    if (mode and Entry.DOS_READ_ONLY != 0) attrs.setReadOnly(true)
                    if (mode and Entry.DOS_HIDDEN != 0) attrs.setHidden(true)
                }
            } else {
                val attrs = Files.getFileAttributeView(
                    outputFile,
                    PosixFileAttributeView::class.java
                )
                attrs?.setPermissions(PosixFilePermissionsUtil.fromUnixMode(mode))
            }
        }

        //</editor-fold>
        @Throws(IOException::class)
        private fun ensureValidPath(entryName: String) {
            if (entryName.contains("..") && ArrayUtil.contains(
                    "..",
                    *entryName.split("[/\\\\]".toRegex())
                        .dropLastWhile { it.isEmpty() }
                        .toTypedArray())
            ) {
                throw IOException("Invalid entry name: $entryName")
            }
        }

        @Throws(IOException::class)
        fun entryFile(outputDir: Path, entryName: String): Path {
            ensureValidPath(entryName)
            return outputDir.resolve(StringUtil.trimLeading(entryName, '/'))
        }
    }

    class FileFilterAdapter private constructor(
        outputDir: Path,
        private val myFilter: FilenameFilter
    ) :
        Predicate<String?> {

        private val myOutputDir: File

        init {
            myOutputDir = outputDir.toFile()
        }

        override fun test(entryName: String?): Boolean {
            val outputFile = File(myOutputDir, entryName)
            return myFilter.accept(outputFile.parentFile, outputFile.name)
        }

        companion object {

            fun wrap(outputDir: Path, filter: FilenameFilter?): FileFilterAdapter? {
                return filter?.let { FileFilterAdapter(outputDir, it) }
            }
        }
    }

}


