/*
 * Copyright (C) 2019 The Android Open Source Project
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
@file:JvmName("TestAarGenerator")

package com.android.testutils

import com.android.SdkConstants
import com.android.testutils.TestInputsGenerator.jarWithEmptyEntries

/**
 * Returns the bytes of an AAR with the given content.
 *
 * Designed for use with [TestInputsGenerator] to generate jars to put in the AAR and with
 * [MavenRepoGenerator] to put the AAR in a test maven repo.
 *
 * @param packageName The package name to put in the AAR manifest.
 * @param mainJar the contents of the main classes.jar. Defaults to an empty jar.
 * @param secondaryJars a map of name (without extension) to contents of jars
 *                      to be stored as `libs/{name}.jar`. Defaults to empty.
 */
@JvmOverloads
fun generateAarWithContent(
    packageName: String,
    mainJar: ByteArray = jarWithEmptyEntries(listOf()),
    secondaryJars: Map<String, ByteArray> = mapOf(),
    resources: Map<String, ByteArray> = mapOf(),
    apiJar: ByteArray? = null,
    lintJar: ByteArray? = null,
    manifest: String = """<manifest package="$packageName"></manifest>""",
    extraFiles: List<Pair<String, ByteArray>> = emptyList()
): ByteArray {
    val entries = mutableMapOf<String, ByteArray>()
    entries[SdkConstants.FN_ANDROID_MANIFEST_XML] = manifest.toByteArray()
    entries["classes.jar"] = mainJar
    secondaryJars.forEach {
        entries["libs/${it.key}.jar"] = it.value
    }
    resources.forEach {
        entries["res/${it.key}"] = it.value
    }
    apiJar?.let { entries["api.jar"] = it }
    lintJar?.let { entries["lint.jar"] = it }
    extraFiles.forEach { (path, byteArray) ->
        entries[path] = byteArray
    }
    return ZipContents(entries).toByteArray()
}
