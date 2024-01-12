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
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.ProfileCapturer
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.privacysandbox.privacySandboxSampleProject
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.builder.model.v2.ide.SyncIssue
import com.android.ide.common.build.GenericBuiltArtifactsLoader
import com.android.ide.common.signing.KeystoreHelper
import com.android.testutils.apk.Apk
import com.android.testutils.apk.Dex
import com.android.testutils.apk.Zip
import com.android.testutils.truth.PathSubject.assertThat
import com.android.testutils.truth.ZipFileSubject
import com.android.utils.FileUtils
import com.android.utils.StdLogger
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.TextFormat
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Path
import java.util.Objects
import kotlin.io.path.readText

/** Smoke integration tests for the privacy sandbox SDK production and consumption */
class PrivacySandboxSdkTest {

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
                "Lcom/example/androidlib1/Example;",
                "Lcom/example/androidlib2/Example;",
                "Lcom/externaldep/externaljar/ExternalClass;"
            )
            assertThat(dex.classes["Lcom/example/androidlib1/Example;"]!!.methods.map { it.name }).contains(
                "f1")
            assertThat(dex.classes["Lcom/example/androidlib2/Example;"]!!.methods.map { it.name }).contains(
                "f2")
            assertThat(dex.classes["Lcom/example/androidlib1/R\$string;"]!!.fields.map { it.name }).containsExactly(
                "string_from_android_lib_1")
        }

        // Check incremental changes are handled
        TestFileUtils.searchAndReplace(
            project.getSubproject("android-lib1")
                .file("src/main/java/com/example/androidlib1/Example.java"),
            "public void f1() {}",
            "public void g() {}"
        )

        executor().run(":privacy-sandbox-sdk:minifyBundleWithR8")

        Dex(dexLocation).also { dex ->
            assertThat(dex.classes["Lcom/example/androidlib1/Example;"]!!.methods.map { it.name }).contains(
                "g")
            assertThat(dex.classes["Lcom/example/androidlib2/Example;"]!!.methods.map { it.name }).contains(
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
                "Lcom/example/androidlib1/Example;"
            )
            assertThat(dex.classes.keys).contains(
                "Lcom/example/androidlib2/Example;",
            )
            assertThat(dex.classes["Lcom/example/androidlib2/Example;"]!!.methods.map { it.name }).contains(
                "f2")
            // none of the resources should be removed
            assertThat(dex.classes["Lcom/example/androidlib1/R\$string;"]!!.fields.map { it.name }).containsExactly(
                "string_from_android_lib_1")
        }

        // Check incremental changes are handled
        TestFileUtils.searchAndReplace(
            project.getSubproject("android-lib2")
                .file("src/main/java/com/example/androidlib2/Example.java"),
            "public void f2() {}",
            "public void g() {}"
        )

        executor().run(":privacy-sandbox-sdk:minifyBundleWithR8")

        Dex(dexLocation).also { dex ->
            assertThat(dex.classes["Lcom/example/androidlib2/Example;"]!!.methods.map { it.name }).contains(
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
                package="com.example.privacysandboxsdk" >

                <uses-sdk
                    android:minSdkVersion="23"
                    android:targetSdkVersion="34" />

                <uses-permission android:name="android.permission.INTERNET" />

                <application android:appComponentFactory="androidx.core.app.CoreComponentFactory" />

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
                modules.contains("base/assets/asset_from_androidlib1.txt")
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
    fun testConsumptionViaBundle() {
        // TODO(b/235469089) expand this to verify installation also

        //Add service to android-lib1
        val pkg = FileUtils.join(project.getSubproject("android-lib1").mainSrcDir,
                "com",
                "example",
                "androidlib1")
        val mySdkFile = File(pkg, "MySdk.kt")
        mySdkFile.writeText(
                "package com.example.androidlib1\n" +
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
            assertThat(it).containsClass(ANDROID_LIB1_CLASS)
            assertThat(it).containsClass("Lcom/example/androidlib2/Example;")
            assertThat(it).containsClass("Lcom/externaldep/externaljar/ExternalClass;")
            assertThat(it).containsClass("Lcom/example/androidlib1/R\$string;")
            assertThat(it).containsClass("Lcom/example/androidlib1/R;")
            assertThat(it).containsClass("Lcom/example/androidlib2/R;")
            assertThat(it).containsClass("Lcom/example/privacysandboxsdk/RPackage;")
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
            assertThat(it).doesNotExist()
        }
        // Expect the first assignment of certDigest to be the same for all modules.
        val certDigest: String
        Apk(baseMaster3Apk).use {
            assertThat(it).exists()
            assertThat(it).containsClass("Lcom/example/privacysandboxsdk/consumer/R;")
            assertThat(it).doesNotContainClass(ANDROID_LIB1_CLASS)
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
            assertThat(manifestContentStr).doesNotContain(INTERNET_PERMISSION)
            assertThat(manifestContentStr).doesNotContain(FOREGROUND_SERVICE)

        }

        // Check building the bundle to deploy to a non-privacy sandbox device:
        apkSelectConfig.writeText(
                """{"sdk_version":32,"codename":"Tiramisu","screen_density":420,"supported_abis":["x86_64","arm64-v8a"],"supported_locales":["en"]}""")

        executor()
                .with(StringOption.IDE_APK_SELECT_CONFIG, apkSelectConfig.absolutePath)
                .run(":example-app:extractApksFromBundleForDebug")

        Apk(baseMaster2Apk).use {
            assertThat(it).exists()
            assertThat(it).containsClass("Lcom/example/privacysandboxsdk/consumer/R;")
            assertThat(it).hasClass("Lcom/example/privacysandboxsdk/RPackage;")
                    .that()
                    .hasExactFields(mutableSetOf("packageId"))
            val rPackageClass = it.getClass("Lcom/example/privacysandboxsdk/RPackage;")
            assertThat(rPackageClass.fields.single().initialValue?.toString()).isEqualTo("0x7e000000")
            assertThat(it).doesNotContainClass(ANDROID_LIB1_CLASS)
            val manifestContent = ApkSubject.getManifestContent(it.file).joinToString("\n")
            assertThat(manifestContent).doesNotContain(USES_SDK_LIBRARY_MANIFEST_ELEMENT)
            assertThat(manifestContent).doesNotContain(MY_PRIVACY_SANDBOX_SDK_MANIFEST_PACKAGE)
        }
        Apk(baseMaster3Apk).use {
            assertThat(it).doesNotExist()
        }
    }

    @Test
    fun testConsumptionViaApk() {
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
            """.trimIndent())

        Apk(project.getSubproject(":example-app")
                .getApk(GradleTestProject.ApkType.DEBUG).file).use {
            assertThat(it).exists()
            val manifestContent = ApkSubject.getManifestContent(it.file).joinToString("\n")
            // This asset must only be packaged in non-sandbox capable devices, otherwise it may
            // cause runtime exceptions on supported privacy sandbox platforms.
            assertThat(it.entries.map { it.toString() })
                    .doesNotContain(RUNTIME_ENABLED_SDK_TABLE_ASSET_FOR_COMPAT)

            assertThat(manifestContent).contains(INTERNET_PERMISSION)
            assertThat(manifestContent).doesNotContain(FOREGROUND_SERVICE)
            assertThat(manifestContent).doesNotContain(USES_SDK_LIBRARY_MANIFEST_ELEMENT)
        }
        val usesSdkLibrarySplitPath =
                GenericBuiltArtifactsLoader.loadListFromFile(privacySandboxSdkInfo.additionalApkSplitFile,
                        LoggerWrapper.getLogger(PrivacySandboxSdkTest::class.java))
                        .elementAt(0).elements.first().outputFile
        Apk(File(usesSdkLibrarySplitPath)).use {
            assertThat(it).exists()
            val manifestContent = ApkSubject.getManifestContent(it.file).joinToString("\n", postfix = "\n")
            val certDigest = certDigestPattern.find(manifestContent)?.value ?: error("")
            assertThat(manifestContent).contains(
                    "          E: uses-sdk-library (line=10)\n" +
                    "            A: http://schemas.android.com/apk/res/android:name(0x01010003)=\"com.example.privacysandboxsdk\" (Raw: \"com.example.privacysandboxsdk\")\n" +
                    "            A: http://schemas.android.com/apk/res/android:certDigest(0x01010548)=\"$certDigest\" (Raw: \"$certDigest\")\n" +
                    "            A: http://schemas.android.com/apk/res/android:versionMajor(0x01010577)=10002\n"
            )
            assertThat(it).doesNotContain(RUNTIME_ENABLED_SDK_TABLE_ASSET_FOR_COMPAT)
        }

        val sdkApks =
                GenericBuiltArtifactsLoader.loadListFromFile(privacySandboxSdkInfo.outputListingFile,
                        StdLogger(StdLogger.Level.INFO))
        Apk(File(sdkApks.single().elements.single().outputFile)).use {
            assertThat(it).exists()
            assertThat(it).containsClass(ANDROID_LIB1_CLASS)
            assertThat(it).doesNotContain(RUNTIME_ENABLED_SDK_TABLE_ASSET_FOR_COMPAT)
        }

        val compatSplits =
                GenericBuiltArtifactsLoader.loadFromFile(privacySandboxSdkInfo.outputListingLegacyFile,
                        StdLogger(StdLogger.Level.INFO))!!

        assertThat(compatSplits.elements).named("compat splits elements").hasSize(2)
        Apk(File(compatSplits.elements.single { it.outputFile.endsWith(
                INJECTED_PRIVACY_SANDBOX_COMPAT_SUFFIX) }.outputFile)).use {
            assertThat(it).exists()
            assertThat(it).contains(RUNTIME_ENABLED_SDK_TABLE_ASSET_FOR_COMPAT)
            val manifestContent = ApkSubject.getManifestContent(it.file).joinToString("\n", postfix = "\n")
            assertThat(manifestContent).doesNotContain(USES_SDK_LIBRARY_MANIFEST_ELEMENT)
        }

    }

    @Test
    fun checkKsp() {
        val androidLib1 = project.getSubproject("android-lib1")
        val pkg =
                FileUtils.join(androidLib1.mainSrcDir, "com", "example", "androidlib1")
        val mySdkFile = File(pkg, "MySdk.kt")

        // Invalid usage of @PrivacySandboxSdk as interface contains two methods with the same name.
        mySdkFile.writeText(
                "package com.example.androidlib1\n" +
                        "import androidx.privacysandbox.tools.PrivacySandboxService\n" +
                        "   @PrivacySandboxService\n" +
                        "   public interface MySdk {\n" +
                        "       suspend fun doStuff(x: Int, y: Int): String\n" +
                        "       suspend fun doStuff(x: Int, y: Int)\n" +
                        "   }\n"
        )

        executor().expectFailure().run("android-lib1:build")

        mySdkFile.writeText(
                "package com.example.androidlib1\n" +
                        "import androidx.privacysandbox.tools.PrivacySandboxService\n" +
                        "   @PrivacySandboxService\n" +
                        "   public interface MySdk {\n" +
                        "       suspend fun doStuff(x: Int, y: Int): String\n" +
                        "   }\n"
        )
        executor().run("android-lib1:build")

        val kspDir = FileUtils.join(androidLib1.generatedDir, "ksp")
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
                    "/assets/asset_from_androidlib1.txt",
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
                """An issue was found when checking AAR metadata:

  1.  Dependency :privacy-sandbox-sdk is an Android Privacy Sandbox SDK library, and needs
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

    companion object {

        private val certDigestPattern = Regex("([0-9A-F]{2}:){31}[0-9A-F]{2}")
        private const val ANDROID_LIB1_CLASS = "Lcom/example/androidlib1/Example;"
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
