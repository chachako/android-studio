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

package com.android.tools.screenshot

import com.android.tools.render.compose.readComposeScreenshotsJson
import com.android.tools.render.compose.readComposeRenderingResultJson
import com.android.tools.render.compose.ComposeScreenshot
import com.android.tools.render.compose.ComposeScreenshotResult
import java.io.File
import javax.imageio.ImageIO
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.support.discovery.EngineDiscoveryRequestResolver

class PreviewScreenshotTestEngine : TestEngine {

    private data class Parameters(
        val diffImageDirPath: String,
        val previewsDiscovered: String,
        val referenceImageDirPath: String,
        val renderResultsFilePath: String,
        val renderTaskOutputDir: String?,
        val resultsDirPath: String?,
        val threshold: String?
    )

    private val parameters: Parameters by lazy {
        Parameters(
            requireNotNull(getParam("diffImageDirPath")) { "diffImageDirPath param must not be null" },
            requireNotNull(getParam("previews-discovered")) { "previews-discovered param must not be null" },
            requireNotNull(getParam("referenceImageDirPath")) { "referenceImageDirPath param must not be null" },
            requireNotNull(getParam("renderResultsFilePath")) { "renderResultsFilePath param must not be null" },
            getParam("renderTaskOutputDir"),
            getParam("resultsDirPath"),
            getParam("threshold"),
        )
    }

    override fun getId(): String {
        return "preview-screenshot-test-engine"
    }

    override fun discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        val engineDescriptor = EngineDescriptor(uniqueId, "Preview Screenshot Test Engine")
        val screenshots: List<ComposeScreenshot> = readComposeScreenshotsJson(File(parameters.previewsDiscovered).reader())
        val screenshotResults = readComposeRenderingResultJson(File(parameters.renderResultsFilePath).reader()).screenshotResults
        val testMap = mutableMapOf<String, MutableSet<Tests.TestMethod>>()
        for (screenshot in screenshots) {
            val methodName = screenshot.methodFQN.split(".").last()
            val className = screenshot.methodFQN.substring(0, screenshot.methodFQN.lastIndexOf("."))
            val previewName = screenshot.previewParams["name"]
            val previewNameList = if (previewName != null) mutableListOf(previewName) else mutableListOf()
            if (testMap.contains(className)) {
                val testMethodSet = testMap[className]!!
                val existingEntry = testMethodSet.find { it.methodName == methodName}
                if (existingEntry != null) {
                    if (previewName != null) {
                        existingEntry.previewNames.add(previewName)
                    }
                } else {
                    testMethodSet.add(
                        Tests.TestMethod(methodName,
                            getScreenshotResultsForMethod(className, methodName, screenshots, screenshotResults).size > 1,
                            previewNameList))
                }
            } else {
                testMap[className] = mutableSetOf(
                    Tests.TestMethod(methodName,
                        getScreenshotResultsForMethod(className, methodName, screenshots, screenshotResults).size > 1,
                        previewNameList))
            }
        }
        val tests = Tests(testMap)

