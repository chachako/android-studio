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

import com.google.api.client.http.InputStreamContent
import com.google.api.services.storage.Storage
import com.google.api.services.storage.model.StorageObject
import java.io.File
import java.io.FileInputStream

/**
 * Handles upload and download of files to cloud for an individual test run
 *
 * @param testRunId the uuid that is associated with the test run
 * @param bucketName a unique id associated with all test files in this module
 * @param storageClient the storage connection that will handle all storage requests.
 */
class TestRunStorage (
    private val testRunId: String,
    private val bucketName: String,
    private val storageClient: Storage) {

    fun uploadToStorage(file: File): StorageObject =
        FileInputStream(file).use { fileInputStream ->
            storageClient.objects().insert(
                bucketName,
                StorageObject(),
                InputStreamContent("application/octet-stream", fileInputStream).apply {
                    length = file.length()
                }
            ).apply {
                name = "${testRunId}_${file.name}"
            }.execute()
        }

    fun downloadFromStorage(fileUri: String, destination: (objectName: String) -> File): File? {
        val matchResult = cloudStorageUrlRegex.find(fileUri) ?: return null
        val (bucketName, objectName) = matchResult.destructured
        return destination(objectName.removePrefix("$testRunId/")).apply {
            parentFile.mkdirs()
            outputStream().use {
                storageClient.objects()
                    .get(bucketName, objectName)
                    .executeMediaAndDownloadTo(it)
            }
        }
    }

    companion object {
        val cloudStorageUrlRegex = Regex("""gs://(.*?)/(.*)""")
    }
}
