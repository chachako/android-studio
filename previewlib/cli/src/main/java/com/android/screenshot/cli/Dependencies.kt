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
import com.android.tools.lint.model.LintModelModule
import java.nio.file.Path

/**
 * This class manages a list of project dependencies.
 */
class Dependencies(project: Project, rootLintModule: LintModelModule) {

    var classLoaderDependencies = initClassLoaderDependencies(project, rootLintModule)
    var systemDependencies = initSystemDependencies(project)
    private fun initSystemDependencies(project: Project): List<String> {
        val dependencies = mutableListOf<String>(
            project.dir.absolutePath,
            project.buildTarget?.location!!,
        )
        dependencies.addAll(project.getJavaLibraries(false).map { it.absolutePath })
        dependencies.addAll(project.testLibraries.map { it.absolutePath })
        return dependencies
    }

    private fun initClassLoaderDependencies(
        project: Project,
        rootLintModule: LintModelModule
    ): List<Path> {
        val trans = arrayListOf<Path>()
        trans.addAll(project.getJavaLibraries(false).map { it.toPath() })
        trans.addAll(project.testLibraries.map { it.toPath() })
        for (path in rootLintModule.variants[0].mainArtifact.classOutputs) {
            if (path.toString().endsWith("R.jar"))
                trans.add(path.toPath())
        }
        return trans
    }
}
