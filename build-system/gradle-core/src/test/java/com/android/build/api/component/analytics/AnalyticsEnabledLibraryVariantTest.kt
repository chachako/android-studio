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

import com.android.build.api.variant.AarMetadata
import com.android.build.api.variant.AndroidTest
import com.android.build.api.variant.DeviceTest
import com.android.build.api.variant.JniLibsTestedComponentPackaging
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.ResourcesPackaging
import com.android.build.api.variant.TestFixtures
import com.android.build.api.variant.TestedComponentPackaging
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import kotlin.test.fail

class AnalyticsEnabledLibraryVariantTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    lateinit var delegate: LibraryVariant

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledLibraryVariant by lazy {
        AnalyticsEnabledLibraryVariant(delegate, stats, FakeObjectFactory.factory)
    }

    @Test
    fun getPackaging() {
        val packaging = Mockito.mock(TestedComponentPackaging::class.java)
        val jniLibsPackagingOptions = Mockito.mock(JniLibsTestedComponentPackaging::class.java)
        val resourcesPackagingOptions = Mockito.mock(ResourcesPackaging::class.java)
        Mockito.`when`(packaging.jniLibs).thenReturn(jniLibsPackagingOptions)
        Mockito.`when`(packaging.resources).thenReturn(resourcesPackagingOptions)
        Mockito.`when`(delegate.packaging).thenReturn(packaging)
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
        Mockito.verify(delegate, Mockito.times(1)).packaging
    }

    @Test
    fun aarMetadata() {
        val aarMetadata = Mockito.mock(AarMetadata::class.java)
        Mockito.`when`(aarMetadata.minCompileSdk).thenReturn(FakeGradleProperty(5))
        Mockito.`when`(delegate.aarMetadata).thenReturn(aarMetadata)
        Truth.assertThat(proxy.aarMetadata.minCompileSdk.get()).isEqualTo(5)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(2)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.map { it.type }
        ).containsExactlyElementsIn(
            listOf(
                VariantPropertiesMethodType.VARIANT_AAR_METADATA_VALUE,
                VariantPropertiesMethodType.VARIANT_AAR_METADATA_MIN_COMPILE_SDK_VALUE,
            )
        )
        Mockito.verify(delegate, Mockito.times(1)).aarMetadata
    }

    @Test
    fun testFixtures() {
        val testFixtures = Mockito.mock(TestFixtures::class.java)
        Mockito.`when`(testFixtures.pseudoLocalesEnabled).thenReturn(FakeGradleProperty(false))
        Mockito.`when`(delegate.testFixtures).thenReturn(testFixtures)

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
        Mockito.verify(delegate, Mockito.times(1)).testFixtures
    }

    @Test
    fun androidTest() {
        val androidTest = Mockito.mock(AndroidTest::class.java)
        Mockito.`when`(androidTest.pseudoLocalesEnabled).thenReturn(FakeGradleProperty(false))
        Mockito.`when`(delegate.androidTest).thenReturn(androidTest)

        proxy.androidTest.let {
            Truth.assertThat(it?.pseudoLocalesEnabled?.get()).isEqualTo(false)
        }

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(2)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.map { it.type }
        ).containsExactlyElementsIn(
            listOf(
                VariantPropertiesMethodType.ANDROID_TEST_VALUE,
                VariantPropertiesMethodType.VARIANT_PSEUDOLOCALES_ENABLED_VALUE,
            )
        )
        Mockito.verify(delegate, Mockito.times(1)).androidTest
    }

    @Test
    fun getDeviceTests() {
        val deviceTest = Mockito.mock(DeviceTest::class.java)
        Mockito.`when`(delegate.deviceTests).thenReturn(listOf(deviceTest))
        val deviceTestsProxy = proxy.deviceTests

        Truth.assertThat(deviceTestsProxy.size).isEqualTo(1)
        val deviceTestProxy = deviceTestsProxy.single()
        Truth.assertThat(deviceTestProxy is AnalyticsEnabledDeviceTest).isTrue()
        Truth.assertThat((deviceTestProxy as AnalyticsEnabledDeviceTest).delegate).isEqualTo(deviceTest)
        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.DEVICE_TESTS_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .deviceTests
    }

    @Test
    fun getDeviceTests_for_android_test() {
        val deviceTest = Mockito.mock(AndroidTest::class.java)
        Mockito.`when`(delegate.deviceTests).thenReturn(listOf(deviceTest))
        Mockito.`when`(delegate.androidTest).thenReturn(deviceTest)
        val deviceTestsProxy = proxy.deviceTests

        Truth.assertThat(deviceTestsProxy.size).isEqualTo(1)
        var deviceTestProxy = deviceTestsProxy.single()
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
        Mockito.verify(delegate, Mockito.times(1))
            .deviceTests
        Mockito.verify(delegate, Mockito.times(1))
            .androidTest
    }
}
