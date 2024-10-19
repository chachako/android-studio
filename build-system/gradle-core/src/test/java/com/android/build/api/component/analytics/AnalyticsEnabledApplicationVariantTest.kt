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

import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.DependenciesInfo
import com.android.build.api.variant.ApplicationAndroidResources
import com.android.build.api.variant.BundleConfig
import com.android.build.api.variant.DeviceTest
import com.android.build.api.variant.DeviceTestBuilder
import com.android.build.api.variant.JniLibsTestedApkPackaging
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.ResourcesPackaging
import com.android.build.api.variant.SigningConfig
import com.android.build.api.variant.TestFixtures
import com.android.build.api.variant.TestedApkPackaging
import com.android.build.api.variant.VariantOutput
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
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

class AnalyticsEnabledApplicationVariantTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    private val delegate: ApplicationVariant = mock()

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledApplicationVariant by lazy {
        AnalyticsEnabledApplicationVariant(delegate, stats, FakeObjectFactory.factory)
    }

    @Test
    fun getApplicationId() {
        whenever(delegate.applicationId).thenReturn(FakeGradleProperty("myApp"))
        Truth.assertThat(proxy.applicationId.get()).isEqualTo("myApp")

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.APPLICATION_ID_VALUE)
        verify(delegate, times(1))
            .applicationId
    }

    @Test
    fun getOutputs() {
        whenever(delegate.outputs).thenReturn(listOf())
        Truth.assertThat(proxy.outputs).isEqualTo(listOf<VariantOutput>())

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.GET_OUTPUTS_VALUE)
        verify(delegate, times(1))
            .outputs
    }

    @Test
    fun getDependenciesInfo() {
        val dependenciesInfo = mock<DependenciesInfo>()
        whenever(delegate.dependenciesInfo).thenReturn(dependenciesInfo)
        Truth.assertThat(proxy.dependenciesInfo).isEqualTo(dependenciesInfo)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.DEPENDENCIES_INFO_VALUE)
        verify(delegate, times(1))
            .dependenciesInfo
    }

    @Test
    fun getAndroidResources() {
        val androidResources = mock<ApplicationAndroidResources>()
        whenever(delegate.androidResources).thenReturn(androidResources)
        Truth.assertThat(proxy.androidResources).isEqualTo(androidResources)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.AAPT_OPTIONS_VALUE)
        verify(delegate, times(1))
            .androidResources
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

        deviceTestProxy = proxy.androidTest ?: fail("androidTest method returned false")
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

    @Test
    fun getSigningConfig() {
        val signingConfig = mock<SigningConfig>()
        whenever(delegate.signingConfig).thenReturn(signingConfig)
        Truth.assertThat(
            (proxy.signingConfig as AnalyticsEnabledSigningConfig).delegate).isEqualTo(signingConfig)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.SIGNING_CONFIG_VALUE)
        verify(delegate, times(1))
            .signingConfig
    }

    @Test
    fun getRenderscript() {
        val renderscript = mock<Renderscript>()
        whenever(delegate.renderscript).thenReturn(renderscript)
        // simulate a user configuring packaging options for jniLibs and resources
        proxy.renderscript

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
                stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.RENDERSCRIPT_VALUE)
        verify(delegate, times(1)).renderscript
    }

    @Test
    fun getPackaging() {
        val packaging = mock<TestedApkPackaging>()
        val jniLibsPackagingOptions = mock<JniLibsTestedApkPackaging>()
        val resourcesPackagingOptions = mock<ResourcesPackaging>()
        whenever(packaging.jniLibs).thenReturn(jniLibsPackagingOptions)
        whenever(packaging.resources).thenReturn(resourcesPackagingOptions)
        whenever(delegate.packaging).thenReturn(packaging)
        // simulate a user configuring packaging options for jniLibs and resources
        proxy.packaging.jniLibs
        proxy.packaging.resources

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(4)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.map { it.type }
        ).containsExactlyElementsIn(
            listOf(
                VariantPropertiesMethodType.PACKAGING_OPTIONS_VALUE,
                VariantPropertiesMethodType.JNI_LIBS_PACKAGING_OPTIONS_VALUE,
                VariantPropertiesMethodType.PACKAGING_OPTIONS_VALUE,
                VariantPropertiesMethodType.RESOURCES_PACKAGING_OPTIONS_VALUE
            )
        )
        verify(delegate, times(1)).packaging
    }

    @Test
    fun androidTest() {
        @Suppress("DEPRECATION")
        val androidTest = mock<com.android.build.api.variant.AndroidTest>()
        whenever(androidTest.applicationId).thenReturn(FakeGradleProperty("appId"))
        whenever(delegate.androidTest).thenReturn(androidTest)

        proxy.androidTest.let {
            Truth.assertThat(it?.applicationId?.get()).isEqualTo("appId")
        }

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(2)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.map { it.type }
        ).containsExactlyElementsIn(
            listOf(
                VariantPropertiesMethodType.ANDROID_TEST_VALUE,
                VariantPropertiesMethodType.APPLICATION_ID_VALUE,
            )
        )
        verify(delegate, times(1)).androidTest
    }

    @Test
    fun testFixtures() {
        val testFixtures = mock<TestFixtures>()
        whenever(testFixtures.pseudoLocalesEnabled).thenReturn(FakeGradleProperty(false))
        whenever(delegate.testFixtures).thenReturn(testFixtures)

        proxy.testFixtures.let {
            Truth.assertThat(it?.pseudoLocalesEnabled?.get()).isEqualTo(false)
        }

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(2)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.map { it.type }
        ).containsExactlyElementsIn(
            listOf(
                VariantPropertiesMethodType.TEST_FIXTURES_VALUE,
                VariantPropertiesMethodType.VARIANT_PSEUDOLOCALES_ENABLED_VALUE,
            )
        )
        verify(delegate, times(1)).testFixtures
    }

    @Test
    fun testBundleConfig() {
        val bundleConfig = mock<BundleConfig>()
        whenever(delegate.bundleConfig).thenReturn(bundleConfig)

        Truth.assertThat((proxy.bundleConfig as AnalyticsEnabledBundleConfig).delegate)
            .isEqualTo(bundleConfig)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.GET_BUNDLE_CONFIG_VALUE)
        verify(delegate, times(1))
            .bundleConfig
    }
}
