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

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.instrumentation.StaticTestData
import com.android.builder.testing.api.DeviceConfigProvider
import com.android.tools.firebase.testlab.gradle.FixtureImpl
import com.android.tools.firebase.testlab.gradle.ManagedDeviceImpl
import com.android.tools.firebase.testlab.gradle.UtpTestSuiteResultMerger
import com.android.tools.utp.plugins.host.device.info.proto.AndroidTestDeviceInfoProto
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.util.Utils
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpRequestFactory
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.GenericJson
import com.google.api.client.json.JsonObjectParser
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.Key
import com.google.api.services.storage.Storage
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
import com.google.common.annotations.VisibleForTesting
import com.google.firebase.testlab.gradle.TestLabGradlePluginExtension
import com.google.testing.platform.proto.api.core.TestStatusProto.TestStatus
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayOutputStream
import java.io.File
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

    @get:VisibleForTesting
    internal val apiClientLogger = Logger.getLogger("com.google.api.client").apply {
        level = Level.WARNING
    }

    companion object {
        const val TEST_RESULT_PB_FILE_NAME = "test-result.pb"
        const val clientApplicationName: String = "Firebase TestLab Gradle Plugin"
        const val xGoogUserProjectHeaderKey: String = "X-Goog-User-Project"

        const val INSTRUMENTATION_TEST_SHARD_FIELD = "shardingOption"
        const val TEST_MATRIX_FLAKY_TEST_ATTEMPTS_FIELD = "flakyTestAttempts"
        const val TEST_MATRIX_FAIL_FAST_FIELD = "failFast"

        const val CHECK_TEST_STATE_WAIT_MS = 10 * 1000L;

        private const val STUB_APP_CONFIG_NAME: String =
                "_internal-test-lab-gradle-plugin-configuration-stub-app"
        private const val STUB_APP_NAME: String = "androidx.test.services"

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

        class SmartSharding: GenericJson() {
            @Key var targetedShardDuration: String? = null
        }

        class ShardingOption: GenericJson() {
            @Key var uniformSharding: UniformSharding? = null

            @Key var smartSharding: SmartSharding? = null
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
        val targetedShardDurationMinutes: Property<Int>
        val grantedPermissions: Property<String>
        val extraDeviceFiles: MapProperty<String, String>
        val networkProfile: Property<String>
        val resultsHistoryName: Property<String>
        val directoriesToPull: ListProperty<String>
        val recordVideo: Property<Boolean>
        val performanceMetrics: Property<Boolean>
        val stubAppApk: RegularFileProperty
        val useOrchestrator: Property<Boolean>
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
        get() = parameters.numUniformShards.get()

    val targetedShardDurationMinutes: Int
        get() = parameters.targetedShardDurationMinutes.get()

    private val testResultProcessor: TestResultProcessor by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TestResultProcessor(parameters.directoriesToPull.get())
    }

    fun runTestsOnDevice(
        deviceName: String,
        deviceId: String,
        deviceApiLevel: Int,
        deviceLocale: Locale,
        deviceOrientation: ManagedDeviceImpl.Orientation,
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

        val testRunStorage = TestRunStorage(
            runRequestId,
            bucketName,
            Storage.Builder(
                httpTransport,
                jacksonFactory,
                httpRequestInitializer
            ).apply {
                applicationName = clientApplicationName
            }.build())

        val testApkStorageObject = testRunStorage.uploadToStorage(testData.testApk)

        val configProvider = createConfigProvider(
            ftlDeviceModel, deviceLocale, deviceApiLevel
        )

        // If tested apk is null, this is a self-instrument test (e.g. library module).
        val testedApkFile = testData.testedApkFinder(configProvider).firstOrNull()
        val appApkStorageObject = testRunStorage.uploadToStorage(
            testedApkFile ?: parameters.stubAppApk.asFile.get())
        val useStubApp = testedApkFile == null

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
                            val storageObject = testRunStorage.uploadToStorage(file)
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
                    appPackageId = if (useStubApp) {
                        STUB_APP_NAME
                    } else {
                        testData.testedApplicationId
                    }
                    testPackageId = testData.applicationId
                    testRunnerClass = testData.instrumentationRunner

                    if(parameters.useOrchestrator.get()) {
                        orchestratorOption = "USE_ORCHESTRATOR"
                    }

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
                    testRunStorage.downloadFromStorage(suiteOverview.xmlSource.fileUri) {
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

    private fun createShardingOption(): ShardingOption? = when {

        numUniformShards == 0 && targetedShardDurationMinutes == 0 -> null
        numUniformShards != 0 && targetedShardDurationMinutes != 0 -> {
            error("""
                Only one sharding option should be set for "numUniformShards" or
                "targetedShardDurationMinutes" in firebaseTestLab.testOptions.execution.
            """.trimIndent())

        }
        numUniformShards != 0 -> ShardingOption().apply {
            uniformSharding = UniformSharding().apply {
                numShards = numUniformShards
            }
        }
        else -> ShardingOption().apply {
            smartSharding = SmartSharding().apply {
                targetedShardDuration = "${targetedShardDurationMinutes * 60}s"
            }
        }
    }

    private fun getTestSuiteResult(
        toolResultsClient: ToolResults,
        testMatrix: TestMatrix,
        testExecution: TestExecution,
        deviceInfoFile: File,
        testRunStorage: TestRunStorage,
        resultsOutDir: File
    ): TestSuiteResult {

        val results = testExecution.toolResultsStep
        return if (results != null) {

            val projectId = results.projectId
            val historyId = results.historyId
            val executionId = results.executionId
            val stepId = results.stepId

            // Need the latest version of google-api-client to use
            // toolResultsClient.projects().histories().executions().steps().testCases().list().
            // Manually calling this API until this is available.
            val httpRequestFactory: HttpRequestFactory = httpTransport.createRequestFactory(httpRequestInitializer)
            val url = "https://toolresults.googleapis.com/toolresults/v1beta3/projects/$projectId/histories/$historyId/executions/$executionId/steps/$stepId/testCases"
            val testCaseRequest = httpRequestFactory.buildGetRequest(GenericUrl(url))
            val parser = JsonObjectParser(Utils.getDefaultJsonFactory())
            testCaseRequest.setParser(parser)

            testResultProcessor.toUtpResult(
                resultsOutDir,
                toolResultsClient.projects().histories().executions().steps().get(
                    projectId,
                    historyId,
                    executionId,
                    stepId
                ).execute(),
                toolResultsClient.projects().histories().executions().steps().thumbnails().list(
                    projectId,
                    historyId,
                    executionId,
                    stepId
                ).execute()?.thumbnails,
                testRunStorage,
                deviceInfoFile,
                testCaseRequest.execute().content.use { response ->
                    parser.parseAndClose<TestCases>(
                        response, StandardCharsets.UTF_8, TestCases::class.java)
                },
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
    class RegistrationAction(private val project: Project) {

        private val testLabExtension: TestLabGradlePluginExtension =
                project.extensions.getByType(TestLabGradlePluginExtension::class.java)
        private val androidExtension: CommonExtension<*, *, *, *, *> =
                project.extensions.getByType(CommonExtension::class.java)
        private val providerFactory: ProviderFactory = project.providers
        private val configurationContainer: ConfigurationContainer = project.configurations

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
        fun registerIfAbsent(): Provider<TestLabBuildService> {
            createConfigIfAbsent()
            return project.gradle.sharedServices.registerIfAbsent(
                getBuildServiceName(TestLabBuildService::class.java, project),
                TestLabBuildService::class.java,
            ) { buildServiceSpec ->
                configure(buildServiceSpec.parameters)
            }
        }

        private fun createConfigIfAbsent() {
            if (configurationContainer.findByName(STUB_APP_CONFIG_NAME) == null) {
                configurationContainer.create(STUB_APP_CONFIG_NAME).apply {
                    isVisible = false
                    isTransitive = true
                    isCanBeConsumed = false
                    description = "A configuration to resolve the stub app dependencies for " +
                            "Firebase Test Plugin."
                }
                project.dependencies.add(
                        STUB_APP_CONFIG_NAME,
                        "androidx.test.services:test-services:1.4.2")
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
            params.targetedShardDurationMinutes.set(providerFactory.provider {
                testLabExtension.testOptions.execution.targetedShardDurationMinutes
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
            params.stubAppApk.fileProvider(providerFactory.provider {
                configurationContainer.getByName(STUB_APP_CONFIG_NAME).singleFile
            })
            params.useOrchestrator.set(providerFactory.provider {
                when(androidExtension.testOptions.execution.uppercase()) {
                    "ANDROID_TEST_ORCHESTRATOR", "ANDROIDX_TEST_ORCHESTRATOR" -> true
                    else -> false
                }
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
