/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.testing.utp

import com.android.build.api.variant.impl.AndroidVersionImpl
import com.android.build.gradle.internal.SdkComponentsBuildService.VersionedSdkLoader
import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.test.ApkBundlesFinder
import com.android.build.gradle.internal.test.ApksFinder
import com.android.build.gradle.internal.testing.StaticTestData
import com.android.builder.testing.api.DeviceConfigProvider
import com.android.builder.testing.api.DeviceConnector
import com.android.sdklib.BuildToolInfo
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.TextFormat.escapeDoubleQuotesAndBackslashes
import com.google.testing.platform.proto.api.config.RunnerConfigProto
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.absolutePathString

/**
 * Unit tests for [UtpConfigFactory].
 */
class UtpConfigFactoryTest {
    @get:Rule var mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()
    @get:Rule var temporaryFolder = TemporaryFolder()

    @Mock private lateinit var versionedSdkLoader: VersionedSdkLoader
    @Mock private lateinit var mockAppApk: File
    @Mock private lateinit var mockTestApk: File
    @Mock private lateinit var mockHelperApk: File
    @Mock private lateinit var mockDevice: DeviceConnector
    @Mock private lateinit var mockOutputDir: File
    @Mock private lateinit var mockCoverageOutputDir: File
    @Mock private lateinit var mockTmpDir: File
    @Mock private lateinit var mockSdkDir: File
    @Mock private lateinit var mockAdb: RegularFile
    @Mock private lateinit var mockAdbFile: File
    @Mock private lateinit var mockAdbProvider: Provider<RegularFile>
    @Mock private lateinit var mockBuildToolInfo: BuildToolInfo
    @Mock private lateinit var mockBuildToolInfoProvider: Provider<BuildToolInfo>
    @Mock private lateinit var mockemulatorControlConfig: EmulatorControlConfig
    @Mock private lateinit var mockRetentionConfig: RetentionConfig
    @Mock private lateinit var mockResultListenerClientCert: File
    @Mock private lateinit var mockResultListenerClientPrivateKey: File
    @Mock private lateinit var mockTrustCertCollection: File
    @Mock private lateinit var mockDependencyApk: File

    private lateinit var testResultListenerServerMetadata: UtpTestResultListenerServerMetadata
    private lateinit var testExtractedSdkApks: List<List<Path>>
    private lateinit var testTargetApkConfigBundle: TargetApkConfigBundle



    private val testData = StaticTestData(
        testedApplicationId = "com.example.application",
        applicationId = "com.example.application.test",
        instrumentationTargetPackageId = "com.example.application",
        instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner",
        instrumentationRunnerArguments = emptyMap(),
        animationsDisabled = false,
        isTestCoverageEnabled = false,
        minSdkVersion = AndroidVersionImpl(1),
        isLibrary = false,
        flavorName = "",
        testApk = mockFile("testApk.apk"),
        testDirectories = emptyList(),
        testedApks = object: ApksFinder {
            override fun findApks(deviceConfigProvider: DeviceConfigProvider) = emptyList<File>()
        },
        privacySandboxApks = object: ApkBundlesFinder {
            override fun findBundles(deviceConfigProvider: DeviceConfigProvider) =
                listOf(listOf(mockPath("mockDependencyApkPath")))
        }
    )

    private val utpDependencies: UtpDependencies = mock(UtpDependencies::class.java) {
        FakeConfigurableFileCollection(
            mockFile("path-to-${it.method.name.removePrefix("get")}.jar"))
    }

    private val mockDependencyApkPath = mockPath("mockDependencyApkPath")

    private fun mockFile(absolutePath: String): File = mock(File::class.java).also {
        `when`(it.absolutePath).thenReturn(absolutePath)
    }

    private fun mockPath(absolutePath: String): Path = mock(Path::class.java,
            Answers.RETURNS_DEEP_STUBS).also {
        `when`(it.absolutePathString()).thenReturn(absolutePath)
    }

