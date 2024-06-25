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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.nio.file.Paths
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.io.path.exists

abstract class PreviewRenderWorkAction: WorkAction<PreviewRenderWorkAction.RenderWorkActionParameters> {
    companion object {
        private const val MAIN_CLASS = "com.android.tools.render.compose.MainKt"
        private val logger: Logger = Logger.getLogger(PreviewRenderWorkAction::class.qualifiedName)
    }
    abstract class RenderWorkActionParameters : WorkParameters {
        abstract val jvmArgs: ListProperty<String>
        abstract val layoutlibJar: ConfigurableFileCollection
        abstract val cliToolArgumentsFile: RegularFileProperty
        abstract val toolJarPath: ConfigurableFileCollection
        abstract val resultsFile: RegularFileProperty
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun execute() {
        render()
        verifyRender()
    }

    private fun render() {
        execOperations.javaexec { spec ->
            spec.mainClass.set(MAIN_CLASS)
            spec.classpath = parameters.layoutlibJar + parameters.toolJarPath
            spec.jvmArgs = parameters.jvmArgs.get()
            spec.args = listOf(parameters.cliToolArgumentsFile.asFile.get().absolutePath)
        }.rethrowFailure().assertNormalExitValue()
    }

    private fun verifyRender() {
        val resultFile = parameters.resultsFile.get().asFile
        if (!resultFile.exists()) {
            throw GradleException("There was an error with the rendering process.")
        }
        val composeRenderingResult = readComposeRenderingResultJson(resultFile.reader())
        val outputFolder = readComposeRenderingJson(parameters.cliToolArgumentsFile.get().asFile.reader()).outputFolder

        val renderingErrors =
            composeRenderingResult.screenshotResults.count {
                !Paths.get(outputFolder, it.imagePath).exists() || (it.error != null && it.error!!.status != "SUCCESS")
            }
        if (composeRenderingResult.globalError != null || renderingErrors > 0) {
            throw GradleException("Rendering failed for one or more previews. For more details, check ${resultFile.absolutePath}")
        }
        val renderWarnings = composeRenderingResult.screenshotResults.count { it.error != null }
        if (renderWarnings > 0) {
            logger.log(
                Level.WARNING,
                "There were some issues with rendering one or more previews. For more details, check ${resultFile.absolutePath}"
            )
        }
    }
}
