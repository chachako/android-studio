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

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.privacysandbox.privacySandboxSampleProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.TestUtils
import com.android.testutils.apk.Apk
import com.android.testutils.apk.Dex
import com.android.tools.apk.analyzer.AaptInvoker
import com.android.utils.FileUtils
import com.android.utils.StdLogger
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.io.path.name
import org.junit.Ignore

/** Integration tests for the privacy sandbox SDK to SDK dependency */
class PrivacySandboxSdkToSdkTest {
    @get:Rule
    val project = privacySandboxSampleProject()

    private fun executor() = project.executor()
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
        .with(BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT, true)
        .withFailOnWarning(false) // kgp uses deprecated api WrapUtil
        .withPerTestPrefsRoot(true)
        .with(BooleanOption.ENABLE_PROFILE_JSON, true) // Regression test for b/237278679

    @Test
    fun testSdkToSdk() {
        // There is usage of the privacy-sandbox-sdk-b Shim generated symbols in
        // privacy-sandbox-sdk, therefore the test passes if the project compiles.
        TestFileUtils.searchAndReplace(
            project.getSubproject(":sdk-impl-a")
                .mainSrcDir.resolve("com/example/sdkImplA/Example.kt"),
            "companion object {}",
            "companion object {\n" +
                    "    val useSymbolFromPrivacySandboxB = object : com.example.sdkImplB.MySdkB {\n" +
                    "        override suspend fun f1(p1: Int): Int {\n" +
                    "            return p1\n" +
                    "        }\n" +
                    "    }\n" +
                    "}")
        executor().run(":sdk-impl-a:assembleDebug")

        val aar =
            FileUtils.join(project.getSubproject(":sdk-impl-a").outputDir,
                "aar",
                "sdk-impl-a-debug.aar")
        Apk(aar).use { sdkImplAAar ->
            val entries = sdkImplAAar.entries.map { it.name }
            Truth.assertThat(entries).contains(
                SdkConstants.FN_CLASSES_JAR
            )
            ZipFile(sdkImplAAar.getEntryAsFile("/${SdkConstants.FN_CLASSES_JAR}")
                .toFile()).use { sdkImplAClassesJar ->
                Truth.assertThat(sdkImplAClassesJar.entries()
                    .toList()
                    .map { it.name }
                    .filter { it.endsWith(SdkConstants.EXT_CLASS) }).containsExactly(
                    "com/example/sdkImplA/Example\$Companion\$useSymbolFromPrivacySandboxB$1.class",
                    "com/example/sdkImplA/Example\$Companion.class",
                    "com/example/sdkImplA/Example.class"
                )
            }
        }

        assertGeneratedConsumptionShimFromSdkB("privacy-sandbox-sdk")

        executor().run(":example-app:buildPrivacySandboxSdkApksForDebug")

        val standaloneSdkApk =
            project.getSubproject(":example-app")
                .getIntermediateFile(
                    InternalArtifactType.EXTRACTED_APKS_FROM_PRIVACY_SANDBOX_SDKs.getFolderName(),
                    "debug",
                    "buildPrivacySandboxSdkApksForDebug",
                    "privacy-sandbox-sdk",
                    "standalone.apk")
        val protoApkDump =
            AaptInvoker(TestUtils.getAapt2(), StdLogger(StdLogger.Level.VERBOSE)).dumpResources(
                standaloneSdkApk)
        val dumpedRes =
            protoApkDump.map { it.trim().removeSuffix(" PUBLIC") }
                .filter { it.startsWith("resource") }
                .map { it.substringAfterLast("/") }
        // Verify that resources from non directly used SDKs are excluded from the APK.
        Truth.assertThat(dumpedRes).contains("string_from_sdk_impl_a")
        Truth.assertThat(dumpedRes).doesNotContain("string_from_sdk_impl_b")

        // Verify resources from an SDK are not able to be referenced from another SDK.
        val sdkImplAResValues = FileUtils.join(
            project.getSubproject(":sdk-impl-a").mainResDir, "values")
        sdkImplAResValues.also {
            it.mkdirs()
            // Referencing a resource from another SDK should not be possible, expect a failure
            File(it, "strings.xml").writeText("""
              <resources>
                <string name="ref_to_sdk_lib_b">@string/string_from_sdk_impl_b</string>
              </resources>""".trimIndent()
            )
        }
        val buildFailureDuringResourceLinking =
            executor().expectFailure().run(":privacy-sandbox-sdk:linkPrivacySandboxResources")
        buildFailureDuringResourceLinking.assertErrorContains(
            "error: resource string/string_from_sdk_impl_b (aka com.example.privacysandboxsdk:string/string_from_sdk_impl_b) not found.")
        Files.delete(sdkImplAResValues.resolve("strings.xml").toPath())
    }

    private fun assertGeneratedConsumptionShimFromSdkB(sdkProjectName: String) {
        executor().run(":$sdkProjectName:minifyBundleWithR8")
        val dexLocation = project.getSubproject(":$sdkProjectName")
            .getIntermediateFile("dex", "single", "minifyBundleWithR8", "classes.dex")

        Dex(dexLocation).also { dex ->
            assertThat(dex.classes.keys).containsAtLeast(
                "Lcom/example/sdkImplB/MySdkB;",
                "Lcom/example/sdkImplB/ICancellationSignal;",
                "Lcom/example/sdkImplB/ICancellationSignal\$Default;",
                "Lcom/example/sdkImplB/ICancellationSignal\$Stub\$Proxy;",
                "Lcom/example/sdkImplB/ICancellationSignal\$Stub;",
                "Lcom/example/sdkImplB/IIntTransactionCallback;",
                "Lcom/example/sdkImplB/IIntTransactionCallback\$Default;",
                "Lcom/example/sdkImplB/IIntTransactionCallback\$Stub\$Proxy;",
                "Lcom/example/sdkImplB/IIntTransactionCallback\$Stub;",
                "Lcom/example/sdkImplB/IIntTransactionCallback\$_Parcel;",
                "Lcom/example/sdkImplB/IMySdkB;",
                "Lcom/example/sdkImplB/IMySdkB\$Default;",
                "Lcom/example/sdkImplB/IMySdkB\$Stub\$Proxy;",
                "Lcom/example/sdkImplB/IMySdkB\$Stub;",
                "Lcom/example/sdkImplB/MySdkBClientProxy\$f1\$2\$1;",
                "Lcom/example/sdkImplB/MySdkBClientProxy\$f1\$2\$transactionCallback\$1;",
                "Lcom/example/sdkImplB/MySdkBClientProxy;",
                "Lcom/example/sdkImplB/MySdkBFactory;",
                "Lcom/example/sdkImplB/ParcelableStackFrame\$1;",
                "Lcom/example/sdkImplB/ParcelableStackFrame;",
                "Lcom/example/sdkImplB/PrivacySandboxCancellationException;",
                "Lcom/example/sdkImplB/PrivacySandboxException;",
                "Lcom/example/sdkImplB/PrivacySandboxThrowableParcel\$1;",
                "Lcom/example/sdkImplB/PrivacySandboxThrowableParcel;",
                "Lcom/example/sdkImplB/PrivacySandboxThrowableParcelConverter;",
                "Lcom/example/sdkImplB/TransportCancellationCallback;",
            )
        }
    }
}
