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
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.privacysandbox.privacySandboxSampleProjectWithSeparateTest
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test

class PrivacySandboxTestOnlyModuleApkOutputTest {

    @JvmField
    @Rule
    val project = privacySandboxSampleProjectWithSeparateTest()

    private fun executor() = project.executor()
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
        .with(BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT, true)
        .withFailOnWarning(false) // kgp uses deprecated api WrapUtil
        .withPerTestPrefsRoot(true)
        .with(BooleanOption.ENABLE_PROFILE_JSON, true) // Regression test for b/237278679
        .with(BooleanOption.PRIVACY_SANDBOX_SDK_REQUIRE_SERVICES, false)

    @Test
    fun getTestOnlyModuleApkOutput() {
        project.getSubproject("example-app-test").buildFile.appendText("""
            import com.android.build.api.variant.ApkInstallGroup
            import com.android.build.api.variant.ApkOutput
            import com.android.build.api.variant.TestVariant
            import com.android.build.api.variant.DeviceSpec

            abstract class FetchApkTask extends DefaultTask {
                @Internal
                abstract Property<ApkOutput> getPrivacySandboxEnabledApkOutput()

                @Internal
                abstract Property<ApkOutput> getPrivacySandboxDisabledApkOutput()

                @TaskAction
                void execute() {
                   def apkInstall = getPrivacySandboxEnabledApkOutput().get().apkInstallGroups
                    if (apkInstall.size() != 4 || apkInstall[0].apks.size() != 1 || apkInstall[1].apks.size() != 1 || apkInstall[2].apks.size() != 2 || apkInstall[3].apks.size() != 1) {
                        throw new GradleException("Unexpected number of apks")
                    }
                    assert apkInstall[0].apks.first().getAsFile().name.contains("extracted-apk.apk")
                    assert apkInstall[0].description.contains("Source Sdk: privacy-sandbox-sdk.apks")

                    assert apkInstall[1].apks.first().getAsFile().name.contains("extracted-apk.apk")
                    assert apkInstall[1].description.contains("Source Sdk: privacy-sandbox-sdk-b.apks")

                    assert apkInstall[2].apks.any { it.getAsFile().name.contains("example-app-debug.apk") }
                    assert apkInstall[2].apks.any { it.getAsFile().name.contains("example-app-debug-injected-privacy-sandbox.apk") }

                    assert apkInstall[3].apks.any { it.getAsFile().name.contains("example-app-test-debug.apk") }
                    assert apkInstall[3].description.contains("Testing Apk")

                    apkInstall = getPrivacySandboxDisabledApkOutput().get().apkInstallGroups
                    if (apkInstall.size() != 2 || apkInstall[0].apks.size() != 4 || apkInstall[1].apks.size() != 1) {
                        throw new GradleException("Unexpected number of apks")
                    }
                    assert apkInstall[0].apks.any { it.getAsFile().name.contains("example-app-debug.apk") }
                    assert apkInstall[0].apks.any { it.getAsFile().absolutePath.contains("splits" + File.separator + "comexampleprivacysandboxsdk-master.apk") }
                    assert apkInstall[0].apks.any { it.getAsFile().absolutePath.contains("splits" + File.separator + "comexampleprivacysandboxsdkb-master.apk") }
                    assert apkInstall[0].apks.any { it.getAsFile().absolutePath.contains("splits" + File.separator + "example-app-debug-injected-privacy-sandbox-compat.apk") }
                    assert apkInstall[1].apks.any { it.getAsFile().name.contains("example-app-test-debug.apk") }
                    assert apkInstall[1].description.contains("Testing Apk")
                }
            }

            def taskProvider = tasks.register("fetchApks", FetchApkTask)

            androidComponents {
                onVariants(selector().withName("debug")) { variant ->
                    if (variant instanceof TestVariant) {
                        TestVariant testVariant = (TestVariant) variant
                        testVariant.outputProviders.provideApkOutputToTask(taskProvider, FetchApkTask::getPrivacySandboxEnabledApkOutput, new DeviceSpec.Builder().setName("testDevice").setApiLevel(33).setCodeName("").setAbis([]).setSupportsPrivacySandbox(true).build())
                        testVariant.outputProviders.provideApkOutputToTask(taskProvider, FetchApkTask::getPrivacySandboxDisabledApkOutput, new DeviceSpec.Builder().setName("testDevice").setApiLevel(33).setCodeName("").setAbis([]).setSupportsPrivacySandbox(false).build())
                    }
                }
            }
        """.trimIndent()
        )

        executor().run(":example-app-test:fetchApks")
    }

}
