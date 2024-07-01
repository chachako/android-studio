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
import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryConfigurations
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryGlobalScope
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryGlobalScopeImpl
import com.google.common.truth.Truth
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.attributes.Usage.JAVA_RUNTIME
import org.gradle.api.file.RegularFile
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
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

        val variantScope = Mockito.mock(FusedLibraryGlobalScopeImpl::class.java)
        val artifacts = Mockito.mock(ArtifactsImpl::class.java)
        val incomingConfigurations = Mockito.mock(FusedLibraryConfigurations::class.java)
        val configuration = Mockito.mock(Configuration::class.java)
        val dependencySet = Mockito.mock(DependencySet::class.java)
        val fileCollectionDependency = Mockito.mock(FileCollectionDependency::class.java)

        Mockito.`when`(variantScope.artifacts).thenReturn(artifacts)
        Mockito.`when`(variantScope.projectLayout).thenReturn(project.layout)
        Mockito.`when`(variantScope.incomingConfigurations).thenReturn(incomingConfigurations)
        Mockito.`when`(incomingConfigurations.getConfiguration(JAVA_RUNTIME)).thenReturn(configuration)

        Mockito.`when`(configuration.allDependencies).thenReturn(dependencySet)
        Mockito.`when`(fileCollectionDependency.files).thenReturn(FakeConfigurableFileCollection(File("someJar.jar")))
        Mockito.`when`(dependencySet.iterator()).thenReturn(mutableSetOf(fileCollectionDependency).iterator())

        @Suppress("UNCHECKED_CAST")
        val request = Mockito.mock(SingleInitialProviderRequestImpl::class.java)
                as SingleInitialProviderRequestImpl<T, RegularFile>

        Mockito.`when`(artifacts.setInitialProvider(taskProvider, FusedLibraryBundle::outputFile))
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
