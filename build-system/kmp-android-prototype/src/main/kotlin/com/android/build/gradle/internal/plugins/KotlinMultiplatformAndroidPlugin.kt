/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.build.api.variant.impl.KmpVariantImpl
import com.android.build.gradle.internal.DependencyConfigurator
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.core.dsl.KmpComponentDslInfo
import com.android.build.gradle.internal.core.dsl.impl.KmpVariantDslInfoImpl
import com.android.build.gradle.internal.dependency.AgpVersionCompatibilityRule
import com.android.build.gradle.internal.dependency.SingleVariantBuildTypeRule
import com.android.build.gradle.internal.dependency.SingleVariantProductFlavorRule
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtension
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtensionImpl
import com.android.build.gradle.internal.dsl.decorator.androidPluginDslDecorator
import com.android.build.gradle.internal.scope.KotlinMultiplatformBuildFeaturesValuesImpl
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.Aapt2ThreadPoolBuildService
import com.android.build.gradle.internal.services.ClassesHierarchyBuildService
import com.android.build.gradle.internal.services.DslServicesImpl
import com.android.build.gradle.internal.services.FakeDependencyJarBuildService
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.TaskCreationServicesImpl
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.services.VariantServicesImpl
import com.android.build.gradle.internal.services.VersionedSdkLoaderService
import com.android.build.gradle.internal.tasks.KmpTaskManager
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfigImpl
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.KmpGlobalTaskCreationConfigImpl
import com.android.build.gradle.internal.utils.validatePreviewTargetValue
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.builder.core.ComponentTypeImpl
import com.android.repository.Revision
import com.android.utils.FileUtils
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.build.event.BuildEventsListenerRegistry
import javax.inject.Inject

abstract class KotlinMultiplatformAndroidPlugin @Inject constructor(
    listenerRegistry: BuildEventsListenerRegistry
): AndroidPluginBaseServices(listenerRegistry), Plugin<Project> {

    private lateinit var global: GlobalTaskCreationConfig
    private lateinit var androidExtension: KotlinMultiplatformAndroidExtensionImpl

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

    // TODO(b/243387425): Support analytics
    override fun getAnalyticsPluginType(): GradleBuildProject.PluginType? =
        GradleBuildProject.PluginType.UNKNOWN_PLUGIN_TYPE

    override fun configureProject(project: Project) { }

    override fun createTasks(project: Project) { }

    override fun apply(project: Project) {
        super.basePluginApply(project)

        FakeDependencyJarBuildService.RegistrationAction(project).execute()
        Aapt2ThreadPoolBuildService.RegistrationAction(project, projectServices.projectOptions).execute()
        Aapt2DaemonBuildService.RegistrationAction(project, projectServices.projectOptions).execute()
        ClassesHierarchyBuildService.RegistrationAction(project).execute()

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
            dslServices
        )

        TaskManager.createTasksBeforeEvaluate(
            project,
            ComponentTypeImpl.KMP_ANDROID,
            emptySet(),
            global
        )

        project.afterEvaluate {
            if (!project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                throw RuntimeException("Kotlin multiplatform plugin was not found. This plugin needs" +
                        " to be applied as part of the kotlin multiplatform plugin.")
            }
            afterEvaluate(it)
        }
    }

    override fun configureExtension(project: Project) {
        val extensionImplClass = androidPluginDslDecorator
            .decorate(KotlinMultiplatformAndroidExtensionImpl::class.java)
        androidExtension = project.extensions.create(
            KotlinMultiplatformAndroidExtension::class.java,
            "android",
            extensionImplClass,
            dslServices
        ) as KotlinMultiplatformAndroidExtensionImpl
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
            .configureGeneralTransforms(namespacedAndroidResources = false)
            .configureCalculateStackFramesTransforms(global)

        val variantServices = VariantServicesImpl(projectServices)
        val taskServices = TaskCreationServicesImpl(projectServices)

        val taskManager = KmpTaskManager()

        val mainVariant = createVariant(
            project,
            global,
            variantServices,
            taskServices
        )

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

        dependencyConfigurator.configureVariantTransforms(
            variants = listOf(mainVariant),
            nestedComponents = listOf(),
            bootClasspathConfig = global
        )

        taskManager.createTasks(
            project,
            createVariant(
                project,
                global,
                variantServices,
                taskServices
            )
        )
    }

    // TODO: implement
    abstract fun createVariantDependencies(
        project: Project,
        dslInfo: KmpComponentDslInfo
    ): VariantDependencies

    private fun createVariant(
        project: Project,
        global: GlobalTaskCreationConfig,
        variantServices: VariantServices,
        taskCreationServices: TaskCreationServices,
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

        return KmpVariantImpl(
            dslInfo = dslInfo,
            internalServices = variantServices,
            buildFeatures = KotlinMultiplatformBuildFeaturesValuesImpl(),
            variantDependencies = createVariantDependencies(project, dslInfo),
            paths = paths,
            artifacts = artifacts,
            taskContainer = MutableTaskContainer(),
            services = taskCreationServices,
            global = global,
            // TODO: Search for the manifest in the kotlin source directories
            manifestFile = FileUtils.join(
                project.projectDir, "src", "androidMain", "AndroidManifest.xml"
            )
        )
    }

    private fun configureDisambiguationRules(project: Project) {
        project.dependencies.attributesSchema { schema ->
            if (androidExtension.buildTypeMatching.isNotEmpty()) {
                schema.attribute(BuildTypeAttr.ATTRIBUTE)
                    .disambiguationRules
                    .add(SingleVariantBuildTypeRule::class.java) { config ->
                        config.setParams(androidExtension.buildTypeMatching)
                    }
            }

            androidExtension.productFlavorsMatching.forEach { (dimension, fallbacks) ->
                schema.attribute(ProductFlavorAttr.of(dimension))
                    .disambiguationRules
                    .add(SingleVariantProductFlavorRule::class.java) { config ->
                        config.setParams(fallbacks)
                    }
            }

            schema.attribute(AgpVersionAttr.ATTRIBUTE)
                .compatibilityRules
                .add(AgpVersionCompatibilityRule::class.java)
        }
    }
}
