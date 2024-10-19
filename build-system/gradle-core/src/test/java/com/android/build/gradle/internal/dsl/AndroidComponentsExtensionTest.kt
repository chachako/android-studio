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

package com.android.build.gradle.internal.dsl

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.SdkComponents
import com.android.build.api.dsl.TestExtension
import com.android.build.api.extension.impl.AndroidComponentsExtensionImpl
import com.android.build.api.extension.impl.ApplicationAndroidComponentsExtensionImpl
import com.android.build.api.extension.impl.DynamicFeatureAndroidComponentsExtensionImpl
import com.android.build.api.extension.impl.LibraryAndroidComponentsExtensionImpl
import com.android.build.api.extension.impl.TestAndroidComponentsExtensionImpl
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.instrumentation.manageddevice.ManagedDeviceRegistry
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.ApplicationVariantBuilder
import com.android.build.api.variant.DslExtension
import com.android.build.api.variant.DynamicFeatureVariant
import com.android.build.api.variant.DynamicFeatureVariantBuilder
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.LibraryVariantBuilder
import com.android.build.api.variant.TestVariant
import com.android.build.api.variant.TestVariantBuilder
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.VariantExtension
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.regex.Pattern

class AndroidComponentsExtensionTest {
    private lateinit var dslServices: DslServices
    private lateinit var sdkComponents: SdkComponents
    private lateinit var managedDeviceRegistry: ManagedDeviceRegistry
    private lateinit var applicationExtension: ApplicationExtension

    @Before
    fun setUp() {
        val sdkComponentsBuildService = mock<SdkComponentsBuildService>()
        dslServices = createDslServices(sdkComponents = FakeGradleProvider(sdkComponentsBuildService))
        sdkComponents = mock<SdkComponents>()
        managedDeviceRegistry = mock<ManagedDeviceRegistry>()
        applicationExtension = mock<ApplicationExtension>()
    }

    @Test
    fun testStaticPluginVersion() {
        assertThat(AndroidPluginVersion.getCurrent()).isNotNull()
        val extension = mock<ApplicationExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>(extension)

        val androidComponents: ApplicationAndroidComponentsExtension = ApplicationAndroidComponentsExtensionImpl(
            dslServices,
            sdkComponents,
            managedDeviceRegistry,
            variantApiOperationsRegistrar,
            extension
        )
        assertThat(androidComponents.pluginVersion).isEqualTo(AndroidPluginVersion.getCurrent())
    }

    @Test
    fun testPluginVersion() {
        val extension = mock<ApplicationExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>(extension)

        val androidComponents: ApplicationAndroidComponentsExtension = ApplicationAndroidComponentsExtensionImpl(
            dslServices,
            sdkComponents,
            managedDeviceRegistry,
            variantApiOperationsRegistrar,
            extension
        )
        assertThat(androidComponents.pluginVersion).isNotNull()
        assertThat(androidComponents.pluginVersion >= AndroidPluginVersion(4, 2)).isTrue()
    }

    @Test
    fun testSdkComponents() {
        val extension = mock<ApplicationExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>(extension)

        val sdkComponentsFromComponents = ApplicationAndroidComponentsExtensionImpl(
            dslServices,
            sdkComponents,
            managedDeviceRegistry,
            variantApiOperationsRegistrar,
            extension
        ).sdkComponents
        assertThat(sdkComponentsFromComponents).isSameInstanceAs(sdkComponents)
    }

    @Test
    fun testCustomDeviceRegistry() {
        val extension = mock<ApplicationExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>(extension)

        val deviceRegistryFromComponents = ApplicationAndroidComponentsExtensionImpl(
            dslServices,
            sdkComponents,
            managedDeviceRegistry,
            variantApiOperationsRegistrar,
            extension
        ).managedDeviceRegistry
        assertThat(deviceRegistryFromComponents).isSameInstanceAs(managedDeviceRegistry)
    }

    @Test
    fun testApplicationModuleNoSelection() {
        val extension = mock<ApplicationExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>(extension)
        testNoSelection(
                ApplicationAndroidComponentsExtensionImpl(
                        dslServices,
                        sdkComponents,
                        managedDeviceRegistry,
                        variantApiOperationsRegistrar,
                        extension
                ),
                variantApiOperationsRegistrar,
                ApplicationVariantBuilder::class.java)
    }

    @Test
    fun testLibraryModuleNoSelection() {
        val extension = mock<LibraryExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<LibraryExtension, LibraryVariantBuilder, LibraryVariant>(extension)
        testNoSelection(
                LibraryAndroidComponentsExtensionImpl(
                        dslServices,
                        sdkComponents,
                        managedDeviceRegistry,
                        variantApiOperationsRegistrar,
                        extension
                ),
                variantApiOperationsRegistrar,
                LibraryVariantBuilder::class.java)
    }

