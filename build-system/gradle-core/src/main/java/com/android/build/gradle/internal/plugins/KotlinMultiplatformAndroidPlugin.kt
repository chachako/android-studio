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

package com.android.build.gradle.internal.plugins

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.attributes.AgpVersionAttr
import com.android.build.api.attributes.BuildTypeAttr
import com.android.build.api.attributes.ProductFlavorAttr
import com.android.build.api.component.analytics.AnalyticsEnabledKotlinMultiplatformAndroidVariant
import com.android.build.api.component.impl.KmpAndroidTestImpl
import com.android.build.api.component.impl.KmpUnitTestImpl
import com.android.build.api.dsl.SettingsExtension
import com.android.build.api.variant.impl.KmpPredefinedAndroidCompilation
import com.android.build.api.variant.impl.KmpVariantImpl
import com.android.build.api.variant.impl.KotlinMultiplatformAndroidCompilation
import com.android.build.api.variant.impl.KotlinMultiplatformAndroidCompilationImpl
import com.android.build.api.variant.impl.KotlinMultiplatformAndroidTarget
import com.android.build.api.variant.impl.KotlinMultiplatformAndroidTargetImpl
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.DependencyConfigurator
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.VariantManager.Companion.finalizeAllComponents
import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.core.dsl.KmpComponentDslInfo
import com.android.build.gradle.internal.core.dsl.impl.KmpAndroidTestDslInfoImpl
import com.android.build.gradle.internal.core.dsl.impl.KmpUnitTestDslInfoImpl
import com.android.build.gradle.internal.core.dsl.impl.KmpVariantDslInfoImpl
import com.android.build.gradle.internal.dependency.AgpVersionCompatibilityRule
import com.android.build.gradle.internal.dependency.JacocoInstrumentationService
import com.android.build.gradle.internal.dependency.ModelArtifactCompatibilityRule.Companion.setUp
import com.android.build.gradle.internal.dependency.SingleVariantBuildTypeRule
import com.android.build.gradle.internal.dependency.SingleVariantProductFlavorRule
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtension
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtensionImpl
import com.android.build.gradle.internal.dsl.decorator.androidPluginDslDecorator
import com.android.build.gradle.internal.ide.dependencies.LibraryDependencyCacheBuildService
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService
import com.android.build.gradle.internal.ide.kmp.KotlinAndroidSourceSetMarker
import com.android.build.gradle.internal.ide.kmp.KotlinAndroidSourceSetMarker.Companion.android
import com.android.build.gradle.internal.ide.kmp.KotlinIdeImportConfigurator
import com.android.build.gradle.internal.ide.kmp.KotlinModelBuildingConfigurator
import com.android.build.gradle.internal.ide.v2.GlobalSyncService
import com.android.build.gradle.internal.lint.LintFixBuildService
import com.android.build.gradle.internal.manifest.LazyManifestParser
import com.android.build.gradle.internal.scope.KotlinMultiplatformBuildFeaturesValuesImpl
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.Aapt2ThreadPoolBuildService
import com.android.build.gradle.internal.services.ClassesHierarchyBuildService
import com.android.build.gradle.internal.services.DslServicesImpl
import com.android.build.gradle.internal.services.FakeDependencyJarBuildService
import com.android.build.gradle.internal.services.LintClassLoaderBuildService
import com.android.build.gradle.internal.services.StringCachingBuildService
import com.android.build.gradle.internal.services.SymbolTableBuildService
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.TaskCreationServicesImpl
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.services.VariantServicesImpl
import com.android.build.gradle.internal.services.VersionedSdkLoaderService
import com.android.build.gradle.internal.tasks.KmpTaskManager
import com.android.build.gradle.internal.tasks.SigningConfigUtils.Companion.createSigningOverride
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfigImpl
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.KmpGlobalTaskCreationConfigImpl
import com.android.build.gradle.internal.utils.KOTLIN_MPP_PLUGIN_ID
import com.android.build.gradle.internal.utils.validatePreviewTargetValue
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.ComponentTypeImpl
import com.android.repository.Revision
import com.android.utils.FileUtils
import com.android.utils.appendCapitalized
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.gradle.build.event.BuildEventsListenerRegistry
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.Companion.COMMON_MAIN_SOURCE_SET_NAME
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.Companion.COMMON_TEST_SOURCE_SET_NAME
import org.jetbrains.kotlin.gradle.plugin.mpp.external.ExternalKotlinTargetDescriptor
import org.jetbrains.kotlin.gradle.plugin.mpp.external.createExternalKotlinTarget
import javax.inject.Inject

