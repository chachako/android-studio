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

package com.android.build.gradle.integration.privacysandbox

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.privacysandbox.privacySandboxSampleProjectWithDynamicFeature
import com.android.build.gradle.integration.privacysandbox.PrivacySandboxDefaultApkOutputTest.Companion.getBuildFileContentWithFetchTask
import com.android.build.gradle.integration.privacysandbox.PrivacySandboxDefaultApkOutputTest.Companion.viaBundleVerificationString
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test

class PrivacySandboxAppWithDynamicFeatureApkOutputTest {

    @JvmField
    @Rule
    val project = privacySandboxSampleProjectWithDynamicFeature()

    private fun executor() = project.executor()
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
        .with(BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT, true)
        .withFailOnWarning(false) // kgp uses deprecated api WrapUtil
        .withPerTestPrefsRoot(true)
        .with(BooleanOption.ENABLE_PROFILE_JSON, true) // Regression test for b/237278679
        .with(BooleanOption.PRIVACY_SANDBOX_SDK_REQUIRE_SERVICES, false)

    @Test
    fun getApkOutputForAppWithDynamicFeature() {
        project.getSubproject("example-app").buildFile.appendText(
            getBuildFileContentWithFetchTask(viaBundleVerificationString)
        )

        executor().run(":example-app:fetchApks")
    }
}
