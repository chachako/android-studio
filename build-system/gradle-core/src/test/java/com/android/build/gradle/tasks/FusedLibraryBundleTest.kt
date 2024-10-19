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

package com.android.build.gradle.tasks

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.artifact.impl.SingleInitialProviderRequestImpl
import com.android.build.gradle.internal.dependency.PluginConfigurations
import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryGlobalScope
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryGlobalScopeImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.google.common.truth.Truth
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.file.RegularFile
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

internal class FusedLibraryBundleTest {
    @Rule
    @JvmField var temporaryFolder = TemporaryFolder()

    val build: File by lazy {
        temporaryFolder.newFolder("build")
    }

    @Test
    fun testAarBundle() {
        testCreationConfig<FusedLibraryBundleAar, FusedLibraryBundleAar.CreationAction>(
            "bundle.aar"
        )
    }

    inline fun <reified T: FusedLibraryBundle, reified U: FusedLibraryBundle.CreationAction<T>> testCreationConfig(
        archiveFileName: String,
    ) {
        val project: Project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        val taskProvider = project.tasks.register("bundle", T::class.java)

        val variantScope = mock<FusedLibraryGlobalScopeImpl>()
        val artifacts = mock<ArtifactsImpl>()
        val incomingConfigurations = mock<PluginConfigurations>()
        val configuration = mock<Configuration>()
        val dependencySet = mock<DependencySet>()
        val fileCollectionDependency = mock<FileCollectionDependency>()

        whenever(variantScope.artifacts).thenReturn(artifacts)
        whenever(variantScope.projectLayout).thenReturn(project.layout)
        whenever(variantScope.incomingConfigurations).thenReturn(incomingConfigurations)
        whenever(incomingConfigurations.getByConfigType(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH)).thenReturn(configuration)

        whenever(configuration.allDependencies).thenReturn(dependencySet)
        whenever(fileCollectionDependency.files).thenReturn(FakeConfigurableFileCollection(File("someJar.jar")))
        whenever(dependencySet.iterator()).thenReturn(mutableSetOf(fileCollectionDependency).iterator())

        val request = mock<SingleInitialProviderRequestImpl<T, RegularFile>>()

        whenever(artifacts.setInitialProvider(taskProvider, FusedLibraryBundle::outputFile))
                .thenReturn(request)

        val creationAction = U::class.java.getDeclaredConstructor(FusedLibraryGlobalScope::class.java)
            .newInstance(variantScope)
        creationAction.handleProvider(taskProvider)

        val task = taskProvider.get()
        creationAction.configure(task)

        Truth.assertThat(task.destinationDirectory.get().asFile.absolutePath).isEqualTo(
            project.layout.buildDirectory.dir(task.name).get().asFile.absolutePath
        )
        Truth.assertThat(task.archiveFileName.get()).isEqualTo(archiveFileName)
    }
}
