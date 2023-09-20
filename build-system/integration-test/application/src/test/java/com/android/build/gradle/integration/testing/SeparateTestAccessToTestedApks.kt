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

package com.android.build.gradle.integration.testing

import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SeparateTestAccessToTestedApks {

    @get:Rule
    var project = builder().fromTestProject("separateTestModule").create()

    @Before
    fun setUp() {
        project.getSubproject(":test").buildFile.appendText(
            """
                import org.gradle.api.DefaultTask
                import org.gradle.api.file.DirectoryProperty
                import org.gradle.api.tasks.InputFiles
                import org.gradle.api.tasks.TaskAction
                import com.android.build.api.artifact.SingleArtifact
                import com.android.build.api.variant.BuiltArtifactsLoader
                import com.android.build.api.variant.BuiltArtifacts
                import org.gradle.api.provider.Property
                import org.gradle.api.tasks.Internal

                abstract class DisplayApksTask extends DefaultTask {

                    @InputFiles
                    abstract DirectoryProperty getApkFolder()

                    @Internal
                    abstract Property<BuiltArtifactsLoader> getBuiltArtifactsLoader()

                    @TaskAction
                    void taskAction() {

                        BuiltArtifacts artifacts = getBuiltArtifactsLoader().get().load(getApkFolder().get())
                        if (artifacts == null) {
                            throw new RuntimeException("Cannot load APKs")
                        }
                        artifacts.elements.forEach {
                            println("Got an APK at ${ '$' }{it.outputFile}")
                        }
                    }
                }

                androidComponents.onVariants(androidComponents.selector().all(), { variant ->
                    project.tasks.register(variant.getName() + "DisplayApks", DisplayApksTask.class) {
                        it.apkFolder.set(variant.testedApks)
                        it.builtArtifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
                    }
                })
            """.trimIndent()
        )
    }

    @Test
    fun build() {
        project.execute(":test:debugDisplayApks")
        Truth.assertThat(
            project.buildResult.didWorkTasks.contains(":test:debugDisplayApks")
        ).isTrue()
        Truth.assertThat(
            project.buildResult.stdout.findAll("app-debug.apk").count()
        ).isEqualTo(1)
    }
}
