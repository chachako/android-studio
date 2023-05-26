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

package com.android.tools.firebase.testlab.gradle.services

import com.android.tools.firebase.testlab.gradle.services.storage.FileHashCache
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.util.DateTime
import com.google.api.services.storage.Storage
import com.google.api.services.storage.model.StorageObject
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.io.File
import java.util.Date

/**
 * Unit tests for [StorageManager]
 */
class StorageManagerTest {

    @get:Rule
    val mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    @Mock
    lateinit var cloudStorageClient: Storage

    @Mock
    lateinit var mockObjects: Storage.Objects

    @Mock
    lateinit var mockInsert: Storage.Objects.Insert

    @Mock
    lateinit var mockGet: Storage.Objects.Get

    @Before
    fun setup() {
        `when`(cloudStorageClient.objects()).thenReturn(mockObjects)
        `when`(mockObjects.insert(any(), any(), any())).thenReturn(mockInsert)
        `when`(mockObjects.get(any(), any())).thenReturn(mockGet)
    }

    @Test
    fun testUpload() {
        val storageObject: StorageObject = mock()
        `when`(mockInsert.execute()).thenReturn(storageObject)

        val file = temporaryFolderRule.newFile("World").apply {
            writeText("aabbcc")
        }

        val storageManager = StorageManager(cloudStorageClient)

        val result = storageManager.uploadFile(
            file,
            "some-bucket",
            "Hello_"
        )

        assertThat(result).isSameInstanceAs(storageObject)

        verify(mockObjects).insert(eq("some-bucket"), any(), any())

        inOrder(mockInsert).also {
            it.verify(mockInsert).setName("Hello_World")
            it.verify(mockInsert).execute()
        }

        verifyNoMoreInteractions(mockObjects)
        verifyNoMoreInteractions(mockInsert)
    }

    @Test
    fun testDownloadFile() {
        val storageObject: StorageObject = mock<StorageObject>().apply {
            `when`(this.bucket).thenReturn("some-bucket")
            `when`(this.name).thenReturn("some-file-name")
        }
        val resultFile = temporaryFolderRule.newFile("localFile")

        val storageManager = StorageManager(cloudStorageClient)

        val returnValue = storageManager.downloadFile(storageObject) { resultFile }

        assertThat(returnValue).isSameInstanceAs(resultFile)

        verify(mockObjects).get(eq("some-bucket"), eq("some-file-name"))
        verify(mockGet).executeMediaAndDownloadTo(any())

        verifyNoMoreInteractions(mockObjects)
        verifyNoMoreInteractions(mockGet)
    }

    @Test
    fun testRetrieveOrUploadShared() {
        `when`(mockGet.execute()).thenAnswer {
            throw GoogleJsonResponseException(mock(), mock())
        }

        val storageObject: StorageObject = mock()

        val cache = mock<FileHashCache>().apply {
            `when`(this.retrieveOrGenerateHash(any())).thenReturn("fileHash")
        }

        `when`(mockInsert.execute()).thenReturn(storageObject)

        val file = temporaryFolderRule.newFile("text").apply {
            writeText("aabbcc")
        }

        val storageManager = StorageManager(cloudStorageClient, cache)

        // First run should behave normally.
        val result = storageManager.retrieveOrUploadSharedFile(file, "bucket", "module")

        assertThat(result).isSameInstanceAs(storageObject)

        inOrder(mockObjects).also {
            // Checks for file existence first.
            it.verify(mockObjects).get(eq("bucket"), eq("module/fileHash-text"))
            verify(mockGet).execute()

            // Then inserts when it doesn't exist
            it.verify(mockObjects).insert(eq("bucket"), any(), any())
            inOrder(mockInsert).also {
                it.verify(mockInsert).setName("module/fileHash-text")
                it.verify(mockInsert).execute()
            }
        }

        verifyNoMoreInteractions(mockObjects)
        verifyNoMoreInteractions(mockGet)
        verifyNoMoreInteractions(mockInsert)
    }

