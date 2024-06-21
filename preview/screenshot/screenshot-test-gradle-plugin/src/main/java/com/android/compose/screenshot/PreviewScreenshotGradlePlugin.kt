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

package com.android.compose.screenshot

import com.android.SdkConstants
import com.android.build.api.AndroidPluginVersion
import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasHostTests
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.Variant
import com.android.build.gradle.api.AndroidBasePlugin
import com.android.compose.screenshot.layoutlibExtractor.LayoutlibDataFromMaven
import com.android.compose.screenshot.services.AnalyticsService
import com.android.compose.screenshot.tasks.PreviewDiscoveryTask
import com.android.compose.screenshot.tasks.PreviewScreenshotRenderTask
import com.android.compose.screenshot.tasks.PreviewScreenshotUpdateTask
import com.android.compose.screenshot.tasks.PreviewScreenshotValidationTask
import com.android.compose.screenshot.tasks.ScreenshotTestReportTask
import java.lang.StringBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.jvm.toolchain.JavaToolchainService
import java.util.Locale
import java.util.Properties
import java.util.UUID
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult

private val minAgpVersion = AndroidPluginVersion(8, 5, 0).beta(1)
private val maxAgpVersion = AndroidPluginVersion(8,6    ,255)

/**
 * An entry point for Screenshot plugin that adds support for screenshot testing on Compose Previews
 */
class PreviewScreenshotGradlePlugin : Plugin<Project> {

    companion object {
        /**
         * Get build service name that works even if build service types come from different
         * class loaders. If the service name is the same, and some type T is defined in two
         * class loaders L1 and L2. E.g. this is true for composite builds and other project
         * setups (see b/154388196).
         *
         * Registration of service may register (T from L1) or (T from L2). This means that
         * querying it with T from other class loader will fail at runtime. This method makes
         * sure both T from L1 and T from L2 will successfully register build services.
         *
         * Copied from
         * com.android.build.gradle.internal.services.BuildServicesKt.getBuildServiceName.
         */
        private fun getBuildServiceName(type: Class<*>): String {
            return type.name + "_" + perClassLoaderConstant
        }

        /**
         *  Used to get unique build service name. Each class loader will initialize its own
         *  version.
         */
        private val perClassLoaderConstant = UUID.randomUUID().toString()

        const val ST_SOURCE_SET_ENABLED = "android.experimental.enableScreenshotTest"
        private const val LAYOUTLIB_VERSION = "14.0.9"
        private const val LAYOUTLIB_RUNTIME_VERSION = "14.0.9"
        private const val LAYOUTLIB_RESOURCES_VERSION = "14.0.9"

        val SCREENSHOT_TEST_PLUGIN_VERSION: String by lazy {
            requireNotNull(PreviewScreenshotGradlePlugin::class.java.getResourceAsStream("/version.properties"))
                .buffered().use { stream ->
                    Properties().let { properties ->
                        properties.load(stream)
                        properties.getProperty("buildVersion")
                    }
                }
        }
    }

