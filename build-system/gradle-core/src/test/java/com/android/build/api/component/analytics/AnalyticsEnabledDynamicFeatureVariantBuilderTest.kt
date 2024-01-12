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

import com.android.build.api.variant.DeviceTestBuilder
import com.android.build.api.variant.DynamicFeatureVariantBuilder
import com.android.tools.build.gradle.internal.profile.VariantMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

class AnalyticsEnabledDynamicFeatureVariantBuilderTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    lateinit var delegate: DynamicFeatureVariantBuilder

    @Suppress("DEPRECATION")
    @Mock
    lateinit var androidTestBuilder: com.android.build.api.variant.AndroidTestBuilder

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledDynamicFeatureVariantBuilder by lazy {
        AnalyticsEnabledDynamicFeatureVariantBuilder(delegate, stats)
    }

    @Before
    fun setup() {
        @Suppress("DEPRECATION")
        Mockito.`when`(delegate.androidTest).thenReturn(androidTestBuilder)
    }

    @Test
    fun getDeviceTests() {
        val deviceTest = Mockito.mock(DeviceTestBuilder::class.java)
        Mockito.`when`(delegate.deviceTests).thenReturn(listOf(deviceTest))
        val deviceTestsProxy = proxy.deviceTests

        Truth.assertThat(deviceTestsProxy.size).isEqualTo(1)
        val deviceTestProxy = deviceTestsProxy.single()
        Truth.assertThat(deviceTestProxy is AnalyticsEnabledDeviceTestBuilder).isTrue()
        Truth.assertThat((deviceTestProxy as AnalyticsEnabledDeviceTestBuilder).delegate).isEqualTo(deviceTest)
        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.DEVICE_TESTS_BUILDER_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .deviceTests
    }

    @Suppress("DEPRECATION")
    @Test
    fun getDeviceTests_for_android_test() {
        val deviceTest = Mockito.mock(com.android.build.api.variant.AndroidTestBuilder::class.java)
        Mockito.`when`(delegate.deviceTests).thenReturn(listOf(deviceTest))
        val deviceTestsProxy = proxy.deviceTests

        Truth.assertThat(deviceTestsProxy.size).isEqualTo(1)
        val deviceTestProxy = deviceTestsProxy.single()
        Truth.assertThat(deviceTestProxy is AnalyticsEnabledAndroidTestBuilder).isTrue()
        Truth.assertThat((deviceTestProxy as AnalyticsEnabledAndroidTestBuilder).delegate).isEqualTo(deviceTest)

        val androidTestProxy = proxy.androidTest
        Truth.assertThat(androidTestProxy is AnalyticsEnabledAndroidTestBuilder).isTrue()
        Truth.assertThat((androidTestProxy as AnalyticsEnabledAndroidTestBuilder).delegate).isEqualTo(androidTestBuilder)

        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(2)
        Truth.assertThat(
            stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.DEVICE_TESTS_BUILDER_VALUE)
        Truth.assertThat(
            stats.variantApiAccess.variantAccessList.last().type
        ).isEqualTo(VariantMethodType.ANDROID_TEST_BUILDER_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .deviceTests
        Mockito.verify(delegate, Mockito.times(1))
            .androidTest
    }

}
