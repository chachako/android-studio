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

package com.android.build.gradle.integration.multiplatform.v2

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.AarSubject
import com.android.testutils.apk.Aar
import org.junit.Rule
import org.junit.Test

class KotlinMultiplatformGeneratedSourcesTest {

    private val library = MinimalSubProject.kotlinMultiplatformAndroid("com.mylibrary.foo")

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder().fromTestApp(
        MultiModuleTestProject.builder().subproject(":library", library).build()
    ).withKotlinGradlePlugin(true).create()

    @Test
    fun testGeneratedKotlinSources() {

        project.getSubproject(":library").buildFile.appendText(
            """
                 abstract class GenerateJavaRes extends DefaultTask {
                  @OutputDirectory
                  abstract DirectoryProperty getOutputDir();

                  @TaskAction
                  void taskAction() {
                    File d = getOutputDir().asFile.get();
                    d.mkdirs();
                    new File(d, "res.txt").createNewFile()
                  }
                }
                TaskProvider<GenerateJavaRes> generateJavaRes = tasks.register("generateJavaRes", GenerateJavaRes.class)
                generateJavaRes.configure {
                  it.getOutputDir().set(project.layout.buildDirectory.dir("generated/javaRes"))
                }

                kotlin {
                  sourceSets.androidMain.resources.srcDir(generateJavaRes.map { it.getOutputDir() })
                }
            """.trimIndent()
        )

        project.executor().run(":library:assembleAndroidMain")

        Aar(project.getSubproject("library").getOutputFile("aar", "library.aar")).use {
            AarSubject.assertThat(it).containsJavaResource("res.txt")
        }
    }
}
