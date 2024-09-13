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

package com.android.compose.screenshot

import com.android.tools.preview.multipreview.BaseAnnotationRepresentation
import com.android.tools.preview.multipreview.MethodRepresentation
import com.android.tools.preview.multipreview.PreviewMethodFinder
import com.android.tools.preview.multipreview.ParameterRepresentation
import com.android.tools.preview.multipreview.PreviewMethod
import com.android.tools.render.compose.ComposeRendering
import com.android.tools.render.compose.ComposeScreenshot
import com.android.tools.render.compose.readComposeScreenshotsJson
import com.android.tools.render.compose.writeComposeRenderingToJson
import com.android.tools.render.compose.writeComposeScreenshotsToJson
import com.google.common.annotations.VisibleForTesting
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.objectweb.asm.Type
import java.util.SortedMap
import java.util.logging.Level
import java.util.logging.Logger

private val logger = Logger.getLogger("PreviewFinder")
private val invalidCharsRegex = """[\u0000-\u001F\\/:*?"<>|]+""".toRegex()

fun configureInput (
    classPath: List<String>,
    projectClassPath: List<String>,
    fontsPath: String?,
    layoutlibPath: String,
    outputFolder: String,
    metaDataFolder: String,
    namespace: String,
    resourceApkPath: String,
    cliToolArgumentsFile: File,
    previewsFile: File,
    resultsFilePath: String
) {
    if (!File(outputFolder).exists()) {
        Files.createDirectories(Path.of(outputFolder))
    }
    val previews = readComposeScreenshotsJson(previewsFile.reader())
    val composeRendering = ComposeRendering(
        fontsPath,
        layoutlibPath,
        outputFolder,
        metaDataFolder,
        classPath,
        projectClassPath,
        namespace,
        resourceApkPath,
        previews,
        resultsFilePath
    )
    writeComposeRenderingToJson(cliToolArgumentsFile.writer(), composeRendering)
}

fun discoverPreviews(
    testDirs: List<File>,
    testJars: List<File>,
    mainDirs: List<File>,
    mainJars: List<File>,
    deps: List<File>,
    outputPath: Path) {
    val previewFinder = PreviewMethodFinder(testDirs, testJars, mainDirs, mainJars, deps)
    val previews = previewFinder.findAllPreviewMethods()
    serializePreviewMethods(previews, outputPath)
}

private fun serializePreviewMethods(
    previews: Set<PreviewMethod>,
    outputFile: Path
) {
    Files.newBufferedWriter(outputFile).use { fileWriter ->
        val composeScreenshots = previews.flatMap { (method, previewAnnotations) ->
            previewAnnotations.map { annotation ->
                ComposeScreenshot(
                    method.methodFqn,
                    convertListMap(method.parameters),
                    convertMap(annotation.parameters),
                    calcPreviewId(method, annotation)
                )
            }
        }
        writeComposeScreenshotsToJson(fileWriter, composeScreenshots.sortedBy { it.previewId })
    }
}

/**
 * Converts list of [ParameterRepresentation] to a list of maps representing method parameters
 *
 * The generated list contains sorted maps and is sorted by key and then by value.
 *
 * @param parameters the list of [ParameterRepresentation] to convert
 * @return a list of maps
 * **/
private fun convertListMap(parameters: List<ParameterRepresentation>): List<SortedMap<String, String>> {
    return sortListOfSortedMaps(parameters.map { convertMap(it.annotationParameters) })

}

private fun convertMap(map: Map<String, Any?>): SortedMap<String, String> =
    map.map { (key, value) ->
        key to (if (key == "provider") (value as Type).className else value.toString())
    }.toMap().toSortedMap()


private fun calcHexString(digest: ByteArray): String {
    val hexString = digest.joinToString("") { eachByte -> "%02x".format(eachByte) }
    return if (hexString.length >= 8) {
        hexString.substring(0,8)
    } else {
        hexString
    }
}

/**
 * Sort provided list of maps
 *
 * Empty maps will be listed first, then maps will be sorted by key. If there are multiple
 * maps with the same key, that key's value will be used to sort.
 * **/
@VisibleForTesting
fun sortListOfSortedMaps(listToSort: List<SortedMap<String, String>>): List<SortedMap<String, String>> {
    return listToSort.sortedWith { map1, map2 ->
        val sortedEntries1 =
            map1.entries.sortedWith(compareBy<Map.Entry<String, String>> { it.key }.thenBy { it.value })
        val sortedEntries2 =
            map2.entries.sortedWith(compareBy<Map.Entry<String, String>> { it.key }.thenBy { it.value })

        // Iterate through both lists of entries, comparing each pair
        val largerSize = maxOf(sortedEntries1.size, sortedEntries2.size)
        for (i in IntRange(0, largerSize)) {
            // If we run out of entries in one map, the shorter map comes first
            if (i >= sortedEntries1.size) return@sortedWith -1
            if (i >= sortedEntries2.size) return@sortedWith 1

            val (key1, value1) = sortedEntries1[i]
            val (key2, value2) = sortedEntries2[i]

            // Compare keys first
            val keyComparison = key1.compareTo(key2)
            if (keyComparison != 0) return@sortedWith keyComparison

            // If keys are equal, compare values
            val valueComparison = value1.compareTo(value2)
            if (valueComparison != 0) return@sortedWith valueComparison
        }

        // If all entries match, the maps are equal
        return@sortedWith 0
    }
}

private fun calcPreviewId(method: MethodRepresentation, annotation: BaseAnnotationRepresentation): String {
    val previewIdBuilder = StringBuilder(method.methodFqn)

    val previewName = annotation.parameters["name"]
    if (previewName != null && previewName is CharSequence) {
        if (previewName.contains(invalidCharsRegex)) {
            logger.warning("Preview name '$previewName' contains invalid characters. It will be included in the HTML report but ignored in image file names.")
        } else {
            previewIdBuilder.append("_").append(previewName)
        }
    }

    val digest = MessageDigest.getInstance("SHA-1")

    updateAndAppendHash(digest, previewIdBuilder, "annotation-parameters", annotation.parameters)

    if (method.parameters.isNotEmpty()) {
        for (param in method.parameters) { //currently only one param is supported and max size of method.parameters is 1
            updateAndAppendHash(digest, previewIdBuilder, "method-parameter-annotations", param.annotationParameters)
        }
    }

    return previewIdBuilder.toString()
}

private fun updateAndAppendHash(digest: MessageDigest, builder: StringBuilder, dataSectionName: String, dataMap: Map<String, *>) {
    if (dataMap.isNotEmpty()) {
        digest.update(dataSectionName.toByteArray())
        for ((key, value) in dataMap.toSortedMap()) {
            digest.update(key.toByteArray())
            digest.update(value.toString().toByteArray())
        }
        builder.append("_").append(calcHexString(digest.digest()))
    }
}
