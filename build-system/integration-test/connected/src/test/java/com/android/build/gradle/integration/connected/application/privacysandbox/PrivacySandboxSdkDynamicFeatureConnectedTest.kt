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

import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.privacysandbox.privacySandboxSdkAppLargeSampleProjectWithTestModule
import com.android.build.gradle.integration.connected.application.privacysandbox.PrivacySandboxSdkTestBase.Companion.packageExists
import com.android.build.gradle.integration.connected.application.privacysandbox.PrivacySandboxSdkTestBase.Companion.setupDevice
import com.android.build.gradle.integration.connected.utils.getEmulator
import com.android.build.gradle.options.BooleanOption
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class PrivacySandboxSdkDynamicFeatureConnectedTest: PrivacySandboxSdkTestBase {
    @get:Rule override var project = privacySandboxSdkAppLargeSampleProjectWithTestModule()
    @Before
    fun setUp() {
        // fail fast if no response
        project.addAdbTimeout()
        setupDevice()
    }

    @Test
    fun `connectedAndroidTest task for application with dynamic feature`() {
        executor()
            .with(BooleanOption.PRIVACY_SANDBOX_SDK_REQUIRE_SERVICES, false)
            .run(":client-app:connectedAndroidTest")
    }

    @Test
    fun `install and uninstall works for both SDK and APK for application with dynamic feature`() {
        executor()
            .with(BooleanOption.PRIVACY_SANDBOX_SDK_REQUIRE_SERVICES, false)
            .run(":client-app:installDebug")
        Truth.assertThat(packageExists(APP_PACKAGE_NAME)).isTrue()
        Truth.assertThat(
            packageExists(
                SDK_PACKAGE_NAME,
                isLibrary = true
            )
        ).isTrue()

        // project.execute(":app-with-dynamic-feature:uninstallAll")
        // TODO: uninstall not supported yet, verify both APKs are deleted here
    }

    companion object {
        @JvmField @ClassRule val emulator = getEmulator()
    }
}
