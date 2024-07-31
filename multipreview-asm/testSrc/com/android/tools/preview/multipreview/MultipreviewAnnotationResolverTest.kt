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
import java.util.zip.ZipInputStream

class MultipreviewAnnotationResolverTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val resolver: MultipreviewAnnotationResolver by lazy {
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
        MultipreviewAnnotationResolver(
            listOf(File(rootDir, "screenshotTestDirs/dir1")),
            listOf(File(rootDir, "screenshotTestJars/precompiledTestClasses.jar")),
            listOf(File(rootDir, "mainDirs/dir1")),
            listOf(File(rootDir, "mainJars/jar1.jar")),
            listOf(File(rootDir, "depsJars/libjar1.jar"))
        )
    }

    @Test
    fun basePreviewAnnotationsShouldBeResolvedToFalse() {
        assertThat(resolver.isMultipreviewAnnotation(
            "Landroidx/compose/ui/tooling/preview/Preview;") is MultipreviewAnnotationResolver.AnnotationDetails.MultiPreviewAnnotation).isFalse()
        assertThat(resolver.isMultipreviewAnnotation(
            "Landroidx/compose/ui/tooling/preview/Preview\$Container;") is MultipreviewAnnotationResolver.AnnotationDetails.MultiPreviewAnnotation).isFalse()
    }

    @Test
    fun nonMultipreviewAnnotationsShouldBeResolvedToFalse() {
        assertThat(resolver.isMultipreviewAnnotation("Lorg/junit/Rule;") is MultipreviewAnnotationResolver.AnnotationDetails.MultiPreviewAnnotation).isFalse()
        assertThat(resolver.isMultipreviewAnnotation("Lorg/junit/Test;") is MultipreviewAnnotationResolver.AnnotationDetails.MultiPreviewAnnotation).isFalse()
    }

    @Test
    fun invalidInputShouldBeResolvedToFalse() {
        assertThat(resolver.isMultipreviewAnnotation("")is MultipreviewAnnotationResolver.AnnotationDetails.MultiPreviewAnnotation).isFalse()
        assertThat(resolver.isMultipreviewAnnotation("invalid input") is MultipreviewAnnotationResolver.AnnotationDetails.MultiPreviewAnnotation).isFalse()
    }

    @Test
    fun multipreviewIsDefinedInTestSource() {
        val annotation = resolver.isMultipreviewAnnotation(
            "Lcom/example/myscreenshottestexample/screenshottest/MyCustomMultipreviewAnnotation;")
        assertThat((annotation as MultipreviewAnnotationResolver.AnnotationDetails.MultiPreviewAnnotation).previewList.size)
            .isEqualTo(2)
    }

    @Test
    fun multipreviewIsDefinedInMainSource() {
        val annotation = resolver.isMultipreviewAnnotation(
            "Lcom/example/myscreenshottestexample/MyCustomMultipreviewAnnotationInMain;")
        assertThat((annotation as MultipreviewAnnotationResolver.AnnotationDetails.MultiPreviewAnnotation).previewList.size)
            .isEqualTo(1)
    }

    @Test
    fun multipreviewIsDefinedInDependency() {
        val annotation = resolver.isMultipreviewAnnotation(
            "Lcom/example/mylibrary/MyCustomPreviewAnnotationInLibrary;")
        assertThat((annotation as MultipreviewAnnotationResolver.AnnotationDetails.MultiPreviewAnnotation).previewList.size)
            .isEqualTo(1)
    }

    @Test
    fun cyclicPreviewableAnnotation() {
        val annotation = resolver.isMultipreviewAnnotation(
            "Lcom/example/myscreenshottestexample/screenshottest/CyclicPreviewableAnnotation;")
        assertThat((annotation as MultipreviewAnnotationResolver.AnnotationDetails.MultiPreviewAnnotation).previewList.size)
            .isEqualTo(2)
    }

    @Test
    fun cyclicNonPreviewableAnnotation() {
        assertThat(resolver.isMultipreviewAnnotation(
            "Lcom/example/myscreenshottestexample/screenshottest/CyclicNonPreviewableAnnotation;")is MultipreviewAnnotationResolver.AnnotationDetails.MultiPreviewAnnotation)
            .isFalse()
    }
}
