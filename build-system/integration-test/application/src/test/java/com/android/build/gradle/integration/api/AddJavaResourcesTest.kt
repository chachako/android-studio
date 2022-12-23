/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.integration.api

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import org.junit.Rule
import org.junit.Test

class AddJavaResourcesTest {

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create()

    /** Regression test for http://b/263469991.*/
    @Test
    fun testAddingJavaResources() {
        project.buildFile.appendText(
            """
            abstract class VersionFileWriterTask extends DefaultTask {
                @OutputDirectory
                abstract DirectoryProperty getOutputDirectory();

                @TaskAction
                void run() { }
            }

            def writeVersionFile = tasks.register("writeVersionFile", VersionFileWriterTask.class)
            androidComponents {
                onVariants(selector().all(),  { variant ->
                    variant.sources.resources.addGeneratedSourceDirectory(writeVersionFile, VersionFileWriterTask::getOutputDirectory)
                })
            }
        """.trimIndent()
        )

        project.executor().run("processDebugJavaRes")
    }

    /** Regression test for http://b/263469991.*/
    @Test
    fun testAddingJavaResourcesOldApi() {
        project.buildFile.appendText(
            """

            abstract class VersionFileWriterTask extends DefaultTask {
                @OutputDirectory
                abstract DirectoryProperty getOutputDirectory();

                @TaskAction
                void run() { }
            }

            def writeVersionFile = tasks.register("writeVersionFile", VersionFileWriterTask.class)
            writeVersionFile.configure { it.getOutputDirectory().fileValue(new File("build/foo")) }
            android.applicationVariants.all(variant -> {
                if (variant.name == "debug") {
                    Provider<Directory> outputDir = writeVersionFile.flatMap(VersionFileWriterTask::getOutputDirectory)
                    android.sourceSets.getByName(variant.getName()).getResources().srcDir(outputDir)
                }
            });
        """.trimIndent()
        )

        project.executor().run("processDebugJavaRes")
    }

}
