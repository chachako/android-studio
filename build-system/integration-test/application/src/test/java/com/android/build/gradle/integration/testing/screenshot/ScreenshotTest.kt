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
import com.android.build.gradle.integration.common.fixture.ProfileCapturer
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.TestUtils.KOTLIN_VERSION_FOR_COMPOSE_TESTS
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.build.gradle.internal.profile.GradleTaskExecutionType
import com.google.common.collect.Iterables
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

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
                fun SimpleComposable(text: String = "Hello World") {
                    Text(text)
                }
            """.trimIndent()
            )
            addFile(
                "src/main/java/com/SimplePreviewParameterProvider.kt", """
                package pkg.name

                import androidx.compose.ui.tooling.preview.PreviewParameterProvider

                class SimplePreviewParameterProvider : PreviewParameterProvider<String> {
                    override val values = sequenceOf(
                        "Primary text", "Secondary text"
                    )
                }
            """.trimIndent()
            )
            addFile(
                    "src/androidTest/java/com/ExampleTest.kt", """
                package pkg.name

                import androidx.compose.ui.tooling.preview.Preview
                import androidx.compose.ui.tooling.preview.PreviewParameter
                import androidx.compose.runtime.Composable

                class ExampleTest {
                    @Preview(showBackground = true)
                    @Composable
                    fun simpleComposableTest() {
                        SimpleComposable()
                    }

                    @Preview(showBackground = true)
                    @Preview(showBackground = false)
                    @Composable
                    fun multiPreviewTest() {
                        SimpleComposable()
                    }

                    @Preview
                    @Composable
                    fun parameterProviderTest(
                        @PreviewParameter(SimplePreviewParameterProvider::class) data: String
                    ) {
                       SimpleComposable(data)
                    }
                }
            """.trimIndent()
            )
        }
    }
            .withKotlinGradlePlugin(true)
            .withKotlinVersion(KOTLIN_VERSION_FOR_COMPOSE_TESTS)
            .enableProfileOutput()
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

    private fun getExecutor(): GradleTaskExecutor =
        project.executor()
            .with(BooleanOption.USE_ANDROID_X, true)
            .withFailOnWarning(false) // TODO(298678053): Remove after updating TestUtils.KOTLIN_VERSION_FOR_COMPOSE_TESTS to 1.8.0+

    @Test
    fun discoverPreviews() {
        getExecutor().run("debugPreviewDiscovery")
        val previewsDiscoveredFile  = project.buildDir.resolve("intermediates/preview/debug/previews_discovered.json")
        assert(previewsDiscoveredFile.exists())
        assertThat(previewsDiscoveredFile.readText()).isEqualTo("""
            {
              "screenshots": [
                {
                  "methodFQN": "pkg.name.ExampleTest.simpleComposableTest",
                  "methodParams": [],
                  "previewParams": {
                    "showBackground": "true"
                  },
                  "imageName": "pkg.name.ExampleTest.simpleComposableTest_3d8b4969_da39a3ee"
                },
                {
                  "methodFQN": "pkg.name.ExampleTest.multiPreviewTest",
                  "methodParams": [],
                  "previewParams": {
                    "showBackground": "true"
                  },
                  "imageName": "pkg.name.ExampleTest.multiPreviewTest_3d8b4969_da39a3ee"
                },
                {
                  "methodFQN": "pkg.name.ExampleTest.multiPreviewTest",
                  "methodParams": [],
                  "previewParams": {
                    "showBackground": "false"
                  },
                  "imageName": "pkg.name.ExampleTest.multiPreviewTest_a45d2556_da39a3ee"
                },
                {
                  "methodFQN": "pkg.name.ExampleTest.parameterProviderTest",
                  "methodParams": [
                    {
                      "provider": "pkg.name.SimplePreviewParameterProvider"
                    }
                  ],
                  "previewParams": {},
                  "imageName": "pkg.name.ExampleTest.parameterProviderTest_da39a3ee_77e30523"
                }
              ]
            }
        """.trimIndent())
    }

    @Test
    fun runPreviewScreenshotTest() {
        // Generate screenshots to be tested against
        getExecutor().run("previewScreenshotUpdateDebugAndroidTest")

        val referenceScreenshotDir = project.projectDir.resolve("src/androidTest/screenshot/debug/").toPath()
        assertThat(referenceScreenshotDir.listDirectoryEntries().map { it.name }).containsExactly(
            "pkg.name.ExampleTest.simpleComposableTest_3d8b4969_da39a3ee_0.png",
            "pkg.name.ExampleTest.multiPreviewTest_3d8b4969_da39a3ee_0.png",
            "pkg.name.ExampleTest.multiPreviewTest_a45d2556_da39a3ee_0.png",
            "pkg.name.ExampleTest.parameterProviderTest_da39a3ee_77e30523_0.png",
            "pkg.name.ExampleTest.parameterProviderTest_da39a3ee_77e30523_1.png"
        )

        // Validate previews matches screenshots
        getExecutor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF) // TODO(322357154) Remove this when configuration caching issues are resolved
            .run("previewScreenshotDebugAndroidTest")

        // Verify that test engine generated HTML reports and all tests pass
        val indexHtmlReport = project.buildDir.resolve("reports/tests/previewScreenshotDebugAndroidTest/index.html")
        val classHtmlReport = project.buildDir.resolve("reports/tests/previewScreenshotDebugAndroidTest/classes/pkg.name.ExampleTest.html")
        val packageHtmlReport = project.buildDir.resolve("reports/tests/previewScreenshotDebugAndroidTest/packages/pkg.name.html")
        assertThat(indexHtmlReport).exists()
        assertThat(classHtmlReport).exists()
        val expectedOutput = listOf(
            """<td class="success">simpleComposableTest</td>""",
            """<td class="success">multiPreviewTest_{showBackground=true}</td>""",
            """<td class="success">multiPreviewTest_{showBackground=false}</td>""",
            """<td class="success">parameterProviderTest_[{provider=pkg.name.SimplePreviewParameterProvider}]_0</td>""",
            """<td class="success">parameterProviderTest_[{provider=pkg.name.SimplePreviewParameterProvider}]_1</td>"""
        )
        var classHtmlReportText = classHtmlReport.readText()
        expectedOutput.forEach { assertThat(classHtmlReportText).contains(it) }
        assertThat(packageHtmlReport).exists()

        // Assert that no diff images were generated because screenshot matched the reference image
        val diffDir = project.buildDir.resolve("outputs/androidTest-results/preview/debug/diffs").toPath()
        assert(diffDir.listDirectoryEntries().isEmpty())

        // Update previews to be different from the references
        val testFile = project.projectDir.resolve("src/main/java/com/Example.kt")
        TestFileUtils.searchAndReplace(testFile, "Hello World", "HelloWorld ")
        val previewParameterProviderFile = project.projectDir.resolve("src/main/java/com/SimplePreviewParameterProvider.kt")
        TestFileUtils.searchAndReplace(previewParameterProviderFile, "Primary text", " Primarytext")

        // Rerun validation task - modified tests should fail and diffs are generated
        getExecutor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF) // TODO(322357154) Remove this when configuration caching issues are resolved
            .expectFailure()
            .run("previewScreenshotDebugAndroidTest")

        assertThat(indexHtmlReport).exists()
        assertThat(classHtmlReport).exists()
        val expectedOutputAfterChangingPreviews = listOf(
            "Failed tests",
            """<td class="failures">simpleComposableTest</td>""",
            """<td class="failures">multiPreviewTest_{showBackground=true}</td>""",
            """<td class="failures">multiPreviewTest_{showBackground=false}</td>""",
            """<td class="failures">parameterProviderTest_[{provider=pkg.name.SimplePreviewParameterProvider}]_0</td>""",
            """<td class="success">parameterProviderTest_[{provider=pkg.name.SimplePreviewParameterProvider}]_1</td>"""
        )
        classHtmlReportText = classHtmlReport.readText()
        expectedOutputAfterChangingPreviews.forEach { assertThat(classHtmlReportText).contains(it) }
        assertThat(packageHtmlReport).exists()

        assertThat(diffDir).exists()
        assertThat(diffDir.listDirectoryEntries().map { it.name }).containsExactly(
            "pkg.name.ExampleTest.simpleComposableTest_3d8b4969_da39a3ee_0.png",
            "pkg.name.ExampleTest.multiPreviewTest_3d8b4969_da39a3ee_0.png",
            "pkg.name.ExampleTest.multiPreviewTest_a45d2556_da39a3ee_0.png",
            "pkg.name.ExampleTest.parameterProviderTest_da39a3ee_77e30523_0.png"
        )
    }

    @Test
    fun analytics() {
        val capturer = ProfileCapturer(project)

        val profiles = capturer.capture {
            getExecutor().run("debugPreviewDiscovery")
        }

        val spanList = Iterables.getOnlyElement(profiles).spanList
        val taskSpan = spanList.first {
            it.task.type == GradleTaskExecutionType.PREVIEW_DISCOVERY_VALUE
        }
        val executionSpan = spanList.first {
            it.parentId == taskSpan.id && it.type == ExecutionType.TASK_EXECUTION_ALL_PHASES
        }
        assertThat(executionSpan.durationInMs).isGreaterThan(0L)
    }
}
