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
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.TestUtils
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test

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
                    }
                """.trimIndent()
            }
            addFile(
                    "src/main/kotlin/com/Example.kt", """
                package foo

                import androidx.compose.foundation.layout.Column
                import androidx.compose.material.Text
                import androidx.compose.runtime.Composable

                @Composable
                fun MainView() {
                    Column {
                        Text(text = "Hello World")
                    }
                }
            """.trimIndent()
            )
            addFile(
                    "src/androidTest/kotlin/com/ExampleTest.kt", """
                package foo

                import androidx.compose.ui.tooling.preview.Preview
                import androidx.compose.runtime.Composable

                @Preview(showBackground = true)
                @Composable
                fun MainViewTest() {
                    MainView()
                }
            """.trimIndent()
            )
        }
    }
            .withKotlinGradlePlugin(true)
            .withKotlinVersion(TestUtils.KOTLIN_VERSION_FOR_COMPOSE_TESTS)
            .create()

    @Test
    fun runScreenshotTest() {
        project.executor()
                .with(BooleanOption.USE_ANDROID_X, true)
                .with(BooleanOption.ENABLE_SCREENSHOT_TEST, true)
                .run("screenshotTestDebugAndroidTest")

        assertThat(
                project.getOutputFile(
                        "androidTest-results","screenshot","debug",
                        "MainViewTest.png")).exists()
    }
}
