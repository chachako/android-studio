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

package com.android.build.gradle.internal.tasks

import com.android.build.api.variant.impl.TestVariantImpl
import com.android.build.gradle.internal.AvdComponentsBuildService
import com.android.build.gradle.internal.ManagedVirtualDeviceLockManager
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.SdkComponentsBuildService.VersionedSdkLoader
import com.android.build.gradle.internal.component.DeviceTestCreationConfig
import com.android.build.gradle.internal.dsl.EmulatorControl
import com.android.build.gradle.internal.dsl.EmulatorSnapshots
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfigImpl
import com.android.build.gradle.internal.test.AbstractTestDataImpl
import com.android.build.gradle.internal.testing.utp.EmulatorControlConfig
import com.android.build.gradle.internal.testing.utp.ManagedDeviceTestRunner
import com.android.build.gradle.internal.testing.utp.RetentionConfig
import com.android.build.gradle.internal.testing.utp.UtpDependencies
import com.android.build.gradle.internal.testing.utp.UtpRunProfileManager
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.TestOptions
import com.android.repository.Revision
import com.google.common.truth.Truth.assertThat
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.logging.Logger
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildServiceRegistration
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers.CALLS_REAL_METHODS
import org.mockito.Answers.RETURNS_DEEP_STUBS
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.io.File
import java.util.logging.Level

class ManagedDeviceInstrumentationTestTaskTest {
    private lateinit var mockVersionedSdkLoader: VersionedSdkLoader

    private lateinit var utpJvm: File
    private lateinit var emulatorFile: File
    private lateinit var avdFolder: File
    private lateinit var resultsFolder: File
    private lateinit var codeCoverage: File
    private lateinit var reportsFolder: File

    private val emulatorDirectory: Directory = mock()
    private val avdDirectory: Directory = mock()
    private val resultsDirectory: Directory = mock()
    private val coverageDirectory: Directory = mock()
    private val reportsDirectory: Directory = mock()
    private val utpJvmFile: RegularFile = mock()

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    private val avdService: AvdComponentsBuildService = mock()

    private val sdkService: SdkComponentsBuildService = mock()

    private val creationConfig: TestVariantImpl = mock(defaultAnswer = RETURNS_DEEP_STUBS, extraInterfaces = arrayOf(DeviceTestCreationConfig::class))

    private val globalConfig: GlobalTaskCreationConfigImpl = mock(defaultAnswer = RETURNS_DEEP_STUBS)

    private val testData: AbstractTestDataImpl = mock(defaultAnswer = RETURNS_DEEP_STUBS)

    private val runnerFactory: ManagedDeviceInstrumentationTestTask.TestRunnerFactory = mock()

    private val intallOptions: ListProperty<String> = mock()

    private val dependencies: ArtifactCollection = mock()

    private val utpRunProfileManager: UtpRunProfileManager = mock()

    private lateinit var project: Project
    private lateinit var workerExecutor: WorkerExecutor

    @Before
    fun setup() {
        whenever(creationConfig.computeTaskNameInternal(any(), any())).then {
            val prefix = it.getArgument<String>(0)
            val suffix = it.getArgument<String>(0)
            "${prefix}AndroidDebugTest$suffix"
        }
        whenever(creationConfig.name).thenReturn("AndroidDebugTest")

        // Setup Build Services for configuration.
        val mockGeneralRegistration = mock<BuildServiceRegistration<*, *>>(defaultAnswer = RETURNS_DEEP_STUBS)
        whenever(creationConfig.services.buildServiceRegistry.registrations.getByName(any()))
            .thenReturn(mockGeneralRegistration)

        mockVersionedSdkLoader = mock<VersionedSdkLoader>()
        whenever(mockVersionedSdkLoader.offlineMode).thenReturn(false)

        whenever(sdkService.sdkLoader(any(), any())).thenReturn(mockVersionedSdkLoader)

        emulatorFile = temporaryFolderRule.newFolder("emulator")
        whenever(emulatorDirectory.asFile).thenReturn(emulatorFile)
        whenever(avdService.emulatorDirectory).thenReturn(FakeGradleProvider(emulatorDirectory))

        utpJvm = temporaryFolderRule.newFile("java")

        avdFolder = temporaryFolderRule.newFolder("gradle/avd")
        whenever(avdDirectory.asFile).thenReturn(avdFolder)
        whenever(avdService.avdFolder).thenReturn(FakeGradleProvider(avdDirectory))

        val lockManager = mock<ManagedVirtualDeviceLockManager>()
        val lock = mock<ManagedVirtualDeviceLockManager.DeviceLock>()
        whenever(lock.lockCount).thenReturn(1)
        whenever(lockManager.lock(any())).thenReturn(lock)
        whenever(avdService.lockManager).thenReturn(lockManager)

        reportsFolder = temporaryFolderRule.newFolder("reports")
        whenever(reportsDirectory.asFile).thenReturn(reportsFolder)

        resultsFolder = temporaryFolderRule.newFolder("results")
        whenever(resultsDirectory.asFile).thenReturn(resultsFolder)

        codeCoverage = temporaryFolderRule.newFolder("coverage")
        whenever(coverageDirectory.asFile).thenReturn(codeCoverage)

        project = ProjectBuilder.builder().withProjectDir(temporaryFolderRule.newFolder()).build()
        workerExecutor = FakeGradleWorkExecutor(project.objects, temporaryFolderRule.newFolder())
    }

