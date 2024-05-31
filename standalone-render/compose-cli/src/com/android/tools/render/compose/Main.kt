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

package com.android.tools.render.compose

import com.android.ide.common.rendering.api.Result
import com.android.tools.preview.applyTo
import com.android.tools.render.RenderRequest
import com.android.tools.render.Renderer
import com.android.tools.rendering.RenderResult
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.io.path.Path
import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Path to the Compose rendering settings file is missing.")
        return
    }

    val composeRendering = readComposeRenderingJson(File(args[0]).reader())

    val composeRenderingResult = renderCompose(composeRendering)

    writeComposeRenderingResult(
        File(composeRendering.resultsFilePath).writer(),
        composeRenderingResult,
    )
}

fun renderCompose(composeRendering: ComposeRendering): ComposeRenderingResult = try {
    val r = Renderer.createRenderer(
        composeRendering.fontsPath,
        composeRendering.resourceApkPath,
        composeRendering.namespace,
        composeRendering.classPath,
        composeRendering.projectClassPath,
        composeRendering.layoutlibPath,
    )
    val screenshotResults = mutableListOf<ComposeScreenshotResult>()
    val requestToPreviewId = composeRendering.screenshots.mapNotNull { screenshot ->
        screenshot.toPreviewElement()?.let { previewElement ->
            RenderRequest(previewElement::applyTo) {
                previewElement.resolve().map { it.toPreviewXml().buildString() }
            } to screenshot.previewId
        }
    }.toMap()

    r.use { renderer ->
        renderer.render(requestToPreviewId.keys.asSequence()) { request, i, result, usedPaths ->
            val previewId = requestToPreviewId[request]!!
            val resultId = "${previewId}_$i"
            val imageName = "$resultId.png"
            val screenshotResult = try {
                val imageRendered = result.renderedImage.copy
                imageRendered?.let { image ->
                    val imgFile =
                        Path(composeRendering.outputFolder).resolve("$imageName")
                            .toFile()

                    imgFile.createNewFile()
                    ImageIO.write(image, "png", imgFile)
                }
                val screenshotError = extractError(result, imageRendered, composeRendering.outputFolder)
                ComposeScreenshotResult(previewId, imageName, screenshotError)

            } catch (t: Throwable) {
                ComposeScreenshotResult(previewId, imageName, ScreenshotError(t))
            }
            screenshotResults.add(screenshotResult)
            val classesUsed = Path(composeRendering.metaDataFolder).resolve("$resultId.classes.txt").toFile()
            classesUsed.writer().use { writer ->
                usedPaths.forEach { path ->
                    writer.write("$path\n")
                }
            }
        }
    }
    ComposeRenderingResult(null, screenshotResults.sortedBy {it.imageName})
} catch (t: Throwable) {
    ComposeRenderingResult(t.stackTraceToString(), emptyList())
}


private fun extractError(renderResult: RenderResult, imageRendered: BufferedImage?, outputFolder: String): ScreenshotError? {
    if (renderResult.renderResult.status == Result.Status.SUCCESS
        && !renderResult.logger.hasErrors() && imageRendered != null) {
        return null
    }
    val errorMessage = when {
        imageRendered != null && renderResult.renderResult.status == Result.Status.SUCCESS -> "Nothing to render in Preview. Cannot generate image"
        else -> renderResult.renderResult.errorMessage ?: ""
    }
    return ScreenshotError(
        renderResult.renderResult.status.name,
        errorMessage,
        renderResult.renderResult.exception?.stackTraceToString() ?: "",
        renderResult.logger.messages.map { RenderProblem(it.html, it.throwable?.stackTraceToString()) },
        renderResult.logger.brokenClasses.map { BrokenClass(it.key, it.value.stackTraceToString()) },
        renderResult.logger.missingClasses.toList(),
    )
}
