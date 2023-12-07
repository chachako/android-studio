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

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasAndroidTest
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.api.AndroidBasePlugin
import com.android.tools.preview.screenshot.tasks.PreviewDiscoveryTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.nio.file.Files

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

            componentsExtension.onVariants { variant ->
                if (variant is HasAndroidTest) {
                    val variantName = variant.name
                    val flavor: String? = variant.flavorName
                    val buildTarget: String = variant.buildType ?: variantName
                    val flavorDir = if (flavor.isNullOrEmpty()) "" else "flavors/$flavor"
                    val buildDir = project.layout.buildDirectory
                    val testOutputDirPath = "outputs/androidTest-results/connected/$buildTarget/$flavorDir"
                    val resultsDir = buildDir.file(testOutputDirPath)
                    val referenceImageDir =
                        File("${project.projectDir.absolutePath}/src/androidTest/screenshot/$buildTarget/$flavorDir")

                    val discoveryTaskProvider =
                        project.tasks.register(
                            "${variantName}PreviewDiscovery",
                            PreviewDiscoveryTask::class.java
                        ) { task ->
                            task.previewsOutputFile.set(buildDir.file("$testOutputDirPath/intermediates/previews_discovered.json"))
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

                }
            }
        }
    }
}
