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
import com.android.tools.configurations.Configuration
import com.android.tools.preview.applyTo
import com.android.tools.render.RenderRequest
import com.android.tools.render.Renderer
import com.android.tools.render.framework.IJFramework
import com.android.tools.rendering.RenderResult
import com.intellij.openapi.util.Disposer
import java.awt.AlphaComposite
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import javax.imageio.ImageIO

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Path to the Compose rendering settings file is missing.")
        return
    }
    try {
        renderCompose(File(args[0]))
    } finally {
        Disposer.dispose(IJFramework)
    }
}

fun renderCompose(composeRenderingJson: File) {
    val composeRendering = readComposeRenderingJson(composeRenderingJson.reader())
    val composeRenderingResult = try {
        renderCompose(composeRendering)
    } catch (t: Throwable) {
        ComposeRenderingResult(t.stackTraceToString(), emptyList())
    }

    writeComposeRenderingResult(
        File(composeRendering.resultsFilePath).writer(),
        composeRenderingResult,
    )
}

fun renderCompose(composeRendering: ComposeRendering): ComposeRenderingResult {
    return Renderer(
        composeRendering.fontsPath,
        composeRendering.resourceApkPath,
        composeRendering.namespace,
        composeRendering.classPath,
        composeRendering.projectClassPath,
        composeRendering.layoutlibPath,
    ).use { renderer ->
        val screenshotResults = composeRendering.screenshots.flatMap {
            render(it, composeRendering.outputFolder, renderer)
        }.sortedBy { it.imagePath }
        ComposeRenderingResult(globalError = null, screenshotResults)
    }
}

private fun render(screenshot: ComposeScreenshot, outputFolderPath: String, renderer: Renderer):
        Sequence<ComposeScreenshotResult> {
    val previewElement = screenshot.toPreviewElement()
    val renderRequest = RenderRequest(
        configurationModifier = previewElement::applyTo,
        xmlLayoutsProvider = {
            previewElement.resolve().map { it.toPreviewXml().buildString() }
        }
    )

    return renderer.render(renderRequest).withIndex().map { (index, value) ->
        val (config, renderResult) = value
        val previewId = screenshot.previewId
        val resultId = "${previewId.substringAfterLast(".")}_$index"
        val imageName = "$resultId.png"
        val methodFQN = screenshot.methodFQN
        val relativeImagePath = methodFQN.substringBeforeLast(".")
            .replace(".", File.separator) + File.separator + imageName
        val screenshotResult = try {
            val imageRendered = postProcessRenderedImage(config, renderResult)
            if (imageRendered != null) {
                val imagePath = Paths.get(outputFolderPath, relativeImagePath)
                Files.createDirectories(imagePath.parent)
                val imgFile = imagePath.toFile()
                imgFile.createNewFile()
                ImageIO.write(imageRendered, "png", imgFile)
            }

            val screenshotError = extractError(renderResult, imageRendered)
            ComposeScreenshotResult(previewId, methodFQN, relativeImagePath, screenshotError)
        } catch (t: Throwable) {
            ComposeScreenshotResult(previewId, methodFQN, relativeImagePath, ScreenshotError(t))
        }
        screenshotResult
    }
}

private fun postProcessRenderedImage(config: Configuration, renderResult: RenderResult): BufferedImage? {
    val imageCopy = renderResult.renderedImage.copy
    if (imageCopy == null && renderResult.renderResult.status != Result.Status.SUCCESS) return null

    val image = imageCopy ?: BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    val screenShape = config.device?.screenShape(0.0, 0.0, Dimension(image.width, image.height))
        ?: return image
    return resizeImage(image, screenShape)
}

private fun resizeImage(image: BufferedImage, shape: java.awt.Shape): BufferedImage {
    val newImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
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
    return newImage
}

private fun extractError(renderResult: RenderResult, imageRendered: BufferedImage?): ScreenshotError? {
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
