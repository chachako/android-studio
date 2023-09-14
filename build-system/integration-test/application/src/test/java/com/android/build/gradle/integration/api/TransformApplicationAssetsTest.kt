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

package com.android.build.gradle.integration.api

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test

/**
 * The same transformation does not work for library as library does not do global resource merge,
 * so it does not produce ASSETS artifact
 */
class TransformApplicationAssetsTest {

    @get: Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Test
    fun transformAssetInLibrary() {
        project.settingsFile.appendText(
            """
                buildscript.dependencies{
                    classpath 'commons-io:commons-io:2.4'
                }
            """.trimIndent()
        )
        project.buildFile.appendText(
            """
        import org.apache.commons.io.FileUtils
        import com.android.build.api.artifact.ScopedArtifact
        import com.android.build.api.artifact.SingleArtifact
        import com.android.build.api.variant.ScopedArtifacts.Scope

        abstract class AppendAssets extends DefaultTask {

            @Input
            abstract Property<String> getActivityName()

            @InputDirectory
            abstract DirectoryProperty getAssetsFolder()

            @OutputDirectory
            abstract DirectoryProperty getUpdatedAssetsFolder()

            @TaskAction
            void taskAction() {
                def src = assetsFolder.get().asFile
                def dest = updatedAssetsFolder.get().asFile
                try {
                    FileUtils.copyDirectory(src, dest);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("In: " + src)
                System.out.println("Out: " + dest)
                new File(dest,"ejson.json") << "{ \"key\": \"8437ycn95y73o947c3b\"}"
            }
        }

        androidComponents {
            onVariants(selector().all(), { variant ->
                TaskProvider copyEjsonKeyTask = tasks.register("append" + variant.name + "Ejson", AppendAssets.class){
                    getActivityName().set("ManuallyAdded")
                }

                variant
                    .artifacts
                    .use(copyEjsonKeyTask)
                    .wiredWithDirectories(
                        { it.getAssetsFolder() },
                        { it.getUpdatedAssetsFolder() }
                    )
                    .toTransform(SingleArtifact.ASSETS.INSTANCE)

            })
        }
        """.trimIndent()
        )

        val result = project.executor().run("appendDebugEjson")

        ScannerSubject.assertThat(result.stdout)
            .contains(FileUtils.join(
                    "intermediates",
                    "assets",
                    "debug",
                    "mergeDebugAssets" //in
                )
            )

        ScannerSubject.assertThat(result.stdout)
            .contains(FileUtils.join("intermediates", "assets", "debug", "appenddebugEjson")) //out
    }
}
