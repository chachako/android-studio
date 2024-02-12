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
import com.android.tools.render.render
import com.android.tools.rendering.RenderResult
import javax.imageio.ImageIO
import kotlin.io.path.Path
import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Path to the Compose rendering settings file is missing.")
        return
    }

    val composeRendering = readComposeRenderingJson(File(args[0]).reader())

    val composeRenderingResult = try {
        val screenshotResults = mutableListOf<ComposeScreenshotResult>()
        val requestToImageName = composeRendering.screenshots.mapNotNull { screenshot ->
            screenshot.toPreviewElement()?.let { previewElement ->
                RenderRequest(previewElement::applyTo) {
                    previewElement.resolve().map { it.toPreviewXml().buildString() }
                } to screenshot.imageName
            }
        }.toMap()

        render(
            composeRendering.fontsPath,
            composeRendering.resourceApkPath,
            composeRendering.packageName,
            composeRendering.classPath,
            composeRendering.layoutlibPath,
            requestToImageName.keys.asSequence(),
        ) { request, i, result ->
            val resultId = "${requestToImageName[request]}_$i"
            val screenshotResult = try {
                val imagePath = result.renderedImage.copy?.let { image ->
                    val imgFile =
                        Path(composeRendering.outputFolder).resolve("$resultId.png")
                            .toFile()

                    imgFile.createNewFile()
                    ImageIO.write(image, "png", imgFile)
                    imgFile.absolutePath
                }
                val screenshotError = extractError(result)
                ComposeScreenshotResult(resultId, imagePath, screenshotError)
            } catch (t: Throwable) {
                ComposeScreenshotResult(resultId, null, ScreenshotError(t))
            }
            screenshotResults.add(screenshotResult)
        }
        ComposeRenderingResult(null, screenshotResults)
    } catch (t: Throwable) {
        ComposeRenderingResult(t.stackTraceToString(), emptyList())
    }

    writeComposeRenderingResult(
        Path(composeRendering.outputFolder).resolve(composeRendering.resultsFileName).toFile().writer(),
        composeRenderingResult,
    )
}

private fun extractError(renderResult: RenderResult): ScreenshotError? {
    if (renderResult.renderResult.status == Result.Status.SUCCESS
        && !renderResult.logger.hasErrors()) {
        return null
    }
    return ScreenshotError(
        renderResult.renderResult.status.name,
        renderResult.renderResult.errorMessage ?: "",
        renderResult.renderResult.exception?.stackTraceToString() ?: "",
        renderResult.logger.messages.map { RenderProblem(it.html, it.throwable?.stackTraceToString()) },
        renderResult.logger.brokenClasses.map { BrokenClass(it.key, it.value.stackTraceToString()) },
        renderResult.logger.missingClasses.toList(),
    )
}
