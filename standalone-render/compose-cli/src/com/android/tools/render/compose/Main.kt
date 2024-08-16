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
import com.android.sdklib.devices.screenShape
import com.android.tools.preview.applyTo
import com.android.tools.render.RenderRequest
import com.android.tools.render.Renderer
import com.android.tools.rendering.RenderResult
import java.awt.AlphaComposite
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import javax.imageio.ImageIO
import kotlin.io.path.Path

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
    val screenshotResults = mutableListOf<ComposeScreenshotResult>()
    val requestToScreenshotInfo = composeRendering.screenshots.mapNotNull { screenshot ->
        screenshot.toPreviewElement()?.let { previewElement ->
            RenderRequest(previewElement::applyTo) {
                previewElement.resolve().map { it.toPreviewXml().buildString() }
            } to ScreenshotInfo(screenshot.previewId, screenshot.methodFQN)
        }
    }.toMap()

    Renderer(
        composeRendering.fontsPath,
        composeRendering.resourceApkPath,
        composeRendering.namespace,
        composeRendering.classPath,
        composeRendering.projectClassPath,
        composeRendering.layoutlibPath,
    ).use { renderer ->
        renderer.render(requestToScreenshotInfo.keys.asSequence()) { request, config, i, result, usedPaths ->
            val requestToScreenshotInfoValue = requestToScreenshotInfo[request]!!
            val previewId = requestToScreenshotInfoValue.previewId
            val resultId = "${previewId.substringAfterLast(".")}_$i"
            val imageName = "$resultId.png"
            val methodFQN = requestToScreenshotInfoValue.methodFQN
            val relativeImagePath = (methodFQN.substringBeforeLast(".")
                .replace(".", File.separator)) + File.separator + imageName
            val screenshotResult = try {
                val imageRendered = result.renderedImage.copy
                imageRendered?.let { image ->
                    val screenShape =
                        config.device?.screenShape(0.0, 0.0, Dimension(image.width, image.height))
                    val shapedImage = screenShape?.let { shape ->
                        val newImage =
                            BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
                        val g = newImage.createGraphics()
                        try {
                            g.composite = AlphaComposite.Clear
                            g.fillRect(0, 0, image.width, image.height)
                            g.composite = AlphaComposite.Src
                            g.clip = shape
                            g.drawImage(image, 0, 0, null)
                        } finally {
                            g.dispose()
                        }
                        newImage
                    } ?: image
                    val imagePath = Paths.get(composeRendering.outputFolder, relativeImagePath)
                    Files.createDirectories(imagePath.parent)
                    val imgFile = imagePath.toFile()

                    imgFile.createNewFile()
                    ImageIO.write(shapedImage, "png", imgFile)
                }
                val screenshotError =
                    extractError(result, imageRendered, composeRendering.outputFolder)
                ComposeScreenshotResult(previewId, methodFQN, relativeImagePath, screenshotError)
            } catch (t: Throwable) {
                ComposeScreenshotResult(previewId, methodFQN, relativeImagePath, ScreenshotError(t))
            }
            screenshotResults.add(screenshotResult)
            val classesUsed =
                Path(composeRendering.metaDataFolder).resolve("$resultId.classes.txt").toFile()
            classesUsed.writer().use { writer ->
                usedPaths.forEach { path ->
                    writer.write("$path\n")
                }
            }
        }
    }

    ComposeRenderingResult(null, screenshotResults.sortedBy {it.imagePath})
} catch (t: Throwable) {
    ComposeRenderingResult(t.stackTraceToString(), emptyList())
}


private fun extractError(renderResult: RenderResult, imageRendered: BufferedImage?, outputFolder: String): ScreenshotError? {
    if (renderResult.renderResult.status == Result.Status.SUCCESS
        && !renderResult.logger.hasErrors() && imageRendered != null) {
        return null
    }
    val errorMessage = when {
        imageRendered == null && renderResult.renderResult.status == Result.Status.SUCCESS -> "Nothing to render in Preview. Cannot generate image"
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

data class ScreenshotInfo(val previewId: String, val methodFQN: String)
