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

package com.android.tools.preview.screenshot.junit.engine

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Path
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SaveResultsUtilTest {
    @get:Rule
    val tempDirRule = TemporaryFolder()
    private val referenceImagePath: Path = Path.of("referencePath")
    private val actualPath: Path = Path.of("actualPath")
    private val diffPath: Path = Path.of("diffPath")

    private lateinit var previewResults: List<PreviewResult>

    private fun createPreviewResultSuccess(): PreviewResult {
        return PreviewResult(0, "package.previewTest1", "Reference image saved", ImageDetails(referenceImagePath, null))
    }

    private fun createPreviewResultFailed(): PreviewResult {
        return PreviewResult(1, "package.previewTest2", "Images don't match", ImageDetails(referenceImagePath, null),
            ImageDetails(actualPath, null), ImageDetails(diffPath, null)
        )
    }

    private fun createPreviewResultError(): PreviewResult {
        return PreviewResult(2, "package.previewTest3", "Render error",
            ImageDetails(referenceImagePath, null), ImageDetails(null, "Render error: Class XYZ not found"))
    }

    @Test
    fun testSaveResults() {
        previewResults = listOf(createPreviewResultSuccess(), createPreviewResultSuccess(),
            createPreviewResultError(), createPreviewResultFailed())
        val outputFilePath = tempDirRule.newFile().absolutePath
        saveResults(previewResults, outputFilePath)
        val file = File(outputFilePath)
        assertTrue(file.exists())
        val fileContent =
            javaClass.getResourceAsStream("results.xml")?.readBytes()?.toString(Charsets.UTF_8)
        val expectedContent = fileContent!!.replace("referencePath", referenceImagePath.toString())
            .replace("actualPath", actualPath.toString())
            .replace("diffPath", diffPath.toString()).trimEnd()
        assertThat(file.readText()).isEqualTo(expectedContent)
    }
}


