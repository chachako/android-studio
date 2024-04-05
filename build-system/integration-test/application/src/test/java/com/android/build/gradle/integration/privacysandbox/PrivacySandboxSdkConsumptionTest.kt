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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.ProfileCapturer
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.privacysandbox.privacySandboxSampleProject
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.builder.model.v2.ide.SyncIssue
import com.android.ide.common.build.GenericBuiltArtifactsLoader
import com.android.testutils.apk.Apk
import com.android.utils.FileUtils
import com.android.utils.StdLogger
import com.google.protobuf.TextFormat
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Path

/** Integration tests for the privacy sandbox SDK for consumption */
class PrivacySandboxSdkConsumptionTest {

    @get:Rule
    val project = privacySandboxSampleProject()

    private fun executor() = project.executor()
            .with(BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT, true)
            .withFailOnWarning(false) // kgp uses deprecated api WrapUtil
            .withPerTestPrefsRoot(true)
            .with(BooleanOption.ENABLE_PROFILE_JSON, true) // Regression test for b/237278679

    private fun modelV2() = project.modelV2()
            .withFailOnWarning(false) // kgp uses deprecated api WrapUtil
            .withPerTestPrefsRoot(true)
            .with(BooleanOption.ENABLE_PROFILE_JSON, true) // Regression test for b/237278679
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)

    @Test
    fun testConsumptionViaBundle() {
        // TODO(b/235469089) expand this to verify installation also

        //Add service to sdk-impl-a
        val pkg = FileUtils.join(project.getSubproject("sdk-impl-a").mainSrcDir,
                "com",
                "example",
                "sdkImplA")
        val mySdkFile = File(pkg, "MySdk.kt")
        mySdkFile.writeText(
                "package com.example.sdkImplA\n" +
                        "import androidx.privacysandbox.tools.PrivacySandboxService\n" +
                        "   @PrivacySandboxService\n" +
                        "   public interface MySdk {\n" +
                        "       suspend fun foo(bar: Int): String\n" +
                        "   }\n"
        )

        // Check building the SDK itself
        executor().run(":example-app:buildPrivacySandboxSdkApksForDebug")

        val privacySandboxSdkApk = project.getSubproject(":example-app")
                .getIntermediateFile("extracted_apks_from_privacy_sandbox_sdks",
                        "debug",
                        "buildPrivacySandboxSdkApksForDebug",
                        "privacy-sandbox-sdk",
                        "standalone.apk")

        Apk(privacySandboxSdkApk).use {
            ApkSubject.assertThat(it).containsClass(SDK_IMPL_A_CLASS)
            ApkSubject.assertThat(it).containsClass("Lcom/example/androidlib/Example;")
            ApkSubject.assertThat(it).containsClass("Lcom/example/androidlib/R;")
            ApkSubject.assertThat(it).containsClass("Lcom/example/privacysandboxsdk/RPackage;")
            ApkSubject.assertThat(it).containsClass("Lcom/example/sdkImplA/R\$string;")
            ApkSubject.assertThat(it).containsClass("Lcom/example/sdkImplA/R;")
            ApkSubject.assertThat(it).containsClass("Lcom/externaldep/externaljar/ExternalClass;")
        }

        // Check building the bundle to deploy to UpsideDownCake
        val apkSelectConfig = project.file("apkSelectConfig.json")
        apkSelectConfig.writeText(
                """{"sdk_version":34,"sdk_runtime":{"supported":"true"},"screen_density":420,"supported_abis":["x86_64","arm64-v8a"],"supported_locales":["en"]}""")

        executor()
                .with(StringOption.IDE_APK_SELECT_CONFIG, apkSelectConfig.absolutePath)
                .run(":example-app:extractApksFromBundleForDebug")

        val extractedApks = project.getSubproject(":example-app")
                .getIntermediateFile("extracted_apks", "debug", "extractApksFromBundleForDebug")
                .toPath()
        val baseMaster2Apk = extractedApks.resolve("base-master_2.apk")
        val baseMaster3Apk = extractedApks.resolve("base-master_3.apk")

        Apk(baseMaster2Apk).use {
            ApkSubject.assertThat(it).doesNotExist()
        }
        // Expect the first assignment of certDigest to be the same for all modules.
        val certDigest: String
        Apk(baseMaster3Apk).use {
            ApkSubject.assertThat(it).exists()
            ApkSubject.assertThat(it).containsClass("Lcom/example/privacysandboxsdk/consumer/R;")
            ApkSubject.assertThat(it).doesNotContainClass(SDK_IMPL_A_CLASS)
            val manifestContent = ApkSubject.getManifestContent(it.file)
            val manifestContentStr = manifestContent.joinToString("\n")
            certDigest = certDigestPattern.find(manifestContentStr)?.value!!
            assertThat(manifestContentStr)
                    .contains(MY_PRIVACY_SANDBOX_SDK_MANIFEST_PACKAGE)
            assertThat(manifestContent).containsAtLeastElementsIn(
                    listOf(
                            "          E: uses-sdk-library (line=0)",
                            "            A: http://schemas.android.com/apk/res/android:name(0x01010003)=\"com.example.privacysandboxsdk\" (Raw: \"com.example.privacysandboxsdk\")",
                            "            A: http://schemas.android.com/apk/res/android:certDigest(0x01010548)=\"$certDigest\" (Raw: \"$certDigest\")",
                            "            A: http://schemas.android.com/apk/res/android:versionMajor(0x01010577)=\"10002\" (Raw: \"10002\")"
                    )
            )
            assertThat(manifestContentStr)
                    .doesNotContain(INTERNET_PERMISSION)
            assertThat(manifestContentStr)
                    .doesNotContain(FOREGROUND_SERVICE)

        }

        // Check building the bundle to deploy to a non-privacy sandbox device:
        apkSelectConfig.writeText(
                """{"sdk_version":32,"codename":"Tiramisu","screen_density":420,"supported_abis":["x86_64","arm64-v8a"],"supported_locales":["en"]}""")

        executor()
                .with(StringOption.IDE_APK_SELECT_CONFIG, apkSelectConfig.absolutePath)
                .run(":example-app:extractApksFromBundleForDebug")

        Apk(baseMaster2Apk).use {
            ApkSubject.assertThat(it).exists()
            ApkSubject.assertThat(it).containsClass("Lcom/example/privacysandboxsdk/consumer/R;")
            ApkSubject.assertThat(it).hasClass("Lcom/example/privacysandboxsdk/RPackage;")
                    .that()
                    .hasExactFields(mutableSetOf("packageId"))
            val rPackageClass = it.getClass("Lcom/example/privacysandboxsdk/RPackage;")
            assertThat(rPackageClass.fields.single().initialValue?.toString()).isEqualTo("0x7e000000")
            ApkSubject.assertThat(it).doesNotContainClass(SDK_IMPL_A_CLASS)
            val manifestContent = ApkSubject.getManifestContent(it.file).joinToString("\n")
            assertThat(manifestContent)
                    .doesNotContain(USES_SDK_LIBRARY_MANIFEST_ELEMENT)
            assertThat(manifestContent)
                    .doesNotContain(MY_PRIVACY_SANDBOX_SDK_MANIFEST_PACKAGE)
        }
        Apk(baseMaster3Apk).use {
            ApkSubject.assertThat(it).doesNotExist()
        }
    }

    @Test
    fun testConsumptionViaApk() {
        declarePrivacySandboxSdkServiceOnSdkA()
        val model =
                modelV2().with(BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT, true)
                        .fetchModels().container.getProject(":example-app")
        val exampleAppDebug = model.androidProject!!.variants.single { it.name == "debug" }
        val privacySandboxSdkInfo = exampleAppDebug.mainArtifact.privacySandboxSdkInfo!!

        val profiles = ProfileCapturer(project).capture {
            executor().with(BooleanOption.PRIVACY_SANDBOX_SDK_REQUIRE_SERVICES, false)
                    .run(exampleAppDebug.mainArtifact.assembleTaskName,
                            privacySandboxSdkInfo.task,
                            privacySandboxSdkInfo.taskLegacy,
                            privacySandboxSdkInfo.additionalApkSplitTask)
        }

        val actualMetricsMetadata = profiles.single().projectList.single { it.androidPlugin == GradleBuildProject.PluginType.APPLICATION }.variantList.single { it.isDebug }.privacySandboxDependenciesInfo
        assertThat(TextFormat.printer().printToString(actualMetricsMetadata).trim()).isEqualTo("""
            sdk {
              package_name: "com.example.privacysandboxsdk"
              version_major: 1
              version_minor: 2
              build_time_version_patch: 3
            }
            sdk {
              package_name: "com.example.privacysandboxsdkb"
              version_major: 1
              version_minor: 2
              build_time_version_patch: 3
            }
            """.trimIndent())

        Apk(project.getSubproject(":example-app")
                .getApk(GradleTestProject.ApkType.DEBUG).file).use {
            ApkSubject.assertThat(it).exists()
            val manifestContent = ApkSubject.getManifestContent(it.file).joinToString("\n")
            // This asset must only be packaged in non-sandbox capable devices, otherwise it may
            // cause runtime exceptions on supported privacy sandbox platforms.
            assertThat(it.entries.map { it.toString() })
                    .doesNotContain(RUNTIME_ENABLED_SDK_TABLE_ASSET_FOR_COMPAT)

            assertThat(manifestContent).contains(INTERNET_PERMISSION)
            assertThat(manifestContent)
                    .doesNotContain(FOREGROUND_SERVICE)
            assertThat(manifestContent)
                    .doesNotContain(USES_SDK_LIBRARY_MANIFEST_ELEMENT)
        }
        val usesSdkLibrarySplitPath =
                GenericBuiltArtifactsLoader.loadListFromFile(privacySandboxSdkInfo.additionalApkSplitFile,
                        LoggerWrapper.getLogger(PrivacySandboxSdkConsumptionTest::class.java))
                        .elementAt(0).elements.first().outputFile
        Apk(File(usesSdkLibrarySplitPath)).use {
            ApkSubject.assertThat(it).exists()
            val manifestContent = ApkSubject.getManifestContent(it.file).joinToString("\n", postfix = "\n")
            val certDigest = certDigestPattern.find(manifestContent)?.value ?: error("")
            assertThat(manifestContent).contains(
                    "          E: uses-sdk-library (line=10)\n" +
                            "            A: http://schemas.android.com/apk/res/android:name(0x01010003)=\"com.example.privacysandboxsdk\" (Raw: \"com.example.privacysandboxsdk\")\n" +
                            "            A: http://schemas.android.com/apk/res/android:certDigest(0x01010548)=\"$certDigest\" (Raw: \"$certDigest\")\n" +
                            "            A: http://schemas.android.com/apk/res/android:versionMajor(0x01010577)=10002\n"
            )
            ApkSubject.assertThat(it)
                    .doesNotContain(RUNTIME_ENABLED_SDK_TABLE_ASSET_FOR_COMPAT)
        }

        val sdkApks =
                GenericBuiltArtifactsLoader.loadListFromFile(privacySandboxSdkInfo.outputListingFile,
                        StdLogger(StdLogger.Level.INFO))
        Apk(File(sdkApks.single { it.applicationId.startsWith("com.example.privacysandboxsdk_") }.elements.single().outputFile)).use {
            ApkSubject.assertThat(it).exists()
            ApkSubject.assertThat(it).containsClass(SDK_IMPL_A_CLASS)
            ApkSubject.assertThat(it)
                    .doesNotContain(RUNTIME_ENABLED_SDK_TABLE_ASSET_FOR_COMPAT)
        }

        val compatSplits =
                GenericBuiltArtifactsLoader.loadFromFile(privacySandboxSdkInfo.outputListingLegacyFile,
                        StdLogger(StdLogger.Level.INFO))!!

        assertThat(compatSplits.elements).named("compat splits elements").hasSize(3)
        Apk(File(compatSplits.elements.single { it.outputFile.endsWith(
                INJECTED_PRIVACY_SANDBOX_COMPAT_SUFFIX) }.outputFile)).use {
            ApkSubject.assertThat(it).exists()
            ApkSubject.assertThat(it)
                    .contains(RUNTIME_ENABLED_SDK_TABLE_ASSET_FOR_COMPAT)
            val manifestContent = ApkSubject.getManifestContent(it.file).joinToString("\n", postfix = "\n")
            assertThat(manifestContent)
                    .doesNotContain(USES_SDK_LIBRARY_MANIFEST_ELEMENT)
        }

    }

    @Test
    fun producesApkSplitsFromSdks() {
        // For API S-, ensure that APKs are produced for each SDK that the app requires.
        val apkSelectConfig = project.file("apkSelectConfig.json")
        apkSelectConfig.writeText(
                """{"sdk_version":28,"codename":"Pie","screen_density":420,"supported_abis":["x86_64","arm64-v8a"],"supported_locales":["en"]}""")

        executor()
                .withFailOnWarning(false)
                .with(StringOption.IDE_APK_SELECT_CONFIG, apkSelectConfig.absolutePath)
                .run(":example-app:extractApksFromSdkSplitsForDebug")

        val extractedSdkApksDir =
                File(project.getSubproject(":example-app").intermediatesDir,
                        InternalArtifactType.EXTRACTED_SDK_APKS.getFolderName())
        val extractedSdkApks = extractedSdkApksDir
                .walkTopDown()
                .filter { it.isFile }
                .filter { it.extension == "apk" }
                .toList()
        assertThat(extractedSdkApks.map { it.name })
                .containsExactly(
                        "example-app-debug-injected-privacy-sandbox-compat.apk",
                        "comexampleprivacysandboxsdkb-master.apk",
                        "comexampleprivacysandboxsdk-master.apk"
                )

        Apk(extractedSdkApks.single { it.name == "comexampleprivacysandboxsdk-master.apk" }).use {
            val manifestContent = ApkSubject.getManifestContent(it.file)
            assertThat(manifestContent).containsAtLeastElementsIn(
                    listOf(
                            "N: android=http://schemas.android.com/apk/res/android (line=2)",
                            "  E: manifest (line=2)",
                            "    A: http://schemas.android.com/apk/res/android:versionCode(0x0101021b)=4",
                            "    A: http://schemas.android.com/apk/res/android:isFeatureSplit(0x0101055b)=true",
                            "    A: package=\"com.example.privacysandboxsdk.consumer\" (Raw: \"com.example.privacysandboxsdk.consumer\")",
                            "    A: split=\"comexampleprivacysandboxsdk\" (Raw: \"comexampleprivacysandboxsdk\")",
                            "      E: uses-permission (line=9)",
                            "        A: http://schemas.android.com/apk/res/android:name(0x01010003)=\"android.permission.INTERNET\" (Raw: \"android.permission.INTERNET\")",
                            "      E: application (line=11)",
                            "        A: http://schemas.android.com/apk/res/android:hasCode(0x0101000c)=false",
                            "        A: http://schemas.android.com/apk/res/android:appComponentFactory(0x0101057a)=\"androidx.core.app.CoreComponentFactory\" (Raw: \"androidx.core.app.CoreComponentFactory\")",
                            "      E: uses-sdk (line=0)",
                            "        A: http://schemas.android.com/apk/res/android:minSdkVersion(0x0101020c)=23",
                            "      E: http://schemas.android.com/apk/distribution:module (line=0)",
                            "          E: http://schemas.android.com/apk/distribution:delivery (line=0)",
                            "              E: http://schemas.android.com/apk/distribution:install-time (line=0)",
                            "                  E: http://schemas.android.com/apk/distribution:removable (line=0)",
                            "                    A: http://schemas.android.com/apk/distribution:value(0x01010024)=true",
                            "          E: http://schemas.android.com/apk/distribution:fusing (line=0)",
                            "            A: http://schemas.android.com/apk/distribution:include=true"
                    )
            )

            val entries = it.entries.map { it.toString() }
            // Not an exhaustive list of expected entries.
            assertThat(entries).containsAtLeast(
                    "/AndroidManifest.xml",
                    "/assets/asset_from_sdkImplA.txt",
                    "/assets/RuntimeEnabledSdk-com.example.privacysandboxsdk/CompatSdkConfig.xml",
                    "/META-INF/MANIFEST.MF",
                    "/META-INF/BNDLTOOL.RSA",
                    "/META-INF/BNDLTOOL.SF",
                    "/resources.arsc"
            )
        }

        Apk(extractedSdkApks.single { it.name == "example-app-debug-injected-privacy-sandbox-compat.apk" }).use {
            val manifestContent = ApkSubject.getManifestContent(it.file)
            assertThat(manifestContent).named("Manifest content of %s", it.file).containsAtLeast(
                    "N: android=http://schemas.android.com/apk/res/android (line=2)",
                    "  E: manifest (line=2)",
                    "    A: http://schemas.android.com/apk/res/android:versionCode(0x0101021b)=4",
                    "    A: http://schemas.android.com/apk/res/android:isFeatureSplit(0x0101055b)=true",
                    "    A: http://schemas.android.com/apk/res/android:compileSdkVersion(0x01010572)=34",
                    "    A: http://schemas.android.com/apk/res/android:compileSdkVersionCodename(0x01010573)=\"14\" (Raw: \"14\")",
                    "    A: package=\"com.example.privacysandboxsdk.consumer\" (Raw: \"com.example.privacysandboxsdk.consumer\")",
                    "    A: platformBuildVersionCode=34",
                    "    A: platformBuildVersionName=14",
                    "    A: split=\"exampleappdebuginjectedprivacysandboxcompat\" (Raw: \"exampleappdebuginjectedprivacysandboxcompat\")",
                    "      E: application (line=9)",
                    "        A: http://schemas.android.com/apk/res/android:hasCode(0x0101000c)=false",
            )

            val entries = it.entries.map(Path::toString)
            assertThat(entries).named("entries of %s", it.file).containsExactly(
                    "/AndroidManifest.xml",
                    "/META-INF/CERT.RSA",
                    "/META-INF/CERT.SF",
                    "/META-INF/MANIFEST.MF",
                    RUNTIME_ENABLED_SDK_TABLE_ASSET_FOR_COMPAT,
                    "/resources.arsc"
            )
        }
    }

    @Test
    fun testPublicationAndConsumptionCanBeToggledSeparately() {
        val buildFailsPublicationNotEnabled = executor()
                .with(BooleanOption.PRIVACY_SANDBOX_SDK_PLUGIN_SUPPORT, false)
                .with(BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT, false)
                .expectFailure()
                .run(":privacy-sandbox-sdk:assemble")
        buildFailsPublicationNotEnabled.stderr.use {
            val expectedContents = listOf(
                    "> Failed to apply plugin 'com.android.internal.privacy-sandbox-sdk'.",
                    "> Privacy Sandbox SDK Plugin support must be explicitly enabled."
            )
            expectedContents.forEach { line ->
                ScannerSubject.assertThat(it).contains(line)
            }
        }

        executor()
                .with(BooleanOption.PRIVACY_SANDBOX_SDK_PLUGIN_SUPPORT, true)
                .with(BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT, false)
                .run(":privacy-sandbox-sdk:assemble")
        val sdkProject = project.getSubproject(":privacy-sandbox-sdk")
        assertThat(
                sdkProject.getOutputFile("asb", "single", "privacy-sandbox-sdk.asb").exists()
        ).isTrue()

        val buildFailsConsumptionNotEnabled = executor()
                .with(BooleanOption.PRIVACY_SANDBOX_SDK_PLUGIN_SUPPORT, true)
                .with(BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT, false)
                .expectFailure()
                .run(":example-app:assemble")
        assertThat(buildFailsConsumptionNotEnabled.failureMessage).isEqualTo(
                """2 issues were found when checking AAR metadata:

  1.  Dependency :privacy-sandbox-sdk is an Android Privacy Sandbox SDK library, and needs
      Privacy Sandbox support to be enabled in projects that depend on it.

      Recommended action: Enable privacy sandbox consumption in this project by setting
          android {
              privacySandbox {
                  enable = true
              }
          }
      in this project's build.gradle

  2.  Dependency :privacy-sandbox-sdk-b is an Android Privacy Sandbox SDK library, and needs
      Privacy Sandbox support to be enabled in projects that depend on it.

      Recommended action: Enable privacy sandbox consumption in this project by setting
          android {
              privacySandbox {
                  enable = true
              }
          }
      in this project's build.gradle"""
        )
        // Other tests verify behaviour with publication and consumption enabled.
    }

    private fun declarePrivacySandboxSdkServiceOnSdkA() {
        //Add service to sdk-impl-a
        val pkg = FileUtils.join(project.getSubproject("sdk-impl-a").mainSrcDir,
                "com",
                "example",
                "sdkImplA")
        val mySdkFile = File(pkg, "MySdk.kt")
        mySdkFile.writeText(
                "package com.example.sdkImplA\n" +
                        "import androidx.privacysandbox.tools.PrivacySandboxService\n" +
                        "   @PrivacySandboxService\n" +
                        "   public interface MySdk {\n" +
                        "       suspend fun foo(bar: Int): String\n" +
                        "   }\n"
        )
    }

    companion object {

        private val certDigestPattern = Regex("([0-9A-F]{2}:){31}[0-9A-F]{2}")
        private const val SDK_IMPL_A_CLASS = "Lcom/example/sdkImplA/Example;"
        private const val USES_SDK_LIBRARY_MANIFEST_ELEMENT = "uses-sdk-library"
        private const val MY_PRIVACY_SANDBOX_SDK_MANIFEST_PACKAGE =
                "=\"com.example.privacysandboxsdk\""
        private const val INTERNET_PERMISSION =
                "A: http://schemas.android.com/apk/res/android:name(0x01010003)=\"android.permission.INTERNET\" (Raw: \"android.permission.INTERNET\")"
        private const val FOREGROUND_SERVICE = "FOREGROUND_SERVICE"
        private const val INJECTED_PRIVACY_SANDBOX_COMPAT_SUFFIX = "-injected-privacy-sandbox-compat.apk"
        private const val RUNTIME_ENABLED_SDK_TABLE_ASSET_FOR_COMPAT = "/assets/RuntimeEnabledSdkTable.xml"
    }
}
