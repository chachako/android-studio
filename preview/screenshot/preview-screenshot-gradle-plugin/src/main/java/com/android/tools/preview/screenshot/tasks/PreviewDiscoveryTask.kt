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
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.Classpath

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

    @TaskAction
    fun run() {
        val outputFilePath = previewsOutputFile.get().asFile.toPath()
        val classpathJars = mutableListOf<String>()
        // COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR is included in allJars
        classpathJars.addAll(testJars.get().map { it.asFile.absolutePath })
        classpathJars.addAll(mainJars.get().map { it.asFile.absolutePath })
        classpathJars.addAll(testClassesDir.get().map { it.asFile.absolutePath})
        classpathJars.addAll(mainClassesDir.get().map { it.asFile.absolutePath})
        findPreviewsAndSerialize(classpathJars, outputFilePath)
    }
}
