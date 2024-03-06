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

package com.android.tools.preview.screenshot.tasks

import com.android.tools.render.compose.readComposeRenderingResultJson
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.net.URLClassLoader
import java.util.logging.Level
import java.util.logging.Logger

abstract class PreviewRenderWorkAction: WorkAction<PreviewRenderWorkAction.RenderWorkActionParameters> {
    companion object {
        const val MAIN_METHOD = "main"
        const val MAIN_CLASS = "com.android.tools.render.compose.MainKt"
        val logger = Logger.getLogger(PreviewRenderWorkAction::class.qualifiedName)
    }
    abstract class RenderWorkActionParameters : WorkParameters {
        abstract val cliToolArgumentsFile: RegularFileProperty
        abstract val toolJarPath: ConfigurableFileCollection
        abstract val outputDir: DirectoryProperty
    }

    override fun execute() {
        render()
        verifyRender()
    }

    private fun render() {
        val classLoader = getClassloader(parameters.toolJarPath)
        invokeMainMethod(listOf(parameters.cliToolArgumentsFile.get().asFile.absolutePath), classLoader)
    }

    private fun invokeMainMethod(arguments: List<String>, classLoader: ClassLoader) {
        val cls = classLoader.loadClass(MAIN_CLASS)
        val method = cls.getMethod(MAIN_METHOD, Array<String>::class.java)
        method.invoke(null, arguments.toTypedArray())
    }

    private fun getClassloader(classpath: FileCollection): ClassLoader {
        return URLClassLoader(classpath.files.map { it.toURI().toURL() }.toTypedArray())
    }

    private fun verifyRender() {
        val resultFile = parameters.outputDir.file("results.json").get().asFile
        if (!resultFile.exists()) {
            throw GradleException("There was an error with the rendering process.")
        }
        val composeRenderingResult = readComposeRenderingResultJson(resultFile.reader())
        val renderingErrors =
            composeRenderingResult.screenshotResults.count { it.imagePath == null && it.error != null && it.error!!.status != "SUCCESS" }
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