    @Before
    fun setupMocks() {
        `when`(mockDevice.apiLevel).thenReturn(30)
        `when`(mockDevice.serialNumber).thenReturn("mockDeviceSerialNumber")
        `when`(mockDevice.name).thenReturn("mockDeviceName")
        `when`(mockOutputDir.absolutePath).thenReturn("mockOutputDirPath")
        `when`(mockCoverageOutputDir.absolutePath).thenReturn("mockCoverageOutputDir")
        `when`(mockTmpDir.absolutePath).thenReturn("mockTmpDirPath")
        `when`(mockAppApk.absolutePath).thenReturn("mockAppApkPath")
        `when`(mockTestApk.absolutePath).thenReturn("mockTestApkPath")
        `when`(mockHelperApk.absolutePath).thenReturn("mockHelperApkPath")
        `when`(versionedSdkLoader.sdkDirectoryProvider).thenReturn(
            FakeGradleProvider(
                FakeGradleDirectory(mockSdkDir)
            )
        )
        `when`(mockSdkDir.absolutePath).thenReturn("mockSdkDirPath")
        `when`(versionedSdkLoader.adbExecutableProvider).thenReturn(mockAdbProvider)
        `when`(mockAdbProvider.get()).thenReturn(mockAdb)
        `when`(mockAdb.asFile).thenReturn(mockAdbFile)
        `when`(mockAdbFile.absolutePath).thenReturn("mockAdbPath")
        `when`(versionedSdkLoader.buildToolInfoProvider).thenReturn(mockBuildToolInfoProvider)
        `when`(mockBuildToolInfoProvider.get()).thenReturn(mockBuildToolInfo)
        `when`(mockBuildToolInfo.getPath(ArgumentMatchers.any())).then {
            when (it.getArgument<BuildToolInfo.PathId>(0)) {
                BuildToolInfo.PathId.AAPT -> "mockAaptPath"
                BuildToolInfo.PathId.DEXDUMP -> "mockDexdumpPath"
                else -> null
            }
        }
        `when`(mockResultListenerClientCert.absolutePath).thenReturn("mockResultListenerClientCertPath")
        `when`(mockResultListenerClientPrivateKey.absolutePath).thenReturn("mockResultListenerClientPrivateKeyPath")
        `when`(mockTrustCertCollection.absolutePath).thenReturn("mockTrustCertCollectionPath")
        `when`(mockDependencyApk.toPath()).thenReturn(mockDependencyApkPath)
        testResultListenerServerMetadata = UtpTestResultListenerServerMetadata(
                serverCert = mockTrustCertCollection,
                serverPort = 1234,
                clientCert = mockResultListenerClientCert,
                clientPrivateKey = mockResultListenerClientPrivateKey
        )
        testExtractedSdkApks = listOf(listOf(mockPath("mockDependencyApkPath")))
        testTargetApkConfigBundle = TargetApkConfigBundle(
                appApks = listOf(mockAppApk, mockTestApk),
                isSplitApk = false
        )
    }

    private fun createForLocalDevice(
            testData: StaticTestData = this.testData,
            useOrchestrator: Boolean = false,
            uninstallIncompatibleApks: Boolean = false,
            additionalTestOutputDir: File? = null,
            installApkTimeout: Int? = null,
            shardConfig: ShardConfig? = null,
            targetApkConfigBundle: TargetApkConfigBundle = testTargetApkConfigBundle,
            extractedSdkApks: List<List<Path>> = testExtractedSdkApks,
            cleanTestArtifacts: Boolean = false,
    ): RunnerConfigProto.RunnerConfig {
        return UtpConfigFactory().createRunnerConfigProtoForLocalDevice(
                mockDevice,
                testData,
                targetApkConfigBundle,
                listOf("-additional_install_option"),
                listOf(mockHelperApk),
                uninstallIncompatibleApks,
                utpDependencies,
                versionedSdkLoader,
                mockOutputDir,
                mockTmpDir,
                mockemulatorControlConfig,
                mockRetentionConfig,
                mockCoverageOutputDir,
                useOrchestrator,
                additionalTestOutputDir,
                1234,
                mockResultListenerClientCert,
                mockResultListenerClientPrivateKey,
                mockTrustCertCollection,
                installApkTimeout,
                extractedSdkApks,
                cleanTestArtifacts,
                shardConfig,
        )
    }

