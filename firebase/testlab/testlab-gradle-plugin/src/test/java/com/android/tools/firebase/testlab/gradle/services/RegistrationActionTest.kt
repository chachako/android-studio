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

package com.android.tools.firebase.testlab.gradle.services

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ManagedDevices
import com.android.build.api.dsl.TestOptions
import com.android.tools.firebase.testlab.gradle.TestLabGradlePluginExtensionImpl
import com.google.common.truth.Truth.assertThat
import com.google.firebase.testlab.gradle.TestLabGradlePluginExtension
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.io.File

/**
 * Tests for [TestLabBuildService.RegistrationAction]
 */
class RegistrationActionTest {

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    @get:Rule
    val mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()

    @Mock(answer = Answers.RETURNS_MOCKS)
    lateinit var androidManagedDevices: ManagedDevices

    @Mock
    lateinit var androidExtension: CommonExtension<*, *, *, *, *>

    @Mock
    lateinit var mockAndroidTestOptions: TestOptions

    lateinit var projectPath: File

    lateinit var fakeCredentialFile: File

    lateinit var fakeStubFile: File

    lateinit var project: Project

    lateinit var extension: TestLabGradlePluginExtension

    lateinit var buildServiceParameters: TestLabBuildService.Parameters

    lateinit var testExecution: String

    @Before
    fun setup() {
        projectPath = temporaryFolderRule.newFolder("testProject")
        project = ProjectBuilder.builder()
            .withName("testProject")
            .withProjectDir(projectPath)
            .build()

        extension = project.extensions.create(
            TestLabGradlePluginExtension::class.java,
            "firebaseTestLab",
            TestLabGradlePluginExtensionImpl::class.java,
            project.objects,
            androidManagedDevices
        ) as TestLabGradlePluginExtension

        buildServiceParameters = project.objects.newInstance(
            TestLabBuildService.Parameters::class.java
        )

        testExecution = "androidx_test_orchestrator"

        mockAndroidTestOptions.apply {
            `when`(execution).thenAnswer { testExecution }
        }

        androidExtension.apply {
            `when`(testOptions).thenReturn(mockAndroidTestOptions)
            project.extensions.add(
                CommonExtension::class.java,
                "Android",
                this
            )
        }

        fakeCredentialFile = temporaryFolderRule.newFile().apply {
            writeText(
                """
                {
                    "client_id": "test_client_id",
                    "quota_project_id": "test_quota_project_id"
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun configureBuildService() {

        extension.apply {
            serviceAccountCredentials.set(fakeCredentialFile)

            testOptions.apply {

                fixture.apply {
                    grantedPermissions = "all"

                    extraDeviceFiles.apply {
                        put("path/on/device", "local/path")
                        put("other/path/on/device", "gs://storage/link")
                    }

                    networkProfile = "lte"
                }

                execution.apply {
                    timeoutMinutes = 20

                    maxTestReruns = 1

                    failFast = false

                    numUniformShards = 5
                }

                results.apply {
                    cloudStorageBucket = "my_bucket"

                    resultsHistoryName = "history_for_project"

                    directoriesToPull.add("some/path/on/device")

                    recordVideo = true

                    performanceMetrics = true
                }
            }
        }

        TestLabBuildService.RegistrationAction(project).configure(buildServiceParameters)

        buildServiceParameters.apply {
            assertThat(credentialFile.get().asFile)
                .isEqualTo(fakeCredentialFile)
            assertThat(cloudStorageBucket.get()).isEqualTo("my_bucket")
            assertThat(timeoutMinutes.get()).isEqualTo(20)
            assertThat(maxTestReruns.get()).isEqualTo(1)
            assertThat(failFast.get()).isFalse()
            assertThat(numUniformShards.get()).isEqualTo(5)
            assertThat(targetedShardDurationMinutes.get()).isEqualTo(0)
            assertThat(grantedPermissions.get()).isEqualTo("ALL")
            assertThat(networkProfile.get()).isEqualTo("lte")
            assertThat(directoriesToPull.get()).containsExactly(
                "some/path/on/device"
            )
            assertThat(recordVideo.get()).isTrue()
            assertThat(performanceMetrics.get()).isTrue()
            // Presently cannot properly mock out Configurations, so can't acertain stubApk.
            assertThat(useOrchestrator.get()).isTrue()
        }
    }
}
