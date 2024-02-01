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

package com.android.tools.preview.screenshot.tasks

import com.android.tools.preview.screenshot.findPreviewsAndSerialize
import com.android.tools.preview.screenshot.services.AnalyticsService
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files

abstract class PreviewDiscoveryTask: DefaultTask() {

    @get:OutputFile
    abstract val previewsOutputFile: RegularFileProperty

    /**
     * Full scope, including project scope and all dependencies. [ test ]
     */
    @get:Classpath
    abstract val testClassesDir: ListProperty<Directory>

    /**
     * Full scope, including project scope and all dependencies. [ test ]
     */
    @get:Classpath
    abstract val testJars: ListProperty<RegularFile>

    /**
     * Full scope, including project scope and all dependencies.
     */
    @get:Classpath
    abstract val mainClassesDir: ListProperty<Directory>

    /**
     * Full scope, including project scope and all dependencies.
     */
    @get:Classpath
    abstract val mainJars: ListProperty<RegularFile>

    @get:OutputDirectory
    abstract val resultsDir: DirectoryProperty

    @get:OutputDirectory
    abstract val referenceImageDir: DirectoryProperty

    @get:Internal
    abstract val analyticsService: Property<AnalyticsService>

    @TaskAction
    fun run() = analyticsService.get().recordTaskAction(path) {
        Files.createDirectories(resultsDir.asFile.get().toPath())
        Files.createDirectories(referenceImageDir.asFile.get().toPath())

        val outputFilePath = previewsOutputFile.get().asFile.toPath()
        val classpathJars = mutableListOf<String>()
        classpathJars.addAll(testJars.get().map { it.asFile.absolutePath })
        classpathJars.addAll(mainJars.get().map { it.asFile.absolutePath })
        classpathJars.addAll(testClassesDir.get().map { it.asFile.absolutePath})
        classpathJars.addAll(mainClassesDir.get().map { it.asFile.absolutePath})

        // TODO: use a worker here
        findPreviewsAndSerialize(classpathJars, outputFilePath)
    }
}
