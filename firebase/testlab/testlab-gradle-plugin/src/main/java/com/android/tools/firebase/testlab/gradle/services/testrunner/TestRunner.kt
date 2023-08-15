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

package com.android.tools.firebase.testlab.gradle.services.testrunner

import com.android.build.api.instrumentation.StaticTestData
import com.android.tools.firebase.testlab.gradle.UtpTestSuiteResultMerger
import com.android.tools.firebase.testlab.gradle.services.FtlTestRunResult
import com.android.tools.firebase.testlab.gradle.services.StorageManager
import com.android.tools.firebase.testlab.gradle.services.TestResultProcessor
import com.android.tools.firebase.testlab.gradle.services.TestingManager
import com.android.tools.firebase.testlab.gradle.services.ToolResultsManager
import com.android.tools.firebase.testlab.gradle.services.passed
import com.android.tools.firebase.testlab.gradle.services.storage.TestRunStorage
import com.android.builder.testing.api.DeviceConfigProvider
import com.google.api.services.testing.model.ToolResultsStep
import com.google.api.services.testing.model.TestMatrix
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Class for starting and managing test runs between the TestLab Plugin with Firebase Testlab.
 *
 * @param projectSettings all configurations associated with the given gradle module
 * @param toolResultsManager client for handling all ToolResults requests
 * @param testingManager client for handling all Testing requests
 * @param storageManager client for handling all Storage requests
 * @param testResultProcessor processes the ftl results of finished test runs
 * @param testMatrixGenerator generates the TestMatrix for any test run.
 */
