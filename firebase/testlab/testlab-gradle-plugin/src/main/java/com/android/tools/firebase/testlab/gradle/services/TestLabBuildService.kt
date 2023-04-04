/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.firebase.testlab.gradle.services

import com.android.build.api.instrumentation.StaticTestData
import com.android.builder.testing.api.DeviceConfigProvider
import com.android.tools.firebase.testlab.gradle.FixtureImpl
import com.android.tools.firebase.testlab.gradle.UtpTestSuiteResultMerger
import com.android.tools.utp.plugins.host.device.info.proto.AndroidTestDeviceInfoProto
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.util.Utils
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpRequestFactory
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.InputStreamContent
import com.google.api.client.json.GenericJson
import com.google.api.client.json.JsonObjectParser
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.Key
import com.google.api.services.storage.Storage
import com.google.api.services.storage.model.StorageObject
import com.google.api.services.testing.Testing
import com.google.api.services.testing.model.AndroidDevice
import com.google.api.services.testing.model.AndroidDeviceCatalog
import com.google.api.services.testing.model.AndroidDeviceList
import com.google.api.services.testing.model.AndroidInstrumentationTest
import com.google.api.services.testing.model.AndroidModel
import com.google.api.services.testing.model.ClientInfo
import com.google.api.services.testing.model.DeviceFile
import com.google.api.services.testing.model.EnvironmentMatrix
import com.google.api.services.testing.model.GoogleCloudStorage
import com.google.api.services.testing.model.RegularFile
import com.google.api.services.testing.model.ResultStorage
import com.google.api.services.testing.model.TestExecution
import com.google.api.services.testing.model.TestMatrix
import com.google.api.services.testing.model.TestSetup
import com.google.api.services.testing.model.TestSpecification
import com.google.api.services.testing.model.ToolResultsHistory
import com.google.api.services.toolresults.ToolResults
import com.google.api.services.toolresults.model.History
import com.google.api.services.toolresults.model.StackTrace
import com.google.firebase.testlab.gradle.Orientation
import com.google.firebase.testlab.gradle.TestLabGradlePluginExtension
import com.google.testing.platform.proto.api.core.ErrorProto.Error
import com.google.testing.platform.proto.api.core.IssueProto.Issue
import com.google.testing.platform.proto.api.core.LabelProto.Label
import com.google.testing.platform.proto.api.core.PathProto.Path
import com.google.testing.platform.proto.api.core.TestArtifactProto.Artifact
import com.google.testing.platform.proto.api.core.TestArtifactProto.ArtifactType
import com.google.testing.platform.proto.api.core.TestCaseProto
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestStatusProto.TestStatus
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteMetaData
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistry
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * A Gradle Build service that provides APIs to talk to the Firebase Test Lab backend server.
 */
abstract class TestLabBuildService : BuildService<TestLabBuildService.Parameters> {

    init {
        Logger.getLogger("com.google.api.client").level = Level.WARNING
    }

    companion object {
        const val TEST_RESULT_PB_FILE_NAME = "test-result.pb"
        const val clientApplicationName: String = "Firebase TestLab Gradle Plugin"
        const val xGoogUserProjectHeaderKey: String = "X-Goog-User-Project"
        val cloudStorageUrlRegex = Regex("""gs://(.*?)/(.*)""")

        const val INSTRUMENTATION_TEST_SHARD_FIELD = "shardingOption"
        const val TEST_MATRIX_FLAKY_TEST_ATTEMPTS_FIELD = "flakyTestAttempts"
        const val TEST_MATRIX_FAIL_FAST_FIELD = "failFast"

        const val CHECK_TEST_STATE_WAIT_MS = 10 * 1000L;

        val oauthScope = listOf(
                // Scope for Cloud Tool Results API and Cloud Testing API.
                "https://www.googleapis.com/auth/cloud-platform",
        )

        class TestCases : GenericJson() {
            @Key var testCases: List<TestCase>? = null
            @Key var nextPageToken: String? = null
        }

        class TestCase : GenericJson() {
            @Key var testCaseId: String? = null
            @Key var startTime: TimeStamp? = null
            @Key var endTime: TimeStamp? = null
            @Key var stackTraces: List<StackTrace>? = null
            @Key var status: String? = null
            @Key var testCaseReference: TestCaseReference? = null
            @Key var toolOutputs: List<ToolOutputReference>? = null
        }

        class TimeStamp : GenericJson() {
            @Key var seconds: String? = null
            @Key var nanos: Int? = null
        }

        class TestCaseReference : GenericJson() {
            @Key var name: String? = null
            @Key var className: String? = null
            @Key var testSuiteName: String? = null
        }

        class ToolOutputReference: GenericJson() {
            @Key var output: FileReference? = null
        }

        class FileReference: GenericJson() {
            @Key var fileUri: String? = null
        }

        class UniformSharding: GenericJson() {
            @Key var numShards: Int? = null
        }

        class ShardingOption: GenericJson() {
            @Key var uniformSharding: UniformSharding? = null
        }
    }

