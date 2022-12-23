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

package com.android.build.gradle.internal.testing

import com.android.build.api.dsl.Device
import com.android.build.api.dsl.ManagedDevices
import com.android.build.api.dsl.TestOptions
import com.android.build.api.instrumentation.manageddevice.DeviceSetupConfigureAction
import com.android.build.api.instrumentation.manageddevice.DeviceSetupInput
import com.android.build.api.instrumentation.manageddevice.DeviceSetupTaskAction
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunConfigureAction
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunInput
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunTaskAction
import com.google.common.truth.Truth.assertThat
import java.lang.reflect.Proxy
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.eq
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

private interface TestDeviceSetupInput: DeviceSetupInput
private interface TestDeviceSetupTaskAction: DeviceSetupTaskAction<TestDeviceSetupInput>

private interface TestDeviceTestRunInput: DeviceTestRunInput
private interface TestDeviceTestRunTaskAction: DeviceTestRunTaskAction<TestDeviceTestRunInput>

private interface SharedDeviceApi : Device
private interface SharedDeviceTestRunConfigAction:
    DeviceTestRunConfigureAction<SharedDeviceApi, TestDeviceTestRunInput>

private interface TestDeviceApi : Device
private interface TestDeviceSetupConfigAction:
    DeviceSetupConfigureAction<TestDeviceApi, TestDeviceSetupInput>
private interface TestDeviceTestRunConfigAction:
    DeviceTestRunConfigureAction<TestDeviceApi, TestDeviceTestRunInput>
private interface TestDeviceImpl: TestDeviceApi, SharedDeviceApi

private interface TestDeviceApi2: Device
private interface TestDeviceImpl2: TestDeviceApi2, SharedDeviceApi

private val testDeviceImplProxy: TestDeviceImpl = Proxy.newProxyInstance(
    ManagedDeviceRegistryTest::class.java.classLoader,
    arrayOf<Class<*>>(TestDeviceImpl::class.java)) { _, _, _ ->
        null
    } as TestDeviceImpl

@RunWith(JUnit4::class)
class ManagedDeviceRegistryTest {
    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    private lateinit var registry: ManagedDeviceRegistry

    @Mock
    private lateinit var testOptions: TestOptions

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var managedDevicesBlock: ManagedDevices

    @Before
    fun setup() {
        `when`(testOptions.managedDevices).thenReturn(managedDevicesBlock)
        registry = ManagedDeviceRegistry(testOptions)
    }

    @Test
    fun registrationNoSetupActions() {
        registry.registerDeviceType(TestDeviceApi::class.java) {
            dslImplementationClass = TestDeviceImpl::class.java
            setTestRunActions(
                TestDeviceTestRunConfigAction::class.java,
                TestDeviceTestRunTaskAction::class.java)
        }

        verify(managedDevicesBlock.devices).registerBinding(
            eq(TestDeviceApi::class.java),
            eq(TestDeviceImpl::class.java)
        )

        val registration = registry.get(TestDeviceImpl::class.java)

        requireNotNull(registration)
        assertThat(registration.deviceApi).isEqualTo(TestDeviceApi::class.java)
        assertThat(registration.deviceImpl).isEqualTo(TestDeviceImpl::class.java)
        assertThat(registration.setupConfigAction).isNull()
        assertThat(registration.setupTaskAction).isNull()
        assertThat(registration.testRunConfigAction)
            .isEqualTo(TestDeviceTestRunConfigAction::class.java)
        assertThat(registration.testRunTaskAction)
            .isEqualTo(TestDeviceTestRunTaskAction::class.java)
    }

    @Test
    fun registrationWithSetupActions() {
        registry.registerDeviceType(TestDeviceApi::class.java) {
            dslImplementationClass = TestDeviceImpl::class.java
            setSetupActions(
                TestDeviceSetupConfigAction::class.java,
                TestDeviceSetupTaskAction::class.java,
            )
            setTestRunActions(
                TestDeviceTestRunConfigAction::class.java,
                TestDeviceTestRunTaskAction::class.java)
        }

        val registration = registry.get(TestDeviceImpl::class.java)

        requireNotNull(registration)
        assertThat(registration.setupConfigAction).isEqualTo(TestDeviceSetupConfigAction::class.java)
        assertThat(registration.setupTaskAction).isEqualTo(TestDeviceSetupTaskAction::class.java)
    }

    @Test
    fun lookupRegistryByProxyImplClass() {
        registry.registerDeviceType(TestDeviceApi::class.java) {
            dslImplementationClass = TestDeviceImpl::class.java
            setTestRunActions(
                TestDeviceTestRunConfigAction::class.java,
                TestDeviceTestRunTaskAction::class.java)
        }

        val registration = registry.get(TestDeviceImpl::class.java)
        val proxyRegistration = registry.get(testDeviceImplProxy.javaClass)

        assertThat(proxyRegistration).isSameInstanceAs(registration)
    }


    @Test
    fun registerMultipleSameApiShouldFail() {
        registry.registerDeviceType(SharedDeviceApi::class.java) {
            dslImplementationClass = TestDeviceImpl::class.java
            setTestRunActions(
                SharedDeviceTestRunConfigAction::class.java,
                TestDeviceTestRunTaskAction::class.java)
        }

        val exception = assertThrows(IllegalStateException::class.java) {
            registry.registerDeviceType(SharedDeviceApi::class.java) {
                dslImplementationClass = TestDeviceImpl2::class.java
                setTestRunActions(
                    SharedDeviceTestRunConfigAction::class.java,
                    TestDeviceTestRunTaskAction::class.java)
            }
        }

        assertThat(exception.message).isEqualTo(
            "Custom Device Api Class: " +
                    "interface com.android.build.gradle.internal.testing.SharedDeviceApi " +
                    "is already registered with the Managed Device Registry.")
    }

    @Test
    fun registerMultipleSameImplShouldFail() {
        registry.registerDeviceType(TestDeviceApi::class.java) {
            dslImplementationClass = TestDeviceImpl::class.java
            setTestRunActions(
                TestDeviceTestRunConfigAction::class.java,
                TestDeviceTestRunTaskAction::class.java)
        }

        val exception = assertThrows(IllegalStateException::class.java) {
            registry.registerDeviceType(SharedDeviceApi::class.java) {
                dslImplementationClass = TestDeviceImpl::class.java
                setTestRunActions(
                    SharedDeviceTestRunConfigAction::class.java,
                    TestDeviceTestRunTaskAction::class.java)
            }
        }

        assertThat(exception.message).isEqualTo(
            "Custom Device Implementation Class: " +
                    "interface com.android.build.gradle.internal.testing.TestDeviceImpl " +
                    "is already registered with the Managed Device Registry."
        )
    }
}
