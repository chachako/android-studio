package com.buildsrc.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class DumpAndroidTargetPlugin: Plugin<Project> {

    override fun apply(target: Project) {
        target.tasks.register("dumpAndroidTarget", DumpAndroidTargetTask::class.java)
        target.tasks.register("dumpSourceSetDependencies", DumpSourceSetDependenciesTask::class.java)
    }
}