    private val logger = Logging.getLogger(this.javaClass)

    /**
     * Parameters of [TestLabBuildService].
     */
    interface Parameters : BuildServiceParameters {
        val quotaProjectName: Property<String>
        val credentialFile: RegularFileProperty
        val cloudStorageBucket: Property<String>
        val timeoutMinutes: Property<Int>
        val maxTestReruns: Property<Int>
        val failFast: Property<Boolean>
        val numUniformShards: Property<Int>
        val grantedPermissions: Property<String>
        val extraDeviceFiles: MapProperty<String, String>
        val networkProfile: Property<String>
        val resultsHistoryName: Property<String>
        val directoriesToPull: ListProperty<String>
        val recordVideo: Property<Boolean>
        val performanceMetrics: Property<Boolean>
    }

    internal open val credential: GoogleCredential by lazy {
        parameters.credentialFile.get().asFile.inputStream().use {
            GoogleCredential.fromStream(it).createScoped(oauthScope)
        }
    }

    private val httpRequestInitializer: HttpRequestInitializer = HttpRequestInitializer { request ->
        credential.initialize(request)
        request.headers[xGoogUserProjectHeaderKey] = parameters.quotaProjectName.get()
    }

    private val jacksonFactory: JacksonFactory
        get() = JacksonFactory.getDefaultInstance()

    internal open val httpTransport: HttpTransport
        get() = GoogleNetHttpTransport.newTrustedTransport()
    val numUniformShards: Int
        get() = parameters.numUniformShards.getOrNull() ?: 0

