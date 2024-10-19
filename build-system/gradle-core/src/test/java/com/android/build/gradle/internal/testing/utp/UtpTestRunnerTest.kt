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
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.testing.AdbHelper
import com.android.build.gradle.internal.testing.StaticTestData
import com.android.builder.model.TestOptions
import com.android.builder.testing.api.DeviceConnector
import com.android.ide.common.process.ProcessExecutor
import com.android.ide.common.workers.ExecutorServiceAdapter
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.ILogger
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.proto.api.config.RunnerConfigProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import com.google.testing.platform.proto.api.service.ServerConfigProto.ServerConfig
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
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import java.io.File
import java.util.logging.Level
import kotlin.io.path.Path

/**
 * Unit tests for [UtpTestRunner].
 */
class UtpTestRunnerTest {
    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)
    @get:Rule var temporaryFolderRule = TemporaryFolder()

    private val mockProcessExecutor: ProcessExecutor = mock()
    private val mockWorkerExecutor: WorkerExecutor = mock()
    private val mockWorkQueue: WorkQueue = mock()
    private val mockExecutorServiceAdapter: ExecutorServiceAdapter = mock()
    private val mockVersionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader = mock()
    private val mockAdbHelper: AdbHelper = mock()
    private val mockTestData: StaticTestData = mock()
    private val mockAppApk: File = mock()
    private val mockPrivacySandboxSdkApk: File = mock()
    private val mockHelperApk: File = mock()
    private val mockDevice: DeviceConnector = mock()
    private val mockLogger: ILogger = mock()
    private val mockUtpConfigFactory: UtpConfigFactory = mock()
    private val mockemulatorControlConfig: EmulatorControlConfig = mock()
    private val mockRetentionConfig: RetentionConfig = mock()
    private val mockTestResultListener: UtpTestResultListener = mock()
    private val mockUtpRunProfileManager: UtpRunProfileManager = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
    private val mockUtpDependencies: UtpDependencies = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
    private val mockUtpTestResultListenerServerMetadata: UtpTestResultListenerServerMetadata = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)

    private lateinit var resultsDirectory: File
    private lateinit var jvmExecutable: File
    private lateinit var capturedRunnerConfigs: List<UtpRunnerConfig>

    @Before
    fun setupMocks() {
        jvmExecutable = temporaryFolderRule.newFile()

        whenever(mockDevice.serialNumber).thenReturn("mockDeviceSerialNumber")
        whenever(mockDevice.apiLevel).thenReturn(28)
        whenever(mockTestData.minSdkVersion).thenReturn(AndroidVersionImpl(28))
        whenever(mockTestData.testedApkFinder).thenReturn { listOf(mockAppApk) }

        val adbHelperProvider: Provider<AdbHelper> = mock()
        whenever(adbHelperProvider.get()).thenReturn(mockAdbHelper)
        whenever(mockVersionedSdkLoader.adbHelper).thenReturn(adbHelperProvider)


    }

    private fun runUtp(result: UtpTestRunResult, expectedToRunTests: Boolean = true): Boolean {
        if (expectedToRunTests) {
            // need to set up additional stubbing here, b/c of strict stubbing mode.
            whenever(mockDevice.name).thenReturn("mockDeviceName")
            whenever(mockUtpConfigFactory.createRunnerConfigProtoForLocalDevice(
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
                anyOrNull<File>(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull<Int>(),
                any(),
                any(),
                anyOrNull<ShardConfig>(),
            )).then {
                RunnerConfigProto.RunnerConfig.getDefaultInstance()
            }
            whenever(mockUtpConfigFactory.createServerConfigProto())
                .thenReturn(ServerConfig.getDefaultInstance())
        }

        val runner = UtpTestRunner(
            null,
            mockProcessExecutor,
            mockWorkerExecutor,
            mockExecutorServiceAdapter,
            jvmExecutable,
            mockUtpDependencies,
            mockVersionedSdkLoader,
            mockemulatorControlConfig,
            mockRetentionConfig,
            useOrchestrator = false,
            forceCompilation = false,
            uninstallIncompatibleApks = false,
            mockTestResultListener,
            Level.WARNING,
            null,
            false,
            false,
            mockUtpRunProfileManager,
            mockUtpConfigFactory,
            { runnerConfigs, _, _, _, _ ->
                capturedRunnerConfigs = runnerConfigs
                listOf(result)
            },
        )

        resultsDirectory = temporaryFolderRule.newFolder("results")
        return runner.runTests(
            "projectName",
            "variantName",
            mockTestData,
            setOf(mockPrivacySandboxSdkApk),
            setOf(mockHelperApk),
            listOf(mockDevice),
            0,
            setOf(),
            resultsDirectory,
            false,
            null,
            temporaryFolderRule.newFolder("coverageDir"),
            mockLogger)
    }

    @Test
    fun runUtpAndPassed() {
        whenever(mockTestData.privacySandboxInstallBundlesFinder).thenReturn { emptyList() }

        val result = runUtp(UtpTestRunResult(testPassed = true,
                                             TestSuiteResult.getDefaultInstance()))

        assertThat(capturedRunnerConfigs).hasSize(1)
        assertThat(capturedRunnerConfigs[0].runnerConfig(
            mockUtpTestResultListenerServerMetadata,
            temporaryFolderRule.newFolder("tmp")))
            .isEqualTo(RunnerConfigProto.RunnerConfig.getDefaultInstance())

        assertThat(result).isTrue()
        assertThat(File(resultsDirectory, TEST_RESULT_PB_FILE_NAME)).exists()
    }

    @Test
    fun runUtpAndFailed() {
        whenever(mockTestData.privacySandboxInstallBundlesFinder).thenReturn { emptyList() }

        val result = runUtp(UtpTestRunResult(testPassed = false, null))

        assertThat(capturedRunnerConfigs).hasSize(1)
        assertThat(capturedRunnerConfigs[0].runnerConfig(
            mockUtpTestResultListenerServerMetadata,
            temporaryFolderRule.newFolder("tmp")))
            .isEqualTo(RunnerConfigProto.RunnerConfig.getDefaultInstance())

        assertThat(result).isFalse()
    }

    @Test
    fun runTestsFiltersManagedDevices() {
        whenever(mockTestData.privacySandboxInstallBundlesFinder).thenReturn { emptyList() }
        // Ensure all devices are determined to be managed devices.
        whenever(mockAdbHelper.isManagedDevice(any(), any())).thenReturn(true)

        val result = runUtp(UtpTestRunResult(testPassed = true,
            TestSuiteResult.getDefaultInstance()), expectedToRunTests = false)

        // Since the only available devices will only be managed devices, we expect no tests to
        // be run.
        assertThat(capturedRunnerConfigs).hasSize(0)
        assertThat(result).isTrue()
    }
}
