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

package com.android.tools.preview.screenshot.tasks

import com.android.tools.preview.screenshot.ImageDetails
import com.android.tools.preview.screenshot.ImageDiffer
import com.android.tools.preview.screenshot.PreviewResult
import com.android.tools.preview.screenshot.Verify
import com.android.tools.preview.screenshot.saveResults
import com.android.tools.preview.screenshot.toPreviewResponse
import com.android.tools.render.compose.ComposeScreenshot
import com.android.tools.render.compose.readComposeScreenshotsJson
import com.android.utils.FileUtils
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.VerificationTask
import javax.imageio.ImageIO
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Runs screenshot tests of a variant.
 */
@CacheableTask
abstract class PreviewScreenshotValidationTask : DefaultTask(), VerificationTask {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val referenceImageDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val renderTaskOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val imageOutputDir: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val previewFile: RegularFileProperty

    @get:OutputFile
    abstract val resultsFile: RegularFileProperty

    @TaskAction
    fun run() {
        val screenshots = readComposeScreenshotsJson(previewFile.get().asFile.reader())
        if (screenshots.isNotEmpty()) {
            val resultsToSave = mutableListOf<PreviewResult>()
            var allTestsPass = true
            for (screenshot in screenshots) {
                val imageComparison = compareImages(screenshot)
                resultsToSave.add(imageComparison)
                if (imageComparison.responseCode != 0) {
                    allTestsPass = false
                }
            }

            saveResults(resultsToSave, resultsFile.get().asFile.absolutePath)

            if (!allTestsPass) {
                throw GradleException("There were failing tests")
            }
        } else {
            logger.info("No tests found, nothing to do.")
        }
    }

    private fun compareImages(composeScreenshot: ComposeScreenshot): PreviewResult {
        // TODO(b/296430073) Support custom image difference threshold from DSL or task argument
        val imageDiffer = ImageDiffer.MSSIMMatcher()
        val screenshotName = composeScreenshot.imageName
        val screenshotNamePng = "$screenshotName.png"
        var referencePath = referenceImageDir.asFile.get().toPath().resolve(screenshotNamePng)
        var referenceMessage: String? = null
        val actualPath = imageOutputDir.asFile.get().toPath().resolve(screenshotName + "_actual.png")
        var diffPath = imageOutputDir.asFile.get().toPath().resolve(screenshotName + "_diff.png")
        var diffMessage: String? = null
        var code = 0
        val verifier = Verify(imageDiffer, diffPath)

        //If the CLI tool could not render the preview, return the preview result with the
        //code and message along with reference path if it exists
        val renderedFile = renderTaskOutputDir.asFile.get().toPath().resolve(screenshotName + "_0.png").toFile()
        if (!renderedFile.exists()) {
            if (!referencePath.toFile().exists()) {
                referencePath = null
                referenceMessage = "Reference image missing"
            }

            return PreviewResult(1,
                composeScreenshot.methodFQN,
                "Image render failed",
                referenceImage = ImageDetails(referencePath, referenceMessage),
                actualImage = ImageDetails(null, "Image render failed")
            )

        }

        // copy rendered image from intermediate dir to output dir
        FileUtils.copyFile(renderedFile, actualPath.toFile())

        val result =
            verifier.assertMatchReference(
                referencePath,
                ImageIO.read(actualPath.toFile())
            )
        when (result) {
            is Verify.AnalysisResult.Failed -> {
                code = 1
            }
            is Verify.AnalysisResult.Passed -> {
                if (result.imageDiff.highlights == null) {
                    diffPath = null
                    diffMessage = "Images match!"
                }
            }
            is Verify.AnalysisResult.MissingReference -> {
                referencePath = null
                diffPath = null
                referenceMessage = "Reference image missing"
                diffMessage = "No diff available"
                code = 1
            }
            is Verify.AnalysisResult.SizeMismatch -> {
                diffMessage = result.message
                diffPath = null
                code = 1
            }
        }
        return result.toPreviewResponse(code, composeScreenshot.methodFQN,
            ImageDetails(referencePath, referenceMessage),
            ImageDetails(actualPath, null),
            ImageDetails(diffPath, diffMessage))
    }
}
