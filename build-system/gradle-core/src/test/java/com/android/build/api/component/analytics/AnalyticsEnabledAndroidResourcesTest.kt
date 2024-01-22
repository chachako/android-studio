/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.build.gradle.internal.fixtures.FakeListProperty
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

class AnalyticsEnabledAndroidResourcesTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    lateinit var delegate: AndroidResources

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledAndroidResources by lazy {
        AnalyticsEnabledAndroidResources(delegate, stats)
    }

    @Test
    fun ignoreAssetsPatterns() {
        val list = FakeListProperty<String>()
        Mockito.`when`(delegate.ignoreAssetsPatterns).thenReturn(list)
        Truth.assertThat(proxy.ignoreAssetsPatterns).isEqualTo(list)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.IGNORE_ASSETS_PATTERN_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .ignoreAssetsPatterns
    }

    @Test
    fun aaptAdditionalParameters() {
        val list = FakeListProperty<String>()
        Mockito.`when`(delegate.aaptAdditionalParameters).thenReturn(list)
        Truth.assertThat(proxy.aaptAdditionalParameters).isEqualTo(list)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.AAPT_ADDITIONAL_PARAMETERS_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .aaptAdditionalParameters
    }

    @Test
    fun noCompress() {
        val list = FakeListProperty<String>()
        Mockito.`when`(delegate.noCompress).thenReturn(list)
        Truth.assertThat(proxy.noCompress).isEqualTo(list)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.NO_COMPRESS_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .noCompress
    }

    @Test
    fun testDataBindingComponents() {
        Mockito.`when`(delegate.dataBinding).thenReturn(true)
        Truth.assertThat(proxy.dataBinding).isEqualTo(true)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.DATA_BINDING_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .dataBinding
    }


    @Test
    fun testViewBindingComponents() {
        Mockito.`when`(delegate.viewBinding).thenReturn(true)
        Truth.assertThat(proxy.viewBinding).isEqualTo(true)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.VIEW_BINDING_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .viewBinding
    }

}
