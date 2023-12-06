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

import com.android.Version
import com.android.build.api.AndroidPluginVersion
import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.HasAndroidTest
import com.android.build.api.variant.HasUnitTest
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.api.AndroidBasePlugin
import com.android.tools.preview.screenshot.layoutlibExtractor.LayoutlibFromMaven
import com.android.tools.preview.screenshot.tasks.PreviewDiscoveryTask
import com.android.tools.preview.screenshot.tasks.PreviewScreenshotRenderTask
import com.android.tools.preview.screenshot.tasks.PreviewScreenshotUpdateTask
import com.android.tools.preview.screenshot.tasks.PreviewScreenshotValidationTask
import com.android.tools.preview.screenshot.tasks.ScreenshotTestReportTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.jvm.toolchain.JavaToolchainService
import java.io.File

/**
 * An entry point for Screenshot plugin that adds support for screenshot testing on Compose Previews
 */
class PreviewScreenshotGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.withType(AndroidBasePlugin::class.java) {
            val agpVersion =
                project.extensions.getByType(AndroidComponentsExtension::class.java).pluginVersion
            if (agpVersion < AndroidPluginVersion(8, 3, 0).alpha(1)) {
                error("Android Gradle plugin version 8.3.0-alpha01 or higher is required." +
                        " Current version is $agpVersion.")
            }
            if (agpVersion >= AndroidPluginVersion(8,4).alpha(1)) {
                error("Preview screenshot plugin is an experimental feature. It requires Android " +
                        "Gradle plugin version 8.3.0. Current version is $agpVersion.")
            }
            val componentsExtension = project.extensions.getByType(AndroidComponentsExtension::class.java)

            val sdkDirectory = componentsExtension.sdkComponents.sdkDirectory
            createPreviewlibCliToolConfiguration(project)
            val layoutlibFromMaven = LayoutlibFromMaven.create(project)
            componentsExtension.onVariants { variant ->
                if (variant is HasAndroidTest) {
                    val variantName = variant.name
                    val flavor: String? = variant.flavorName
                    val buildTarget: String = variant.buildType ?: variantName
                    val flavorDir = if (flavor.isNullOrEmpty()) "" else "flavors/$flavor"
                    val buildDir = project.layout.buildDirectory
                    val testOutputDir = "outputs/androidTest-results/connected/$buildTarget/$flavorDir"
                    val resultsDir = buildDir.file(testOutputDir)
                    val referenceImageDir =
                        File("${project.projectDir.absolutePath}/src/androidTest/screenshot/$buildTarget/$flavorDir")
                    val renderedDir = buildDir.dir("$testOutputDir/rendered")
                    val previewOut = buildDir.file("$testOutputDir/intermediates/previews_discovered.json")
                    val cliInput = buildDir.file("$testOutputDir/intermediates/cli_tool_input.json")
                    val testResultsDir = buildDir.dir("$testOutputDir/results")
                    val testResultsFile = buildDir.file("$testOutputDir/results/TEST-results.xml")

                    val discoveryTaskProvider =
                        project.tasks.register(
                            "${variantName}PreviewDiscovery",
                            PreviewDiscoveryTask::class.java
                        ) { task ->
                            task.previewsOutputFile.set(previewOut)
                            task.previewsOutputFile.disallowChanges()
                            task.resultsDir.set(resultsDir)
                            task.referenceImageDir.set(referenceImageDir)
                        }
                    variant.androidTest?.artifacts
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
                        artifactsImplGet.invoke(artifactImplObject, instance) as Provider<Directory>
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
                        artifactsImplGet.invoke(artifactImplObject, instance) as Provider<RegularFile>
                    } else null

                    val renderTaskProvider = project.tasks.register(
                        "${variantName}PreviewScreenshotRender",
                        PreviewScreenshotRenderTask::class.java
                    ) { task ->

                        task.outputDir.set(renderedDir)
                        task.sdk.set(sdkDirectory)
                        task.previewsDiscovered.set(discoveryTaskProvider.flatMap { it.previewsOutputFile })
                        task.screenshotCliJar.from(task.project.configurations.getByName(previewlibCliToolConfigurationName))
                        task.layoutlibDir.setFrom(layoutlibFromMaven.layoutlibDirectory)
                        resourceDirProvider?.let { task.resourcesDir.set(it) }
                        resourceFileProvider?.let { task.resourceFile.set(it) }

                        task.packageName.set(variant.namespace)
                        task.cliToolInput.set(cliInput)

                        val toolchain = project.extensions.getByType(JavaPluginExtension::class.java).toolchain
                        val service = project.extensions.getByType(JavaToolchainService::class.java)
                        task.javaLauncher.set(service.launcherFor(toolchain))
                    }

                    variant.artifacts
                        .forScope(ScopedArtifacts.Scope.ALL)
                        .use(renderTaskProvider)
                        .toGet(
                            ScopedArtifact.CLASSES,
                            PreviewScreenshotRenderTask::mainClasspath,
                            PreviewScreenshotRenderTask::mainClassesDir,
                        )

                    variant.androidTest?.artifacts
                        ?.forScope(ScopedArtifacts.Scope.ALL)
                        ?.use(renderTaskProvider)
                        ?.toGet(
                            ScopedArtifact.CLASSES,
                            PreviewScreenshotRenderTask::testClasspath,
                            PreviewScreenshotRenderTask::testClassesDir,
                        )

                    project.tasks.register(
                        "previewScreenshotUpdate${variantName}AndroidTest",
                        PreviewScreenshotUpdateTask::class.java
                    ) { task ->
                        task.referenceImageDir.set(referenceImageDir)
                        task.previewFile.set(previewOut)
                        task.renderTaskOutputDir.set(renderTaskProvider.flatMap { it.outputDir })
                        task.description = "Update screenshots for the $variantName build."
                    }

                    val previewScreenshotValidationTask = project.tasks.register(
                        "previewScreenshot${variantName.capitalized()}AndroidTest",
                        PreviewScreenshotValidationTask::class.java
                    ) { task ->
                        task.referenceImageDir.set(referenceImageDir)
                        task.referenceImageDir.disallowChanges()
                        task.previewFile.set(previewOut)
                        task.renderTaskOutputDir.set(renderTaskProvider.flatMap { it.outputDir })
                        task.resultsFile.set(testResultsFile)
                        task.imageOutputDir.set(renderedDir)
                        task.imageOutputDir.disallowChanges()
                        task.description = "Run screenshot tests for the " + variantName + " build."
                        task.group = JavaBasePlugin.VERIFICATION_GROUP
                    }.get()

                    val screenshotHtmlTask = project.tasks.register(
                        "${variantName}ScreenshotReport",
                        ScreenshotTestReportTask::class.java
                    ) { task ->
                        task.outputDir.set(testResultsDir)
                        task.resultsDir.set(testResultsDir)
                    }
                    previewScreenshotValidationTask.finalizedBy(screenshotHtmlTask)
                }
            }
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

    companion object {
        const val previewlibCliToolConfigurationName = "_internal-screenshot-test-task-previewlib-cli"
        private const val ARTIFACT_IMPL = "com.android.build.api.artifact.impl.ArtifactsImpl"
        private const val ANALYTICS_ENABLED_ARTIFACTS = "com.android.build.api.component.analytics.AnalyticsEnabledArtifacts"
        private const val INTERNAL_ARTIFACT_TYPE = "com.android.build.gradle.internal.scope.InternalArtifactType"
    }
}
