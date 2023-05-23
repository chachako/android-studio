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
package com.android.screenshot.cli

import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.model.LintModelExternalLibrary
import com.android.tools.lint.model.LintModelModule
import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * This class manages a list of project dependencies.
 */
class Dependencies(project: Project, rootLintModule: LintModelModule) {
    private val dependencies = initDependencies(project, rootLintModule)
    var systemDependencies = dependencies.reversed() // This gets reversed in the ScreenshotAndroidModuleSystem as it does its caching.
    var classLoaderDependencies = initClassLoaderDependencies(project)

    private fun initDependencies(project: Project, rootLintModule: LintModelModule) : List<String> {
        val deps = mutableListOf<String>()
        deps.addAll(rootLintModule.defaultVariant()!!.mainArtifact.classOutputs.filter { it.extension == "jar" }.map { it.absolutePath })
        deps.addAll(project.buildVariant!!
                                .androidTestArtifact!!.dependencies.packageDependencies
                                .getAllLibraries()
                                .filterIsInstance<LintModelExternalLibrary>()
                                .flatMap{it.jarFiles}
                                .map { it.absolutePath })
        val compileDeps = project.buildVariant!!
            .androidTestArtifact!!.dependencies.compileDependencies
            .getAllLibraries()
            .filterIsInstance<LintModelExternalLibrary>()
            .flatMap{it.jarFiles}
            .map { it.absolutePath }
        deps.addAll(compileDeps)
        return deps.distinct()
    }

    private fun initClassLoaderDependencies(
        project: Project
    ): List<Path> {
        val classLoaderDeps = mutableListOf<Path>()
        classLoaderDeps.addAll(dependencies.map { File(it).toPath() })
        classLoaderDeps.addAll(FileUtils.listFiles(File(project.buildTarget?.location!!), arrayOf("jar"), true).map { it.toPath() })
        classLoaderDeps.addAll(project.buildVariant!!.mainArtifact.classOutputs.filter { it.extension == "jar" }.map { it.toPath() })
        return classLoaderDeps.distinct()
    }
}
