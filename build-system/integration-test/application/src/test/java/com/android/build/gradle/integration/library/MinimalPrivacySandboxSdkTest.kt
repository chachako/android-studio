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

package com.android.build.gradle.integration.library

import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.integration.common.utils.SdkHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.sdklib.BuildToolInfo
import com.android.testutils.apk.Apk
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File

class MinimalPrivacySandboxSdkTest {

    @get:Rule
    val project = createGradleProjectBuilder {
        val aidlPath = SdkHelper.getBuildTool(BuildToolInfo.PathId.AIDL).absolutePath
                .replace("""\""", """\\""")
        val androidxPrivacySandboxSdkVersion = "1.0.0-alpha02"
        subProject(":androidlib3") {
            useNewPluginsDsl = true
            plugins.add(PluginType.ANDROID_LIB)
            plugins.add(PluginType.KOTLIN_ANDROID)
            plugins.add(PluginType.KSP)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidlib3"
                minSdk = 14
                compileSdkPreview = "TiramisuPrivacySandbox"
            }
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.3")
                implementation("androidx.privacysandbox.tools:tools:$androidxPrivacySandboxSdkVersion")
                implementation("androidx.privacysandbox.sdkruntime:sdkruntime-core:$androidxPrivacySandboxSdkVersion")
                implementation("androidx.privacysandbox.sdkruntime:sdkruntime-client:$androidxPrivacySandboxSdkVersion")

                ksp("androidx.privacysandbox.tools:tools-apicompiler:$androidxPrivacySandboxSdkVersion")
            }
            appendToBuildFile {
                """
                   def aidlCompilerPath = '$aidlPath'
                   ksp { arg("aidl_compiler_path", aidlCompilerPath) }
                """
            }
            addFile("src/main/AndroidManifest.xml", """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                </manifest>
                """.trimIndent()
            )
        }
        subProject(":empty-privacy-sandbox-sdk") {
            plugins.add(PluginType.PRIVACY_SANDBOX_SDK)
            android {
                defaultCompileSdk()
                minSdk = 14
            }
            appendToBuildFile {
                """
                        android {
                            bundle {
                                applicationId = "com.example.emptyprivacysandboxsdk"
                                sdkProviderClassName = "Test"
                                setVersion(1, 2, 3)
                            }
                        }
                    """.trimIndent()
            }
            dependencies {
                include(project(":androidlib3"))
            }
        }
        subProject(":minimal-app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                defaultCompileSdk()
                minSdk = 14
                namespace = "com.example.emptyprivacysandboxsdk.consumer"
            }
            dependencies {
                implementation(project(":empty-privacy-sandbox-sdk"))
            }
            appendToBuildFile { //language=groovy
                """
                    android {
                        defaultConfig {
                            versionCode = 1
                        }
                    }
                """.trimIndent()
            }
        }
    }.withKotlinGradlePlugin(true)
            .addGradleProperties("${BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT.propertyName}=true")
            .addGradleProperties("${BooleanOption.USE_ANDROID_X.propertyName}=true")
            .create()

    // Test disabled due to https://github.com/google/ksp/issues/1050
    // that is impacting the current KSP version in prebuilts.
    @Ignore
    @Test
    fun privacySandboxWithMinimalConfigAndDependency() {
        project.execute(":minimal-app:buildPrivacySandboxSdkApksForDebug")
        val minimalApp = project.getSubproject("minimal-app")
        val extractedPssApk = minimalApp.getIntermediateFile(
                InternalArtifactType.EXTRACTED_APKS_FROM_PRIVACY_SANDBOX_SDKs.getFolderName(),
                "debug",
                "empty-privacy-sandbox-sdk/standalone.apk"
        )
        Apk(extractedPssApk).use { apk ->
            ApkSubject.assertThat(apk).exists()
            val manifest = ApkSubject.getManifestContent(extractedPssApk.toPath())
            Truth.assertThat(manifest).containsExactly(
                    "N: android=http://schemas.android.com/apk/res/android (line=2)",
                    "  E: manifest (line=2)",
                    "    A: http://schemas.android.com/apk/res/android:versionCode(0x0101021b)=1",
                    "    A: http://schemas.android.com/apk/res/android:versionName(0x0101021c)=\"1.2.3\" (Raw: \"1.2.3\")",
                    "    A: http://schemas.android.com/apk/res/android:compileSdkVersion(0x01010572)=33",
                    "    A: http://schemas.android.com/apk/res/android:compileSdkVersionCodename(0x01010573)=\"13\" (Raw: \"13\")",
                    "    A: package=\"com.example.emptyprivacysandboxsdk_10002\" (Raw: \"com.example.emptyprivacysandboxsdk_10002\")",
                    "    A: platformBuildVersionCode=33",
                    "    A: platformBuildVersionName=13",
                    "      E: uses-sdk (line=5)",
                    "        A: http://schemas.android.com/apk/res/android:minSdkVersion(0x0101020c)=33",
                    "      E: application (line=7)",
                    "          E: sdk-library (line=0)",
                    "            A: http://schemas.android.com/apk/res/android:name(0x01010003)=\"com.example.emptyprivacysandboxsdk\" (Raw: \"com.example.emptyprivacysandboxsdk\")",
                    "            A: http://schemas.android.com/apk/res/android:versionMajor(0x01010577)=10002",
                    "          E: property (line=0)",
                    "            A: http://schemas.android.com/apk/res/android:name(0x01010003)=\"shadow.bundletool.com.android.vending.sdk.version.patch\" (Raw: \"shadow.bundletool.com.android.vending.sdk.version.patch\")",
                    "            A: http://schemas.android.com/apk/res/android:value(0x01010024)=3",
                    "          E: property (line=0)",
                    "            A: http://schemas.android.com/apk/res/android:name(0x01010003)=\"android.sdksandbox.PROPERTY_SDK_PROVIDER_CLASS_NAME\" (Raw: \"android.sdksandbox.PROPERTY_SDK_PROVIDER_CLASS_NAME\")",
                    "            A: http://schemas.android.com/apk/res/android:value(0x01010024)=\"Test\" (Raw: \"Test\")"
            )
            Truth.assertThat(apk.entries.map { it.toString() }).containsExactly(
                    "/resources.arsc",
                    "/kotlin/reflect/reflect.kotlin_builtins",
                    "/kotlin/ranges/ranges.kotlin_builtins",
                    "/kotlin/kotlin.kotlin_builtins",
                    "/kotlin/internal/internal.kotlin_builtins",
                    "/kotlin/coroutines/coroutines.kotlin_builtins",
                    "/kotlin/collections/collections.kotlin_builtins",
                    "/kotlin/annotation/annotation.kotlin_builtins",
                    "/classes2.dex",
                    "/classes.dex",
                    "/META-INF/tools.kotlin_module",
                    "/META-INF/sdkruntime-core_release.kotlin_module",
                    "/META-INF/sdkruntime-client_release.kotlin_module",
                    "/META-INF/kotlinx_coroutines_core.version",
                    "/META-INF/kotlinx-coroutines-core.kotlin_module",
                    "/META-INF/kotlin-stdlib.kotlin_module",
                    "/META-INF/kotlin-stdlib-jdk8.kotlin_module",
                    "/META-INF/kotlin-stdlib-jdk7.kotlin_module",
                    "/META-INF/kotlin-stdlib-common.kotlin_module",
                    "/META-INF/androidx.privacysandbox.sdkruntime_sdkruntime-core.version",
                    "/META-INF/androidx.privacysandbox.sdkruntime_sdkruntime-client.version",
                    "/DebugProbesKt.bin",
                    "/AndroidManifest.xml"
            )
        }
    }


    // Test disabled due to https://github.com/google/ksp/issues/1050
    // that is impacting the current KSP version in prebuilts.
    @Ignore
    @Test
    fun testAssemble() {
        val androidLib3SrcMainSrcJava =
                FileUtils.join(project.getSubproject("androidlib3").mainSrcDir,
                        "com",
                        "example",
                        "androidlib3")
        androidLib3SrcMainSrcJava.mkdirs()

        val sdkServiceFile = File(androidLib3SrcMainSrcJava, "MySdk.kt").also {
            it.writeText(
                    """package com.example.androidlib3
                            import androidx.privacysandbox.tools.PrivacySandboxService
                            @PrivacySandboxService
                            public interface MySdk {
                                suspend fun foo(bar: Int): String
                            }
                        """
            )
        }


        project.executor().run(":minimal-app:assembleDebug")

        FileUtils.delete(sdkServiceFile)

        project.executor().expectFailure().run(":minimal-app:assembleDebug").also {
            Truth.assertThat(it.failureMessage).contains(
                    "Unable to proceed generating shim with no provided sdk descriptor entries in: ")
        }

    }

    @Ignore("Re-enable once we use a newer KSP version -- see bug 271248269")
    @Test
    fun checkPrivacySandboxOptInRequired() {
        TestFileUtils.searchAndReplace(
                project.file("gradle.properties"),
                BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT.propertyName,
                "# " + BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT.propertyName)
        val result = project.executor()
                .expectFailure()
                .run(":minimal-app:buildPrivacySandboxSdkApksForDebug")
        assertThat(result.stderr).contains(
                """
                    Privacy Sandbox SDK support is experimental, and must be explicitly enabled.
                    To enable support, add
                        android.experimental.privacysandboxsdk.enable=true
                    to your project's gradle.properties file.
                """.trimIndent()
        )

    }
}
