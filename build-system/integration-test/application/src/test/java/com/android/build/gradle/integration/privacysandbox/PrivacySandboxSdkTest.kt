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

package com.android.build.gradle.integration.privacysandbox

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.DEFAULT_COMPILE_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.privacysandbox.privacySandboxSampleProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.ide.common.signing.KeystoreHelper
import com.android.testutils.apk.Dex
import com.android.testutils.apk.Zip
import com.android.testutils.truth.PathSubject.assertThat
import com.android.testutils.truth.ZipFileSubject
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Objects
import kotlin.io.path.readText

/** Integration tests for the privacy sandbox SDK */
class PrivacySandboxSdkTest {

    @get:Rule
    val project = privacySandboxSampleProject()

    private fun executor() = project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .with(BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT, true)
            .withFailOnWarning(false) // kgp uses deprecated api WrapUtil
            .withPerTestPrefsRoot(true)
            .with(BooleanOption.ENABLE_PROFILE_JSON, true) // Regression test for b/237278679

    @Test
    fun testDexingWithR8() {
        val privacySandboxSdkProject = project.getSubproject(":privacy-sandbox-sdk")
        privacySandboxSdkProject.buildFile.appendText(
            """
                    android.experimentalProperties["android.experimental.privacysandboxsdk.optimize"] = false
            """
        )
        val dexLocation = project.getSubproject(":privacy-sandbox-sdk")
            .getIntermediateFile("dex", "single", "minifyBundleWithR8", "classes.dex")

        executor().run(":privacy-sandbox-sdk:minifyBundleWithR8")

        Dex(dexLocation).also { dex ->
            assertThat(dex.classes.keys).containsAtLeast(
                "Lcom/example/sdkImplA/Example;",
                "Lcom/example/androidlib/Example;",
                "Lcom/externaldep/externaljar/ExternalClass;"
            )
            assertThat(dex.classes["Lcom/example/sdkImplA/Example;"]!!.methods.map { it.name }).contains(
                "f1")
            assertThat(dex.classes["Lcom/example/androidlib/Example;"]!!.methods.map { it.name }).contains(
                "f2")
            assertThat(dex.classes["Lcom/example/sdkImplA/R\$string;"]!!.fields.map { it.name }).containsExactly(
                "string_from_sdk_impl_a")
        }

        // Check incremental changes are handled
        TestFileUtils.searchAndReplace(
            project.getSubproject("sdk-impl-a")
                .file("src/main/java/com/example/sdkImplA/Example.kt"),
            "fun f1() {}",
            "fun g() {}"
        )

        executor().run(":privacy-sandbox-sdk:minifyBundleWithR8")

        Dex(dexLocation).also { dex ->
            assertThat(dex.classes["Lcom/example/sdkImplA/Example;"]!!.methods.map { it.name }).contains(
                "g")
            assertThat(dex.classes["Lcom/example/androidlib/Example;"]!!.methods.map { it.name }).contains(
                "f2")
        }
    }

    @Test
    fun testDexingWithR8optimization() {
        val privacySandboxSdkProject = project.getSubproject(":privacy-sandbox-sdk")
        privacySandboxSdkProject.buildFile.appendText(
            """
                    android.experimentalProperties["android.experimental.privacysandboxsdk.optimize"] = true
                    android.optimization.keepRules.files.add(new File(project.projectDir, "proguard-rules.pro"))

            """
        )
        val dexLocation = project.getSubproject(":privacy-sandbox-sdk")
            .getIntermediateFile("dex", "single", "minifyBundleWithR8", "classes.dex")

        executor().run(":privacy-sandbox-sdk:minifyBundleWithR8")

        Dex(dexLocation).also { dex ->
            assertThat(dex.classes.keys).doesNotContain(
                "Lcom/example/sdkImplA/Example;"
            )
            assertThat(dex.classes.keys).contains(
                "Lcom/example/androidlib/Example;",
            )
            assertThat(dex.classes["Lcom/example/androidlib/Example;"]!!.methods.map { it.name }).contains(
                "f2")
            // none of the resources should be removed
            assertThat(dex.classes["Lcom/example/sdkImplA/R\$string;"]!!.fields.map { it.name }).containsExactly(
                "string_from_sdk_impl_a")
        }

        // Check incremental changes are handled
        TestFileUtils.searchAndReplace(
            project.getSubproject("android-lib")
                .file("src/main/java/com/example/androidlib/Example.java"),
            "public void f2() {}",
            "public void g() {}"
        )

        executor().run(":privacy-sandbox-sdk:minifyBundleWithR8")

        Dex(dexLocation).also { dex ->
            assertThat(dex.classes["Lcom/example/androidlib/Example;"]!!.methods.map { it.name }).contains(
                "g")
        }
    }

