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

package com.android.build.api.component.analytics

import com.android.build.api.variant.AndroidTestBuilder
import com.android.build.api.variant.ApplicationAndroidResourcesBuilder
import com.android.build.api.variant.ApplicationVariantBuilder
import com.android.build.api.variant.DeviceTestBuilder
import com.android.build.api.variant.PropertyAccessNotAllowedException
import com.android.build.api.variant.HostTestBuilder
import com.android.tools.build.gradle.internal.profile.VariantMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.junit.Assert
import org.junit.Before
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

internal class AnalyticsEnabledApplicationVariantBuilderTest {
    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    private val delegate: ApplicationVariantBuilder = mock()

    @Suppress("DEPRECATION")
    private val androidTest: com.android.build.api.variant.AndroidTestBuilder = mock()

    private val unitTest: HostTestBuilder = mock()

    private val screenshotTest: HostTestBuilder = mock()

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledApplicationVariantBuilder by lazy {
        AnalyticsEnabledApplicationVariantBuilder(delegate, stats)
    }

    @Before
    fun setup() {
        @Suppress("DEPRECATION")
        whenever(delegate.androidTest).thenReturn(androidTest)
    }

    @Test
    fun dependenciesInfo() {
        proxy.dependenciesInfo

        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(1)
        Truth.assertThat(
                stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.VARIANT_BUILDER_DEPENDENCIES_INFO_VALUE)
    }

    @Test
    fun testFixtures() {
        proxy.enableTestFixtures = true

        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.TEST_FIXTURES_ENABLED_VALUE)
    }

    @Test
    fun testProfileableWriteOnly() {
        proxy.profileable = true

        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.PROFILEABLE_ENABLED_VALUE)
        val exception = Assert.assertThrows(PropertyAccessNotAllowedException::class.java) {
            // direct call of proxy.profileable fails compilation
            // we do small workaround here
            val func = proxy::profileable
            func.get()
        }
        Truth.assertThat(exception.message).isEqualTo(
            """
                You cannot access 'profileable' on ApplicationVariantBuilder in the [AndroidComponentsExtension.beforeVariants]
                callbacks. Other plugins applied later can still change this value, it is not safe
                to read at this stage.""".trimIndent())
    }

    @Test
    fun testAndroidResources() {
        val androidResources = mock<ApplicationAndroidResourcesBuilder>()
        whenever(delegate.androidResources).thenReturn(androidResources)
        val proxiedAndroidResources = proxy.androidResources
        Truth.assertThat(proxiedAndroidResources).isInstanceOf(
            AnalyticsEnabledApplicationAndroidResourcesBuilder::class.java
        )
        Truth.assertThat(
            (proxiedAndroidResources as AnalyticsEnabledApplicationAndroidResourcesBuilder).delegate
        ).isEqualTo(androidResources)

        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.ANDROID_RESOURCES_BUILDER_VALUE)
    }

    @Test
    fun getDeviceTests() {
        val deviceTest = mock<DeviceTestBuilder>()
        whenever(delegate.deviceTests).thenReturn(mapOf(DeviceTestBuilder.ANDROID_TEST_TYPE to deviceTest))
        val deviceTestsProxy = proxy.deviceTests

        Truth.assertThat(deviceTestsProxy.size).isEqualTo(1)
        val deviceTestProxy = deviceTestsProxy[DeviceTestBuilder.ANDROID_TEST_TYPE]
        Truth.assertThat(deviceTestProxy is AnalyticsEnabledDeviceTestBuilder).isTrue()
        Truth.assertThat((deviceTestProxy as AnalyticsEnabledDeviceTestBuilder).delegate).isEqualTo(deviceTest)
        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.DEVICE_TESTS_BUILDER_VALUE)
        verify(delegate, times(1))
            .deviceTests
    }

    @Test
    fun getDeviceTests_for_android_test() {
        @Suppress("DEPRECATION") val deviceTest = mock<com.android.build.api.variant.AndroidTestBuilder>()
        whenever(delegate.deviceTests).thenReturn(mapOf(DeviceTestBuilder.ANDROID_TEST_TYPE to deviceTest))
        val deviceTestsProxy = proxy.deviceTests

        Truth.assertThat(deviceTestsProxy.size).isEqualTo(1)
        val deviceTestProxy = deviceTestsProxy[DeviceTestBuilder.ANDROID_TEST_TYPE]
        Truth.assertThat(deviceTestProxy is AnalyticsEnabledAndroidTestBuilder).isTrue()
        Truth.assertThat((deviceTestProxy as AnalyticsEnabledAndroidTestBuilder).delegate).isEqualTo(deviceTest)

        val androidTestProxy = proxy.androidTest ?: fail("androidTest method returned false")
        Truth.assertThat(androidTestProxy is AnalyticsEnabledAndroidTestBuilder).isTrue()
        Truth.assertThat((androidTestProxy as AnalyticsEnabledAndroidTestBuilder).delegate).isEqualTo(androidTest)

        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(2)
        Truth.assertThat(
            stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.DEVICE_TESTS_BUILDER_VALUE)
        Truth.assertThat(
            stats.variantApiAccess.variantAccessList.last().type
        ).isEqualTo(VariantMethodType.ANDROID_TEST_BUILDER_VALUE)
        verify(delegate, times(1))
            .deviceTests
        verify(delegate, times(1))
            .androidTest
    }

    @Test
    fun testAndroidTest() {
        @Suppress("DEPRECATION")
        Truth.assertThat(proxy.androidTest).isInstanceOf(AndroidTestBuilder::class.java)
        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.ANDROID_TEST_BUILDER_VALUE)
    }

    @Test
    fun testHostTests() {
        Truth.assertThat(proxy.hostTests).isInstanceOf(Map::class.java)
        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.HOST_TESTS_BUILDER_VALUE)
    }
}
