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

package com.android.build.gradle.internal.api

import com.android.build.gradle.internal.api.artifact.SourceArtifactType
import com.android.build.gradle.internal.fixtures.ProjectFactory
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DefaultAndroidSourceDirectorySetTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()
    private lateinit var sourceDirectorySet: DefaultAndroidSourceDirectorySet
    private lateinit var project: Project

    @Before
    fun setUp() {
        project = ProjectFactory.project
        sourceDirectorySet = DefaultAndroidSourceDirectorySet(
            "Java",
            "Java source",
            ProjectFactory.project,
            SourceArtifactType.JAVA_SOURCES
        )

        temporaryFolder.newFolder("java_1").let {
            File(it, "Test_1_1.java").createNewFile()
            File(it, "Test_1_2.java").createNewFile()
        }
        temporaryFolder.newFolder("java_2").let {
            File(it, "Test_2_1.java").createNewFile()
            File(it, "Test_2_2.java").createNewFile()
        }
    }

    @Test
    fun testIterableFiles() {
        sourceDirectorySet.srcDir(
            listOf(
                File(temporaryFolder.root, "java_1"),
                File(temporaryFolder.root, "java_2"),
            )
        )

        val files = sourceDirectorySet.getSourceFiles().files.map { it.name }

        assertThat(files).hasSize(4)
        assertThat(files).containsExactlyElementsIn(
            listOf("Test_1_1.java", "Test_1_2.java", "Test_2_1.java", "Test_2_2.java")
        )
    }

    @Test
    fun testFilesWithNewAPI() {
        val directorySet = (sourceDirectorySet as com.android.build.api.dsl.AndroidSourceDirectorySet)
        directorySet.srcDir(File(temporaryFolder.root, "java_1"))
        directorySet.srcDir(File(temporaryFolder.root, "java_2"))

        val files = sourceDirectorySet.getSourceFiles().files.map { it.name }

        assertThat(files).hasSize(4)
        assertThat(files).containsExactlyElementsIn(
            listOf("Test_1_1.java", "Test_1_2.java", "Test_2_1.java", "Test_2_2.java")
        )
    }

    @Test
    fun testPathsWithNewAPI() {
        val directorySet = (sourceDirectorySet as com.android.build.api.dsl.AndroidSourceDirectorySet)
        directorySet.srcDir(File(temporaryFolder.root, "java_1").absolutePath)
        directorySet.srcDir(File(temporaryFolder.root, "java_2").absolutePath)

        val files = sourceDirectorySet.getSourceFiles().files.map { it.name }

        assertThat(files).hasSize(4)
        assertThat(files).containsExactlyElementsIn(
            listOf("Test_1_1.java", "Test_1_2.java", "Test_2_1.java", "Test_2_2.java")
        )
    }

    @Test
    fun testDirectoriesWithNewAPI() {
        val directorySet = (sourceDirectorySet as com.android.build.api.dsl.AndroidSourceDirectorySet)
        val projectDirectory = project.layout.projectDirectory
        directorySet.srcDir(projectDirectory.dir(File(temporaryFolder.root, "java_1").absolutePath))
        directorySet.srcDir(projectDirectory.dir(File(temporaryFolder.root, "java_2").absolutePath))

        val files = sourceDirectorySet.getSourceFiles().files.map { it.name }

        assertThat(files).hasSize(4)
        assertThat(files).containsExactlyElementsIn(
            listOf("Test_1_1.java", "Test_1_2.java", "Test_2_1.java", "Test_2_2.java")
        )
    }

    @Test
    fun testSetDirectoriesWithNewAPI() {
        val projectDirectory = project.layout.projectDirectory

        val directories =
            (sourceDirectorySet as com.android.build.api.dsl.AndroidSourceDirectorySet).directories
        listOf(
            projectDirectory.dir(File(temporaryFolder.root, "java_1").absolutePath),
            projectDirectory.dir(File(temporaryFolder.root, "java_2").absolutePath),
        ).forEach { directories.add(it.toString()) }

        val files = sourceDirectorySet.getSourceFiles().files.map { it.name }

        assertThat(files).hasSize(4)
        assertThat(files).containsExactlyElementsIn(
            listOf("Test_1_1.java", "Test_1_2.java", "Test_2_1.java", "Test_2_2.java")
        )
    }

    @Test
    fun testSetPathsWithNewAPI() {
        val directories =
            (sourceDirectorySet as com.android.build.api.dsl.AndroidSourceDirectorySet).directories

        listOf(
            File(temporaryFolder.root, "java_1").absolutePath,
            File(temporaryFolder.root, "java_2").absolutePath,
        ).forEach { directories.add(it.toString()) }

        val files = sourceDirectorySet.getSourceFiles().files.map { it.name }

        assertThat(files).hasSize(4)
        assertThat(files).containsExactlyElementsIn(
            listOf("Test_1_1.java", "Test_1_2.java", "Test_2_1.java", "Test_2_2.java")
        )
    }

    @Test
    fun testDirectoriesSetMixedContent() {
        val directorySet = (sourceDirectorySet as com.android.build.api.dsl.AndroidSourceDirectorySet)
        val directories = directorySet.directories
        assertThat(directories.iterator().hasNext()).isFalse()
        directorySet.srcDir(File(temporaryFolder.root, "java_1"))
        assertThat(directories.first()).endsWith("java_1")

        // iterator test
        directories.add(File(temporaryFolder.root, "java_2").absolutePath)
        val iterator = directories.iterator()
        assertThat(iterator.hasNext()).isTrue()
        assertThat(iterator.next()).endsWith("java_1")
        assertThat(iterator.hasNext()).isTrue()
        assertThat(iterator.next()).endsWith("java_2")

        directories.clear()
        assertThat(directories.size).isEqualTo(0)

        directorySet.srcDir(File(temporaryFolder.root, "java_1"))
        directories.add(File(temporaryFolder.root, "java_2").absolutePath)

        val files = sourceDirectorySet.getSourceFiles().files.map { it.name }

        assertThat(files).hasSize(4)
        assertThat(files).containsExactlyElementsIn(
            listOf("Test_1_1.java", "Test_1_2.java", "Test_2_1.java", "Test_2_2.java")
        )
    }

    @Test
    fun testIterablePaths() {
        sourceDirectorySet.srcDir(
            listOf(
                File(temporaryFolder.root, "java_1").toPath(),
                File(temporaryFolder.root, "java_2").toPath(),
            )
        )

        val files = sourceDirectorySet.getSourceFiles().files.map { it.name }

        assertThat(files).hasSize(4)
        assertThat(files).containsExactlyElementsIn(
            listOf("Test_1_1.java", "Test_1_2.java", "Test_2_1.java", "Test_2_2.java")
        )
    }
}