    override fun apply(project: Project) {
        project.plugins.withType(AndroidBasePlugin::class.java) {
            val componentsExtension = project.extensions.getByType(AndroidComponentsExtension::class.java)
            val agpVersion = componentsExtension.pluginVersion
            if (agpVersion < minAgpVersion || (agpVersion > maxAgpVersion && agpVersion.previewType != "dev")) {
                error(
                    """
                    Preview screenshot plugin requires Android Gradle plugin version between ${minAgpVersion.toVersionString()} and ${maxAgpVersion.major}.${maxAgpVersion.minor}.
                    Current version is $agpVersion.
                    """.trimIndent()
                )
            }
            val screenshotSourcesetEnabled = project.findProperty(ST_SOURCE_SET_ENABLED)
            if (screenshotSourcesetEnabled.toString().lowercase(Locale.US) != "true") {
                error(
                    """
                    Please enable screenshotTest source set first to apply the screenshot test plugin.
                    Add "$ST_SOURCE_SET_ENABLED=true" to gradle.properties
                    """.trimIndent()
                )
            }

            val analyticsServiceProvider = project.gradle.sharedServices.registerIfAbsent(
                getBuildServiceName(AnalyticsService::class.java),
                AnalyticsService::class.java) { spec ->
                spec.parameters.androidGradlePluginVersion.set(agpVersion.toVersionString())
            }

            val sdkDirectory = componentsExtension.sdkComponents.sdkDirectory
            createPreviewlibCliToolConfiguration(project)
            createLayoutlibConfiguration(project)
            createLayoutlibRuntimeConfiguration(project)
            createLayoutlibResourcesConfiguration(project)
            val layoutlibDataFromMaven = LayoutlibDataFromMaven.create(project, LAYOUTLIB_RUNTIME_VERSION,
              project.configurations.getByName(layoutlibResourcesConfigurationName))

            val updateAllTask = project.tasks.register(
                "updateScreenshotTest",
                Task::class.java
            ) { task ->
                task.description = "Update screenshots for all variants."
                task.group = JavaBasePlugin.VERIFICATION_GROUP
            }

            val validateAllTask = project.tasks.register(
                "validateScreenshotTest",
                Task::class.java
            ) { task ->
                task.description = "Run screenshot tests for all variants."
                task.group = JavaBasePlugin.VERIFICATION_GROUP
            }

            val buildDir = project.layout.buildDirectory

            // this will be provided by AGP at some point.
            fun Variant.computePathSegments(): String {
                return buildType?.let { bt ->
                    flavorName?.let { fn ->
                        "$bt/$fn"
                    } ?: bt
                } ?: flavorName ?: ""
            }

            componentsExtension.beforeVariants {
                val extension = project.extensions.getByType(CommonExtension::class.java)
                val screenshotSourceSetEnabledInModule = extension.experimentalProperties[ST_SOURCE_SET_ENABLED]
                if (screenshotSourceSetEnabledInModule.toString().lowercase(Locale.US) != "true") {
                    error(
                        """
                    Please enable screenshotTest source set in module first to apply the screenshot test plugin.
                    Add "experimentalProperties["$ST_SOURCE_SET_ENABLED"] = true" to the android block of the module's build file: ${project.buildFile.toURI()}
                    """.trimIndent()
                    )
                }
            }
            componentsExtension.onVariants { variant ->
                if (variant is HasHostTests && variant.debuggable) {
                    val variantName = variant.name
                    val screenshotTestComponent = variant.hostTests[HostTestBuilder.SCREENSHOT_TEST_TYPE] ?: return@onVariants
                    val referenceImagePath = "src/$variantName/screenshotTest/reference"
                    val discoveryTaskProvider =
                        project.tasks.register(
                            "${variantName}PreviewDiscovery",
                            PreviewDiscoveryTask::class.java
                        ) { task ->
                            val variantSegments = variant.computePathSegments()
                            task.previewsOutputFile.set(buildDir.file("$PREVIEW_INTERMEDIATES/$variantSegments/previews_discovered.json"))
                            task.previewsOutputFile.disallowChanges()
                            task.resultsDir.set(buildDir.dir("$PREVIEW_OUTPUT/$variantSegments"))
                            task.analyticsService.set(analyticsServiceProvider)
                            task.usesService(analyticsServiceProvider)

                            val dependencyArtifacts = screenshotTestComponent.runtimeConfiguration.incoming.artifactView {
                                it.attributes.apply {
                                    attribute(
                                        Attribute.of("artifactType", String::class.java),
                                        "android-classes"
                                    )
                                }
                            }.artifacts

                            task.dependencies.from(dependencyArtifacts.artifactFiles)
                            task.dependencies.disallowChanges()
                        }

                    screenshotTestComponent.artifacts
                        .forScope(ScopedArtifacts.Scope.PROJECT)
                        .use(discoveryTaskProvider)
                        .toGet(
                            ScopedArtifact.CLASSES,
                            PreviewDiscoveryTask::testJars,
                            PreviewDiscoveryTask::testClassesDir,
                        )

                    variant.artifacts
                        .forScope(ScopedArtifacts.Scope.PROJECT)
                        .use(discoveryTaskProvider)
                        .toGet(
                            ScopedArtifact.CLASSES,
                            PreviewDiscoveryTask::mainJars,
                            PreviewDiscoveryTask::mainClassesDir,
                        )

                    //reflection to access gradle-core classes without explicit dependency
                    val classLoader = this.javaClass.classLoader
                    val artifactsImplClass = classLoader.loadClass(ARTIFACT_IMPL)
                    val analyticsEnabledArtifactsClass = classLoader.loadClass(ANALYTICS_ENABLED_ARTIFACTS)
                    val analyticsEnabledArtifactsGetDelegateMethod = analyticsEnabledArtifactsClass.getMethod("getDelegate")
                    val apkForLocalTestClass = classLoader.loadClass("${INTERNAL_ARTIFACT_TYPE}\$APK_FOR_LOCAL_TEST")
                    val artifactsImplGet = artifactsImplClass.getDeclaredMethod("get", Artifact.Single::class.java)

                    val artifacts = screenshotTestComponent.artifacts
                    val artifactImplObject = when {
                        artifactsImplClass.isInstance(artifacts) -> artifacts
                        analyticsEnabledArtifactsClass.isInstance(artifacts) -> analyticsEnabledArtifactsGetDelegateMethod.invoke(artifacts)
                        else -> throw RuntimeException("Unexpected artifact type ${artifacts.javaClass}")
                    }
                    val instance = apkForLocalTestClass.getField("INSTANCE").get(null)
                    // ArtifactsImpl::get(InternalArtifactType.APK_FOR_LOCAL_TEST)
                    @Suppress("UNCHECKED_CAST")
                    val resourceFileProvider = artifactsImplGet.invoke(artifactImplObject, instance) as? Provider<RegularFile>

                    val renderTaskProvider = project.tasks.register(
                        "${variantName}PreviewScreenshotRender",
                        PreviewScreenshotRenderTask::class.java
                    ) { task ->
                        val variantSegments = variant.computePathSegments()
                        val output = "$PREVIEW_OUTPUT/$variantSegments"
                        task.outputDir.set(buildDir.dir("$output/rendered"))
                        task.resultsFile.set(buildDir.file("$output/results.json"))
                        task.metaDataDir.set(buildDir.dir("$output/meta-data"))

                        // need to use project.providers as a workaround to gradle issue: https://github.com/gradle/gradle/issues/12388
                        task.sdkFontsDir.set(project.providers.provider {
                            val subDir = sdkDirectory.get().asFile.resolve(SdkConstants.SDK_DL_FONTS_FOLDER)
                            if (subDir.exists()) sdkDirectory.get().dir(SdkConstants.SDK_DL_FONTS_FOLDER) else null
                        })
                        task.previewsDiscovered.set(discoveryTaskProvider.flatMap { it.previewsOutputFile })
                        task.screenshotCliJar.from(task.project.configurations.getByName(
                            previewlibCliToolConfigurationName
                        ))
                        task.layoutlibJar.from(task.project.configurations.getByName(
                            layoutlibJarConfigurationName))
                        task.layoutlibDataDir.setFrom(layoutlibDataFromMaven.layoutlibDataDirectory)
                        resourceFileProvider?.let { task.resourceFile.set(it) }

                        task.namespace.set(variant.namespace)
                        task.cliToolArgumentsFile.set(buildDir.file("$PREVIEW_INTERMEDIATES/$variantSegments/cli_tool_arguments.json"))

                        val toolchain = project.extensions.getByType(JavaPluginExtension::class.java).toolchain
                        val service = project.extensions.getByType(JavaToolchainService::class.java)
                        task.javaLauncher.set(service.launcherFor(toolchain))

                        task.analyticsService.set(analyticsServiceProvider)
                        task.usesService(analyticsServiceProvider)
                    }

                    // Rendering requires androidx.compose.ui:ui-tooling as a runtime dependency
                    variant.runtimeConfiguration.checkUiToolingPresent { isPresent ->
                        if (!isPresent) {
                            val errorMessage = "Missing required runtime dependency. Please add androidx.compose.ui:ui-tooling as a screenshotTestImplementation dependency."
                            screenshotTestComponent.runtimeConfiguration.checkUiToolingPresent { isPresentInScreenshotTests ->
                                if (!isPresentInScreenshotTests) {
                                    throw RuntimeException(errorMessage)
                                }
                            }
                        }
                    }

                    variant.artifacts
                        .forScope(ScopedArtifacts.Scope.ALL)
                        .use(renderTaskProvider)
                        .toGet(
                            ScopedArtifact.CLASSES,
                            PreviewScreenshotRenderTask::mainClasspathAll,
                            PreviewScreenshotRenderTask::mainClassesDirAll,
                        )

                    screenshotTestComponent.artifacts
                        .forScope(ScopedArtifacts.Scope.ALL)
                        .use(renderTaskProvider)
                        .toGet(
                            ScopedArtifact.CLASSES,
                            PreviewScreenshotRenderTask::testClasspathAll,
                            PreviewScreenshotRenderTask::testClassesDirAll,
                        )

                    variant.artifacts
                            .forScope(ScopedArtifacts.Scope.PROJECT)
                            .use(renderTaskProvider)
                            .toGet(
                                    ScopedArtifact.CLASSES,
                                    PreviewScreenshotRenderTask::mainClasspathProject,
                                    PreviewScreenshotRenderTask::mainClassesDirProject,
                            )

                    screenshotTestComponent.artifacts
                            .forScope(ScopedArtifacts.Scope.PROJECT)
                            .use(renderTaskProvider)
                            .toGet(
                                    ScopedArtifact.CLASSES,
                                    PreviewScreenshotRenderTask::testClasspathProject,
                                    PreviewScreenshotRenderTask::testClassesDirProject,
                            )

                    val updateTask = project.tasks.register(
                        "update${variantName.capitalized()}ScreenshotTest",
                        PreviewScreenshotUpdateTask::class.java
                    ) { task ->
                        task.referenceImageDir.set(project.layout.projectDirectory.dir(referenceImagePath))
                        task.renderTaskOutputDir.set(renderTaskProvider.flatMap { it.outputDir })
                        task.renderTaskResultFile.set(renderTaskProvider.flatMap { it.resultsFile })
                        task.description = "Update screenshots for the $variantName build."
                        task.group = JavaBasePlugin.VERIFICATION_GROUP
                        task.analyticsService.set(analyticsServiceProvider)
                        task.usesService(analyticsServiceProvider)
                    }
                    updateAllTask.configure { it.dependsOn(updateTask) }

                    val previewScreenshotValidationTask = project.tasks.register(
                        "validate${variantName.capitalized()}ScreenshotTest",
                        PreviewScreenshotValidationTask::class.java
                    ) { task ->
                        val variantSegments = variant.computePathSegments()
                        task.referenceImageDir.set(project.layout.projectDirectory.dir(referenceImagePath))
                        task.referenceImageDir.disallowChanges()
                        task.previewFile.set(discoveryTaskProvider.flatMap { it.previewsOutputFile })
                        task.renderTaskOutputDir.set(renderTaskProvider.flatMap { it.outputDir })
                        task.renderTaskOutputFile.set(renderTaskProvider.flatMap { it.resultsFile })
                        task.resultsDir.set(buildDir.dir("$PREVIEW_OUTPUT/$variantSegments/results"))
                        task.diffImageDir.set(buildDir.dir("$PREVIEW_OUTPUT/$variantSegments/diffs"))
                        task.diffImageDir.disallowChanges()
                        task.analyticsService.set(analyticsServiceProvider)
                        task.usesService(analyticsServiceProvider)
                        task.description = "Run screenshot tests for the $variantName build."
                        task.group = JavaBasePlugin.VERIFICATION_GROUP
                        maybeCreateScreenshotTestConfiguration(project)
                        task.useJUnitPlatform {
                            it.includeEngines("preview-screenshot-test-engine")
                        }
                        task.testLogging {
                            it.showStandardStreams = true
                        }
                        // TODO(b/325320710): Use the standard test report when extension points for
                        //  adding custom information become available
                        task.reports { it.html.required.set(false) }
                        task.testClassesDirs = project.files(renderTaskProvider.flatMap { it.testClassesDirAll })
                        task.classpath = task.project.configurations.getByName(previewScreenshotTestEngineConfigurationName) + task.testClassesDirs + project.files(renderTaskProvider.flatMap { it.testClasspathAll })
                    }

                    val screenshotHtmlTask = project.tasks.register(
                        "${variantName}ScreenshotReport",
                        ScreenshotTestReportTask::class.java
                    ) { task ->
                        val variantSegments = variant.computePathSegments()
                        task.outputDir.set(buildDir.dir("$PREVIEW_REPORTS/$variantSegments"))
                        task.resultsDir.set(previewScreenshotValidationTask.flatMap { it.resultsDir })
                        task.analyticsService.set(analyticsServiceProvider)
                        task.usesService(analyticsServiceProvider)
                    }
                    previewScreenshotValidationTask.configure {
                        it.finalizedBy(screenshotHtmlTask)
                    }
                    validateAllTask.configure { it.dependsOn(previewScreenshotValidationTask) }
                }
            }
        }
    }

