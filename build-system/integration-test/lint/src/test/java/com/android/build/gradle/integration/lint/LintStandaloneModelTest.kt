/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test

class LintStandaloneModelTest {

    private val javaToolchain = """
        java {
            toolchain {
                languageVersion = JavaLanguageVersion.of(17)
            }
        }
    """.trimIndent()

    private val javaLib1 =
        MinimalSubProject.javaLibrary()
            .appendToBuild(
                """
                    apply plugin: 'com.android.lint'

                    $javaToolchain

                    dependencies {
                        compileOnly 'com.google.guava:guava:19.0'
                        testImplementation 'junit:junit:4.12'
                        implementation 'com.android.support:appcompat-v7:${SUPPORT_LIB_VERSION}'
                    }
                """.trimIndent()
            )

    private val javaLib2 = MinimalSubProject.javaLibrary().appendToBuild(javaToolchain)
    private val javaLib3 = MinimalSubProject.javaLibrary().appendToBuild(javaToolchain)

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .withName("project")
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":java-lib1", javaLib1)
                    .subproject(":java-lib2", javaLib2)
                    .subproject(":java-lib3", javaLib3)
                    .dependency("implementation", javaLib1, javaLib2)
                    .dependency("compileOnly", javaLib1, javaLib3)
                    .build()
            )
            .create()

    @Test
    fun testLintModel() {
        project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .with(BooleanOption.LINT_ANALYSIS_PER_COMPONENT, false)
            .run("clean", ":java-lib1:lint")

        checkLintModels(
            project = project,
            lintModelDir = FileUtils.join(
                project.getSubproject("java-lib1").intermediatesDir,
                "lintAnalyzeJvm",
                "android-lint-model"
            ).toPath(),
            modelSnapshotResourceRelativePath = "standalone/perComponent_false/javalib/lintAnalyzeJvm",
            "main-artifact-dependencies.xml",
            "main-artifact-libraries.xml",
            "main-testArtifact-dependencies.xml",
            "main-testArtifact-libraries.xml",
            "main.xml",
            "module.xml",
        )
    }

    @Test
    fun testLintModelWithPerComponentAnalysis() {
        project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .with(BooleanOption.LINT_ANALYSIS_PER_COMPONENT, true)
            .run("clean", ":java-lib1:lint")

        checkLintModels(
            project = project,
            lintModelDir = FileUtils.join(
                project.getSubproject("java-lib1").intermediatesDir,
                "lintAnalyzeJvmMain",
                "android-lint-model"
            ).toPath(),
            modelSnapshotResourceRelativePath = "standalone/perComponent_true/javalib/lintAnalyzeJvmMain",
            "main-artifact-dependencies.xml",
            "main-artifact-libraries.xml",
            "main.xml",
            "module.xml",
        )

        checkLintModels(
            project = project,
            lintModelDir = FileUtils.join(
                project.getSubproject("java-lib1").intermediatesDir,
                "lintAnalyzeJvmTest",
                "android-lint-model"
            ).toPath(),
            modelSnapshotResourceRelativePath = "standalone/perComponent_true/javalib/lintAnalyzeJvmTest",
            "main-artifact-dependencies.xml",
            "main-artifact-libraries.xml",
            "main.xml",
            "module.xml",
        )
    }
}
