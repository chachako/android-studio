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

package com.android.build.gradle.internal.dependency

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtractAarTransformTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `test extract aar`() {
        val aarContents = sampleAarContents()

        val aarFile = tmp.newFile("foo.aar")
        createAar(aarFile, aarContents)

        val extractedAarDir = tmp.newFolder("extracted-aar")
        AarExtractor().extract(aarFile, extractedAarDir)

        val extractedAarDirContents = readDirectoryContents(extractedAarDir)
        val expectedDirContents = aarContents.toMutableMap().also {
            it["jars/classes.jar"] = it["classes.jar"]!!
            it.remove("classes.jar")
        }
        assertThat(extractedAarDirContents.size).isEqualTo(expectedDirContents.size)
        extractedAarDirContents.forEach {
            assertThat(it.value.contentEquals(expectedDirContents[it.key]!!)).isTrue()
        }
    }

    /** Regression test for b/315336689. */
    @Test
    fun `test extract aar which does not have classes jar`() {
        val aarContents = sampleAarContents()
        val aarContentsWithoutClassesJar = aarContents.toMutableMap().also {
            it.remove("classes.jar")
        }

        val aarFile = tmp.newFile("foo.aar")
        createAar(aarFile, aarContentsWithoutClassesJar)

        val extractedAarDir = tmp.newFolder("extracted-aar")
        AarExtractor().extract(aarFile, extractedAarDir)

        val extractedAarDirContents = readDirectoryContents(extractedAarDir)
        val expectedDirContents = aarContentsWithoutClassesJar.toMutableMap().also {
            it["jars/classes.jar"] = emptyJar()
        }
        assertThat(extractedAarDirContents.size).isEqualTo(expectedDirContents.size)
        extractedAarDirContents.forEach {
            assertThat(it.value.contentEquals(expectedDirContents[it.key]!!)).isTrue()
        }
    }

    private fun sampleAarContents(): Map<String, ByteArray> =
        mapOf(
            "AndroidManifest.xml" to "<manifest/>".toByteArray(),
            "res/values/value.xml" to "<resources/>".toByteArray(),
            "classes.jar" to sampleJarContents()
        )

    private fun sampleJarContents(): ByteArray =
        ByteArrayOutputStream().apply {
            JarOutputStream(this).use {
                it.writeEntry("com/example/SomeClass.class", byteArrayOf())
            }
        }.toByteArray()

    private fun emptyJar(): ByteArray =
        ByteArrayOutputStream().apply {
            JarOutputStream(this).use { }
        }.toByteArray()

    private fun createAar(aarFile: File, aarContents: Map<String, ByteArray>) {
        ZipOutputStream(FileOutputStream(aarFile).buffered()).use { zos ->
            aarContents.forEach {
                zos.writeEntry(it.key, it.value)
            }
        }
    }

    private fun ZipOutputStream.writeEntry(name: String, contents: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(contents)
        closeEntry()
    }

    private fun readDirectoryContents(directory: File): Map<String, ByteArray> {
        val contents = mutableMapOf<String, ByteArray>()
        directory.walk().forEach {
            if (it.isFile) {
                contents[it.relativeTo(directory).invariantSeparatorsPath] = it.readBytes()
            }
        }
        return contents
    }

}
