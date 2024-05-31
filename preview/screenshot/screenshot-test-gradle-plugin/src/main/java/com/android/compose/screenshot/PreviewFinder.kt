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
import com.android.tools.preview.multipreview.MultipreviewSettings
import com.android.tools.preview.multipreview.ParameterRepresentation
import com.android.tools.preview.multipreview.buildMultipreview
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
import java.util.jar.JarFile
import java.util.logging.Level
import java.util.logging.Logger

private val logger = Logger.getLogger("PreviewFinder")

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

fun findPreviewsAndSerialize(classPath: List<String>, outputFile: Path, testDirectories: List<File>, testJars: List<File>) {
    val settings = MultipreviewSettings(
        "androidx.compose.ui.tooling.preview.Preview",
        "androidx.compose.ui.tooling.preview.PreviewParameter"
    )
    val multipreview = buildMultipreview(settings, classPath) {
        val className = getClassName(it)
        classExistsIn(className, testDirectories, testJars)
    }
    val previews =  multipreview.methods.flatMap { method ->
        multipreview.getAnnotations(method).map { baseAnnotation ->
            method to baseAnnotation
        }
    }
    serializePreviews(previews, outputFile)
}

// TODO: move this check to MethodFilter interface b/330334806
fun classExistsIn(className: String, dirs: List<File>, jars: List<File>): Boolean {
    val classFilePath = className.replace('.', '/') + ".class"
    for (dir in dirs) {
        val potentialClassFile = File(dir, classFilePath)
        if (potentialClassFile.exists()) {
            return true
        }
    }
    for (jar in jars) {
        JarFile(jar).use {
            if (it.getJarEntry(classFilePath) != null) {
                return true
            }
        }
    }
    return false
}

private fun getClassName(fqcn: String): String {
    if (fqcn.contains("$")) {
        return fqcn.substring(0, fqcn.indexOf('$'))
    }
    return fqcn.substring(0, fqcn.lastIndexOf("."))
}

private fun serializePreviews(
    previews: List<Pair<MethodRepresentation, BaseAnnotationRepresentation>>,
    outputFile: Path
) {
    val fileWriter = Files.newBufferedWriter(outputFile)
    val composeScreenshots = previews.map { (method, annotation) ->
        ComposeScreenshot(
            method.methodFqn,
            convertListMap(method.parameters),
            convertMap(annotation.parameters),
            calcImageName(method, annotation)
        )
    }
    writeComposeScreenshotsToJson(fileWriter, composeScreenshots.sortedBy { it.previewId })
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

private fun calcImageName(method: MethodRepresentation, annotation: BaseAnnotationRepresentation): String {
    var imageName = method.methodFqn
    if (annotation.parameters.containsKey("name")) {
        val previewName = annotation.parameters["name"].toString()
        val invalidCharacters = Regex("""[\u0000-\u001F\\/:*?"<>|]+""")
        if (invalidCharacters.containsMatchIn(previewName)) {
            logger.log(
                Level.WARNING,
                "Preview name $previewName contains characters that are not valid in file " +
                        "names. It will be included in the test name in the HTML test report but " +
                        "ignored in image file names for screenshot test results."
            )
        } else {
            imageName += "_${previewName}"
        }
    }
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
    return "${imageName}_${previewSettingsHash}_$methodParamHash"
}
