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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.privacysandbox.privacySandboxSdkLibraryProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.privacysandbox.privacySandboxSdkProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.bundle.SdkBundleConfigProto
import com.android.bundle.SdkBundleConfigProto.SdkBundleConfig
import com.android.bundle.SdkBundleConfigProto.SdkDependencyType
import com.android.bundle.SdkMetadataOuterClass
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import com.android.tools.build.bundletool.model.SdkBundle
import com.google.common.collect.ImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipFile

/*
 Test to cover complex SDK dependencies such as a 'mediator' SDK that depends on multiple other SDKs.
 */
class PrivacySandboxMediatorSdkTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    val mavenRepo: MavenRepoGenerator = MavenRepoGenerator(
            listOf(
                    MavenRepoGenerator.Library("com.externaldep:externaljar:1",
                            "jar",
                            TestInputsGenerator.jarWithEmptyClasses(
                                    ImmutableList.of("com/externaldep/externaljar/ExternalClass")
                            )
                    ),
            )
    )

    @get:Rule
    val project: GradleTestProject = createGradleProjectBuilder {

        /*
        R-SDK = Required Privacy Sandbox Sdk
        O-SDK = Optional Privacy Sandbox Sdk
        LIB = Android Library
        APP = Android Application
        ┌───────────────────────────────────────────┐
        │               app (APP)                   │
        │                  ▲                        │
        │                  │                        │
        │     privacy-sandbox-sdk-mediator (SDK)    │
        │                  ▲                        │
        │                  │                        │
        │ privacy-sandbox-sdk-mediator-impl (LIB)   │
        │     ▲            ▲            ▲           │
        │     │            │            │           │
        │ sdk-a (R-SDK) sdk-b (O-SDK) lib (LIB)     │
        │     ▲                 ▲                   │
        │     │                 │                   │
        │ sdk-a-impl (LIB) sdk-b-impl (LIB)         │
        │                                           │
        └───────────────────────────────────────────┘
         */
        withKotlinPlugin = true
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                defaultCompileSdk()
                minSdk = 14
                namespace = "com.example.privacysandboxsdk.consumer"
                compileSdk = 34
            }
            addFile("src/main/java/com/example/privacysandboxsdk/consumer/Main.kt",
                    """
                        package com.example.privacysandboxsdk.consumer

                        class SdkAServiceImpl : com.example.sdkAImpl.SdkAService {
                            override suspend fun f1(p1: Int): Int {
                                return 0
                            }
                        }
                        class SdkBServiceImpl : com.example.sdkBImpl.SdkBService {
                            override suspend fun f1(p1: Int): Int {
                                return 0
                            }
                        }
                    """.trimIndent())
            dependencies {
                implementation(project(":privacy-sandbox-sdk-mediator"))
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
        privacySandboxSdkProject(":privacy-sandbox-sdk-mediator") {
            android {
                minSdk = 23
            }
            appendToBuildFile {
                """
                        android {
                            bundle {
                                applicationId = "com.example.privacysandboxsdk"
                                sdkProviderClassName = "androidx.privacysandbox.sdkruntime.provider.SandboxedSdkProviderAdapter"
                                compatSdkProviderClassName = "androidx.privacysandbox.sdkruntime.client.SdkSandboxManagerCompat"
                                setVersion(1, 2, 3)
                            }
                        }
                    """.trimIndent()
            }
            dependencies {
                include(project(":privacy-sandbox-sdk-mediator-impl"))
                requiredSdk(project(":sdk-a"))
                optionalSdk(project(":sdk-b"))
            }
        }
        privacySandboxSdkLibraryProject(":privacy-sandbox-sdk-mediator-impl") {
            useNewPluginsDsl = true
            android {
                defaultCompileSdk()
                namespace = "com.example.sdkmediator"
                minSdk = 23
            }
            addFile("src/main/java/com/example/sdkmediator/Mediation.kt",
                    """
                        package com.example.sdkmediator

                        class SdkAServiceImpl : com.example.sdkAImpl.SdkAService {
                            override suspend fun f1(p1: Int): Int {
                                return 0
                            }
                        }
                        class SdkBServiceImpl : com.example.sdkBImpl.SdkBService {
                            override suspend fun f1(p1: Int): Int {
                                return 0
                            }
                        }
                    """.trimIndent())

            dependencies {
                implementation(project(":sdk-a"))
                implementation(project(":sdk-b"))
                implementation("com.externaldep:externaljar:1")
            }
        }
        privacySandboxSdkProject(":sdk-a") {
            android {
                minSdk = 23
            }

            appendToBuildFile {
                """
                        android {
                            bundle {
                                applicationId = "com.example.sdka"
                                sdkProviderClassName = "Test"
                                compatSdkProviderClassName = "Test"
                                setVersion(1, 2, 3)
                            }
                        }
                    """.trimIndent()
            }
            dependencies {
                include(project(":sdk-a-impl"))
            }
        }
        privacySandboxSdkLibraryProject(":sdk-a-impl") {
            android {
                namespace = "com.example.sdkAImpl"
                minSdk = 23
            }
            addFile("src/main/kotlin/com/example/sdkAImpl/SdkAService.kt",
                    """
                    package com.example.sdkAImpl

                    import androidx.privacysandbox.tools.PrivacySandboxService

                    @PrivacySandboxService
                    interface SdkAService {
                        suspend fun f1(p1: Int): Int
                    }
                    """.trimIndent())
        }
        privacySandboxSdkProject(":sdk-b") {
            plugins.add(PluginType.MAVEN_PUBLISH)
            android {
                minSdk = 23
            }

            appendToBuildFile {
                """
                        android {
                            bundle {
                                applicationId = "com.example.sdkb"
                                sdkProviderClassName = "Test"
                                compatSdkProviderClassName = "Test"
                                setVersion(1, 2, 3)
                            }
                        }
                        publishing {
                            publications {
                                debug(MavenPublication) {
                                    groupId = 'com.example'
                                    artifactId = 'sdkb'
                                    version = '1.0.0'
                                }
                            }
                              repositories {
                                maven {
                                  url = ".../additional_maven_repo"
                                }
                              }
                        }
                    """.trimIndent()
            }
            dependencies {
                include(project(":sdk-b-impl"))
            }
        }
        privacySandboxSdkLibraryProject(":sdk-b-impl") {
            android {
                namespace = "com.example.sdkBImpl"
                minSdk = 23
            }
            addFile("src/main/kotlin/com/example/sdkBImpl/SdkBService.kt",
                    """
                    package com.example.sdkBImpl

                    import androidx.privacysandbox.tools.PrivacySandboxService

                    @PrivacySandboxService
                    interface SdkBService{
                        suspend fun f1(p1: Int): Int
                    }
                    """.trimIndent())
        }
        subProject(":android-lib") {
            plugins.add(PluginType.ANDROID_LIB)
            plugins.add(PluginType.KOTLIN_ANDROID)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidlib"
                minSdk = 23
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
            .with(BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT, true)
            .withFailOnWarning(false) // kgp uses deprecated api WrapUtil
            .withPerTestPrefsRoot(true)
            .with(BooleanOption.ENABLE_PROFILE_JSON, true) // Regression test for b/237278679

    @Test
    fun missingRequiredDependency() {
        val sdkProject = project.getSubproject(":privacy-sandbox-sdk-mediator")
        // :sdk-a is an SDK dependency of :privacy-sandbox-sdk-mediator-impl
        TestFileUtils.searchAndReplace(sdkProject.buildFile.toPath(),
                "requiredSdk project(':sdk-a')",
                "")

        // Building the SDK should fail since configurations are invalid.
        val execution =
                executor().expectFailure()
                        .run(":privacy-sandbox-sdk-mediator:validatePrivacySandboxSdkConfiguration")
        execution.assertErrorContains("project :sdk-a must also be defined in 'optionalSdk' or 'requiredSdk' configurations.")
    }

    @Test
    fun failWhenAddingDirectSdkDependencyInIncludes() {
        val sdkProject = project.getSubproject(":privacy-sandbox-sdk-mediator")
        TestFileUtils.searchAndReplace(sdkProject.buildFile.toPath(),
                "include project(':privacy-sandbox-sdk-mediator-impl')",
                "include project(':sdk-a')")
        TestFileUtils.searchAndReplace(sdkProject.buildFile.toPath(),
                "requiredSdk project(':sdk-a')",
                "")
        TestFileUtils.searchAndReplace(sdkProject.buildFile.toPath(),
                "optionalSdk project(':sdk-b')",
                "")
        // Building the SDK should fail since configurations are invalid.
        val execution =
                executor().expectFailure()
                        .run(":privacy-sandbox-sdk-mediator:validatePrivacySandboxSdkConfiguration")
        execution.assertErrorContains("project :sdk-a must be defined in 'optionalSdk' or 'requiredSdk' configurations only.")
    }

    @Test
    fun failWhenAddingAnSdkDependencyInIncludeAndRequired() {
        val sdkProject = project.getSubproject(":privacy-sandbox-sdk-mediator")
        TestFileUtils.searchAndReplace(sdkProject.buildFile.toPath(),
                "include project(':privacy-sandbox-sdk-mediator-impl')",
                "include project(':privacy-sandbox-sdk-mediator-impl')\n" +
                        "include project(':sdk-a')")
        val execution =
                executor().expectFailure()
                        .withArgument("--dry-run") // Skip execution.
                        .run(":privacy-sandbox-sdk-mediator:validatePrivacySandboxSdkConfiguration")
        execution.assertErrorContains("'include' configuration can not contains dependencies found in 'requiredSdk' or 'optionalSdk'. " +
                "Recommended Action: Remove the following dependency from the 'include' configuration: sdk-a")
    }

    @Test
    fun failWhenAddingANonSdkDependencyToRequiredSdk() {
        val sdkProject = project.getSubproject(":privacy-sandbox-sdk-mediator")
        // Add a new requiredSdk dependency, that is not an SDK.
        TestFileUtils.searchAndReplace(
            sdkProject.buildFile.toPath(),
            "include project(':privacy-sandbox-sdk-mediator-impl')",
            """include project(':privacy-sandbox-sdk-mediator-impl')
                requiredSdk('com.externaldep:externaljar:1')""".trimIndent()
        )
        var execution =
            executor().expectFailure()
                .run(":privacy-sandbox-sdk-mediator:validatePrivacySandboxSdkConfiguration")
        execution.assertErrorContains(
            "com.externaldep:externaljar:1 is a not a privacy sandbox sdk."
        )

        TestFileUtils.searchAndReplace(
            sdkProject.buildFile.toPath(),
            "requiredSdk('com.externaldep:externaljar:1')",
            "requiredSdk(project(':android-lib'))"
        )
        execution =
            executor().expectFailure()
                .run(":privacy-sandbox-sdk-mediator:validatePrivacySandboxSdkConfiguration")
        execution.assertErrorContains(
            "project :android-lib is a not a privacy sandbox sdk"
        )
    }



    @Test
    fun asbSdkBundleConfigProtoContainsCorrectSdkDependencyInfo() {
        val sdkMediatorProject = project.getSubproject(":privacy-sandbox-sdk-mediator")
        executor().run(":privacy-sandbox-sdk-mediator:packagePrivacySandboxSdkBundle")

        val asb =
                sdkMediatorProject.getIntermediateFile(
                        "asb",
                        "single",
                        "packagePrivacySandboxSdkBundle",
                        "privacy-sandbox-sdk-mediator.asb")
        ZipFile(asb).use { openAsar ->
            val sdkBundleConfig =
                    openAsar.getEntry("SdkBundleConfig.pb")
            val sdkMetadataBytes = openAsar.getInputStream(sdkBundleConfig).readBytes()
            val proto: SdkBundleConfigProto.SdkBundleConfig = sdkMetadataBytes.inputStream()
                    .buffered()
                    .use { input -> SdkBundleConfig.parseFrom(input) }
            assertThat(proto.sdkDependenciesCount).isEqualTo(2)
            val sdkBundlePackageNameMap =
                    proto.sdkDependenciesList.toList().associateBy { it.packageName }
            val sdkA = sdkBundlePackageNameMap.get("com.example.sdka")
            val sdkB = sdkBundlePackageNameMap.get("com.example.sdkb")
            val expectedSdkA = SdkBundleConfigProto.SdkBundle.newBuilder()
                    .setPackageName("com.example.sdka")
                    .setDependencyType(SdkDependencyType.SDK_DEPENDENCY_TYPE_REQUIRED)
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setBuildTimeVersionPatch(3)
                    .setCertificateDigest(sdkA?.certificateDigest)
                    .build()
            val expectedSdkB = SdkBundleConfigProto.SdkBundle.newBuilder()
                    .setPackageName("com.example.sdkb")
                    .setDependencyType(SdkDependencyType.SDK_DEPENDENCY_TYPE_OPTIONAL)
                    .setVersionMajor(1)
                    .setVersionMinor(2)
                    .setBuildTimeVersionPatch(3)
                    .setCertificateDigest(sdkB?.certificateDigest)
                    .build()
            assertThat(sdkA).isEqualTo(expectedSdkA)
            assertThat(sdkB).isEqualTo(expectedSdkB)
        }
    }
}
