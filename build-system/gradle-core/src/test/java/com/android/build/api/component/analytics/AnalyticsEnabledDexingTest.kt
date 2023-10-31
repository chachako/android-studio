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

import com.android.build.api.variant.Dexing
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.file.RegularFileProperty
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

class AnalyticsEnabledDexingTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    lateinit var delegate: Dexing

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledDexing by lazy {
        AnalyticsEnabledDexing(delegate, stats)
    }

    @Test
    fun testMultiDexKeepProguard() {
        val returnValue = Mockito.mock(RegularFileProperty::class.java)
        Mockito.`when`(delegate.multiDexKeepProguard).thenReturn(returnValue)
        Truth.assertThat(proxy.multiDexKeepProguard).isEqualTo(returnValue)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.MULTI_DEX_KEEP_PROGUARD_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .multiDexKeepProguard
        Mockito.verifyNoMoreInteractions(delegate)
    }

    @Test
    fun testMultiDexKeepFile() {
        val returnValue = Mockito.mock(RegularFileProperty::class.java)
        Mockito.`when`(delegate.multiDexKeepFile).thenReturn(returnValue)
        Truth.assertThat(proxy.multiDexKeepFile).isEqualTo(returnValue)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.MULTI_DEX_KEEP_FILE_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .multiDexKeepFile
        Mockito.verifyNoMoreInteractions(delegate)
    }
}
