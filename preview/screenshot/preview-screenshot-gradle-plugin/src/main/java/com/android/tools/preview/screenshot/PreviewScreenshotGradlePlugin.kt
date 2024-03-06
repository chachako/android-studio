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

package com.android.tools.preview.screenshot

import com.android.SdkConstants
import com.android.Version
import com.android.build.api.AndroidPluginVersion
import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.HasDeviceTests
import com.android.build.api.variant.HasUnitTest
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.Variant
import com.android.build.gradle.api.AndroidBasePlugin
import com.android.tools.preview.screenshot.layoutlibExtractor.LayoutlibFromMaven
import com.android.tools.preview.screenshot.services.AnalyticsService
import com.android.tools.preview.screenshot.tasks.PreviewDiscoveryTask
import com.android.tools.preview.screenshot.tasks.PreviewScreenshotRenderTask
import com.android.tools.preview.screenshot.tasks.PreviewScreenshotUpdateTask
import com.android.tools.preview.screenshot.tasks.PreviewScreenshotValidationTask
import com.android.tools.preview.screenshot.tasks.ScreenshotTestReportTask
import java.lang.StringBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.jvm.toolchain.JavaToolchainService

/**
 * An entry point for Screenshot plugin that adds support for screenshot testing on Compose Previews
 */
class PreviewScreenshotGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.withType(AndroidBasePlugin::class.java) {
            val componentsExtension = project.extensions.getByType(AndroidComponentsExtension::class.java)
            val agpVersion = componentsExtension.pluginVersion
            if (agpVersion < AndroidPluginVersion(8, 4, 0).alpha(9)) {
                error("Android Gradle plugin version 8.4.0-alpha09 or higher is required." +
                        " Current version is $agpVersion.")
            }
            if (agpVersion >= AndroidPluginVersion(8,5).alpha(1)
                && agpVersion.previewType != "dev") {
                error("Preview screenshot plugin is an experimental feature. It requires Android " +
                        "Gradle plugin version 8.4 (alpha09+). Current version is $agpVersion.")
            }

            val analyticsServiceProvider = project.gradle.sharedServices.registerIfAbsent(
                AnalyticsService::class.java.canonicalName,
                AnalyticsService::class.java) { spec ->
                spec.parameters.androidGradlePluginVersion.set(agpVersion.toVersionString())
            }

            val sdkDirectory = componentsExtension.sdkComponents.sdkDirectory
            createPreviewlibCliToolConfiguration(project)
            val layoutlibFromMaven = LayoutlibFromMaven.create(project)

            val updateAllTask = project.tasks.register(
                "previewScreenshotUpdateAndroidTest",
                Task::class.java
            ) { task ->
                task.description = "Update screenshots for all variants."
                task.group = JavaBasePlugin.VERIFICATION_GROUP
            }

