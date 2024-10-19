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

import com.android.build.api.variant.AndroidResources
import com.android.build.api.variant.AndroidTest
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.JniLibsApkPackaging
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.ResValue
import com.android.build.api.variant.ResourcesPackaging
import com.android.build.api.variant.SigningConfig
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import java.io.Serializable

class AnalyticsEnabledAndroidTestTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    private val delegate: AndroidTest = mock()

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledAndroidTest by lazy {
        AnalyticsEnabledAndroidTest(delegate, stats, FakeObjectFactory.factory)
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
    fun getAndroidResources() {
        val androidResources = mock<AndroidResources>()
        whenever(delegate.androidResources).thenReturn(androidResources)
        val proxiedAndroidResources = proxy.androidResources
        Truth.assertThat(proxiedAndroidResources).isInstanceOf(
            AnalyticsEnabledAndroidResources::class.java
        )
        Truth.assertThat((proxiedAndroidResources as AnalyticsEnabledAndroidResources).delegate)
            .isEqualTo(androidResources)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.AAPT_OPTIONS_VALUE)
        verify(delegate, times(1))
            .androidResources
    }

    @Test
    fun instrumentationRunner() {
        whenever(delegate.instrumentationRunner).thenReturn(FakeGradleProperty("my_runner"))
        Truth.assertThat(proxy.instrumentationRunner.get()).isEqualTo("my_runner")

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.INSTRUMENTATION_RUNNER_VALUE)
        verify(delegate, times(1))
            .instrumentationRunner
    }

    @Test
    fun handleProfiling() {
        whenever(delegate.handleProfiling).thenReturn(FakeGradleProperty(true))
        Truth.assertThat(proxy.handleProfiling.get()).isEqualTo(true)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.HANDLE_PROFILING_VALUE)
        verify(delegate, times(1))
            .handleProfiling
    }

    @Test
    fun functionalTest() {
        whenever(delegate.functionalTest).thenReturn(FakeGradleProperty(true))
        Truth.assertThat(proxy.functionalTest.get()).isEqualTo(true)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.FUNCTIONAL_TEST_VALUE)
        verify(delegate, times(1))
            .functionalTest
    }

    @Test
    fun testLabel() {
        whenever(delegate.testLabel).thenReturn(FakeGradleProperty("some_label"))
        Truth.assertThat(proxy.testLabel.get()).isEqualTo("some_label")

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.TEST_LABEL_VALUE)
        verify(delegate, times(1))
            .testLabel
    }

    @Test
    fun getBuildConfigFields() {
        @Suppress("UNCHECKED_CAST")
        val map: MapProperty<String, BuildConfigField<out Serializable>> =
            mock<MapProperty<String, BuildConfigField<out Serializable>>>()
        whenever(delegate.buildConfigFields).thenReturn(map)
        Truth.assertThat(proxy.buildConfigFields).isEqualTo(map)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.BUILD_CONFIG_FIELDS_VALUE)
        verify(delegate, times(1))
            .buildConfigFields
    }


    @Test
    fun getResValues() {
        @Suppress("UNCHECKED_CAST")
        val map: MapProperty<ResValue.Key, ResValue> =
            mock<MapProperty<ResValue.Key, ResValue>>()
        whenever(delegate.resValues).thenReturn(map)
        Truth.assertThat(proxy.resValues).isEqualTo(map)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.RES_VALUE_VALUE)
        verify(delegate, times(1))
            .resValues
    }

    @Test
    fun getManifestPlaceholders() {
        @Suppress("UNCHECKED_CAST")
        val map: MapProperty<String, String> =
            mock<MapProperty<String, String>>()
        whenever(delegate.manifestPlaceholders).thenReturn(map)
        Truth.assertThat(proxy.manifestPlaceholders).isEqualTo(map)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.MANIFEST_PLACEHOLDERS_VALUE)
        verify(delegate, times(1))
            .manifestPlaceholders
    }

    @Test
    fun getSigningConfig() {
        val signingConfig = mock<SigningConfig>()
        whenever(delegate.signingConfig).thenReturn(signingConfig)
        Truth.assertThat(proxy.signingConfig).isEqualTo(signingConfig)

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
    fun getApkPackaging() {
        val apkPackaging = mock<ApkPackaging>()
        val jniLibsApkPackagingOptions = mock<JniLibsApkPackaging>()
        val resourcesPackagingOptions = mock<ResourcesPackaging>()
        whenever(apkPackaging.jniLibs).thenReturn(jniLibsApkPackagingOptions)
        whenever(apkPackaging.resources).thenReturn(resourcesPackagingOptions)
        whenever(delegate.packaging).thenReturn(apkPackaging)
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
    fun getProguardFiles() {
        @Suppress("UNCHECKED_CAST")
        val proguardFiles = mock<ListProperty<RegularFile>>()
        whenever(delegate.proguardFiles).thenReturn(proguardFiles)

        Truth.assertThat(proxy.proguardFiles).isEqualTo(proguardFiles)
        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.PROGUARD_FILES_VALUE)
    }

    @Test
    fun isCodeCoverageEnabled() {
        whenever(delegate.codeCoverageEnabled).thenReturn(true)
        Truth.assertThat(proxy.codeCoverageEnabled).isTrue()

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.DEVICE_TEST_CODE_COVERAGE_ENABLED_VALUE)
        verify(delegate, times(1)).codeCoverageEnabled
    }
}
