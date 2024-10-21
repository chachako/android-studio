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
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.privacysandbox.privacySandboxSampleProject
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test

class PrivacySandboxDefaultApkOutputTest {
    companion object {
        fun getBuildFileContentWithFetchTask(verificationString: String) = """

            import com.android.build.api.variant.ApkInstallGroup
            import com.android.build.api.variant.ApkOutput
            import com.android.build.api.variant.ApplicationVariant
            import com.android.build.api.variant.DeviceSpec

            abstract class FetchApkTask extends DefaultTask {
                @Internal
                abstract Property<ApkOutput> getPrivacySandboxEnabledApkOutput()

                @Internal
                abstract Property<ApkOutput> getPrivacySandboxDisabledApkOutput()

                @TaskAction
                void execute() {
                    $verificationString
                }
            }

            def taskProvider = tasks.register("fetchApks", FetchApkTask)

            androidComponents {
                onVariants(selector().withName("debug")) { variant ->
                    if (variant instanceof ApplicationVariant) {
                        ApplicationVariant appVariant = (ApplicationVariant) variant
                        appVariant.outputProviders.provideApkOutputToTask(taskProvider, FetchApkTask::getPrivacySandboxEnabledApkOutput, new DeviceSpec.Builder().setName("testDevice").setApiLevel(34).setCodeName("").setAbis([]).setSupportsPrivacySandbox(true).build())
                        appVariant.outputProviders.provideApkOutputToTask(taskProvider, FetchApkTask::getPrivacySandboxDisabledApkOutput, new DeviceSpec.Builder().setName("testDevice").setApiLevel(34).setCodeName("").setAbis([]).setSupportsPrivacySandbox(false).build())
                    }
                }
            }
        """.trimIndent()
        val viaBundleVerificationString = """
                    def apkInstall = getPrivacySandboxEnabledApkOutput().get().apkInstallGroups
                    if (apkInstall.size() != 3 || apkInstall[0].apks.size() != 1 || apkInstall[1].apks.size() != 1 || apkInstall[2].apks.size() != 1) {
                        throw new GradleException("Unexpected number of apks")
                    }
                    assert apkInstall[0].apks.first().getAsFile().name.contains("extracted-apk.apk")
                    assert apkInstall[0].description.contains("Source Sdk: privacy-sandbox-sdk.apks")

                    assert apkInstall[1].apks.first().getAsFile().name.contains("extracted-apk.apk")
                    assert apkInstall[1].description.contains("Source Sdk: privacy-sandbox-sdk-b.apks")

                    assert apkInstall[2].apks.first().asFile.name.contains("base-master_3.apk")
                    assert apkInstall[2].description.contains("Apks from Main Bundle")

                    apkInstall = getPrivacySandboxDisabledApkOutput().get().apkInstallGroups
                    if (apkInstall.size() != 1 || apkInstall[0].apks.size() != 3) {
                        throw new GradleException("Unexpected number of apks")
                    }
                    assert apkInstall[0].apks.any { it.getAsFile().name.contains("base-master_2.apk") }
                    assert apkInstall[0].apks.any { it.getAsFile().absolutePath.contains("comexampleprivacysandboxsdk-master.apk") }
                    assert apkInstall[0].apks.any { it.getAsFile().absolutePath.contains("comexampleprivacysandboxsdkb-master.apk") }
        """.trimIndent()

        val skipApkViaBundleVerificationString = """
                    def apkInstall = getPrivacySandboxEnabledApkOutput().get().apkInstallGroups
                    if (apkInstall.size() != 3 || apkInstall[0].apks.size() != 1 || apkInstall[1].apks.size() != 1 || apkInstall[2].apks.size() != 2) {
                        throw new GradleException("Unexpected number of apks")
                    }
                    assert apkInstall[0].apks.first().getAsFile().name.contains("extracted-apk.apk")
                    assert apkInstall[0].description.contains("Source Sdk: privacy-sandbox-sdk.apks")

                    assert apkInstall[1].apks.first().getAsFile().name.contains("extracted-apk.apk")
                    assert apkInstall[1].description.contains("Source Sdk: privacy-sandbox-sdk-b.apks")

                    assert apkInstall[2].apks.any { it.getAsFile().name.contains("example-app-debug.apk") }
                    assert apkInstall[2].apks.any { it.getAsFile().name.contains("example-app-debug-injected-privacy-sandbox.apk") }
                    apkInstall = getPrivacySandboxDisabledApkOutput().get().apkInstallGroups
                    if (apkInstall.size() != 1 || apkInstall[0].apks.size() != 4) {
                        throw new GradleException("Unexpected number of apks")
                    }
                    assert apkInstall[0].apks.any { it.getAsFile().name.contains("example-app-debug.apk") }
                    assert apkInstall[0].apks.any { it.getAsFile().absolutePath.contains("splits" + File.separator + "comexampleprivacysandboxsdk-master.apk") }
                    assert apkInstall[0].apks.any { it.getAsFile().absolutePath.contains("splits" + File.separator + "comexampleprivacysandboxsdkb-master.apk") }
                    assert apkInstall[0].apks.any { it.getAsFile().absolutePath.contains("splits" + File.separator + "example-app-debug-injected-privacy-sandbox-compat.apk") }
        """.trimIndent()
    }

    @JvmField
    @Rule
    val project = privacySandboxSampleProject()

    private fun executor() = project.executor()
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
        .with(BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT, true)
        .withFailOnWarning(false) // kgp uses deprecated api WrapUtil
        .withPerTestPrefsRoot(true)
        .with(BooleanOption.ENABLE_PROFILE_JSON, true) // Regression test for b/237278679
        .with(BooleanOption.PRIVACY_SANDBOX_SDK_REQUIRE_SERVICES, false)

    @Test
    fun getApkOutput() {
        project.getSubproject("example-app").buildFile.appendText(
            getBuildFileContentWithFetchTask(skipApkViaBundleVerificationString))

        executor()
            .with(BooleanOption.SKIP_APKS_VIA_BUNDLE_IF_POSSIBLE, true)
            .run(":example-app:fetchApks")
    }

    @Test
    fun getViaBundleApkOutput() {
        project.getSubproject("example-app").buildFile.appendText(
            getBuildFileContentWithFetchTask(viaBundleVerificationString))

        executor().run(":example-app:fetchApks")
    }
}