    @Test
    fun testDynamicFeatureModuleNoSelection() {
        val extension = mock<com.android.build.api.dsl.DynamicFeatureExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<com.android.build.api.dsl.DynamicFeatureExtension, DynamicFeatureVariantBuilder, DynamicFeatureVariant>(extension)
        testNoSelection(
                DynamicFeatureAndroidComponentsExtensionImpl(
                        dslServices,
                        sdkComponents,
                        managedDeviceRegistry,
                        variantApiOperationsRegistrar,
                        extension
                ),
                variantApiOperationsRegistrar,
                DynamicFeatureVariantBuilder::class.java)
    }

    @Test
    fun testTestModuleNoSelection() {
        val extension = mock<TestExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<TestExtension, TestVariantBuilder, TestVariant>(extension)
        testNoSelection(
                TestAndroidComponentsExtensionImpl(
                        dslServices,
                        sdkComponents,
                        managedDeviceRegistry,
                        variantApiOperationsRegistrar,
                        extension
                ),
                variantApiOperationsRegistrar,
                TestVariantBuilder::class.java)
    }

    @Test
    fun testApplicationModuleAllSelection() {
        val extension = mock<ApplicationExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>(extension)
        testAllSelection(
                ApplicationAndroidComponentsExtensionImpl(
                        dslServices,
                        sdkComponents,
                        managedDeviceRegistry,
                        variantApiOperationsRegistrar,
                        extension
                ),
                variantApiOperationsRegistrar,
                ApplicationVariantBuilder::class.java)
    }

    @Test
    fun testLibraryModuleAllSelection() {
        val extension = mock<LibraryExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<LibraryExtension, LibraryVariantBuilder, LibraryVariant>(extension)
        testAllSelection(
                LibraryAndroidComponentsExtensionImpl(
                        dslServices,
                        sdkComponents,
                        managedDeviceRegistry,
                        variantApiOperationsRegistrar,
                        extension
                ),
                variantApiOperationsRegistrar,
                LibraryVariantBuilder::class.java)
    }

    @Test
    fun testDynamicFeatureModuleAllSelection() {
        val extension = mock<com.android.build.api.dsl.DynamicFeatureExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<com.android.build.api.dsl.DynamicFeatureExtension, DynamicFeatureVariantBuilder, DynamicFeatureVariant>(extension)
        testAllSelection(
                DynamicFeatureAndroidComponentsExtensionImpl(
                        dslServices,
                        sdkComponents,
                        managedDeviceRegistry,
                        variantApiOperationsRegistrar,
                        extension
                ),
                variantApiOperationsRegistrar,
                DynamicFeatureVariantBuilder::class.java)
    }

    @Test
    fun testTestModuleAllSelection() {
        val extension = mock<TestExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<TestExtension, TestVariantBuilder, TestVariant>(extension)
        testAllSelection(
                TestAndroidComponentsExtensionImpl(
                        dslServices,
                        sdkComponents,
                        managedDeviceRegistry,
                        variantApiOperationsRegistrar,
                        extension,
                ),
                variantApiOperationsRegistrar,
                TestVariantBuilder::class.java)
    }


    @Test
    fun testBeforeVariants() {
        val extension = mock<ApplicationExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>(extension)
        val appExtension = ApplicationAndroidComponentsExtensionImpl(dslServices,
            mock<SdkComponents>(),
            mock<ManagedDeviceRegistry>(),
            variantApiOperationsRegistrar,
            extension
        ) as ApplicationAndroidComponentsExtension
        val fooVariant = appExtension.selector().withName(Pattern.compile("foo"))
        appExtension.beforeVariants {
                    it.minSdk = 23
        }

        appExtension.beforeVariants(fooVariant) {
                    it.enable = false
        }
    }

    @Test
    fun testOnVariantsProperties() {
        val extension = mock<ApplicationExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>(extension)
        val appExtension = ApplicationAndroidComponentsExtensionImpl(dslServices,
            mock<SdkComponents>(),
            mock<ManagedDeviceRegistry>(),
            variantApiOperationsRegistrar,
            extension
        )
        val fooVariant = appExtension.selector().withName(Pattern.compile("foo"))

        appExtension.onVariants(fooVariant, Action { it.artifacts.get(SingleArtifact.APK) })
        val f1Variant = appExtension.selector().withFlavor("f1" to "dim1")
        appExtension.onVariants(f1Variant, Action { it.artifacts.get(SingleArtifact.APK) })

        val debugF1Variant = appExtension.selector()
                .withBuildType("debug")
                .withFlavor("f1" to "dim1")
        appExtension.onVariants(debugF1Variant) {
                    it.artifacts.get(SingleArtifact.APK)
        }
    }

