package com.buildsrc.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class DumpAndroidTargetPlugin: Plugin<Project> {

    override fun apply(target: Project) {
        target.tasks.register("dumpAndroidTarget", DumpAndroidTargetTask::class.java) { task ->
            task.notCompatibleWithConfigurationCache(
                "DumpAndroidTargetTask is not compatible with configuration caching"
            )
        }
        target.tasks.register("dumpSourceSetDependencies", DumpSourceSetDependenciesTask::class.java) { task ->
            task.notCompatibleWithConfigurationCache(
                "DumpSourceSetDependenciesTask is not compatible with configuration caching"
            )
        }
    }
}
