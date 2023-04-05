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
import com.android.build.gradle.integration.common.fixture.ModelContainerV2
import com.android.build.gradle.integration.common.fixture.model.BaseModelComparator
import com.android.build.gradle.integration.common.fixture.model.BasicComparator
import com.android.utils.FileUtils
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

class KmpModelComparator(
    testClass: BaseModelComparator,
    private val project: GradleTestProject,
): BasicComparator(testClass) {

    fun fetchAndCompareModelForProject(
        projectPath: String,
        buildMap: Map<String, ModelContainerV2.BuildInfo>,
    ) {
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

        outputFolder.listFiles()!!.forEach { jsonReport ->
            val sourceSetName = jsonReport.name.removeSuffix(DOT_JSON)

            val content = normalizer.normalize(
                jsonReport.inputStream().buffered().reader().use {
                    JsonParser.parseReader(it)
                }
            ).let {
                gson.toJson(it)
            }.also {
                generateStdoutHeader(normalizer)
                println(it)
            }

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
