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
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.testing.screenshot.ImageDiffer
import com.android.build.gradle.internal.testing.screenshot.Verify
import com.android.build.gradle.internal.testing.screenshot.ResponseTypeAdapter
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.core.ComponentType
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import org.gradle.api.tasks.VerificationTask
import javax.imageio.ImageIO

/**
 * Runs screenshot tests of a variant.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class PreviewScreenshotValidationTask : NonIncrementalTask(), VerificationTask {

    companion object {

        const val previewlibCliToolConfigurationName = "_internal-screenshot-test-task-previewlib-cli"
    }

    @Internal
    override lateinit var variantName: String

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val goldenImageDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val renderTaskOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val imageOutputDir: DirectoryProperty

    override fun doTaskAction() {
        var exitValue = 0
        try {
            val responseFile = renderTaskOutputDir.get().file("response.json").asFile
            val response = ResponseTypeAdapter().fromJson(responseFile.readText())
            exitValue = response.status
        } catch (e: Exception) {
            throw GradleException("Unable to render screenshots.", e)
        }
        if (exitValue in listOf(1, 2)) {
            throw GradleException("Screenshots could not be generated. See report for details.")
        } else if (exitValue > 3) {
            throw GradleException("Unknown error code $exitValue returned.")
        }

        var missingGoldens = 0
        var verificationFailures = 0
        for (screenshot in renderTaskOutputDir.asFileTree.files) {
            screenshot.copyTo(File(imageOutputDir.asFile.get().absolutePath + "/" +  screenshot.name))
            val imageDiffer = ImageDiffer.MSSIMMatcher()
            // TODO(b/296430073) Support custom image difference threshold from DSL or task argument
            val verifier =
                Verify(imageDiffer, imageOutputDir.asFile.get().absolutePath + screenshot.name)
            // Verify that golden image directory contains a file with the same name
            val goldenImageForScreenshot =
                goldenImageDir.files().files.filter { it.name == screenshot.name }.first()
            if (goldenImageForScreenshot == null) {
                missingGoldens++
            } else {
                val result =
                    verifier.assertMatchGolden(
                        goldenImageForScreenshot.absolutePath,
                        ImageIO.read(screenshot)
                    )
                if (result !is Verify.AnalysisResult.Passed) {
                    verificationFailures++
                }
            }
        }
        if (missingGoldens > 0 || verificationFailures > 0) {
            var message = "Failed to validate screenshots."
            if (missingGoldens > 0) {
                message += " There were $missingGoldens golden images missing."
            }
            if (verificationFailures > 0) {
              message += " $verificationFailures screenshots failed to match their golden images."
            }
            message += " See test report for details."
            throw GradleException(message)
        }
    }

    class CreationAction(
            private val androidTestCreationConfig: AndroidTestCreationConfig,
            private val imageOutputDir: File,
            private val goldenImageDir: File,
    ) :
            VariantTaskCreationAction<
                    PreviewScreenshotValidationTask,
                    InstrumentedTestCreationConfig
                    >(androidTestCreationConfig) {

        override val name = computeTaskName(ComponentType.PREVIEW_SCREENSHOT_PREFIX)
        override val type = PreviewScreenshotValidationTask::class.java

        override fun configure(task: PreviewScreenshotValidationTask) {
            val testedConfig = (creationConfig as? AndroidTestCreationConfig)?.mainVariant
            task.variantName = testedConfig?.name ?: creationConfig.name

            val testedVariant = androidTestCreationConfig.mainVariant
            task.description = "Run screenshot tests for the " + testedVariant.name + " build."

            task.group = JavaBasePlugin.VERIFICATION_GROUP

            maybeCreatePreviewlibCliToolConfiguration(task.project)

            task.goldenImageDir.set(goldenImageDir)
            task.goldenImageDir.disallowChanges()

            task.imageOutputDir.set(imageOutputDir)
            task.imageOutputDir.disallowChanges()

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.SCREENSHOTS_RENDERED, task.renderTaskOutputDir
            )


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
