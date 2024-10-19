/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.build.api.component.analytics

import com.android.build.api.variant.DeviceTest
import com.android.build.api.variant.DeviceTestBuilder
import com.android.build.api.variant.KotlinMultiplatformAndroidVariant
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import kotlin.test.fail

class AnalyticsEnabledKotlinMultiplatformAndroidVariantTest {
    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    private val delegate: KotlinMultiplatformAndroidVariant = mock()

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledKotlinMultiplatformAndroidVariant by lazy {
        AnalyticsEnabledKotlinMultiplatformAndroidVariant(delegate, stats, FakeObjectFactory.factory)
    }

    @Test
    fun getDeviceTests() {
        val deviceTest = mock<DeviceTest>()
        whenever(delegate.deviceTests).thenReturn(mapOf(DeviceTestBuilder.ANDROID_TEST_TYPE to deviceTest))
        val deviceTestsProxy = proxy.deviceTests

        Truth.assertThat(deviceTestsProxy.size).isEqualTo(1)
        val deviceTestProxy = deviceTestsProxy[DeviceTestBuilder.ANDROID_TEST_TYPE]
        Truth.assertThat(deviceTestProxy is AnalyticsEnabledDeviceTest).isTrue()
        Truth.assertThat((deviceTestProxy as AnalyticsEnabledDeviceTest).delegate).isEqualTo(deviceTest)
        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.DEVICE_TESTS_VALUE)
        verify(delegate, times(1))
            .deviceTests
    }

    @Test
    fun getDeviceTests_for_android_test() {
        @Suppress("DEPRECATION")
        val deviceTest = mock<com.android.build.api.variant.AndroidTest>()
        whenever(delegate.deviceTests).thenReturn(mapOf(DeviceTestBuilder.ANDROID_TEST_TYPE to deviceTest))
        whenever(delegate.androidTest).thenReturn(deviceTest)
        val deviceTestsProxy = proxy.deviceTests

        Truth.assertThat(deviceTestsProxy.size).isEqualTo(1)
        var deviceTestProxy = deviceTestsProxy[DeviceTestBuilder.ANDROID_TEST_TYPE]
        Truth.assertThat(deviceTestProxy is AnalyticsEnabledAndroidTest).isTrue()
        Truth.assertThat((deviceTestProxy as AnalyticsEnabledAndroidTest).delegate).isEqualTo(deviceTest)

        deviceTestProxy = proxy.androidTest ?: fail("deviceTest method returned null")
        Truth.assertThat(deviceTestProxy is AnalyticsEnabledAndroidTest).isTrue()
        Truth.assertThat((deviceTestProxy as AnalyticsEnabledAndroidTest).delegate).isEqualTo(deviceTest)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(2)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.DEVICE_TESTS_VALUE)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.last().type
        ).isEqualTo(VariantPropertiesMethodType.ANDROID_TEST_VALUE)
        verify(delegate, times(1))
            .deviceTests
        verify(delegate, times(1))
            .androidTest
    }
}
