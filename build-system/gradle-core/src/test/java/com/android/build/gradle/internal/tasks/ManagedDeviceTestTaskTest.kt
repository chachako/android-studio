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

import com.android.build.api.dsl.Device
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunConfigureAction
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunInput
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunParameters
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunTaskAction
import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.test.AbstractTestDataImpl
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.model.TestOptions
import com.google.common.truth.Truth.assertThat
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers.RETURNS_DEEP_STUBS
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import javax.inject.Inject

/**
 * Unit tests for [ManagedDeviceTestTask].
 */
class ManagedDeviceTestTaskTest {

    interface TestDevice : Device
    interface TestRunInput : DeviceTestRunInput
    interface TestRunConfigAction : DeviceTestRunConfigureAction<TestDevice, TestRunInput>
    interface TestRunTaskAction : DeviceTestRunTaskAction<TestRunInput>

    @get:Rule val tempFolderRule = TemporaryFolder()

    private val creationConfig: InstrumentedTestCreationConfig = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val device: TestDevice = mock()
    private val testData: AbstractTestDataImpl = mock()

    private lateinit var project: Project

    abstract class TaskForTest @Inject constructor(testWorkerExecutor: WorkerExecutor) :
        ManagedDeviceTestTask() {

            override val workerExecutor = testWorkerExecutor

            // Allows for setting the dependencies to the Test Task without expanding
            // internal public api.
            fun setDependenciesForTest(collection: ArtifactCollection) {
                dependencies = collection
            }
        }

    // Needs to be a true abstract class and not a mock, to allow for the ObjectFactory to
    // decorate the class for test.
    abstract class FakeTestAction @Inject constructor(): DeviceTestRunTaskAction<DeviceTestRunInput> {

        override fun runTests(params: DeviceTestRunParameters<DeviceTestRunInput>): Boolean {
            return shouldSucceed
        }

        companion object {
            internal var shouldSucceed: Boolean = false
        }
    }

    @Before
    fun setupMocks() {
        project = ProjectBuilder.builder().withProjectDir(tempFolderRule.root).build()

        whenever(device.name).thenReturn("testDeviceName")
        whenever(creationConfig.name).thenReturn("variantName")
        whenever(testData.hasTests(any(), any(), any())).thenReturn(realPropertyFor(true))
        whenever(testData.flavorName).thenReturn(realPropertyFor(""))
        whenever(testData.getAsStaticData()).thenReturn(mock())

        FakeTestAction.shouldSucceed = false
    }

    @Test
    fun configureTask() {
        val creationAction = ManagedDeviceTestTask.CreationAction(
            creationConfig,
            device,
            TestRunConfigAction::class.java,
            TestRunTaskAction::class.java,
            testData,
            tempFolderRule.newFolder(),
            tempFolderRule.newFolder(),
            tempFolderRule.newFolder(),
            tempFolderRule.newFolder(),
            null,
        )

        val mockTask = mock<ManagedDeviceTestTask>(defaultAnswer = RETURNS_DEEP_STUBS)
        val mockConfigAction = mock<TestRunConfigAction>()
        whenever(mockTask.objectFactory.newInstance(eq(TestRunConfigAction::class.java)))
            .thenReturn(mockConfigAction)
        val mockTestRunInput = mock<TestRunInput>()
        whenever(mockConfigAction.configureTaskInput(eq(device))).thenReturn(mockTestRunInput)

        creationAction.configure(mockTask)

        verify(mockTask.deviceInput).setDisallowChanges(same(mockTestRunInput))
    }

    @Test
    fun runTaskWithPassingTests() {
        val task = createTask(testRunTaskActionResult = true)

        task.doTaskAction()
    }

    @Test
    fun runTaskWithFailedTests() {
        val task = createTask(testRunTaskActionResult = false)

        val exception = assertThrows(GradleException::class.java) {
            task.doTaskAction()
        }

        assertThat(exception).hasMessageThat().contains(
            "There were failing tests for Device: myDevice.")
    }

    private inline fun <reified ValueClass> realPropertyFor(
        providedValue: ValueClass): Property<ValueClass> {

        val property = project.objects.property(ValueClass::class.java)
        property.set(providedValue)
        return property
    }

    private fun createTask(testRunTaskActionResult: Boolean): ManagedDeviceTestTask {
        FakeTestAction.shouldSucceed = testRunTaskActionResult

        return project.tasks.register(
            "testTask",
            TaskForTest::class.java,
            FakeGradleWorkExecutor(project.objects, tempFolderRule.newFolder())
        ).get().apply {
            resultsDir.set(tempFolderRule.newFolder())
            getCoverageDirectory().set(tempFolderRule.newFolder())
            getAdditionalTestOutputEnabled().set(true)
            getAdditionalTestOutputDir().set(tempFolderRule.newFolder())
            testAction.set(FakeTestAction::class.java)
            analyticsService.set(mock<AnalyticsService>())
            testData.set(this@ManagedDeviceTestTaskTest.testData)
            setDependenciesForTest(
                mock<ArtifactCollection>(defaultAnswer = RETURNS_DEEP_STUBS))
            executionEnum.set(TestOptions.Execution.HOST)
            deviceInput.set(mock<DeviceTestRunInput>())
            deviceDslName.set("myDevice")
            projectPath.set("project_path")
            getReportsDir().set(tempFolderRule.newFolder())
        }
    }
}