    fun runTestsOnDevice(
        deviceName: String,
        deviceId: String,
        deviceApiLevel: Int,
        deviceLocale: Locale,
        deviceOrientation: Orientation,
        ftlDeviceModel: AndroidModel,
        testData: StaticTestData,
        resultsOutDir: File,
        projectPath: String,
        variantName: String,
    ): ArrayList<FtlTestRunResult> {
        resultsOutDir.apply {
            if (!exists()) {
                mkdirs()
            }
        }

        val projectName = parameters.quotaProjectName.get()
        val runRequestId = UUID.randomUUID().toString()

        val toolResultsClient = ToolResults.Builder(
            httpTransport,
            jacksonFactory,
            httpRequestInitializer
        ).apply {
            applicationName = clientApplicationName
        }.build()

        val initSettingsResult =
                toolResultsClient.projects().initializeSettings(projectName).execute()
        val bucketName = parameters.cloudStorageBucket.orNull?.ifBlank { null }
                ?: initSettingsResult.defaultBucket

        val storageClient = Storage.Builder(
            httpTransport,
            jacksonFactory,
            httpRequestInitializer
        ).apply {
            applicationName = clientApplicationName
        }.build()

        val testApkStorageObject = uploadToCloudStorage(
            testData.testApk, runRequestId, storageClient, bucketName
        )

        val configProvider = createConfigProvider(
            ftlDeviceModel, deviceLocale, deviceApiLevel
        )
        val appApkStorageObject = uploadToCloudStorage(
            testData.testedApkFinder(configProvider).first(),
            runRequestId,
            storageClient,
            bucketName
        )

        val testingClient = Testing.Builder(
            httpTransport,
            jacksonFactory,
            httpRequestInitializer
        ).apply {
            applicationName = clientApplicationName
        }.build()

        val testMatricesClient = testingClient.projects().testMatrices()

        val testHistoryName = parameters.resultsHistoryName.getOrElse("").ifBlank {
            testData.testedApplicationId ?: testData.applicationId
        }
        val historyId = getOrCreateHistory(toolResultsClient, projectName, testHistoryName)

        val testMatrix = TestMatrix().apply {
            projectId = projectName
            clientInfo = ClientInfo().apply {
                name = clientApplicationName
            }
            testSpecification = TestSpecification().apply {
                testSetup = TestSetup().apply {
                    set("dontAutograntPermissions", parameters.grantedPermissions.orNull ==
                            FixtureImpl.GrantedPermissions.NONE.name)
                    if(parameters.networkProfile.getOrElse("").isNotBlank()) {
                        networkProfile = parameters.networkProfile.get()
                    }
                    filesToPush = mutableListOf()
                    parameters.extraDeviceFiles.get().forEach { (onDevicePath, filePath) ->
                        val gcsFilePath = if (filePath.startsWith("gs://")) {
                            filePath
                        } else {
                            val file = File(filePath)
                            check(file.exists()) { "$filePath doesn't exist." }
                            check(file.isFile) { "$filePath must be file." }
                            val storageObject = uploadToCloudStorage(
                                    file, runRequestId, storageClient, bucketName)
                            "gs://$bucketName/${storageObject.name}"
                        }
                        filesToPush.add(DeviceFile().apply {
                            regularFile = RegularFile().apply {
                                content = com.google.api.services.testing.model.FileReference().apply {
                                    gcsPath = gcsFilePath
                                }
                                devicePath = onDevicePath
                            }
                        })
                    }

                    directoriesToPull = parameters.directoriesToPull.get()
                }
                androidInstrumentationTest = AndroidInstrumentationTest().apply {
                    testApk = com.google.api.services.testing.model.FileReference().apply {
                        gcsPath = "gs://$bucketName/${testApkStorageObject.name}"
                    }
                    appApk = com.google.api.services.testing.model.FileReference().apply {
                        gcsPath = "gs://$bucketName/${appApkStorageObject.name}"
                    }
                    appPackageId = testData.testedApplicationId
                    testPackageId = testData.applicationId
                    testRunnerClass = testData.instrumentationRunner

                    createShardingOption()?.also { sharding ->
                        this.set(INSTRUMENTATION_TEST_SHARD_FIELD, sharding)
                    }
                }
                environmentMatrix = EnvironmentMatrix().apply {
                    androidDeviceList = AndroidDeviceList().apply {
                        androidDevices = listOf(
                            AndroidDevice().apply {
                                androidModelId = deviceId
                                androidVersionId = deviceApiLevel.toString()
                                locale = deviceLocale.toString()
                                orientation = deviceOrientation.toString().lowercase()
                            }
                        )
                    }
                }
                resultStorage = ResultStorage().apply {
                    googleCloudStorage = GoogleCloudStorage().apply {
                        gcsPath = "gs://$bucketName/$runRequestId/results"
                    }
                    toolResultsHistory = ToolResultsHistory().apply {
                        projectId = projectName
                        this.historyId = historyId
                    }
                }
                testTimeout = "${parameters.timeoutMinutes.get() * 60}s"
                disablePerformanceMetrics = !parameters.performanceMetrics.get()
                disableVideoRecording = !parameters.recordVideo.get()
            }
            set(TEST_MATRIX_FLAKY_TEST_ATTEMPTS_FIELD, parameters.maxTestReruns.get())
            set(TEST_MATRIX_FAIL_FAST_FIELD, parameters.failFast.get())
        }
        val updatedTestMatrix = testMatricesClient.create(projectName, testMatrix).apply {
            this.requestId = runRequestId
        }.execute()

        lateinit var resultTestMatrix: TestMatrix
        var previousTestMatrixState = ""
        var printResultsUrl = true
        while (true) {
            val latestTestMatrix = testMatricesClient.get(
                projectName, updatedTestMatrix.testMatrixId).execute()
            if (previousTestMatrixState != latestTestMatrix.state) {
                previousTestMatrixState = latestTestMatrix.state
                logger.lifecycle("Firebase TestLab Test execution state: $previousTestMatrixState")
            }
            if (printResultsUrl) {
                val resultsUrl = latestTestMatrix.resultStorage?.get("resultsUrl") as String?
                if (!resultsUrl.isNullOrBlank()) {
                    logger.lifecycle(
                            "Test request for device $deviceName has been submitted to " +
                                    "Firebase TestLab: $resultsUrl")
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
            createDeviceInfoFile(resultsOutDir, deviceName, deviceId, deviceApiLevel)

        val ftlTestRunResults: ArrayList<FtlTestRunResult> = ArrayList()
        resultTestMatrix.testExecutions.forEach { testExecution ->
            if (testExecution.toolResultsStep != null) {
                val executionStep =
                    toolResultsClient.projects().histories().executions().steps().get(
                        testExecution.toolResultsStep.projectId,
                        testExecution.toolResultsStep.historyId,
                        testExecution.toolResultsStep.executionId,
                        testExecution.toolResultsStep.stepId
                    ).execute()
                executionStep.testExecutionStep.testSuiteOverviews?.forEach { suiteOverview ->
                    downloadFromCloudStorage(storageClient, suiteOverview.xmlSource.fileUri, runRequestId) {
                        File(resultsOutDir, "TEST-${it.replace("/", "_")}")
                    }?.also {
                        updateTestResultXmlFile(it, deviceName, projectPath, variantName)
                    }
                }
            }
            val testSuiteResult = getTestSuiteResult(
                toolResultsClient,
                resultTestMatrix,
                testExecution,
                deviceInfoFile,
                storageClient,
                resultsOutDir,
                runRequestId,
            )

            val testSuitePassed = testSuiteResult.testStatus.isPassedOrSkipped()
            val hasAnyFailedTestCase = testSuiteResult.testResultList.any { testCaseResult ->
                !testCaseResult.testStatus.isPassedOrSkipped()
            }
            val testPassed = testSuitePassed && !hasAnyFailedTestCase && !testSuiteResult.hasPlatformError()
            ftlTestRunResults.add(FtlTestRunResult(testPassed, testSuiteResult))
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

    fun getOrCreateHistory(
            toolResultsClient: ToolResults,
            projectId: String, historyName: String): String {
        val historyList = toolResultsClient.projects().histories().list(projectId).apply {
            filterByName = historyName
        }.execute()
        historyList?.histories?.firstOrNull()?.historyId?.let { return it }

        return toolResultsClient.projects().histories().create(
                projectId,
                History().apply {
                    name = historyName
                    displayName = historyName
                }).apply {
            requestId = UUID.randomUUID().toString()
        }.execute().historyId
    }

    fun catalog(): AndroidDeviceCatalog {
        val testingClient = Testing.Builder(
            httpTransport,
            jacksonFactory,
            httpRequestInitializer,
        ).apply {
            applicationName = clientApplicationName
        }.build()

        val catalog = testingClient.testEnvironmentCatalog().get("ANDROID").apply {
            projectId = parameters.quotaProjectName.get()
        }.execute()

        return catalog.androidDeviceCatalog
    }

    private fun createShardingOption(): ShardingOption? {
        if (numUniformShards == 0) {
            return null
        }
        return ShardingOption().apply {
            uniformSharding = UniformSharding().apply {
                numShards = numUniformShards
            }
        }
    }

    private fun getTestSuiteResult(
        toolResultsClient: ToolResults,
        testMatrix: TestMatrix,
        testExecution: TestExecution,
        deviceInfoFile: File,
        storageClient: Storage,
        resultsOutDir: File,
        runRequestId: String,
    ): TestSuiteResult {
        val testSuiteResult = TestSuiteResult.newBuilder()

        val toolResultsStep = testExecution.toolResultsStep
        if (toolResultsStep != null) {
            val projectId = toolResultsStep.projectId
            val historyId = toolResultsStep.historyId
            val executionId = toolResultsStep.executionId
            val stepId = toolResultsStep.stepId
            val step = toolResultsClient.projects().histories().executions().steps().get(
                projectId,
                historyId,
                executionId,
                stepId
            ).execute()

            testSuiteResult.apply {
                testSuiteMetaData = TestSuiteMetaData.newBuilder().apply {
                    testSuiteName = step.name
                    var scheduledTestCount = 0
                    step.testExecutionStep.testSuiteOverviews?.forEach { testSuiteOverview ->
                        scheduledTestCount += testSuiteOverview.totalCount
                    }
                    scheduledTestCaseCount = scheduledTestCount
                }.build()

                testStatus = when (step.outcome.summary) {
                    "success" -> TestStatus.PASSED
                    "failure" -> TestStatus.FAILED
                    "skipped" -> TestStatus.SKIPPED
                    else -> TestStatus.TEST_STATUS_UNSPECIFIED
                }
                val testResultXmlFilePath =
                    step.testExecutionStep.testSuiteOverviews?.get(0)?.xmlSource?.fileUri.orEmpty()
                if (testResultXmlFilePath.isNotBlank()) {
                    addOutputArtifact(
                            Artifact.newBuilder().apply {
                                label = Label.newBuilder().apply {
                                    label = "firebase.xmlSource"
                                    namespace = "android"
                                }.build()
                                sourcePath = Path.newBuilder().apply {
                                    path = testResultXmlFilePath
                                }.build()
                                type = ArtifactType.TEST_DATA
                            }.build()
                    )
                }
            }

            step.testExecutionStep.toolExecution?.toolLogs?.forEach { log ->
                testSuiteResult.apply {
                    addOutputArtifact(Artifact.newBuilder().apply {
                        label = Label.newBuilder().apply {
                            label = "firebase.toolLog"
                            namespace = "android"
                        }.build()
                        sourcePath = Path.newBuilder().apply {
                            path = log.fileUri
                        }.build()
                        type = ArtifactType.TEST_DATA
                        mimeType = "text/plain"
                    }.build())
                }
            }

            step.testExecutionStep.toolExecution?.toolOutputs?.forEach { toolOutput ->
                val outputArtifact = Artifact.newBuilder().apply {
                    label = Label.newBuilder().apply {
                        label = "firebase.toolOutput"
                        namespace = "android"
                    }.build()
                    sourcePath = Path.newBuilder().apply {
                        path = toolOutput.output.fileUri
                    }.build()
                    type = ArtifactType.TEST_DATA
                }.build()
                if (toolOutput.testCase == null) {
                    testSuiteResult.apply {
                        addOutputArtifact(outputArtifact)
                    }
                }
            }

            val thumbnails =
                toolResultsClient.projects().histories().executions().steps().thumbnails().list(
                    projectId,
                    historyId,
                    executionId,
                    stepId
                ).execute().thumbnails
            if (thumbnails != null) {
                for (thumbnail in thumbnails) {
                    testSuiteResult.apply {
                        addOutputArtifact(
                            Artifact.newBuilder().apply {
                                label = Label.newBuilder().apply {
                                    label = "firebase.thumbnail"
                                    namespace = "android"
                                }.build()
                                sourcePath = Path.newBuilder().apply {
                                    path = thumbnail.sourceImage.output.fileUri
                                }.build()
                                type = ArtifactType.TEST_DATA
                                mimeType = "image/jpeg"
                            })
                    }
                }
            }

            step.testExecutionStep?.testIssues?.forEach { testIssue ->
                testSuiteResult.apply {
                    addIssue(Issue.newBuilder().apply {
                        message = testIssue.errorMessage
                        name = testIssue.get("type").toString()
                        namespace = Label.newBuilder().apply {
                            label = "firebase.issue"
                            namespace = "android"
                        }.build()
                        severity = when (testIssue.get("severity")) {
                            "info" -> Issue.Severity.INFO
                            "suggestion" -> Issue.Severity.SUGGESTION
                            "warning" -> Issue.Severity.WARNING
                            "severe" -> Issue.Severity.SEVERE
                            else -> Issue.Severity.SEVERITY_UNSPECIFIED
                        }
                        code = testIssue.get("type").toString().hashCode()
                    }.build())
                }
            }

            step.testExecutionStep?.toolExecution?.toolOutputs?.forEach { toolOutput ->
                if (toolOutput?.output?.fileUri != null) {
                    val fileUri = requireNotNull(toolOutput.output.fileUri)
                    val download = parameters.directoriesToPull.get().any { directoriesToPull ->
                        fileUri.contains(directoriesToPull)
                    }
                    if (download) {
                        val downloadedFile = downloadFromCloudStorage(
                                storageClient, fileUri, runRequestId) {
                            File(resultsOutDir, it)
                        } ?: return@forEach
                        testSuiteResult.addOutputArtifactBuilder().apply {
                            labelBuilder.apply {
                                label = "firebase.toolOutput"
                                namespace = "android"
                            }
                            sourcePathBuilder.apply {
                                path = downloadedFile.path
                            }.build()
                            type = ArtifactType.TEST_DATA
                        }
                    }
                }
            }

            // Need the latest version of google-api-client to use
            // toolResultsClient.projects().histories().executions().steps().testCases().list().
            // Manually calling this API until this is available.
            val httpRequestFactory: HttpRequestFactory = httpTransport.createRequestFactory(httpRequestInitializer)
            val url = "https://toolresults.googleapis.com/toolresults/v1beta3/projects/$projectId/histories/$historyId/executions/$executionId/steps/$stepId/testCases"
            val request = httpRequestFactory.buildGetRequest(GenericUrl(url))
            val parser = JsonObjectParser(Utils.getDefaultJsonFactory())
            request.setParser(parser)
            val response = request.execute()
            response.content.use {
                val testCaseContents = parser.parseAndClose<TestCases>(
                    it, StandardCharsets.UTF_8,
                    TestCases::class.java
                )
                (testCaseContents["testCases"] as? List<TestCase>)?.forEach { case ->
                    testSuiteResult.apply {
                        addTestResult(TestResult.newBuilder().apply {
                            testCase = TestCaseProto.TestCase.newBuilder().apply {
                                val packageName: String = case.testCaseReference!!.className!!
                                val className: String = packageName.split(".").last()
                                testClass = className
                                testPackage = packageName.dropLast(className.length + 1)
                                testMethod = case.testCaseReference!!.name
                                startTimeBuilder.apply {
                                    seconds = case.startTime!!.seconds!!.toLong()
                                    nanos = case.startTime!!.nanos!!.toInt()
                                }
                                endTimeBuilder.apply {
                                    seconds = case.endTime!!.seconds!!.toLong()
                                    nanos = case.endTime!!.nanos!!.toInt()
                                }
                            }.build()

                            val status = case.status
                            testStatus = when (status) {
                                null -> TestStatus.PASSED
                                "passed" -> TestStatus.PASSED
                                "failed" -> TestStatus.FAILED
                                "error" -> TestStatus.ERROR
                                "skipped" -> TestStatus.SKIPPED
                                else -> TestStatus.TEST_STATUS_UNSPECIFIED
                            }

                            if (status == "failed" || status == "error") {
                                error = Error.newBuilder().apply {
                                    stackTrace = case.stackTraces!![0].exception
                                }.build()
                            }

                            if (case.toolOutputs != null) {
                                for (toolOutput in (case.toolOutputs as List<ToolOutputReference>)) {
                                    if (toolOutput.output!!.fileUri!!.endsWith("logcat")) {
                                        val logcatFile = downloadFromCloudStorage(
                                            storageClient, toolOutput.output!!.fileUri!!,
                                            runRequestId) {
                                            File(resultsOutDir, it)
                                        }
                                        if (logcatFile != null) {
                                            addOutputArtifactBuilder().apply {
                                                labelBuilder.apply {
                                                    label = "logcat"
                                                    namespace = "android"
                                                }
                                                sourcePathBuilder.apply {
                                                    path = logcatFile.path
                                                }.build()
                                                type = ArtifactType.TEST_DATA
                                            }
                                        }
                                    }
                                }
                            }

                            addOutputArtifactBuilder().apply {
                                labelBuilder.label = "device-info"
                                labelBuilder.namespace = "android"
                                sourcePathBuilder.path = deviceInfoFile.path
                            }
                        }.build())
                    }
                }
            }
        }

        if (testMatrix.invalidMatrixDetails?.isNotBlank() == true) {
            testSuiteResult.apply {
                platformErrorBuilder.addErrorsBuilder().apply {
                    summaryBuilder.apply {
                        errorName = testMatrix.invalidMatrixDetails
                        errorCode = testMatrix.invalidMatrixDetails.hashCode()
                        errorMessage = getInvalidMatrixDetailsErrorMessage(testMatrix.invalidMatrixDetails)
                        namespaceBuilder.apply {
                            label = "firebase.invalidMatrixDetails"
                            namespace = "android"
                        }
                    }
                }
            }
        }

        return testSuiteResult.build()
    }

    private fun TestStatus.isPassedOrSkipped(): Boolean {
        return when (this) {
            TestStatus.PASSED,
            TestStatus.IGNORED,
            TestStatus.SKIPPED -> true
            else -> false
        }
    }

    private fun getInvalidMatrixDetailsErrorMessage(invalidMatrixDetailsEnumValue: String): String {
        return when(invalidMatrixDetailsEnumValue) {
            "MALFORMED_APK" -> "The input app APK could not be parsed."
            "MALFORMED_TEST_APK" -> "The input test APK could not be parsed."
            "NO_MANIFEST" -> "The AndroidManifest.xml could not be found."
            "NO_PACKAGE_NAME" -> "The APK manifest does not declare a package name."
            "INVALID_PACKAGE_NAME" -> "The APK application ID (aka package name) is invalid. See also https://developer.android.com/studio/build/application-id"
            "TEST_SAME_AS_APP" -> "The test package and app package are the same."
            "NO_INSTRUMENTATION" -> "The test apk does not declare an instrumentation."
            "NO_SIGNATURE" -> "The input app apk does not have a signature."
            "INSTRUMENTATION_ORCHESTRATOR_INCOMPATIBLE" -> "The test runner class specified by user or in the test APK's manifest file is not compatible with Android Test Orchestrator. Orchestrator is only compatible with AndroidJUnitRunner version 1.1 or higher. Orchestrator can be disabled by using DO_NOT_USE_ORCHESTRATOR OrchestratorOption."
            "NO_TEST_RUNNER_CLASS" -> "The test APK does not contain the test runner class specified by user or in the manifest file. This can be caused by either of the following reasons: - the user provided a runner class name that's incorrect, or - the test runner isn't built into the test APK (might be in the app APK instead)."
            "NO_LAUNCHER_ACTIVITY" -> "A main launcher activity could not be found."
            "FORBIDDEN_PERMISSIONS" -> "The app declares one or more permissions that are not allowed."
            "INVALID_ROBO_DIRECTIVES" -> "There is a conflict in the provided roboDirectives."
            "INVALID_RESOURCE_NAME" -> "There is at least one invalid resource name in the provided robo directives"
            "INVALID_DIRECTIVE_ACTION" -> "Invalid definition of action in the robo directives (e.g. a click or ignore action includes an input text field)"
            "TEST_LOOP_INTENT_FILTER_NOT_FOUND" -> "There is no test loop intent filter, or the one that is given is not formatted correctly."
            "SCENARIO_LABEL_NOT_DECLARED" -> "The request contains a scenario label that was not declared in the manifest."
            "SCENARIO_LABEL_MALFORMED" -> "There was an error when parsing a label's value."
            "SCENARIO_NOT_DECLARED" -> "The request contains a scenario number that was not declared in the manifest."
            "DEVICE_ADMIN_RECEIVER" -> "Device administrator applications are not allowed."
            "MALFORMED_XC_TEST_ZIP" -> "The zipped XCTest was malformed. The zip did not contain a single .xctestrun file and the contents of the DerivedData/Build/Products directory."
            "BUILT_FOR_IOS_SIMULATOR" -> "The zipped XCTest was built for the iOS simulator rather than for a physical device."
            "NO_TESTS_IN_XC_TEST_ZIP" -> "The .xctestrun file did not specify any test targets."
            "USE_DESTINATION_ARTIFACTS" -> "One or more of the test targets defined in the .xctestrun file specifies \"UseDestinationArtifacts\", which is disallowed."
            "TEST_NOT_APP_HOSTED" -> "XC tests which run on physical devices must have \"IsAppHostedTestBundle\" == \"true\" in the xctestrun file."
            "PLIST_CANNOT_BE_PARSED" -> "An Info.plist file in the XCTest zip could not be parsed."
            "MALFORMED_IPA" -> "The input IPA could not be parsed."
            "MISSING_URL_SCHEME" -> "The application doesn't register the game loop URL scheme."
            "MALFORMED_APP_BUNDLE" -> "The iOS application bundle (.app) couldn't be processed."
            "NO_CODE_APK" -> "APK contains no code. See also https://developer.android.com/guide/topics/manifest/application-element.html#code"
            "INVALID_INPUT_APK" -> "Either the provided input APK path was malformed, the APK file does not exist, or the user does not have permission to access the APK file."
            "INVALID_APK_PREVIEW_SDK" -> "APK is built for a preview SDK which is unsupported"
            else -> "The matrix is INVALID, but there are no further details available."
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

    /**
     * Uploads the given file to cloud storage.
     *
     * @param file the file to be uploaded
     * @param runId a uuid that is unique to this test run.
     * @param storageClient the storage connection for the file to be written to.
     * @param bucketName a unique id for the set of files to be associated with this test.
     * @return a handle to the Storage object in the cloud.
     */
    private fun uploadToCloudStorage(
        file: File, runId: String, storageClient: Storage, bucketName: String
    ): StorageObject =
        FileInputStream(file).use { fileInputStream ->
            storageClient.objects().insert(
                bucketName,
                StorageObject(),
                InputStreamContent("application/octet-stream", fileInputStream).apply {
                    length = file.length()
                }
            ).apply {
                name = "${runId}_${file.name}"
            }.execute()
        }

    /**
     * Downloads the given file from cloud storage.
     */
    private fun downloadFromCloudStorage(
        storageClient: Storage, fileUri: String,
        runId: String, destination: (objectName: String) -> File): File? {
        val matchResult = cloudStorageUrlRegex.find(fileUri) ?: return null
        val (bucketName, objectName) = matchResult.destructured
        return destination(objectName.removePrefix("$runId/")).apply {
            parentFile.mkdirs()
            outputStream().use {
                storageClient.objects()
                    .get(bucketName, objectName)
                    .executeMediaAndDownloadTo(it)
            }
        }
    }

    private fun createConfigProvider(
        ftlDeviceModel: AndroidModel,
        locale: Locale,
        apiLevel: Int
    ): DeviceConfigProvider {
        return object : DeviceConfigProvider {
            override fun getConfigFor(abi: String?): String {
                return requireNotNull(abi)
            }

            override fun getDensity(): Int = ftlDeviceModel.screenDensity

            override fun getLanguage(): String {
                return locale.language
            }

            override fun getRegion(): String? {
                return locale.country
            }

            override fun getAbis() = ftlDeviceModel.supportedAbis

            override fun getApiLevel() = apiLevel
        }
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

    /**
     * An action to register TestLabBuildService to a project.
     */
    class RegistrationAction(
        private val testLabExtension: TestLabGradlePluginExtension,
        private val providerFactory: ProviderFactory,
    ) {
        companion object {
            /**
             * Get build service name that works even if build service types come from different
             * class loaders. If the service name is the same, and some type T is defined in two
             * class loaders L1 and L2. E.g. this is true for composite builds and other project
             * setups (see b/154388196).
             *
             * Registration of service may register (T from L1) or (T from L2). This means that
             * querying it with T from other class loader will fail at runtime. This method makes
             * sure both T from L1 and T from L2 will successfully register build services.
             *
             * Copied from
             * com.android.build.gradle.internal.services.BuildServicesKt.getBuildServiceName.
             */
            private fun getBuildServiceName(type: Class<*>, project: Project): String {
                return type.name + "_" + perClassLoaderConstant + "_" + project.path
            }

            fun getBuildService(project: Project): Provider<TestLabBuildService> {
                val serviceName = getBuildServiceName(TestLabBuildService::class.java, project)
                return project.gradle.sharedServices.registerIfAbsent(
                    serviceName,
                    TestLabBuildService::class.java,
                ) {
                    throw IllegalStateException("Service $serviceName is not registered.")
                }
            }

            /**
             *  Used to get unique build service name. Each class loader will initialize its own
             *  version.
             */
            private val perClassLoaderConstant = UUID.randomUUID().toString()

            private const val WELL_KNOWN_CREDENTIALS_FILE = "application_default_credentials.json"
            private const val CLOUDSDK_CONFIG_DIRECTORY = "gcloud"

            private fun getGcloudApplicationDefaultCredentialsFile(): File {
                val os = System.getProperty("os.name", "").lowercase(Locale.US)
                val envPath = System.getenv("CLOUDSDK_CONFIG") ?: ""
                val cloudConfigPath = if (envPath.isNotBlank()) {
                    File(envPath)
                } else if (os.indexOf("windows") >= 0) {
                    val appDataPath = File(System.getenv("APPDATA"))
                    File(appDataPath, CLOUDSDK_CONFIG_DIRECTORY)
                } else {
                    val configPath = File(System.getProperty("user.home", ""), ".config")
                    File(configPath, CLOUDSDK_CONFIG_DIRECTORY)
                }
                return File(cloudConfigPath, WELL_KNOWN_CREDENTIALS_FILE)
            }

            private fun getQuotaProjectName(credentialFile: File): String {
                if (!credentialFile.exists() || !credentialFile.isFile) {
                    throwCredentialNotFoundError()
                }
                val quotaProjectName = credentialFile.inputStream().use {
                    val parser = JsonObjectParser(Utils.getDefaultJsonFactory())
                    val fileContents = parser.parseAndClose<GenericJson>(
                        it, StandardCharsets.UTF_8,
                        GenericJson::class.java
                    )

                    val quota = fileContents["quota_project_id"] as? String
                    if (!quota.isNullOrBlank()) {
                        quota
                    } else {
                        // Falls-back to project ID.
                        fileContents["project_id"] as? String
                    }
                }
                if (quotaProjectName.isNullOrBlank()) {
                    throwCredentialNotFoundError()
                }
                return quotaProjectName
            }

            private fun throwCredentialNotFoundError(): Nothing {
                error("""
                    Unable to find the application-default credentials to send a request to
                    Firebase TestLab. Please initialize your credentials using gcloud CLI.
                    Examples:
                      gcloud config set project ${"$"}YOUR_PROJECT_ID
                      gcloud auth application-default login
                      gcloud auth application-default set-quota-project ${"$"}YOUR_PROJECT_ID
                    Please read https://cloud.google.com/sdk/gcloud for details.
                """.trimIndent())
            }
        }

        /**
         * Register [TestLabBuildService] to a registry if absent.
         */
        fun registerIfAbsent(project: Project): Provider<TestLabBuildService> {
            return project.gradle.sharedServices.registerIfAbsent(
                getBuildServiceName(TestLabBuildService::class.java, project),
                TestLabBuildService::class.java,
            ) { buildServiceSpec ->
                configure(buildServiceSpec.parameters)
            }
        }

        private fun configure(params: Parameters) {
            params.credentialFile.fileProvider(providerFactory.provider {
                if (testLabExtension.serviceAccountCredentials.isPresent) {
                    testLabExtension.serviceAccountCredentials.get().asFile
                } else {
                    getGcloudApplicationDefaultCredentialsFile()
                }
            })
            params.quotaProjectName.set(params.credentialFile.map {
                getQuotaProjectName(it.asFile)
            })
            params.cloudStorageBucket.set(providerFactory.provider {
                testLabExtension.testOptions.results.cloudStorageBucket
            })

            params.timeoutMinutes.set(providerFactory.provider {
                testLabExtension.testOptions.execution.timeoutMinutes
            })
            params.maxTestReruns.set(providerFactory.provider {
                testLabExtension.testOptions.execution.maxTestReruns
            })
            params.failFast.set(providerFactory.provider {
                testLabExtension.testOptions.execution.failFast
            })
            params.numUniformShards.set( providerFactory.provider {
                testLabExtension.testOptions.execution.numUniformShards
            })
            params.grantedPermissions.set(providerFactory.provider {
                testLabExtension.testOptions.fixture.grantedPermissions
            })
            params.extraDeviceFiles.set(testLabExtension.testOptions.fixture.extraDeviceFiles)
            params.networkProfile.set(providerFactory.provider {
                testLabExtension.testOptions.fixture.networkProfile
            })
            params.resultsHistoryName.set(providerFactory.provider {
                testLabExtension.testOptions.results.resultsHistoryName
            })
            params.directoriesToPull.set(testLabExtension.testOptions.results.directoriesToPull)
            params.recordVideo.set(providerFactory.provider {
                testLabExtension.testOptions.results.recordVideo
            })
            params.performanceMetrics.set(providerFactory.provider {
                testLabExtension.testOptions.results.performanceMetrics
            })
        }
    }
}

/**
 * Encapsulates result of a FTL test run.
 *
 * @property testPassed true when all test cases in the test suite is passed.
 * @property resultsProto test suite result protobuf message. This can be null if
 *     the test runner exits unexpectedly.
 */
data class FtlTestRunResult(
    val testPassed: Boolean,
    val resultsProto: TestSuiteResult?,
)
