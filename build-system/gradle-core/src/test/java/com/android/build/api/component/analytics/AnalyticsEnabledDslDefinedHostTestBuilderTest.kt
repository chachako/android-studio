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

import com.android.build.api.variant.HostTestBuilder
import com.android.tools.build.gradle.internal.profile.VariantMethodType
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

class AnalyticsEnabledDslDefinedHostTestBuilderTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    private val delegate: HostTestBuilder = mock()

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledHostTestBuilder by lazy {
        AnalyticsEnabledHostTestBuilder(delegate, stats)
    }

    @Test
    fun testEnable() {
        proxy.enable = true

        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.UNIT_TEST_ENABLED_VALUE)
        verify(delegate, times(1)).enable = true
    }

    @Test
    fun testEnableCodeCoverage() {
        proxy.enableCodeCoverage = true

        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.HOST_TEST_ENABLE_CODE_COVERAGE_VALUE)
        verify(delegate, times(1)).enableCodeCoverage = true
    }
}
