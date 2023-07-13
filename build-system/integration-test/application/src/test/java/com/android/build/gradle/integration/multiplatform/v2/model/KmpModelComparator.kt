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
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.FileNormalizerImpl
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.model.BaseModelComparator
import com.android.build.gradle.integration.common.fixture.model.BasicComparator
import com.android.build.gradle.integration.common.fixture.model.normaliseCompileTarget
import com.android.build.gradle.integration.common.fixture.model.normalizeAgpVersion
import com.android.build.gradle.integration.common.fixture.model.normalizeBuildToolsVersion
import com.android.build.gradle.integration.multiplatform.v2.getBuildMap
import com.android.testutils.TestUtils
import com.google.common.truth.Truth
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.io.File

class KmpModelComparator(
    testClass: BaseModelComparator,
    private val project: GradleTestProject,
    private val modelSnapshotTask: String,
    private val taskOutputLocator: (String) -> File
): BasicComparator(testClass) {

    private val buildMap = project.getBuildMap()

    // TODO (b/293964676): remove withFailOnWarning(false) once KMP bug is fixed
    private fun fetchModels(
        projectPath: String,
        printModelToStdout: Boolean = true,
        failOnWarning: Boolean = true
    ): Map<String, String> {
        val executor = project.executor()
            .withFailOnWarning(failOnWarning)
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
        executor.run("$projectPath:$modelSnapshotTask")

        val outputFolder = taskOutputLocator(projectPath)

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
        ) {
            val normalizedString = normalizeBuildToolsVersion(
                normaliseCompileTarget(it).toString()
            ).toString()

            // the normalizer doesn't cover the modules-2 files, since the path contains the library
            // itself, we just override it here.
            if (normalizedString.startsWith(
                    "{GRADLE}/caches/modules-2/files-2.1/com.example/kmpSecondLib-android/1.0/"
                )) {
                "{GRADLE_CACHE}/{MODULES_2}/{LIBRARY_COORDINATES}/{CHECKSUM}" + normalizedString.removePrefix(
                    "{GRADLE}/caches/modules-2/files-2.1/com.example/kmpSecondLib-android/1.0/"
                ).substring(40) // remove the hash
            } else {
                normalizedString
            }
        }

        val gson = GsonBuilder().setPrettyPrinting().create()

        return outputFolder.listFiles()!!.associate { jsonReport ->
            val reportName = jsonReport.name.removeSuffix(DOT_JSON)

            val content = normalizer.normalize(
                jsonReport.inputStream().buffered().reader().use {
                    JsonParser.parseReader(it)
                }
            ).let {
                gson.toJson(it).normalizeAgpVersion()
            }.also {
                if (printModelToStdout) {
                    generateStdoutHeader(normalizer)
                    println(it)
                }
            }

            reportName to content
        }
    }

    // TODO (b/293964676): remove withFailOnWarning(false) once KMP bug is fixed
    fun fetchAndCompareModels(
        projects: List<String>,
        failOnWarning: Boolean = true
    ) {
        projects.forEach { projectPath ->
            fetchModels(projectPath, failOnWarning=failOnWarning).forEach { (reportName, content) ->
                runComparison(
                    name = projectPath.substringAfterLast(":") + "/" + reportName,
                    actualContent = content,
                    goldenFile = projectPath
                        .removePrefix(":")
                        .replace(':', '_') + "_" + reportName
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

            projectBaseModels.keys.forEach { reportName ->
                val baseModel = projectBaseModels[reportName]!!
                val changedModel = projectChangedModels[reportName]!!

                runComparison(
                    name = projectPath.substringAfterLast(":") + "/" + reportName,
                    actualContent = TestUtils.getDiff(
                        baseModel,
                        changedModel
                    ),
                    goldenFile = projectPath
                        .removePrefix(":")
                        .replace(':', '_') + "_" + reportName
                )
            }
        }
    }
}
