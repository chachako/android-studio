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

package com.android.build.gradle.internal.ide.v2

import com.android.AndroidProjectTypes
import com.android.build.api.component.impl.TestFixturesImpl
import com.android.build.api.dsl.ApplicationAndroidResources
import com.android.build.api.dsl.ApplicationBuildFeatures
import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ApplicationInstallation
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.variant.impl.ApplicationVariantImpl
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.component.DeviceTestCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.dsl.ApplicationBuildFeaturesImpl
import com.android.build.gradle.internal.dsl.ApplicationExtensionImpl
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeLogger
import com.android.build.gradle.internal.fixtures.ProjectFactory
import com.android.build.gradle.internal.scope.BuildFeatureValuesImpl
import com.android.build.gradle.internal.scope.DelayedActionsExecutor
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.createDslServices
import com.android.build.gradle.internal.services.createProjectServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfigImpl
import com.android.build.gradle.internal.variant.LegacyVariantInputManager
import com.android.build.gradle.internal.variant.VariantInputModelBuilder
import com.android.build.gradle.internal.variant.VariantModel
import com.android.build.gradle.internal.variant.VariantModelImpl
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.SyncOptions
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.model.v2.ide.ProjectType
import com.google.common.truth.Truth
import org.gradle.api.Project
import org.gradle.api.plugins.PluginManager
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SourceSetBuilderTest {

    private val project: Project = ProjectFactory.project
    private val projectServices: ProjectServices = createProjectServices(
        issueReporter = SyncIssueReporterImpl(
            SyncOptions.EvaluationMode.IDE,
            SyncOptions.ErrorFormatMode.HUMAN_READABLE,
            FakeLogger()
        )
    )
    private val testComponentList: MutableList<TestComponentCreationConfig> = mutableListOf()
    private val variantsList: MutableList<VariantCreationConfig> = mutableListOf()

    private val dslServices = createDslServices(
        projectServices = projectServices,
        sdkComponents = FakeGradleProvider(mock<SdkComponentsBuildService>())
    )

    @Before
    fun setUp() {
        testComponentList.clear()
        variantsList.clear()
    }

    @Test
    fun testDefaultContainerBuilderInternalData() {
        val variantModel = createVariantModel()
        val builder =
            createApplicationModelBuilder(variantModel)
        val defaultContainerBuilder = builder.DefaultSourceSetContainerBuilder()

        Truth.assertThat(defaultContainerBuilder.defaultSourceSet).isEqualTo(variantModel.inputs.defaultConfigData.sourceSet)
        Truth.assertThat(defaultContainerBuilder.variantData).isEqualTo(variantModel.inputs.defaultConfigData)
    }

    @Test
    fun testBuildTypeContainerBuilderInternalData() {
        val variantModel = createVariantModel()
        val builder =
            createApplicationModelBuilder(variantModel)
        val debug = variantModel.inputs.buildTypes.getValue("debug")
        Truth.assertThat(debug).isNotNull()
        val buildTypeContainerBuilder = builder.BuildTypeSourceSetBuilder(debug)

        Truth.assertThat(buildTypeContainerBuilder.defaultSourceSet).isEqualTo(debug.sourceSet)
        Truth.assertThat(buildTypeContainerBuilder.variantData).isEqualTo(debug)
    }

    @Test
    fun testFlavorContainerBuilderInternalData() {
        val variantModel = createVariantModel()
        val builder =
            createApplicationModelBuilder(variantModel)

        val flavor = variantModel.inputs.productFlavors.getValue("flavor")
        Truth.assertThat(flavor).isNotNull()
        val flavorContainerBuilder = builder.FlavorSourceSetContainerBuilder(flavor)

        Truth.assertThat(flavorContainerBuilder.defaultSourceSet).isEqualTo(flavor.sourceSet)
        Truth.assertThat(flavorContainerBuilder.variantData).isEqualTo(flavor)
    }

    @Test
    fun testShouldTakeOnDefault() {
        val buildType = "debug"
        testComponentList.addAll(
            listOf(
                createAndroidTestComponent(buildType),
                createUnitTestComponent(buildType),
                createScreenshotTestComponent(buildType)
            )
        )
        variantsList.add(createFixturesVariant(buildType))

        val variantModel = createVariantModel()
        val builder =
            createApplicationModelBuilder(variantModel)
        val defaultContainerBuilder = builder.DefaultSourceSetContainerBuilder()

        Truth.assertThat(defaultContainerBuilder.shouldTakeAndroidTestSourceSet()).isTrue()
        Truth.assertThat(defaultContainerBuilder.shouldTakeUnitSourceSet()).isTrue()
        Truth.assertThat(defaultContainerBuilder.shouldTakeFixtureSourceSet()).isTrue()
        Truth.assertThat(defaultContainerBuilder.shouldTakeScreenshotSourceSet()).isTrue()
    }

    @Test
    fun testShouldTakeOnDefaultNegative() {
        val variantModel = createVariantModel()
        val builder =
            createApplicationModelBuilder(variantModel)
        val defaultContainerBuilder = builder.DefaultSourceSetContainerBuilder()

        Truth.assertThat(defaultContainerBuilder.shouldTakeAndroidTestSourceSet()).isFalse()
        Truth.assertThat(defaultContainerBuilder.shouldTakeUnitSourceSet()).isFalse()
        Truth.assertThat(defaultContainerBuilder.shouldTakeFixtureSourceSet()).isFalse()
        Truth.assertThat(defaultContainerBuilder.shouldTakeScreenshotSourceSet()).isFalse()
    }

    @Test
    fun testShouldTakeOnBuildType() {
        val buildType = "debug"
        testComponentList.addAll(
            listOf(
                createAndroidTestComponent(buildType),
                createUnitTestComponent(buildType),
                createScreenshotTestComponent(buildType)
            )
        )
        variantsList.add(createFixturesVariant(buildType))

        val variantModel = createVariantModel()
        val builder =
            createApplicationModelBuilder(variantModel)

        val debug = variantModel.inputs.buildTypes.getValue("debug")
        Truth.assertThat(debug).isNotNull()
        val buildTypeContainerBuilder = builder.BuildTypeSourceSetBuilder(debug)
        Truth.assertThat(buildTypeContainerBuilder.shouldTakeAndroidTestSourceSet()).isTrue()
        Truth.assertThat(buildTypeContainerBuilder.shouldTakeUnitSourceSet()).isTrue()
        Truth.assertThat(buildTypeContainerBuilder.shouldTakeFixtureSourceSet()).isTrue()
        Truth.assertThat(buildTypeContainerBuilder.shouldTakeScreenshotSourceSet()).isTrue()
    }

    @Test
    fun testShouldTakeOnBuildTypeNegative() {
        val variantModel = createVariantModel()
        val builder =
            createApplicationModelBuilder(variantModel)

        val debug = variantModel.inputs.buildTypes.getValue("debug")
        Truth.assertThat(debug).isNotNull()
        val buildTypeContainerBuilder = builder.BuildTypeSourceSetBuilder(debug)
        Truth.assertThat(buildTypeContainerBuilder.shouldTakeAndroidTestSourceSet()).isFalse()
        Truth.assertThat(buildTypeContainerBuilder.shouldTakeUnitSourceSet()).isFalse()
        Truth.assertThat(buildTypeContainerBuilder.shouldTakeFixtureSourceSet()).isFalse()
        Truth.assertThat(buildTypeContainerBuilder.shouldTakeScreenshotSourceSet()).isFalse()
    }

    @Test
    fun testShouldTakeOnFlavors() {
        val buildType = "debug"
        val flavors = listOf("one" to "flavor")
        testComponentList.addAll(
            listOf(
                createAndroidTestComponent(buildType, flavors),
                createUnitTestComponent(buildType, flavors),
                createScreenshotTestComponent(buildType, flavors)
            )
        )
        variantsList.add(createFixturesVariant(buildType, flavors))

        val variantModel = createVariantModel()
        val builder =
            createApplicationModelBuilder(variantModel)

        val flavor = variantModel.inputs.productFlavors.getValue("flavor")
        Truth.assertThat(flavor).isNotNull()
        val flavorContainerBuilder = builder.FlavorSourceSetContainerBuilder(flavor)
        Truth.assertThat(flavorContainerBuilder.shouldTakeAndroidTestSourceSet()).isTrue()
        Truth.assertThat(flavorContainerBuilder.shouldTakeUnitSourceSet()).isTrue()
        Truth.assertThat(flavorContainerBuilder.shouldTakeFixtureSourceSet()).isTrue()
        Truth.assertThat(flavorContainerBuilder.shouldTakeScreenshotSourceSet()).isTrue()
    }

    @Test
    fun testShouldTakeOnFlavorsNegative() {
        val variantModel = createVariantModel()
        val builder =
            createApplicationModelBuilder(variantModel)

        val flavor = variantModel.inputs.productFlavors.getValue("flavor")
        Truth.assertThat(flavor).isNotNull()
        val flavorContainerBuilder = builder.FlavorSourceSetContainerBuilder(flavor)
        Truth.assertThat(flavorContainerBuilder.shouldTakeAndroidTestSourceSet()).isFalse()
        Truth.assertThat(flavorContainerBuilder.shouldTakeUnitSourceSet()).isFalse()
        Truth.assertThat(flavorContainerBuilder.shouldTakeFixtureSourceSet()).isFalse()
        Truth.assertThat(flavorContainerBuilder.shouldTakeScreenshotSourceSet()).isFalse()
    }

    private fun createAndroidTestComponent(
        buildType: String,
        flavors: List<Pair<String, String>> = listOf()
    ): DeviceTestCreationConfig {
        val testComponentConfig = mock<DeviceTestCreationConfig>()
        whenever(testComponentConfig.buildType).thenReturn(buildType)
        whenever(testComponentConfig.productFlavors).thenReturn(flavors)
        return testComponentConfig
    }

    private fun createUnitTestComponent(
        buildType: String,
        flavors: List<Pair<String, String>> = listOf()
    ): TestComponentCreationConfig {
        val testComponentConfig = mock<TestComponentCreationConfig>()
        whenever(testComponentConfig.componentType).thenReturn(ComponentTypeImpl.UNIT_TEST)
        whenever(testComponentConfig.buildType).thenReturn(buildType)
        whenever(testComponentConfig.productFlavors).thenReturn(flavors)
        return testComponentConfig
    }

    private fun createScreenshotTestComponent(
        buildType: String,
        flavors: List<Pair<String, String>> = listOf()
    ): TestComponentCreationConfig {
        val testComponentConfig = mock<TestComponentCreationConfig>()
        whenever(testComponentConfig.componentType).thenReturn(ComponentTypeImpl.SCREENSHOT_TEST)
        whenever(testComponentConfig.buildType).thenReturn(buildType)
        whenever(testComponentConfig.productFlavors).thenReturn(flavors)
        return testComponentConfig
    }

    private fun createFixturesVariant(
        buildType: String,
        flavors: List<Pair<String, String>> = listOf()
    ): VariantCreationConfig {
        val variant = mock<ApplicationVariantImpl>()
        val testFixtures = mock<TestFixturesImpl>()
        whenever(variant.testFixtures).thenReturn(testFixtures)
        whenever(testFixtures.buildType).thenReturn(buildType)
        whenever(testFixtures.productFlavors).thenReturn(flavors)
        return variant
    }

    private fun createApplicationModelBuilder(variantModel: VariantModel):
            ModelBuilder<
                    ApplicationBuildFeatures,
                    ApplicationBuildType,
                    ApplicationDefaultConfig,
                    ApplicationProductFlavor,
                    ApplicationAndroidResources,
                    ApplicationInstallation,
                    ApplicationExtension> {

        // for now create an app extension

        AndroidLocationsBuildService.RegistrationAction(project).execute()

        val variantInputModel = LegacyVariantInputManager(
            dslServices,
            ComponentTypeImpl.BASE_APK,
            SourceSetManager(
                ProjectFactory.project,
                false,
                dslServices,
                DelayedActionsExecutor()
            )
        )

        val extension = dslServices.newDecoratedInstance(
            ApplicationExtensionImpl::class.java,
            dslServices,
            variantInputModel
        )

        // make sure the global issue reporter is registered
        SyncIssueReporterImpl.GlobalSyncIssueService.RegistrationAction(
            project, SyncOptions.EvaluationMode.IDE, SyncOptions.ErrorFormatMode.MACHINE_PARSABLE
        ).execute()

        return ModelBuilder(project, variantModel, extension)
    }

    private fun createVariantModel() : VariantModel {
        val globalConfig = mock<GlobalTaskCreationConfigImpl>()
        whenever(globalConfig.services).thenReturn(dslServices)
        val projectServices = mock<ProjectServices>()
        val projectOptions = mock<ProjectOptions>()
        val pluginManager = mock<PluginManager>()

        whenever(projectServices.plugins).thenReturn(pluginManager)
        whenever(projectServices.projectOptions).thenReturn(projectOptions)
        whenever(pluginManager.hasPlugin(any())).thenReturn(false)
        whenever(projectOptions.get(any<BooleanOption>())).thenReturn(false)

        val inputBuilder = VariantInputModelBuilder(ComponentTypeImpl.BASE_APK, dslServices)
        inputBuilder.buildTypes { create("debug") }
        inputBuilder.productFlavors {
            create("flavor") {
                dimension = "one"
            }
        }

        return VariantModelImpl(
            inputBuilder.toModel(),
            { "debug" },
            { variantsList },
            { testComponentList },
            {
                BuildFeatureValuesImpl(
                    dslServices.newInstance(ApplicationBuildFeaturesImpl::class.java),
                    projectServices
                )
            },
            AndroidProjectTypes.PROJECT_TYPE_APP,
            ProjectType.APPLICATION,
            globalConfig
        )
    }
}