    private fun maybeCreateScreenshotTestConfiguration(project: Project) {
        val container = project.configurations
        val dependencies = project.dependencies
        if (container.findByName(previewScreenshotTestEngineConfigurationName) == null) {
            container.create(previewScreenshotTestEngineConfigurationName).apply {
                isVisible = false
                isTransitive = true
                isCanBeConsumed = false
                description = "A configuration to resolve screenshot test engine dependencies."
            }
            val engineVersion = SCREENSHOT_TEST_PLUGIN_VERSION
            dependencies.add(previewScreenshotTestEngineConfigurationName, "org.junit.platform:junit-platform-launcher")
            dependencies.add(
                previewScreenshotTestEngineConfigurationName,
                "com.android.tools.screenshot:screenshot-validation-junit-engine:${engineVersion}")
        }
    }

    private fun createPreviewlibCliToolConfiguration(project: Project) {
        val container = project.configurations
        val dependencies = project.dependencies
        if (container.findByName(previewlibCliToolConfigurationName) == null) {
            container.create(previewlibCliToolConfigurationName).apply {
                isVisible = false
                isTransitive = true
                isCanBeConsumed = false
                description = "A configuration to resolve render CLI tool dependencies."
            }
            val version = SCREENSHOT_TEST_PLUGIN_VERSION
            dependencies.add(
                previewlibCliToolConfigurationName,
                "com.android.tools.compose:compose-preview-renderer:$version")
        }
    }