    private fun createForManagedDevice(
            testData: StaticTestData = this.testData,
            useOrchestrator: Boolean = false,
            additionalTestOutputDir: File? = null,
            shardConfig: ShardConfig? = null,
            emulatorGpuFlag: String = "auto-no-window",
            showEmulatorKernelLogging: Boolean = false,
            installApkTimeout: Int? = null,
            targetApkConfigBundle: TargetApkConfigBundle = testTargetApkConfigBundle,
            cleanTestArtifacts: Boolean = false,
    ): RunnerConfigProto.RunnerConfig {
        val managedDevice = UtpManagedDevice(
                "deviceName",
                "avdName",
                29,
                "x86",
                "path/to/gradle/avd",
                ":app:deviceNameDebugAndroidTest",
                "path/to/emulator",
                false)
        return UtpConfigFactory().createRunnerConfigProtoForManagedDevice(
                managedDevice,
                testData,
                targetApkConfigBundle,
                listOf("-additional_install_option"),
                listOf(mockHelperApk),
                utpDependencies,
                versionedSdkLoader,
                mockOutputDir,
                mockTmpDir,
                mockemulatorControlConfig,
                mockRetentionConfig,
                mockCoverageOutputDir,
                additionalTestOutputDir,
                useOrchestrator,
                testResultListenerServerMetadata,
                emulatorGpuFlag,
                showEmulatorKernelLogging,
                installApkTimeout,
                testExtractedSdkApks,
                cleanTestArtifacts,
                shardConfig,
        )
    }

    @Test
    fun createRunnerConfigProtoForLocalDevice() {
        val runnerConfigProto = createForLocalDevice()
        assertRunnerConfigProto(runnerConfigProto)
    }

    @Test
    fun createRunnerConfigProtoForLocalDeviceWithSplitApk() {
        val runnerConfigProto = createForLocalDevice(
                targetApkConfigBundle = TargetApkConfigBundle(
                        appApks = listOf(mockAppApk, mockTestApk),
                        isSplitApk = true
                )
        )
        assertRunnerConfigProto(
                runnerConfig = runnerConfigProto,
                isSplitApk = true
        )
    }
    @Test
    fun createRunnerConfigProtoForLocalDeviceUseOrchestrator() {
        val runnerConfigProto = createForLocalDevice(useOrchestrator = true)
        assertRunnerConfigProto(runnerConfigProto, useOrchestrator = true)
    }

    @Test
    fun createRunnerConfigProtoForLocalDeviceInstallApkTimeout() {
        val runnerConfigProto = createForLocalDevice(installApkTimeout = 5)
        assertRunnerConfigProto(runnerConfigProto, installApkTimeout = 5)
    }

    @Test
    fun createRunnerConfigProtoForLocalDeviceWithNoAnimation() {
        val runnerConfigProto = createForLocalDevice(
            testData = testData.copy(
                animationsDisabled = true
            )
        )

        assertRunnerConfigProto(
            runnerConfigProto,
            noWindowAnimation = true)
    }

    @Test
    fun createRunnerConfigProtoWithEmulatorAccess() {
        // First we write a "fake" discover file
        // That indicate we have security features enabled
        val discoveryDirectory = computeRegistrationDirectoryContainer()!!.resolve("avd/running/")
        if (!discoveryDirectory.toFile().exists()) {
            discoveryDirectory.toFile().mkdirs()
        }
        val filePath = discoveryDirectory.resolve("pid_123.ini")
        val jwkFolder = temporaryFolder.newFolder("jwks")
        val content = """
            port.serial=mockDeviceSerialNumber
            grpc.port=1234
            grpc.jwks=${jwkFolder}
            grpc.allowlist=/unused/access.json
        """.trimIndent()
        Files.writeString(filePath, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE)

        `when`(mockemulatorControlConfig.enabled).thenReturn(true)
        `when`(mockemulatorControlConfig.secondsValid).thenReturn(100)
        `when`(mockDevice.serialNumber).thenReturn("emulator-mockDeviceSerialNumber")

        assertThat(mockemulatorControlConfig.enabled).isTrue()

        val runnerConfigProto = createForLocalDevice()

        // Next we extract the token and jkwfile as those
        // are dynamically created.
        val printed = printProto(runnerConfigProto)
        val tokenRegex = "token: \"(.*)\""
        val jwkfileRegex = "jwk_file: \"(.*)\""

        // Both the token and the location where we wrote the file
        // should be set.
        assertThat(printed).containsMatch(tokenRegex)
        assertThat(printed).containsMatch(jwkfileRegex)

        // Let's extract them
        val token = tokenRegex.toRegex().find(printed)?.groupValues?.getOrNull(1) ?: "Not Found"
        val jwkfile = jwkfileRegex.toRegex().find(printed)?.groupValues?.getOrNull(1) ?: "Not found"

        assertRunnerConfigProto(
            runnerConfigProto,
            deviceId = "emulator-mockDeviceSerialNumber",
            instrumentationArgs = mapOf("grpc.port" to "1234", "grpc.token" to token),
            emulatorControlConfig = """
                emulator_grpc_port: 1234
                token: "${token}"
                jwk_file: "${jwkfile}"
                seconds_valid: 100
            """
        )
    }

