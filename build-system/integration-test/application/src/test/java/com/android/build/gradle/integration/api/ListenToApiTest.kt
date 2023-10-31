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
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.google.common.truth.Truth
import junit.framework.TestCase.fail
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

class ListenToApiTest {

    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Test
    fun singleRegularFileListener() {
        project.buildFile.appendText(
            """
                import org.gradle.api.DefaultTask
                import org.gradle.api.file.DirectoryProperty
                import org.gradle.api.tasks.InputFile
                import org.gradle.api.tasks.TaskAction
                import com.android.build.api.artifact.SingleArtifact
                import com.android.build.api.variant.BuiltArtifactsLoader
                import com.android.build.api.variant.BuiltArtifacts
                import org.gradle.api.provider.Property
                import org.gradle.api.tasks.Internal

                abstract class VerifyManifestTask extends DefaultTask {

                    @InputFile
                    abstract RegularFileProperty getManifestFile()

                    @TaskAction
                    void taskAction() {
                        println("Got a manifest at ${'$'}{getManifestFile().get().getAsFile()}")
                    }
                }

                androidComponents.onVariants(androidComponents.selector().all(), { variant ->
                    TaskProvider taskProvider = project.tasks.register(
                         variant.getName() + "VerifyManifest",
                         VerifyManifestTask.class
                    )
                    variant.artifacts
                        .use(taskProvider)
                        .wiredWith(VerifyManifestTask::getManifestFile)
                        .toListenTo(SingleArtifact.MERGED_MANIFEST.INSTANCE)
                })
            """.trimIndent()
        )

        project.execute("processDebugMainManifest")
        Truth.assertThat(project.buildResult.didWorkTasks.contains(":debugVerifyManifest")).isTrue()
        Truth.assertThat(project.buildResult.stdout.findAll(
            "Got a manifest at"
        ).count()).isEqualTo(1)
    }

    @Test
    fun warningMessageTest() {
        project.buildFile.appendText(
            """
                import org.gradle.api.DefaultTask
                import org.gradle.api.tasks.InputFiles
                import org.gradle.api.tasks.TaskAction
                import com.android.build.api.artifact.MultipleArtifact
                import com.android.build.api.variant.BuiltArtifactsLoader
                import com.android.build.api.variant.BuiltArtifacts
                import org.gradle.api.provider.ListProperty
                import org.gradle.api.file.RegularFile
                import org.gradle.api.tasks.Internal

                abstract class VerifyProguardFilesTask extends DefaultTask {

                    @InputFiles
                    abstract ListProperty<RegularFile> getProguardFiles()

                    @TaskAction
                    void taskAction() {
                        getProguardFiles.get().forEach {
                            println("Got a File at ${'$'}{it.outputFile}")
                        }
                    }
                }

                androidComponents.onVariants(androidComponents.selector().withBuildType("release"), { variant ->
                    TaskProvider taskProvider = project.tasks.register(
                         variant.getName() + "VerifyProguardFiles",
                         VerifyProguardFilesTask.class
                    )
                    variant.artifacts
                        .use(taskProvider)
                        .wiredWithMultiple(VerifyProguardFilesTask::getProguardFiles)
                        .toListenTo(MultipleArtifact.MULTIDEX_KEEP_PROGUARD.INSTANCE)
                })
            """.trimIndent()
        )

        project.execute("assembleRelease")
        Truth.assertThat(project.buildResult.stdout.findAll(
            "releaseVerifyProguardFiles was registered to listen to the production of the MULTIDEX_KEEP_PROGUARD"
        ).count()).isEqualTo(1)
    }

    @Test
    fun multipleFilesListener() {
        // crete a simple rule file.
        Files.write(
            project.buildFile.toPath().resolveSibling("rules"),
            "-keep class **HelloWorld".toByteArray()
        )
        project.buildFile.appendText(
            """
                android {
                    defaultConfig {
                        multiDexKeepProguard file('default-rules')
                    }
                    buildTypes {
                        release {
                            minifyEnabled true
                            multiDexKeepProguard file('rules')
                        }
                    }
                }

                import org.gradle.api.DefaultTask
                import org.gradle.api.tasks.InputFiles
                import org.gradle.api.tasks.TaskAction
                import com.android.build.api.artifact.MultipleArtifact
                import com.android.build.api.variant.BuiltArtifactsLoader
                import com.android.build.api.variant.BuiltArtifacts
                import org.gradle.api.provider.ListProperty
                import org.gradle.api.file.RegularFile
                import org.gradle.api.tasks.Internal

                abstract class VerifyProguardFilesTask extends DefaultTask {

                    @InputFiles
                    abstract ListProperty<RegularFile> getProguardFiles()

                    @TaskAction
                    void taskAction() {
                        getProguardFiles().get().forEach {
                            println("Got a File at ${'$'}{it.getAsFile()}")
                        }
                    }
                }

                androidComponents.onVariants(androidComponents.selector().all(), { variant ->
                    TaskProvider taskProvider = project.tasks.register(
                         variant.getName() + "VerifyProguardFiles",
                         VerifyProguardFilesTask.class
                    )
                    variant.artifacts
                        .use(taskProvider)
                        .wiredWithMultiple(VerifyProguardFilesTask::getProguardFiles)
                        .toListenTo(MultipleArtifact.MULTIDEX_KEEP_PROGUARD.INSTANCE)
                })
            """.trimIndent()
        )

        project.execute(":releaseVerifyProguardFiles")
        Truth.assertThat(project.buildResult.didWorkTasks.contains(":releaseVerifyProguardFiles")).isTrue()
        Truth.assertThat(project.buildResult.stdout.findAll(
            "Got a File at"
        ).count()).isEqualTo(1)

        project.execute(":debugVerifyProguardFiles")
        Truth.assertThat(project.buildResult.didWorkTasks.contains(":debugVerifyProguardFiles")).isTrue()
        Truth.assertThat(project.buildResult.stdout.findAll(
            "default-rules"
        ).count()).isEqualTo(1)
    }

    @Test
    fun multipleDirectoryListener() {
        project.buildFile.appendText(
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

                abstract class VerifyApksTask extends DefaultTask {

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
                            println("Got an APK at ${'$'}{it.outputFile}")
                        }
                    }
                }

                androidComponents.onVariants(androidComponents.selector().all(), { variant ->
                    TaskProvider taskProvider = project.tasks.register(
                         variant.getName() + "VerifyApks",
                         VerifyApksTask.class
                    ) {
                        it.builtArtifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
                    }
                    variant.artifacts
                        .use(taskProvider)
                        .wiredWith(VerifyApksTask::getApkFolder)
                        .toListenTo(SingleArtifact.APK.INSTANCE)

                    TaskProvider secondTaskProvider = project.tasks.register(
                         variant.getName() + "VerifyAgainApks",
                         VerifyApksTask.class
                    ) {
                        it.builtArtifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
                    }
                    variant.artifacts
                        .use(secondTaskProvider)
                        .wiredWith(VerifyApksTask::getApkFolder)
                        .toListenTo(SingleArtifact.APK.INSTANCE)
                })
            """.trimIndent()
        )

        project.execute("assembleDebug")
        Truth.assertThat(project.buildResult.didWorkTasks.contains(":debugVerifyApks")).isTrue()
        Truth.assertThat(project.buildResult.didWorkTasks.contains(":debugVerifyAgainApks")).isTrue()
        Truth.assertThat(project.buildResult.stdout.findAll(
            "Got an APK at"
        ).count()).isEqualTo(2)
    }
}