    private fun createLayoutlibConfiguration(project: Project) {
        val container = project.configurations
        val dependencies = project.dependencies
        if (container.findByName(layoutlibJarConfigurationName) == null) {
            container.create(layoutlibJarConfigurationName).apply {
                isVisible = false
                isTransitive = true
                isCanBeConsumed = false
                description = "A configuration to resolve layoutlib jar dependencies."
            }
            val version = LAYOUTLIB_VERSION
            dependencies.add(
                layoutlibJarConfigurationName,
                "com.android.tools.layoutlib:layoutlib:$version")
        }
    }

    private fun createLayoutlibRuntimeConfiguration(project: Project) {
        val container = project.configurations
        val dependencies = project.dependencies
        if (container.findByName(layoutlibRunTimeConfigurationName) == null) {
            container.create(layoutlibRunTimeConfigurationName).apply {
                isVisible = false
                isTransitive = true
                isCanBeConsumed = false
                description = "A configuration to resolve layoutlib runtime data dependencies."
            }
            val version = LAYOUTLIB_RUNTIME_VERSION
            dependencies.add(
                layoutlibRunTimeConfigurationName,
                "com.android.tools.layoutlib:layoutlib-runtime:$version")
        }
    }

    private fun createLayoutlibResourcesConfiguration(project: Project) {
        val container = project.configurations
        val dependencies = project.dependencies
        if (container.findByName(layoutlibResourcesConfigurationName) == null) {
            container.create(layoutlibResourcesConfigurationName).apply {
                isVisible = false
                isTransitive = true
                isCanBeConsumed = false
                description = "A configuration to resolve render CLI tool dependencies."
            }
            val version = LAYOUTLIB_RESOURCES_VERSION
            dependencies.add(
                layoutlibResourcesConfigurationName,
                "com.android.tools.layoutlib:layoutlib-resources:$version")
        }
    }

