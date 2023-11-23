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
@file:JvmName("UpdateGoogleMavenRepositoryCache")

package com.android.ide.common.repository

import com.android.ide.common.gradle.Version
import com.android.testutils.TestUtils
import com.android.utils.XmlUtils
import com.google.common.io.Files
import com.google.common.io.Resources
import java.io.File
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files.exists
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections
import java.util.TreeMap
import kotlin.io.path.isDirectory
import kotlin.io.path.readText

/**
 * Updates the checked in index files of the Google Maven repository.
 *
 * This class can be run using IJ run configurations or from bazel:
 * `bazel run //tools/base/sdk-common:update_google_maven_repository_cache`
 *
 * In both cases, the path to the repo root directory (the one with `.repo` in it) can optionally be
 * passed as the only argument.
 *
 * Running this may cause new files to be added to the sdk-common jar, which means `JarContentsTest`
 * gradle integration test will need to be updated. See that class for instructions on how to
 * update it.
 */
fun main(args: Array<String>) {

    val workspace: Path = args.singleOrNull()?.let { Paths.get(it) } ?: try {
        findWorkspaceInBazelRun() ?: TestUtils.getWorkspaceRoot()
    } catch (e: Exception) {
        throw IllegalStateException(
            "Unexpectedly failed to locate repo. \n" +
                    "You can optionally specify the repo root as an argument, but this shouldn't\n" +
                    "be necessary in the usual cases of running from bazel or the tools/adt/idea " +
                    "Intellij project.", e)
    }

    if (!workspace.resolve(".repo").isDirectory()) {
        error("Invalid directory: should be pointing to the root of a tools checkout directory.")
    }
    val dir: File = workspace.resolve("tools/base/sdk-common/src/main/resources/versions-offline/").toFile()

    if (!dir.exists()) {
        error("${dir.absolutePath} does not exist.")
    }
    println("Updating versions-offline in $dir")

    // Delete older copies to ensure we clean up obsolete packages
    dir.deleteRecursively()
    dir.mkdir()
    val master = readUrlDataAsString("${GMAVEN_BASE_URL}master-index.xml")
    val masterFile = File(dir, "master-index.xml")
    Files.asCharSink(masterFile, Charsets.UTF_8).write(master)
    println("Wrote $masterFile")
    val masterDoc = XmlUtils.parseDocumentSilently(master, false)!!
    var current = XmlUtils.getFirstSubTag(masterDoc.documentElement)
    while (current != null) {
        val group = current.tagName
        val relative = group.replace('.', '/')
        val groupIndex = readUrlDataAsString(
                "${GMAVEN_BASE_URL}$relative/group-index.xml")

        // Keep all but the last stable and unstable version
        val sb = StringBuilder()
        groupIndex.lines().forEach { line ->
            var start: Int = line.indexOf("versions=\"")
            var done = false
            if (start != -1) {
                start += "versions=\"".length
                val end: Int = line.indexOf("\"", start)
                val sub = line.substring(start, end)
                var max: Version? = null
                val maxStablePerMajor = TreeMap<Int, Version>(Collections.reverseOrder())
                sub.splitToSequence(",").forEach {
                    val v = Version.parse(it)
                    if (max == null || v > max!!) {
                        max = v
                    }

                    if (!v.isPreview) {
                        v.major?.let { major ->
                            maxStablePerMajor.compute(major) { _, old ->
                                if (old == null) v else maxOf(old, v)
                            }
                        } ?: error("Attempted to add a Version with no major: $v")
                    }
                }
                if (max != null) {
                    sb.append(line.substring(0, start))
                    maxStablePerMajor.values
                            .take(2)
                            .plus(max!!)
                            .toSortedSet()
                            .joinTo(sb, separator = ",")
                    sb.append(line.substring(end))
                    sb.append("\n")
                    done = true
                }
            }

            if (!done) {
                sb.append(line).append("\n")
            }
        }


        val file = File(
                dir,
                relative.replace('/', File.separatorChar) +
                        File.separatorChar + "group-index.xml")
        file.parentFile.mkdirs()
        Files.asCharSink(file, Charsets.UTF_8).write(sb)
        println("Wrote $file")

        current = XmlUtils.getNextTag(current)
    }

    println("Updated indices. NOTE: You may need to update the JarContentsTest gradle " +
            "integration test if the set of packaged xml files has changed. See " +
            "JarContentsTest for instructions on how to update the test.")
}

/**
 * Reads the data from the given URL and returns it as a UTF-8 encoded String.
 */
private fun readUrlDataAsString(url: String): String =
        Resources.asCharSource(URL(url), Charsets.UTF_8).read()


private const val DO_NOT_BUILD_HERE = "DO_NOT_BUILD_HERE"

// This logic is the same as that in WorkspaceUtils.findWorkspace() in tools/base/bazel/
// This can't easily be reused here as it's not part of the idea project.
private fun findWorkspaceInBazelRun(): Path? {
    var currDir: Path? = Paths.get("").toAbsolutePath()
    while (currDir != null) {
        val doNotBuildHere: Path =
            currDir.resolve(DO_NOT_BUILD_HERE)
        if (exists(doNotBuildHere)) {
            return Paths.get(doNotBuildHere.readText(StandardCharsets.ISO_8859_1))
        }
        currDir = currDir.parent
    }
    return null
}
