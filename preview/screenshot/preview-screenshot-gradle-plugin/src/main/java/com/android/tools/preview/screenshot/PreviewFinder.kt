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

package com.android.tools.preview.screenshot

import com.android.tools.preview.multipreview.BaseAnnotationRepresentation
import com.android.tools.preview.multipreview.MethodRepresentation
import com.android.tools.preview.multipreview.MultipreviewSettings
import com.android.tools.preview.multipreview.buildMultipreview
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.objectweb.asm.Type
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

private const val SDK_PATH = "sdkPath"
private const val LAYOUTLIB_PATH = "layoutlibPath"
private const val OUTPUT_FOLDER = "outputFolder"
private const val CLASS_PATH = "classPath"
private const val RESOURCE_APK_PATH = "resourceApkPath"
private const val SCREENSHOTS = "screenshots"
private const val METHOD_FQN = "methodFQN"
private const val METHOD_PARAMS = "methodParams"
private const val IMAGE_NAME = "imageName"
private const val PREVIEW_PARAMS = "previewParams"

fun configureInput (
    classPath: List<String>,
    sdkPath: String,
    layoutlibPath: String,
    outputFolder: String,
    resourceApkPath: String,
    cliToolInputFilePath: Path,
    previewsFile: File
) {
    try {
        val fileWriter = Files.newBufferedWriter(cliToolInputFilePath)
        JsonWriter(fileWriter).use { writer ->
            writer.setIndent("  ")
            writer.beginObject()
            writer.name(SDK_PATH).value(sdkPath)
            writer.name(LAYOUTLIB_PATH).value(layoutlibPath)
            writer.name(OUTPUT_FOLDER).value(outputFolder)
            writer.name(CLASS_PATH)
            writer.beginArray()
            classPath.forEach { writer.value(it) }
            writer.endArray()
            //writer.name(PACKAGE_NAME).value(packageName)
            writer.name(RESOURCE_APK_PATH).value(resourceApkPath)
            writer.name(SCREENSHOTS)
            writer.beginArray()
            JsonReader(previewsFile.reader()).use { reader ->
                reader.beginObject()
                if (reader.hasNext()) {
                    if (reader.nextName() == SCREENSHOTS) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            writer.beginObject()
                            reader.beginObject()
                            while(reader.hasNext()) {
                                when (reader.nextName()) {
                                    METHOD_FQN -> { writer.name(METHOD_FQN).value(reader.nextString()) }
                                    METHOD_PARAMS -> {
                                        reader.beginArray()
                                        writer.name(METHOD_PARAMS)
                                        writer.beginArray()
                                        while (reader.hasNext()) {
                                            reader.beginObject()
                                            writer.beginObject()
                                            while (reader.hasNext()) {
                                                writer.name(reader.nextName()).value(reader.nextString())
                                            }
                                            reader.endObject()
                                            writer.endObject()
                                        }
                                        reader.endArray()
                                        writer.endArray()
                                    }
                                    IMAGE_NAME -> { writer.name(IMAGE_NAME).value(reader.nextString()) }
                                    PREVIEW_PARAMS -> {
                                        reader.beginObject()
                                        writer.name(PREVIEW_PARAMS)
                                        writer.beginObject()
                                        while (reader.hasNext()) {
                                            writer.name(reader.nextName()).value(reader.nextString())
                                        }
                                        reader.endObject()
                                        writer.endObject()
                                    }
                                }
                            }
                            writer.endObject()
                            reader.endObject()
                        }
                        reader.endArray()
                    }
                }
                reader.endObject()
            }
            writer.endArray()
            writer.endObject()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        throw e
    }

}
fun findPreviewsAndSerialize(classPath: List<String>, outputFile: Path) {
    val settings = MultipreviewSettings(
        "androidx.compose.ui.tooling.preview.Preview",
        "androidx.compose.ui.tooling.preview.PreviewParameter"
    )

    val multipreview = buildMultipreview(settings, classPath) {
        getClassName(it).endsWith("Test")
    }

    val previews =  multipreview.methods.flatMap { method ->
        multipreview.getAnnotations(method).map { baseAnnotation ->
            method to baseAnnotation
        }
    }.asSequence()

    serializePreviews(previews, outputFile)
}

fun getClassName(fqcn: String): String {
    return fqcn.substring(0, fqcn.lastIndexOf(PERIOD))
}

fun serializePreviews(
    previews: Sequence<Pair<MethodRepresentation, BaseAnnotationRepresentation>>,
    outputFile: Path
) {
    val fileWriter = Files.newBufferedWriter(outputFile)
    JsonWriter(fileWriter).use { writer ->
        writer.setIndent("  ")
        writer.beginObject()
        writer.name(SCREENSHOTS)
        writer.beginArray()
        previews.forEach { (method, annotation) ->
            writer.beginObject()
            writer.name(METHOD_FQN).value(method.methodFqn)
            writer.name(METHOD_PARAMS)
            writer.beginArray()
            method.parameters.forEach { param ->
                writer.beginObject()
                param.annotationParameters.forEach {
                    // Special case for provider since we want FQCN and not toString()
                    if (it.key == "provider") {
                        (it.value as Type).className.let { v ->
                            writer.name("provider").value(v)
                        }
                    } else {
                        writer.name(it.key).value(it.value.toString())
                    }
                }
                writer.endObject()
            }
            writer.endArray()
            writer.name(PREVIEW_PARAMS)
            writer.beginObject()
            annotation.parameters.forEach {
                writer.name(it.key).value(it.value.toString())
            }
            writer.endObject()
            writer.name(IMAGE_NAME).value(calcImageName(method, annotation))
            writer.endObject()
        }
        writer.endArray()
        writer.endObject()
    }
}

private fun calcHexString(digest: ByteArray): String =
    digest.joinToString("") { it.toInt().and(0xff).toString(16).padStart(2, '0') }.substring(0, 8)

private fun calcImageName(method: MethodRepresentation, annotation: BaseAnnotationRepresentation): String {
    val digest = MessageDigest.getInstance("SHA-1")
    annotation.parameters.keys.sorted().forEach {
        digest.update(it.toByteArray(StandardCharsets.UTF_8))
        digest.update(annotation.parameters[it].toString().toByteArray(StandardCharsets.UTF_8))
    }
    val previewSettingsHash = calcHexString(digest.digest())
    method.parameters.forEach { param ->
        param.annotationParameters.keys.sorted().forEach {
            digest.update(it.toByteArray(StandardCharsets.UTF_8))
            digest.update(param.annotationParameters[it].toString().toByteArray(StandardCharsets.UTF_8))
        }
    }
    val methodParamHash = calcHexString(digest.digest())
    return "${method.methodFqn}_${previewSettingsHash}_$methodParamHash"
}

fun getComposeScreenshots(inputFile: String): List<ComposeScreenshot> {
    val screenshots = mutableListOf<ComposeScreenshot>()
    val jsonReader = JsonReader(File(inputFile)
        .reader())
    jsonReader.isLenient = true
    jsonReader.use {  reader ->
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                SCREENSHOTS -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        screenshots.add(readComposeScreenshot(reader))
                    }
                    reader.endArray()
                }
            }
        }

        reader.endObject()
    }
    return screenshots
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
