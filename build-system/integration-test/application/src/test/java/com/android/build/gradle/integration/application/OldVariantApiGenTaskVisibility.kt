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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class OldVariantApiGenTaskVisibility {

    @get:Rule
    val project = GradleTestProject.builder().fromTestProject("genFolderApi").create()

    @Before
    @Throws(Exception::class)
    fun setUp() {

        File(project.projectDir, "old_variant_api.build.gradle")
            .copyTo(project.buildFile)
    }
    @Test
    fun noManifestConfigurationPassesTest() {
        project.buildFile.appendText(
            """
                public abstract class ValidateResFolders extends DefaultTask {
                    @InputFiles
                    abstract ListProperty<Collection<Directory>> getInputFolders()

                    @OutputFile
                    abstract RegularFileProperty getOutputFile()

                    @TaskAction
                    void taskAction() {
                        var text = ""
                        getInputFolders().get().forEach { folders ->
                            folders.forEach { folder ->
                                text = text + folder.getAsFile().getAbsolutePath() + "\n"
                            }
                        }
                        getOutputFile().get().getAsFile().text = text
                    }
                }
                androidComponents {
                    onVariants(selector().all(), { variant ->
                        TaskProvider<ValidateResFolders> taskProvider =
                            project.tasks.register(
                                variant.getName() + "ValidateResFolders",
                                ValidateResFolders.class
                            ) { task ->
                               task.getInputFolders().set(variant.sources.res.all)
                               task.getOutputFile().set(new File(project.buildDir, "result.txt"))
                            }

                    })
                }
            """.trimIndent()
        )
        project.executor()
            .withArgument("-P" + "inject_enable_generate_values_res=true")
            .run("debugValidateResFolders")

        val sep = File.separatorChar
        File(project.buildDir, "result.txt").readText().also {
            Truth.assertThat(it).contains(
                "genFolderApi${sep}build${sep}generated${sep}res${sep}resValues${sep}debug"
            )
        }
    }
}
