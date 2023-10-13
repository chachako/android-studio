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

import com.android.tools.preview.applyTo
import com.android.tools.preview.resolve
import com.android.tools.render.RenderRequest
import com.android.tools.render.render
import javax.imageio.ImageIO
import kotlin.io.path.Path

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Path to the Compose rendering settings file is missing.")
        return
    }

    val composeRendering = readComposeRenderingJson(args[0])

    val requestToImageName = composeRendering.screenshots.mapNotNull { screenshot ->
        screenshot.toPreviewElement()?.let { previewElement ->
            RenderRequest(previewElement::applyTo) {
                previewElement.resolve().map { it.toPreviewXml().buildString() }
            } to screenshot.imageName
        }
    }.toMap()

    render(
        composeRendering.sdkPath,
        composeRendering.resourceApkPath,
        composeRendering.packageName,
        composeRendering.classPath,
        composeRendering.layoutlibPath,
        requestToImageName.keys.asSequence(),
    ) { request, i, result  ->
        val image = result.renderedImage.copy!!

        val imgFile =
            Path(composeRendering.outputFolder).resolve("${requestToImageName[request]}_$i.png").toFile()

        imgFile.createNewFile()
        ImageIO.write(image, "png", imgFile)
    }
}