    @Test
    fun createRunnerConfigProtoWithEmulatorAccessForManagedDevice() {
        val aud = setOf(*arrayOf("a", "b"))
        `when`(mockemulatorControlConfig.enabled).thenReturn(true)
        `when`(mockemulatorControlConfig.secondsValid).thenReturn(100)
        `when`(mockemulatorControlConfig.allowedEndpoints).thenReturn(aud)
        assertThat(mockemulatorControlConfig.enabled).isTrue()

        val runnerConfigProto = createForManagedDevice()

        // Next we extract the token and jkwfile as those
        // are dynamically created.
        val printed = printProto(runnerConfigProto)

        assertRunnerConfigProto(
            runnerConfigProto,
            deviceId = ":app:deviceNameDebugAndroidTest",
            useGradleManagedDeviceProvider = true,
            emulatorControlConfig = """
                seconds_valid: 100
                allowed_endpoints: "a"
                allowed_endpoints: "b"
            """
        )
    }

    @Test
    fun createRunnerConfigProtoWithIcebox() {
        `when`(mockRetentionConfig.enabled).thenReturn(true)
        `when`(mockRetentionConfig.retainAll).thenReturn(true)

        val runnerConfigProto = createForLocalDevice()

        assertRunnerConfigProto(
            runnerConfigProto,
            instrumentationArgs = mapOf("debug" to "true"),
            iceboxConfig = """
                app_package: "com.example.application"
                emulator_grpc_address: "localhost"
                emulator_grpc_port: 8554
                setup_strategy: CONNECT_BEFORE_ALL_TEST
            """)
    }

    @Test
    fun createRunnerConfigProtoWithDebugAndIcebox() {
        `when`(mockRetentionConfig.enabled).thenReturn(true)
        `when`(mockRetentionConfig.retainAll).thenReturn(true)

        val runnerConfigProto = createForLocalDevice(
            testData = testData.copy(instrumentationRunnerArguments = mapOf("debug" to "true")))

        assertRunnerConfigProto(runnerConfigProto, instrumentationArgs = mapOf("debug" to "true"))
    }

    @Test
    fun createRunnerConfigProtoWithIceboxAndCompression() {
        `when`(mockRetentionConfig.enabled).thenReturn(true)
        `when`(mockRetentionConfig.maxSnapshots).thenReturn(2)
        `when`(mockRetentionConfig.retainAll).thenReturn(false)
        `when`(mockRetentionConfig.compressSnapshots).thenReturn(true)

        val runnerConfigProto = createForLocalDevice()

        assertRunnerConfigProto(
            runnerConfigProto,
            instrumentationArgs = mapOf("debug" to "true"),
            iceboxConfig = """
                app_package: "com.example.application"
                emulator_grpc_address: "localhost"
                emulator_grpc_port: 8554
                max_snapshot_number: 2
                snapshot_compression: TARGZ
                setup_strategy: CONNECT_BEFORE_ALL_TEST
            """)
    }

    @Test
    fun createRunnerConfigProtoWithIceboxAndOrchestrator() {
        `when`(mockRetentionConfig.enabled).thenReturn(true)
        `when`(mockRetentionConfig.retainAll).thenReturn(true)

        val runnerConfigProto = createForLocalDevice(useOrchestrator = true)

        assertRunnerConfigProto(
            runnerConfigProto,
            useOrchestrator = true,
            instrumentationArgs = mapOf("debug" to "true"),
            iceboxConfig = """
                app_package: "com.example.application"
                emulator_grpc_address: "localhost"
                emulator_grpc_port: 8554
                setup_strategy: RECONNECT_BETWEEN_TEST_CASES
            """)
    }

