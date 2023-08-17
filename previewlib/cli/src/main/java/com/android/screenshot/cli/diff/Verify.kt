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
package com.android.screenshot.cli.diff

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class Verify (val imageDiffer: ImageDiffer = ImageDiffer.PixelPerfect, val outputFile: String) {
    fun assertMatchGolden(goldenPath: String, image: BufferedImage) : AnalysisResult{

        return analyze(goldenPath, image)
    }

    private fun analyze(goldenPath: String, given: BufferedImage): Verify.AnalysisResult {
        var golden: BufferedImage? = null
        if (File(goldenPath).exists()) {
            golden = ImageIO.read(File(goldenPath))
        }
        if (golden == null) return AnalysisResult.MissingGolden(given, "MISSING GOLDEN")
        if (given.width != golden.width || given.height != golden.height) {
            return AnalysisResult.SizeMismatch(given, "SIZE MISMATCH", golden)
        }
        val diff = imageDiffer.diff(given, golden)
        if (diff is ImageDiffer.DiffResult.Different) {
            val goldenFile = File(outputFile)
            ImageIO.write(diff.highlights, "png", goldenFile)
            return AnalysisResult.Failed(given, "FAILED",  golden, diff)
        }
        return AnalysisResult.Passed(given, "PASSED", golden, diff as ImageDiffer.DiffResult.Similar)
    }

    sealed interface AnalysisResult {
        val actual: BufferedImage
        val message: String

        data class Passed(
            override val actual: BufferedImage,
            override val message: String,
            val expected: BufferedImage,
            val imageDiff: ImageDiffer.DiffResult.Similar
        ) : AnalysisResult

        data class Failed(
            override val actual: BufferedImage,
            override val message: String,
            val expected: BufferedImage,
            val imageDiff: ImageDiffer.DiffResult.Different
        ) : AnalysisResult

        data class SizeMismatch(
            override val actual: BufferedImage,
            override val message: String,
            val expected: BufferedImage
        ) : AnalysisResult

        data class MissingGolden(
            override val actual: BufferedImage,
            override val message: String
        ) : AnalysisResult

        data class RenderError(
            override  val actual: BufferedImage,
            override val message: String
        ) : AnalysisResult
    }
}
