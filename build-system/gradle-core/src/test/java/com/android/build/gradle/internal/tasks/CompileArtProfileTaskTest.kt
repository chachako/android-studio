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

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.dsl.ModulePropertyKey
import com.android.build.gradle.internal.fixtures.ExecutionMode
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.createProjectServices
import com.android.build.gradle.internal.services.createTaskCreationServices
import com.android.build.gradle.internal.services.getBuildServiceName
import com.google.common.truth.Truth
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito
import javax.inject.Inject

@RunWith(Parameterized::class)
class CompileArtProfileTaskTest(private val r8Rewriting: Boolean) {

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "r8Rewriting={0}")
        fun getParameters(): Collection<Array<Any>> {
            return listOf(arrayOf(false), arrayOf(true))
        }
    }

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val project by lazy {
        ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build()
    }
    private val objects by lazy {
        project.objects
    }

    abstract class CompileArtProfileForTest @Inject constructor(
        override val workerExecutor: WorkerExecutor
    ): CompileArtProfileTask()
    private interface TestCreationConfig: ApkCreationConfig, VariantCreationConfig

    @Test
    fun testConfigurationAndWorkParameters() {
        val taskCreationServices= createTaskCreationServices(createProjectServices(project = project))

        val analyticsServices = project.gradle.sharedServices.registerIfAbsent(
            getBuildServiceName(AnalyticsService::class.java),
            FakeNoOpAnalyticsService::class.java
        ) {}


        val creationConfig = Mockito.mock(TestCreationConfig::class.java)
        val taskContainer = Mockito.mock(MutableTaskContainer::class.java)
        Mockito.`when`(creationConfig.taskContainer).thenReturn(taskContainer)
        Mockito.`when`(taskContainer.preBuildTask).thenReturn(
            project.tasks.register("preBuild")
        )
        Mockito.`when`(creationConfig.name).thenReturn("test")
        Mockito.`when`(creationConfig.services).thenReturn(taskCreationServices)

        val artifacts = Mockito.mock(ArtifactsImpl::class.java)
        Mockito.`when`(creationConfig.artifacts).thenReturn(artifacts)

        val experimentalProperties = objects.mapProperty(String::class.java, Any::class.java)
        experimentalProperties.put(ModulePropertyKey.BooleanWithDefault.ART_PROFILE_R8_REWRITING.key, r8Rewriting)
        Mockito.`when`(creationConfig.experimentalProperties).thenReturn(experimentalProperties)

        val mergedFile = temporaryFolder.newFile("merged_file.txt")
        val workerExecutor = FakeGradleWorkExecutor(
            objectFactory = project.objects,
            tmpDir = temporaryFolder.newFolder(),
            executionMode = ExecutionMode.CAPTURING
        )

        val creationAction = CompileArtProfileTask.CreationAction(creationConfig)
        val taskProvider = project.tasks.register(
            "test",
            CompileArtProfileForTest::class.java,
            workerExecutor,
        )
        // the mapping file is always provided to the task so create it and make it available
        // through the artifact APIs.
        val mappingFile = temporaryFolder.newFile("mapping_file")
        Mockito.`when`(artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE)).thenReturn(
            objects.fileProperty().also { it.set(mappingFile) }
        )

        taskProvider.get().let {
            // fist, configure the task to the minimum required to run the task action.
            it.analyticsService.set(analyticsServices)
            creationAction.configureObfuscationMappingFile(it)
            it.mergedArtProfile.set(mergedFile)

            // ensure the configuration set the task input correctly.
            Truth.assertThat(it.useMappingFile.get()).isEqualTo(!r8Rewriting)

            // run the task and make sure the work action parameters contain the right values.
            it.taskAction()
            Truth.assertThat(workerExecutor.capturedParameters).hasSize(1)
            val workParameters = workerExecutor.capturedParameters.single().let { workParameters ->
                Truth.assertThat(workParameters).isInstanceOf(
                    CompileArtProfileTask.CompileArtProfileWorkAction.Parameters::class.java)
                workParameters as CompileArtProfileTask.CompileArtProfileWorkAction.Parameters
            }
            if (r8Rewriting) {
                Truth.assertThat(workParameters.obfuscationMappingFile.orNull).isNull()
            } else {
                Truth.assertThat(workParameters.obfuscationMappingFile.get().asFile).isEqualTo(
                    mappingFile
                )
            }
        }
    }
}
