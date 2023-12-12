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

package com.android.build.gradle.integration.testing.screenshot

import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.android.testutils.TestUtils.KOTLIN_VERSION_FOR_COMPOSE_TESTS
import com.google.common.truth.Truth.assertThat

class ScreenshotTest {

    @get:Rule
    val project = createGradleProjectBuilder {
        withKotlinPlugin = true
        rootProject {
            plugins.add(PluginType.ANDROID_LIB)
            plugins.add(PluginType.KOTLIN_ANDROID)
            android {
                setUpHelloWorld()
                minSdk = 24
                hasInstrumentationTests = true
            }
            dependencies {
                testImplementation("junit:junit:4.13.2")
                implementation("androidx.compose.ui:ui-tooling:${TaskManager.COMPOSE_UI_VERSION}")
                implementation("androidx.compose.material:material:${TaskManager.COMPOSE_UI_VERSION}")
            }
            appendToBuildFile {
                """
                    android {
                        buildFeatures {
                            compose true
                        }
                        composeOptions {
                            useLiveLiterals false
                        }
                        kotlinOptions {
                            freeCompilerArgs += [
                              "-P", "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=true",
                            ]
                        }
                        kotlin {
                            jvmToolchain(17)
                        }
                    }
                """.trimIndent()
            }
            addFile(
                    "src/main/java/com/Example.kt", """
                package pkg.name

                import androidx.compose.material.Text
                import androidx.compose.runtime.Composable

                @Composable
                fun SimpleComposable() {
                    Text("Hello World")
                }
            """.trimIndent()
            )
            addFile(
                    "src/androidTest/java/com/ExampleTest.kt", """
                package pkg.name

                import androidx.compose.ui.tooling.preview.Preview
                import androidx.compose.runtime.Composable

                class ExampleTest {
                    @Preview(showBackground = true)
                    @Composable
                    fun SimpleComposableTest() {
                        SimpleComposable()
                    }
                }
            """.trimIndent()
            )
        }
    }
            .withKotlinGradlePlugin(true)
            .withKotlinVersion(KOTLIN_VERSION_FOR_COMPOSE_TESTS)
            .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                buildscript {
                    dependencies {
                        classpath "com.android.tools.preview.screenshot:preview-screenshot-gradle-plugin:+"
                    }
                }
                apply plugin: 'com.android.tools.preview.screenshot'
            """.trimIndent()
        )
    }

    @Test
    fun discoverPreviews() {
        project.executor()
            .with(BooleanOption.USE_ANDROID_X, true)
            .withFailOnWarning(false) // TODO(298678053): Remove after updating TestUtils.KOTLIN_VERSION_FOR_COMPOSE_TESTS to 1.8.0+
            .run("debugPreviewDiscovery")

        val previewsDiscoveredFile  = project.file(project.buildDir.absolutePath + "/intermediates/preview/debug/previews_discovered.json")
        assertThat(previewsDiscoveredFile).exists()
        assertThat(previewsDiscoveredFile.readText()).isEqualTo("""
            {
              "screenshots": [
                {
                  "methodFQN": "pkg.name.ExampleTest.SimpleComposableTest",
                  "methodParams": [],
                  "previewParams": {
                    "showBackground": "true"
                  },
                  "imageName": "pkg.name.ExampleTest.SimpleComposableTest_3d8b4969_da39a3ee"
                }
              ]
            }
        """.trimIndent())
    }
}
