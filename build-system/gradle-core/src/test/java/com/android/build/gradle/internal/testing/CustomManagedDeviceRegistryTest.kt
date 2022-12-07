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
import com.android.build.api.instrumentation.manageddevice.DeviceSetupInput
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunInput
import com.android.build.api.instrumentation.manageddevice.ManagedDeviceDslRegistration
import com.android.build.api.instrumentation.manageddevice.ManagedDeviceSetupFactory
import com.android.build.api.instrumentation.manageddevice.ManagedDeviceTestRunFactory
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.testutils.MockitoKt.mock
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.withSettings
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.lang.RuntimeException
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import javax.inject.Inject

private interface TestDeviceApi : Device

private interface SharedApi : Device

private interface TestDeviceImpl: TestDeviceApi, SharedApi

private interface TestDeviceApi2 : Device

private interface TestDeviceImpl2: TestDeviceApi2, SharedApi

private val testDeviceImplProxy: TestDeviceImpl = Proxy.newProxyInstance(
    CustomManagedDeviceRegistryTest::class.java.classLoader,
    arrayOf<Class<*>>(TestDeviceImpl::class.java)) { _, _, _ ->
        null
    } as TestDeviceImpl

@RunWith(JUnit4::class)
class CustomManagedDeviceRegistryTest {
    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    private lateinit var registry: CustomManagedDeviceRegistry

    @Mock
    private lateinit var testOptions: TestOptions

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var managedDevicesBlock: ManagedDevices

    @Before
    fun setup() {
        `when`(testOptions.managedDevices).thenReturn(managedDevicesBlock)
        registry = CustomManagedDeviceRegistry(FakeGradleProvider(testOptions))
    }

    @Test
    fun register_testRegistrationNoSetup() {
        val dslRegistration: ManagedDeviceDslRegistration<TestDeviceApi> =
            mock(withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS))
        `when`(dslRegistration.deviceImpl).thenReturn(TestDeviceImpl::class.java)
        val testRunFactory: ManagedDeviceTestRunFactory<TestDeviceApi, DeviceTestRunInput> = mock()

        registry.registerCustomDeviceType(dslRegistration, testRunFactory)

        val registration = registry.get(TestDeviceImpl::class.java)

        assertThat(registration).isNotNull()
        assertThat(registration!!.dsl).isEqualTo(dslRegistration)
        assertThat(registration.setupFactory).isNull()
        assertThat(registration.testRunFactory).isEqualTo(testRunFactory)

