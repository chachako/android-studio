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

import com.android.SdkConstants
import com.android.tools.preview.screenshot.configureInput
import com.android.tools.preview.screenshot.services.AnalyticsService
import com.android.tools.render.compose.readComposeRenderingResultJson
import com.android.tools.render.compose.readComposeScreenshotsJson
import com.android.utils.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask
import org.gradle.jvm.toolchain.JavaLauncher
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

/**
 * Invoke Render CLI tool
 */
@CacheableTask
abstract class PreviewScreenshotRenderTask : DefaultTask(), VerificationTask {

    @get:Classpath
    abstract val mainClasspath: ListProperty<RegularFile>

    @get:Classpath
    abstract val testClasspath: ListProperty<RegularFile>

    @get:Classpath
    abstract val mainClassesDir: ListProperty<Directory>

    @get:Classpath
    abstract val testClassesDir: ListProperty<Directory>

    @get:OutputFile
    abstract val cliToolInput: RegularFileProperty

    @get:Internal
    abstract val layoutlibDir: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val previewsDiscovered: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val resourceFile: RegularFileProperty

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val resourcesDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val screenshotCliJar: ConfigurableFileCollection

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sdk: DirectoryProperty

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>

    @get:Internal
    abstract val analyticsService: Property<AnalyticsService>

    @TaskAction
    fun run() = analyticsService.get().recordTaskAction(path) {
        FileUtils.cleanOutputDir(outputDir.get().asFile)
        if (readComposeScreenshotsJson(previewsDiscovered.get().asFile.reader()).isEmpty()) {
            return@recordTaskAction // No previews discovered to render
        }

        val resourcesApk = getResourcesApk()
        val classpathJars = mutableListOf<String>()
        classpathJars.addAll(mainClassesDir.get().map{it.asFile }.toList().map { it.absolutePath })
        classpathJars.addAll(testClassesDir.get().map{it.asFile }.toList().map { it.absolutePath })
        classpathJars.addAll(mainClasspath.get().map{it.asFile }.toList().map { it.absolutePath })
        classpathJars.addAll(testClasspath.get().map{it.asFile }.toList().map { it.absolutePath })

        val fontsPath = sdk.get().asFile.toPath().resolve(SdkConstants.SDK_DL_FONTS_FOLDER)
        configureInput(
            classpathJars,
            if (fontsPath.exists()) fontsPath.absolutePathString() else null,
            layoutlibDir.singleFile.absolutePath + "/layoutlib/",
            outputDir.get().asFile.absolutePath,
            packageName.get(),
            resourcesApk,
            cliToolInput.get().asFile,
            previewsDiscovered.get().asFile
        )

        val javaRuntimeVersion =
            JavaVersion.toVersion(javaLauncher.get().metadata.javaRuntimeVersion)
        val params = listOfNotNull(
            javaLauncher.get().executablePath.asFile.absolutePath,
            if (javaRuntimeVersion.isCompatibleWith(JavaVersion.VERSION_17))
                "-Djava.security.manager=allow"
            else
                null,
            "-cp",
            screenshotCliJar.singleFile.absolutePath,
            "com.android.tools.render.compose.MainKt",
            cliToolInput.get().asFile.absolutePath
        )
        if (logger.isEnabled(LogLevel.INFO)) {
            logger.info("Render CLI command: ${params.joinToString(" ")}")
        }
        // invoke CLI tool
        val process = ProcessBuilder(params).apply {
            redirectInput()
            environment().remove("TEST_WORKSPACE")
            redirectErrorStream(true)
            redirectOutput(ProcessBuilder.Redirect.PIPE)
        }.start()
        process.waitFor()
        val resultFile = outputDir.file("results.json").get().asFile
        if (!resultFile.exists()) {
            val output = process.inputStream.bufferedReader().readText()
            throw GradleException("There was an error with the rendering process. Process output: $output")
        }
        val composeRenderingResult = readComposeRenderingResultJson(resultFile.reader())
        val renderingErrors =
            composeRenderingResult.screenshotResults.count { it.imagePath == null && it.error != null && it.error!!.status != "SUCCESS" }
        if (composeRenderingResult.globalError != null || renderingErrors > 0) {
            throw GradleException("Rendering failed for one or more previews. For more details, check ${resultFile.absolutePath}")
        }
        val renderWarnings = composeRenderingResult.screenshotResults.count { it.error != null }
        if (renderWarnings > 0) {
            logger.log(
                LogLevel.WARN,
                "There were some issues with rendering one or more previews. For more details, check ${resultFile.absolutePath}"
            )
        }
    }

    private fun getResourcesApk(): String {
        return if (resourcesDir.isPresent ) {
            resourcesDir.get().asFile.listFiles { _, name -> name.endsWith(SdkConstants.EXT_RES) }
                ?.get(0)?.absolutePath!!
        } else if (resourceFile.isPresent)
            resourceFile.get().asFile.absolutePath
        else
            throw RuntimeException("Resources file missing")
    }
}
