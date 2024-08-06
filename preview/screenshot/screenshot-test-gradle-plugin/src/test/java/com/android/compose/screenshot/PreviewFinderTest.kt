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

package com.android.compose.screenshot

import com.google.common.truth.Truth
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import com.android.testutils.TestInputsGenerator
import java.io.File
import java.io.FileOutputStream

class PreviewFinderTest {
    @get:Rule
    val tempDirRule = TemporaryFolder()

    @Test
    fun testFindPreviewsAndSerializeWithEmptyClassPath() {
        val outputFile = tempDirRule.newFile("outputFile")
        findPreviewsAndSerialize(listOf(), outputFile.toPath(), listOf(tempDirRule.root), listOf(File("empty/jar.jar")))

        Truth.assertThat(outputFile.readText()).isEqualTo("""
            {
              "screenshots": []
            }
        """.trimIndent())
    }

    // TODO(b/315048068): Add a unit test that finds previews on the classpath
    @Test
    fun testDiscoverPreviewsWithEmptyClassPath() {
        val outputFile = tempDirRule.newFile("outputFile")
        discoverPreviews(listOf(tempDirRule.root), listOf(File("empty/jar.jar")), listOf(), listOf(), listOf(), outputFile.toPath())

        Truth.assertThat(outputFile.readText()).isEqualTo("""
            {
              "screenshots": []
            }
        """.trimIndent())
    }

    @Test
    fun testConfigureInput() {
        val classpath = listOf("path/to/classes.jar","path/to/R.jar")
        val previewsFile = tempDirRule.newFile("previews_discovered.json")
        val cliToolArgumentsFile = tempDirRule.newFile("cli_tools_arguments.json")
        previewsFile.writeText("""
            {
              "screenshots": [
                {
                  "methodFQN": "com.example.agptest.ExampleInstrumentedTest.previewThere",
                  "methodParams": [],
                  "previewParams": {
                    "showBackground": "true"
                  },
                  "previewId": "com.example.agptest.ExampleInstrumentedTest.previewThere_3d8b4969_da39a3ee"
                }
              ]
            }
        """.trimIndent())
        configureInput(classpath,
            classpath,
            "fontsPath",
            "layoutlibpath",
            "outputFolder",
            "metaDataFolder",
            "namespace",
            "resourceApkPath",
            cliToolArgumentsFile,
            previewsFile,
            "resultsFilePath"
            )
        assertEquals("""
            {
              "fontsPath": "fontsPath",
              "layoutlibPath": "layoutlibpath",
              "outputFolder": "outputFolder",
              "metaDataFolder": "metaDataFolder",
              "classPath": [
                "path/to/classes.jar",
                "path/to/R.jar"
              ],
              "projectClassPath": [
                "path/to/classes.jar",
                "path/to/R.jar"
              ],
              "namespace": "namespace",
              "resourceApkPath": "resourceApkPath",
              "screenshots": [
                {
                  "methodFQN": "com.example.agptest.ExampleInstrumentedTest.previewThere",
                  "methodParams": [],
                  "previewParams": {
                    "showBackground": "true"
                  },
                  "previewId": "com.example.agptest.ExampleInstrumentedTest.previewThere_3d8b4969_da39a3ee"
                }
              ],
              "resultsFilePath": "resultsFilePath"
            }
        """.trimIndent(),
                cliToolArgumentsFile.readText())
    }

    @Test
    fun testClassExistsInDirs() {
        val subFolder = tempDirRule.newFolder("com", "example")
        val targetFile = File(subFolder, "MyClass.class")
        val dummyContent = byteArrayOf()
        FileOutputStream(targetFile.absolutePath).use {
            it.write(dummyContent)
        }
        val className = "com.example.MyClass"
        val dirs = listOf(tempDirRule.root)
        val jars = emptyList<File>()

        val result = classExistsIn(className, dirs, jars)
        assertEquals(true, result)
    }

    @Test
    fun testClassExistsInJars() {
        val jarFile = tempDirRule.newFile("test.jar")
        val className = "com.example.MyClass"
        val classPath = "com/example/MyClass"
        TestInputsGenerator.jarWithEmptyClasses(jarFile.toPath(), listOf(classPath))

        val dirs = emptyList<File>()
        val jars = listOf(jarFile)

        val result = classExistsIn(className, dirs, jars)
        assertEquals(true, result)
    }

    @Test
    fun testClassExistsInNotFound() {
        val className = "com.example.MyClass"
        val dirs = listOf(tempDirRule.root)

        val jarFile = tempDirRule.newFile("test.jar")
        TestInputsGenerator.jarWithEmptyClasses(jarFile.toPath(), listOf())
        val jars = listOf(jarFile)

        val result = classExistsIn(className, dirs, jars)
        assertEquals(false, result)
    }

    @Test
    fun testSortListOfSortedMaps() {
        val inputList = listOf(
            sortedMapOf(Pair("provider", "b")),
            sortedMapOf(),
            sortedMapOf(Pair("provider", "a")),
            sortedMapOf(Pair("a", "b")))

        val sortedList = sortListOfSortedMaps(inputList)

        val expectedList = listOf(
            sortedMapOf(),
            sortedMapOf(Pair("a", "b")),
            sortedMapOf(Pair("provider", "a")),
            sortedMapOf(Pair("provider", "b"))
        )
        assertEquals(expectedList, sortedList)
    }

    @Test
    fun testSortListOfSortedMapsWithMultipleKeys() {
        val inputList = listOf(
            sortedMapOf(Pair("c", "d"), Pair("provider", "a")),
            sortedMapOf(Pair("z", "z"), Pair("provider", "b")),
            sortedMapOf(Pair("provider", "b")),
            sortedMapOf(Pair("provider", "a"), Pair("z", "z")),
            sortedMapOf(Pair("provider", "a"), Pair("z", "y")),
            sortedMapOf(Pair("a", "b"), Pair("b", "z")),
            sortedMapOf(Pair("a", "b"), Pair("b", "a")),
            sortedMapOf(Pair("a", "b")),
            sortedMapOf())

        val sortedList = sortListOfSortedMaps(inputList)

        val expectedList = listOf(
            sortedMapOf(),
            sortedMapOf(Pair("a", "b")),
            sortedMapOf(Pair("a", "b"), Pair("b", "a")),
            sortedMapOf(Pair("a", "b"), Pair("b", "z")),
            sortedMapOf(Pair("c", "d"), Pair("provider", "a")),
            sortedMapOf(Pair("provider", "a"), Pair("z", "y")),
            sortedMapOf(Pair("provider", "a"), Pair("z", "z")),
            sortedMapOf(Pair("provider", "b")),
            sortedMapOf(Pair("provider", "b"), Pair("z", "z")))
        assertEquals(expectedList, sortedList)
    }
}