    private fun <T> mockEmptyProperty(): Property<T> {
        @Suppress("UNCHECKED_CAST")
        return mock<Property<T>>()
    }

    private fun mockDirectoryProperty(directory: Directory): DirectoryProperty {
        val property = mock<DirectoryProperty>()
        whenever(property.get()).thenReturn(directory)
        return property
    }

    private fun mockFileProperty(file: RegularFile): RegularFileProperty =
        mock<RegularFileProperty>().apply {
            whenever(get()).thenReturn(file)
        }

    private fun basicTaskSetup(): ManagedDeviceInstrumentationTestTask {

        val task = mock<ManagedDeviceInstrumentationTestTask>(defaultAnswer = CALLS_REAL_METHODS)

        doReturn(FakeGradleProperty(mock<AnalyticsService>()))
            .whenever(task).analyticsService
        doReturn(FakeGradleProperty("project_path")).whenever(task).projectPath

        doReturn("path").whenever(task).path
        doReturn(mock<TaskOutputsInternal>(defaultAnswer = RETURNS_DEEP_STUBS))
            .whenever(task).outputs
        doReturn(mock<Logger>()).whenever(task).logger

        doReturn(runnerFactory).whenever(task).testRunnerFactory
        doReturn(FakeGradleProperty(avdService)).whenever(runnerFactory).avdComponents
        doReturn(FakeGradleProperty(testData)).whenever(task).testData
        doReturn(mock<ListProperty<*>>()).whenever(task).installOptions
        val mockManagedDevice = mock<ManagedVirtualDevice>()
        doReturn("testDevice1").whenever(mockManagedDevice).getName()
        doReturn(29).whenever(mockManagedDevice).apiLevel
        doReturn("aosp").whenever(mockManagedDevice).systemImageSource
        doReturn(false).whenever(mockManagedDevice).require64Bit
        doReturn(FakeGradleProperty(mockManagedDevice)).whenever(task).device
        doReturn(FakeGradleProperty(false)).whenever(task).enableEmulatorDisplay
        doReturn(FakeGradleProperty(false)).whenever(task).getAdditionalTestOutputEnabled()
        doReturn(dependencies).whenever(task).dependencies

        doReturn(FakeGradleProperty(true))
            .whenever(testData).hasTests(any(), any(), any())
        doReturn(FakeGradleProperty("flavor_name"))
            .whenever(testData).flavorName

        val buddyApks = mock<ConfigurableFileCollection>()
        whenever(buddyApks.files).thenReturn(setOf())
        whenever(task.buddyApks).thenReturn(buddyApks)

        whenever(task.installOptions).thenReturn(intallOptions)
        whenever(intallOptions.getOrElse(any())).thenReturn(listOf())

        doReturn(mockDirectoryProperty(resultsDirectory)).whenever(task).resultsDir
        doReturn(mockDirectoryProperty(coverageDirectory)).whenever(task).getCoverageDirectory()
        doReturn(mockDirectoryProperty(reportsDirectory)).whenever(task).getReportsDir()

        doReturn(workerExecutor).whenever(task).workerExecutor

        return task
    }

