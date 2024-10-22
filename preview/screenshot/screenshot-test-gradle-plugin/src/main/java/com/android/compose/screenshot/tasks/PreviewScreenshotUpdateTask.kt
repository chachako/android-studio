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

package com.android.compose.screenshot.tasks

import com.android.compose.screenshot.services.AnalyticsService
import com.android.tools.render.compose.ComposeScreenshotResult
import com.android.tools.render.compose.readComposeRenderingResultJson
import com.android.utils.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.util.PatternSet
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.exists

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

    @get:Input
    abstract val filterPattens : ListProperty<String>

    @Option(option = "updateFilter", description = "Filter for the update task")
    fun setUpdateFilter(filterPatterns: List<String>) {
        filterPattens.set(filterPatterns)
    }

    @TaskAction
    fun run() = analyticsService.get().recordTaskAction(path) {
        val resultFile = renderTaskResultFile.get().asFile
        val results = readComposeRenderingResultJson(resultFile.reader()).screenshotResults
        verifyRender(results)
        removeUnusedRefImages()
        if (results.isNotEmpty()) {
            val filteredResults = if (!filterPattens.get().isNullOrEmpty()) {
                results.filter { result ->
                    filterPattens.get().any { pattern ->
                        result.methodFQN.matches(Regex(pattern.replace("*", ".*")))
                    }
                }
            } else results
            for (result in filteredResults) {
                saveReferenceImage(result)
            }
        } else {
            this.logger.lifecycle("No reference images were updated because no previews were found.")
        }
    }

    private fun removeUnusedRefImages() {
        val renderDir = renderTaskOutputDir.get().asFile
        val referenceDir = referenceImageDir.get().asFile

        referenceDir.walkTopDown().forEach { refFile ->
            if (refFile.isFile) {
                val relativePath = refFile.relativeTo(referenceDir).path

                val correspondingRenderFile = File(renderDir, relativePath)
                if (!correspondingRenderFile.exists()) {
                    FileUtils.delete(refFile)
                }
            }
        }

    }

    private fun verifyRender(results: List<ComposeScreenshotResult>) {
        if (results.isNotEmpty()) {
            for (result in results) {
                if (!Paths.get(renderTaskOutputDir.get().asFile.absolutePath, result.imagePath).exists())
                    throw GradleException("Cannot update reference images. Rendering failed for ${result.imagePath.substringBeforeLast(".")}. " +
                            "Error: ${result.error!!.message}. Check ${renderTaskResultFile.get().asFile.absolutePath} for additional info")
            }
        }
    }

    private fun saveReferenceImage(composeScreenshot: ComposeScreenshotResult) {
        val renderedPath = Paths.get(renderTaskOutputDir.get().asFile.absolutePath, composeScreenshot.imagePath)
        if (renderedPath.exists()) {
            if (composeScreenshot.error != null) {
                logger.warn("Rendering preview ${composeScreenshot.imagePath.substringBeforeLast(".")} encountered some problems: ${composeScreenshot.error!!.message}. " +
                        "Check ${renderTaskResultFile.get().asFile.absolutePath} for additional info")
            }

            val referenceImagePath = Paths.get(referenceImageDir.asFile.get().absolutePath, composeScreenshot.imagePath)
            Files.createDirectories(referenceImagePath.parent)
            FileUtils.copyFile(renderedPath.toFile(), referenceImagePath.toFile())
        }
    }
}