        val proxyRegistration = registry.get(testDeviceImplProxy.javaClass)
        assertThat(proxyRegistration).isSameInstanceAs(registration)
    }

    @Test
    fun register_testRegistrationWithSetup() {
        val dslRegistration: ManagedDeviceDslRegistration<TestDeviceApi> =
            mock(withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS))
        `when`(dslRegistration.deviceImpl).thenReturn(TestDeviceImpl::class.java)
        val setupFactory: ManagedDeviceSetupFactory<TestDeviceApi, DeviceSetupInput> = mock()
        val testRunFactory: ManagedDeviceTestRunFactory<TestDeviceApi, DeviceTestRunInput> = mock()

        registry.registerCustomDeviceType(dslRegistration, setupFactory, testRunFactory)

        val registration = registry.get(TestDeviceImpl::class.java)

        assertThat(registration).isNotNull()
        assertThat(registration!!.dsl).isEqualTo(dslRegistration)
        assertThat(registration.setupFactory).isEqualTo(setupFactory)
        assertThat(registration.testRunFactory).isEqualTo(testRunFactory)

        val proxyRegistration = registry.get(testDeviceImplProxy.javaClass)
        assertThat(proxyRegistration).isSameInstanceAs(registration)
    }

    @Test
    fun register_testRegistrationMultiple() {
        val dslRegistration1: ManagedDeviceDslRegistration<TestDeviceApi> = mock()
        `when`(dslRegistration1.deviceApi).thenReturn(TestDeviceApi::class.java)
        `when`(dslRegistration1.deviceImpl).thenReturn(TestDeviceImpl::class.java)
        val testRunFactory1: ManagedDeviceTestRunFactory<TestDeviceApi, DeviceTestRunInput> = mock()

        val dslRegistration2: ManagedDeviceDslRegistration<TestDeviceApi2> = mock()
        `when`(dslRegistration2.deviceApi).thenReturn(TestDeviceApi2::class.java)
        `when`(dslRegistration2.deviceImpl).thenReturn(TestDeviceImpl2::class.java)
        val testRunFactory2: ManagedDeviceTestRunFactory<TestDeviceApi2, DeviceTestRunInput> = mock()

        registry.registerCustomDeviceType(dslRegistration1, testRunFactory1)
        registry.registerCustomDeviceType(dslRegistration2, testRunFactory2)

        val registration1 = registry.get(TestDeviceImpl::class.java)
        val registration2 = registry.get(TestDeviceImpl2::class.java)

        assertThat(registration1).isNotNull()
        assertThat(registration1!!.dsl).isEqualTo(dslRegistration1)
        assertThat(registration1.setupFactory).isNull()
        assertThat(registration1.testRunFactory).isEqualTo(testRunFactory1)

        val proxyRegistration = registry.get(testDeviceImplProxy.javaClass)
        assertThat(proxyRegistration).isSameInstanceAs(registration1)

        assertThat(registration2).isNotNull()
        assertThat(registration2!!.dsl).isEqualTo(dslRegistration2)
        assertThat(registration2.setupFactory).isNull()
        assertThat(registration2.testRunFactory).isEqualTo(testRunFactory2)
    }

    @Test
    fun register_testRegisterMultipleSameApiFails() {
        val dslRegistration1: ManagedDeviceDslRegistration<SharedApi> = mock()
        `when`(dslRegistration1.deviceApi).thenReturn(SharedApi::class.java)
        `when`(dslRegistration1.deviceImpl).thenReturn(TestDeviceImpl::class.java)
        val testRunFactory1: ManagedDeviceTestRunFactory<SharedApi, DeviceTestRunInput> = mock()

        val dslRegistration2: ManagedDeviceDslRegistration<SharedApi> = mock()
        `when`(dslRegistration2.deviceApi).thenReturn(SharedApi::class.java)
        `when`(dslRegistration2.deviceImpl).thenReturn(TestDeviceImpl2::class.java)
        val testRunFactory2: ManagedDeviceTestRunFactory<SharedApi, DeviceTestRunInput> = mock()
        val testSetupFactory2: ManagedDeviceSetupFactory<SharedApi, DeviceSetupInput> = mock()

        registry.registerCustomDeviceType(dslRegistration1, testRunFactory1)

        var exception = assertThrows(IllegalStateException::class.java) {
            registry.registerCustomDeviceType(dslRegistration2, testRunFactory2)
        }

        assertThat(exception.message).isEqualTo(
            """
                Custom Device Api Class: interface com.android.build.gradle.internal.testing.SharedApi
                is already registered with the Custom Managed Device Registry.
            """.trimIndent()
        )

        exception = assertThrows(IllegalStateException::class.java) {
            registry.registerCustomDeviceType(dslRegistration2, testSetupFactory2, testRunFactory2)
        }

        assertThat(exception.message).isEqualTo(
            """
                Custom Device Api Class: interface com.android.build.gradle.internal.testing.SharedApi
                is already registered with the Custom Managed Device Registry.
            """.trimIndent()
        )

        val registration = registry.get(TestDeviceImpl::class.java)

        assertThat(registration).isNotNull()
        assertThat(registration!!.dsl).isEqualTo(dslRegistration1)
        assertThat(registration.setupFactory).isNull()
        assertThat(registration.testRunFactory).isEqualTo(testRunFactory1)

        assertThat(registry.get(TestDeviceImpl2::class.java)).isNull()
    }

    @Test
    fun register_testRegisterMultipleSameImplFails() {
        val dslRegistration1: ManagedDeviceDslRegistration<SharedApi> = mock()
        `when`(dslRegistration1.deviceApi).thenReturn(SharedApi::class.java)
        `when`(dslRegistration1.deviceImpl).thenReturn(TestDeviceImpl::class.java)
        val testRunFactory1: ManagedDeviceTestRunFactory<SharedApi, DeviceTestRunInput> = mock()

        val dslRegistration2: ManagedDeviceDslRegistration<TestDeviceApi> = mock()
        `when`(dslRegistration2.deviceApi).thenReturn(TestDeviceApi::class.java)
        `when`(dslRegistration2.deviceImpl).thenReturn(TestDeviceImpl::class.java)
        val testRunFactory2: ManagedDeviceTestRunFactory<TestDeviceApi, DeviceTestRunInput> = mock()

        registry.registerCustomDeviceType(dslRegistration1, testRunFactory1)

        var exception = assertThrows(IllegalStateException::class.java) {
            registry.registerCustomDeviceType(dslRegistration2, testRunFactory2)
        }

        assertThat(exception.message).isEqualTo(
            """
                Custom Device Implementation Class: interface com.android.build.gradle.internal.testing.TestDeviceImpl
                is already registered with the Custom Managed Device Registry.
            """.trimIndent()
        )

        val registration = registry.get(TestDeviceImpl::class.java)

        assertThat(registration).isNotNull()
        assertThat(registration!!.dsl).isEqualTo(dslRegistration1)
        assertThat(registration.setupFactory).isNull()
        assertThat(registration.testRunFactory).isEqualTo(testRunFactory1)
    }
}
