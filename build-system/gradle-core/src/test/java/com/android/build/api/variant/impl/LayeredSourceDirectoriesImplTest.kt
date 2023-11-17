/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.api.variant.impl

import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.services.VariantServices
import com.google.common.truth.Truth
import java.util.concurrent.Callable
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

internal class LayeredSourceDirectoriesImplTest {
    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Mock
    lateinit var variantServices: VariantServices

    @Captor
    lateinit var callableCaptor: ArgumentCaptor<Callable<*>>

    private lateinit var project: Project

    private fun <T> capture(argumentCaptor: ArgumentCaptor<T>): T = argumentCaptor.capture()

    @Before
    fun setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(temporaryFolder.newFolder())
            .build()

        Mockito.`when`(variantServices.provider(capture(callableCaptor))).thenAnswer {
            project.provider(callableCaptor.value)
        }

        val projectInfo = Mockito.mock(ProjectInfo::class.java)
        Mockito.`when`(variantServices.projectInfo).thenReturn(projectInfo)
        Mockito.`when`(projectInfo.projectDirectory).thenReturn(project.layout.projectDirectory)

        Mockito.`when`(variantServices.newListPropertyForInternalUse(DirectoryEntries::class.java))
            .thenAnswer { project.objects.listProperty(DirectoryEntries::class.java) }
        Mockito.`when`(variantServices.newListPropertyForInternalUse(Collection::class.java))
            .thenAnswer { project.objects.listProperty(Collection::class.java) }
        Mockito.`when`(variantServices.newListPropertyForInternalUse(Directory::class.java))
            .thenAnswer { project.objects.listProperty(Directory::class.java) }
    }
    @Test
    fun testGetAll() {
        val testTarget = createTestTarget()
        val allSources = testTarget.all.get()
        Truth.assertThat(allSources).hasSize(5)
        Truth.assertThat(allSources[0]).hasSize(1)
        Truth.assertThat(allSources[0].single().asFile.name).isEqualTo("safe")
        Truth.assertThat(allSources[1]).hasSize(1)
        Truth.assertThat(allSources[1].single().asFile.name).isEqualTo("highest1")
        Truth.assertThat(allSources[2]).hasSize(3)
        Truth.assertThat(allSources[2].first().asFile.name).contains("higher")
        Truth.assertThat(allSources[3]).hasSize(3)
        Truth.assertThat(allSources[3].first().asFile.name).contains("lower")
        Truth.assertThat(allSources[4]).hasSize(3)
        Truth.assertThat(allSources[4].first().asFile.name).contains("lowest")
    }

    @Test
    fun testGetStatic() {
        val testTarget = createTestTarget()
        val staticSources = testTarget.static.get()
        Truth.assertThat(staticSources).hasSize(4)
        Truth.assertThat(staticSources[0]).hasSize(1)
        Truth.assertThat(staticSources[0].single().asFile.name).isEqualTo("safe")
        Truth.assertThat(staticSources[1]).hasSize(3)
        Truth.assertThat(staticSources[1].first().asFile.name).contains("higher")
        Truth.assertThat(staticSources[2]).hasSize(3)
        Truth.assertThat(staticSources[2].first().asFile.name).contains("lower")
        Truth.assertThat(staticSources[3]).hasSize(3)
        Truth.assertThat(staticSources[3].first().asFile.name).contains("lowest")
    }

    @Test
    fun testGetAllStaticSplit() {
        val testTarget = LayeredSourceDirectoriesImpl(
            "_for_test",
            variantServices,
            null,
        )

        // directories are added in reverse order, lower priority first, then higher priority
        testTarget.addStaticSources(DirectoryEntries("lowestStatic", mutableListOf(
            FileBasedDirectoryEntryImpl("lowestStatic", temporaryFolder.newFolder("lowestStatic")),
        )
        ))

        testTarget.addSource(
            FileBasedDirectoryEntryImpl("lowerGenerated", temporaryFolder.newFolder("lowerGenerated")),
        )

        testTarget.addStaticSources(DirectoryEntries("higherStatic", mutableListOf(
            FileBasedDirectoryEntryImpl("higherStatic", temporaryFolder.newFolder("higherStatic")),
        )
        ))

        testTarget.addSource(
            FileBasedDirectoryEntryImpl("highestGenerated", temporaryFolder.newFolder("highestGenerated")),)

        val staticSources = testTarget.static.get()
        val allSources = testTarget.all.get()

        Truth.assertThat(staticSources).hasSize(2)
        Truth.assertThat(staticSources[0]).hasSize(1)
        Truth.assertThat(staticSources[0].single().asFile.name).isEqualTo("higherStatic")
        Truth.assertThat(staticSources[1]).hasSize(1)
        Truth.assertThat(staticSources[1].single().asFile.name).isEqualTo("lowestStatic")


        Truth.assertThat(allSources).hasSize(4)
        Truth.assertThat(allSources[0]).hasSize(1)
        Truth.assertThat(allSources[0].single().asFile.name).isEqualTo("highestGenerated")
        Truth.assertThat(allSources[1]).hasSize(1)
        Truth.assertThat(allSources[1].single().asFile.name).isEqualTo("higherStatic")
        Truth.assertThat(allSources[2]).hasSize(1)
        Truth.assertThat(allSources[2].single().asFile.name).isEqualTo("lowerGenerated")
        Truth.assertThat(allSources[3]).hasSize(1)
        Truth.assertThat(allSources[3].single().asFile.name).isEqualTo("lowestStatic")

    }

    private fun createTestTarget(): LayeredSourceDirectoriesImpl {

        val testTarget = LayeredSourceDirectoriesImpl(
            "_for_test",
            variantServices,
            null,
        )

        // directories are added in reverse order, lower priority first, then higher priority
        testTarget.addStaticSources(DirectoryEntries("lowest", mutableListOf(
            FileBasedDirectoryEntryImpl("lowest1", temporaryFolder.newFolder("lowest1")),
            FileBasedDirectoryEntryImpl("lowest2", temporaryFolder.newFolder("lowest2")),
            FileBasedDirectoryEntryImpl("lowest3", temporaryFolder.newFolder("lowest3")),
        )
        ))

        testTarget.addStaticSources(DirectoryEntries("lower", mutableListOf(
            FileBasedDirectoryEntryImpl("lower1", temporaryFolder.newFolder("lower1")),
            FileBasedDirectoryEntryImpl("lower2", temporaryFolder.newFolder("lower2")),
            FileBasedDirectoryEntryImpl("lower3", temporaryFolder.newFolder("lower3")),
        )
        ))

        testTarget.addStaticSources(DirectoryEntries("higher", mutableListOf(
            FileBasedDirectoryEntryImpl("higher1", temporaryFolder.newFolder("higher1")),
            FileBasedDirectoryEntryImpl("higher2", temporaryFolder.newFolder("higher2")),
            FileBasedDirectoryEntryImpl("higher3", temporaryFolder.newFolder("higher3")),
        )
        ))

        testTarget.addSource(
            FileBasedDirectoryEntryImpl("highest1", temporaryFolder.newFolder("highest1"))
        )

        testTarget.addStaticSourceDirectory(
            temporaryFolder.newFolder("somewhere/safe").absolutePath,
        )

        return testTarget
    }
}