    interface DslExtensionType
    interface VariantExtensionType: VariantExtension
    interface ProjectDslExtensionType

    @Test
    fun testRegisterProjectExtension() {

        abstract class ExtensionAwareApplicationExtension: ApplicationExtension, ExtensionAware

        val extension = mock<ExtensionAwareApplicationExtension>()
        val extensionContainer= mock<ExtensionContainer>()
        whenever(extension.extensions).thenReturn(extensionContainer)

        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>(extension)
        val appExtension = ApplicationAndroidComponentsExtensionImpl(dslServices,
            mock<SdkComponents>(),
            mock<ManagedDeviceRegistry>(),
            variantApiOperationsRegistrar,
            extension
        )
        appExtension.registerExtension(
            DslExtension.Builder("extension")
                .extendProjectWith(ProjectDslExtensionType::class.java)
                .build()) {
            object: VariantExtensionType {}
        }
        verify(extensionContainer).add("extension", ProjectDslExtensionType::class.java)
        assertThat(variantApiOperationsRegistrar.dslExtensions).hasSize(1)
        assertThat(variantApiOperationsRegistrar.dslExtensions[0].dslExtensionTypes.dslName)
            .isEqualTo("extension")
        assertThat(variantApiOperationsRegistrar.dslExtensions[0].dslExtensionTypes.projectExtensionType)
            .isEqualTo(ProjectDslExtensionType::class.java)
    }

    @Test
    fun testRegisterBuildTypeExtension() {
        val extension = mock<ApplicationExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>(extension)
        val appExtension = ApplicationAndroidComponentsExtensionImpl(dslServices,
            mock<SdkComponents>(),
            mock<ManagedDeviceRegistry>(),
            variantApiOperationsRegistrar,
            extension
        )
        val extensionContainer = createExtensionAwareBuildType(extension)
        appExtension.registerExtension(
            DslExtension.Builder("extension")
                .extendBuildTypeWith(DslExtensionType::class.java)
                .build()) {
                    object: VariantExtensionType {}
        }
        verify(extensionContainer).add("extension", DslExtensionType::class.java)
        assertThat(variantApiOperationsRegistrar.dslExtensions).hasSize(1)
        assertThat(variantApiOperationsRegistrar.dslExtensions[0].dslExtensionTypes.dslName)
            .isEqualTo("extension")
        assertThat(variantApiOperationsRegistrar.dslExtensions[0].dslExtensionTypes.buildTypeExtensionType)
            .isEqualTo(DslExtensionType::class.java)
    }

    @Test
    fun testRegisterProductFlavorExtension() {

        val extension = mock<ApplicationExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>(extension)
        val appExtension = ApplicationAndroidComponentsExtensionImpl(dslServices,
            mock<SdkComponents>(),
            mock<ManagedDeviceRegistry>(),
            variantApiOperationsRegistrar,
            extension
        )
        val extensionContainer = createExtensionAwareProductFlavor(extension)

        appExtension.registerExtension(
            DslExtension.Builder("extension")
                .extendProductFlavorWith(DslExtensionType::class.java)
                .build()) {
            object: VariantExtensionType {}
        }
        verify(extensionContainer).add("extension", DslExtensionType::class.java)
        assertThat(variantApiOperationsRegistrar.dslExtensions).hasSize(1)
        assertThat(variantApiOperationsRegistrar.dslExtensions[0].dslExtensionTypes.dslName)
            .isEqualTo("extension")
        assertThat(variantApiOperationsRegistrar.dslExtensions[0].dslExtensionTypes.productFlavorExtensionType)
            .isEqualTo(DslExtensionType::class.java)
    }

