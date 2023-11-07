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

package com.android.build.gradle.integration.manageddevice.application

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.google.common.io.Resources
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Recommended debugging procedure:
 * If test failed due to build failure caused by kts build script, create an Android project in AS,
 * copy and paste contents in FirebaseTestLabIntegrationTest.txt to build file in app module.
 * Rename "app" module to "kotlinDslApp" (this name is used in this test). Rest of the configuration
 * should be straightforward to setup.
 *
 */
class FirebaseTestLabIntegrationTest {

    @get:Rule
    val project = GradleTestProjectBuilder().fromTestProject("utp").enableProfileOutput().create()

    @Before
    fun setUp() {
        project.rootProject.buildFile.appendText("""
            buildscript {
                dependencies {
                    classpath "com.google.firebase.testlab:testlab-gradle-plugin:+"
                }
            }
            project.buildscript {
                dependencies {
                    classpath "com.google.firebase.testlab:testlab-gradle-plugin:+"
                }
            }
        """.trimIndent())

        val ftlBuildScriptResource =
                Resources.getResource(FirebaseTestLabIntegrationTest::class.java,
                        "FirebaseTestLabIntegrationTest.txt")
        val ktBuildFileContent = project.getSubproject("kotlinDslApp").ktsBuildFile.readText()
        project.getSubproject("kotlinDslApp").ktsBuildFile.writeText(Resources.toString(
                ftlBuildScriptResource,
                Charsets.UTF_8) + ktBuildFileContent.replace("plugins {",
                "plugins { id(\"com.google.firebase.testlab\")"))
        val credentialFile = project.file("${project.projectDir.absolutePath}/credentialFile.json")
        credentialFile.writeText("""
            {
                "client_id": "test_client_id",
                "client_secret": "test_client_secret",
                "quota_project_id": "test_quota_project_id",
                "refresh_token": "test_refresh_token",
                "type": "authorized_user"
            }
        """.trimIndent())
        project.getSubproject("kotlinDslApp").ktsBuildFile.appendText("""
            firebaseTestLab {
                serviceAccountCredentials.set(file("${credentialFile.absolutePath}"))
                managedDevices {
                    create("myFtlDevice1") {
                        device = "testFtlDeviceId1"
                        apiLevel = 32
                    }
                    create("myFtlDevice2") {
                        device = "invalidDeviceId"
                        apiLevel = 30
                    }
                }
            }
        """)
    }

    @Test
    fun runDebugAndroidTestSuccess() {
        val executor: GradleTaskExecutor = project.executor()
        val result = executor.run(":kotlinDslApp:myFtlDevice1DebugAndroidTest")
        result.stdout.use {
            // Check if we actually initiated FTL plugin
            assertThat(it).contains("> Task :kotlinDslApp:myFtlDevice1DebugAndroidTest")
            // Plugin invokes credential check
            assertThat(it).contains("Fake credential initialized")
            assertThat(it).contains("^POST https://www\\.googleapis\\.com/toolresults/v1beta3/projects/test_quota_project_id:initializeSettings\$ is hit")
            // Verifies that test request is sent to FTL server
//            assertThat(it).contains("Firebase Testlab Test for myFtlDevice1: Starting Android test.")
            assertThat(it).contains("Test request for device myFtlDevice1 has been submitted to Firebase TestLab: fake results URL for result storage/details")
            // TODO(b/293338525) enable additional check once this bug is resolved
//            assertThat(it).contains("Test execution: RUNNING")
            assertThat(it).contains("Firebase Testlab Test for myFtlDevice1: state FINISHED")
            // Verifies that we are obtaining test results
            assertThat(it).contains("^GET https://www\\.googleapis\\.com/toolresults/v1beta3/projects/test_quota_project_id/histories/testHistoryId/executions/testExecutionId/steps/testStepId\$ is hit")
            assertThat(it).contains("^GET https://www\\.googleapis\\.com/toolresults/v1beta3/projects/test_quota_project_id/histories/testHistoryId/executions/testExecutionId/steps/testStepId/thumbnails\$ is hit")
            assertThat(it).contains("^GET https://toolResults\\.googleapis\\.com/toolresults/v1beta3/projects/test_quota_project_id/histories/testHistoryId/executions/testExecutionId/steps/testStepId/testCases\$ is hit")
            assertThat(it).contains("BUILD SUCCESSFUL in")
        }
    }

    @Test
    fun runDebugAndroidTestFail_deviceIdNotFoundInAndroidCatalog() {
        val executor: GradleTaskExecutor = project.executor().expectFailure()
        val result = executor.run(":kotlinDslApp:myFtlDevice2DebugAndroidTest")
        result.stderr.use {
            // Check if we actually initiated FTL plugin
            assertThat(it).contains("> A failure occurred while executing com.android.build.gradle.internal.tasks.ManagedDeviceSetupTask\$SetupTaskWorkAction")
            // Check if FTL plugin recognize that invalidDeviceId is not in AndroidDeviceCatalog
            assertThat(it).contains("> Device: invalidDeviceId is not a valid input. Available devices for API level 30 are:")
            assertThat(it).contains("[testFtlDeviceId2 (null)]")
        }
    }
}
