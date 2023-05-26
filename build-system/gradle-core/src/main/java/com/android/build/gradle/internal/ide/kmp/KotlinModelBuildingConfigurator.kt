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

package com.android.build.gradle.internal.ide.kmp

import com.android.SdkConstants
import com.android.Version
import com.android.build.api.component.impl.KmpAndroidTestImpl
import com.android.build.api.component.impl.KmpUnitTestImpl
import com.android.build.api.variant.impl.KotlinMultiplatformAndroidTarget
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.component.KmpCreationConfig
import com.android.build.gradle.internal.component.UnitTestCreationConfig
import com.android.build.gradle.internal.ide.proto.convert
import com.android.build.gradle.internal.ide.proto.setIfNotNull
import com.android.build.gradle.internal.ide.v2.ModelBuilder.Companion.getAgpFlags
import com.android.build.gradle.internal.ide.v2.TestInfoImpl
import com.android.build.gradle.internal.ide.v2.convertToExecution
import com.android.build.gradle.internal.lint.getLocalCustomLintChecksForModel
import com.android.build.gradle.internal.utils.getDesugarLibConfigFile
import com.android.build.gradle.internal.utils.getDesugaredMethods
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.errors.IssueReporter
import com.android.kotlin.multiplatform.ide.models.serialization.androidCompilationKey
import com.android.kotlin.multiplatform.ide.models.serialization.androidSourceSetKey
import com.android.kotlin.multiplatform.ide.models.serialization.androidTargetKey
import com.android.kotlin.multiplatform.models.AndroidCompilation
import com.android.kotlin.multiplatform.models.AndroidSourceSet
import com.android.kotlin.multiplatform.models.AndroidTarget
import com.android.kotlin.multiplatform.models.InstrumentedTestDslInfo
import com.android.kotlin.multiplatform.models.MainVariantDslInfo
import com.android.kotlin.multiplatform.models.SourceProvider
import com.android.kotlin.multiplatform.models.UnitTestDslInfo
import org.gradle.api.Project

object KotlinModelBuildingConfigurator {
    private fun KmpComponentCreationConfig.toType() = when (this) {
        is KmpCreationConfig -> AndroidCompilation.CompilationType.MAIN
        is KmpUnitTestImpl -> AndroidCompilation.CompilationType.UNIT_TEST
        is KmpAndroidTestImpl -> AndroidCompilation.CompilationType.INSTRUMENTED_TEST
        else -> throw IllegalArgumentException("Unknown type ${this::class.java}")
    }

    fun setupAndroidCompilations(
        components: List<KmpComponentCreationConfig>,
        testInstrumentationRunner: String?,
        testInstrumentationRunnerArguments: Map<String, String>
    ) {
        components.forEach { component ->
            val compilation = component.androidKotlinCompilation

            compilation.extras[androidCompilationKey] = {
                AndroidCompilation.newBuilder()
                    .setType(component.toType())
                    .setDefaultSourceSetName(
                        compilation.defaultSourceSet.name
                    )
                    .setAssembleTaskName(
                        component.taskContainer.assembleTask.name
                    )
                    .setIfNotNull(
                        (component as? KmpCreationConfig)?.toDslInfo(),
                        AndroidCompilation.Builder::setMainDslInfo
                    )
                    .setIfNotNull(
                        (component as? UnitTestCreationConfig)?.toDslInfo(),
                        AndroidCompilation.Builder::setUnitTestDslInfo
                    )
                    .setIfNotNull(
                        (component as? AndroidTestCreationConfig)?.toDslInfo(
                            testInstrumentationRunner, testInstrumentationRunnerArguments
                        ),
                        AndroidCompilation.Builder::setInstrumentedTestDslInfo
                    )
                    .build()
            }

            compilation.defaultSourceSet.extras[androidSourceSetKey] = {
                AndroidSourceSet.newBuilder()
                    .setSourceProvider(
                        SourceProvider.newBuilder()
                            .setManifestFile(component.sources.manifestFile.get().convert())
                    )
                    .build()
            }
        }
    }

