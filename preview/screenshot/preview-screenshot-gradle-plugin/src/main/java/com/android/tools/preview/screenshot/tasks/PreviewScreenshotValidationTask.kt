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
import com.android.tools.render.compose.readComposeScreenshotsJson
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask
import org.gradle.api.tasks.testing.Test

/**
 * Runs screenshot tests of a variant.
 */
@CacheableTask
abstract class PreviewScreenshotValidationTask : Test(), VerificationTask {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val referenceImageDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val renderTaskOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val diffImageDir: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val previewFile: RegularFileProperty

    @get:OutputFile
    abstract val resultsFile: RegularFileProperty

    @get:Internal
    abstract val reportFilePath: DirectoryProperty

    @get:Internal
    abstract val analyticsService: Property<AnalyticsService>

    @TaskAction
    fun run() = analyticsService.get().recordTaskAction(path) {
        val screenshots = readComposeScreenshotsJson(previewFile.get().asFile.reader())
        if (screenshots.isNotEmpty()) {
            analyticsService.get().recordPreviewScreenshotTestRun(
                totalTestCount = screenshots.size,
            )
        }
    }

    override fun createTestExecutionSpec(): JvmTestExecutionSpec {
        setTestEngineParam("previews-discovered", previewFile.get().asFile.absolutePath)
        setTestEngineParam("referenceImageDirPath", referenceImageDir.get().asFile.absolutePath)
        setTestEngineParam("diffImageDirPath", diffImageDir.get().asFile.absolutePath)
        setTestEngineParam("renderTaskOutputDirPath", renderTaskOutputDir.get().asFile.absolutePath)
        setTestEngineParam("resultsFilePath", resultsFile.get().asFile.absolutePath)
        setTestEngineParam("reportUrlPath", reportFilePath.get().asFile.absolutePath)
        return super.createTestExecutionSpec()
    }
}

fun PreviewScreenshotValidationTask.setTestEngineParam(key: String, value: String) {
    jvmArgs("-Dcom.android.tools.preview.screenshot.junit.engine.${key}=${value}")
}
