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

package com.android.build.gradle.integration.multiplatform.v2.model

import com.android.SdkConstants.DOT_JSON
import com.android.build.gradle.integration.common.fixture.FileNormalizerImpl
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.model.BaseModelComparator
import com.android.build.gradle.integration.common.fixture.model.BasicComparator
import com.android.build.gradle.integration.multiplatform.v2.getBuildMap
import com.android.testutils.TestUtils
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

class KmpModelComparator(
    testClass: BaseModelComparator,
    private val project: GradleTestProject,
): BasicComparator(testClass) {

    private val buildMap = project.getBuildMap()

    private fun fetchModels(
        projectPath: String,
        printModelToStdout: Boolean = true
    ): Map<String, String> {
        val executor = project.executor()
        executor.run("$projectPath:resolveIdeDependencies")

        val outputFolder =
            FileUtils.join(
                project.getSubproject(projectPath).buildDir,
                "ide",
                "dependencies",
                "json"
            )

        val normalizer = FileNormalizerImpl(
            buildMap = buildMap,
            gradleUserHome = executor.projectLocation.testLocation.gradleUserHome.toFile(),
            gradleCacheDir = executor.projectLocation.testLocation.gradleCacheDir,
            androidSdkDir = project.androidSdkDir,
            androidPrefsDir = executor.preferencesRootDir,
            androidNdkSxSRoot = project.androidNdkSxSRootSymlink,
            localRepos = GradleTestProject.localRepositories,
            additionalMavenRepo = project.additionalMavenRepoDir,
            defaultNdkSideBySideVersion = GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
        )

        val gson = GsonBuilder().setPrettyPrinting().create()

        return outputFolder.listFiles()!!.associate { jsonReport ->
            val sourceSetName = jsonReport.name.removeSuffix(DOT_JSON)

            val content = normalizer.normalize(
                jsonReport.inputStream().buffered().reader().use {
                    JsonParser.parseReader(it)
                }
            ).let {
                gson.toJson(it)
            }.also {
                if (printModelToStdout) {
                    generateStdoutHeader(normalizer)
                    println(it)
                }
            }

            sourceSetName to content
        }
    }

    fun fetchAndCompareModels(
        projects: List<String>,
    ) {
        projects.forEach { projectPath ->
            fetchModels(projectPath).forEach { (sourceSetName, content) ->
                runComparison(
                    name = projectPath.substringAfterLast(":") + "/" + sourceSetName,
                    actualContent = content,
                    goldenFile = projectPath
                        .removePrefix(":")
                        .replace(':', '_') + "_" + sourceSetName
                )
            }
        }
    }

    fun compareModelDeltaAfterChange(
        projects: List<String>,
        action: () -> Unit
    ) {
        val baseModels = projects.associateWith { projectPath ->
            fetchModels(
                projectPath, printModelToStdout = false
            )
        }
        action()
        val changedModels = projects.associateWith { projectPath ->
            fetchModels(
                projectPath, printModelToStdout = false
            )
        }

        projects.forEach { projectPath ->
            val projectBaseModels = baseModels[projectPath]!!
            val projectChangedModels = changedModels[projectPath]!!

            Truth.assertWithMessage(
                "Expected the same sourceSets after change."
            ).that(
                projectBaseModels.keys
            ).containsExactlyElementsIn(projectChangedModels.keys)

            projectBaseModels.keys.forEach { sourceSetName ->
                val baseModel = projectBaseModels[sourceSetName]!!
                val changedModel = projectChangedModels[sourceSetName]!!

                runComparison(
                    name = projectPath.substringAfterLast(":") + "/" + sourceSetName,
                    actualContent = TestUtils.getDiff(
                        baseModel,
                        changedModel
                    ),
                    goldenFile = projectPath
                        .removePrefix(":")
                        .replace(':', '_') + "_" + sourceSetName
                )
            }
        }
    }
}
