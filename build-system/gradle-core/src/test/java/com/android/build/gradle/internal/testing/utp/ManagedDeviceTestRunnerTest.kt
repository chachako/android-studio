/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.build.gradle.internal.AvdComponentsBuildService
import com.android.build.gradle.internal.ManagedVirtualDeviceLockManager
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.build.gradle.internal.testing.StaticTestData
import com.android.builder.model.TestOptions
import com.android.prefs.AndroidLocationsProvider
import com.android.testutils.SystemPropertyOverrides
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.Environment
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.proto.api.config.RunnerConfigProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import com.google.testing.platform.proto.api.service.ServerConfigProto.ServerConfig
import java.io.File
import java.util.logging.Level
import org.gradle.api.file.Directory
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Provider
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.io.path.Path

/**
 * Unit tests for [ManagedDeviceTestRunner].
 */
class ManagedDeviceTestRunnerTest {
    @get:Rule var temporaryFolderRule = TemporaryFolder()

    private val mockWorkerExecutor: WorkerExecutor = mock()
    private val mockWorkQueue: WorkQueue = mock()
    private val mockVersionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader = mock()
    private val mockAvdComponents: AvdComponentsBuildService = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
    private val mockTestData: StaticTestData = mock()
    private val mockAppApk: File = mock()
    private val mockHelperApk: File = mock()
    private val mockLogger: Logger = mock()
    private val mockUtpConfigFactory: UtpConfigFactory = mock()
    private val mockemulatorControlConfig: EmulatorControlConfig = mock()
    private val mockRetentionConfig: RetentionConfig = mock()
    private val mockCoverageOutputDir: File = mock()
    private val mockAdditionalTestOutputDir: File = mock()
    private val mockDslDevice: ManagedVirtualDevice = mock()
    private val mockManagedDeviceShard0: UtpManagedDevice = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
    private val mockManagedDeviceShard1: UtpManagedDevice = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
    private val mockUtpTestResultListenerServerRunner: UtpTestResultListenerServerRunner = mock()
    private val mockUtpTestResultListenerServerMetadata: UtpTestResultListenerServerMetadata = mock()
    private val mockUtpDependencies: UtpDependencies = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
    private val androidLocations: AndroidLocationsProvider = mock()
    private val lockManager: ManagedVirtualDeviceLockManager = mock()
    private val deviceLock: ManagedVirtualDeviceLockManager.DeviceLock = mock()
    private val emulatorProvider: Provider<Directory> = mock()
    private val emulatorDirectory: Directory = mock()
    private val avdProvider: Provider<Directory> = mock()
    private val avdDirectory: Directory = mock()
    private val mockUtpRunProfileManager: UtpRunProfileManager = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var emulatorFolder: File
    private lateinit var avdFolder: File
    private lateinit var emulatorFile: File
    private lateinit var outputDirectory: File
    private lateinit var jvmExecutable: File

    private lateinit var capturedRunnerConfigs: List<UtpRunnerConfig>
    private var utpInvocationCount: Int = 0
    private val extractedSdkApks = listOf(listOf(Path("test1"), Path("test2")))
    private val sdkApkSet = setOf(File("test"))

    @Before
    fun setupMocks() {
        Environment.initialize()

        jvmExecutable = temporaryFolderRule.newFile()

        whenever(mockTestData.minSdkVersion).thenReturn(AndroidVersionImpl(28))
        whenever(mockTestData.testedApkFinder).thenReturn { listOf(mockAppApk) }
        whenever(mockTestData.privacySandboxInstallBundlesFinder).thenReturn { extractedSdkApks }
        whenever(mockUtpConfigFactory.createRunnerConfigProtoForManagedDevice(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull<Int>(),
                any(),
                any(),
                anyOrNull<ShardConfig>(),)).then {
            RunnerConfigProto.RunnerConfig.getDefaultInstance()
        }
        whenever(mockUtpConfigFactory.createServerConfigProto())
                .thenReturn(ServerConfig.getDefaultInstance())

        whenever(mockAvdComponents.lockManager).thenReturn(lockManager)
        whenever(lockManager.lock(any())).thenReturn(deviceLock)

        emulatorFolder = temporaryFolderRule.newFolder("emulator")
        whenever(emulatorDirectory.asFile).thenReturn(emulatorFolder)
        whenever(emulatorProvider.get()).thenReturn(emulatorDirectory)
        whenever(emulatorProvider.isPresent()).thenReturn(true)
        whenever(mockAvdComponents.emulatorDirectory).thenReturn(emulatorProvider)

        avdFolder = temporaryFolderRule.newFolder("avd")
        whenever(avdDirectory.asFile).thenReturn(avdFolder)
        whenever(avdProvider.get()).thenReturn(avdDirectory)
        whenever(mockAvdComponents.avdFolder).thenReturn(avdProvider)

        whenever(mockDslDevice.getName()).thenReturn("testDevice")
        whenever(mockDslDevice.device).thenReturn("Pixel 2")
        whenever(mockDslDevice.apiLevel).thenReturn(28)
        whenever(mockDslDevice.systemImageSource).thenReturn("aosp")
        whenever(mockDslDevice.require64Bit).thenReturn(true)
    }

