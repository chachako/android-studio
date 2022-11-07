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

import com.android.build.gradle.internal.fixtures.FakeGradleProvider
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
import org.mockito.stubbing.Answer

internal class AssetSourceDirectoriesImplTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Mock
    lateinit var variantServices: VariantServices

    @Captor
    lateinit var callableCaptor: ArgumentCaptor<Callable<*>>

    private lateinit var project: Project

    fun <T> capture(argumentCaptor: ArgumentCaptor<T>): T = argumentCaptor.capture()

    @Before
    fun setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(temporaryFolder.newFolder())
            .build()

        Mockito.`when`(variantServices.newListPropertyForInternalUse(DirectoryEntries::class.java))
            .thenReturn(project.objects.listProperty(DirectoryEntries::class.java))
        Mockito.`when`(variantServices.newListPropertyForInternalUse(Collection::class.java))
            .thenReturn(project.objects.listProperty(Collection::class.java))

    }

    @Test
    fun asAssetSetEmptyTest() {
        val testTarget = LayeredSourceDirectoriesImpl(
            "unit_test",
            variantServices,
            null
        )

        Truth.assertThat(testTarget.getAscendingOrderAssetSets(FakeGradleProvider("aapt_env"))).isNotNull()
        Truth.assertThat(testTarget.getAscendingOrderAssetSets(FakeGradleProvider("aapt_env")).get()).isEmpty()
    }

    @Test
    fun asAssetSetTest() {
        val projectInfo = Mockito.mock(ProjectInfo::class.java)
        Mockito.`when`(variantServices.projectInfo).thenReturn(projectInfo)
        Mockito.`when`(projectInfo.projectDirectory).thenReturn(project.layout.projectDirectory)

        Mockito.`when`(variantServices.provider(capture(callableCaptor))).thenAnswer(
            Answer() {
                project.provider(callableCaptor.value)
            }
        )
        Mockito.`when`(variantServices.newListPropertyForInternalUse(Directory::class.java)).also {
            var stub = it
            repeat(5) {
                stub = stub.thenReturn(project.objects.listProperty(Directory::class.java))
            }
        }

        val testTarget = LayeredSourceDirectoriesImpl(
            "unit_test",
            variantServices,
            null
        )

        // directories are added in reverse order, lower priority first, then higher prioriry
        testTarget.addSources(DirectoryEntries("lowest", listOf(
            FileBasedDirectoryEntryImpl("lowest1", temporaryFolder.newFolder("lowest1")),
            FileBasedDirectoryEntryImpl("lowest2", temporaryFolder.newFolder("lowest2")),
            FileBasedDirectoryEntryImpl("lowest3", temporaryFolder.newFolder("lowest3")),
        )))

        testTarget.addSources(DirectoryEntries("lower", listOf(
            FileBasedDirectoryEntryImpl("lower1", temporaryFolder.newFolder("lower1")),
            FileBasedDirectoryEntryImpl("lower2", temporaryFolder.newFolder("lower2")),
            FileBasedDirectoryEntryImpl("lower3", temporaryFolder.newFolder("lower3")),
        )))

        testTarget.addSources(DirectoryEntries("higher", listOf(
            FileBasedDirectoryEntryImpl("higher1", temporaryFolder.newFolder("higher1")),
            FileBasedDirectoryEntryImpl("higher2", temporaryFolder.newFolder("higher2")),
            FileBasedDirectoryEntryImpl("higher3", temporaryFolder.newFolder("higher3")),
        )))

        Truth.assertThat(testTarget.getAscendingOrderAssetSets(FakeGradleProvider("aapt_env"))).isNotNull()
        val assetSets = testTarget.getAscendingOrderAssetSets(FakeGradleProvider("aapt_env")).get().map { it.get() }
        Truth.assertThat(assetSets).hasSize(9)
        Truth.assertThat(assetSets[0].configName).isEqualTo("lowest")
        Truth.assertThat(assetSets[1].configName).isEqualTo("lowest")
        Truth.assertThat(assetSets[2].configName).isEqualTo("lowest")
        Truth.assertThat(assetSets[3].configName).isEqualTo("lower")
        Truth.assertThat(assetSets[4].configName).isEqualTo("lower")
        Truth.assertThat(assetSets[5].configName).isEqualTo("lower")
        Truth.assertThat(assetSets[6].configName).isEqualTo("higher")
        Truth.assertThat(assetSets[7].configName).isEqualTo("higher")
        Truth.assertThat(assetSets[8].configName).isEqualTo("higher")
    }
}
