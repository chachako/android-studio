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
import com.google.gson.stream.JsonWriter
import org.objectweb.asm.Type
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

private const val SCREENSHOTS = "screenshots"
private const val METHOD_FQN = "methodFQN"
private const val METHOD_PARAMS = "methodParams"
private const val IMAGE_NAME = "imageName"
private const val PREVIEW_PARAMS = "previewParams"

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
    }
    serializePreviews(previews, outputFile)
}

private fun getClassName(fqcn: String): String {
    return fqcn.substring(0, fqcn.lastIndexOf(PERIOD))
}

private fun serializePreviews(
    previews: List<Pair<MethodRepresentation, BaseAnnotationRepresentation>>,
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

private fun calcHexString(digest: ByteArray): String {
    val hexString = digest.joinToString("") { eachByte -> "%02x".format(eachByte) }
    if (hexString.length >= 8) {
        return hexString.substring(0,8)
    } else {
        return hexString
    }
}


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