    private fun <T> runInLinuxEnvironment(function: () -> T): T {
        return try {
            // Need to use a custom set up environment to ensure deterministic behavior.
            SystemPropertyOverrides().use { systemPropertyOverrides ->
                // This will ensure the config believes we are running on an x86_64 Linux machine.
                // This will guarantee the x86 system-image is selected.
                systemPropertyOverrides.setProperty("os.name", "Linux")
                Environment.instance = object : Environment() {
                    override fun getVariable(name: EnvironmentVariable): String? =
                        if (name.key == "HOSTTYPE") "x86_64" else null
                }
                systemPropertyOverrides.setProperty("os.arch", "x86_64")

                function.invoke()
            }
        } finally {
            Environment.instance = Environment.SYSTEM
        }
    }

    private fun runUtp(
        result: Boolean,
        numShards: Int? = null,
        hasEmulatorTimeoutException: List<Boolean> = List(numShards ?: 1) { false },
    ): Boolean {

        return runInLinuxEnvironment {
            val runner = ManagedDeviceTestRunner(
                mockWorkerExecutor,
                mockUtpDependencies,
                jvmExecutable,
                mockVersionedSdkLoader,
                mockemulatorControlConfig,
                mockRetentionConfig,
                useOrchestrator = false,
                forceCompilation = false,
                numShards,
                "auto-no-window",
                showEmulatorKernelLogging = false,
                mockAvdComponents,
                null,
                false,
                Level.WARNING,
                false,
                false,
                mockUtpRunProfileManager,
                mockUtpConfigFactory,
                { runnerConfigs, _, _, resultsDir, _ ->
                    utpInvocationCount++
                    capturedRunnerConfigs = runnerConfigs
                    TestSuiteResult.getDefaultInstance()
                        .writeTo(File(resultsDir, TEST_RESULT_PB_FILE_NAME).outputStream())
                    runnerConfigs.map {
                        UtpTestRunResult(
                            result,
                            createTestSuiteResult(
                                hasEmulatorTimeoutException[it.shardConfig?.index ?: 0]
                            )
                        )
                    }
                },
            )

            outputDirectory = temporaryFolderRule.newFolder("results")
            runner.runTests(
                mockDslDevice,
                "mockDeviceId",
                outputDirectory,
                mockCoverageOutputDir,
                mockAdditionalTestOutputDir,
                "projectPath",
                "variantName",
                mockTestData,
                listOf(),
                setOf(mockHelperApk),
                mockLogger,
                sdkApkSet
            )
        }
    }

    private fun createTestSuiteResult(
        hasEmulatorTimeoutException: Boolean = false
    ): TestSuiteResult {
        return TestSuiteResultProto.TestSuiteResult.newBuilder().apply {
            if (hasEmulatorTimeoutException) {
                platformErrorBuilder.apply {
                    addErrorsBuilder().apply {
                        causeBuilder.apply {
                            summaryBuilder.apply {
                                stackTrace = "EmulatorTimeoutException"
                            }
                        }
                    }
                }
            }
        }.build()
    }

    @Test
    fun runUtpAndPassed() {
        val result = runUtp(result = true)

        assertThat(utpInvocationCount).isEqualTo(1)
        assertThat(capturedRunnerConfigs).hasSize(1)
        assertThat(capturedRunnerConfigs[0].runnerConfig(
            mockUtpTestResultListenerServerMetadata,
            temporaryFolderRule.newFolder("tmp")))
            .isEqualTo(RunnerConfigProto.RunnerConfig.getDefaultInstance())

        assertThat(result).isTrue()
        assertThat(File(outputDirectory, TEST_RESULT_PB_FILE_NAME)).exists()
    }

    @Test
    fun runUtpAndFailed() {
        val result = runUtp(result = false)

        assertThat(utpInvocationCount).isEqualTo(1)
        assertThat(capturedRunnerConfigs).hasSize(1)
        assertThat(capturedRunnerConfigs[0].runnerConfig(
            mockUtpTestResultListenerServerMetadata,
            temporaryFolderRule.newFolder("tmp")))
            .isEqualTo(RunnerConfigProto.RunnerConfig.getDefaultInstance())

        assertThat(result).isFalse()
    }

    @Test
    fun runUtpWithShardsAndPassed() {
        whenever(deviceLock.lockCount).thenReturn(2)
        val result = runUtp(result = true, numShards = 2)

        assertThat(capturedRunnerConfigs).hasSize(2)
        assertThat(capturedRunnerConfigs[0].runnerConfig(
            mockUtpTestResultListenerServerMetadata,
            temporaryFolderRule.newFolder("tmp1")))
            .isEqualTo(RunnerConfigProto.RunnerConfig.getDefaultInstance())
        assertThat(capturedRunnerConfigs[0].shardConfig).isEqualTo(ShardConfig(2, 0))
        assertThat(capturedRunnerConfigs[1].runnerConfig(
            mockUtpTestResultListenerServerMetadata,
            temporaryFolderRule.newFolder("tmp2")))
            .isEqualTo(RunnerConfigProto.RunnerConfig.getDefaultInstance())
        assertThat(capturedRunnerConfigs[1].shardConfig).isEqualTo(ShardConfig(2, 1))

        assertThat(result).isTrue()
        assertThat(File(outputDirectory, TEST_RESULT_PB_FILE_NAME)).exists()
    }

