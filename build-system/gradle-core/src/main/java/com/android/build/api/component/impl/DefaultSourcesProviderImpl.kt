/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.api.component.impl

import com.android.build.api.variant.impl.DirectoryEntries
import com.android.build.api.variant.impl.DirectoryEntry
import com.android.build.api.variant.impl.FileBasedDirectoryEntryImpl
import com.android.build.api.variant.impl.ProviderBasedDirectoryEntryImpl
import com.android.build.api.variant.impl.TaskProviderBasedDirectoryEntryImpl
import com.android.build.gradle.api.AndroidSourceDirectorySet
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.api.DefaultAndroidSourceDirectorySet
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.UnitTestCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.builder.compiling.BuildConfigType
import java.io.File
import java.util.Collections

/**
 * Computes the default sources for all [com.android.build.api.variant.impl.SourceType]s.
 */
class DefaultSourcesProviderImpl(
    val component: ComponentCreationConfig,
    val variantSources: VariantSources,
) : DefaultSourcesProvider {

    override fun getJava(): List<DirectoryEntry> =
        component.defaultJavaSources()

    override fun getKotlin(): List<DirectoryEntry> =
        flattenSourceProviders(AndroidSourceSet::kotlin)

    override fun getRes(): List<DirectoryEntries>? =
        if (component.buildFeatures.androidResources) {
            component.defaultResSources()
        } else null

    override fun getResources(): List<DirectoryEntry> =
        flattenSourceProviders { sourceSet -> sourceSet.resources }

    override fun getAssets(): List<DirectoryEntries> =
        defaultAssetsSources()

    override fun getJniLibs(): List<DirectoryEntries> =
        getSourceList(DefaultAndroidSourceSet::jniLibs)

    override fun getShaders(): List<DirectoryEntries>? =
        if (component.buildFeatures.shaders) getSourceList() { sourceProvider ->
            sourceProvider.shaders
        } else null

    override fun getAidl(): List<DirectoryEntry>? =
        if (component.buildFeatures.aidl) {
            flattenSourceProviders { sourceSet -> sourceSet.aidl }
        } else null

    override fun getMlModels(): List<DirectoryEntries>? =
        if (component.buildFeatures.mlModelBinding) {
            getSourceList() { sourceProvider -> sourceProvider.mlModels }
        } else null

    override fun getRenderscript(): List<DirectoryEntry>? =
        if (component.buildFeatures.renderScript) {
            flattenSourceProviders { sourceSet -> sourceSet.renderscript }
        } else null

    override fun getBaselineProfiles(): List<DirectoryEntry> =
        flattenSourceProviders(AndroidSourceSet::baselineProfiles )

    override val artProfile: File
        get() = variantSources.artProfile

    override val mainManifestFile: File
        get() = variantSources.mainManifestFilePath

    override val manifestOverlays: List<File>
        get() = variantSources.manifestOverlays

    override val sourceProvidersNames: List<String>
        get() = variantSources.getSortedSourceProviders().map { it.name }


    private fun flattenSourceProviders(
        sourceDirectory: (sourceSet: AndroidSourceSet) -> com.android.build.api.dsl.AndroidSourceDirectorySet
    ): List<DirectoryEntry> {
        val sourceSets = mutableListOf<DirectoryEntry>()
        // Variant sources are added independently later so that they can be added to the model
        for (sourceProvider in variantSources.getSortedSourceProviders(false)) {
            val sourceSet = sourceProvider as AndroidSourceSet
            val androidSourceDirectorySet =
                sourceDirectory(sourceSet) as DefaultAndroidSourceDirectorySet
            sourceSets.add(
                ProviderBasedDirectoryEntryImpl(
                    sourceSet.name,
                    // It's ok to use a provider here since androidSourceDirectorySet.srcDirs is
                    // Collection<File> which therefore does not need to carry task dependencies.
                    component.services.provider {
                        androidSourceDirectorySet.srcDirs.map {
                            component.services.projectInfo.projectDirectory.dir(
                                it.absolutePath
                            )
                        }
                    },
                    filter = androidSourceDirectorySet.filter,
                    isGenerated = false,
                    isUserAdded = false,
                    shouldBeAddedToIdeModel = false,
                )
            )
        }
        return sourceSets
    }

    /**
     * Computes the default java sources: source sets and generated sources.
     * For access to the final list of java sources, use [com.android.build.api.variant.Sources]
     *
     * Every entry is a ConfigurableFileTree instance to enable incremental java compilation.
     */
    private fun ComponentCreationConfig.defaultJavaSources(): List<DirectoryEntry> {
        // Build the list of source folders.
        val sourceSets = mutableListOf<DirectoryEntry>()

        // First the actual source folders.
        sourceSets.addAll(
            flattenSourceProviders { sourceSet -> sourceSet.java }
        )

        // for the other, there's no duplicate so no issue.
        if (buildConfigCreationConfig?.buildConfigType == BuildConfigType.JAVA_SOURCE) {
            sourceSets.add(
                TaskProviderBasedDirectoryEntryImpl(
                    "generated_build_config",
                    artifacts.get(InternalArtifactType.GENERATED_BUILD_CONFIG_JAVA),
                    services.fileCollection(),
                )
            )
        }
        if (this is ConsumableCreationConfig && buildFeatures.aidl) {
            sourceSets.add(
                TaskProviderBasedDirectoryEntryImpl(
                    "generated_aidl",
                    artifacts.get(InternalArtifactType.AIDL_SOURCE_OUTPUT_DIR),
                    services.fileCollection(),
                )
            )
        }
        if (buildFeatures.dataBinding || buildFeatures.viewBinding) {
            if (this !is UnitTestCreationConfig) {
                sourceSets.add(
                    TaskProviderBasedDirectoryEntryImpl(
                        "databinding_generated",
                        artifacts.get(InternalArtifactType.DATA_BINDING_BASE_CLASS_SOURCE_OUT),
                        services.fileCollection(),
                        )
                )
            }
        }
        if (buildFeatures.mlModelBinding) {
            sourceSets.add(
                TaskProviderBasedDirectoryEntryImpl(
                    name = "mlModel_generated",
                    directoryProvider = artifacts.get(InternalArtifactType.ML_SOURCE_OUT),
                    services.fileCollection(),
                    )
            )
        }
        return sourceSets
    }

    private fun ComponentCreationConfig.defaultResSources(): List<DirectoryEntries> {
        val sourceDirectories = mutableListOf<DirectoryEntries>()

        sourceDirectories.addAll(
            getSourceList() { sourceProvider -> sourceProvider.res }
        )

        val generatedFolders = mutableListOf<DirectoryEntry>()
        if (buildFeatures.renderScript) {
            generatedFolders.add(
                TaskProviderBasedDirectoryEntryImpl(
                    name = "renderscript_generated_res",
                    directoryProvider = artifacts.get(InternalArtifactType.RENDERSCRIPT_GENERATED_RES),
                    services.fileCollection(),
                    )
            )
        }

        if (buildFeatures.resValues) {
            generatedFolders.add(
                TaskProviderBasedDirectoryEntryImpl(
                    name = "generated_res",
                    directoryProvider = artifacts.get(InternalArtifactType.GENERATED_RES),
                    services.fileCollection(),
                    )
            )
        }

        sourceDirectories.add(DirectoryEntries("generated", generatedFolders))

        return Collections.unmodifiableList(sourceDirectories)
    }

    private fun defaultAssetsSources(): List<DirectoryEntries> =
        getSourceList() { sourceProvider -> sourceProvider.assets }

    private fun getSourceList(
        action: (sourceProvider: DefaultAndroidSourceSet) -> AndroidSourceDirectorySet
    ): List<DirectoryEntries> {
        // Variant sources are added independently later so that they can be added to the model
        return variantSources.getSortedSourceProviders(false).map { sourceProvider ->
            sourceProvider as DefaultAndroidSourceSet
            val androidSourceDirectorySet =
                action(sourceProvider) as DefaultAndroidSourceDirectorySet
            DirectoryEntries(
                sourceProvider.name,
                androidSourceDirectorySet.srcDirs.map { directory ->
                    FileBasedDirectoryEntryImpl(
                        sourceProvider.name,
                        directory,
                    )
                }
            )
        }
    }
}
