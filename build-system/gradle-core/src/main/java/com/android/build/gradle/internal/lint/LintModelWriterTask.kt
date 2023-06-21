/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.lint

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.dsl.Lint
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.component.NestedComponentCreationConfig
import com.android.build.gradle.internal.component.TestFixturesCreationConfig
import com.android.build.gradle.internal.component.UnitTestCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask.Companion.PARTIAL_RESULTS_DIR_NAME
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.ANDROID_TEST_LINT_MODEL
import com.android.build.gradle.internal.scope.InternalArtifactType.ANDROID_TEST_LINT_PARTIAL_RESULTS
import com.android.build.gradle.internal.scope.InternalArtifactType.LINT_MODEL
import com.android.build.gradle.internal.scope.InternalArtifactType.LINT_PARTIAL_RESULTS
import com.android.build.gradle.internal.scope.InternalArtifactType.LINT_VITAL_LINT_MODEL
import com.android.build.gradle.internal.scope.InternalArtifactType.LINT_VITAL_PARTIAL_RESULTS
import com.android.build.gradle.internal.scope.InternalArtifactType.TEST_FIXTURES_LINT_MODEL
import com.android.build.gradle.internal.scope.InternalArtifactType.TEST_FIXTURES_LINT_PARTIAL_RESULTS
import com.android.build.gradle.internal.scope.InternalArtifactType.UNIT_TEST_LINT_MODEL
import com.android.build.gradle.internal.scope.InternalArtifactType.UNIT_TEST_LINT_PARTIAL_RESULTS
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.tools.lint.model.LintModelArtifactType
import com.android.tools.lint.model.LintModelModule
import com.android.tools.lint.model.LintModelSerialization
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Task to write the [LintModelModule] representation of one variant of a Gradle project to disk.
 *
 * This serialized [LintModelModule] file is read by Lint in consuming projects to get all the
 * information about this variant in project.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.LINT)
abstract class LintModelWriterTask : NonIncrementalTask() {

    @get:Nested
    abstract val projectInputs: ProjectInputs

    @get:Nested
    abstract val variantInputs: VariantInputs

    /**
     * We care only about this directory's location, not its contents. Gradle's recommendation is to
     * annotate this with @Internal and add a separate String property annotated with @Input which
     * returns the absolute path of the file (https://github.com/gradle/gradle/issues/5789).
     */
    @get:Internal
    abstract val partialResultsDir: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val partialResultsDirPath: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    override fun doTaskAction() {
        val module = projectInputs.convertToLintModelModule()
        val variant =
            variantInputs.toLintModel(
                module,
                partialResultsDir.get().asFile,
                desugaredMethodsFiles = listOf()
            )
        LintModelSerialization.writeModule(
            module = module,
            destination = outputDirectory.get().asFile,
            writeVariants = listOf(variant),
            writeDependencies = true
        )
    }

    /**
     * If [lintModelArtifactType] is not null, only the corresponding artifact is initialized; if
     * it's null, both the main and test artifacts are initialized.
     */
    internal fun configureForStandalone(
        taskCreationServices: TaskCreationServices,
        javaExtension: JavaPluginExtension,
        kotlinExtensionWrapper: KotlinMultiplatformExtensionWrapper?,
        lintOptions: Lint,
        partialResultsDir: File,
        lintModelArtifactType: LintModelArtifactType?,
        fatalOnly: Boolean,
    ) {
        this.variantName = ""
        this.analyticsService.setDisallowChanges(
            getBuildService(taskCreationServices.buildServiceRegistry)
        )
        this.projectInputs
            .initializeForStandalone(project, javaExtension, lintOptions, LintMode.MODEL_WRITING)
        this.variantInputs
            .initializeForStandalone(
                project,
                javaExtension,
                kotlinExtensionWrapper,
                taskCreationServices.projectOptions,
                fatalOnly = fatalOnly,
                useModuleDependencyLintModels = true,
                LintMode.MODEL_WRITING,
                lintModelArtifactType
            )
        this.partialResultsDir.set(partialResultsDir)
        this.partialResultsDir.disallowChanges()
        this.partialResultsDirPath.setDisallowChanges(partialResultsDir.absolutePath)
    }

    class LintCreationAction(
        variant: VariantWithTests,
        useModuleDependencyLintModels: Boolean = true
    ) : BaseCreationAction(variant, useModuleDependencyLintModels) {

        override val fatalOnly: Boolean
            get() = false

        override val name: String
            get() = computeTaskName("generate", "LintModel")
    }

    class LintVitalCreationAction(
        variant: VariantCreationConfig,
        useModuleDependencyLintModels: Boolean = false
    ) : BaseCreationAction(
        VariantWithTests(variant, androidTest = null, unitTest = null, testFixtures = null),
        useModuleDependencyLintModels
    ) {

        override val fatalOnly: Boolean
            get() = true

        override val name: String
            get() = computeTaskName("generate", "LintVitalLintModel")
    }

