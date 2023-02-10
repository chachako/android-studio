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
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
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
 * Unit test for [ManagedDeviceSetupTask].
 */
class ManagedDeviceSetupTaskTest {

    interface TestDevice : Device
    interface TestDeviceSetupInput : DeviceSetupInput
    interface TestDeviceSetupConfigAction
        : DeviceSetupConfigureAction<TestDevice, TestDeviceSetupInput>
    interface TestDeviceSetupTaskAction : DeviceSetupTaskAction<TestDeviceSetupInput>

    @get:Rule val mockitoRule = MockitoJUnit.rule()
    @get:Rule val tempFolderRule = TemporaryFolder()

    @Mock(answer = RETURNS_DEEP_STUBS) lateinit var creationConfig: GlobalTaskCreationConfig
    @Mock lateinit var device: TestDevice

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
            mock<ManagedDeviceSetupTask>(withSettings().defaultAnswer(RETURNS_DEEP_STUBS))
        val mockConfigAction = mock<TestDeviceSetupConfigAction>()
        `when`(mockTask.objectFactory.newInstance(eq(TestDeviceSetupConfigAction::class.java)))
            .thenReturn(mockConfigAction)
        val mockSetupInput = mock<TestDeviceSetupInput>()
        `when`(mockConfigAction.configureTaskInput(eq(device))).thenReturn(mockSetupInput)

        creationAction.configure(mockTask)

        verify(mockTask.deviceInput).setDisallowChanges(eq(mockSetupInput))
    }

    @Test
    fun runTask() {
        val task = createTask()

        task.doTaskAction()
    }

    private fun createTask(): ManagedDeviceSetupTask {
        return mock<ManagedDeviceSetupTask>(
            withSettings().defaultAnswer(RETURNS_DEEP_STUBS)).apply {
            `when`(analyticsService.get()).thenReturn(mock())
            `when`(projectPath).thenReturn(FakeGradleProperty(":app"))
            `when`(path).thenReturn(":app:myDeviceSetup")
            `when`(setupAction.get()).thenReturn(TestDeviceSetupTaskAction::class.java)
            val mockSetupTaskAction = mock<TestDeviceSetupTaskAction>()
            `when`(objectFactory.newInstance(eq(TestDeviceSetupTaskAction::class.java)))
                .thenReturn(mockSetupTaskAction)
            `when`(deviceInput.get()).thenReturn(mock<TestDeviceSetupInput>())

            // We call real method for testing.
            `when`(doTaskAction()).thenCallRealMethod()
        }
    }
}
