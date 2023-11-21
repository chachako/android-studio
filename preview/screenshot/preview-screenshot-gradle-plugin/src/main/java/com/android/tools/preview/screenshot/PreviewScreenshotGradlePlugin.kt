package com.android.tools.preview.screenshot

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.api.AndroidBasePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

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
        }
    }
}