    abstract class BaseCreationAction(
        val variant: VariantWithTests,
        private val useModuleDependencyLintModels: Boolean
    ) : VariantTaskCreationAction<LintModelWriterTask, ConsumableCreationConfig>(variant.main) {
        abstract val fatalOnly: Boolean

        final override val type: Class<LintModelWriterTask>
            get() = LintModelWriterTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<LintModelWriterTask>) {
            super.handleProvider(taskProvider)
            registerOutputArtifacts(
                taskProvider,
                if (fatalOnly) {
                    LINT_VITAL_LINT_MODEL
                } else {
                    LINT_MODEL
                },
                creationConfig.artifacts
            )
        }

        override fun configure(task: LintModelWriterTask) {
            super.configure(task)
            task.projectInputs.initialize(variant, LintMode.MODEL_WRITING)
            task.variantInputs.initialize(
                variant,
                useModuleDependencyLintModels = useModuleDependencyLintModels,
                warnIfProjectTreatedAsExternalDependency = false,
                LintMode.MODEL_WRITING,
                addBaseModuleLintModel = creationConfig is DynamicFeatureCreationConfig,
                fatalOnly = fatalOnly
            )
            val partialResultsDir =
                creationConfig.artifacts.getOutputPath(
                    if (fatalOnly) {
                        LINT_VITAL_PARTIAL_RESULTS
                    } else {
                        LINT_PARTIAL_RESULTS
                    },
                    PARTIAL_RESULTS_DIR_NAME
                )
            task.partialResultsDir.set(partialResultsDir)
            task.partialResultsDir.disallowChanges()
            task.partialResultsDirPath.setDisallowChanges(partialResultsDir.absolutePath)
        }
    }

    class PerComponentCreationAction(
        creationConfig: ComponentCreationConfig,
        private val useModuleDependencyLintModels: Boolean,
        private val fatalOnly: Boolean
    ) : VariantTaskCreationAction<LintModelWriterTask, ComponentCreationConfig>(creationConfig) {

        override val type: Class<LintModelWriterTask>
            get() = LintModelWriterTask::class.java

        override val name: String
            get() = if (fatalOnly) {
                creationConfig.computeTaskName("generate", "LintVitalLintModel")
            } else {
                creationConfig.computeTaskName("generate", "LintModel")
            }

        override fun handleProvider(taskProvider: TaskProvider<LintModelWriterTask>) {
            val artifactType =
                when (creationConfig) {
                    is UnitTestCreationConfig -> UNIT_TEST_LINT_MODEL
                    is AndroidTestCreationConfig -> ANDROID_TEST_LINT_MODEL
                    is TestFixturesCreationConfig -> TEST_FIXTURES_LINT_MODEL
                    else -> if (fatalOnly) {
                        LINT_VITAL_LINT_MODEL
                    } else {
                        LINT_MODEL
                    }
                }
            val mainVariant =
                if (creationConfig is NestedComponentCreationConfig) {
                    creationConfig.mainVariant
                } else {
                    creationConfig
                }
            registerOutputArtifacts(taskProvider, artifactType, mainVariant.artifacts)
        }

        override fun configure(task: LintModelWriterTask) {
            super.configure(task)
            val mainVariant =
                if (creationConfig is NestedComponentCreationConfig) {
                    creationConfig.mainVariant
                } else {
                    creationConfig as VariantCreationConfig
                }
            task.projectInputs.initialize(mainVariant, LintMode.MODEL_WRITING)
            task.variantInputs.initialize(
                mainVariant,
                creationConfig as? UnitTestCreationConfig,
                creationConfig as? AndroidTestCreationConfig,
                creationConfig as? TestFixturesCreationConfig,
                creationConfig.services,
                mainVariant.name,
                useModuleDependencyLintModels = useModuleDependencyLintModels,
                warnIfProjectTreatedAsExternalDependency = false,
                lintMode = LintMode.MODEL_WRITING,
                addBaseModuleLintModel = creationConfig is DynamicFeatureCreationConfig,
                fatalOnly = fatalOnly,
                includeMainArtifact = creationConfig is VariantCreationConfig,
                isPerComponentLintAnalysis = true
            )
            val partialResultsDir =
                mainVariant.artifacts.getOutputPath(
                    when (creationConfig) {
                        is UnitTestCreationConfig -> UNIT_TEST_LINT_PARTIAL_RESULTS
                        is AndroidTestCreationConfig -> ANDROID_TEST_LINT_PARTIAL_RESULTS
                        is TestFixturesCreationConfig -> TEST_FIXTURES_LINT_PARTIAL_RESULTS
                        else -> if (fatalOnly) {
                            LINT_VITAL_PARTIAL_RESULTS
                        } else {
                            LINT_PARTIAL_RESULTS
                        }
                    },
                    PARTIAL_RESULTS_DIR_NAME
                )
            task.partialResultsDir.set(partialResultsDir)
            task.partialResultsDir.disallowChanges()
            task.partialResultsDirPath.setDisallowChanges(partialResultsDir.absolutePath)
        }
    }

    companion object {
        fun registerOutputArtifacts(
            taskProvider: TaskProvider<LintModelWriterTask>,
            internalArtifactType: InternalArtifactType<Directory>,
            artifacts: ArtifactsImpl
        ) {
            artifacts
                .setInitialProvider(taskProvider, LintModelWriterTask::outputDirectory)
                .on(internalArtifactType)
        }
    }
}

