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
import com.android.build.api.instrumentation.manageddevice.DeviceSetupConfigureAction
import com.android.build.api.instrumentation.manageddevice.DeviceSetupInput
import com.android.build.api.instrumentation.manageddevice.DeviceSetupTaskAction
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers.RETURNS_DEEP_STUBS
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.junit.MockitoJUnit

/**
 * Unit test for [ManagedDeviceSetupTask].
 */
class ManagedDeviceSetupTaskTest {

    interface TestDevice : Device
    interface TestDeviceSetupInput : DeviceSetupInput
    interface TestDeviceSetupConfigAction
        : DeviceSetupConfigureAction<TestDevice, TestDeviceSetupInput>
    interface TestDeviceSetupTaskAction : DeviceSetupTaskAction<TestDeviceSetupInput>

    @get:Rule val tempFolderRule = TemporaryFolder()

    private val creationConfig: GlobalTaskCreationConfig = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val device: TestDevice = mock()

    @Test
    fun configureTask() {
        val creationAction = ManagedDeviceSetupTask.CreationAction(
            FakeGradleProvider(FakeGradleDirectory(tempFolderRule.newFolder())),
            TestDeviceSetupConfigAction::class.java,
            TestDeviceSetupTaskAction::class.java,
            device,
            creationConfig,
        )
        val mockTask =
            mock<ManagedDeviceSetupTask>(defaultAnswer = RETURNS_DEEP_STUBS)
        val mockConfigAction = mock<TestDeviceSetupConfigAction>()
        whenever(mockTask.objectFactory.newInstance(eq(TestDeviceSetupConfigAction::class.java)))
            .thenReturn(mockConfigAction)
        val mockSetupInput = mock<TestDeviceSetupInput>()
        whenever(mockConfigAction.configureTaskInput(eq(device))).thenReturn(mockSetupInput)

        creationAction.configure(mockTask)

        verify(mockTask.deviceInput).setDisallowChanges(eq(mockSetupInput))
    }

    @Test
    fun runTask() {
        val task = createTask()

        task.doTaskAction()
    }

    private fun createTask(): ManagedDeviceSetupTask {
        return mock<ManagedDeviceSetupTask>(defaultAnswer = RETURNS_DEEP_STUBS).apply {
            whenever(analyticsService.get()).thenReturn(mock())
            whenever(projectPath).thenReturn(FakeGradleProperty(":app"))
            whenever(path).thenReturn(":app:myDeviceSetup")
            whenever(setupAction.get()).thenReturn(TestDeviceSetupTaskAction::class.java)
            val mockSetupTaskAction = mock<TestDeviceSetupTaskAction>()
            whenever(objectFactory.newInstance(eq(TestDeviceSetupTaskAction::class.java)))
                .thenReturn(mockSetupTaskAction)
            whenever(deviceInput.get()).thenReturn(mock<TestDeviceSetupInput>())

            // We call real method for testing.
            whenever(doTaskAction()).thenCallRealMethod()
        }
    }
}
