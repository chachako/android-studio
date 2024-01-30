/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.render.compose.readComposeScreenshotsJson
import com.android.tools.render.compose.readComposeRenderingResultJson
import com.android.tools.render.compose.ComposeScreenshot
import com.android.tools.render.compose.ComposeScreenshotResult
import com.android.utils.FileUtils
import com.android.utils.cxx.os.getEnvironmentPaths
import java.io.File
import javax.imageio.ImageIO
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.support.discovery.EngineDiscoveryRequestResolver

class PreviewScreenshotTestEngine : TestEngine {

    override fun getId(): String {
        return "preview-screenshot-test-engine"
    }

    override fun discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        val engineDescriptor = EngineDescriptor(uniqueId, "Preview Screenshot Test Engine")
        val screenshots: List<ComposeScreenshot> = readComposeScreenshotsJson(File(getParam("previews-discovered")).reader())
        val testMap = mutableMapOf<String, MutableSet<String>>()
        for (screenshot in screenshots) {
            val methodName = screenshot.methodFQN.split(".").last()
            val className = screenshot.methodFQN.substring(0, screenshot.methodFQN.lastIndexOf("."))
            if (testMap.contains(className)) {
                testMap[className]!!.add(methodName)
            } else {
                testMap[className] = mutableSetOf(methodName)
            }
        }
        val tests = Tests(testMap)

        EngineDiscoveryRequestResolver.builder<EngineDescriptor>()
            .addClassContainerSelectorResolver { testClass ->
                tests.classes.contains(testClass.getName())
            }
            .addSelectorResolver { ctx -> ClassSelectorResolver(ctx.getClassNameFilter(), tests) }
            .addSelectorResolver(MethodSelectorResolver(tests))
            .build()
            .resolve(discoveryRequest, engineDescriptor)
        return engineDescriptor
    }

    override fun execute(request: ExecutionRequest) {
        val listener = request.engineExecutionListener
        val resultsToSave = mutableListOf<PreviewResult>()
        val resultFile = File(getParam("renderTaskOutputDirPath")).toPath().resolve("results.json").toFile()
        val composeRenderingResult = readComposeRenderingResultJson(resultFile.reader())
        val screenshotResults = composeRenderingResult.screenshotResults
        for (classDescriptor in request.getRootTestDescriptor().getChildren()) {
            listener.executionStarted(classDescriptor)
            var classTestResult = TestExecutionResult.successful()
            for (methodDescriptor in classDescriptor.getChildren()) {
                listener.executionStarted(methodDescriptor)
                val methodResults = mutableListOf<PreviewResult>()
                val className: String = (methodDescriptor as TestMethodDescriptor).className
                val methodName: String = methodDescriptor.methodName
                val screenshots =
                    screenshotResults.filter {
                        it.resultId.contains(className) && it.resultId.contains(methodName)
                    }
                for (screenshot in screenshots) {
                    val imageComparison = compareImages(screenshot!!)
                    methodResults.add(imageComparison)
                }
                val failedComparisons = methodResults.filter { it.responseCode != 0 }
                val testResult = if (failedComparisons.size != 0) {
                    val reportUrl = File(File(getParam("reportUrlPath")), "index.html").toURI().toASCIIString()
                    classTestResult =
                        TestExecutionResult.failed(AssertionError("There were failing tests. Creating test report at $reportUrl"))
                    TestExecutionResult.failed(AssertionError(failedComparisons.first().message))
                } else {
                    TestExecutionResult.successful()
                }
                listener.executionFinished(methodDescriptor, testResult)
                resultsToSave.addAll(methodResults)
            }
            listener.executionFinished(classDescriptor, classTestResult)
        }
        if (!resultsToSave.isEmpty()) {
            saveResults(resultsToSave, getParam("resultsFilePath"))
        }
    }

    private fun compareImages(composeScreenshot: ComposeScreenshotResult): PreviewResult {
        // TODO(b/296430073) Support custom image difference threshold from DSL or task argument
        val imageDiffer = ImageDiffer.MSSIMMatcher()
        val screenshotName = composeScreenshot.resultId
        val screenshotNamePng = "$screenshotName.png"
        var referencePath = File(getParam("referenceImageDirPath")).toPath().resolve(screenshotNamePng)
        var referenceMessage: String? = null
        val actualPath = File(composeScreenshot.imagePath).toPath()
        var diffPath = File(getParam("diffImageDirPath")).toPath().resolve(screenshotNamePng)
        var diffMessage: String? = null
        var code = 0
        val verifier = Verify(imageDiffer, diffPath)

        //If the CLI tool could not render the preview, return the preview result with the
        //code and message along with reference path if it exists
        if (!actualPath.toFile().exists()) {
            if (!referencePath.toFile().exists()) {
                referencePath = null
                referenceMessage = "Reference image missing"
            }

            return PreviewResult(1,
                composeScreenshot.resultId,
                "Image render failed",
                referenceImage = ImageDetails(referencePath, referenceMessage),
                actualImage = ImageDetails(null, "Image render failed")
            )

        }

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
        return result.toPreviewResponse(code, composeScreenshot.resultId,
            ImageDetails(referencePath, referenceMessage),
            ImageDetails(actualPath, null),
            ImageDetails(diffPath, diffMessage))
    }

    private fun getParam(key: String): String {
        return System.getProperty("com.android.tools.preview.screenshot.junit.engine.${key}")
    }
}