    @Test
    fun testRegisterMultipleExtension() {

        val extension = mock<ApplicationExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>(extension)
        val appExtension = ApplicationAndroidComponentsExtensionImpl(dslServices,
            mock<SdkComponents>(),
            mock<ManagedDeviceRegistry>(),
            variantApiOperationsRegistrar,
            extension
        )

        val buildTypeExtensionContainer = createExtensionAwareBuildType(extension)
        val productFlavorExtensionContainer = createExtensionAwareProductFlavor(extension)

        appExtension.registerExtension(
            DslExtension.Builder("extension")
                .extendBuildTypeWith(DslExtensionType::class.java)
                .extendProductFlavorWith(DslExtensionType::class.java)
                .build()) {
            object: VariantExtensionType {}
        }
        verify(buildTypeExtensionContainer).add("extension", DslExtensionType::class.java)
        verify(productFlavorExtensionContainer).add("extension", DslExtensionType::class.java)
        assertThat(variantApiOperationsRegistrar.dslExtensions).hasSize(1)
        assertThat(variantApiOperationsRegistrar.dslExtensions[0].dslExtensionTypes.dslName)
            .isEqualTo("extension")
        assertThat(variantApiOperationsRegistrar.dslExtensions[0].dslExtensionTypes.buildTypeExtensionType)
            .isEqualTo(DslExtensionType::class.java)
        assertThat(variantApiOperationsRegistrar.dslExtensions[0].dslExtensionTypes.productFlavorExtensionType)
            .isEqualTo(DslExtensionType::class.java)
    }

    @Test
    fun testMultipleRegisterExtension() {

        val extension = mock<ApplicationExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>(extension)
        val appExtension = ApplicationAndroidComponentsExtensionImpl(dslServices,
            mock<SdkComponents>(),
            mock<ManagedDeviceRegistry>(),
            variantApiOperationsRegistrar,
            extension
        )

        val buildTypeExtensionContainer = createExtensionAwareBuildType(extension)
        val productFlavorExtensionContainer = createExtensionAwareProductFlavor(extension)

        appExtension.registerExtension(
            DslExtension.Builder("buildTypeExtension")
                .extendBuildTypeWith(DslExtensionType::class.java)
                .build()) {
            object: VariantExtensionType {}
        }
        appExtension.registerExtension(
            DslExtension.Builder("productFlavorExtension")
                .extendProductFlavorWith(DslExtensionType::class.java)
                .build()) {
            object: VariantExtensionType {}
        }
        verify(buildTypeExtensionContainer).add("buildTypeExtension", DslExtensionType::class.java)
        verify(productFlavorExtensionContainer).add("productFlavorExtension", DslExtensionType::class.java)
        assertThat(variantApiOperationsRegistrar.dslExtensions).hasSize(2)
        assertThat(variantApiOperationsRegistrar.dslExtensions[0].dslExtensionTypes.dslName)
            .isEqualTo("buildTypeExtension")
        assertThat(variantApiOperationsRegistrar.dslExtensions[0].dslExtensionTypes.buildTypeExtensionType)
            .isEqualTo(DslExtensionType::class.java)
        assertThat(variantApiOperationsRegistrar.dslExtensions[1].dslExtensionTypes.dslName)
            .isEqualTo("productFlavorExtension")
        assertThat(variantApiOperationsRegistrar.dslExtensions[1].dslExtensionTypes.productFlavorExtensionType)
            .isEqualTo(DslExtensionType::class.java)
    }

    @Test
    fun testApplicationDslFinalizationBlock() {
        val extension = mock<ApplicationExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>(extension)
        val appExtension = ApplicationAndroidComponentsExtensionImpl(dslServices,
                mock<SdkComponents>(),
                mock<ManagedDeviceRegistry>(),
                variantApiOperationsRegistrar,
                extension
        )

        var called = false
        appExtension.finalizeDsl {
            assertThat(it).isEqualTo(extension)
            called = true
        }

        variantApiOperationsRegistrar.executeDslFinalizationBlocks()
        assertThat(called).isTrue()
    }

    @Test
    fun testLibraryDslFinalizationBlock() {
        val extension = mock<LibraryExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<LibraryExtension, LibraryVariantBuilder, LibraryVariant>(extension)
        val libraryExtension = LibraryAndroidComponentsExtensionImpl(dslServices,
                mock<SdkComponents>(),
                mock<ManagedDeviceRegistry>(),
                variantApiOperationsRegistrar,
                extension
        )

        var called = false
        libraryExtension.finalizeDsl {
            assertThat(it).isEqualTo(extension)
            called = true
        }

        variantApiOperationsRegistrar.executeDslFinalizationBlocks()
        assertThat(called).isTrue()
    }

    @Test
    fun testDynamicFeatureDslFinalizationBlock() {
        val extension = mock<com.android.build.api.dsl.DynamicFeatureExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<com.android.build.api.dsl.DynamicFeatureExtension, DynamicFeatureVariantBuilder, DynamicFeatureVariant>(extension)
        val dynamicFeatureExtension = DynamicFeatureAndroidComponentsExtensionImpl(dslServices,
                mock<SdkComponents>(),
                mock<ManagedDeviceRegistry>(),
                variantApiOperationsRegistrar,
                extension
        )

        var called = false
        dynamicFeatureExtension.finalizeDsl {
            assertThat(it).isEqualTo(extension)
            called = true
        }

        variantApiOperationsRegistrar.executeDslFinalizationBlocks()
        assertThat(called).isTrue()
    }

