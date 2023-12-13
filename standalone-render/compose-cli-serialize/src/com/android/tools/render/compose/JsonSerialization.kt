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
private const val RESULTS_FILE_NAME = "resultsFileName"

private const val RESULT_ID = "resultId"
private const val GLOBAL_ERROR = "globalError"
private const val SCREENSHOT_RESULTS = "screenshotResults"
private const val IMAGE_PATH = "imagePath"
private const val SCREENSHOT_ERROR = "error"
private const val STATUS = "status"
private const val MESSAGE = "message"
private const val STACKTRACE = "stackTrace"
private const val PROBLEMS = "problems"
private const val HTML = "html"
private const val CLASS_NAME = "className"
private const val BROKEN_CLASSES = "brokenClasses"
private const val MISSING_CLASSES = "missingClasses"

/** Reads JSON text from [jsonReader] containing serialized [ComposeRendering]. */
fun readComposeRenderingJson(jsonReader: Reader): ComposeRendering {
    var sdkPath: String? = null
    var layoutlibPath: String? = null
    var outputFolder: String? = null
    val classPath = mutableListOf<String>()
    var packageName: String? = null
    var resourceApkPath: String? = null
    var screenshots: List<ComposeScreenshot>? = null
    var resultsFileName: String? = null
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
                RESULTS_FILE_NAME -> {
                    resultsFileName = reader.nextString()
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
        screenshots ?: emptyList(),
        resultsFileName ?: "results.json"
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
        writer.name(RESULTS_FILE_NAME).value(composeRendering.resultsFileName)
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

private fun readScreenshotError(reader: JsonReader): ScreenshotError {
    var status: String? = null
    var message: String? = null
    var stackTrace: String? = null
    val problems = mutableListOf<RenderProblem>()
    val brokenClasses = mutableListOf<BrokenClass>()
    val missingClasses = mutableListOf<String>()
    reader.beginObject()
    while (reader.hasNext()) {
        when (reader.nextName()) {
            STATUS -> { status = reader.nextString() }
            MESSAGE -> { message = reader.nextString() }
            STACKTRACE -> { stackTrace = reader.nextString() }
            PROBLEMS -> {
                reader.beginArray()
                while (reader.hasNext()) {
                    reader.beginObject()
                    var html: String? = null
                    var problemStackTrace: String? = null
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            HTML -> { html = reader.nextString() }
                            STACKTRACE -> { problemStackTrace = reader.nextString() }
                        }
                    }
                    reader.endObject()
                    problems.add(
                        RenderProblem(
                            html ?: throw IllegalArgumentException("HTML is missing"),
                            problemStackTrace
                        )
                    )
                }
                reader.endArray()
            }
            BROKEN_CLASSES -> {
                reader.beginArray()
                while (reader.hasNext()) {
                    reader.beginObject()
                    var className: String? = null
                    var classStackTrace: String? = null
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            CLASS_NAME -> { className = reader.nextString() }
                            STACKTRACE -> { classStackTrace = reader.nextString() }
                        }
                    }
                    brokenClasses.add(
                        BrokenClass(
                            className ?: throw IllegalArgumentException("Class name is missing"),
                            classStackTrace
                                ?: throw IllegalArgumentException("Stack trace is missing")
                        )
                    )
                    reader.endObject()
                }
                reader.endArray()
            }
            MISSING_CLASSES -> {
                reader.beginArray()
                while (reader.hasNext()) {
                    missingClasses.add(reader.nextString())
                }
                reader.endArray()
            }
        }
    }
    reader.endObject()
    return ScreenshotError(
        status ?: throw IllegalArgumentException("Status is missing"),
        message ?: throw IllegalArgumentException("Message is missing"),
        stackTrace ?: throw IllegalArgumentException("Stack trace is missing"),
        problems,
        brokenClasses,
        missingClasses
    )
}

private fun readComposeScreenshotResult(reader: JsonReader): ComposeScreenshotResult {
    var imagePath: String? = null
    var screenshotError: ScreenshotError? = null
    var resultId: String? = null
    reader.beginObject()
    while (reader.hasNext()) {
        when (reader.nextName()) {
            RESULT_ID -> { resultId = reader.nextString() }
            IMAGE_PATH -> { imagePath = reader.nextString() }
            SCREENSHOT_ERROR -> {
                screenshotError = readScreenshotError(reader)
            }
        }
    }
    reader.endObject()
    return ComposeScreenshotResult(
        resultId ?: throw IllegalArgumentException("Result ID is missing"),
        imagePath,
        screenshotError
    )
}

/** Reads JSON text from [jsonReader] containing serialized [ComposeRenderingResult]. */
fun readComposeRenderingResultJson(jsonReader: Reader): ComposeRenderingResult {
    var globalError: String? = null
    val screenshotResults = mutableListOf<ComposeScreenshotResult>()
    JsonReader(jsonReader).use { reader ->
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                GLOBAL_ERROR -> {
                    globalError = reader.nextString()
                }

                SCREENSHOT_RESULTS -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        screenshotResults.add(
                            readComposeScreenshotResult(reader)
                        )
                    }
                    reader.endArray()
                }
            }
        }
        reader.endObject()
    }
    return ComposeRenderingResult(globalError, screenshotResults)
}

private fun writeComposeScreenshotResultToJson(
    writer: JsonWriter,
    screenshotResult: ComposeScreenshotResult,
) {
    writer.beginObject()
    writer.name(RESULT_ID).value(screenshotResult.resultId)
    screenshotResult.imagePath?.let {
        writer.name(IMAGE_PATH).value(it)
    }
    screenshotResult.error?.let { screenshotError ->
        writer.name(SCREENSHOT_ERROR)
        writer.beginObject()
        writer.name(STATUS).value(screenshotError.status)
        writer.name(MESSAGE).value(screenshotError.message)
        writer.name(STACKTRACE).value(screenshotError.stackTrace)
        writer.name(PROBLEMS)
        writer.beginArray()
        screenshotError.problems.forEach { problem ->
            writer.beginObject()
            writer.name(HTML).value(problem.html)
            problem.stackTrace?.let {
                writer.name(STACKTRACE).value(it)
            }
            writer.endObject()
        }
        writer.endArray()
        writer.name(BROKEN_CLASSES)
        writer.beginArray()
        screenshotError.brokenClasses.forEach {
            writer.beginObject()
            writer.name(CLASS_NAME).value(it.className)
            writer.name(STACKTRACE).value(it.stackTrace)
            writer.endObject()
        }
        writer.endArray()
        writer.name(MISSING_CLASSES)
        writer.beginArray()
        screenshotError.missingClasses.forEach { writer.value(it) }
        writer.endArray()
        writer.endObject()
    }
    writer.endObject()
}

/** Serializes [ComposeRenderingResult] to a [jsonWriter] in JSON format. */
fun writeComposeRenderingResult(
    jsonWriter: Writer,
    composeRenderingResult: ComposeRenderingResult,
) {
    JsonWriter(jsonWriter).use { writer ->
        writer.setIndent("  ")
        writer.beginObject()
        composeRenderingResult.globalError?.let {
            writer.name(GLOBAL_ERROR).value(it)
        }
        writer.name(SCREENSHOT_RESULTS)
        writer.beginArray()
        composeRenderingResult.screenshotResults.forEach { screenshotResult ->
            writeComposeScreenshotResultToJson(writer, screenshotResult)
        }
        writer.endArray()
        writer.endObject()
    }
}
