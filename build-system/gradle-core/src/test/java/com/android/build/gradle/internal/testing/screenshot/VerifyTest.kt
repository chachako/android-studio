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

package com.android.build.gradle.internal.testing.screenshot

import org.junit.Assert.assertThrows
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Test
import kotlin.test.assertIs
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName
import java.lang.IllegalArgumentException
import kotlin.test.assertFailsWith

class VerifyTest {
    @get:Rule
    val testName = TestName()

    @get:Rule
    val goldenDirectory = TemporaryFolder()

    @get:Rule
    val outputDirectory = TemporaryFolder()

    @Test
    fun assertMatchGolden_missingGolden() {
        val analysis = verifier().assertMatchGolden(goldenFilePath("circle"), loadTestImage("circle"))

        assertIs<Verify.AnalysisResult.MissingGolden>(analysis)
        assertEquals(loadTestImage("circle"), analysis.actual)
    }

    @Test
    fun assertMatchGolden_passed() {
        createGolden("circle")

        val analysis = verifier().assertMatchGolden(goldenFilePath("circle"), loadTestImage("circle"))

        assertIs<Verify.AnalysisResult.Passed>(analysis)
        assertEquals(loadTestImage("circle"), analysis.actual)
        assertEquals(loadTestImage("circle"), analysis.expected)
    }

    @Test
    fun assertMatchGolden_failed() {
        createGolden("circle")

        val analysis = verifier().assertMatchGolden(goldenFilePath("circle"), loadTestImage("star"))

        assertIs<Verify.AnalysisResult.Failed>(analysis)
        assertEquals(loadTestImage("star"), analysis.actual)
        assertEquals(loadTestImage("circle"), analysis.expected)
        assertEquals(loadTestImage("PixelPerfect_diff"), analysis.imageDiff.highlights!!)
    }

    @Test
    fun assertMatchGolden_sizeMismatch() {
        createGolden("vertical_rectangle")

        val analysis = verifier().assertMatchGolden(goldenFilePath("vertical_rectangle"), loadTestImage("horizontal_rectangle"))

        assertIs<Verify.AnalysisResult.SizeMismatch>(analysis)
        assertEquals(loadTestImage("vertical_rectangle"), analysis.actual)
        assertEquals(loadTestImage("horizontal_rectangle"), analysis.expected)
    }

    private fun verifier() = Verify(
            ImageDiffer.MSSIMMatcher,
            outputDirectory.root.absolutePath
    )

    /** Compare two images using [ImageDiffer.MSSIMMatcher]. */
    private fun assertEquals(expected: BufferedImage, actual: BufferedImage) {
        assertIs<ImageDiffer.DiffResult.Similar>(
                ImageDiffer.MSSIMMatcher.diff(expected, actual),
                message = "Expected images to be identical, but they were not."
        )
    }

    /** Create a golden image for this test from the supplied test image [name]. */
    private fun createGolden(name: String) =
        javaClass.getResourceAsStream("$name.png")!!
                .copyTo(goldenDirectory.root.resolve("$name.png").canonicalFile.apply { parentFile!!.mkdirs() }.outputStream())

    /** Creates a file path for the golden from the supplied test image [name]. */
    private fun goldenFilePath(name: String) =
        goldenDirectory.root.resolve("$name.png").canonicalFile.absolutePath

    /** Load a test image from resources. */
    private fun loadTestImage(name: String) =
            ImageIO.read(javaClass.getResourceAsStream("$name.png")!!)
}
