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

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import org.junit.Test
import kotlin.test.assertIs
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName
import java.nio.file.Path

class VerifyTest {
    @get:Rule
    val testName = TestName()

    @get:Rule
    val referenceDirectory = TemporaryFolder()

    @get:Rule
    val outputDirectory = TemporaryFolder()

    @Test
    fun assertMatchReference_missingReference() {
        val analysis = verifier().assertMatchReference(referenceFilePath("circle"), loadTestImage("circle"))

        val previewResult = analysis.toPreviewResponse(1, "name", ImageDetails(Path.of("referencePath"), null))
        assert(previewResult.message == analysis.message)
        assertIs<Verify.AnalysisResult.MissingReference>(analysis)
        assertEquals(loadTestImage("circle"), analysis.actual)
    }

    @Test
    fun assertMatcReference_passed() {
        createReference("circle")

        val analysis = verifier().assertMatchReference(referenceFilePath("circle"), loadTestImage("circle"))

        val previewResult = analysis.toPreviewResponse(1, "name", ImageDetails(Path.of("referencePath"), null))
        assert(previewResult.message == analysis.message)
        assertIs<Verify.AnalysisResult.Passed>(analysis)
        assertEquals(loadTestImage("circle"), analysis.actual)
        assertEquals(loadTestImage("circle"), analysis.expected)
    }

    @Test
    fun assertMatchReferenceWithThreshold_passed() {
        createReference("circle")
        val differ = ImageDiffer.MSSIMMatcher(0.9f)
        val verifier = Verify(
            differ,
            outputDirectory.root.toPath()
        )

        val analysis = verifier.assertMatchReference(referenceFilePath("circle"), loadTestImage("star"))

        assertIs<Verify.AnalysisResult.Passed>(analysis)
        assertEquals(loadTestImage("star"), analysis.actual)
        assertEquals(loadTestImage("circle"), analysis.expected)
    }

    @Test
    fun assertMatchReference_failed() {
        createReference("circle")

        val analysis = verifier().assertMatchReference(referenceFilePath("circle"), loadTestImage("star"))

        val previewResult = analysis.toPreviewResponse(1, "name", ImageDetails(Path.of("referencePath"), null))
        assert(previewResult.message == analysis.message)
        assertIs<Verify.AnalysisResult.Failed>(analysis)
        assertEquals(loadTestImage("star"), analysis.actual)
        assertEquals(loadTestImage("circle"), analysis.expected)
        assertEquals(loadTestImage("PixelPerfect_diff"), analysis.imageDiff.highlights)
    }

    @Test
    fun assertMatchReference_sizeMismatch() {
        createReference("vertical_rectangle")

        val analysis = verifier().assertMatchReference(referenceFilePath("vertical_rectangle"), loadTestImage("horizontal_rectangle"))

        val previewResult = analysis.toPreviewResponse(1, "name", ImageDetails(Path.of("referencePath"), null))
        assert(previewResult.message == analysis.message)
        assertIs<Verify.AnalysisResult.SizeMismatch>(analysis)
        assertEquals(loadTestImage("vertical_rectangle"), analysis.expected)
        assertEquals(loadTestImage("horizontal_rectangle"), analysis.actual)
    }

    private fun verifier() = Verify(
        ImageDiffer.MSSIMMatcher(),
        outputDirectory.root.toPath()
    )

    /** Compare two images using [ImageDiffer.MSSIMMatcher]. */
    private fun assertEquals(expected: BufferedImage, actual: BufferedImage) {
        assertIs<ImageDiffer.DiffResult.Similar>(
            ImageDiffer.MSSIMMatcher().diff(expected, actual),
            message = "Expected images to be identical, but they were not."
        )
    }

    /** Create a reference image for this test from the supplied test image [name]. */
    private fun createReference(name: String) =
        javaClass.getResourceAsStream("$name.png")!!
            .copyTo(referenceDirectory.root.resolve("$name.png").canonicalFile.apply { parentFile!!.mkdirs() }.outputStream())

    /** Creates a file path for the reference image from the supplied test image [name]. */
    private fun referenceFilePath(name: String) =
        referenceDirectory.root.resolve("$name.png").toPath()

    /** Load a test image from resources. */
    private fun loadTestImage(name: String) =
        ImageIO.read(javaClass.getResourceAsStream("$name.png")!!)
}
