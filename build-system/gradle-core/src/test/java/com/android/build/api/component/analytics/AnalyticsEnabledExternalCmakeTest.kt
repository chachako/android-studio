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

import com.android.build.api.variant.ExternalNativeBuild
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

class AnalyticsEnabledExternalCmakeTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    private val delegate: ExternalNativeBuild = mock()

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledExternalCmake by lazy {
        AnalyticsEnabledExternalCmake(delegate, stats)
    }

    @Test
    fun getAbiFilters() {
        @Suppress("UNCHECKED_CAST") val setProperty: SetProperty<String>
                = mock<SetProperty<String>>()
        whenever(delegate.abiFilters).thenReturn(setProperty)
        Truth.assertThat(proxy.abiFilters).isEqualTo(setProperty)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
                stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.CMAKE_OPTIONS_ABI_FILTERS_VALUE)
        verify(delegate, times(1)).abiFilters
        verifyNoMoreInteractions(delegate)
    }

    @Test
    fun getTargets() {
        @Suppress("UNCHECKED_CAST") val setProperty: SetProperty<String>
                = mock<SetProperty<String>>()
        whenever(delegate.targets).thenReturn(setProperty)
        Truth.assertThat(proxy.targets).isEqualTo(setProperty)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
                stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.CMAKE_OPTIONS_TARGETS_VALUE)
        verify(delegate, times(1)).targets
        verifyNoMoreInteractions(delegate)
    }

    @Test
    fun getArguments() {
        @Suppress("UNCHECKED_CAST") val listProperty: ListProperty<String>
                = mock<ListProperty<String>>()
        whenever(delegate.arguments).thenReturn(listProperty)
        Truth.assertThat(proxy.arguments).isEqualTo(listProperty)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
                stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.CMAKE_OPTIONS_ARGUMENTS_VALUE)
        verify(delegate, times(1)).arguments
        verifyNoMoreInteractions(delegate)
    }

    @Test
    fun getCFlags() {
        @Suppress("UNCHECKED_CAST") val listProperty: ListProperty<String>
                = mock<ListProperty<String>>()
        whenever(delegate.cFlags).thenReturn(listProperty)
        Truth.assertThat(proxy.cFlags).isEqualTo(listProperty)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
                stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.CMAKE_OPTIONS_C_FLAGS_VALUE)
        verify(delegate, times(1)).cFlags
        verifyNoMoreInteractions(delegate)
    }

    @Test
    fun getCppFlags() {
        @Suppress("UNCHECKED_CAST") val listProperty: ListProperty<String>
                = mock<ListProperty<String>>()
        whenever(delegate.cppFlags).thenReturn(listProperty)
        Truth.assertThat(proxy.cppFlags).isEqualTo(listProperty)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
                stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.CMAKE_OPTIONS_CPP_FLAGS_VALUE)
        verify(delegate, times(1)).cppFlags
        verifyNoMoreInteractions(delegate)
    }
}
