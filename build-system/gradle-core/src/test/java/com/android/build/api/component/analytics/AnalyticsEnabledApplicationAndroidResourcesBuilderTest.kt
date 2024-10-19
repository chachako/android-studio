package com.android.build.api.component.analytics

import com.android.build.api.variant.ApplicationAndroidResourcesBuilder
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

class AnalyticsEnabledApplicationAndroidResourcesBuilderTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    private val delegate: ApplicationAndroidResourcesBuilder = mock()

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledApplicationAndroidResourcesBuilder by lazy(LazyThreadSafetyMode.SYNCHRONIZED)  {
        AnalyticsEnabledApplicationAndroidResourcesBuilder(delegate, stats)
    }

    @Test
    fun generateLocaleConfig() {
        proxy.generateLocaleConfig = true

        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.GENERATE_LOCALE_CONFIG_BUILDER_VALUE)
        verify(delegate, times(1))
            .generateLocaleConfig = true
    }
}