    fun setupAndroidTargetModels(
        project: Project,
        mainVariant: KmpCreationConfig,
        androidTarget: KotlinMultiplatformAndroidTarget,
        projectOptions: ProjectOptions,
        issueReporter: IssueReporter
    ) {
        androidTarget.extras[androidTargetKey] = {
            AndroidTarget.newBuilder()
                .setAgpVersion(Version.ANDROID_GRADLE_PLUGIN_VERSION)
                .setProjectPath(project.path)
                .setBuildDir(project.layout.buildDirectory.get().asFile.convert())
                .setBuildToolsVersion(mainVariant.global.buildToolsRevision.toString())
                .setGroupId(
                    project.group.toString()
                )
                .addAllBootClasspath(
                    mainVariant.global.filteredBootClasspath.get().map { it.asFile.convert() }
                )
                .setTestInfo(
                    TestInfoImpl(
                        animationsDisabled = mainVariant.global.testOptions.animationsDisabled,
                        execution = mainVariant.global.testOptions.execution.convertToExecution(),
                        additionalRuntimeApks = project
                            .configurations
                            .findByName(
                                SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION
                            )?.files ?: listOf(),
                        instrumentedTestTaskName = mainVariant.androidTest?.taskContainer?.connectedTestTask?.name
                            ?: ""
                    ).convert()
                )
                .setFlags(
                    getAgpFlags(
                        variants = listOf(mainVariant),
                        projectOptions = projectOptions
                    ).convert()
                )
                .addAllLintChecksJars(
                    getLocalCustomLintChecksForModel(
                        project,
                        issueReporter
                    ).map { it.convert() }
                )
                .setIsCoreLibraryDesugaringEnabled(
                    mainVariant.isCoreLibraryDesugaringEnabledLintCheck
                )
                .addAllDesugarLibConfig(
                    if (mainVariant.isCoreLibraryDesugaringEnabledLintCheck) {
                        getDesugarLibConfigFile(project)
                    } else {
                        emptyList()
                    }.map { it.convert() }
                )
                .addAllDesugaredMethodsFiles(
                    getDesugaredMethods(
                        mainVariant.services,
                        mainVariant.isCoreLibraryDesugaringEnabledLintCheck,
                        mainVariant.minSdk,
                        mainVariant.global
                    ).files.map { it.convert() }
                )
                .build()
        }
    }

    private fun KmpCreationConfig.toDslInfo() =
        MainVariantDslInfo.newBuilder()
            .setNamespace(namespace.get())
            .setCompileSdkTarget(global.compileSdkHashString)
            .setMinSdkVersion(minSdk.convert())
            .setIfNotNull(
                maxSdk,
                MainVariantDslInfo.Builder::setMaxSdkVersion
            )
            .addAllProguardFiles(
                optimizationCreationConfig.proguardFiles.get().map { it.asFile.convert() }
            )
            .addAllConsumerProguardFiles(
                optimizationCreationConfig.consumerProguardFiles.map { it.convert() }
            )
            .build()

    private fun UnitTestCreationConfig.toDslInfo() =
        UnitTestDslInfo.newBuilder()
            .setNamespace(namespace.get())
            .build()

    private fun AndroidTestCreationConfig.toDslInfo(
        testInstrumentationRunner: String?,
        testInstrumentationRunnerArguments: Map<String, String>
    ) =
        InstrumentedTestDslInfo.newBuilder()
            .setNamespace(namespace.get())
            .setIfNotNull(
                testInstrumentationRunner,
                InstrumentedTestDslInfo.Builder::setTestInstrumentationRunner
            )
            .setIfNotNull(
                signingConfigImpl?.convert(),
                InstrumentedTestDslInfo.Builder::setSigningConfig
            )
            .putAllTestInstrumentationRunnerArguments(testInstrumentationRunnerArguments)
            .build()
}