    @Test
    fun rerunUtpWhenEmulatorTimeoutExceptionOccurs() {
        whenever(deviceLock.lockCount).thenReturn(2)
        val result = runUtp(
            result = true,
            numShards = 2,
            hasEmulatorTimeoutException = listOf(true, false))

        assertThat(utpInvocationCount).isEqualTo(2)
        assertThat(result).isTrue()
        assertThat(File(outputDirectory, TEST_RESULT_PB_FILE_NAME)).exists()
    }

    @Test
    fun runUtpBlocksDevicesCorrectly() {
        // use a real device lock manager to ensure blocking behavior.
        val avdFolder = temporaryFolderRule.newFolder()
        whenever(androidLocations.gradleAvdLocation).thenReturn(avdFolder.toPath())
        val deviceLockManager = ManagedVirtualDeviceLockManager(androidLocations, 1, 0L)
        whenever(mockAvdComponents.lockManager).thenReturn(deviceLockManager)

        // contains the list of blocking actions in order.
        val resultActions = mutableListOf<String>()

        var blockTestsDevice1 = AtomicBoolean(true)

        runInLinuxEnvironment {
            val firstRun = thread(start = true) {
                runManagedDeviceTestRunnerForDevice(
                    "Device1"
                ) { runnerConfigs, resultsDir ->
                        resultActions.add("Device1 tests started")
                        while (blockTestsDevice1.get()) {}
                        capturedRunnerConfigs = runnerConfigs
                        TestSuiteResult.getDefaultInstance()
                            .writeTo(File(resultsDir, TEST_RESULT_PB_FILE_NAME).outputStream())

                        resultActions.add("Device1 tests completed")
                        runnerConfigs.map {
                            UtpTestRunResult(
                                true,
                                createTestSuiteResult()
                            )
                        }
                    }
            }

            // wait for tests to "start"
            firstRun.join(50L)

            // tests will not finish until we unblock them.
            assertThat(resultActions).containsExactly("Device1 tests started")

            val secondRun = thread(start = true) {
                runManagedDeviceTestRunnerForDevice(
                    "Device2"
                ) { runnerConfigs, resultsDir ->
                        resultActions.add("Device2 tests started")
                        capturedRunnerConfigs = runnerConfigs
                        TestSuiteResult.getDefaultInstance()
                            .writeTo(File(resultsDir, TEST_RESULT_PB_FILE_NAME).outputStream())
                        resultActions.add("Device2 tests completed")
                        runnerConfigs.map {
                            UtpTestRunResult(
                                true,
                                createTestSuiteResult()
                            )
                        }
                    }
            }

            // give second run time to start if it could.
            // it shouldn't b/c it should be waiting for device1 to complete.
            secondRun.join(100L)

            // The second run will be blocked by the device lock and will not have started.
            assertThat(resultActions).containsExactly("Device1 tests started")

            // unblock device1, should allow device2 to start.
            blockTestsDevice1.set(false)

            firstRun.join()
            secondRun.join()

            assertThat(resultActions).containsExactly(
                "Device1 tests started",
                "Device1 tests completed",
                "Device2 tests started",
                "Device2 tests completed"
            ).inOrder()
        }
    }

    private fun runManagedDeviceTestRunnerForDevice(
        deviceName: String,
        runTestsFunc: (
            List<UtpRunnerConfig>, File
        ) -> List<UtpTestRunResult>
    ) {
        ManagedDeviceTestRunner(
            mockWorkerExecutor,
            mockUtpDependencies,
            jvmExecutable,
            mockVersionedSdkLoader,
            mockemulatorControlConfig,
            mockRetentionConfig,
            useOrchestrator = false,
            forceCompilation = false,
            numShards = null,
            emulatorGpuFlag = "auto-no-window",
            showEmulatorKernelLogging = false,
            mockAvdComponents,
            installApkTimeout = null,
            enableEmulatorDisplay = false,
            utpLoggingLevel = Level.WARNING,
            targetIsSplitApk = false,
            uninstallApksAfterTest = false,
            mockUtpRunProfileManager,
            mockUtpConfigFactory
        ) { runnerConfigs, _, _, resultsDir, _ ->
            runTestsFunc(runnerConfigs, resultsDir)
        }.runTests(
            mockDslDevice,
            deviceName,
            temporaryFolderRule.newFolder(),
            mockCoverageOutputDir,
            mockAdditionalTestOutputDir,
            "projectPath",
            "variantName",
            mockTestData,
            listOf(),
            setOf(mockHelperApk),
            mockLogger,
            sdkApkSet
        )
    }
}
