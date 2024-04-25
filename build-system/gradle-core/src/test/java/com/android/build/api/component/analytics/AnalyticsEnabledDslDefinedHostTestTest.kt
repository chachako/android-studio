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

import com.android.build.api.variant.HostTest
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.model.ObjectFactory
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import java.util.concurrent.atomic.AtomicBoolean

class AnalyticsEnabledDslDefinedHostTestTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    lateinit var delegate: HostTest

    @Mock
    lateinit var objectFactory: ObjectFactory

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledHostTest by lazy {
        AnalyticsEnabledHostTest(delegate, stats, objectFactory)
    }

    @Test
    fun configureTestTask() {
        val lambdaCalled = AtomicBoolean(false)
        val function: (org.gradle.api.tasks.testing.Test) -> Unit = { }
        Mockito.doAnswer { lambdaCalled.set(true) }.`when`(delegate).configureTestTask(function)

        proxy.configureTestTask(function)
        Truth.assertThat(lambdaCalled.get()).isTrue()

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.CONFIGURE_TEST_TASK_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .configureTestTask(function)
    }

    @Test
    fun codeCoverageEnabled() {
        Mockito.`when`(delegate.codeCoverageEnabled).thenReturn(true)
        Truth.assertThat(proxy.codeCoverageEnabled).isEqualTo(true)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.HOST_TEST_CODE_COVERAGE_ENABLED_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .codeCoverageEnabled
    }
}
