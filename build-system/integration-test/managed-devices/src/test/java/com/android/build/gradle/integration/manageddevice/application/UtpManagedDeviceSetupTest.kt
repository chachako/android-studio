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
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.manageddevice.utils.getStandardExecutor
import com.android.build.gradle.integration.manageddevice.utils.setupSdkDir
import com.android.build.gradle.integration.manageddevice.utils.setupSdkRepo
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test
import java.io.File

class UtpManagedDeviceSetupTest {

    @get:Rule
    val project1 = GradleTestProjectBuilder()
            .fromTestProject("utp")
            .withName("utpProject1")
            .create()

    @get:Rule
    val project2 = GradleTestProjectBuilder()
            .fromTestProject("utp")
            .withName("utpProject2")
            .create()

    private val executors: List<GradleTaskExecutor> by lazy {
        listOf(setupProject(project1), setupProject(project2))
    }

    private fun setupProject(project: GradleTestProject): GradleTaskExecutor {
        val testDir = project.rootProject.projectDir.parentFile

        val sdkImageSource = File(
            testDir,
            FileUtils.toSystemDependentPath("sysImgSource/dl.google.com/android/repository"))
        setupSdkRepo(sdkImageSource)

        val userHomeDirectory = File(testDir, "local")
        val localPrefDirectory =
                File(testDir, FileUtils.toSystemDependentPath("local/.android"))
        if (!localPrefDirectory.exists()) {
            FileUtils.mkdirs(localPrefDirectory)
        }

        val sdkLocation = File(testDir, "projectSDK")
        setupSdkDir(project, sdkLocation)

        val appProject = project.getSubproject("app")
        appProject.buildFile.appendText("""
            android {
                testOptions {
                    managedDevices {
                        localDevices {
                            device1 {
                                device = "Pixel 2"
                                apiLevel = 29
                                systemImageSource = "aosp"
                                require64Bit = true
                            }
                        }
                    }
                }
            }
        """.trimIndent())

        return getStandardExecutor(
            project,
            userHomeDirectory,
            localPrefDirectory,
            sdkImageSource,
        )
    }

    @Test
    fun setupSingleDevice() {
        executors[0].run(":app:cleanManagedDevices")
        executors[0].run(":app:device1Setup")
    }

    @Test
    fun setupTwoIdenticalManagedDeviceInParallel() {
        executors.parallelStream().forEach {
            it.run(":app:cleanManagedDevices")
        }
        executors.parallelStream().forEach {
            it.run(":app:device1Setup")
        }
    }
}