            val validateAllTask = project.tasks.register(
                "previewScreenshotAndroidTest",
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

            componentsExtension.onVariants { variant ->
                if (variant is HasDeviceTests && variant.debuggable) {
                    val variantName = variant.name

                    val discoveryTaskProvider =
                        project.tasks.register(
                            "${variantName}PreviewDiscovery",
                            PreviewDiscoveryTask::class.java
                        ) { task ->
                            val variantSegments = variant.computePathSegments()
                            task.previewsOutputFile.set(buildDir.file("$PREVIEW_INTERMEDIATES/$variantSegments/previews_discovered.json"))
                            task.previewsOutputFile.disallowChanges()
                            task.resultsDir.set(buildDir.dir("$PREVIEW_OUTPUT/$variantSegments"))
                            task.referenceImageDir.set(project.layout.projectDirectory.dir("src/androidTest/screenshot/$variantSegments"))
                            task.analyticsService.set(analyticsServiceProvider)
                            task.usesService(analyticsServiceProvider)
                        }
                    variant.deviceTests.singleOrNull()?.artifacts
                        ?.forScope(ScopedArtifacts.Scope.ALL)
                        ?.use(discoveryTaskProvider)
                        ?.toGet(
                            ScopedArtifact.CLASSES,
                            PreviewDiscoveryTask::testJars,
                            PreviewDiscoveryTask::testClassesDir,
                        )
                    variant.artifacts
                        .forScope(ScopedArtifacts.Scope.ALL)
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
                    val processedResClass = classLoader.loadClass("${INTERNAL_ARTIFACT_TYPE}\$PROCESSED_RES")
                    val apkForLocalTestClass = classLoader.loadClass("${INTERNAL_ARTIFACT_TYPE}\$APK_FOR_LOCAL_TEST")
                    val artifactsImplGet = artifactsImplClass.getDeclaredMethod("get", Artifact.Single::class.java)
                    val resourceDirProvider = if (variant is ApplicationVariant) {
                        val artifacts = variant.artifacts
                        val artifactImplObject = when {
                            artifactsImplClass.isInstance(artifacts) -> artifacts
                            analyticsEnabledArtifactsClass.isInstance(artifacts) -> analyticsEnabledArtifactsGetDelegateMethod.invoke(artifacts)
                            else -> throw RuntimeException("Unexpected artifact type ${artifacts.javaClass}")
                        }
                        val instance = processedResClass.getField("INSTANCE").get(null)
                        // ArtifactsImpl::get(InternalArtifactType.PROCESSED_RES)
                        @Suppress("UNCHECKED_CAST")
                        artifactsImplGet.invoke(artifactImplObject, instance) as? Provider<Directory>
                    } else null
                    val resourceFileProvider = if (variant is LibraryVariant) {
                        val artifacts = (variant as HasUnitTest).unitTest!!.artifacts
                        val artifactImplObject: Any = when {
                            artifactsImplClass.isInstance(artifacts) -> artifacts
                            analyticsEnabledArtifactsClass.isInstance(artifacts) -> analyticsEnabledArtifactsGetDelegateMethod.invoke(artifacts)
                            else -> throw RuntimeException("Unexpected artifact type ${artifacts.javaClass}")
                        }
                        val instance = apkForLocalTestClass.getField("INSTANCE").get(null)
                        // ArtifactsImpl::get(InternalArtifactType.APK_FOR_LOCAL_TEST)
                        @Suppress("UNCHECKED_CAST")
                        artifactsImplGet.invoke(artifactImplObject, instance) as? Provider<RegularFile>
                    } else null

                    val renderTaskProvider = project.tasks.register(
                        "${variantName}PreviewScreenshotRender",
                        PreviewScreenshotRenderTask::class.java
                    ) { task ->
                        val variantSegments = variant.computePathSegments()
                        task.outputDir.set(buildDir.dir("$PREVIEW_OUTPUT/$variantSegments/rendered"))

                        // need to use project.providers as a workaround to gradle issue: https://github.com/gradle/gradle/issues/12388
                        task.sdkFontsDir.set(project.providers.provider {
                            val subDir = sdkDirectory.get().asFile.resolve(SdkConstants.SDK_DL_FONTS_FOLDER)
                            if (subDir.exists()) sdkDirectory.get().dir(SdkConstants.SDK_DL_FONTS_FOLDER) else null
                        })
                        task.previewsDiscovered.set(discoveryTaskProvider.flatMap { it.previewsOutputFile })
                        task.screenshotCliJar.from(task.project.configurations.getByName(previewlibCliToolConfigurationName))
                        task.layoutlibDir.setFrom(layoutlibFromMaven.layoutlibDirectory)
                        resourceDirProvider?.let { task.resourcesDir.set(it) }
                        resourceFileProvider?.let { task.resourceFile.set(it) }

                        task.packageName.set(variant.namespace)
                        task.cliToolArgumentsFile.set(buildDir.file("$PREVIEW_INTERMEDIATES/$variantSegments/cli_tool_arguments.json"))

                        val toolchain = project.extensions.getByType(JavaPluginExtension::class.java).toolchain
                        val service = project.extensions.getByType(JavaToolchainService::class.java)
                        task.javaLauncher.set(service.launcherFor(toolchain))

                        task.analyticsService.set(analyticsServiceProvider)
                        task.usesService(analyticsServiceProvider)
                    }

                    variant.artifacts
                        .forScope(ScopedArtifacts.Scope.ALL)
                        .use(renderTaskProvider)
                        .toGet(
                            ScopedArtifact.CLASSES,
                            PreviewScreenshotRenderTask::mainClasspath,
                            PreviewScreenshotRenderTask::mainClassesDir,
                        )

                    variant.deviceTests.singleOrNull()?.artifacts
                        ?.forScope(ScopedArtifacts.Scope.ALL)
                        ?.use(renderTaskProvider)
                        ?.toGet(
                            ScopedArtifact.CLASSES,
                            PreviewScreenshotRenderTask::testClasspath,
                            PreviewScreenshotRenderTask::testClassesDir,
                        )

                    val updateTask = project.tasks.register(
                        "previewScreenshotUpdate${variantName.capitalized()}AndroidTest",
                        PreviewScreenshotUpdateTask::class.java
                    ) { task ->
                        val variantSegments = variant.computePathSegments()
                        task.referenceImageDir.set(project.layout.projectDirectory.dir("src/androidTest/screenshot/$variantSegments"))
                        task.renderTaskOutputDir.set(renderTaskProvider.flatMap { it.outputDir })
                        task.description = "Update screenshots for the $variantName build."
                        task.group = JavaBasePlugin.VERIFICATION_GROUP
                        task.analyticsService.set(analyticsServiceProvider)
                        task.usesService(analyticsServiceProvider)
                    }
                    updateAllTask.configure { it.dependsOn(updateTask) }

                    val previewScreenshotValidationTask = project.tasks.register(
                        "previewScreenshot${variantName.capitalized()}AndroidTest",
                        PreviewScreenshotValidationTask::class.java
                    ) { task ->
                        val variantSegments = variant.computePathSegments()
                        task.referenceImageDir.set(project.layout.projectDirectory.dir("src/androidTest/screenshot/$variantSegments"))
                        task.referenceImageDir.disallowChanges()
                        task.previewFile.set(buildDir.file("$PREVIEW_INTERMEDIATES/$variantSegments/previews_discovered.json"))
                        task.renderTaskOutputDir.set(renderTaskProvider.flatMap { it.outputDir })
                        task.resultsDir.set(buildDir.dir("$PREVIEW_OUTPUT/$variantSegments/results"))
                        task.diffImageDir.set(buildDir.dir("$PREVIEW_OUTPUT/$variantSegments/diffs"))
                        task.diffImageDir.disallowChanges()
                        task.reportFilePath.set(buildDir.dir("$PREVIEW_REPORTS/$variantSegments"))
                        task.reportFilePath.disallowChanges()
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
                        task.testClassesDirs = project.files(renderTaskProvider.flatMap { it.testClassesDir }) + project.files(renderTaskProvider.flatMap { it.testClasspath })
                        task.classpath = task.project.configurations.getByName(previewScreenshotTestEngineConfigurationName) + task.testClassesDirs
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
                description = "A configuration to resolve preview screenshot test engine dependencies."
            }
            val engineVersion = "0.0.1" +
                    if (Version.ANDROID_GRADLE_PLUGIN_VERSION.endsWith("-dev"))
                        "-dev"
                    else
                        "-eap01"
            dependencies.add(
                previewScreenshotTestEngineConfigurationName,
                "com.android.tools.preview.screenshot.junit.engine:junit-engine:${engineVersion}")
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
            val version = "0.0.1" +
                    if (Version.ANDROID_GRADLE_PLUGIN_VERSION.endsWith("-dev"))
                        "-dev"
                    else
                        "-alpha01"
            dependencies.add(
                previewlibCliToolConfigurationName,
                "com.android.tools:standalone-render.compose-cli:$version")
        }
    }

    private fun AndroidPluginVersion.toVersionString(): String {
        val builder = StringBuilder("$major.$minor.$micro")
        previewType?.let { builder.append("-$it")}
        if (preview > 0) {
            builder.append(preview.toString().padStart(2, '0'))
        }
        return builder.toString()
    }
}

private const val previewlibCliToolConfigurationName = "_internal-screenshot-test-task-previewlib-cli"
private const val previewScreenshotTestEngineConfigurationName = "_internal-preview-screenshot-test-engine"
private const val ARTIFACT_IMPL = "com.android.build.api.artifact.impl.ArtifactsImpl"
private const val ANALYTICS_ENABLED_ARTIFACTS = "com.android.build.api.component.analytics.AnalyticsEnabledArtifacts"
private const val INTERNAL_ARTIFACT_TYPE = "com.android.build.gradle.internal.scope.InternalArtifactType"

private const val PREVIEW_OUTPUT = "outputs/androidTest-results/preview"
private const val PREVIEW_INTERMEDIATES = "intermediates/preview"
private const val PREVIEW_REPORTS = "reports/androidTests/preview"

