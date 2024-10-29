/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.integration.connected.application.privacysandbox

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.deviceSupportsPrivacySandbox
import com.android.build.gradle.integration.common.fixture.enablePrivacySandboxOnTestDevice
import com.android.build.gradle.integration.common.fixture.executeShellCommand
import com.android.build.gradle.integration.common.fixture.uninstallPackage
import com.android.build.gradle.options.BooleanOption

const val APP_PACKAGE_NAME = "com.example.privacysandbox.client"
const val SDK_PACKAGE_NAME = "com.example.sdk"
private const val TEST_PACKAGE_NAME = "com.example.privacysandbox.client.test"
private const val MAX_ATTEMPTS_POLLING_SERVICES_SANDBOX_SDK = 10
private const val POLLING_SERVICES_WAIT_TIME_SANDBOX_SDK = 1000L
interface PrivacySandboxSdkTestBase {
    var project:GradleTestProject
    companion object {
        fun setupDevice() {
            ensureDeviceSupportsPrivacySandbox()
            uninstallIfExists(APP_PACKAGE_NAME)
            uninstallIfExists(TEST_PACKAGE_NAME)
            uninstallIfExists(SDK_PACKAGE_NAME, isLibrary = true)
        }
        private fun ensureDeviceSupportsPrivacySandbox() {
            if (!deviceSupportsPrivacySandbox()) {
                enablePrivacySandboxOnTestDevice()
                repeat(MAX_ATTEMPTS_POLLING_SERVICES_SANDBOX_SDK) {
                    if (deviceSupportsPrivacySandbox()) {
                        return
                    }
                    Thread.sleep(POLLING_SERVICES_WAIT_TIME_SANDBOX_SDK)
                }
                throw RuntimeException("Device does not support Privacy Sandbox after $MAX_ATTEMPTS_POLLING_SERVICES_SANDBOX_SDK attempts.")
            }
        }
        private fun uninstallIfExists(packageName: String, isLibrary: Boolean = false) {
            if (packageExists(packageName, isLibrary = isLibrary)) {
                uninstallPackage(packageName)
            }
        }
        fun packageExists(packageName: String, isLibrary: Boolean = false) : Boolean {
            val type =  if (isLibrary) "libraries" else "packages"
            return executeShellCommand("pm", "list", type)
                .lines()
                .map { it.substringAfter(":") }
                // Libraries listed here don't have the version number after the _
                // So that part is stripped out
                .any { it == packageName.substringBefore("_") }
        }
        fun executor(project: GradleTestProject) = project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .with(BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT, true)
            .withFailOnWarning(false) // kgp uses deprecated api WrapUtil
    }
    fun executor() = executor(project)
}
