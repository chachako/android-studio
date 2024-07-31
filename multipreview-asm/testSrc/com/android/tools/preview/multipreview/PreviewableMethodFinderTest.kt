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

package com.android.tools.preview.multipreview

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.util.stream.Collectors
import java.util.zip.ZipInputStream

class PreviewableMethodFinderTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val finder: PreviewableMethodFinder by lazy {
        val testClassesDir = tempDir.newFolder()
        ZipInputStream(requireNotNull(
            this::class.java.classLoader.getResourceAsStream(
                "testJarsAndClasses.zip"))).use { zipInputStream ->
            generateSequence { zipInputStream.nextEntry }.forEach { entry ->
                val outputFile = File(testClassesDir, entry.name)
                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile.mkdirs()
                    Files.copy(zipInputStream, outputFile.toPath())
                }
            }
        }

        val rootDir = File(testClassesDir, "testJarsAndClasses")
        PreviewableMethodFinder(
            listOf(File(rootDir, "screenshotTestDirs/dir1")),
            listOf(File(rootDir, "screenshotTestJars/precompiledTestClasses.jar")),
            listOf(File(rootDir, "mainDirs/dir1")),
            listOf(File(rootDir, "mainJars/jar1.jar")),
            listOf(File(rootDir, "depsJars/libjar1.jar"))
        )
    }

    @Test
    fun findPreviewableFunctions() {
        val discoveredPreviews = finder.findAllPreviewableMethods()
        val uniqueMethods = discoveredPreviews.stream().map { it.method.methodFqn }.collect(Collectors.toSet())
        assertThat(discoveredPreviews.size).isEqualTo(11)
        assertThat(uniqueMethods.size).isEqualTo(8)
        assertThat(uniqueMethods).containsExactly(
            "com.example.myprecompiledtestclasses.PreviewableMethodsFromStaticLibraryKt.GreetingPreview1",
            "com.example.myscreenshottestexample.screenshottest.ExampleScreenshotTest.GreetingPreview2",
            "com.example.myscreenshottestexample.screenshottest.ExampleScreenshotTestKt.CustomMultipreviewAnnotationTest",
            "com.example.myscreenshottestexample.screenshottest.ExampleScreenshotTestKt.CyclicPreviewableAnnotationTest",
            "com.example.myscreenshottestexample.screenshottest.ExampleScreenshotTestKt.GreetingPreview1",
            "com.example.myscreenshottestexample.screenshottest.ExampleScreenshotTestKt.GreetingPreviewWithRepeatedAnnotation",
            "com.example.myscreenshottestexample.screenshottest.ExampleScreenshotTestKt.PreviewAnnotationFromLibrary",
            "com.example.myscreenshottestexample.screenshottest.ExampleScreenshotTestKt.PreviewAnnotationFromMain",
        )
    }
}
