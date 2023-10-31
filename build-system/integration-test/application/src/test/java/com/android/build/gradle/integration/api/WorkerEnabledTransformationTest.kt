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

import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldAppKts
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

class WorkerEnabledTransformationTest{
    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
         .fromTestApp(HelloWorldAppKts.forPlugin("com.android.application")).create();

    @Test
    fun workerEnabledTransformation() {
        val copyTask =
        """
            import java.io.Serializable
            import javax.inject.Inject
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.tasks.InputFile
            import org.gradle.api.tasks.OutputFile
            import org.gradle.api.tasks.TaskAction
            import org.gradle.workers.WorkerExecutor
            import com.android.build.api.artifact.ArtifactTransformationRequest
            import com.android.build.api.variant.BuiltArtifact

            interface WorkItemParameters: WorkParameters, Serializable {

                val inputApkFile: RegularFileProperty
                val outputApkFile: RegularFileProperty
            }

            abstract class WorkItem @Inject constructor(private val workItemParameters: WorkItemParameters)
                : WorkAction<WorkItemParameters> {
                override fun execute() {
                    workItemParameters.outputApkFile.get().asFile.delete()
                    workItemParameters.inputApkFile.asFile.get().copyTo(
                        workItemParameters.outputApkFile.get().asFile)
                }
            }
            abstract class CopyApksTask @Inject constructor(private val workers: WorkerExecutor): DefaultTask() {

                @get:InputFiles
                abstract val apkFolder: DirectoryProperty

                @get:OutputDirectory
                abstract val outFolder: DirectoryProperty

                @get:Internal
                abstract val transformationRequest: Property<ArtifactTransformationRequest<CopyApksTask>>

                @TaskAction
                fun taskAction() {
                  transformationRequest.get().submit(
                     this,
                     workers.noIsolation(),
                     WorkItem::class.java) {
                         builtArtifact: BuiltArtifact,
                         outputLocation: Directory,
                         param: WorkItemParameters ->
                            val inputFile = File(builtArtifact.outputFile)
                            param.inputApkFile.set(inputFile)
                            param.outputApkFile.set(File(outputLocation.asFile, inputFile.name))
                            param.outputApkFile.get().asFile
                     }
                }
            }
        """

        TestFileUtils.searchAndReplace(
            project.ktsBuildFile,
            "//import anchor",
            """
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import java.io.File
            import com.android.build.api.variant.AndroidComponentsExtension
            import com.android.build.api.artifact.SingleArtifact

            $copyTask

            """.trimIndent())
           TestFileUtils.appendToFile(project.ktsBuildFile,
            """
               androidComponents.onVariants { variant ->
                    val copyApksProvider = tasks.register<CopyApksTask>("copy${"$"}{variant.name}Apks")

                    val transformationRequest = variant.artifacts.use(copyApksProvider)
                        .wiredWithDirectories(
                            CopyApksTask::apkFolder,
                            CopyApksTask::outFolder)
                        .toTransformMany(SingleArtifact.APK)

                    copyApksProvider.configure {
                        this.transformationRequest.set(transformationRequest)
                    }
               }
            """.trimIndent())
        project.executor().run("clean", "copydebugApks")

        val intermediateFolder = File(project.buildDir, "/intermediates/apk/debug/packageDebug/")
        assertThat(intermediateFolder).exists()
        assertThat(intermediateFolder.listFiles()?.asList()?.map { it.name }).containsExactly(
            "project-debug.apk", BuiltArtifactsImpl.METADATA_FILE_NAME
        )
        val outFolder = File(project.buildDir, "/outputs/apk/debug/")
        assertThat(outFolder).exists()
        assertThat(outFolder.listFiles()?.asList()?.map { it.name }).containsExactly(
            "project-debug.apk", BuiltArtifactsImpl.METADATA_FILE_NAME
        )
    }
}