        EngineDiscoveryRequestResolver.builder<EngineDescriptor>()
            .addClassContainerSelectorResolver { testClass ->
                tests.classes.contains(testClass.getName())
            }
            .addSelectorResolver { ctx -> ClassSelectorResolver(ctx.classNameFilter, tests) }
            .addSelectorResolver(MethodSelectorResolver(tests))
            .build()
            .resolve(discoveryRequest, engineDescriptor)
        return engineDescriptor
    }

    override fun execute(request: ExecutionRequest) {
        val listener = request.engineExecutionListener
        val resultsToSave = mutableListOf<PreviewResult>()
        if (request.rootTestDescriptor.children.isEmpty()) return
        val resultFile = File(parameters.renderResultsFilePath)
        val screenshotResults = readComposeRenderingResultJson(resultFile.reader()).screenshotResults
        val composeScreenshots: List<ComposeScreenshot> = readComposeScreenshotsJson(File(parameters.previewsDiscovered).reader())

        for (classDescriptor in request.rootTestDescriptor.children) {
            listener.executionStarted(classDescriptor)
            for (methodDescriptor in classDescriptor.children) {
                if (methodDescriptor is TestMethodTestDescriptor) {
                    resultsToSave.add(
                        runTestMethodThatGeneratesASingleScreenshotTest(
                            methodDescriptor,
                            listener,
                            screenshotResults))
                } else if (methodDescriptor is TestMethodDescriptor) {
                    resultsToSave.addAll(
                        runTestMethodThatGeneratesMultipleScreenshotTests(
                            methodDescriptor,
                            listener,
                            composeScreenshots,
                            screenshotResults
                        )
                    )
                }
            }
            listener.executionFinished(classDescriptor, TestExecutionResult.successful())
        }
        if (resultsToSave.isNotEmpty()) {
            saveResults(resultsToSave, "${parameters.resultsDirPath}/TEST-results.xml")
        }
    }

    private fun compareImages(composeScreenshot: ComposeScreenshotResult, testDisplayName: String, startTime: Long): PreviewResult {
        // TODO(b/296430073) Support custom image difference threshold from DSL or task argument
        var referencePath = File(parameters.referenceImageDirPath).toPath().resolve(composeScreenshot.imageName)
        var referenceMessage: String? = null
        val actualPath = File(parameters.renderTaskOutputDir, composeScreenshot.imageName).toPath()
        var diffPath = File(parameters.diffImageDirPath).toPath().resolve(composeScreenshot.imageName)
        var diffMessage: String? = null
        var code = 0
        val threshold = parameters.threshold?.toFloat()
        val imageDiffer = if (threshold != null) {
            ImageDiffer.MSSIMMatcher(threshold)
        } else {
            ImageDiffer.MSSIMMatcher()
        }
        val verifier = Verify(imageDiffer, diffPath)

        //If the CLI tool could not render the preview, return the preview result with the
        //code and message along with reference path if it exists
        if (!actualPath.toFile().exists()) {
            if (!referencePath.toFile().exists()) {
                referencePath = null
                referenceMessage = "Reference image missing"
            }

            return PreviewResult(1,
                composeScreenshot.previewId,
                getDurationInSeconds(startTime),
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
        return result.toPreviewResponse(code, testDisplayName,
            getDurationInSeconds(startTime),
            ImageDetails(referencePath, referenceMessage),
            ImageDetails(actualPath, null),
            ImageDetails(diffPath, diffMessage)
        )
    }

    private fun getParam(key: String): String? {
        return System.getProperty("com.android.tools.preview.screenshot.junit.engine.${key}")
    }

    private fun getDurationInSeconds(startTimeMillis: Long): Float {
        return (System.currentTimeMillis() - startTimeMillis) / 1000F
    }

    private fun reportResult(listener: EngineExecutionListener, screenshot: ComposeScreenshotResult, testDescriptor: TestDescriptor, testDisplayName: String): PreviewResult {
        val startTime = System.currentTimeMillis()
        listener.executionStarted(testDescriptor)
        val imageComparison = compareImages(screenshot, testDisplayName, startTime)
        val result = if (imageComparison.responseCode != 0) {
            TestExecutionResult.failed(AssertionError(imageComparison.message))
        } else TestExecutionResult.successful()
        listener.executionFinished(testDescriptor, result)
        return imageComparison
    }

    private fun getPreviewIdWithoutSuffix(previewId: String, previewName: String?): String {
        val previewIdWithoutHash = previewId.substringBeforeLast('_').substringBeforeLast('_')
        return if (previewName.isNullOrEmpty()) previewIdWithoutHash else previewIdWithoutHash.removeSuffix("_${previewName}")
    }

    /**
     * Returns a list of [ComposeScreenshotResult]s that are generated from a test method
     *
     * The provided list of screenshot results is filtered by matching the provided class name
     * and method name against a ComposeScreenshotResult's previewId with the preview name and
     * hash suffix removed.
     *
     * @param className the name of the class containing the test method
     * @param methodName the name of the test method
     * @param allScreenshots a list of all discovered ComposeScreenshots
     * @param allScreenshotResults a list of all discovered ComposeScreenshotResults
     */
    private fun getScreenshotResultsForMethod(className: String, methodName: String, allScreenshots: List<ComposeScreenshot>, allScreenshotResults: List<ComposeScreenshotResult>): List<ComposeScreenshotResult> {
        val screenshotResults = mutableListOf<ComposeScreenshotResult>()
        val matchingComposeScreenshotPreviewNames = allScreenshots.filter { it.methodFQN == "$className.$methodName" }.map {
            if (it.previewParams.containsKey("name") && isPreviewNameValidFileName(it.previewParams["name"].toString())) {
                it.previewParams["name"]
            } else {
                ""
            }}.distinct()
        matchingComposeScreenshotPreviewNames.forEach {previewName ->
            screenshotResults.addAll(allScreenshotResults.filter { getPreviewIdWithoutSuffix(it.previewId, previewName) == "$className.${methodName}" })
        }
        return screenshotResults.toList()
    }

    private fun isPreviewNameValidFileName(previewName: String): Boolean {
        val invalidCharacters = Regex("""[\u0000-\u001F\\/:*?"<>|]+""")
        return !(invalidCharacters.containsMatchIn(previewName))
    }

    private fun runTestMethodThatGeneratesASingleScreenshotTest(methodDescriptor: TestMethodTestDescriptor,
        listener: EngineExecutionListener,
        screenshotResults: List<ComposeScreenshotResult>): PreviewResult {
        val className: String = methodDescriptor.className
        val methodName: String = methodDescriptor.methodName
        val previewName: String? = methodDescriptor.previewName
        val screenshots =
            screenshotResults.filter {
                getPreviewIdWithoutSuffix(it.previewId, previewName) == "$className.${methodName}"
            }
        var displayName = "$className.${methodName}"
        if (!previewName.isNullOrEmpty()) { displayName += "_$previewName"}
        return reportResult(listener, screenshots.single(), methodDescriptor, displayName)
    }

    private fun runTestMethodThatGeneratesMultipleScreenshotTests(
        methodDescriptor: TestMethodDescriptor,
        listener: EngineExecutionListener,
        composeScreenshots: List<ComposeScreenshot>,
        screenshotResults: List<ComposeScreenshotResult>): List<PreviewResult> {
        val results = mutableListOf<PreviewResult>()
        listener.executionStarted(methodDescriptor)
        val className: String = methodDescriptor.className
        val methodName: String = methodDescriptor.methodName
        val methodScreenshotResults = getScreenshotResultsForMethod(className, methodName, composeScreenshots, screenshotResults)
        for ((run, screenshot) in methodScreenshotResults.withIndex()) {
            val currentComposePreview = composeScreenshots.single {
                it.methodFQN == "$className.$methodName" && screenshot.imageName.contains(it.previewId)
            }
            var suffix = ""
            if (currentComposePreview.previewParams.containsKey("name")) {
                suffix += "_${currentComposePreview.previewParams["name"]}"
            }
            val previewParamsSuffix = currentComposePreview.previewParams.filter { it.key != "name" }
            if (previewParamsSuffix.isNotEmpty()) {
                // Skip "name" parameter because it is added to the suffix above
                suffix += "_${previewParamsSuffix}"
            }
            if (currentComposePreview.methodParams.isNotEmpty()) {
                // Method parameters can generate multiple screenshots from one preview,
                // add the method parameters and the count indicated by the previewId
                suffix += "_${currentComposePreview.methodParams}"
                val paramIndex = screenshot.imageName.substringBeforeLast(".").substringAfterLast("_")
                suffix += "_$paramIndex"
            }
            val previewTestDescriptor = PreviewTestDescriptor(methodDescriptor, methodName, run, suffix)
            methodDescriptor.addChild(previewTestDescriptor)
            listener.dynamicTestRegistered(previewTestDescriptor)
            listener.executionStarted(previewTestDescriptor)
            results.add(reportResult(listener, screenshot, previewTestDescriptor,"${className}.${methodName}$suffix"))
        }
        listener.executionFinished(methodDescriptor, TestExecutionResult.successful())
        return results
    }
}
