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

import javax.imageio.ImageIO
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.junit.Test
import org.junit.Assert.assertEquals

class ImageDifferTest {
    @Test
    fun mssimMatcherSimilar() {
        val result = ImageDiffer.MSSIMMatcher().diff(loadTestImage("circle"), loadTestImage("circle"))
        assertIs<ImageDiffer.DiffResult.Similar>(result)
        assertEquals("[MSSIM] Required SSIM: 1.000, Actual SSIM: 1.000", result.description)
        assertNull(result.highlights)
    }

    @Test
    fun mssimMatcherDifferentWithImageDifferenceThreshold() {
        val differ = ImageDiffer.MSSIMMatcher(0.9f)

        val result = differ.diff(loadTestImage("circle"), loadTestImage("star"))
        assertIs<ImageDiffer.DiffResult.Similar>(result)
        assertEquals("[MSSIM] Required SSIM: 0.100, Actual SSIM: 0.338", result.description)
        assertNull(result.highlights)
    }

    @Test
    fun mssimMatcherDifferent() {
        val result = ImageDiffer.MSSIMMatcher().diff(loadTestImage("circle"), loadTestImage("star"))
        assertIs<ImageDiffer.DiffResult.Different>(result)
        assertEquals("17837 of 65536 pixels different", result.description)
        assertIs<ImageDiffer.DiffResult.Similar>(
                ImageDiffer.PixelPerfect.diff(result.highlights, loadTestImage("PixelPerfect_diff"))
        )
    }

    @Test
    fun mmsimName() {
        assertEquals("MSSIMMatcher", ImageDiffer.MSSIMMatcher().name)
    }

    @Test
    fun pixelPerfectSimilar() {
        val result = ImageDiffer.PixelPerfect.diff(loadTestImage("circle"), loadTestImage("circle"))
        assertIs<ImageDiffer.DiffResult.Similar>(result)
        assertEquals("0 of 65536 pixels different", result.description)
        assertNull(result.highlights)
    }

    @Test
    fun pixelPerfectDifferent() {
        val result = ImageDiffer.PixelPerfect.diff(loadTestImage("circle"), loadTestImage("star"))
        assertIs<ImageDiffer.DiffResult.Different>(result)
        assertEquals("17837 of 65536 pixels different", result.description)
        assertIs<ImageDiffer.DiffResult.Similar>(
                ImageDiffer.PixelPerfect.diff(result.highlights, loadTestImage("PixelPerfect_diff"))
        )
    }

    @Test
    fun pixelPerfectName() {
        assertEquals("PixelPerfect", ImageDiffer.PixelPerfect.name)
    }

    private fun loadTestImage(name: String) =
            ImageIO.read(javaClass.getResourceAsStream("$name.png")!!)
}
