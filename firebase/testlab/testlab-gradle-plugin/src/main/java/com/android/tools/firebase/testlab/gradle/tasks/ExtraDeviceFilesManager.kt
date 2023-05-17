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

package com.android.tools.firebase.testlab.gradle.tasks

import com.android.tools.firebase.testlab.gradle.services.toUrl
import com.google.api.services.storage.model.StorageObject
import java.io.File

/**
 * Handles all extra device files uploaded/tracked by the DSL and the [ExtraDeviceFilesUploadTask]
 *
 * Internally this is handled by a internal map of deviceFile to the Google Cloud Storage link and
 * associated hash value.
 */
class ExtraDeviceFilesManager(deviceFile: File? = null) {

    private data class FileInfo(
        val gsUrl: String,
        val md5Hash: String
    ) {
        override fun toString(): String = "$gsUrl\t$md5Hash"
    }

    private val devicePathToInfo = mutableMapOf<String, FileInfo>()

    init {
        deviceFile?.forEachLine { line ->
            val pair = line.split("\t")
            devicePathToInfo[pair[0]] = FileInfo(
                pair[1],
                pair[2]
            )
        }
    }

    /** Two managers are considered equal if their internal maps are equal */
    override fun equals(other: Any?): Boolean {
        return other != null &&
                other is ExtraDeviceFilesManager &&
                devicePathToInfo == other.devicePathToInfo
    }

    /** Hashing is done via the internal mapping as well */
    override fun hashCode(): Int = devicePathToInfo.hashCode()

    fun add(devicePath: String, storageObject: StorageObject) {
        devicePathToInfo[devicePath] = FileInfo(
            storageObject.toUrl(),
            storageObject.md5Hash
        )
    }

    fun devicePathsToUrls(): Map<String, String> = devicePathToInfo.entries.associate {
        it.key to it.value.gsUrl
    }

    fun toFile(file: File) {
        if (devicePathToInfo.isEmpty()) {
            file.writeText("")
            return
        }

        val outputContents = StringBuilder()
        devicePathToInfo.toSortedMap().forEach { entry ->
            outputContents.appendLine(
                "${entry.key}\t${entry.value}"
            )
        }
        file.writeText(
            outputContents.substring(0, outputContents.length - 1)
        )
    }
}