    @Test
    fun testTestDslFinalizationBlock() {
        val extension = mock<TestExtension>()
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<TestExtension, TestVariantBuilder, TestVariant>(extension)
        val testExtension = TestAndroidComponentsExtensionImpl(dslServices,
                mock<SdkComponents>(),
                mock<ManagedDeviceRegistry>(),
                variantApiOperationsRegistrar,
                extension
        )

        var called = false
        testExtension.finalizeDsl {
            assertThat(it).isEqualTo(extension)
            called = true
        }

        variantApiOperationsRegistrar.executeDslFinalizationBlocks()
        assertThat(called).isTrue()
    }

    private fun createExtensionAwareBuildType(extension: ApplicationExtension): ExtensionContainer {
        @Suppress("UNCHECKED_CAST")
        val buildTypesContainer = mock<NamedDomainObjectContainer<ApplicationBuildType>>()
        whenever(extension.buildTypes).thenReturn(buildTypesContainer)
        val buildTypeExtensionContainer= mock<ExtensionContainer>()
        val buildType = mock<ApplicationBuildType>()
        whenever(buildType.extensions).thenReturn(buildTypeExtensionContainer)
        val buildTypes = listOf<ApplicationBuildType>(buildType)
        val argument = argumentCaptor<Action<in ApplicationBuildType>>()

        whenever(buildTypesContainer.configureEach(argument.capture())).thenAnswer {invocation ->
            buildTypes.forEach {
                invocation.getArgument<Action<in ApplicationBuildType>>(0).execute(it)
            }
        }
        return buildTypeExtensionContainer
    }

    private fun createExtensionAwareProductFlavor(extension: ApplicationExtension): ExtensionContainer {
        @Suppress("UNCHECKED_CAST")
        val productFlavorContainer = mock<NamedDomainObjectContainer<ApplicationProductFlavor>>()
        whenever(extension.productFlavors).thenReturn(productFlavorContainer)
        val extensionContainer= mock<ExtensionContainer>()
        val productFlavor = mock<ApplicationProductFlavor>()
        whenever(productFlavor.extensions).thenReturn(extensionContainer)
        val productFlavors = listOf<ApplicationProductFlavor>(productFlavor)
        whenever(productFlavorContainer.iterator()).thenAnswer { productFlavors.iterator() }
        val argument = argumentCaptor<Action<in ApplicationProductFlavor>>()

        whenever(productFlavorContainer.configureEach(argument.capture())).thenAnswer { invocation ->
            productFlavors.forEach {
                invocation.getArgument<Action<in ApplicationProductFlavor>>(0).execute(it)
            }
        }
        return extensionContainer
    }

    private inline fun <DslExtensionT: CommonExtension<*, *, *, *, *, *>, reified VariantBuilderT: VariantBuilder, VariantT: Variant> testAllSelection(
            extension: AndroidComponentsExtensionImpl<DslExtensionT, VariantBuilderT, VariantT>,
            operationsRegistrar: VariantApiOperationsRegistrar<DslExtensionT, VariantBuilderT, VariantT>,
            variantType: Class<VariantBuilderT>) {
        val visitedVariants = mutableListOf<VariantBuilderT>()
        extension.beforeVariants(extension.selector().all()) {
            visitedVariants.add(it)
        }
        @Suppress("UNCHECKED_CAST")
        operationsRegistrar.variantBuilderOperations.executeOperations(mock<VariantBuilderT>())
        assertThat(visitedVariants).hasSize(1)
    }

    private inline fun <DslExtensionT: CommonExtension<*, *, *, *, *, *>, reified VariantBuilderT: VariantBuilder, VariantT: Variant> testNoSelection(
            extension: AndroidComponentsExtension<DslExtensionT, VariantBuilderT, VariantT>,
            operationsRegistrar: VariantApiOperationsRegistrar<DslExtensionT, VariantBuilderT, VariantT>,
            variantType: Class<VariantBuilderT>) {
        val visitedVariants = mutableListOf<VariantBuilderT>()
        extension.beforeVariants { variant -> visitedVariants.add(variant)}
        operationsRegistrar.variantBuilderOperations.executeOperations(mock<VariantBuilderT>())
        assertThat(visitedVariants).hasSize(1)
    }
}
