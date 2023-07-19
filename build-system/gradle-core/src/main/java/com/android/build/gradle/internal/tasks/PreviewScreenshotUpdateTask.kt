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

package com.android.build.gradle.internal.tasks

import com.android.Version
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.core.ComponentType
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import org.gradle.api.tasks.VerificationTask
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Classpath
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

/**
 * Update golden images of a variant.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class PreviewScreenshotUpdateTask : NonIncrementalTask(), VerificationTask {

    companion object {

        const val previewlibCliToolConfigurationName = "_internal-screenshot-test-task-previewlib-cli"
    }

    @Internal
    override lateinit var variantName: String

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val screenshotCliJar: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val lintModelDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val lintCacheDir: DirectoryProperty

    @get:OutputDirectory
    abstract val ideExtractionDir: DirectoryProperty

    @get:OutputDirectory
    abstract val goldenImageDir: DirectoryProperty

    @get:OutputDirectory
    abstract val imageOutputDir: DirectoryProperty

    @get:Classpath
    abstract val testClassesDir: ConfigurableFileCollection

    private val cliParams: MutableMap<String, String> = mutableMapOf()

    @TaskAction
    override fun doTaskAction() {
        cliParams["previewJar"] = screenshotCliJar.singleFile.absolutePath
        val testClassesDependencies = testClassesDir.files
            .filter { it.exists() && it.isDirectory}.map { it.absolutePath + '/' }.joinToString(";")

        // invoke CLI tool
        val process = ProcessBuilder(
            mutableListOf(cliParams["java"],
                    "-cp", cliParams["previewJar"], "com.android.screenshot.cli.Main",
                    "--client-name", cliParams["client.name"],
                    "--client-version", cliParams["client.version"],
                    "--jdk-home", cliParams["java.home"],
                    "--sdk-home", cliParams["androidsdk"],
                    "--extraction-dir", cliParams["extraction.dir"],
                    "--jar-location", cliParams["previewJar"],
                    "--lint-model", cliParams["lint.model"],
                    "--cache-dir", cliParams["lint.cache"],
                    "--root-lint-model", cliParams["lint.model"],
                    "--output-location", cliParams["output.location"] + "/",
                    "--golden-location", cliParams["golden.location"] + "/",
                    "--file-path", cliParams["sources"]!!.split(",").first(),
                    "--additional-deps", ";${cliParams["additional.deps"]};$testClassesDependencies",
                    "--record-golden")
        ).apply {
            environment().remove("TEST_WORKSPACE")
            redirectErrorStream(true)
            redirectOutput(ProcessBuilder.Redirect.INHERIT)
        }.start()
        process.waitFor()

        var success: Boolean
        var message = ""
        when (process.exitValue()) {
            in listOf(0, 3) -> {
                success = true
            }
            in listOf(1, 2) -> {
                success = false
                message = "Tests failed. See report for details. "
            }
            else ->  {
                success = false
                message = "Unknown error code ${process.exitValue()} returned"
            }
        }

        if (!success) {
            throw GradleException(message)
        }
    }

    class CreationAction(
            private val androidTestCreationConfig: AndroidTestCreationConfig,
            private val imageOutputDir: File,
            private val goldenImageDir: File,
            private val ideExtractionDir: File,
            private val lintModelDir: File,
            private val lintCacheDir: File,
            private val additionalDependencyPaths: List<String>
    ) :
            VariantTaskCreationAction<
                    PreviewScreenshotUpdateTask,
                    InstrumentedTestCreationConfig
                    >(androidTestCreationConfig) {

        override val name = computeTaskName(ComponentType.PREVIEW_SCREENSHOT_UPDATE_PREFIX)
        override val type = PreviewScreenshotUpdateTask::class.java

        override fun configure(task: PreviewScreenshotUpdateTask) {
            val testedConfig = (creationConfig as? AndroidTestCreationConfig)?.mainVariant
            task.variantName = testedConfig?.name ?: creationConfig.name

            val testedVariant = androidTestCreationConfig.mainVariant
            task.description = "Update screenshots for the " + testedVariant.name + " build."

            task.group = JavaBasePlugin.VERIFICATION_GROUP

            creationConfig.sources.kotlin?.getVariantSources()?.forEach {
                task.sourceFiles.from(
                        it.asFileTree { task.project.objects.fileTree() }
                )
            }
            task.sourceFiles.disallowChanges()
            task.cliParams["sources"] =
                    task.sourceFiles.files.map { it.absolutePath }.joinToString(",")

            maybeCreatePreviewlibCliToolConfiguration(task.project)
            task.screenshotCliJar.from(
                    task.project.configurations.getByName(previewlibCliToolConfigurationName)
            )

            val toolchain = task.project.extensions.getByType(JavaPluginExtension::class.java).toolchain
            val service = task.project.extensions.getByType(JavaToolchainService::class.java)
            // TODO(b/295886078) Investigate error handling needed for getting JavaLauncher.
            val javaLauncher = try {
                service.launcherFor(toolchain).get()
            } catch (ex: GradleException) {
                // If the JDK that was set is not available get the JDK 11 as a default
                service.launcherFor { toolchainSpec ->
                    toolchainSpec.languageVersion.set(JavaLanguageVersion.of(11))
                }.get()
            }
            task.cliParams["java"] =
                    javaLauncher.executablePath.asFile.absolutePath
            task.cliParams["java.home"] =
                    javaLauncher.metadata.installationPath.asFile.absolutePath

            task.cliParams["androidsdk"] =
                    getBuildService(
                            creationConfig.services.buildServiceRegistry,
                            SdkComponentsBuildService::class.java)
                            .get().sdkDirectoryProvider.get().asFile.absolutePath

            task.goldenImageDir.set(goldenImageDir)
            task.goldenImageDir.disallowChanges()
            task.cliParams["golden.location"] = goldenImageDir.absolutePath

            task.imageOutputDir.set(imageOutputDir)
            task.imageOutputDir.disallowChanges()
            task.cliParams["output.location"] = imageOutputDir.absolutePath

            task.ideExtractionDir.set(ideExtractionDir)
            task.ideExtractionDir.disallowChanges()
            task.cliParams["extraction.dir"] = ideExtractionDir.absolutePath

            task.lintModelDir.set(lintModelDir)
            task.lintModelDir.disallowChanges()
            task.cliParams["lint.model"] = lintModelDir.absolutePath

            task.lintCacheDir.set(lintCacheDir)
            task.lintCacheDir.disallowChanges()
            task.cliParams["lint.cache"] = lintCacheDir.absolutePath

            task.cliParams["client.name"] = "Android Gradle Plugin"
            task.cliParams["client.version"] = Version.ANDROID_GRADLE_PLUGIN_VERSION

            task.testClassesDir.from(creationConfig.services.fileCollection().apply {from(creationConfig
                .artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
                .getFinalArtifacts(ScopedArtifact.CLASSES)) })
            task.testClassesDir.disallowChanges()

            task.cliParams["additional.deps"] = additionalDependencyPaths.joinToString (";")
        }

        private fun maybeCreatePreviewlibCliToolConfiguration(project: Project) {
            val container = project.configurations
            val dependencies = project.dependencies
            if (container.findByName(previewlibCliToolConfigurationName) == null) {
                container.create(previewlibCliToolConfigurationName).apply {
                    isVisible = false
                    isTransitive = true
                    isCanBeConsumed = false
                    description = "A configuration to resolve PreviewLib CLI tool dependencies."
                }
                dependencies.add(
                        previewlibCliToolConfigurationName,
                        "com.android.screenshot.cli:screenshot:${Version.ANDROID_TOOLS_BASE_VERSION}")
            }
        }
    }
}

