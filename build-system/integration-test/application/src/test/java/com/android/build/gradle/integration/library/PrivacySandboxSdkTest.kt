/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.build.gradle.integration.common.utils.SdkHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.ide.common.signing.KeystoreHelper
import com.android.sdklib.BuildToolInfo
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.apk.Apk
import com.android.testutils.apk.Dex
import com.android.testutils.apk.Zip
import com.android.testutils.truth.PathSubject.assertThat
import com.android.testutils.truth.ZipFileSubject
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Objects
import kotlin.io.path.readText

/** Smoke integration tests for the privacy sandbox SDK production and consumption */
class PrivacySandboxSdkTest {

    private val mavenRepo = MavenRepoGenerator(
            listOf(
                    MavenRepoGenerator.Library("com.externaldep:externaljar:1",
                            "jar",
                            TestInputsGenerator.jarWithEmptyClasses(
                                    ImmutableList.of("com/externaldep/externaljar/ExternalClass")
                            )),
            )
    )

    @get:Rule
    val project = createGradleProjectBuilder {
        privacySandboxSdkProject(":privacy-sandbox-sdk") {
                android {
                    minSdk = 14
                }
                appendToBuildFile {
                    """
                        android {
                            bundle {
                                applicationId = "com.example.privacysandboxsdk"
                                sdkProviderClassName = "Test"
                                compatSdkProviderClassName = "Test"
                                setVersion(1, 2, 3)
                            }
                        }
                    """.trimIndent()
                }
                dependencies {
                    include(project(":android-lib1"))
                    include(project(":android-lib2"))
                    include("com.externaldep:externaljar:1")
                }
        }
        privacySandboxSdkLibraryProject(":android-lib2") {
            android {
                namespace = "com.example.androidlib2"
                minSdk = 14
            }
            addFile(
                    "src/main/java/com/example/androidlib2/Example.java",
                    // language=java
                    """
                package com.example.androidlib2;

                class Example {

                    public Example() {}

                    public void f2() {}
                }
            """.trimIndent()
            )
            // Have an empty manifest as a regression test of b/237279793
            addFile("src/main/AndroidManifest.xml", """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                </manifest>
                """.trimIndent()
            )
        }

        privacySandboxSdkLibraryProject(":android-lib1") {
            android {
                namespace = "com.example.androidlib1"
                minSdk = 14
            }
            dependencies {
                implementation("androidx.privacysandbox.tools:tools-apipackager:$androidxPrivacySandboxSdkToolsVersion")
            }
            addFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                <string name="string_from_android_lib_1">androidLib</string>
              </resources>"""
            )
            addFile(
                    "src/main/java/com/example/androidlib1/Example.java",
                    // language=java
                    """
                package com.example.androidlib1;

                class Example {

                    public Example() {}
                    public void f1() {}
                    public void f2() {
                    }
                }
            """.trimIndent()
            )
            addFile("src/main/resources/my_java_resource.txt", "some java resource")
            addFile("src/main/assets/asset_from_androidlib1.txt", "some asset")
        }
        subProject(":example-app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                defaultCompileSdk()
                minSdk = 14
                namespace = "com.example.privacysandboxsdk.consumer"
                compileSdkPreview = "TiramisuPrivacySandbox"
            }
            dependencies {
                implementation(project(":privacy-sandbox-sdk"))
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
        rootProject {
            useNewPluginsDsl = true
            plugins.add(PluginType.KSP)
        }
    }
            .withAdditionalMavenRepo(mavenRepo)
            .addGradleProperties("${BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT.propertyName}=true")
            .addGradleProperties("${BooleanOption.USE_ANDROID_X.propertyName}=true")
            .create()

    private fun executor() = project.executor()
            .withPerTestPrefsRoot(true)
            .with(BooleanOption.ENABLE_PROFILE_JSON, true) // Regression test for b/237278679

    @Test
    fun testDexing() {
        val dexLocation = project.getSubproject(":privacy-sandbox-sdk")
                .getIntermediateFile("dex", "single", "classes.dex")

        executor().run(":privacy-sandbox-sdk:mergeDex")

        Dex(dexLocation).also { dex ->
            assertThat(dex.classes.keys).containsAtLeast(
                "Lcom/example/androidlib1/Example;",
                "Lcom/example/androidlib2/Example;",
                    "Lcom/externaldep/externaljar/ExternalClass;"
            )
            assertThat(dex.classes["Lcom/example/androidlib1/Example;"]!!.methods.map { it.name }).contains("f1")
            assertThat(dex.classes["Lcom/example/androidlib2/Example;"]!!.methods.map { it.name }).contains("f2")
            assertThat(dex.classes["Lcom/example/androidlib1/R\$string;"]!!.fields.map { it.name }).containsExactly("string_from_android_lib_1")
        }

        // Check incremental changes are handled
        TestFileUtils.searchAndReplace(
                project.getSubproject("android-lib1")
                        .file("src/main/java/com/example/androidlib1/Example.java"),
                "public void f1() {}",
                "public void g() {}"
        )

        executor().run(":privacy-sandbox-sdk:mergeDex")

        Dex(dexLocation).also { dex ->
            assertThat(dex.classes["Lcom/example/androidlib1/Example;"]!!.methods.map { it.name }).contains("g")
            assertThat(dex.classes["Lcom/example/androidlib2/Example;"]!!.methods.map { it.name }).contains("f2")
        }

    }

    @Test
    fun testAsb() {
        executor().run(":privacy-sandbox-sdk:assemble")
        val asbFile =
            project.getSubproject(":privacy-sandbox-sdk")
                    .getOutputFile("asb", "single", "privacy-sandbox-sdk.asb")
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
    fun testConsumption() {
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
                .getIntermediateFile("extracted_apks_from_privacy_sandbox_sdks", "debug", "privacy-sandbox-sdk", "standalone.apk")

        Apk(privacySandboxSdkApk).use {
            assertThat(it).containsClass(ANDROID_LIB1_CLASS)
            assertThat(it).containsClass("Lcom/example/androidlib2/Example;")
            assertThat(it).containsClass("Lcom/externaldep/externaljar/ExternalClass;")
            val rPackageDex = it.secondaryDexFiles.last()
            assertThat(rPackageDex.classes.keys).containsExactly("Lcom/example/privacysandboxsdk/RPackage;")
        }

        // Check building the bundle to deploy to TiramisuPrivacySandbox
        val apkSelectConfig = project.file("apkSelectConfig.json")
        apkSelectConfig.writeText(
                """{"sdk_version":32,"codename":"TiramisuPrivacySandbox","screen_density":420,"supported_abis":["x86_64","arm64-v8a"],"supported_locales":["en"]}""")

        executor()
                .with(StringOption.IDE_APK_SELECT_CONFIG, apkSelectConfig.absolutePath)
                .run(":example-app:extractApksFromBundleForDebug")

        val extractedApks = project.getSubproject(":example-app")
                .getIntermediateFile("extracted_apks", "debug")
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
                            "      E: application (line=14)",
                            "          E: uses-sdk-library (line=0)",
                            "            A: http://schemas.android.com/apk/res/android:name(0x01010003)=\"com.example.privacysandboxsdk\" (Raw: \"com.example.privacysandboxsdk\")",
                            "            A: http://schemas.android.com/apk/res/android:certDigest(0x01010548)=\"$certDigest\" (Raw: \"$certDigest\")",
                            "            A: http://schemas.android.com/apk/res/android:versionMajor(0x01010577)=\"10002\" (Raw: \"10002\")"
                    )
            )
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

        executor()
            .with(StringOption.IDE_APK_SELECT_CONFIG, apkSelectConfig.absolutePath)
            .run(":example-app:assembleDebug")
        Apk(project.getSubproject(":example-app").getApk(GradleTestProject.ApkType.DEBUG).file).use {
            assertThat(it).exists()
            val manifestContent = ApkSubject.getManifestContent(it.file)
            assertThat(manifestContent).containsAtLeastElementsIn(
                listOf(
                "      E: application (line=14)",
                "          E: uses-sdk-library (line=18)",
                "            A: http://schemas.android.com/apk/res/android:name(0x01010003)=\"com.example.privacysandboxsdk\" (Raw: \"com.example.privacysandboxsdk\")",
                "            A: http://schemas.android.com/apk/res/android:certDigest(0x01010548)=\"$certDigest\" (Raw: \"$certDigest\")",
                "            A: http://schemas.android.com/apk/res/android:versionMajor(0x01010577)=10002"
                )
            )
        }
    }

    @Test
    fun checkKsp() {
        val executor = project.executor()
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

        executor.expectFailure().run("android-lib1:build")

        mySdkFile.writeText(
                "package com.example.androidlib1\n" +
                        "import androidx.privacysandbox.tools.PrivacySandboxService\n" +
                        "   @PrivacySandboxService\n" +
                        "   public interface MySdk {\n" +
                        "       suspend fun doStuff(x: Int, y: Int): String\n" +
                        "   }\n"
        )
        project.execute("android-lib1:build")

        val kspDir = FileUtils.join(androidLib1.generatedDir,"ksp")
        assertThat(kspDir.exists()).isTrue()
    }

    @Test
    fun testNoServiceDefinedInModuleUsedBySdk() {
        project.executor()
                .with(BooleanOption.PRIVACY_SANDBOX_SDK_REQUIRE_SERVICES, true)
                .expectFailure()
                .run(":example-app:assembleDebug")
                .also {
                    assertThat(it.failureMessage).contains(
                            "Unable to proceed generating shim with no provided sdk descriptor entries in:")
                }

        project.executor()
                .with(BooleanOption.PRIVACY_SANDBOX_SDK_REQUIRE_SERVICES, false)
                .run(":example-app:assembleDebug")
    }

    companion object {
        private val certDigestPattern = Regex("([0-9A-F]{2}:){31}[0-9A-F]{2}")
        private const val ANDROID_LIB1_CLASS = "Lcom/example/androidlib1/Example;"
        private const val USES_SDK_LIBRARY_MANIFEST_ELEMENT = "uses-sdk-library"
        private const val MY_PRIVACY_SANDBOX_SDK_MANIFEST_PACKAGE = "=\"com.example.privacysandboxsdk\""
    }
}

