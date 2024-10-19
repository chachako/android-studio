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

package com.android.build.api.artifact.impl

import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.ArtifactKind
import com.google.common.truth.Truth
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit Tests for [StorageProviderImpl]
 */
class StorageProviderImplTest {

    private val objects: ObjectFactory = mock()
    private val stringProperty: Property<String> = mock()
    private val fileProperty: RegularFileProperty = mock()
    private val filesProperty: ListProperty<RegularFile> = mock()
    private val directoryProperty: DirectoryProperty = mock()
    private val directoriesProperty: ListProperty<Directory> = mock()

    @Before
    fun setUp() {
        }

    sealed class SingleTestTypes<T: FileSystemLocation>(
        kind: ArtifactKind<T>
    ): Artifact.Single<T>(kind, Category.INTERMEDIATES) {
        object SINGLE_FILE : SingleTestTypes<RegularFile>(ArtifactKind.FILE)
        object SINGLE_DIRECTORY : SingleTestTypes<Directory>(ArtifactKind.DIRECTORY)
    }
    sealed class MultipleTestTypes<T: FileSystemLocation>(
        kind: ArtifactKind<T>
    ): Artifact.Multiple<T>(kind, Category.INTERMEDIATES) {
        object MULTIPLE_FILES: MultipleTestTypes<RegularFile>(ArtifactKind.FILE)
        object MULTIPLE_DIRECTORIESS: MultipleTestTypes<Directory>(ArtifactKind.DIRECTORY)
    }

    @Test
    fun singleFileAllocationTest() {
        addInitMocks()
        whenever(objects.fileProperty()).thenReturn(fileProperty)
        val storage = StorageProviderImpl().getStorage(ArtifactKind.FILE)
        val artifact = storage.getArtifact(objects, SingleTestTypes.SINGLE_FILE)
        Truth.assertThat(artifact.getCurrent().isPresent).isFalse()
    }

    private fun addInitMocks(){
        whenever(objects.property(String::class.java)).thenReturn(stringProperty)
        whenever(objects.directoryProperty()).thenReturn(directoryProperty)
    }

    @Test
    fun singleDirectoryAllocationTest() {
        addInitMocks()
        whenever(objects.directoryProperty()).thenReturn(directoryProperty)
        val storage = StorageProviderImpl().getStorage(ArtifactKind.DIRECTORY)
        val artifact = storage.getArtifact(objects, SingleTestTypes.SINGLE_DIRECTORY)
        Truth.assertThat(artifact.getCurrent().isPresent).isFalse()
    }

    @Test
    fun multipleFilesAllocationTest() {
        whenever(objects.listProperty(eq(RegularFile::class.java))).thenReturn(filesProperty)
        val storage = StorageProviderImpl().getStorage(ArtifactKind.FILE)
        val artifact = storage.getArtifact(objects, MultipleTestTypes.MULTIPLE_FILES)
        Truth.assertThat(artifact.getCurrent().isPresent).isFalse()
    }

    @Test
    fun multipleDirectoriesAllocationTest() {
        whenever(objects.listProperty(eq(Directory::class.java))).thenReturn(directoriesProperty)
        val storage = StorageProviderImpl().getStorage(ArtifactKind.DIRECTORY)
        val artifact = storage.getArtifact(objects, MultipleTestTypes.MULTIPLE_DIRECTORIESS)
        Truth.assertThat(artifact.getCurrent().isPresent).isFalse()
    }
}
