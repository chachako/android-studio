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

import com.android.tools.firebase.testlab.gradle.services.TestLabBuildService
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import com.google.api.services.storage.model.StorageObject
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import kotlin.text.StringBuilder

/**
 * Handles the upload of local files and the validation of files in cloud storage
 */
abstract class ExtraDeviceFilesUploadTask: DefaultTask() {

    /**
     * This task is never assumed to be up-to-date as cloud storage files specified
     * in the DSL might have been modified. Or the contents in local files may have changed
     * and need to be revalidated.
     */
    init {
        outputs.upToDateWhen { false }
    }

    @get: Input
    abstract val extraFiles: MapProperty<String, String>

    @get: Input
    abstract val projectPath: Property<String>

    @get: Internal
    abstract val buildService: Property<TestLabBuildService>

    @get: OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun validateOrUploadExtraFiles() {

        val errorMessage = StringBuilder()
        val extraDeviceFiles = ExtraDeviceFilesManager()

        val filePathToStorage = mutableMapOf<String, StorageObject>()

        extraFiles.get().forEach { devicePath, filePath ->
            val storageObject = when {
                filePathToStorage.containsKey(filePath) -> filePathToStorage[filePath]
                isGoogleStorageLink(filePath) -> {
                    buildService.get().getStorageObject(filePath).also {
                        it ?: errorMessage.appendLine(
                            "    Google Storage link: $filePath does not reference a valid Storage " +
                                    "Object."
                        )
                    }
                }
                else -> {
                    // Try as a local file.
                    val file = File(filePath)
                    when {
                        !file.exists() -> {
                            errorMessage.appendLine(
                                "    Local file path: $filePath does not exist. Cannot upload file."
                            )
                            null
                        }
                        !file.isFile -> {
                            errorMessage.appendLine(
                                "    Local file path: $filePath must be a file. Cannot be uploaded."
                            )
                            null
                        }
                        else -> {
                            buildService.get().uploadSharedFile(projectPath.get(), file)
                        }
                    }
                }
            }

            storageObject?.apply {
                extraDeviceFiles.add(devicePath, this)
            }
        }
        if (errorMessage.isNotEmpty()) {
            error("Could not upload extraDeviceFiles for Firebase Test Lab.\n" +
                    errorMessage.toString())
        }

        extraDeviceFiles.toFile(outputFile.get().asFile)
    }

    private fun isGoogleStorageLink(filePath: String) =
        filePath.startsWith("gs://")
}