    @Test
    fun testRetrieveOrUploadShared_preventsReuploadSameFile() {
        // Need to do some history management of what files have been uploaded
        var insertFileName: String? = null
        var getFileName: String? = null
        var uploadedFiles = mutableMapOf<String, StorageObject>()
        val storageObject: StorageObject = mock<StorageObject>().apply() {
            `when`(this.getUpdated()).thenReturn(DateTime(Date(4000)))
            `when`(this.getTimeCreated()).thenReturn(DateTime(Date(4000)))
        }

        val file = temporaryFolderRule.newFile("text").apply {
            writeText("aabbcc")
        }

        mockInsert.apply {
            `when`(this.setName(any<String>())).thenAnswer { invocation ->
                insertFileName = invocation.getArguments()[0] as String
                this
            }
            `when`(this.execute()).thenAnswer {
                assertThat(insertFileName).isNotNull()
                uploadedFiles[insertFileName!!] = storageObject
                storageObject
            }
        }

        `when`(mockObjects.get(any(), any())).thenAnswer { invocation ->
            getFileName = invocation.getArguments()[1] as String
            mockGet
        }
        `when`(mockGet.execute()).thenAnswer {
            assertThat(getFileName).isNotNull()
            uploadedFiles[getFileName] ?: throw GoogleJsonResponseException(mock(), mock())
        }

        val cache = mock<FileHashCache>().apply {
            `when`(this.retrieveOrGenerateHash(any())).thenReturn("fileHash")
        }
        val storageManager = StorageManager(cloudStorageClient, cache)

        val result = storageManager.retrieveOrUploadSharedFile(file, "bucket", "module")

        assertThat(result).isSameInstanceAs(storageObject)

        // Second run should return get result.
        val rerunResult = storageManager.retrieveOrUploadSharedFile(file, "bucket", "module")

        assertThat(rerunResult).isSameInstanceAs(storageObject)

        inOrder(mockObjects).also {
            // Checks for file existence first.
            it.verify(mockObjects).get(eq("bucket"), eq("module/fileHash-text"))

            // Then inserts when it doesn't exist
            it.verify(mockObjects).insert(eq("bucket"), any(), any())
            inOrder(mockInsert).also {
                it.verify(mockInsert).setName("module/fileHash-text")
                it.verify(mockInsert).execute()
            }

            // Second run checks for object existence (it does) and does not reupload.
            it.verify(mockObjects).get(eq("bucket"), eq("module/fileHash-text"))
        }

        verify(mockGet, times(2)).execute()

        verifyNoMoreInteractions(mockObjects)
        verifyNoMoreInteractions(mockGet)
        verifyNoMoreInteractions(mockInsert)
    }

    @Test
    fun testRetrieveOrUploadShared_uploadsModifiedFile() {
        // Need to do some history management of what files have been uploaded
        var insertFileName: String? = null
        var getFileName: String? = null
        var uploadedFiles = mutableMapOf<String, StorageObject>()
        val storageObject: StorageObject = mock<StorageObject>().apply() {
            `when`(this.getUpdated()).thenReturn(DateTime(Date(4000)))
            `when`(this.getTimeCreated()).thenReturn(DateTime(Date(4000)))
        }

        // For this version the hash will be based on file contents.
        val file = temporaryFolderRule.newFile("text").apply {
            writeText("aabbcc")
        }

        mockInsert.apply {
            `when`(this.setName(any<String>())).thenAnswer { invocation ->
                insertFileName = invocation.getArguments()[0] as String
                this
            }
            `when`(this.execute()).thenAnswer {
                assertThat(insertFileName).isNotNull()
                uploadedFiles[insertFileName!!] = storageObject
                storageObject
            }
        }

        `when`(mockObjects.get(any(), any())).thenAnswer { invocation ->
            getFileName = invocation.getArguments()[1] as String
            mockGet
        }
        `when`(mockGet.execute()).thenAnswer {
            assertThat(getFileName).isNotNull()
            uploadedFiles[getFileName] ?: throw GoogleJsonResponseException(mock(), mock())
        }

        val cache = mock<FileHashCache>().apply {
            `when`(this.retrieveOrGenerateHash(any())).thenAnswer { invocation ->
                (invocation.getArguments()[0] as File).readLines().first()
            }
        }
        val storageManager = StorageManager(cloudStorageClient, cache)

        val result = storageManager.retrieveOrUploadSharedFile(file, "bucket", "module")

        assertThat(result).isSameInstanceAs(storageObject)

        file.apply {
            writeText("xxyyzz")
        }

        // Second run should re-upload b/c file has been modified
        val rerunResult = storageManager.retrieveOrUploadSharedFile(file, "bucket", "module")

        assertThat(rerunResult).isSameInstanceAs(storageObject)

        inOrder(mockObjects).also {
            // Checks for file existence first.
            it.verify(mockObjects).get(eq("bucket"), eq("module/aabbcc-text"))

            // Then inserts when it doesn't exist
            it.verify(mockObjects).insert(eq("bucket"), any(), any())

            // Second run checks for object existence, the name will have changed due to hash
            // and therefore doesn't exist.
            it.verify(mockObjects).get(eq("bucket"), eq("module/xxyyzz-text"))

            // re-uploads.
            it.verify(mockObjects).insert(eq("bucket"), any(), any())
        }

        inOrder(mockInsert).also {
            // first upload
            it.verify(mockInsert).setName("module/aabbcc-text")
            it.verify(mockInsert).execute()
            // second upload
            it.verify(mockInsert).setName("module/xxyyzz-text")
            it.verify(mockInsert).execute()
        }

        verify(mockGet, times(2)).execute()

        verifyNoMoreInteractions(mockObjects)
        verifyNoMoreInteractions(mockGet)
        verifyNoMoreInteractions(mockInsert)
    }
}
