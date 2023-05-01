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

import com.android.tools.firebase.testlab.gradle.services.storage.TestRunStorage
import com.google.api.client.http.InputStreamContent
import com.google.api.services.storage.Storage
import com.google.api.services.storage.model.StorageObject
import java.io.File
import java.io.FileInputStream

/**
 * class to handle all [Storage] related requests made by the [TestLabBuildService]
 */
class StorageManager (
    private val storageClient: Storage
) {

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

    fun uploadFile(file: File, bucketName: String, prefix: String): StorageObject {
        val storageObject = FileInputStream(file).use { fileInputStream ->
            storageClient.objects().insert(
                bucketName,
                StorageObject(),
                InputStreamContent("application/octet-stream", fileInputStream).apply {
                    length = file.length()
                }
            ).apply {
                name = "$prefix${file.name}"
            }.execute()
        }
        return storageObject
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
}

fun StorageObject.toUrl() = "gs://$bucket/$name"