    @Test
    fun createRunnerConfigProtoForManagedDevice() {
        val runnerConfigProto = createForManagedDevice()

        assertRunnerConfigProto(
            runnerConfigProto,
            deviceId = ":app:deviceNameDebugAndroidTest",
            useGradleManagedDeviceProvider = true
        )
    }

    @Test
    fun createRunnerConfigProtoForManagedDeviceWithSplitApk() {
        val runnerConfigProto = createForManagedDevice(
                targetApkConfigBundle = TargetApkConfigBundle(
                        appApks = listOf(mockAppApk, mockTestApk),
                        isSplitApk = true
                )
        )

        assertRunnerConfigProto(
                runnerConfigProto,
                deviceId = ":app:deviceNameDebugAndroidTest",
                useGradleManagedDeviceProvider = true,
                isSplitApk = true,
        )
    }

    @Test
    fun createRunnerConfigProtoForManagedDeviceUseOrchestrator() {
        val runnerConfigProto = createForManagedDevice(useOrchestrator = true)

        assertRunnerConfigProto(
            runnerConfigProto,
            deviceId = ":app:deviceNameDebugAndroidTest",
            useOrchestrator = true,
            useGradleManagedDeviceProvider = true
        )
    }

    @Test
    fun createRunnerConfigProtoForManagedDeviceInstallApkTimeout() {
        val runnerConfigProto = createForManagedDevice(installApkTimeout = 5)
        assertRunnerConfigProto(runnerConfigProto,
            deviceId = ":app:deviceNameDebugAndroidTest",
            useGradleManagedDeviceProvider = true,
            installApkTimeout = 5)
    }

    @Test
    fun createRunnerConfigManagedDeviceWithRetention() {
        `when`(mockRetentionConfig.enabled).thenReturn(true)
        `when`(mockRetentionConfig.retainAll).thenReturn(true)

        val runnerConfigProto = createForManagedDevice()

        assertRunnerConfigProto(
            runnerConfigProto,
            deviceId = ":app:deviceNameDebugAndroidTest",
            useGradleManagedDeviceProvider = true,
            instrumentationArgs = mapOf("debug" to "true"),
            iceboxConfig = """
                app_package: "com.example.application"
                emulator_grpc_address: "localhost"
                setup_strategy: CONNECT_BEFORE_ALL_TEST
            """
        )
    }

    @Test
    fun createRunnerConfigProtoForLocalDeviceWithTestCoverage() {
        val runnerConfigProto = createForLocalDevice(
            testData = testData.copy(isTestCoverageEnabled = true)
        )

        val outputOnHost = "mockCoverageOutputDir${File.separator}"
        assertRunnerConfigProto(
            runnerConfigProto,
            instrumentationArgs = mapOf(
                "coverage" to "true",
                "coverageFile" to "/data/data/com.example.application/coverage.ec",
            ),
            testCoverageConfig = """
                single_coverage_file: "/data/data/com.example.application/coverage.ec"
                run_as_package_name: "com.example.application"
                output_directory_on_host: "${escapeDoubleQuotesAndBackslashes(outputOnHost)}"
            """
        )
    }

    @Test
    fun createRunnerConfigProtoForLocalDeviceWithTestCoverageAndOrchestrator() {
        val runnerConfigProto = createForLocalDevice(
            testData = testData.copy(isTestCoverageEnabled = true),
            useOrchestrator = true
        )

        val outputOnHost = "mockCoverageOutputDir${File.separator}"
        assertRunnerConfigProto(
            runnerConfigProto,
            useOrchestrator = true,
            instrumentationArgs = mapOf(
                "coverage" to "true",
                "coverageFilePath" to "/data/data/com.example.application/coverage_data/",
            ),
            testCoverageConfig = """
                multiple_coverage_files_in_directory: "/data/data/com.example.application/coverage_data/"
                run_as_package_name: "com.example.application"
                output_directory_on_host: "${escapeDoubleQuotesAndBackslashes(outputOnHost)}"
            """
        )
    }

