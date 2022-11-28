/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.google.common.truth.Truth.assertThat
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import com.android.builder.core.BuilderConstants
import com.android.ide.common.resources.AssetSet
import com.google.common.collect.Lists
import java.io.File
import java.util.Arrays
import java.util.HashSet
import java.util.LinkedHashSet
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MergeSourceSetFoldersTest {

    @get:Rule
    var temporaryFolder = TemporaryFolder()

    private lateinit var project: Project
    private lateinit var task: MergeSourceSetFolders

    @Before
    fun setUp() {
        val testDir = temporaryFolder.newFolder()
        project = ProjectBuilder.builder().withProjectDir(testDir).build()

        task = project.tasks.create("test", MergeSourceSetFolders::class.java)
        task.aaptEnv.set("aapt")
    }

    @Test
    fun singleSetWithSingleFile() {
        val file = File("src/main")
        val mainSet = createAssetSet(BuilderConstants.MAIN, file)

        val result = task.computeAssetSetList()
        assertThat(result).hasSize(1)
        assertThat(result[0].configName).isEqualTo(mainSet.configName)
        assertThat(result[0].sourceFiles).isEqualTo(mainSet.sourceFiles)
    }

    @Test
    fun singleSetWithMultiFiles() {
        val file = File("src/main")
        val file2 = File("src/main2")
        val mainSet = createAssetSet(BuilderConstants.MAIN, file, file2)

        val result = task.computeAssetSetList()
        assertThat(result).hasSize(1)
        assertThat(result[0].configName).isEqualTo(mainSet.configName)
        assertThat(result[0].sourceFiles).isEqualTo(mainSet.sourceFiles)
    }

    @Test
    fun twoSetsWithSingleFile() {
        val file = File("src/main")
        val mainSet = createAssetSet(BuilderConstants.MAIN, file)

        val file2 = File("src/debug")
        val debugSet = createAssetSet("debug", file2)

        val result = task.computeAssetSetList()
        assertThat(result).hasSize(2)
        assertThat(result[0].configName).isEqualTo(mainSet.configName)
        assertThat(result[0].sourceFiles).isEqualTo(mainSet.sourceFiles)
        assertThat(result[1].configName).isEqualTo(debugSet.configName)
        assertThat(result[1].sourceFiles).isEqualTo(debugSet.sourceFiles)
    }

    @Test
    fun testMergingAssetSets() {
        val file1 = File("src/main1")
        val file2 = File("src/main2")
        val file3 = File("src/main3")
        createAssetSet(BuilderConstants.MAIN, file1)
        createAssetSet(BuilderConstants.MAIN, file2)
        createAssetSet(BuilderConstants.MAIN, file3)

        val fileLib = File("src/debug")
        val debugSet = createAssetSet("debug", fileLib)

        val result = task.computeAssetSetList()
        assertThat(result).hasSize(2)
        assertThat(result[0].configName).isEqualTo(BuilderConstants.MAIN)
        assertThat(result[0].sourceFiles).containsExactly(
            file1, file2, file3
        )
        assertThat(result[1].configName).isEqualTo(debugSet.configName)
        assertThat(result[1].sourceFiles).containsExactly(fileLib)
    }

    @Test
    fun singleSetWithDependency() {
        val file = File("src/main").absoluteFile
        val mainSet = createAssetSet(BuilderConstants.MAIN, file)

        val file2 = File("foo/bar/1.0").absoluteFile
        val librarySets = setupLibraryDependencies(file2, ":path")

        assertThat(task.libraries.files).containsExactly(file2)
        val result = task.computeAssetSetList()
        assertThat(result).hasSize(2)
        assertThat(result[0].configName).isEqualTo(librarySets[0].configName)
        assertThat(result[0].sourceFiles).containsExactly(file2)
        assertThat(result[1].configName).isEqualTo(mainSet.configName)
        assertThat(result[1].sourceFiles).containsExactly(file)
    }

    @Test
    fun singleSetWithRenderscript() {
        val file = File("src/main")
        val mainSet = createAssetSet(BuilderConstants.MAIN, file)

        val generatedSet = createAssetSet(BuilderConstants.GENERATED)

        val shaderFile = temporaryFolder.newFile("shader")
        task.shadersOutputDir.set(shaderFile)

        val result = task.computeAssetSetList()
        assertThat(result).hasSize(2)
        assertThat(result[0].configName).isEqualTo(mainSet.configName)
        assertThat(result[0].sourceFiles).containsExactly(file)
        assertThat(result[1].configName).isEqualTo(generatedSet.configName)
        assertThat(result[1].sourceFiles).containsExactly(shaderFile)
    }

    @Test
    fun singleSetWithMlModels() {
        val file = File("src/main")
        val mainSet = createAssetSet(BuilderConstants.MAIN, file)

        val generatedSet = createAssetSet(BuilderConstants.GENERATED)

        val mlModelsDir = temporaryFolder.newFile("ml")
        task.mlModelsOutputDir.set(mlModelsDir)

        val result = task.computeAssetSetList()
        assertThat(result).hasSize(2)
        assertThat(result[0].configName).isEqualTo(mainSet.configName)
        assertThat(result[0].sourceFiles).containsExactly(file)
        assertThat(result[1].configName).isEqualTo(generatedSet.configName)
        assertThat(result[1].sourceFiles).containsExactly(mlModelsDir)
    }

    @Test
    fun everything() {
        val file = File("src/main")
        val file2 = File("src/main2")
        val mainSet = createAssetSet(BuilderConstants.MAIN, file, file2)

        val debugFile = File("src/debug")
        val debugSet = createAssetSet("debug", debugFile)

        val libFile = File("foo/bar/1.0").absoluteFile
        val libFile2 = File("foo/bar/2.0").absoluteFile

        val generatedSet = createAssetSet(BuilderConstants.GENERATED)

        // the order returned by the dependency is meant to be in the wrong order (consumer first,
        // when we want dependent first for the merger), so the order in the asset set should be
        // the opposite order.
        val librarySets = setupLibraryDependencies(
            libFile, "foo:bar:1.0",
            libFile2, "foo:bar:2.0"
        )
        val librarySet = librarySets[0]
        val librarySet2 = librarySets[1]

        val shaderFile = File("shader")
        task.shadersOutputDir.set(shaderFile)

        assertThat(task.libraries.files).containsExactly(libFile, libFile2)
        val result = task.computeAssetSetList()
        assertThat(result).hasSize(5)
        assertThat(result[0].configName).isEqualTo(librarySet2.configName)
        assertThat(result[0].sourceFiles).isEqualTo(librarySet2.sourceFiles)
        assertThat(result[1].configName).isEqualTo(librarySet.configName)
        assertThat(result[1].sourceFiles).isEqualTo(librarySet.sourceFiles)
        assertThat(result[2].configName).isEqualTo(mainSet.configName)
        assertThat(result[2].sourceFiles).isEqualTo(mainSet.sourceFiles)
        assertThat(result[3].configName).isEqualTo(debugSet.configName)
        assertThat(result[3].sourceFiles).isEqualTo(debugSet.sourceFiles)
        assertThat(result[4].configName).isEqualTo(generatedSet.configName)
        assertThat(result[4].sourceFiles[0].absolutePath).contains("shader")
    }

    private fun createAssetSet(
        name: String,
        vararg files: File
    ): AssetSet {
        val mainSet = AssetSet(name, null)
        mainSet.addSources(Arrays.asList(*files))
        task.assetSets.add(project.provider { mainSet })
        return mainSet
    }

    private fun setupLibraryDependencies(vararg objects: Any): List<AssetSet> {
        val libraries = mock(ArtifactCollection::class.java)

        val artifacts = LinkedHashSet<ResolvedArtifactResult>()
        val files = HashSet<File>()
        val assetSets = Lists.newArrayListWithCapacity<AssetSet>(objects.size / 2)

        var i = 0
        val count = objects.size
        while (i < count) {
            assertThat(objects[i]).isInstanceOf(File::class.java)
            assertThat(objects[i + 1]).isInstanceOf(String::class.java)

            val file = objects[i] as File
            val path = objects[i + 1] as String

            files.add(file)

            val artifact = mock(ResolvedArtifactResult::class.java)
            artifacts.add(artifact)

            val artifactId = mock(ComponentArtifactIdentifier::class.java)
            val id = mock(ProjectComponentIdentifier::class.java)

            `when`(id.projectPath).thenReturn(path)
            `when`<ComponentIdentifier>(artifactId.componentIdentifier).thenReturn(id)
            `when`(artifact.file).thenReturn(file)
            `when`(artifact.id).thenReturn(artifactId)

            // create a resource set that must match the one returned by the computation
            val set = AssetSet(path, null)
            set.addSource(file)
            assetSets.add(set)
            i += 2
        }

        `when`(libraries.artifacts).thenReturn(artifacts)
        `when`(libraries.artifactFiles).thenReturn(task.libraries)

        task.libraryCollection = libraries
        task.libraries.from(files)
        task.aaptEnv.set("aapt")

        return assetSets
    }
}
