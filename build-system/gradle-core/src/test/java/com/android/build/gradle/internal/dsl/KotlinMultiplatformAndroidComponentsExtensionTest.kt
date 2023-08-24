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

package com.android.build.gradle.internal.dsl

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidExtension
import com.android.build.api.dsl.SdkComponents
import com.android.build.api.extension.impl.KotlinMultiplatformAndroidComponentsExtensionImpl
import com.android.build.api.extension.impl.MultiplatformVariantApiOperationsRegistrar
import com.android.build.api.instrumentation.manageddevice.ManagedDeviceRegistry
import com.android.build.api.variant.KotlinMultiplatformAndroidVariant
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class KotlinMultiplatformAndroidComponentsExtensionTest {
    private lateinit var sdkComponents: SdkComponents
    private lateinit var managedDeviceRegistry: ManagedDeviceRegistry
    private lateinit var applicationExtension: ApplicationExtension

    @Before
    fun setUp() {
        sdkComponents = Mockito.mock(SdkComponents::class.java)
        managedDeviceRegistry = Mockito.mock(ManagedDeviceRegistry::class.java)
        applicationExtension = Mockito.mock(ApplicationExtension::class.java)
    }

    @Test
    fun testPluginVersion() {
        val extension = Mockito.mock(KotlinMultiplatformAndroidExtension::class.java)
        val variantApiOperationsRegistrar = MultiplatformVariantApiOperationsRegistrar(extension)

        val androidComponents = KotlinMultiplatformAndroidComponentsExtensionImpl(
            sdkComponents,
            managedDeviceRegistry,
            variantApiOperationsRegistrar
        )
        Truth.assertThat(androidComponents.pluginVersion).isNotNull()
        Truth.assertThat(androidComponents.pluginVersion >= AndroidPluginVersion(4, 2)).isTrue()
    }

    @Test
    fun testSdkComponents() {
        val extension = Mockito.mock(KotlinMultiplatformAndroidExtension::class.java)
        val variantApiOperationsRegistrar = MultiplatformVariantApiOperationsRegistrar(extension)
        val sdkComponentsFromComponents = KotlinMultiplatformAndroidComponentsExtensionImpl(
            sdkComponents,
            managedDeviceRegistry,
            variantApiOperationsRegistrar
        ).sdkComponents
        Truth.assertThat(sdkComponentsFromComponents).isSameInstanceAs(sdkComponents)
    }

    @Test
    fun testCustomDeviceRegistry() {
        val extension = Mockito.mock(KotlinMultiplatformAndroidExtension::class.java)
        val variantApiOperationsRegistrar = MultiplatformVariantApiOperationsRegistrar(extension)
        val deviceRegistryFromComponents = KotlinMultiplatformAndroidComponentsExtensionImpl(
            sdkComponents,
            managedDeviceRegistry,
            variantApiOperationsRegistrar
        ).managedDeviceRegistry
        Truth.assertThat(deviceRegistryFromComponents).isSameInstanceAs(managedDeviceRegistry)
    }

    @Test
    fun testCallingOnVariant() {
        val extension = Mockito.mock(KotlinMultiplatformAndroidExtension::class.java)
        val variant = Mockito.mock(KotlinMultiplatformAndroidVariant::class.java)
        val variantApiOperationsRegistrar = MultiplatformVariantApiOperationsRegistrar(extension)
        val componentsExtension = KotlinMultiplatformAndroidComponentsExtensionImpl(
            Mockito.mock(SdkComponents::class.java),
            Mockito.mock(ManagedDeviceRegistry::class.java),
            variantApiOperationsRegistrar,
        )

        var called = false
        componentsExtension.onVariant {
            Truth.assertThat(it).isEqualTo(variant)
            called = true
        }

        variantApiOperationsRegistrar.variantOperations.executeOperations(variant)
        Truth.assertThat(called).isTrue()
    }

    @Test
    fun testDslFinalizationBlock() {
        val extension = Mockito.mock(KotlinMultiplatformAndroidExtension::class.java)
        val variantApiOperationsRegistrar = MultiplatformVariantApiOperationsRegistrar(extension)
        val componentsExtension = KotlinMultiplatformAndroidComponentsExtensionImpl(
            Mockito.mock(SdkComponents::class.java),
            Mockito.mock(ManagedDeviceRegistry::class.java),
            variantApiOperationsRegistrar,
        )

        var called = false
        componentsExtension.finalizeDsl {
            Truth.assertThat(it).isEqualTo(extension)
            called = true
        }

        variantApiOperationsRegistrar.executeDslFinalizationBlocks()
        Truth.assertThat(called).isTrue()
    }
}
