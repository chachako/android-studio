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

package com.android.build.gradle.integration.dsl

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.DEFAULT_COMPILE_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.app.ManifestFileBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.utils.XmlUtils
import com.google.common.io.Files
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.nio.charset.StandardCharsets

class LegacyVariantApiDslManifestPlaceholderTest {
    companion object {
        private const val mainPermissionSuffix = "main"
        private const val androidTestPermissionSuffix = "test"
        private const val unitTestPermissionSuffix = "unitTest"
        private const val permissionPrefix = "org.test.permission.READ_CREDENTIALS"
        private val libraryManifest = with(ManifestFileBuilder()) {
            addUsesPermissionTag("$permissionPrefix:\${permissionSuffix}")
            build()
        }
    }

    @get:Rule
    val project = createGradleProject {
        // A Gradle project with 3 modules: app, lib1 and lib2. lib2 has a manifest with a placeholder.
        // lib1 depends on lib2. We use legacy variant API to specify substitutions for lib2's placeholder
        // for lib1's unit test and android test variants in lib1's build file.
        // app depends on lib2. We use legacy variant API to specify substitutions for lib2's placeholder
        // for app's application variant in app's build file.
        subProject("app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                minSdk = 14
                setUpHelloWorld(true)
            }
            dependencies {
                implementation(project(":lib2"))
            }
            appendToBuildFile {
                """
                    android.applicationVariants.configureEach { variant ->
                        variant.mergedFlavor.manifestPlaceholders += [
                                "permissionSuffix" : "$mainPermissionSuffix",
                        ]
                    }

                """.trimIndent()
            }
        }
        subProject(":lib1") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                minSdk = 14
                hasInstrumentationTests = true
                defaultCompileSdk()
            }
            dependencies {
                androidTestImplementation(project(":lib2"))
                testImplementation(project(":lib2"))
            }
            appendToBuildFile {
                """
                     android.testVariants.configureEach {
                        mergedFlavor.manifestPlaceholders += [
                                "permissionSuffix" : "$androidTestPermissionSuffix",
                        ]
                    }
                    android.unitTestVariants.configureEach {
                        mergedFlavor.manifestPlaceholders += [
                                "permissionSuffix" : "$unitTestPermissionSuffix",
                        ]
                    }
                    android.testOptions {
                        unitTests.includeAndroidResources = true
                    }

                """.trimIndent()
            }
        }
        subProject(":lib2") {
            plugins.add(PluginType.ANDROID_LIB)
            addFile("src/main/AndroidManifest.xml", libraryManifest)
            android {
                compileSdk = DEFAULT_COMPILE_SDK_VERSION
                hasInstrumentationTests = false
            }
        }
    }

    @Test
    fun applicationVariantManifestPlaceholder() {
        project.execute(":app:processDebugManifest")
        verifyPermissionAddedToManifest(
                "app/build/intermediates/merged_manifests/debug/AndroidManifest.xml",
                mainPermissionSuffix)
    }

    @Test
    fun androidTestManifestPlaceholder() {
        project.execute(":lib1:processDebugAndroidTestManifest")
        verifyPermissionAddedToManifest(
                "lib1/build/intermediates/packaged_manifests/debugAndroidTest/AndroidManifest.xml",
                androidTestPermissionSuffix)
    }

    @Test
    fun unitTestManifestPlaceholder() {
        project.execute(":lib1:processDebugUnitTestManifest")
        verifyPermissionAddedToManifest(
                "lib1/build/intermediates/packaged_manifests/debugUnitTest/AndroidManifest.xml",
                unitTestPermissionSuffix)
    }

    private fun verifyPermissionAddedToManifest(manifestPath: String, permissionSuffix: String) {
        val manifestFile =
                project.file(manifestPath)

        val document = XmlUtils.parseDocument(
                Files.asCharSource(manifestFile, StandardCharsets.UTF_8).read(), false)
        val nodeList = document.getElementsByTagName(SdkConstants.TAG_USES_PERMISSION)

        var found = false
        for (i in 0 until nodeList.length) {
            val permissionName = nodeList.item(i).attributes.getNamedItem("android:name")?.nodeValue
            found = found || permissionName.equals("$permissionPrefix:$permissionSuffix")
        }
        Truth.assertThat(found).isTrue()
    }
}
