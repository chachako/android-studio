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
                    classpath "com.google.firebase.testlab:testlab-gradle-plugin:+"
                }
            }
            project.buildscript {
                dependencies {
                    classpath "com.google.firebase.testlab:testlab-gradle-plugin:+"
                }
            }
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
                        orientation = "landscape"
                        locale = "en-US"
                    }
                }
            }
        """)

        val ktBuildFileContent = project.getSubproject("kotlinDslApp").ktsBuildFile.readText()
        project.getSubproject("kotlinDslApp").ktsBuildFile.writeText(
                ktBuildFileContent.replace(
                        "plugins {",
                        "plugins { id(\"com.google.firebase.testlab\")"))
        project.getSubproject("kotlinDslApp").ktsBuildFile.appendText("""
            firebaseTestLab {
                managedDevices {
                    create("myFtlDevice3") {
                        device = "Pixel2"
                        apiLevel = 29
                    }
                    create("myFtlDevice4") {
                        device = "Pixel3"
                        apiLevel = 30
                        orientation = "landscape"
                        locale = "en-US"
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
            assertThat(it).contains("myFtlDevice3Check")
            assertThat(it).contains("myFtlDevice3DebugAndroidTest")
            assertThat(it).contains("myFtlDevice4Check")
            assertThat(it).contains("myFtlDevice4DebugAndroidTest")
        }
    }

    @Test
    fun dsl() {
        project.getSubproject("app").buildFile.appendText("""
            firebaseTestLab {
                serviceAccountCredentials = file("test.json")
                testOptions {
                    fixture {
                        grantedPermissions = "none"
                        extraDeviceFiles["/sdcard/Android/data/com.example.myapplication/myAdditionalText.txt"] = "app/myAdditionalText.txt"
                        networkProfile = "LTE"
                    }
                    results {
                        cloudStorageBucket = "my_example_custom_bucket"
                        resultsHistoryName = "MyCustomHistoryName"
                        directoriesToPull.addAll("/sdcard/Android/data/com.example.myapplication")
                    }
                }
            }
            task("printDslProperties") {
                println("orientation = " + firebaseTestLab.managedDevices.getByName("myFtlDevice2").orientation)
                println("serviceAccountCredentials = " + firebaseTestLab.serviceAccountCredentials.asFile.get().name)
                println("grantedPermissions = " + firebaseTestLab.testOptions.fixture.grantedPermissions)
                println("extraDeviceFiles = " + firebaseTestLab.testOptions.fixture.extraDeviceFiles.get())
                println("networkProfile = " + firebaseTestLab.testOptions.fixture.networkProfile)
                println("cloudStorageBucket = " + firebaseTestLab.testOptions.results.cloudStorageBucket)
                println("resultsHistoryName = " + firebaseTestLab.testOptions.results.resultsHistoryName)
                println("directoriesToPull = " + firebaseTestLab.testOptions.results.directoriesToPull.get())
                doLast { /* no-op */ }
            }
        """)
        val result = executor.run(":app:printDslProperties")
        result.stdout.use {
            assertThat(it).contains("orientation = LANDSCAPE")
            assertThat(it).contains("serviceAccountCredentials = test.json")
            assertThat(it).contains("grantedPermissions = NONE")
            assertThat(it).contains("extraDeviceFiles = [/sdcard/Android/data/com.example.myapplication/myAdditionalText.txt:app/myAdditionalText.txt]")
            assertThat(it).contains("networkProfile = LTE")
            assertThat(it).contains("cloudStorageBucket = my_example_custom_bucket")
            assertThat(it).contains("resultsHistoryName = MyCustomHistoryName")
            assertThat(it).contains("directoriesToPull = [/sdcard/Android/data/com.example.myapplication]")
        }
    }

    @Test
    fun kotlinDsl() {
        project.getSubproject("kotlinDslApp").ktsBuildFile.appendText("""
            firebaseTestLab {
                serviceAccountCredentials.set(file("test.json"))
                testOptions {
                    fixture {
                        grantedPermissions = "none"
                        extraDeviceFiles.put("/sdcard/Android/data/com.example.myapplication/myAdditionalText.txt", "app/myAdditionalText.txt")
                        networkProfile = "LTE"
                    }
                    results {
                        cloudStorageBucket = "my_example_custom_bucket"
                        resultsHistoryName = "MyCustomHistoryName"
                        directoriesToPull.addAll("/sdcard/Android/data/com.example.myapplication")
                    }
                }
            }
            task("printDslProperties") {
                println("orientation = " + firebaseTestLab.managedDevices.getByName("myFtlDevice4").orientation)
                println("serviceAccountCredentials = " + firebaseTestLab.serviceAccountCredentials.asFile.get().name)
                println("grantedPermissions = " + firebaseTestLab.testOptions.fixture.grantedPermissions)
                println("extraDeviceFiles = " + firebaseTestLab.testOptions.fixture.extraDeviceFiles.get())
                println("networkProfile = " + firebaseTestLab.testOptions.fixture.networkProfile)
                println("cloudStorageBucket = " + firebaseTestLab.testOptions.results.cloudStorageBucket)
                println("resultsHistoryName = " + firebaseTestLab.testOptions.results.resultsHistoryName)
                println("directoriesToPull = " + firebaseTestLab.testOptions.results.directoriesToPull.get())
                doLast { /* no-op */ }
            }
        """)
        val result = executor.run(":kotlinDslApp:printDslProperties")
        result.stdout.use {
            assertThat(it).contains("orientation = LANDSCAPE")
            assertThat(it).contains("serviceAccountCredentials = test.json")
            assertThat(it).contains("grantedPermissions = NONE")
            assertThat(it).contains("extraDeviceFiles = {/sdcard/Android/data/com.example.myapplication/myAdditionalText.txt=app/myAdditionalText.txt}")
            assertThat(it).contains("networkProfile = LTE")
            assertThat(it).contains("cloudStorageBucket = my_example_custom_bucket")
            assertThat(it).contains("resultsHistoryName = MyCustomHistoryName")
            assertThat(it).contains("directoriesToPull = [/sdcard/Android/data/com.example.myapplication]")
        }
    }

    @Test
    fun managedDevicesAddsAllDevices() {
        project.getSubproject("app").buildFile.appendText("""
            android {
                testOptions {
                    managedDevices {
                        deviceGroups {
                            ftlDevices {
                                // devices added to firebaseTestLab.manageddevices
                                // should be available through allDevices
                                targetDevices.add(allDevices.myFtlDevice1)
                                targetDevices.add(allDevices.myFtlDevice2)
                            }
                        }
                    }
                }
            }
        """.trimIndent())
        val result = executor.run("tasks")
        result.stdout.use {
            assertThat(it).contains("ftlDevicesGroupCheck")
            assertThat(it).contains("ftlDevicesGroupDebugAndroidTest")
        }
    }

    @Test
    fun managedDevicesRemovesAllDevices() {
        project.getSubproject("app").buildFile.appendText("""
            firebaseTestLab {
                managedDevices.remove(managedDevices.myFtlDevice1)
            }
        """.trimIndent())

        val result = executor.run("tasks")
        // b/c stdout is a scanner, we have to start over every time we search for something
        // that does not exist.
        result.stdout.use {
            assertThat(it).doesNotContain("myFtlDevice1Check")
        }
        result.stdout.use {
            assertThat(it).doesNotContain("myFtlDevice1DebugAndroidTest")
        }
        result.stdout.use {
            assertThat(it).contains("myFtlDevice2Check")
            assertThat(it).contains("myFtlDevice2DebugAndroidTest")
            assertThat(it).contains("myFtlDevice3Check")
            assertThat(it).contains("myFtlDevice3DebugAndroidTest")
            assertThat(it).contains("myFtlDevice4Check")
            assertThat(it).contains("myFtlDevice4DebugAndroidTest")
        }
    }
}
