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
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunTaskAction
import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeListProperty
import com.android.build.gradle.internal.test.AbstractTestDataImpl
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.model.TestOptions
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.same
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFalse
import org.gradle.api.GradleException
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers.RETURNS_DEEP_STUBS
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.withSettings
import org.mockito.junit.MockitoJUnit

/**
 * Unit tests for [ManagedDeviceTestTask].
 */
class ManagedDeviceTestTaskTest {

    interface TestDevice : Device
    interface TestRunInput : DeviceTestRunInput
    interface TestRunConfigAction : DeviceTestRunConfigureAction<TestDevice, TestRunInput>
    interface TestRunTaskAction : DeviceTestRunTaskAction<TestRunInput>

    @get:Rule val mockitoRule = MockitoJUnit.rule()
    @get:Rule val tempFolderRule = TemporaryFolder()

    @Mock(answer = RETURNS_DEEP_STUBS) lateinit var creationConfig: InstrumentedTestCreationConfig
    @Mock lateinit var device: TestDevice
    @Mock lateinit var testData: AbstractTestDataImpl

    @Before
    fun setupMocks() {
        `when`(device.name).thenReturn("testDeviceName")
        `when`(creationConfig.name).thenReturn("variantName")
        `when`(testData.hasTests(any(), any(), any())).thenReturn(FakeGradleProperty(true))
        `when`(testData.flavorName).thenReturn(FakeGradleProperty(""))
        `when`(testData.getAsStaticData()).thenReturn(mock())
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

        val mockTask = mock<ManagedDeviceTestTask>(withSettings().defaultAnswer(RETURNS_DEEP_STUBS))
        val mockConfigAction = mock<TestRunConfigAction>()
        `when`(mockTask.objectFactory.newInstance(eq(TestRunConfigAction::class.java)))
            .thenReturn(mockConfigAction)
        val mockTestRunInput = mock<TestRunInput>()
        `when`(mockConfigAction.configureTaskInput(eq(device))).thenReturn(mockTestRunInput)

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

    private fun createTask(testRunTaskActionResult: Boolean): ManagedDeviceTestTask {
        return mock<ManagedDeviceTestTask>(
            withSettings().defaultAnswer(RETURNS_DEEP_STUBS)).apply {
            `when`(analyticsService.get()).thenReturn(mock())
            `when`(buddyApks.files).thenReturn(setOf())
            `when`(resultsDir.get().asFile).thenReturn(tempFolderRule.newFolder())
            `when`(getCoverageDirectory().get().asFile).thenReturn(tempFolderRule.newFolder())
            `when`(getAdditionalTestOutputEnabled().get()).thenReturn(true)
            `when`(getAdditionalTestOutputDir().get().asFile).thenReturn(tempFolderRule.newFolder())
            `when`(getReportsDir().get().asFile).thenReturn(tempFolderRule.newFolder())
            `when`(testData.get()).thenReturn(this@ManagedDeviceTestTaskTest.testData)
            `when`(executionEnum.get()).thenReturn(TestOptions.Execution.HOST)
            `when`(projectPath).thenReturn(FakeGradleProperty(":app"))
            `when`(path).thenReturn(":app:myDeviceDebugAndroidTest")
            `when`(deviceDslName).thenReturn(FakeGradleProperty("myDevice"))
            `when`(installOptions).thenReturn(FakeListProperty())
            `when`(testAction).thenReturn(FakeGradleProperty(TestRunTaskAction::class.java))
            val mockTestRunTaskAction = mock<TestRunTaskAction>()
            `when`(objectFactory.newInstance(eq(TestRunTaskAction::class.java)))
                .thenReturn(mockTestRunTaskAction)
            `when`(mockTestRunTaskAction.runTests(any())).thenReturn(testRunTaskActionResult)

            // We call real method for testing.
            `when`(doTaskAction()).thenCallRealMethod()
        }
    }
}
