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

package com.android.tools.firebase.testlab.gradle.services.storage

import com.android.tools.firebase.testlab.gradle.services.StorageManager
import com.google.api.services.storage.model.StorageObject
import java.io.File

/**
 * Handles upload and download of files to cloud for an individual test run
 *
 * @param testRunId the uuid that is associated with the test run
 * @param bucketName a unique id associated with all test files in this module
 * @param historyId the id associated with the test history this run should belong to.
 * @param storageManager the storage manager that the uploads and downloads are delegated to.
 */
class TestRunStorage (
    private val testRunId: String,
    private val bucketName: String,
    val historyId: String,
    private val storageManager: StorageManager) {

    val resultStoragePath: String = "gs://$bucketName/$testRunId/results"

    fun uploadToStorage(file: File): StorageObject =
        storageManager.uploadFile(file, bucketName, prefix = "${testRunId}_")

    fun downloadFromStorage(fileUri: String, destination: (objectName: String) -> File): File? =
        storageManager.downloadFile(fileUri) { objectName ->
            destination(objectName.removePrefix("$testRunId/"))
        }
}