    @Test
    fun createRunnerConfigProtoForLocalDeviceWithTestCoverageAndTestStorageService() {
        val runnerConfigProto = createForLocalDevice(
            testData = testData.copy(
                isTestCoverageEnabled = true,
                instrumentationRunnerArguments = mapOf("useTestStorageService" to "true"))
        )

        val outputOnHost = "mockCoverageOutputDir${File.separator}"
        assertRunnerConfigProto(
            runnerConfigProto,
            useTestStorageService = true,
            instrumentationArgs = mapOf(
                "coverage" to "true",
                "coverageFile" to "/data/data/com.example.application/coverage.ec",
                "useTestStorageService" to "true",
            ),
            testCoverageConfig = """
                single_coverage_file: "/data/data/com.example.application/coverage.ec"
                run_as_package_name: "com.example.application"
                output_directory_on_host: "${escapeDoubleQuotesAndBackslashes(outputOnHost)}"
                use_test_storage_service: true
            """
        )
    }

    @Test
    fun createRunnerConfigProtoForManagedDeviceWithTestCoverage() {
        val runnerConfigProto = createForManagedDevice(
            testData = testData.copy(isTestCoverageEnabled = true)
        )

        val outputOnHost = "mockCoverageOutputDir${File.separator}"
        assertRunnerConfigProto(
            runnerConfigProto,
            deviceId = ":app:deviceNameDebugAndroidTest",
            useGradleManagedDeviceProvider = true,
            instrumentationArgs = mapOf(
                "coverage" to "true",
                "coverageFile" to "/data/data/com.example.application/coverage.ec",
            ),
            testCoverageConfig = """
                single_coverage_file: "/data/data/com.example.application/coverage.ec"
                run_as_package_name: "com.example.application"
                output_directory_on_host: "${escapeDoubleQuotesAndBackslashes(outputOnHost)}"
            """
        )
    }

    @Test
    fun createRunnerConfigProtoForLocalDeviceWithShardConfig() {
        val runnerConfigProto = createForLocalDevice(
            shardConfig = ShardConfig(totalCount = 10, index = 2))
        assertRunnerConfigProto(
            runnerConfigProto,
            // TODO(b/201577913): remove
            instrumentationArgs = mapOf(
                "numShards" to "10",
                "shardIndex" to "2"
            ),
            shardingConfig = """
                shard_count: 10
                shard_index: 2
            """
        )
    }

    @Test
    fun createRunnerConfigProtoForLocalDeviceWithUninstallIncompatibleApks() {
        val runnerConfigProto = createForLocalDevice(
            uninstallIncompatibleApks = true
        )
        assertRunnerConfigProto(
            runnerConfigProto,
            uninstallIncompatibleApks = true,
        )
    }

    @Test
    fun createLocalDeviceRunnerConfigProtoToUninstallApksAfterTest() {
        val runnerConfigProto = createForLocalDevice(
                cleanTestArtifacts = true
        )
        assertRunnerConfigProto(
                runnerConfigProto,
                isUninstallAfterTest = true,
        )
    }

    @Test
    fun createManagedDeviceRunnerConfigProtoToUninstallApksAfterTest() {
        val runnerConfigProto = createForManagedDevice(
                cleanTestArtifacts = true
        )
        assertRunnerConfigProto(
                runnerConfigProto,
                useGradleManagedDeviceProvider = true,
                deviceId = ":app:deviceNameDebugAndroidTest",
                isUninstallAfterTest = true,
        )
    }

    @Test
    fun createRunnerConfigProtoForManagedDeviceWithShardConfig() {
        val runnerConfigProto = createForManagedDevice(
            shardConfig = ShardConfig(totalCount = 10, index = 2))
        assertRunnerConfigProto(
            runnerConfigProto,
            // TODO(b/201577913): remove
            instrumentationArgs = mapOf(
                "numShards" to "10",
                "shardIndex" to "2"
            ),
            deviceId = ":app:deviceNameDebugAndroidTest",
            useGradleManagedDeviceProvider = true,
            shardingConfig = """
                shard_count: 10
                shard_index: 2
            """
        )
    }

