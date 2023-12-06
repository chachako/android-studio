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
import com.google.common.io.ByteStreams
import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import java.nio.file.Files
import java.util.zip.ZipInputStream

class LayoutlibFromMaven(val layoutlibDirectory: FileCollection) {
    companion object {
        private const val MAVEN_LAYOUTLIB_DIR = "prebuilts/studio/"
        private const val OUT_LAYOUTLIB_DIR = ""
        private const val MAVEN_GROUP = "com.android.tools" //TODO: change this once maven names are confirmed
        private const val MAVEN_ARTIFACT = "standalone-render.compose-cli"
        private const val TYPE_EXTRACTED_LAYOUTLIB = "_internal-android-extracted-layoutlib"

        /**
         * Extract layoutlib from maven for this project.
         *
         * This uses a detached configuration and is not idempotent, and should only be called when
         * creating the project services.
         */
        @JvmStatic
        fun create(
            project: Project
        ): LayoutlibFromMaven {
            val version = "0.0.1" +
                    if (Version.ANDROID_GRADLE_PLUGIN_VERSION.endsWith("-dev"))
                        "-dev"
                    else
                        "-alpha01"
            val configuration = project.configurations.detachedConfiguration(
                project.dependencies.create(
                    mapOf(
                        "group" to MAVEN_GROUP, //TODO: change this once maven names are confirmed
                        "name" to MAVEN_ARTIFACT,
                        "version" to version,
                        "classifier" to ""
                    )
                )
            )
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true

            project.dependencies.registerTransform(LayoutLibExtractor::class.java) {
                it.from.attribute(
                    ARTIFACT_TYPE_ATTRIBUTE,
                    ArtifactTypeDefinition.JAR_TYPE
                )
                it.to.attribute(ARTIFACT_TYPE_ATTRIBUTE, TYPE_EXTRACTED_LAYOUTLIB)
            }

            val layoutlibDirectory = configuration.incoming.artifactView { config ->
                config.attributes {
                    it.attribute(
                        ARTIFACT_TYPE_ATTRIBUTE,
                        TYPE_EXTRACTED_LAYOUTLIB
                    )
                }
            }.artifacts.artifactFiles
            return LayoutlibFromMaven(layoutlibDirectory)
        }

        abstract class LayoutLibExtractor : TransformAction<TransformParameters.None> {

            @get:Classpath
            @get:InputArtifact
            abstract val inputArtifact: Provider<FileSystemLocation>

            override fun transform(transformOutputs: TransformOutputs) {
                val input = inputArtifact.get().asFile
                val outDir = transformOutputs.dir(input.nameWithoutExtension).toPath()
                Files.createDirectories(outDir)
                ZipInputStream(input.inputStream().buffered()).use { zipInputStream ->
                    while (true) {
                        val entry = zipInputStream.nextEntry ?: break
                        if (entry.name.contains("../") || entry.isDirectory) {
                            continue
                        }
                        val isLayoutLib = entry.name.contains(MAVEN_LAYOUTLIB_DIR)
                        if (!isLayoutLib) {
                            continue
                        }
                        val path = entry.name.replace(MAVEN_LAYOUTLIB_DIR, OUT_LAYOUTLIB_DIR)
                        val destinationFile = outDir.resolve(path)
                        Files.createDirectories(destinationFile.parent)
                        Files.newOutputStream(destinationFile).buffered().use { output ->
                            ByteStreams.copy(zipInputStream, output)
                        }
                    }
                }
            }
        }

    }
}
