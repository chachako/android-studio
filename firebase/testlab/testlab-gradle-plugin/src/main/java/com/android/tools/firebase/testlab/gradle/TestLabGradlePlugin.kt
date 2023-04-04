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

package com.android.tools.firebase.testlab.gradle

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.api.AndroidBasePlugin
import com.android.tools.firebase.testlab.gradle.device.SetupConfigureAction
import com.android.tools.firebase.testlab.gradle.device.SetupTaskAction
import com.android.tools.firebase.testlab.gradle.device.TestRunConfigureAction
import com.android.tools.firebase.testlab.gradle.device.TestRunTaskAction
import com.android.tools.firebase.testlab.gradle.services.TestLabBuildService
import com.google.firebase.testlab.gradle.ManagedDevice
import com.google.firebase.testlab.gradle.TestLabGradlePluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * An entry point for Firebase Test Lab Gradle plugin that extends Gradle Managed Device
 * and adds support for Firebase Test Lab devices.
 */
class TestLabGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.withType(AndroidBasePlugin::class.java) {
            val agpVersion =
                project.extensions.getByType(AndroidComponentsExtension::class.java).pluginVersion
            if (agpVersion < AndroidPluginVersion(8, 1, 0).alpha(9)) {
                error("Android Gradle plugin version 8.1.0-alpha09 or higher is required." +
                              " Current version is $agpVersion.")
            }
            if (agpVersion >= AndroidPluginVersion(8, 3, 0).alpha(1) &&
                    agpVersion.previewType != "dev") {
                error("Firebase TestLab plugin is an experimental feature. It requires Android " +
                        "Gradle plugin version 8.2.0. Current version is $agpVersion.")
            }

            // Registering with the Device registry will take care of the test options binding.
            project.extensions.getByType(AndroidComponentsExtension::class.java)
                .managedDeviceRegistry.registerDeviceType(ManagedDevice::class.java) {
                    dslImplementationClass = ManagedDeviceImpl::class.java
                    setSetupActions(
                        SetupConfigureAction::class.java,
                        SetupTaskAction::class.java)
                    setTestRunActions(
                        TestRunConfigureAction::class.java,
                        TestRunTaskAction::class.java)
                }

            val androidExtension = project.extensions.getByType(CommonExtension::class.java)
            project.extensions.create(
                TestLabGradlePluginExtension::class.java,
                "firebaseTestLab",
                TestLabGradlePluginExtensionImpl::class.java,
                project.objects,
                androidExtension.testOptions.managedDevices
            )

            TestLabBuildService.RegistrationAction(
                    project.extensions.getByType(TestLabGradlePluginExtension::class.java),
                    project.providers,
            ).registerIfAbsent(project)
        }
    }
}
