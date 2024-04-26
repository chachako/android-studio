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

import com.android.build.gradle.integration.common.fixture.GradleBuildResult
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.ProfileCapturer
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.SubProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.integration.common.truth.forEachLine
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.TestUtils
import com.android.testutils.TestUtils.KOTLIN_VERSION_FOR_COMPOSE_TESTS
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.build.gradle.internal.profile.GradleTaskExecutionType
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileOutputStream
import java.util.UUID
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class ScreenshotTest {

    @get:Rule
    val project = createGradleProjectBuilder {
        withKotlinPlugin = true
        subProject("app") {
            plugins.add(PluginType.ANDROID_APP)
            setupProject()
        }
        subProject("lib") {
            plugins.add(PluginType.ANDROID_LIB)
            setupProject()
        }
    }
            .withKotlinGradlePlugin(true)
            .withKotlinVersion(KOTLIN_VERSION_FOR_COMPOSE_TESTS)
            .enableProfileOutput()
            .create()

    @Before
    fun tweakBuildScriptForRootProject() {
        // Do not add any buildscript dependencies, those are added per project
        // to enforce Gradle to load them by a separate classloader per project.
        project.buildFile.writeText("""
            apply from: "../commonHeader.gradle"
        """.trimIndent())
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "${BooleanOption.ENABLE_SCREENSHOT_TEST.propertyName}=true"
        )
    }

    @JvmField
    @Rule
    val temporaryFolder = TemporaryFolder()

    private val appProject: GradleTestProject
        get() = project.getSubproject("app")

    private fun SubProjectBuilder.setupProject() {
        plugins.add(PluginType.KOTLIN_ANDROID)
        plugins.add(PluginType.Custom("com.android.tools.preview.screenshot"))
        appendToBuildFile {
            val customJarName = UUID.randomUUID().toString()
            val customJar = temporaryFolder.newFile(customJarName)
            JarOutputStream(FileOutputStream(customJar)).use {
                it.putNextEntry(JarEntry(customJarName))
                it.write(customJarName.toByteArray())
                it.closeEntry()
            }
            """
            buildscript {
                apply from: "../../commonBuildScript.gradle"
                dependencies {
                    classpath "com.android.tools.preview.screenshot:preview-screenshot-gradle-plugin:+"

                    // Gradle will use a separate classloader for a project only when it has a
                    // different set of classpath dependencies. So here we add an empty jar file.
                    classpath files('${customJar.invariantSeparatorsPath}')
                }
            }
            println("Class loader for AGP API = " + com.android.build.api.variant.AndroidComponentsExtension.class.getClassLoader().hashCode())
            """
        }
        android {
            setUpHelloWorld()
            minSdk = 24
            hasInstrumentationTests = true
        }
        dependencies {
            testImplementation("junit:junit:4.13.2")
            implementation("androidx.compose.ui:ui-tooling:${TaskManager.COMPOSE_UI_VERSION}")
            implementation("androidx.compose.ui:ui-tooling-preview:${TaskManager.COMPOSE_UI_VERSION}")
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
                composeOptions {
                    kotlinCompilerExtensionVersion = "${TestUtils.COMPOSE_COMPILER_FOR_TESTS}"
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

                    @Preview(widthDp = 800, heightDp = 800)
                    @Composable
                    fun simpleComposableTest2() {
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
        addFile(
                "src/androidTest/java/com/TopLevelPreviewTest.kt", """

                package pkg.name

                import androidx.compose.ui.tooling.preview.Preview
                import androidx.compose.ui.tooling.preview.PreviewParameter
                import androidx.compose.runtime.Composable

                @Preview(showBackground = true)
                @Composable
                fun simpleComposableTest_3() {
                    SimpleComposable()
                }

            """.trimIndent()
        )
    }

    private fun getExecutor(): GradleTaskExecutor =
        project.executor()
            .with(BooleanOption.USE_ANDROID_X, true)

    @Test
    fun discoverPreviews() {
        getExecutor().run(":app:debugPreviewDiscovery")
        val previewsDiscoveredFile  = appProject.buildDir.resolve("intermediates/preview/debug/previews_discovered.json")
        assert(previewsDiscoveredFile.exists())
        assertThat(previewsDiscoveredFile.readText()).isEqualTo("""
            {
              "screenshots": [
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
                },
                {
                  "methodFQN": "pkg.name.ExampleTest.simpleComposableTest2",
                  "methodParams": [],
                  "previewParams": {
                    "heightDp": "800",
                    "widthDp": "800"
                  },
                  "imageName": "pkg.name.ExampleTest.simpleComposableTest2_b55c4b0c_da39a3ee"
                },
                {
                  "methodFQN": "pkg.name.ExampleTest.simpleComposableTest",
                  "methodParams": [],
                  "previewParams": {
                    "showBackground": "true"
                  },
                  "imageName": "pkg.name.ExampleTest.simpleComposableTest_3d8b4969_da39a3ee"
                },
                {
                  "methodFQN": "pkg.name.TopLevelPreviewTestKt.simpleComposableTest_3",
                  "methodParams": [],
                  "previewParams": {
                    "showBackground": "true"
                  },
                  "imageName": "pkg.name.TopLevelPreviewTestKt.simpleComposableTest_3_3d8b4969_da39a3ee"
                }
              ]
            }
        """.trimIndent())
    }

    @Test
    fun runPreviewScreenshotTest() {
        // Generate screenshots to be tested against
        getExecutor().run(":app:previewScreenshotUpdateDebugAndroidTest")

        val referenceScreenshotDir = appProject.projectDir.resolve("src/androidTest/screenshot/debug/").toPath()
        assertThat(referenceScreenshotDir.listDirectoryEntries().map { it.name }).containsExactly(
            "pkg.name.ExampleTest.simpleComposableTest_3d8b4969_da39a3ee_0.png",
            "pkg.name.ExampleTest.simpleComposableTest2_b55c4b0c_da39a3ee_0.png",
            "pkg.name.ExampleTest.multiPreviewTest_3d8b4969_da39a3ee_0.png",
            "pkg.name.ExampleTest.multiPreviewTest_a45d2556_da39a3ee_0.png",
            "pkg.name.ExampleTest.parameterProviderTest_da39a3ee_77e30523_0.png",
            "pkg.name.ExampleTest.parameterProviderTest_da39a3ee_77e30523_1.png",
            "pkg.name.TopLevelPreviewTestKt.simpleComposableTest_3_3d8b4969_da39a3ee_0.png"
        )

        // Validate previews matches screenshots
        getExecutor().run(":app:previewScreenshotDebugAndroidTest")

        // Verify that HTML reports are generated and all tests pass
        val indexHtmlReport = appProject.buildDir.resolve("reports/androidTests/preview/debug/index.html")
        val classHtmlReport = appProject.buildDir.resolve("reports/androidTests/preview/debug/pkg.name.ExampleTest.html")
        val class2HtmlReport = appProject.buildDir.resolve("reports/androidTests/preview/debug/pkg.name.TopLevelPreviewTestKt.html")
        val packageHtmlReport = appProject.buildDir.resolve("reports/androidTests/preview/debug/pkg.name.html")
        assertThat(indexHtmlReport).exists()
        assertThat(classHtmlReport).exists()
        val expectedOutput = listOf(
            """<h3 class="success">simpleComposableTest</h3>""",
            """<h3 class="success">simpleComposableTest2</h3>""",
            """<h3 class="success">multiPreviewTest_{showBackground=true}</h3>""",
            """<h3 class="success">multiPreviewTest_{showBackground=false}</h3>""",
            """<h3 class="success">parameterProviderTest_[{provider=pkg.name.SimplePreviewParameterProvider}]_0</h3>""",
            """<h3 class="success">parameterProviderTest_[{provider=pkg.name.SimplePreviewParameterProvider}]_1</h3>"""
        )
        var classHtmlReportText = classHtmlReport.readText()
        expectedOutput.forEach { assertThat(classHtmlReportText).contains(it) }
        assertThat(class2HtmlReport.readText()).contains("""<h3 class="success">simpleComposableTest_3</h3>""")
        assertThat(packageHtmlReport).exists()

        // Assert that no diff images were generated because screenshot matched the reference image
        val diffDir = appProject.buildDir.resolve("outputs/androidTest-results/preview/debug/diffs").toPath()
        assert(diffDir.listDirectoryEntries().isEmpty())

        // Update previews to be different from the references
        val testFile = appProject.projectDir.resolve("src/main/java/com/Example.kt")
        TestFileUtils.searchAndReplace(testFile, "Hello World", "HelloWorld ")
        val previewParameterProviderFile = appProject.projectDir.resolve("src/main/java/com/SimplePreviewParameterProvider.kt")
        TestFileUtils.searchAndReplace(previewParameterProviderFile, "Primary text", " Primarytext")

        // Rerun validation task - modified tests should fail and diffs are generated
        getExecutor().expectFailure().run(":app:previewScreenshotDebugAndroidTest")

        assertThat(indexHtmlReport).exists()
        assertThat(classHtmlReport).exists()
        val expectedOutputAfterChangingPreviews = listOf(
            "Failed tests",
            """<h3 class="failures">simpleComposableTest</h3>""",
            """<h3 class="failures">simpleComposableTest2</h3>""",
            """<h3 class="failures">multiPreviewTest_{showBackground=true}</h3>""",
            """<h3 class="failures">multiPreviewTest_{showBackground=false}</h3>""",
            """<h3 class="failures">parameterProviderTest_[{provider=pkg.name.SimplePreviewParameterProvider}]_0</h3>""",
            """<h3 class="success">parameterProviderTest_[{provider=pkg.name.SimplePreviewParameterProvider}]_1</h3>"""
        )
        classHtmlReportText = classHtmlReport.readText()
        expectedOutputAfterChangingPreviews.forEach { assertThat(classHtmlReportText).contains(it) }
        assertThat(class2HtmlReport.readText()).contains("""<h3 class="failures">simpleComposableTest_3</h3>""")
        assertThat(packageHtmlReport).exists()

        assertThat(diffDir).exists()
        assertThat(diffDir.listDirectoryEntries().map { it.name }).containsExactly(
            "pkg.name.ExampleTest.simpleComposableTest_3d8b4969_da39a3ee_0.png",
            "pkg.name.ExampleTest.simpleComposableTest2_b55c4b0c_da39a3ee_0.png",
            "pkg.name.ExampleTest.multiPreviewTest_3d8b4969_da39a3ee_0.png",
            "pkg.name.ExampleTest.multiPreviewTest_a45d2556_da39a3ee_0.png",
            "pkg.name.ExampleTest.parameterProviderTest_da39a3ee_77e30523_0.png",
            "pkg.name.TopLevelPreviewTestKt.simpleComposableTest_3_3d8b4969_da39a3ee_0.png"
        )
    }

    @Test
    fun runPreviewScreenshotTestWithMultiModuleProject() {
        // Generate screenshots to be tested against
        verifyClassLoaderSetup(getExecutor().run("previewScreenshotUpdateDebugAndroidTest"))

        // Validate previews matches screenshots
        verifyClassLoaderSetup(getExecutor().run("previewScreenshotDebugAndroidTest"))
    }

    private fun verifyClassLoaderSetup(result: GradleBuildResult) {
        val taskLogs = mutableSetOf<String>()
        result.stdout.forEachLine {
            if (it.startsWith("Class loader for AGP API = ")) {
                taskLogs.add(it)
            }
        }
        assertThat(taskLogs)
                .named("Log lines that should contain different class loader hashes")
                .hasSize(2)
    }

    @Test
    fun analytics() {
        val capturer = ProfileCapturer(project)

        val profiles = capturer.capture {
            getExecutor().run(":app:debugPreviewDiscovery")
        }

        profiles.mapNotNull { profile ->
            val spanList = profile.spanList
            val taskSpan = spanList.firstOrNull {
                it.task.type == GradleTaskExecutionType.PREVIEW_DISCOVERY_VALUE
            } ?: return@mapNotNull null
            val executionSpan = spanList.firstOrNull {
                it.parentId == taskSpan.id && it.type == ExecutionType.TASK_EXECUTION_ALL_PHASES
            } ?: return@mapNotNull null
            executionSpan.durationInMs
        }.first { durationInMs ->
            durationInMs > 0L
        }
    }

    @Test
    fun runPreviewScreenshotTestWithNoPreviewsToTest() {
        // Comment out preview tests
        val testFile1 = appProject.projectDir.resolve("src/androidTest/java/com/ExampleTest.kt")
        TestFileUtils.replaceLine(testFile1, 1, "/*")
        TestFileUtils.replaceLine(testFile1, testFile1.readLines().size, "*/")
        val testFile2 = appProject.projectDir.resolve("src/androidTest/java/com/TopLevelPreviewTest.kt")
        TestFileUtils.replaceLine(testFile2, 1, "/*")
        TestFileUtils.replaceLine(testFile2, testFile2.readLines().size, "*/")

        getExecutor().run(":app:previewScreenshotUpdateDebugAndroidTest")

        val referenceScreenshotDir = appProject.projectDir.resolve("src/androidTest/screenshot/debug/").toPath()
        assertThat(referenceScreenshotDir.listDirectoryEntries()).isEmpty()

        val resultsJson = appProject.buildDir.resolve("outputs/androidTest-results/preview/debug/rendered/results.json")
        assertThat(resultsJson.readText()).contains(""""screenshotResults": []""")

        getExecutor()
            .withFailOnWarning(false) // TODO(b/333398506): remove once fixed
            .run(":app:previewScreenshotDebugAndroidTest")

        val indexHtmlReport = appProject.buildDir.resolve("reports/androidTests/preview/debug/index.html")
        assertThat(indexHtmlReport).exists()
        assertThat(indexHtmlReport.readText()).contains("""<div class="counter">0</div>""")

        // Uncomment preview tests
        TestFileUtils.replaceLine(testFile1, 1, "")
        TestFileUtils.replaceLine(testFile1, testFile1.readLines().size, "")
        TestFileUtils.replaceLine(testFile2, 1, "")
        TestFileUtils.replaceLine(testFile2, testFile2.readLines().size, "")
    }

    @Test
    fun runPreviewScreenshotTestsWithMissingUiToolingDep() {
        val uiToolingDep =
            "implementation 'androidx.compose.ui:ui-tooling:${TaskManager.COMPOSE_UI_VERSION}'"

        // Verify that no exception is thrown when ui-tooling is added as an androidTestImplementation dependency
        val androidTestImplementationDep = "androidTestImplementation 'androidx.compose.ui:ui-tooling:${TaskManager.COMPOSE_UI_VERSION}'"
        TestFileUtils.searchAndReplace(appProject.buildFile, uiToolingDep, androidTestImplementationDep)
        getExecutor().run(":app:previewScreenshotUpdateDebugAndroidTest")

        // Verify that exception is thrown when ui-tooling dep is missing
        TestFileUtils.searchAndReplace(appProject.buildFile, androidTestImplementationDep, "")
        val result =
            getExecutor().expectFailure().run(":app:previewScreenshotUpdateDebugAndroidTest")
        result.assertErrorContains("Missing required runtime dependency. Please add androidx.compose.ui:ui-tooling to your testing module's dependencies.")
    }

    @Test
    fun runPreviewScreenshotTestsOnMultipleFlavors() {
        TestFileUtils.appendToFile(
            appProject.buildFile,
            """
            android {
                flavorDimensions "new"
                productFlavors {
                    create("flavor1") {
                        dimension "new"
                    }
                     create("flavor2") {
                        dimension "new"
                    }
                }
            }
            """.trimIndent()
        )

        // Comment out the previews in ExampleTest to limit this test to running on the preview in TopLevelPreviewTest
        val testFile1 = appProject.projectDir.resolve("src/androidTest/java/com/ExampleTest.kt")
        TestFileUtils.replaceLine(testFile1, 1, "/*")
        TestFileUtils.replaceLine(testFile1, testFile1.readLines().size, "*/")

        getExecutor().run(":app:previewScreenshotUpdateAndroidTest")

        // Verify that reference images are created for both flavors
        val flavor1ReferenceScreenshotDir = appProject.projectDir.resolve("src/androidTest/screenshot/debug/flavor1").toPath()
        val flavor2ReferenceScreenshotDir = appProject.projectDir.resolve("src/androidTest/screenshot/debug/flavor2").toPath()
        assertThat(flavor1ReferenceScreenshotDir.listDirectoryEntries().single().name)
            .isEqualTo("pkg.name.TopLevelPreviewTestKt.simpleComposableTest_3_3d8b4969_da39a3ee_0.png")
        assertThat(flavor2ReferenceScreenshotDir.listDirectoryEntries().single().name)
            .isEqualTo("pkg.name.TopLevelPreviewTestKt.simpleComposableTest_3_3d8b4969_da39a3ee_0.png")

        getExecutor().run(":app:previewScreenshotAndroidTest")

        // Verify that HTML reports are generated for each flavor and all tests pass
        val flavor1IndexHtmlReport = appProject.buildDir.resolve("reports/androidTests/preview/debug/flavor1/index.html")
        val flavor2IndexHtmlReport = appProject.buildDir.resolve("reports/androidTests/preview/debug/flavor2/index.html")
        val flavor1ClassHtmlReport = appProject.buildDir.resolve("reports/androidTests/preview/debug/flavor1/pkg.name.TopLevelPreviewTestKt.html")
        val flavor2ClassHtmlReport = appProject.buildDir.resolve("reports/androidTests/preview/debug/flavor2/pkg.name.TopLevelPreviewTestKt.html")
        val flavor1PackageHtmlReport = appProject.buildDir.resolve("reports/androidTests/preview/debug/flavor1/pkg.name.html")
        val flavor2PackageHtmlReport = appProject.buildDir.resolve("reports/androidTests/preview/debug/flavor2/pkg.name.html")
        assertThat(flavor1IndexHtmlReport).exists()
        assertThat(flavor2IndexHtmlReport).exists()
        assertThat(flavor1ClassHtmlReport).exists()
        assertThat(flavor2ClassHtmlReport).exists()
        val expectedOutput = listOf(
            """<h3 class="success">simpleComposableTest_3</h3>""",
        )
        expectedOutput.forEach {
            assertThat(flavor1ClassHtmlReport.readText()).contains(it)
            assertThat(flavor2ClassHtmlReport.readText()).contains(it)
        }
        assertThat(flavor1PackageHtmlReport).exists()
        assertThat(flavor2PackageHtmlReport).exists()

        // Assert that no diff images were generated because screenshots matched the reference images
        val diffDir1 = appProject.buildDir.resolve("outputs/androidTest-results/preview/debug/flavor1/diffs").toPath()
        val diffDir2 = appProject.buildDir.resolve("outputs/androidTest-results/preview/debug/flavor2/diffs").toPath()
        assert(diffDir1.listDirectoryEntries().isEmpty())
        assert(diffDir2.listDirectoryEntries().isEmpty())
    }
}
