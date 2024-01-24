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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.TestUtils.KOTLIN_VERSION_FOR_COMPOSE_TESTS
import com.google.common.truth.Truth.assertThat
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.io.path.listDirectoryEntries

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
                        testOptions {
                            unitTests.includeAndroidResources true
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
        getExecutor().run("debugPreviewDiscovery")
        val previewsDiscoveredFile  = project.buildDir.resolve("intermediates/preview/debug/previews_discovered.json")
        assert(previewsDiscoveredFile.exists())
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

    @Test
    fun runPreviewScreenshotTest() {
        // Generate screenshot to be tested against
        getExecutor().run("previewScreenshotUpdateDebugAndroidTest")

        val screenshot  = project.file("src/androidTest/screenshot/debug/pkg.name.ExampleTest.SimpleComposableTest_3d8b4969_da39a3ee_0.png")
        assertThat(screenshot).exists()

        // Validate preview matches screenshot
        getExecutor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF) // TODO(322357154) Remove this when configuration caching issues are resolved
            .run("previewScreenshotDebugAndroidTest")

        // Verify that test engine generated HTML reports and test passes
        val indexHtmlReport = project.buildDir.resolve("reports/tests/previewScreenshotDebugAndroidTest/index.html")
        val classHtmlReport = project.buildDir.resolve("reports/tests/previewScreenshotDebugAndroidTest/classes/pkg.name.ExampleTest.html")
        val packageHtmlReport = project.buildDir.resolve("reports/tests/previewScreenshotDebugAndroidTest/packages/pkg.name.html")
        assertThat(indexHtmlReport).exists()
        assertThat(classHtmlReport).exists()
        assertThat(classHtmlReport.readText()).doesNotContain("Failed tests")
        assertThat(packageHtmlReport).exists()

        // Assert that no diff images were generated because screenshot matched the reference image
        val diffDir = project.buildDir.resolve("outputs/androidTest-results/preview/debug/diffs").toPath()
        assert(diffDir.listDirectoryEntries().isEmpty())

        // Update preview to be different from the reference
        val testFile = project.projectDir.resolve("src/main/java/com/Example.kt")
        TestFileUtils.searchAndReplace(testFile, "Hello World", "HelloWorld ")

        // Rerun validation task - test should fail and diff is generated
        getExecutor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF) // TODO(322357154) Remove this when configuration caching issues are resolved
            .expectFailure()
            .run("previewScreenshotDebugAndroidTest")

        assertThat(indexHtmlReport).exists()
        assertThat(classHtmlReport).exists()
        assertThat(classHtmlReport.readText()).contains("Failed tests")
        assertThat(packageHtmlReport).exists()

        val diff = diffDir.resolve("pkg.name.ExampleTest.SimpleComposableTest_3d8b4969_da39a3ee_0.png")
        assertThat(diff).exists()
    }

    private fun getExecutor(): GradleTaskExecutor =
        project.executor()
            .with(BooleanOption.USE_ANDROID_X, true)
            .withFailOnWarning(false) // TODO(298678053): Remove after updating TestUtils.KOTLIN_VERSION_FOR_COMPOSE_TESTS to 1.8.0+
}
