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
import java.util.Locale

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

            componentsExtension.onVariants { variant ->
                if (variant is HasAndroidTest) {
                    val variantName = variant.name
                    val flavor: String? = variant.flavorName
                    val buildTarget: String = if (flavor == null) {
                        variantName
                    } else {
                        // build target is the variant with the flavor name stripped from the front.
                        variantName.substring(flavor.length).lowercase(Locale.US)
                    }
                    val flavorDir = if (flavor.isNullOrEmpty()) "" else "flavors/$flavor"
                    val referenceImageDir =
                        File("${project.projectDir.absolutePath}/src/androidTest/screenshot/$buildTarget/$flavorDir")
                    val resultsDir =
                        File("${project.projectDir.absolutePath}/build/outputs/androidTest-results/connected/$buildTarget/$flavorDir")
                    Files.createDirectories(referenceImageDir.toPath())
                    Files.createDirectories(resultsDir.toPath())
                    val discoveryTaskProvider =
                        project.tasks.register(
                            "${variantName}PreviewDiscovery",
                            PreviewDiscoveryTask::class.java
                        ) { task ->
                            // TODO determine where this should be saved
                            task.previewsOutputFile.set(File("${project.projectDir.absolutePath}/build/outputs/androidTest-results/connected/$buildTarget/$flavorDir/previews_discovered.json"))
                            task.previewsOutputFile.disallowChanges()

                        }
                    val previewDiscoveryTask: PreviewDiscoveryTask = discoveryTaskProvider.get()
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
