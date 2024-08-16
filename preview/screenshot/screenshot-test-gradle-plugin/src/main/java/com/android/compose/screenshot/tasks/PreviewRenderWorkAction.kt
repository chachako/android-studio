/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.render.compose.readComposeRenderingJson
import com.android.tools.render.compose.readComposeRenderingResultJson
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.nio.file.Paths
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.io.path.exists

abstract class PreviewRenderWorkAction: WorkAction<PreviewRenderWorkAction.RenderWorkActionParameters> {
    companion object {
        private const val MAIN_CLASS = "com.android.tools.render.compose.MainKt"
        private val logger: Logger = Logger.getLogger(PreviewRenderWorkAction::class.qualifiedName)
    }
    abstract class RenderWorkActionParameters : WorkParameters {
        abstract val cliToolArgumentsFile: RegularFileProperty
        abstract val resultsFile: RegularFileProperty
    }

    override fun execute() {
        render()
        verifyRender()
    }

    private fun render() {
        Class.forName(MAIN_CLASS).getMethod("main", Array<String>::class.java)(
            null, arrayOf(parameters.cliToolArgumentsFile.asFile.get().absolutePath))
    }

    private fun verifyRender() {
        val resultFile = parameters.resultsFile.get().asFile
        if (!resultFile.exists()) {
            throw GradleException(
                "There was an error with the rendering process. " +
                    "Unable to open the rendering result file from ${resultFile.absolutePath}")
        }

        val composeRenderingResult = readComposeRenderingResultJson(resultFile.reader())
        val outputFolder = readComposeRenderingJson(parameters.cliToolArgumentsFile.get().asFile.reader()).outputFolder

        val hasAtLeastOneRendering = composeRenderingResult.screenshotResults.any {
            Paths.get(outputFolder, it.imagePath).exists()
        }
        if (!hasAtLeastOneRendering) {
            throw GradleException(
                "Rendering failed. For more details, check ${resultFile.absolutePath}")
        }

        val hasRenderingErrors = composeRenderingResult.screenshotResults.any { it.error != null }
        if (hasRenderingErrors || composeRenderingResult.globalError != null) {
            logger.log(
                Level.WARNING,
                "There were some issues with rendering one or more previews. " +
                    "For more details, check ${resultFile.absolutePath}"
            )
        }
    }
}