@OptIn(ExternalKotlinTargetApi::class)
abstract class KotlinMultiplatformAndroidPlugin @Inject constructor(
    listenerRegistry: BuildEventsListenerRegistry
): AndroidPluginBaseServices(listenerRegistry), Plugin<Project> {

    private lateinit var global: GlobalTaskCreationConfig

    private lateinit var kotlinExtension: KotlinMultiplatformExtension
    private lateinit var androidExtension: KotlinMultiplatformAndroidExtensionImpl
    private lateinit var androidTarget: KotlinMultiplatformAndroidTargetImpl

    private lateinit var mainVariant: KmpVariantImpl

    private val sourceSetToCreationConfigMap = mutableMapOf<KotlinSourceSet, KmpComponentCreationConfig>()
    private val extraSourceSetsToIncludeInResolution = mutableSetOf<KotlinSourceSet>()

    private val dslServices by lazy {
        withProject("dslServices") { project ->
            val sdkComponentsBuildService: Provider<SdkComponentsBuildService> =
                SdkComponentsBuildService.RegistrationAction(
                    project,
                    projectServices.projectOptions
                ).execute()

            DslServicesImpl(
                projectServices,
                sdkComponentsBuildService
            )
        }
    }

    override fun getAnalyticsPluginType(): GradleBuildProject.PluginType? =
        GradleBuildProject.PluginType.KOTLIN_MULTIPLATFORM_ANDROID_LIBRARY

    override fun configureProject(project: Project) { }

    override fun createTasks(project: Project) { }

    override fun apply(project: Project) {
        super.basePluginApply(project)

        FakeDependencyJarBuildService.RegistrationAction(project).execute()
        Aapt2ThreadPoolBuildService.RegistrationAction(project, projectServices.projectOptions).execute()
        Aapt2DaemonBuildService.RegistrationAction(project, projectServices.projectOptions).execute()
        ClassesHierarchyBuildService.RegistrationAction(project).execute()
        JacocoInstrumentationService.RegistrationAction(project).execute()
        SymbolTableBuildService.RegistrationAction(project).execute()

        val stringCachingService: Provider<StringCachingBuildService> =
            StringCachingBuildService.RegistrationAction(project).execute()
        val mavenCoordinatesCacheBuildService =
            MavenCoordinatesCacheBuildService.RegistrationAction(project, stringCachingService)
                .execute()
        LibraryDependencyCacheBuildService.RegistrationAction(
            project, mavenCoordinatesCacheBuildService
        ).execute()
        GlobalSyncService.RegistrationAction(project, mavenCoordinatesCacheBuildService).execute()

        LintClassLoaderBuildService.RegistrationAction(project).execute()
        LintFixBuildService.RegistrationAction(project).execute()

        val versionedSdkLoaderService: VersionedSdkLoaderService by lazy {
            withProject("versionedSdkLoaderService") { project ->
                VersionedSdkLoaderService(
                    dslServices,
                    project,
                    ::getCompileSdkVersion,
                    ::getBuildToolsVersion
                )
            }
        }

        val bootClasspathConfig = BootClasspathConfigImpl(
            project,
            projectServices,
            versionedSdkLoaderService,
            libraryRequests = emptyList(),
            isJava8Compatible = { true },
            returnDefaultValuesForMockableJar = { false },
            forUnitTest = false
        )

        global = KmpGlobalTaskCreationConfigImpl(
            project,
            androidExtension,
            versionedSdkLoaderService,
            bootClasspathConfig,
            ::getCompileSdkVersion,
            ::getBuildToolsVersion,
            BasePlugin.createAndroidJarConfig(project),
            dslServices,
            createSettingsOptions(dslServices)
        )

        TaskManager.createTasksBeforeEvaluate(
            project,
            ComponentTypeImpl.KMP_ANDROID,
            emptySet(),
            global
        )

        // enable the gradle property that enables the kgp IDE import APIs that we rely on.
        project.extensions.extraProperties.set(
            "kotlin.mpp.import.enableKgpDependencyResolution", "true"
        )

        project.afterEvaluate {
            if (!project.plugins.hasPlugin(KOTLIN_MPP_PLUGIN_ID)) {
                throw RuntimeException("Kotlin multiplatform plugin was not found. This plugin needs" +
                        " to be applied as part of the kotlin multiplatform plugin.")
            }
            afterEvaluate(it)
        }
    }

    override fun configureExtension(project: Project) {
        val extensionImplClass = androidPluginDslDecorator
            .decorate(KotlinMultiplatformAndroidExtensionImpl::class.java)
        androidExtension = dslServices.newInstance(
            extensionImplClass,
            dslServices,
            { jvmConfiguration: KotlinMultiplatformAndroidExtensionImpl.KotlinMultiplatformAndroidTestConfigurationImpl ->
                if (project.pluginManager.hasPlugin(KOTLIN_MPP_PLUGIN_ID)) {
                    createCompilation(
                        compilationName = jvmConfiguration.compilationName,
                        defaultSourceSetName = jvmConfiguration.defaultSourceSetName,
                        compilationToAssociateWith = listOf(androidTarget.compilations.getByName(
                            KmpPredefinedAndroidCompilation.MAIN.compilationName
                        ))
                    )
                }
            },
            { deviceConfiguration: KotlinMultiplatformAndroidExtensionImpl.KotlinMultiplatformAndroidTestConfigurationImpl ->
                if (project.pluginManager.hasPlugin(KOTLIN_MPP_PLUGIN_ID)) {
                    createCompilation(
                        compilationName = deviceConfiguration.compilationName,
                        defaultSourceSetName = deviceConfiguration.defaultSourceSetName,
                        compilationToAssociateWith = listOf(androidTarget.compilations.getByName(
                            KmpPredefinedAndroidCompilation.MAIN.compilationName
                        ))
                    )
                }
            }
        )

        settingsExtension?.let {
            androidExtension.initExtensionFromSettings(it)
        }

        BasePlugin.createAndroidTestUtilConfiguration(project)

        project.pluginManager.withPlugin(KOTLIN_MPP_PLUGIN_ID) {
            kotlinExtension = project.extensions.getByName("kotlin") as KotlinMultiplatformExtension

            androidTarget = kotlinExtension.createExternalKotlinTarget {
                targetName = androidTargetName
                platformType = KotlinPlatformType.jvm
                targetFactory = ExternalKotlinTargetDescriptor.TargetFactory { delegate ->
                    KotlinMultiplatformAndroidTargetImpl(
                        delegate, kotlinExtension, androidExtension
                    )
                }
                configureIdeImport {
                    KotlinIdeImportConfigurator.configure(
                        project,
                        this,
                        sourceSetToCreationConfigMap = lazy {
                            addSourceSetsThatShouldBeResolvedAsAndroid()
                            sourceSetToCreationConfigMap
                        },
                        extraSourceSetsToIncludeInResolution = lazy {
                            addSourceSetsThatShouldBeResolvedAsAndroid()
                            extraSourceSetsToIncludeInResolution
                        }
                    )
                }
            }

            (kotlinExtension as ExtensionAware).extensions.add(
                KotlinMultiplatformAndroidTarget::class.java,
                androidExtensionOnKotlinExtensionName,
                androidTarget
            )

            val mainCompilation = createCompilation(
                compilationName = KmpPredefinedAndroidCompilation.MAIN.compilationName,
                defaultSourceSetName = KmpPredefinedAndroidCompilation.MAIN.compilationName.getNamePrefixedWithTarget(),
                compilationToAssociateWith = emptyList()
            )

            androidExtension.androidTestOnJvmConfiguration?.let { jvmConfiguration ->
                createCompilation(
                    compilationName = jvmConfiguration.compilationName,
                    defaultSourceSetName = jvmConfiguration.defaultSourceSetName,
                    compilationToAssociateWith = listOf(mainCompilation)
                )
            }

            androidExtension.androidTestOnDeviceConfiguration?.let { deviceConfiguration ->
                createCompilation(
                    compilationName = deviceConfiguration.compilationName,
                    defaultSourceSetName = deviceConfiguration.defaultSourceSetName,
                    compilationToAssociateWith = listOf(mainCompilation)
                )
            }
        }
    }

    protected open fun KotlinMultiplatformAndroidExtension.initExtensionFromSettings(
        settings: SettingsExtension
    ) {
        settings.compileSdk?.let { compileSdk ->
            this.compileSdk = compileSdk

            settings.compileSdkExtension?.let { compileSdkExtension ->
                this.compileSdkExtension = compileSdkExtension
            }
        }

        settings.compileSdkPreview?.let { compileSdkPreview ->
            this.compileSdkPreview = compileSdkPreview
        }

        settings.minSdk?.let { minSdk ->
            this.minSdk = minSdk
        }

        settings.minSdkPreview?.let { minSdkPreview ->
            this.minSdkPreview = minSdkPreview
        }

        settings.buildToolsVersion.let { buildToolsVersion ->
            this.buildToolsVersion = buildToolsVersion
        }
    }

    private fun createCompilation(
        compilationName: String,
        defaultSourceSetName: String,
        compilationToAssociateWith: List<KotlinMultiplatformAndroidCompilation>
    ): KotlinMultiplatformAndroidCompilation {
        kotlinExtension.sourceSets.maybeCreate(
            defaultSourceSetName
        ).apply {
            android = KotlinAndroidSourceSetMarker()
        }
        return androidTarget.compilations.maybeCreate(
            compilationName
        ).also { main ->
            compilationToAssociateWith.forEach { other ->
                main.associateWith(other)
            }
        }
    }

    private fun getCompileSdkVersion(): String =
        androidExtension.compileSdkPreview?.let { validatePreviewTargetValue(it) }?.let { "android-$it" } ?:
        androidExtension.compileSdkExtension?.let { "android-${androidExtension.compileSdk}-ext$it" } ?:
        androidExtension.compileSdk?.let {"android-$it"} ?: throw RuntimeException(
            "compileSdk version is not set"
        )

    private fun getBuildToolsVersion(): Revision =
        Revision.parseRevision(androidExtension.buildToolsVersion, Revision.Precision.MICRO)

    private fun afterEvaluate(
        project: Project
    ) {
        androidExtension.lock()

        configureDisambiguationRules(project)

        val dependencyConfigurator = DependencyConfigurator(
            project = project,
            projectServices = projectServices
        )
            .configureDependencySubstitutions()
            .configureDependencyChecks()
            .configureGeneralTransforms(namespacedAndroidResources = global.namespacedAndroidResources, global.aarOrJarTypeToConsume)
            .configureCalculateStackFramesTransforms(global)

        val variantServices = VariantServicesImpl(projectServices)
        val taskServices = TaskCreationServicesImpl(projectServices)

        val taskManager = KmpTaskManager(
            project, global
        )

        mainVariant = createVariant(
            project,
            global,
            variantServices,
            taskServices,
            androidTarget
        )

        val unitTest = createUnitTestComponent(
            project,
            global,
            variantServices,
            taskServices,
            androidTarget
        )

        val androidTest = createAndroidTestComponent(
            project,
            global,
            variantServices,
            taskServices,
            taskManager,
            androidTarget
        )

        mainVariant.unitTest = unitTest
        mainVariant.androidTest = androidTest

        val stats = configuratorService.getVariantBuilder(
            project.path,
            mainVariant.name
        )

        androidExtension.executeVariantOperations(
            stats?.let {
                variantServices.newInstance(
                    AnalyticsEnabledKotlinMultiplatformAndroidVariant::class.java,
                    mainVariant,
                    stats
                )
            } ?: mainVariant
        )
        listOfNotNull(mainVariant, unitTest, androidTest).forEach {
            it.syncAndroidAndKmpClasspathAndSources()

            it.androidKotlinCompilation.kotlinSourceSets.forEach { sourceSet ->
                sourceSetToCreationConfigMap[sourceSet] = it
            }
        }

        (global.compileOptions as CompileOptions).finalizeSourceAndTargetCompatibility(project)

        dependencyConfigurator.configureVariantTransforms(
            variants = listOf(mainVariant),
            nestedComponents = mainVariant.nestedComponents,
            bootClasspathConfig = global
        )

        if (androidTest?.isAndroidTestCoverageEnabled == true) {
            dependencyConfigurator.configureJacocoTransforms()
        }

        taskManager.createTasks(
            project,
            mainVariant,
            unitTest,
            androidTest
        )

        finalizeAllComponents(listOfNotNull(mainVariant, unitTest, androidTest))

        KotlinModelBuildingConfigurator.setupAndroidTargetModels(
            project,
            mainVariant,
            androidTarget,
            projectServices.projectOptions,
            syncIssueReporter
        )

        KotlinModelBuildingConfigurator.setupAndroidCompilations(
            listOfNotNull(mainVariant, unitTest, androidTest),
            androidExtension.testInstrumentationRunner,
            androidExtension.testInstrumentationRunnerArguments
        )
    }

    private fun addSourceSetsThatShouldBeResolvedAsAndroid() {
        // Here we check if there are sourceSets that are included only in the androidTarget, this
        // means that the sourceSet should be treated as android sourceSet in IDE Import and its
        // dependencies should be resolved from the component that maps to the compilation
        // containing this sourceSet.
        kotlinExtension.sourceSets.mapNotNull { sourceSet ->
            val targetsContainingSourceSet = kotlinExtension.targets.filter { target ->
                target.platformType != KotlinPlatformType.common &&
                        target.compilations.any { compilation ->
                            compilation.allKotlinSourceSets.contains(sourceSet)
                        }
            }
            sourceSet.takeIf { targetsContainingSourceSet.singleOrNull() == androidTarget }
        }.forEach { commonSourceSet ->
            for (component in listOfNotNull(mainVariant, mainVariant.unitTest, mainVariant.androidTest)) {
                if (component.androidKotlinCompilation.allKotlinSourceSets.contains(commonSourceSet)) {
                    sourceSetToCreationConfigMap[commonSourceSet] = component
                    extraSourceSetsToIncludeInResolution.add(commonSourceSet)
                    break
                }
            }
        }
    }

    private fun Configuration.forMainVariantConfiguration(
        dslInfo: KmpComponentDslInfo
    ): Configuration? {
        return this.takeIf {
            !dslInfo.componentType.isTestComponent
        }
    }

    private fun createVariantDependencies(
        project: Project,
        dslInfo: KmpComponentDslInfo,
        androidKotlinCompilation: KotlinMultiplatformAndroidCompilationImpl,
        androidTarget: KotlinMultiplatformAndroidTargetImpl
    ): VariantDependencies = VariantDependencies.createForKotlinMultiplatform(
        project = project,
        projectOptions = projectServices.projectOptions,
        dslInfo = dslInfo,
        apiClasspath = project.configurations.getByName(
            androidKotlinCompilation.apiConfigurationName
        ),
        compileClasspath = project.configurations.getByName(
            androidKotlinCompilation.compileDependencyConfigurationName
        ),
        runtimeClasspath = project.configurations.getByName(
            androidKotlinCompilation.runtimeDependencyConfigurationName!!
        ),
        apiElements = androidTarget.apiElementsConfiguration.forMainVariantConfiguration(dslInfo),
        runtimeElements = androidTarget.runtimeElementsConfiguration.forMainVariantConfiguration(dslInfo),
        sourcesElements = project.configurations.findByName(
            androidTarget.sourcesElementsConfigurationName
        )?.forMainVariantConfiguration(dslInfo),
        apiPublication = androidTarget.apiElementsPublishedConfiguration.forMainVariantConfiguration(dslInfo),
        runtimePublication = androidTarget.runtimeElementsPublishedConfiguration.forMainVariantConfiguration(dslInfo)
    )

    private fun getAndroidManifestDefaultLocation(
        compilation: KotlinMultiplatformAndroidCompilation
    ) = FileUtils.join(
        compilation.project.projectDir,
        "src",
        compilation.defaultSourceSet.name,
        "AndroidManifest.xml"
    )

    private fun createVariant(
        project: Project,
        global: GlobalTaskCreationConfig,
        variantServices: VariantServices,
        taskCreationServices: TaskCreationServices,
        androidTarget: KotlinMultiplatformAndroidTargetImpl
    ): KmpVariantImpl {

        val dslInfo = KmpVariantDslInfoImpl(
            androidExtension,
            variantServices,
            project.layout.buildDirectory
        )

        val paths = VariantPathHelper(
            project.layout.buildDirectory,
            dslInfo,
            dslServices
        )

        val artifacts = ArtifactsImpl(project, dslInfo.componentIdentity.name)

        val kotlinCompilation = androidTarget.compilations.getByName(
            KmpPredefinedAndroidCompilation.MAIN.compilationName
        ) as KotlinMultiplatformAndroidCompilationImpl

        return KmpVariantImpl(
            dslInfo = dslInfo,
            internalServices = variantServices,
            buildFeatures = KotlinMultiplatformBuildFeaturesValuesImpl(),
            variantDependencies = createVariantDependencies(project, dslInfo, kotlinCompilation, androidTarget),
            paths = paths,
            artifacts = artifacts,
            taskContainer = MutableTaskContainer(),
            services = taskCreationServices,
            global = global,
            androidKotlinCompilation = kotlinCompilation,
            manifestFile = getAndroidManifestDefaultLocation(kotlinCompilation)
        )
    }

    private fun createUnitTestComponent(
        project: Project,
        global: GlobalTaskCreationConfig,
        variantServices: VariantServices,
        taskCreationServices: TaskCreationServices,
        androidTarget: KotlinMultiplatformAndroidTargetImpl
    ): KmpUnitTestImpl? {
        if (!mainVariant.dslInfo.enabledUnitTest) {
            return null
        }

        val dslInfo = KmpUnitTestDslInfoImpl(
            androidExtension,
            variantServices,
            mainVariant.dslInfo,
        )

        val paths = VariantPathHelper(
            project.layout.buildDirectory,
            dslInfo,
            dslServices
        )

        val artifacts = ArtifactsImpl(project, dslInfo.componentIdentity.name)

        val kotlinCompilation = androidTarget.compilations.getByName(
            androidExtension.androidTestOnJvmConfiguration!!.compilationName
        ) as KotlinMultiplatformAndroidCompilationImpl

        return KmpUnitTestImpl(
            dslInfo = dslInfo,
            internalServices = variantServices,
            buildFeatures = KotlinMultiplatformBuildFeaturesValuesImpl(),
            variantDependencies = createVariantDependencies(project, dslInfo, kotlinCompilation, androidTarget),
            paths = paths,
            artifacts = artifacts,
            taskContainer = MutableTaskContainer(),
            services = taskCreationServices,
            global = global,
            androidKotlinCompilation = kotlinCompilation,
            mainVariant = mainVariant,
            manifestFile = getAndroidManifestDefaultLocation(kotlinCompilation)
        )
    }

    private fun createAndroidTestComponent(
        project: Project,
        global: GlobalTaskCreationConfig,
        variantServices: VariantServices,
        taskCreationServices: TaskCreationServices,
        taskManager: KmpTaskManager,
        androidTarget: KotlinMultiplatformAndroidTargetImpl
    ): KmpAndroidTestImpl? {
        if (!mainVariant.dslInfo.enableAndroidTest) {
            return null
        }

        val kotlinCompilation = androidTarget.compilations.getByName(
            androidExtension.androidTestOnDeviceConfiguration!!.compilationName
        ) as KotlinMultiplatformAndroidCompilationImpl

        val manifestLocation = getAndroidManifestDefaultLocation(kotlinCompilation)

        val manifestParser = LazyManifestParser(
            manifestFile = projectServices.objectFactory.fileProperty().fileValue(manifestLocation),
            manifestFileRequired = true,
            projectServices = projectServices
        ) {
            taskManager.hasCreatedTasks || !projectServices.projectOptions.get(
                BooleanOption.DISABLE_EARLY_MANIFEST_PARSING
            )
        }

        val dslInfo = KmpAndroidTestDslInfoImpl(
            androidExtension,
            variantServices,
            manifestParser,
            mainVariant.dslInfo,
            createSigningOverride(dslServices),
            dslServices
        )

        val paths = VariantPathHelper(
            project.layout.buildDirectory,
            dslInfo,
            dslServices
        )

        val artifacts = ArtifactsImpl(project, dslInfo.componentIdentity.name)

        return KmpAndroidTestImpl(
            dslInfo = dslInfo,
            internalServices = variantServices,
            buildFeatures = KotlinMultiplatformBuildFeaturesValuesImpl(),
            variantDependencies = createVariantDependencies(project, dslInfo, kotlinCompilation, androidTarget),
            paths = paths,
            artifacts = artifacts,
            taskContainer = MutableTaskContainer(),
            services = taskCreationServices,
            global = global,
            androidKotlinCompilation = kotlinCompilation,
            mainVariant = mainVariant,
            manifestFile = manifestLocation
        )
    }

    private fun configureDisambiguationRules(project: Project) {
        project.dependencies.attributesSchema { schema ->
            val buildTypesToMatch = androidExtension.dependencyVariantSelection.buildTypes.get()
            if (buildTypesToMatch.isNotEmpty()){
                schema.attribute(BuildTypeAttr.ATTRIBUTE)
                    .disambiguationRules
                    .add(SingleVariantBuildTypeRule::class.java) { config ->
                        config.setParams(buildTypesToMatch)
                    }
            }

            androidExtension.dependencyVariantSelection.productFlavors.get().forEach { (dimension, fallbacks) ->
                schema.attribute(ProductFlavorAttr.of(dimension))
                    .disambiguationRules
                    .add(SingleVariantProductFlavorRule::class.java) { config ->
                        config.setParams(fallbacks)
                    }
            }

            schema.attribute(AgpVersionAttr.ATTRIBUTE)
                .compatibilityRules
                .add(AgpVersionCompatibilityRule::class.java)

            setUp(schema)
        }
    }

    companion object {
        private const val androidTargetName = "android"
        const val androidExtensionOnKotlinExtensionName = "androidExperimental"
        fun String.getNamePrefixedWithTarget() = androidTargetName.appendCapitalized(this)
    }
}