    @Test
    fun testRunnerFactory_testCreateTestRunner() {
        val factory = mock<ManagedDeviceInstrumentationTestTask.TestRunnerFactory>(
            defaultAnswer = CALLS_REAL_METHODS)

        whenever(factory.executionEnum)
            .thenReturn(FakeGradleProperty(TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR))
        whenever(factory.forceCompilation)
            .thenReturn(FakeGradleProperty(false))
        whenever(factory.retentionConfig)
            .thenReturn(FakeGradleProperty(mock<RetentionConfig>()))
        whenever(factory.emulatorControlConfig)
            .thenReturn(FakeGradleProperty(mock<EmulatorControlConfig>()))
        whenever(factory.compileSdkVersion).thenReturn(FakeGradleProperty("sdkVersion"))
        whenever(factory.buildToolsRevision)
            .thenReturn(FakeGradleProperty(mock<Revision>()))
        whenever(factory.testShardsSize).thenReturn(FakeGradleProperty(null))
        whenever(factory.sdkBuildService).thenReturn(FakeGradleProperty(sdkService))
        whenever(factory.avdComponents).thenReturn(FakeGradleProperty(avdService))
        whenever(factory.utpDependencies).thenReturn(mock<UtpDependencies>())
        whenever(factory.utpLoggingLevel)
            .thenReturn(FakeGradleProperty(Level.OFF))
        whenever(factory.emulatorGpuFlag).thenReturn(FakeGradleProperty("auto-no-window"))
        whenever(factory.showEmulatorKernelLoggingFlag).thenReturn(FakeGradleProperty(false))
        whenever(factory.installApkTimeout).thenReturn(FakeGradleProperty(0))
        whenever(factory.enableEmulatorDisplay).thenReturn(FakeGradleProperty(false))
        whenever(factory.getTargetIsSplitApk).thenReturn(FakeGradleProperty(false))
        whenever(factory.getKeepInstalledApks).thenReturn(FakeGradleProperty(false))
        doReturn(mockFileProperty(utpJvmFile)).whenever(factory).jvmExecutable
        doReturn(utpJvm).whenever(utpJvmFile).asFile

        val testRunner = factory.createTestRunner(workerExecutor, null, utpRunProfileManager)
        assertThat(testRunner).isInstanceOf(ManagedDeviceTestRunner::class.java)
    }

    @Test
    fun creationConfig_testTaskConfiguration() {
        // Parameters for config class.
        val resultsDir = temporaryFolderRule.newFolder("resultsDir")
        val reportsDir = temporaryFolderRule.newFolder("reportsDir")
        val additionalTestOutputDir = temporaryFolderRule.newFolder("additionalTestOutputDir")
        val coverageOutputDir = temporaryFolderRule.newFolder("coverageOutputDir")
        val managedDevice = ManagedVirtualDevice("someNameHere").also {
            it.device = "Pixel 2"
            it.apiLevel = 27
            it.systemImageSource = "aosp"
        }
        val emulatorControl = mock<EmulatorControl>()
        // Needed for cast from api class to internal class
        val snapshots = mock<EmulatorSnapshots>()
        whenever(creationConfig.global.androidTestOptions.emulatorSnapshots).thenReturn(snapshots)
        whenever(creationConfig.global.androidTestOptions.emulatorControl).thenReturn(emulatorControl)
        // Needed to ensure that UTP is active
        whenever(
            creationConfig.services
                .projectOptions[BooleanOption.ANDROID_TEST_USES_UNIFIED_TEST_PLATFORM])
            .thenReturn(true)
        // Needed to ensure the ExecutionEnum
        whenever(creationConfig.global.testOptionExecutionEnum)
            .thenReturn(TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR)
        whenever(creationConfig
                .services
                .projectOptions
                .get(BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT)).thenReturn(true)

        whenever(testData.privacySandboxSdkApks)
                .thenReturn(project.objects.fileCollection())
        val config = ManagedDeviceInstrumentationTestTask.CreationAction(
            creationConfig,
            managedDevice,
            testData,
            resultsDir,
            reportsDir,
            additionalTestOutputDir,
            coverageOutputDir,
            ""
        )

        val task = mock<ManagedDeviceInstrumentationTestTask>(defaultAnswer = RETURNS_DEEP_STUBS)

        // We need to create mock properties to verify/capture values in the task as
        // RETURNS_DEEP_STUBS does not work as expected with verify. Also, we can't use
        // FakeGradleProperties because they do not support disallowChanges().

        val executionEnum = mockEmptyProperty<TestOptions.Execution>()
        whenever(task.testRunnerFactory.executionEnum).thenReturn(executionEnum)
        val sdkBuildService = mockEmptyProperty<SdkComponentsBuildService>()
        whenever(task.testRunnerFactory.sdkBuildService).thenReturn(sdkBuildService)
        val avdComponents = mockEmptyProperty<AvdComponentsBuildService>()
        whenever(task.testRunnerFactory.avdComponents).thenReturn(avdComponents)

        val device = mockEmptyProperty<ManagedVirtualDevice>()
        whenever(task.device).thenReturn(device)

        config.configure(task)

        verify(executionEnum).set(TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR)
        verify(executionEnum).disallowChanges()
        verifyNoMoreInteractions(executionEnum)

        verify(sdkBuildService).set(any<Provider<SdkComponentsBuildService>>())
        verify(sdkBuildService).disallowChanges()
        verifyNoMoreInteractions(sdkBuildService)

        verify(device).set(managedDevice)
        verify(device).disallowChanges()
        verifyNoMoreInteractions(device)

        verify(avdComponents).set(any<Provider<AvdComponentsBuildService>>())
        verify(avdComponents).disallowChanges()
        verifyNoMoreInteractions(avdComponents)
    }

