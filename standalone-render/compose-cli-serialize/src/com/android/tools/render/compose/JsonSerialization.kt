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

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.Reader
import java.io.Writer

private const val SDK_PATH = "sdkPath"
private const val LAYOUTLIB_PATH = "layoutlibPath"
private const val OUTPUT_FOLDER = "outputFolder"
private const val CLASS_PATH = "classPath"
private const val PACKAGE_NAME = "packageName"
private const val RESOURCE_APK_PATH = "resourceApkPath"
private const val SCREENSHOTS = "screenshots"
private const val METHOD_FQN = "methodFQN"
private const val METHOD_PARAMS = "methodParams"
private const val IMAGE_NAME = "imageName"
private const val PREVIEW_PARAMS = "previewParams"

/** Reads JSON text from [jsonReader] containing serialized [ComposeRendering]. */
fun readComposeRenderingJson(jsonReader: Reader): ComposeRendering {
    var sdkPath: String? = null
    var layoutlibPath: String? = null
    var outputFolder: String? = null
    val classPath = mutableListOf<String>()
    var packageName: String? = null
    var resourceApkPath: String? = null
    var screenshots: List<ComposeScreenshot>? = null
    JsonReader(jsonReader).use {  reader ->
        reader.beginObject()
        while (reader.hasNext()) {
            when (val fieldName = reader.nextName()) {
                SDK_PATH -> { sdkPath = reader.nextString() }
                LAYOUTLIB_PATH -> { layoutlibPath = reader.nextString() }
                OUTPUT_FOLDER -> { outputFolder = reader.nextString() }
                CLASS_PATH -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        classPath.add(reader.nextString())
                    }
                    reader.endArray()
                }
                PACKAGE_NAME -> { packageName = reader.nextString() }
                RESOURCE_APK_PATH -> { resourceApkPath = reader.nextString() }
                SCREENSHOTS -> {
                    screenshots = readComposeScreenshots(reader)
                }
                else -> {
                    val fieldValue = reader.nextString()
                    println("$fieldName $fieldValue")
                }
            }
        }

        reader.endObject()
    }

    return ComposeRendering(
        sdkPath ?: throw IllegalArgumentException("SDK path is missing"),
        layoutlibPath ?: throw IllegalArgumentException("Layoutlib path is missing"),
        outputFolder ?: throw IllegalArgumentException("Output folder path is missing"),
        classPath,
        packageName ?: throw IllegalArgumentException("Package name"),
        resourceApkPath ?: throw IllegalArgumentException("Resource APK path is missing"),
        screenshots ?: emptyList()
    )
}

private fun readComposeScreenshot(reader: JsonReader): ComposeScreenshot {
    var methodFQN: String? = null
    val methodParams = mutableListOf<Map<String, String>>()
    var imageName: String? = null
    val previewParams = mutableMapOf<String, String>()
    reader.beginObject()
    while (reader.hasNext()) {
        when (reader.nextName()) {
            METHOD_FQN -> { methodFQN = reader.nextString() }
            METHOD_PARAMS -> {
                reader.beginArray()
                while (reader.hasNext()) {
                    reader.beginObject()
                    val methodParam = mutableMapOf<String, String>()
                    while (reader.hasNext()) {
                        methodParam[reader.nextName()] = reader.nextString()
                    }
                    methodParams.add(methodParam)
                    reader.endObject()
                }
                reader.endArray()
            }
            IMAGE_NAME -> { imageName = reader.nextString() }
            PREVIEW_PARAMS -> {
                reader.beginObject()
                while (reader.hasNext()) {
                    previewParams[reader.nextName()] = reader.nextString()
                }
                reader.endObject()
            }
        }
    }
    reader.endObject()
    return ComposeScreenshot(
        methodFQN ?: throw IllegalArgumentException("FQN of a method is missing"),
        methodParams,
        previewParams,
        imageName ?: throw IllegalArgumentException("Output image name is missing")
    )
}

private fun readComposeScreenshots(reader: JsonReader): List<ComposeScreenshot> {
    val screenshots = mutableListOf<ComposeScreenshot>()
    reader.beginArray()
    while (reader.hasNext()) {
        screenshots.add(readComposeScreenshot(reader))
    }
    reader.endArray()
    return screenshots
}

/** Reads a list of [ComposeScreenshot] from a [JsonReader]. */
fun readComposeScreenshotsJson(jsonReader: Reader): List<ComposeScreenshot> {
    return JsonReader(jsonReader).use { reader ->
        reader.beginObject()
        val screenshotsEntryName = reader.nextName()
        if (screenshotsEntryName != SCREENSHOTS) {
            throw IllegalArgumentException("$SCREENSHOTS entry is missing")
        }
        val results = readComposeScreenshots(reader)
        reader.endObject()
        results
    }
}

/** Serializes [composeRendering] to [jsonWriter] in JSON format. */
fun writeComposeRenderingToJson(
    jsonWriter: Writer,
    composeRendering: ComposeRendering,
) {
    JsonWriter(jsonWriter).use { writer ->
        writer.setIndent("  ")
        writer.beginObject()
        writer.name(SDK_PATH).value(composeRendering.sdkPath)
        writer.name(LAYOUTLIB_PATH).value(composeRendering.layoutlibPath)
        writer.name(OUTPUT_FOLDER).value(composeRendering.outputFolder)
        writer.name(CLASS_PATH)
        writer.beginArray()
        composeRendering.classPath.forEach { writer.value(it) }
        writer.endArray()
        writer.name(PACKAGE_NAME).value(composeRendering.packageName)
        writer.name(RESOURCE_APK_PATH).value(composeRendering.resourceApkPath)
        writer.name(SCREENSHOTS)
        writeComposeScreenshots(writer, composeRendering.screenshots)
        writer.endObject()
    }
}

private fun writeComposeScreenshot(writer: JsonWriter, screenshot: ComposeScreenshot) {
    writer.beginObject()
    writer.name(METHOD_FQN).value(screenshot.methodFQN)
    writer.name(METHOD_PARAMS)
    writer.beginArray()
    screenshot.methodParams.forEach { param ->
        writer.beginObject()
        param.forEach {
            writer.name(it.key).value(it.value)
        }
        writer.endObject()
    }
    writer.endArray()
    writer.name(PREVIEW_PARAMS)
    writer.beginObject()
    screenshot.previewParams.forEach {
        writer.name(it.key).value(it.value)
    }
    writer.endObject()
    writer.name(IMAGE_NAME).value(screenshot.imageName)
    writer.endObject()
}

private fun writeComposeScreenshots(writer: JsonWriter, screenshots: List<ComposeScreenshot>) {
    writer.beginArray()
    screenshots.forEach {
        writeComposeScreenshot(writer, it)
    }
    writer.endArray()
}

/** Writes a list of [ComposeScreenshot] to a [Writer] as a json array. */
fun writeComposeScreenshotsToJson(
    jsonWriter: Writer,
    screenshots: List<ComposeScreenshot>
) {
    JsonWriter(jsonWriter).use { writer ->
        writer.setIndent("  ")
        writer.beginObject()
        writer.name(SCREENSHOTS)
        writeComposeScreenshots(writer, screenshots)
        writer.endObject()
    }
}