    @Test
    fun userSuppliedShardArgsAreNotSupportedWithShardConfig() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            createForManagedDevice(
                testData = testData.copy(instrumentationRunnerArguments = mapOf("numShards" to "2")),
                shardConfig = ShardConfig(totalCount = 10, index = 2))
        }
        assertThat(exception).hasMessageThat().contains(
            "testInstrumentationRunnerArguments.[numShards | shardIndex] is currently incompatible")
    }

    @Test
    fun createRunnerConfigProtoForLocalDeviceWithAdditionalTestOutput() {
        val runnerConfigProto = createForLocalDevice(
            additionalTestOutputDir = mockFile("additionalTestOutputDir")
        )

        val onDeviceDir = "/sdcard/Android/media/com.example.application/additional_test_output"
        val onHostDir = "additionalTestOutputDir${File.separator}"
        assertRunnerConfigProto(
            runnerConfigProto,
            instrumentationArgs = mapOf(
                "additionalTestOutputDir" to onDeviceDir,
            ),
            additionalTestOutputConfig = """
               additional_output_directory_on_device: "${onDeviceDir}"
               additional_output_directory_on_host: "${escapeDoubleQuotesAndBackslashes(onHostDir)}"
            """
        )
    }

    @Test
    fun createRunnerConfigProtoForManagedDeviceWithCustomEmulatorGpuFlag() {
        val runnerConfigProto = createForManagedDevice(
            emulatorGpuFlag = "swiftshader_indirect"
        )

        assertRunnerConfigProto(
            runnerConfigProto,
            deviceId = ":app:deviceNameDebugAndroidTest",
            useGradleManagedDeviceProvider = true,
            emulatorGpuFlag = "swiftshader_indirect"
        )
    }

    @Test
    fun createRunnerConfigProtoForManagedDeviceWithShowEmulatorKernelLogging() {
        val runnerConfigProto = createForManagedDevice(
            showEmulatorKernelLogging = true
        )

        assertRunnerConfigProto(
            runnerConfigProto,
            deviceId = ":app:deviceNameDebugAndroidTest",
            useGradleManagedDeviceProvider = true,
            showEmulatorKernelLogging = true,
        )
    }

    @Test
    fun createRunnerConfigProtoForLocalDeviceWithAdditionalTestOutputNotSupported() {
        `when`(mockDevice.apiLevel).thenReturn(15)
        val runnerConfigProto = createForLocalDevice(
            additionalTestOutputDir = mockFile("additionalTestOutputDir")
        )

        // Setting up on device directory for additional test output is not supported on
        // API level 15 but the plugin can still copy files from TestStorage service.
        val onHostDir = "additionalTestOutputDir${File.separator}"
        assertRunnerConfigProto(
            runnerConfigProto,
            additionalTestOutputConfig = """
               additional_output_directory_on_host: "${escapeDoubleQuotesAndBackslashes(onHostDir)}"
            """.trimIndent(),
        )
    }

    @Test
    fun createRunnerConfigProtoForManagedDeviceWithAdditionalTestOutput() {
        val runnerConfigProto = createForManagedDevice(
            additionalTestOutputDir = mockFile("additionalTestOutputDir")
        )

        val onDeviceDir = "/sdcard/Android/media/com.example.application/additional_test_output"
        val onHostDir = "additionalTestOutputDir${File.separator}"
        assertRunnerConfigProto(
            runnerConfigProto,
            deviceId = ":app:deviceNameDebugAndroidTest",
            useGradleManagedDeviceProvider = true,
            instrumentationArgs = mapOf(
                "additionalTestOutputDir" to onDeviceDir,
            ),
            additionalTestOutputConfig = """
               additional_output_directory_on_device: "${onDeviceDir}"
               additional_output_directory_on_host: "${escapeDoubleQuotesAndBackslashes(onHostDir)}"
            """
        )
    }

    @Test
    fun createServerConfigProto() {
        val factory = UtpConfigFactory()
        val serverConfigProto = factory.createServerConfigProto()

        assertThat(serverConfigProto.toString().trim()).isEqualTo("""
            address: "localhost:20000"
        """.trimIndent())
    }

    @Test
    fun multipleDependencyApk() {
        val mockPath1 = mockPath("mockDependencyApkPath1")
        val mockPath2 = mockPath("mockDependencyApkPath2")
        val extractedSdkApks = listOf(listOf(mockPath1, mockPath2))

        val runnerConfigProto = createForLocalDevice(
                extractedSdkApks = extractedSdkApks)
        assertRunnerConfigProto(
                runnerConfigProto,
                isDependencyApkSplit = true)
    }
}
