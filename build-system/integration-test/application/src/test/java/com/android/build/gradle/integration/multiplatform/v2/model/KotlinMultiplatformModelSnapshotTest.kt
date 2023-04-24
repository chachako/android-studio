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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.fixture.model.BaseModelComparator
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.TestInputsGenerator
import com.android.testutils.generateAarWithContent
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class KotlinMultiplatformModelSnapshotTest: BaseModelComparator {
    @Suppress("DEPRECATION") // kmp doesn't support configuration caching for now (b/276472789)
    @get:Rule
    val project = GradleTestProjectBuilder()
        .fromTestProject("kotlinMultiplatform")
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
        .create()

    @Before
    fun setUp() {
        // Add a local aar
        FileUtils.join(
            project.getSubproject("kmpFirstLib").projectDir,
            "libs",
            "local.aar"
        ).apply {
            parentFile.mkdir()
            writeBytes(
                generateAarWithContent(
                    packageName = "com.example.aar",
                    mainJar = TestInputsGenerator.jarWithEmptyClasses(ImmutableList.of("com/example/aar/AarClass")),
                    resources = mapOf("values/strings.xml" to """<resources><string name="aar_string">Aar String</string></resources>""".toByteArray())
                )
            )
        }

        // Add a local jar
        FileUtils.join(
            project.getSubproject("kmpFirstLib").projectDir,
            "libs",
            "local.jar"
        ).writeBytes(
            TestInputsGenerator.jarWithEmptyClasses(ImmutableList.of("com/example/jar/JarClass"))
        )

        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin.sourceSets.getByName("androidMain").dependencies {
                    implementation(files("libs/local.aar", "libs/local.jar"))
                }
            """.trimIndent()
        )
    }

    @Test
    fun testModels() {
        KmpModelComparator(
            project = project,
            testClass = this,
            modelSnapshotTask = "dumpSourceSetDependencies",
            taskOutputLocator = { projectPath ->
                FileUtils.join(
                    project.getSubproject(projectPath).buildDir,
                    "ide",
                    "dependencies",
                    "json"
                )
            }
        ).fetchAndCompareModels(
            listOf(":kmpFirstLib", ":kmpSecondLib")
        )
    }
}