class TestRunner(
    private val projectSettings: ProjectSettings,
    private val toolResultsManager: ToolResultsManager,
    private val testingManager: TestingManager,
    private val storageManager: StorageManager,
    private val testResultProcessor: TestResultProcessor,
    private val testMatrixGenerator: TestMatrixGenerator = TestMatrixGenerator(projectSettings),
    private val matrixRunProcessTracker: TestMatrixRunProcessTracker =
        TestMatrixRunProcessTracker(testingManager, projectSettings.name),
    private val deviceInfoFileManager: DeviceInfoFileManager = DeviceInfoFileManager(),
    private val testSuiteMergerFactory: () -> UtpTestSuiteResultMerger = {
        UtpTestSuiteResultMerger()
    },
    private val xmlHandlerFactory: (TestDeviceData) -> TestResultsXmlHandler = {
        getDefaultHandler(it)
    }
) {

    companion object {
        const val TEST_RESULT_PB_FILE_NAME = "test-result.pb"

        fun getDefaultHandler(device: TestDeviceData): TestResultsXmlHandler =
            object: TestResultsXmlHandler {
                override fun updateXml(xml: File, variantName: String, projectPath: String) {
                    val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    val document = builder.parse(xml)
                    val testSuiteElements = document.getElementsByTagName("testsuite").let { nodeList ->
                        List(nodeList.length) { index ->
                            nodeList.item(index)
                        }
                    }
                    testSuiteElements.forEach { testSuite ->
                        val propertyNode = testSuite.childNodes.let { nodeList ->
                            List(nodeList.length) { index ->
                                nodeList.item(index)
                            }
                        }.firstOrNull { node ->
                            node.nodeType == Node.ELEMENT_NODE && node.nodeName.lowercase() == "properties"
                        }

                        val propertyElement = if (propertyNode == null) {
                            document.createElement("properties").also {
                                testSuite.appendChild(it)
                            }
                        } else {
                            propertyNode as Element
                        }

                        propertyElement.appendChild(document.createElement("property").apply {
                            setAttribute("name", "device")
                            setAttribute("value", device.name)
                        })
                        propertyElement.appendChild(document.createElement("property").apply {
                            setAttribute("name", "flavor")
                            setAttribute("value", variantName)
                        })
                        propertyElement.appendChild(document.createElement("property").apply {
                            setAttribute("name", "project")
                            setAttribute("value", projectPath)
                        })
                    }

                    val transformerFactory = TransformerFactory.newInstance()
                    val transformer = transformerFactory.newTransformer()
                    transformer.transform(DOMSource(document), StreamResult(xml))
                }
            }
    }

    fun runTests(
        device: TestDeviceData,
        testData: StaticTestData,
        resultsOutDir: File,
        projectPath: String,
        variantName: String
    ): List<FtlTestRunResult> {

        val runRequestId = UUID.randomUUID().toString()

        val testHistoryName = projectSettings.testHistoryName ?:
        testData.testedApplicationId ?: testData.applicationId

        val historyId = toolResultsManager.getOrCreateHistory(projectSettings.name, testHistoryName)

        val testRunStorage = storageManager.testRunStorage(
            runRequestId,
            projectSettings.storageBucket,
            historyId)

        val testApkStorageObject = storageManager.retrieveOrUploadSharedFile(
            testData.testApk,
            projectSettings.storageBucket,
            projectPath,
            uploadFileName = "$variantName-${testData.testApk.name}"
        )

        val configProvider = createConfigProvider(device)

        // If tested apk is null, this is a self-instrument test (e.g. library module).
        val testedApkFile = testData.testedApkFinder(configProvider).firstOrNull()

        // If the test apk is a stub, then it can be shared between gradle projects.
        val (testedApkName, storageModule) = testedApkFile?.run {
            Pair("$variantName-${testedApkFile.name}", projectPath)
        } ?: Pair("stub", "shared")

        val appApkStorageObject = storageManager.retrieveOrUploadSharedFile(
            testedApkFile ?: projectSettings.stubAppApk,
            projectSettings.storageBucket,
            storageModule,
            uploadFileName = testedApkName
        )

        val testMatrix = testingManager.createTestMatrixRun(
            projectSettings.name,
            testMatrixGenerator.createTestMatrix(
                device,
                testData,
                testRunStorage,
                testApkStorageObject,
                appApkStorageObject,
                testedApkFile == null
            ),
            runRequestId
        )

        val resultTestMatrix =
            matrixRunProcessTracker.waitForTestResults(device.name, testMatrix)

        val ftlTestRunResults: MutableList<FtlTestRunResult> = mutableListOf()

        resultTestMatrix.testExecutions?.forEach { testExecution ->

            val testSuiteResult = handleTestSuiteResult(
                resultTestMatrix,
                testExecution.toolResultsStep,
                device,
                testRunStorage,
                resultsOutDir,
                projectPath,
                variantName
            )

            ftlTestRunResults.add(FtlTestRunResult(testSuiteResult.passed(), testSuiteResult))
        }

        val resultProtos = ftlTestRunResults.mapNotNull(FtlTestRunResult::resultsProto)
        if (resultProtos.isNotEmpty()) {
            val resultsMerger = testSuiteMergerFactory()
            resultProtos.forEach(resultsMerger::merge)

            val mergedTestResultPbFile = File(resultsOutDir, TEST_RESULT_PB_FILE_NAME)
            mergedTestResultPbFile.outputStream().use {
                resultsMerger.result.writeTo(it)
            }
        }

        return ftlTestRunResults
    }

    private fun handleTestSuiteResult(
        testMatrix: TestMatrix,
        results: ToolResultsStep?,
        device: TestDeviceData,
        testRunStorage: TestRunStorage,
        resultsOutDir: File,
        projectPath: String,
        variantName: String
    ): TestSuiteResult {
        val xmlHandler = xmlHandlerFactory(device)

        val deviceInfoFile = deviceInfoFileManager.createFile(resultsOutDir, device)

        return if (results != null) {

            val requestInfo = ToolResultsManager.requestFrom(results)
            val executionStep = toolResultsManager.requestStep(requestInfo)
            executionStep.testExecutionStep.testSuiteOverviews?.forEach { suiteOverview ->
                testRunStorage.downloadFromStorage(suiteOverview.xmlSource.fileUri) {
                    File(resultsOutDir, "TEST-${it.replace("/", "_")}")
                }?.also {
                    xmlHandler.updateXml(it, projectPath, variantName)
                }
            }

            testResultProcessor.toUtpResult(
                resultsOutDir,
                executionStep,
                toolResultsManager.requestThumbnails(requestInfo)?.thumbnails,
                testRunStorage,
                deviceInfoFile,
                toolResultsManager.requestTestCases(requestInfo),
                testMatrix.invalidMatrixDetails
            )
        } else {
            testResultProcessor.toUtpResult(
                testMatrix.invalidMatrixDetails
            )
        }
    }

    private fun createConfigProvider(device: TestDeviceData) =
        object : DeviceConfigProvider {
            override fun getConfigFor(abi: String?): String {
                return requireNotNull(abi)
            }

            override fun getDensity(): Int = device.ftlModel.screenDensity

            override fun getLanguage(): String {
                return device.locale.language
            }

            override fun getRegion(): String? {
                return device.locale.country
            }

            override fun getAbis() = device.ftlModel.supportedAbis

            override fun getApiLevel() = device.apiLevel

            override fun getSupportsPrivacySandbox(): Boolean = false
        }
}
