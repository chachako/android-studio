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
import java.io.File

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

/** Reads Json file containing serialized [ComposeRendering]. */
fun readComposeRenderingJson(jsonFileName: String): ComposeRendering {
    val inputFile = File(jsonFileName)

    var sdkPath: String? = null
    var layoutlibPath: String? = null
    var outputFolder: String? = null
    val classPath = mutableListOf<String>()
    var packageName: String? = null
    var resourceApkPath: String? = null
    val screenshots = mutableListOf<ComposeScreenshot>()
    JsonReader(inputFile.reader()).use {  reader ->
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
                    reader.beginArray()
                    while (reader.hasNext()) {
                        screenshots.add(readComposeScreenshot(reader))
                    }
                    reader.endArray()
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
        screenshots
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
