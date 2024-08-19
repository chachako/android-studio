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
import com.android.tools.render.compose.readComposeScreenshotsJson
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test

/**
 * Runs screenshot tests of a variant.
 */
@CacheableTask
abstract class PreviewScreenshotValidationTask : Test() {
    @get:Optional
    @get:InputFiles // using InputFiles to allow nonexistent reference image directory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val referenceImageDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val renderTaskOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val diffImageDir: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val previewFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val renderTaskOutputFile: RegularFileProperty

    @get:OutputDirectory
    abstract val resultsDir: DirectoryProperty

    @get:Internal
    abstract val analyticsService: Property<AnalyticsService>

    @get:Input
    abstract val threshold: Property<Float>

    @TaskAction
    override fun executeTests() {
        analyticsService.get().recordTaskAction(path) {
            val screenshots = readComposeScreenshotsJson(previewFile.get().asFile.reader())
            if (screenshots.isNotEmpty()) {
                analyticsService.get().recordPreviewScreenshotTestRun(
                    totalTestCount = screenshots.size,
                )
            }
            if (referenceImageDir.orNull?.asFile?.exists() != true){
                throw GradleException("Reference images missing. Please run the update<variant>ScreenshotTest task to generate the reference images.")
            }
            setTestEngineParam("previews-discovered", previewFile.get().asFile.absolutePath)
            setTestEngineParam("referenceImageDirPath", referenceImageDir.get().asFile.absolutePath)
            setTestEngineParam("diffImageDirPath", diffImageDir.get().asFile.absolutePath)
            setTestEngineParam("renderResultsFilePath", renderTaskOutputFile.get().asFile.absolutePath)
            setTestEngineParam("renderTaskOutputDir", renderTaskOutputDir.get().asFile.absolutePath)
            setTestEngineParam("resultsDirPath", resultsDir.get().asFile.absolutePath)
            threshold.orNull?.let {
                validateFloat(it)
                setTestEngineParam("threshold", it.toString())
            }
            super.executeTests()
        }
    }

    private fun validateFloat(value: Float) {
        if (value < 0 || value > 1) {
            throw GradleException("Invalid threshold provided. Please provide a float value between 0.0 and 1.0")
        }
    }
}

fun PreviewScreenshotValidationTask.setTestEngineParam(key: String, value: String) {
    jvmArgs("-Dcom.android.tools.preview.screenshot.junit.engine.${key}=${value}")
}
