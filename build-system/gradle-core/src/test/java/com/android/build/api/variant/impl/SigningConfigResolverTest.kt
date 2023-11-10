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

package com.android.build.api.variant.impl

import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.internal.core.MergedFlavor
import com.android.build.gradle.internal.core.dsl.impl.SigningConfigResolver
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.android.builder.core.ComponentTypeImpl
import com.android.testutils.MockitoKt.any
import com.google.common.truth.Truth
import org.gradle.api.NamedDomainObjectContainer
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

class SigningConfigResolverTest {
    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)
    private val dslServices: DslServices by lazy { createDslServices() }

    private fun extension(): ApplicationExtension {
        val extension = Mockito.mock(ApplicationExtension::class.java)

        @Suppress("UNCHECKED_CAST")
        val domainObject = Mockito.mock(NamedDomainObjectContainer::class.java)
                as NamedDomainObjectContainer<ApkSigningConfig>
        Mockito.`when`(extension.signingConfigs)
            .thenReturn(domainObject)
        return extension
    }

    private fun buildType(name: String) =
        dslServices.newDecoratedInstance(BuildType::class.java, name, dslServices, ComponentTypeImpl.BASE_APK)

    private fun signingConfig(name: String) =
        dslServices.newDecoratedInstance(SigningConfig::class.java, name, dslServices)

    @Test
    fun createWithDslSigningOnly() {
        val buildType = buildType("Test")
        val dslSigningConfig = signingConfig("Test")
        buildType.setSigningConfig(dslSigningConfig)
        val mergedMock = Mockito.mock(MergedFlavor::class.java)

        val resolver = SigningConfigResolver.create(buildType, mergedMock, null, extension(), dslServices)

        Truth.assertThat(resolver.dslSigningConfig).isEqualTo(dslSigningConfig)
        Truth.assertThat(resolver.signingConfigOverride).isNull()
        Truth.assertThat(resolver.debugSigningConfig).isNull()
    }

    @Test
    fun createWithOverrideOnly() {
        val buildType = buildType("Test")
        val override = signingConfig("Test")
        val mergedMock = Mockito.mock(MergedFlavor::class.java)

        val resolver = SigningConfigResolver.create(buildType, mergedMock, override, extension(), dslServices)

        Truth.assertThat(resolver.dslSigningConfig).isNull()
        Truth.assertThat(resolver.signingConfigOverride).isEqualTo(override)
        Truth.assertThat(resolver.debugSigningConfig).isNull()
    }

    @Test
    fun createWithDslAndOverride() {
        val buildType = buildType("Test")

        val dsl = signingConfig("Dsl")
        dsl.enableV1Signing = true
        dsl.enableV2Signing = true
        dsl.enableV3Signing = true
        dsl.enableV4Signing = true
        buildType.setSigningConfig(dsl)

        val override = signingConfig("Override")

        val mergedMock = Mockito.mock(MergedFlavor::class.java)

        val resolver = SigningConfigResolver.create(buildType, mergedMock, override, extension(), dslServices)

        Truth.assertThat(resolver.dslSigningConfig).isEqualTo(dsl)
        Truth.assertThat(resolver.signingConfigOverride).isEqualTo(override)
        Truth.assertThat(resolver.signingConfigOverride!!.enableV1Signing).isEqualTo(true)
        Truth.assertThat(resolver.signingConfigOverride!!.enableV2Signing).isEqualTo(true)
        Truth.assertThat(resolver.signingConfigOverride!!.enableV3Signing).isEqualTo(true)
        Truth.assertThat(resolver.signingConfigOverride!!.enableV4Signing).isEqualTo(true)
        Truth.assertThat(resolver.debugSigningConfig).isNull()
    }

    @Test
    fun createWithDebugConfigFallback() {
        val buildType = buildType("Test")
        val debugConfig = signingConfig("debug")
        val mergedMock = Mockito.mock(MergedFlavor::class.java)
        val extension = extension()
        Mockito.`when`(extension.signingConfigs.findByName(any())).thenReturn(debugConfig)
        val resolver = SigningConfigResolver.create(buildType, mergedMock, null, extension, dslServices)

        Truth.assertThat(resolver.dslSigningConfig).isNull()
        Truth.assertThat(resolver.signingConfigOverride).isNull()
        Truth.assertThat(resolver.debugSigningConfig).isEqualTo(debugConfig)
    }
}
