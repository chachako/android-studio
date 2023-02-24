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
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.core.ComponentType
import org.gradle.api.Project
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.testing.Test
import java.io.File

/**
 * Runs screenshot tests of a variant.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class ScreenshotTestTask : Test(), VariantAwareTask {

    companion object {

        const val configurationName = "_internal-screenshot-test-task-test-engine"
    }

    @Internal
    override lateinit var variantName: String

    class CreationAction(private val androidTestCreationConfig: AndroidTestCreationConfig) :
            VariantTaskCreationAction<
                    ScreenshotTestTask,
                    InstrumentedTestCreationConfig
                    >(androidTestCreationConfig) {

        override val name = computeTaskName(ComponentType.SCREENSHOT_TEST_PREFIX)
        override val type = ScreenshotTestTask::class.java

        override fun configure(task: ScreenshotTestTask) {
            val testedConfig = (creationConfig as? AndroidTestCreationConfig)?.mainVariant
            task.variantName = testedConfig?.name ?: creationConfig.name

            val testedVariant = androidTestCreationConfig.mainVariant
            task.description = "Run screenshot tests for the " + testedVariant.name + " build."

            task.useJUnitPlatform()
            (task.testFramework as JUnitPlatformTestFramework)
                    .options.includeEngines("screenshot-test-engine")

            task.group = JavaBasePlugin.VERIFICATION_GROUP

            task.testClassesDirs = creationConfig.services.fileCollection().apply {
                from(creationConfig
                        .artifacts
                        .forScope(ScopedArtifacts.Scope.PROJECT)
                        .getFinalArtifacts(ScopedArtifact.CLASSES))
                creationConfig.buildConfigCreationConfig?.let {
                    from(it.compiledBuildConfig)
                }
                creationConfig.androidResourcesCreationConfig?.let {
                    from(it.getCompiledRClasses(ConsumedConfigType.RUNTIME_CLASSPATH))
                }
            }

            maybeCreateScreenshotTestConfiguration(task.project)
            task.classpath = creationConfig.services.fileCollection().apply {
                from(task.project.configurations.getByName(configurationName))
                from(task.testClassesDirs)
            }

            task.reports.junitXml.outputLocation.set(
                    File(
                            creationConfig
                                    .services
                                    .projectInfo
                                    .getTestResultsFolder(),
                            task.name))
            task.reports.html.outputLocation.set(
                    File(
                            creationConfig
                                    .services
                                    .projectInfo
                                    .getTestReportFolder(),
                            task.name))
        }

        private fun maybeCreateScreenshotTestConfiguration(project: Project) {
            val container = project.configurations
            val dependencies = project.dependencies
            if (container.findByName(configurationName) == null) {
                container.create(configurationName).apply {
                    isVisible = false
                    isTransitive = true
                    isCanBeConsumed = false
                    description = "A configuration to resolve Screenshot Test dependencies."
                }
                val engineVersion = "0.0.1" +
                        if (Version.ANDROID_GRADLE_PLUGIN_VERSION.endsWith("-dev"))
                            "-dev"
                        else
                            "-alpha01"
                dependencies.add(
                        configurationName,
                        "com.android.tools.screenshot:junit-engine:${engineVersion}")
            }
        }
    }
}
