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
                include(project(":android-lib1"))
                include(project(":android-lib2"))
                include("com.externaldep:externaljar:1")
                include("com.externaldep:externalaar:1")
            }
        }
        privacySandboxSdkLibraryProject(":android-lib2") {
            android {
                namespace = "com.example.androidlib2"
                minSdk = 23
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
                minSdk = 23
            }
            dependencies {
                implementation("androidx.privacysandbox.tools:tools-apipackager:$androidxPrivacySandboxVersion")
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


