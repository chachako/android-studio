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

package com.android.tools.preview.screenshot.layoutlibExtractor

import com.android.Version
import com.android.utils.FileUtils
import com.google.common.io.ByteStreams
import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import java.nio.file.Files
import java.util.zip.ZipInputStream

class LayoutlibDataFromMaven(val layoutlibDataDirectory: FileCollection) {
    companion object {
        private const val MAVEN_GROUP = "com.android.tools.layoutlib"
        private const val MAVEN_ARTIFACT = "layoutlib-runtime"
        private const val TYPE_EXTRACTED_LAYOUTLIB_DATA = "_internal-android-extracted-layoutlib-data"

        /**
         * Extract layoutlib data from maven for this project.
         */
        @JvmStatic
        fun create(
            project: Project,
            version: String,
            frameworkResJar: FileCollection
        ): LayoutlibDataFromMaven {
            val configuration = project.configurations.detachedConfiguration(
                project.dependencies.create(
                    mapOf(
                        "group" to MAVEN_GROUP,
                        "name" to MAVEN_ARTIFACT,
                        "version" to version,
                        "classifier" to ""
                    )
                )
            )
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true

            project.dependencies.registerTransform(LayoutLibDataExtractor::class.java) {
                it.from.attribute(
                    ARTIFACT_TYPE_ATTRIBUTE,
                    ArtifactTypeDefinition.JAR_TYPE
                )
                it.to.attribute(ARTIFACT_TYPE_ATTRIBUTE, TYPE_EXTRACTED_LAYOUTLIB_DATA)
                it.parameters.apply {
                    frameworkRes.setFrom(frameworkResJar)
                }
            }

            val layoutlibDataDirectory = configuration.incoming.artifactView { config ->
                config.attributes {
                    it.attribute(
                        ARTIFACT_TYPE_ATTRIBUTE,
                        TYPE_EXTRACTED_LAYOUTLIB_DATA
                    )
                }
            }.artifacts.artifactFiles
            return LayoutlibDataFromMaven(layoutlibDataDirectory)
        }

        abstract class LayoutLibDataExtractor : TransformAction<LayoutLibDataExtractor.Parameters> {

            abstract class Parameters: TransformParameters {

                @get:Classpath
                abstract val frameworkRes: ConfigurableFileCollection
            }

            @get:Classpath
            @get:InputArtifact
            abstract val inputArtifact: Provider<FileSystemLocation>

            override fun transform(transformOutputs: TransformOutputs) {
                val input = inputArtifact.get().asFile
                val outDir = transformOutputs.dir("layoutlib").toPath()
                Files.createDirectories(outDir)
                ZipInputStream(input.inputStream().buffered()).use { zipInputStream ->
                    while (true) {
                        val entry = zipInputStream.nextEntry ?: break
                        if (entry.name.contains("../") || entry.isDirectory) {
                            continue
                        }
                        val destinationFile = outDir.resolve(entry.name)
                        Files.createDirectories(destinationFile.parent)
                        Files.newOutputStream(destinationFile).buffered().use { output ->
                            ByteStreams.copy(zipInputStream, output)
                        }
                    }
                }
                val resJar = outDir.resolve("data").resolve("framework_res.jar").toFile()
                FileUtils.copyFile(parameters.frameworkRes.singleFile, resJar)
            }
        }

    }
}
