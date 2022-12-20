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

package com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.privacysandbox

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.TestProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.generateAarWithContent
import com.google.common.collect.ImmutableList

fun createGradleProjectWithPrivacySandboxLibrary(action: TestProjectBuilder.() -> Unit) =
        createGradleProjectBuilder {
    subProject(":privacy-sandbox-sdk") {
        plugins.add(PluginType.PRIVACY_SANDBOX_SDK)
        android {
            defaultCompileSdk()
            namespace = "com.example.privacysandboxsdk"
            minSdk = 33
        }
        dependencies {
            include(project(":privacy-sandbox-sdk-impl"))
        }
        appendToBuildFile {
            """
                android {
                    bundle {
                        applicationId = "com.example.privacysandboxsdk"
                        sdkProviderClassName = "Test"
                        setVersion(1, 2, 3)
                    }
                }
            """.trimIndent()
        }
    }
    subProject(":privacy-sandbox-sdk-impl") {
        plugins.add(PluginType.ANDROID_LIB)
        android {
            defaultCompileSdk()
            namespace = "com.example.privacysandboxsdk"
            minSdk = 33
        }
    }
    action(this)
}.addGradleProperties("${BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT.propertyName}=true")
    .create()

private val mavenRepo = MavenRepoGenerator(
        listOf(
                MavenRepoGenerator.Library("com.externaldep:externaljar:1",
                        "jar",
                        TestInputsGenerator.jarWithEmptyClasses(
                                ImmutableList.of("com/externaldep/externaljar/ExternalClass")
                        )),
                MavenRepoGenerator.Library("com.externaldep:externalaar:1",
                        "aar",
                        generateAarWithContent("com.externaldep.externalaar",
                                // language=xml
                                manifest = """
                                         <manifest package="com.externaldex.externalaar" xmlns:android="http://schemas.android.com/apk/res/android">
                                             <uses-sdk android:targetSdkVersion="34" android:minSdkVersion="21" />
                                             <!-- Permission that needs to be removed before ASB packaging -->
                                             <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
                                         </manifest>
                                    """.trimIndent()
                        ))
        )
)

fun privacySandboxSampleProject(): GradleTestProject {
    return createGradleProjectBuilder {
        /*
        example-app
             ▲
             │
        privacy-sandbox-sdk  ◄──────
             ▲                     │
             │                     │
        sdk-impl-a            android-lib
             ▲
             │
        privacy-sandbox-sdk-b
             ▲
             │
        sdk-impl-b
         */
        // An SDK module used by the :example-app application.
        privacySandboxSdkProject(":privacy-sandbox-sdk") {
            android {
                minSdk = 23
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
                include(project(":sdk-impl-a"))
                include(project(":android-lib"))
                include("com.externaldep:externaljar:1")
                include("com.externaldep:externalaar:1")
            }
            addFile(
                "proguard-rules.pro",
            """-keep class com.example.androidlib.Example { *; }
                """.trimMargin())
        }
        // A library module included in the :privacy-sandbox-sdk SDK module.
        privacySandboxSdkLibraryProject(":android-lib") {
            android {
                namespace = "com.example.androidlib"
                minSdk = 23
            }
            addFile(
                    "src/main/java/com/example/androidlib/Example.java",
                    // language=java
                    """
                package com.example.androidlib;

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
        // An SDK used by the :sdk-impl-a library module.
        privacySandboxSdkProject(":privacy-sandbox-sdk-b") {
            plugins.add(PluginType.PRIVACY_SANDBOX_SDK)
            android {
                defaultCompileSdk()
                namespace = "com.example.privacysandboxsdkb"
                minSdk = 33
            }
            dependencies {
                include(project(":sdk-impl-b"))
            }
            appendToBuildFile {
                """
                android {
                    bundle {
                        applicationId = "com.example.privacysandboxsdkb"
                        sdkProviderClassName = "Test"
                        compatSdkProviderClassName = "Test"
                        setVersion(1, 2, 3)
                    }
                }
            """.trimIndent()
            }
        }
        // A library included in :privacy-sandbox-sdk-b (SDK module).
        privacySandboxSdkLibraryProject(":sdk-impl-b") {
            useNewPluginsDsl = true
            android {
                defaultCompileSdk()
                namespace = "com.example.sdkImplB"
                minSdk = 14
            }
            dependencies {
            }
            addFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                <string name="string_from_sdk_impl_b">androidLib</string>
              </resources>"""
            )
            addFile(
                    "src/main/java/com/example/sdkImplB/Example.java",
                    // language=java
                    """
                package com.example.sdkImplB;

                class Example {

                    public Example() {}

                    public void f1() {}
                }
            """.trimIndent()
            )
            addFile(
                    "src/main/java/com/example/sdkImplB/MySdkB.kt",
                    // language=kotlin
                    """
                package com.example.sdkImplB

                import androidx.privacysandbox.tools.PrivacySandboxService

                @PrivacySandboxService
                interface MySdkB {
                    suspend fun f1(p1: Int): Int
                }
            """.trimIndent()
            )
            addFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                <string name="string_from_sdk_impl_b">fromSdkImplB</string>
              </resources>"""
            )
            addFile("src/main/resources/my_java_resource.txt", "some java resource")
            addFile("src/main/assets/asset_from_sdkImplB.txt", "some asset")
        }
        // A library module included in the :privacy-sandbox-sdk SDK.
        privacySandboxSdkLibraryProject(":sdk-impl-a") {
            android {
                defaultCompileSdk()
                namespace = "com.example.sdkImplA"
                minSdk = 23
            }
            dependencies {
                implementation(project(":privacy-sandbox-sdk-b"))
            }
            addFile("src/main/AndroidManifest.xml", """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"  xmlns:tools="http://schemas.android.com/tools">
                    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" tools:node="remove" />
                    <uses-permission android:name="android.permission.INTERNET" />
                </manifest>
                """.trimIndent()
            )
            addFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                <string name="string_from_sdk_impl_a">fromSdkImplA</string>
              </resources>"""
            )
            addFile(
                    "src/main/java/com/example/sdkImplA/Example.kt",
                    // language=kotlin
                    """
                package com.example.sdkImplA

                class Example {

                    fun f1() {}
                    // The line below should compile if classes from another SDK are in the
                    // same compile classpath.
                    fun f2() {}
                    companion object {}
                }
            """.trimIndent()
            )
            addFile("src/main/resources/my_java_resource.txt", "some java resource")
            addFile("src/main/assets/asset_from_sdkImplA.txt", "some asset")
        }
        subProject(":example-app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                defaultCompileSdk()
                minSdk = 23
                namespace = "com.example.privacysandboxsdk.consumer"
            }
            dependencies {
                implementation(project(":privacy-sandbox-sdk"))
            }
            appendToBuildFile { //language=groovy
                """
                    android {
                        defaultConfig {
                            versionCode = 4
                        }
                    }
                """.trimIndent()
            }
            addFile(
                    "src/main/java/com/privacysandboxsdk/consumer/HelloWorld.kt",
                    // language=kotlin
                    """
                package com.example.privacysandboxsdk.consumer

                class HelloWorld {

                    fun doSomething() {
                        // The line below should compile if classes from another SDK are in the
                        // same compile classpath.
                        com.example.sdkImplA.Example().f1()
                    }
                }
            """.trimIndent()
            )
        }
        rootProject {
            useNewPluginsDsl = true
            plugins.add(PluginType.KSP)
        }
    }
            .withAdditionalMavenRepo(mavenRepo)
            .addGradleProperties("${BooleanOption.USE_ANDROID_X.propertyName}=true")
            .enableProfileOutput()
            .create()
}


