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

    var classLoaderDependencies = initClassLoaderDependencies(project,rootLintModule)
    var systemDependencies = initSystemDependencies(project,rootLintModule)
    private fun initSystemDependencies(project: Project, rootLintModule: LintModelModule): List<String> {
        val dependencies = mutableListOf<String>()
        dependencies.addAll(rootLintModule.defaultVariant()!!.mainArtifact.classOutputs.filter { it.extension == "jar" }.map { it.absolutePath })
        dependencies.addAll(project.buildVariant!!
                                .androidTestArtifact!!.dependencies.packageDependencies
                                .getAllLibraries()
                                .filterIsInstance<LintModelExternalLibrary>()
                                .flatMap{it.jarFiles}
                                .map { it.absolutePath })
        // This gets reversed in the ScreenshotAndroidModuleSystem as it does its caching.
        return dependencies.reversed()
    }

    private fun initClassLoaderDependencies(
        project: Project,
        rootLintModule: LintModelModule
    ): List<Path> {
        val trans = mutableListOf<Path>()
        trans.addAll(FileUtils.listFiles(File(project.buildTarget?.location!!), arrayOf("jar"), true).map { it.toPath() })
        trans.addAll(rootLintModule.defaultVariant()!!.mainArtifact.classOutputs.filter { it.extension == "jar" }.map { it.toPath() })
        trans.addAll(project.buildVariant!!
            .mainArtifact.dependencies.packageDependencies
            .getAllLibraries()
            .filterIsInstance<LintModelExternalLibrary>()
            .flatMap{it.jarFiles}
            .map { it.toPath() })
        trans.addAll(project.buildVariant!!.mainArtifact.classOutputs.filter { it.extension == "jar" }.map { it.toPath() })
        return trans
    }
}