    @Test
    fun taskAction_basicTaskPath() {
        val task = basicTaskSetup()

        val testRunner = mock<ManagedDeviceTestRunner>()
        doReturn(true).whenever(testRunner).runTests(
            managedDevice = any(),
            runId = any(),
            outputDirectory = any(),
            coverageOutputDirectory = any(),
            additionalTestOutputDir = eq(null),
            projectPath = any(),
            variantName = any(),
            testData = any(),
            additionalInstallOptions = any(),
            helperApks = any(),
            logger = any(),
            dependencyApks = any()
        )
        println("TestRunner: $testRunner")

        doReturn(FakeGradleProperty<Int>()).whenever(runnerFactory).testShardsSize
        doReturn(testRunner).whenever(runnerFactory).createTestRunner(any(), eq(null), any())
        whenever(runnerFactory.executionEnum)
            .thenReturn(FakeGradleProperty(TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR))

        task.doTaskAction()

        verify(testRunner).runTests(
            managedDevice = argThat {
                getName() == "testDevice1"
                        && this is ManagedVirtualDevice
                        && apiLevel == 29
                        && systemImageSource == "aosp"
                        && require64Bit == false
            },
            runId = any(),
            outputDirectory = eq(resultsFolder),
            coverageOutputDirectory = eq(codeCoverage),
            additionalTestOutputDir = eq(null),
            projectPath = eq("project_path"),
            variantName = eq("flavor_name"),
            testData = any(),
            additionalInstallOptions = eq(listOf()),
            helperApks = any(),
            logger = any(),
            dependencyApks = any()
        )
        verifyNoMoreInteractions(testRunner)
    }

    @Test
    fun taskAction_testFailuresPath() {
        val task = basicTaskSetup()

        val testRunner = mock<ManagedDeviceTestRunner>()
        doReturn(false).whenever(testRunner).runTests(
            managedDevice = any(),
            runId = any(),
            outputDirectory = any(),
            coverageOutputDirectory = any(),
            additionalTestOutputDir = eq(null),
            projectPath = any(),
            variantName = any(),
            testData = any(),
            additionalInstallOptions = any(),
            helperApks = any(),
            logger = any(),
            dependencyApks = any()
        )

        doReturn(FakeGradleProperty<Int>()).whenever(runnerFactory).testShardsSize

        doReturn(testRunner).whenever(runnerFactory).createTestRunner(any(), eq(null), any())
        whenever(runnerFactory.executionEnum)
            .thenReturn(FakeGradleProperty(TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR))

        task.setIgnoreFailures(false)

        try {
            task.doTaskAction()

            error("Should not reach.")
        } catch (e: GradleException) {
            assertThat(e.message).startsWith("There were failing tests")
        }

        verify(testRunner).runTests(
            managedDevice = argThat {
                getName() == "testDevice1"
                        && this is ManagedVirtualDevice
                        && apiLevel == 29
                        && systemImageSource == "aosp"
                        && require64Bit == false
            },
            runId = any(),
            outputDirectory = eq(resultsFolder),
            coverageOutputDirectory = eq(codeCoverage),
            additionalTestOutputDir = eq(null),
            projectPath = eq("project_path"),
            variantName = eq("flavor_name"),
            testData = any(),
            additionalInstallOptions = eq(listOf()),
            helperApks = any(),
            logger = any(),
            dependencyApks = any()
        )
        verifyNoMoreInteractions(testRunner)
    }

    @Test
    fun taskAction_noTestsPath() {
        val task = basicTaskSetup()

        val testRunner = mock<ManagedDeviceTestRunner>()
        doReturn(false).whenever(testRunner).runTests(
            managedDevice = any(),
            runId = any(),
            outputDirectory = any(),
            coverageOutputDirectory = any(),
            additionalTestOutputDir = eq(null),
            projectPath = any(),
            variantName = any(),
            testData = any(),
            additionalInstallOptions = any(),
            helperApks = any(),
            logger = any(),
            dependencyApks = any()
        )

        doReturn(FakeGradleProperty<Int>()).whenever(runnerFactory).testShardsSize

        doReturn(testRunner).whenever(runnerFactory).createTestRunner(any(), eq(null), any())
        whenever(runnerFactory.executionEnum)
            .thenReturn(FakeGradleProperty(TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR))

        // When the data has no Tests, the testRunner should not be run.
        doReturn(FakeGradleProperty(false))
            .whenever(testData).hasTests(any(), any(), any())

        task.doTaskAction()

        verifyNoInteractions(testRunner)
    }
}
