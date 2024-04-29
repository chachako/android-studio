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

package com.android.tools.preview.screenshot.tasks

import com.android.tools.preview.screenshot.services.AnalyticsService
import com.android.tools.render.compose.ComposeScreenshotResult
import com.android.tools.render.compose.readComposeRenderingResultJson
import com.android.utils.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Update reference images of a variant.
 */
@CacheableTask
abstract class PreviewScreenshotUpdateTask : DefaultTask() {

    @get:OutputDirectory
    abstract val referenceImageDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val renderTaskOutputDir: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val renderTaskResultFile: RegularFileProperty

    @get:Internal
    abstract val analyticsService: Property<AnalyticsService>

    @TaskAction
    fun run() = analyticsService.get().recordTaskAction(path) {
        FileUtils.cleanOutputDir(referenceImageDir.get().asFile)
        //throw exception at the first encountered error
        val resultFile = renderTaskResultFile.get().asFile
        val results = readComposeRenderingResultJson(resultFile.reader()).screenshotResults
        if (results.isNotEmpty()) {
            for (result in results) {
                saveReferenceImage(result)
            }
        } else {
            this.logger.lifecycle("No reference images were updated because no previews were found.")
        }
    }

    private fun saveReferenceImage(composeScreenshot: ComposeScreenshotResult) {

        val renderedFile = File(composeScreenshot.imagePath!!)
        if (composeScreenshot.error != null) {
            if (!renderedFile.exists()) {
                throw GradleException("Rendering failed for ${composeScreenshot.resultId}. Error: ${composeScreenshot.error!!.message}. Check ${renderTaskResultFile.get().asFile.absolutePath} for additional info")
            } else {
                logger.warn("Rendering preview ${composeScreenshot.resultId} encountered some problems: ${composeScreenshot.error!!.message}. Check ${renderTaskResultFile.get().asFile.absolutePath} for additional info")
            }
        }
        val referenceImagePath = referenceImageDir.asFile.get().toPath().resolve("${composeScreenshot.resultId}.png")
        FileUtils.copyFile(renderedFile, referenceImagePath.toFile())
    }
}