    @Test
    fun testAsb() {
        executor().run(":privacy-sandbox-sdk:assemble")
        val sdkProject = project.getSubproject(":privacy-sandbox-sdk")
        val asbFile = sdkProject.getOutputFile("asb", "single", "privacy-sandbox-sdk.asb")
        val asbManifest = sdkProject.getIntermediateFile(
                                "merged_manifest", "single", "mergeManifest", "AndroidManifest.xml")
        val asbManifestBlameReport = sdkProject.getOutputFile(
                "${SdkConstants.FD_LOGS}/manifest-merger-mergeManifest-report.txt")
        assertThat(asbManifest).hasContents(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="com.example.privacysandboxsdk" >

                    <uses-sdk
                        android:minSdkVersion="23"
                        android:targetSdkVersion="$DEFAULT_COMPILE_SDK_VERSION" />

                    <uses-permission android:name="android.permission.INTERNET" />
                    <uses-permission
                        android:name="com.example.privacysandboxsdkb.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
                        tools:requiredByPrivacySandboxSdk="true" />

                    <permission
                        android:name="com.example.privacysandboxsdk.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
                        android:protectionLevel="signature" />

                    <uses-permission android:name="com.example.privacysandboxsdk.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />

                    <application android:appComponentFactory="androidx.core.app.CoreComponentFactory" >
                        <activity
                            android:name="androidx.privacysandbox.sdkruntime.client.activity.SdkActivity"
                            android:exported="true" />

                        <provider
                            android:name="androidx.startup.InitializationProvider"
                            android:authorities="com.example.privacysandboxsdk.androidx-startup"
                            android:exported="false" >
                            <meta-data
                                android:name="androidx.profileinstaller.ProfileInstallerInitializer"
                                android:value="androidx.startup" />
                        </provider>

                        <receiver
                            android:name="androidx.profileinstaller.ProfileInstallReceiver"
                            android:directBootAware="false"
                            android:enabled="true"
                            android:exported="true"
                            android:permission="android.permission.DUMP" >
                            <intent-filter>
                                <action android:name="androidx.profileinstaller.action.INSTALL_PROFILE" />
                            </intent-filter>
                            <intent-filter>
                                <action android:name="androidx.profileinstaller.action.SKIP_FILE" />
                            </intent-filter>
                            <intent-filter>
                                <action android:name="androidx.profileinstaller.action.SAVE_PROFILE" />
                            </intent-filter>
                            <intent-filter>
                                <action android:name="androidx.profileinstaller.action.BENCHMARK_OPERATION" />
                            </intent-filter>
                        </receiver>
                    </application>

                </manifest>
        """.trimIndent())
        assertThat(asbManifestBlameReport.exists()).isTrue()
        assertThat(asbFile.exists()).isTrue()

        Zip(asbFile).use {
            assertThat(
                    Objects.requireNonNull(it.getEntryAsFile(
                            "BUNDLE-METADATA/com.android.tools.build.gradle/app-metadata.properties"
                    )).readText()
            ).let { metadataContent ->
                metadataContent.contains("appMetadataVersion=")
                metadataContent.contains("androidGradlePluginVersion=")
            }

            assertThat(it.getEntry("SdkBundleConfig.pb")).isNotNull()

            ZipFileSubject.assertThat(
                    Objects.requireNonNull(it.getEntryAsFile("modules.resm"))
            ) { modules ->

                modules.contains("base/dex/classes.dex")
                modules.contains("base/assets/asset_from_sdkImplA.txt")
                modules.contains("base/manifest/AndroidManifest.xml")
                modules.contains("base/resources.pb")
                modules.contains("base/root/my_java_resource.txt")
                modules.contains("SdkModulesConfig.pb")
            }
        }
    }

    @Test
    fun testAsbSigning() {
        val privacySandboxSdkProject = project.getSubproject(":privacy-sandbox-sdk")
        val storeType = "jks"
        val storeFile = project.file("privacysandboxsdk.jks")
        val storePassword = "rbStore123"
        val keyPassword = "rbKey123"
        val keyAlias = "privacysandboxsdkkey"
        KeystoreHelper.createNewStore(
                storeType,
                storeFile,
                storePassword,
                keyPassword,
                keyAlias,
                "CN=Privacy Sandbox Sdk test",
                100
        )
        privacySandboxSdkProject.buildFile.appendText(
                """
                    android.signingConfig {
                                storeFile = file("${storeFile.absolutePath.replace("\\", "\\\\")}")
                                keyAlias = "$keyAlias"
                                keyPassword = "$keyPassword"
                                storeType = "$storeType"
                                storePassword = "$storePassword"
                            }
                            """
        )
        executor().run(":privacy-sandbox-sdk:assemble")
        val asbFile =
                privacySandboxSdkProject.getOutputFile("asb", "single", "privacy-sandbox-sdk.asb")
        Zip(asbFile).use {
            assertThat(it.getEntry("/META-INF/MANIFEST.MF")).isNotNull()
            assertThat(it.getEntry("/META-INF/PRIVACYS.RSA")).isNotNull()
            assertThat(it.getEntry("/META-INF/PRIVACYS.SF")).isNotNull()
        }
    }

    @Test
    fun checkKsp() {
        val sdkImplA = project.getSubproject("sdk-impl-a")
        val pkg =
                FileUtils.join(sdkImplA.mainSrcDir, "com", "example", "sdkImplA")
        val mySdkFile = File(pkg, "MySdk.kt")

        // Invalid usage of @PrivacySandboxSdk as interface contains two methods with the same name.
        mySdkFile.writeText(
                "package com.example.sdkImplA\n" +
                        "import androidx.privacysandbox.tools.PrivacySandboxService\n" +
                        "   @PrivacySandboxService\n" +
                        "   public interface MySdk {\n" +
                        "       suspend fun doStuff(x: Int, y: Int): String\n" +
                        "       suspend fun doStuff(x: Int, y: Int)\n" +
                        "   }\n"
        )

        executor().expectFailure().run("sdk-impl-a:build")

        mySdkFile.writeText(
                "package com.example.sdkImplA\n" +
                        "import androidx.privacysandbox.tools.PrivacySandboxService\n" +
                        "   @PrivacySandboxService\n" +
                        "   public interface MySdk {\n" +
                        "       suspend fun doStuff(x: Int, y: Int): String\n" +
                        "   }\n"
        )
        executor().run("sdk-impl-a:build")

        val kspDir = FileUtils.join(sdkImplA.generatedDir, "ksp")
        assertThat(kspDir.exists()).isTrue()
    }

    @Test
    fun testNoServiceDefinedInModuleUsedBySdk() {
        executor()
                .withFailOnWarning(false) // kgp uses deprecated api WrapUtil
                .with(BooleanOption.PRIVACY_SANDBOX_SDK_REQUIRE_SERVICES, true)
                .expectFailure()
                .run(":example-app:assembleDebug")
                .also {
                    assertThat(it.failureMessage).contains(
                            "Unable to proceed generating shim with no provided sdk descriptor entries in:")
                }

        executor()
                .withFailOnWarning(false) // kgp uses deprecated api WrapUtil
                .with(BooleanOption.PRIVACY_SANDBOX_SDK_REQUIRE_SERVICES, false)
                .run(":example-app:assembleDebug")
    }

    @Test
    fun testProguardRulesGeneration() {
        TestFileUtils.searchAndReplace(
            project.getSubproject(":privacy-sandbox-sdk").buildFile,
            """compatSdkProviderClassName = "Test"""",
            ""
        )
        TestFileUtils.searchAndReplace(
            project.getSubproject(":privacy-sandbox-sdk").buildFile,
            """sdkProviderClassName = "Test"""",
            ""
        )
        executor().run(":privacy-sandbox-sdk:generatePrivacySandboxProguardRules")
    }

    @Test
    fun testTargetSdkVersion() {
        project.getSubproject(":privacy-sandbox-sdk").buildFile.appendText("\nandroid.targetSdk 33")
        executor().run(":privacy-sandbox-sdk:assemble")
        val sdkProject = project.getSubproject(":privacy-sandbox-sdk")
        val asbManifest = sdkProject.getIntermediateFile(
            "merged_manifest", "single", "mergeManifest", "AndroidManifest.xml")
        val manifestLines = asbManifest.readLines()
        assertThat(manifestLines).containsAtLeastElementsIn(
            listOf(
                "    <uses-sdk",
                "        android:minSdkVersion=\"23\"",
                "        android:targetSdkVersion=\"33\" />"
            )
        )
    }
}
