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
import com.android.tools.utp.plugins.host.device.info.proto.AndroidTestDeviceInfoProto
import com.android.builder.testing.api.DeviceConfigProvider
import com.google.api.services.testing.model.TestExecution
import com.google.api.services.testing.model.TestMatrix
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import org.gradle.api.logging.Logging
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
    private val testMatrixGenerator: TestMatrixGenerator = TestMatrixGenerator(projectSettings)
) {

    private val logger = Logging.getLogger(this.javaClass)

    companion object {
        const val TEST_RESULT_PB_FILE_NAME = "test-result.pb"

        const val CHECK_TEST_STATE_WAIT_MS = 10 * 1000L;
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

        val testApkStorageObject = testRunStorage.uploadToStorage(testData.testApk)

        val configProvider = createConfigProvider(device)

        // If tested apk is null, this is a self-instrument test (e.g. library module).
        val testedApkFile = testData.testedApkFinder(configProvider).firstOrNull()
        val appApkStorageObject =
            testRunStorage.uploadToStorage(testedApkFile ?: projectSettings.stubAppApk)

        val updatedTestMatrix = testingManager.createTestMatrixRun(
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

        lateinit var resultTestMatrix: TestMatrix
        var previousTestMatrixState = ""
        var printResultsUrl = true
        while (true) {
            val latestTestMatrix =
                testingManager.getTestMatrix(projectSettings.name, updatedTestMatrix)
            if (previousTestMatrixState != latestTestMatrix.state) {
                previousTestMatrixState = latestTestMatrix.state
                logger.lifecycle("Firebase TestLab Test execution state: $previousTestMatrixState")
            }
            if (printResultsUrl) {
                val resultsUrl = latestTestMatrix.resultStorage?.get("resultsUrl") as String?
                if (!resultsUrl.isNullOrBlank()) {
                    val resultDetailsUrl = if (resultsUrl.endsWith("details")) {
                        resultsUrl
                    } else {
                        "$resultsUrl/details"
                    }
                    logger.lifecycle(
                        "Test request for device ${device.name} has been submitted to " +
                                "Firebase TestLab: $resultDetailsUrl")
                    printResultsUrl = false
                }
            }
            val testFinished = when (latestTestMatrix.state) {
                "VALIDATING", "PENDING", "RUNNING" -> false
                else -> true
            }
            logger.info("Test execution: ${latestTestMatrix.state}")
            if (testFinished) {
                resultTestMatrix = latestTestMatrix
                break
            }
            Thread.sleep (CHECK_TEST_STATE_WAIT_MS)
        }

        val deviceInfoFile =
            createDeviceInfoFile(resultsOutDir, device.name, device.deviceId, device.apiLevel)

        val ftlTestRunResults: MutableList<FtlTestRunResult> = mutableListOf()

        resultTestMatrix.testExecutions.forEach { testExecution ->
            if (testExecution.toolResultsStep != null) {
                val executionStep = toolResultsManager.requestStep(
                    ToolResultsManager.requestFrom(testExecution.toolResultsStep))
                executionStep.testExecutionStep.testSuiteOverviews?.forEach { suiteOverview ->
                    testRunStorage.downloadFromStorage(suiteOverview.xmlSource.fileUri) {
                        File(resultsOutDir, "TEST-${it.replace("/", "_")}")
                    }?.also {
                        updateTestResultXmlFile(it, device.name, projectPath, variantName)
                    }
                }
            }
            val testSuiteResult = getTestSuiteResult(
                resultTestMatrix,
                testExecution,
                deviceInfoFile,
                testRunStorage,
                resultsOutDir
            )

            ftlTestRunResults.add(FtlTestRunResult(testSuiteResult.passed(), testSuiteResult))
        }

        val resultProtos = ftlTestRunResults.mapNotNull(FtlTestRunResult::resultsProto)
        if (resultProtos.isNotEmpty()) {
            val resultsMerger = UtpTestSuiteResultMerger()
            resultProtos.forEach(resultsMerger::merge)

            val mergedTestResultPbFile = File(resultsOutDir, TEST_RESULT_PB_FILE_NAME)
            mergedTestResultPbFile.outputStream().use {
                resultsMerger.result.writeTo(it)
            }
        }

        return ftlTestRunResults
    }

    private fun getTestSuiteResult(
        testMatrix: TestMatrix,
        testExecution: TestExecution,
        deviceInfoFile: File,
        testRunStorage: TestRunStorage,
        resultsOutDir: File
    ): TestSuiteResult {

        val results = testExecution.toolResultsStep
        return if (results != null) {

            val requestInfo = ToolResultsManager.requestFrom(results)

            testResultProcessor.toUtpResult(
                resultsOutDir,
                toolResultsManager.requestStep(requestInfo),
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

    private fun createDeviceInfoFile(
        resultsOutDir: File,
        deviceName: String,
        deviceId: String,
        deviceApiLevel: Int
    ): File {
        val deviceInfoFile = File(resultsOutDir, "device-info.pb")
        val androidTestDeviceInfo = AndroidTestDeviceInfoProto.AndroidTestDeviceInfo.newBuilder()
            .setName(deviceName)
            .setApiLevel(deviceApiLevel.toString())
            .setGradleDslDeviceName(deviceName)
            .setModel(deviceId)
            .build()
        FileOutputStream(deviceInfoFile).use {
            androidTestDeviceInfo.writeTo(it)
        }
        return deviceInfoFile
    }

    private fun updateTestResultXmlFile(
        xmlFile: File,
        deviceName: String,
        projectPath: String,
        variantName: String,
    ) {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val document = builder.parse(xmlFile)
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
                setAttribute("value", deviceName)
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
        transformer.transform(DOMSource(document), StreamResult(xmlFile))
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
        }
}
