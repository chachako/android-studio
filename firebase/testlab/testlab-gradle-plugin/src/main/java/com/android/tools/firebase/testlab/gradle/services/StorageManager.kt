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
import com.android.tools.firebase.testlab.gradle.services.storage.TestRunStorage
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.InputStreamContent
import com.google.api.services.storage.Storage
import com.google.api.services.storage.model.StorageObject
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * class to handle all [Storage] related requests made by the [TestLabBuildService]
 *
 * @param storageClient The underlying storage api object
 */
class StorageManager (
    private val storageClient: Storage,
    private val hashingCache: FileHashCache = FileHashCache()
) {
    private val fileLocks: ConcurrentHashMap<String, Any> = ConcurrentHashMap()

    companion object {
        val cloudStorageUrlRegex = Regex("""gs://(.*?)/(.*)""")
    }

    fun testRunStorage(testRunId: String, bucketName: String, historyId: String): TestRunStorage =
        TestRunStorage(
            testRunId,
            bucketName,
            historyId,
            this
        )

    fun uploadFile(
        file: File,
        bucketName: String,
        prefix: String = "",
        uploadFileName: String = file.name
    ): StorageObject {
        val storageObject = FileInputStream(file).use { fileInputStream ->
            storageClient.objects().insert(
                bucketName,
                StorageObject(),
                InputStreamContent("application/octet-stream", fileInputStream).apply {
                    length = file.length()
                }
            ).apply {
                name = "$prefix${uploadFileName}"
            }.execute()
        }
        return storageObject
    }

    /**
     * Checks whether the given shared file exists as a shared file in the cloud. Otherwise
     * uploads the file to the cloud.
     *
     * The file's sha256 hash is used for shared files, as multiple versions of shared files are
     * expected to uploaded to the cloud at the same time.
     *
     * Firstly, the sha256 is retrieved or computed from the file hash cache.
     *
     * Then the file will be retrieved from [bucketName]/[moduleName]/<computed-hash>-[file]. If it
     * exists, has not been modified, it is returned, otherwise the file is uploaded to the above
     * location.
     *
     * @return the StorageObject for the given shared file.
     */
    fun retrieveOrUploadSharedFile(
        file: File,
        bucketName: String,
        moduleName: String,
        uploadFileName: String = file.name
    ): StorageObject {
        return lockOnFile(file) {
            val hash = hashingCache.retrieveOrGenerateHash(file)
            val hashQualifiedPrefix = "$moduleName/$hash-"
            val hashQualifiedName = hashQualifiedPrefix + uploadFileName

            val storageObject = try {
                storageClient.objects().get(bucketName, hashQualifiedName).execute()
            } catch (e: GoogleJsonResponseException) {
                null
            }
            if (storageObject != null && storageObject.isNotModified()) {
                // TODO (b/276517167): check if storage object has become stale.
                storageObject
            } else {
                uploadFile(file, bucketName, hashQualifiedPrefix, uploadFileName)
            }
        }
    }

    fun retrieveFile(fileUri: String): StorageObject? {
        val matchResult = cloudStorageUrlRegex.find(fileUri) ?: return null
        val (bucketName, objectName) = matchResult.destructured
        return try {
            storageClient.objects().get(bucketName, objectName).execute()
        } catch (e: GoogleJsonResponseException) {
            null
        }
    }

    fun downloadFile(storageObject: StorageObject, destination: (objectName: String) -> File): File? =
        download(storageObject.bucket, storageObject.name, destination(storageObject.name))

    fun downloadFile(fileUri: String, destination: (objectName: String) -> File): File? {
        val matchResult = cloudStorageUrlRegex.find(fileUri) ?: return null
        val (bucketName, objectName) = matchResult.destructured
        return download(bucketName, objectName, destination(objectName))
    }

    private fun download(bucketName: String, objectName: String, destination: File): File? =
        destination.apply {
            parentFile.mkdirs()
            outputStream().use {
                storageClient.objects()
                    .get(bucketName, objectName)
                    .executeMediaAndDownloadTo(it)
            }
        }

    private fun <V> lockOnFile(file: File, execution: () -> V) =
        synchronized(fileLocks.computeIfAbsent(file.absolutePath) { Any() }) {
            execution.invoke()
        }
}

fun StorageObject.toUrl() = "gs://$bucket/$name"

fun StorageObject.isNotModified(): Boolean =
    getUpdated() == null || getUpdated() == getTimeCreated()
