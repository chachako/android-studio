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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FirebaseTestLabDeviceTest {
    @get:Rule
    val project = GradleTestProjectBuilder()
            .fromTestProject("utp")
            .enableProfileOutput()
            .create()

    private val executor: GradleTaskExecutor
        get() = project.executor()

    @Before
    fun setUp() {
        project.rootProject.buildFile.appendText("""
            buildscript {
                dependencies {
                    classpath "com.google.firebase.testlab:testlab-gradle-plugin:0.0.1-dev"
                }
            }
            project.buildscript {
                dependencies {
                    classpath "com.google.firebase.testlab:testlab-gradle-plugin:0.0.1-dev"
                }
            }
        """.trimIndent())
        project.gradlePropertiesFile.appendText("""
            android.experimental.testOptions.managedDevices.customDevice=true
        """.trimIndent())
        val appBuildFileContent = project.getSubproject("app").buildFile.readText()
        project.getSubproject("app").buildFile.writeText("""
            apply plugin: 'com.google.firebase.testlab'
        """.trimIndent()+"\n$appBuildFileContent")
        project.getSubproject("app").buildFile.appendText("""
            firebaseTestLab {
                managedDevices {
                    myFtlDevice1 {
                        device = "Pixel2"
                        apiLevel = 29
                    }
                    myFtlDevice2 {
                        device = "Pixel3"
                        apiLevel = 30
                    }
                }
            }
        """)
    }

    @Test
    fun ftlManagedDeviceTasks() {
        val result = executor.run("tasks")
        result.stdout.use {
            assertThat(it).contains("myFtlDevice1Check")
            assertThat(it).contains("myFtlDevice1DebugAndroidTest")
            assertThat(it).contains("myFtlDevice2Check")
            assertThat(it).contains("myFtlDevice2DebugAndroidTest")
        }
    }

    @Test
    fun serviceAccountCredentials() {
        project.getSubproject("app").buildFile.appendText("""
            firebaseTestLab {
                serviceAccountCredentials = file("test.json")
            }
            task("printServiceAccountCredentials") {
                println("serviceAccountCredentials = " + project.firebaseTestLab.serviceAccountCredentials.asFile.get().name)
                doLast { /* no-op */ }
            }
        """)
        val result = executor.run(":app:printServiceAccountCredentials")
        result.stdout.use {
            assertThat(it).contains("serviceAccountCredentials = test.json")
        }
    }
}