    private fun AndroidPluginVersion.toVersionString(): String {
        val builder = StringBuilder("$major.$minor.$micro")
        previewType?.let { builder.append("-$it") }
        if (preview > 0) {
            builder.append(preview.toString().padStart(2, '0'))
        }
        return builder.toString()
    }

    private fun Configuration.checkUiToolingPresent(callback: (Boolean) -> Unit) {
        incoming.afterResolve {
            val isPresent = it.resolutionResult.allDependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .map { result -> result.selected.id }
                .filterIsInstance<ModuleComponentIdentifier>()
                .any { identifier -> identifier.group == "androidx.compose.ui" && identifier.module == "ui-tooling"
                }
            callback(isPresent)
        }
    }

}

private const val previewlibCliToolConfigurationName = "_internal-screenshot-test-task-previewlib-cli"
private const val previewScreenshotTestEngineConfigurationName = "_internal-screenshot-validation-junit-engine"
private const val layoutlibJarConfigurationName = "_internal-screenshot-test-task-layoutlib"
private const val layoutlibRunTimeConfigurationName = "_internal-screenshot-test-task-layoutlib-data"
private const val layoutlibResourcesConfigurationName = "_internal-screenshot-test-task-layoutlib-res"
private const val ARTIFACT_IMPL = "com.android.build.api.artifact.impl.ArtifactsImpl"
private const val ANALYTICS_ENABLED_ARTIFACTS = "com.android.build.api.component.analytics.AnalyticsEnabledArtifacts"
private const val INTERNAL_ARTIFACT_TYPE = "com.android.build.gradle.internal.scope.InternalArtifactType"

private const val PREVIEW_OUTPUT = "outputs/screenshotTest-results/preview"
private const val PREVIEW_INTERMEDIATES = "intermediates/preview"
private const val PREVIEW_REPORTS = "reports/screenshotTest/preview"
