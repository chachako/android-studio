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
import com.android.tools.render.compose.agp.obtainPreviewsAndSerialize
import com.android.tools.render.compose.rubish.RenderDependencies.Companion.readFromDump
import com.android.tools.render.render
import java.io.File

fun main(args: Array<String>) {
    // This is to be removed completely once AGP starts producing Json with the correct format
    val settingsPath = if (args.isEmpty()) "rendering_settings.txt" else args[0]
    val renderSettings = File(settingsPath).readLines().map { it.split(":") }.associate { it[0] to it[1] }
    val layoutlibPath = renderSettings["layoutlib"]!!
    val sdkPath = renderSettings["sdk"]!!
    val outputFolder = renderSettings["output"]!!

    val renderDeps = readFromDump(renderSettings["dependencies"]!!)

    val classPath = renderDeps.classesJars + listOf(renderDeps.mainClasses)
    val packageName = renderDeps.packageName
    val resourceApkPath = renderDeps.resourcesApk

    val inputJsonFile = "json.txt"

    // This should be moved to the AGP
    obtainPreviewsAndSerialize(
        classPath,
        sdkPath,
        layoutlibPath,
        outputFolder,
        packageName,
        resourceApkPath,
        inputJsonFile
    )

    // This is the first thing we should actually do in the compose-cli
    val composeRendering = readComposeRenderingJson(inputJsonFile)

    val renderRequests = composeRendering.screenshots.mapNotNull { screenshot ->
        screenshot.toPreviewElement()?.let { previewElement ->
            RenderRequest(previewElement::applyTo, screenshot.imageName) {
                previewElement.resolve().map { it.toPreviewXml().buildString() }
            }
        }
    }.asSequence()

    render(
        composeRendering.sdkPath,
        composeRendering.resourceApkPath,
        composeRendering.packageName,
        composeRendering.classPath,
        composeRendering.layoutlibPath,
        renderRequests,
        composeRendering.outputFolder,
    )
}
