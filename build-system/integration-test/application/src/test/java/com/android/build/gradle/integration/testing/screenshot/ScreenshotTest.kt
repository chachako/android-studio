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
import com.android.build.gradle.integration.common.fixture.GradleBuildResult
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.LoggingLevel
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
import kotlin.io.path.deleteExisting
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
        subProject("lib1") {
            plugins.add(PluginType.ANDROID_LIB)
            setupProject()
        }

        // All of these libraries will share the same class loader.
        // See b/340362066 for more details.
        repeat(2) {
            subProject("lib2_$it") {
                plugins.add(PluginType.ANDROID_LIB)
                setupProject(addEmptyJarToClassPath = false)
            }
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

    private fun SubProjectBuilder.setupProject(addEmptyJarToClassPath: Boolean = true) {
        plugins.add(PluginType.KOTLIN_ANDROID)
        plugins.add(PluginType.Custom("com.android.compose.screenshot"))
        appendToBuildFile {
            val customJar = if (addEmptyJarToClassPath) {
                val customJarName = UUID.randomUUID().toString()
                val customJar = temporaryFolder.newFile(customJarName)
                JarOutputStream(FileOutputStream(customJar)).use {
                    it.putNextEntry(JarEntry(customJarName))
                    it.write(customJarName.toByteArray())
                    it.closeEntry()
                }
                customJar
            } else {
                null
            }

            """
            buildscript {
                apply from: "../../commonBuildScript.gradle"
                dependencies {
                    classpath "com.android.compose.screenshot:screenshot-test-gradle-plugin:+"

                    ${if(customJar != null) {"""
                        // Gradle will use a separate classloader for a project only when it has a
                        // different set of classpath dependencies. So here we add an empty jar file.
                        classpath files('${customJar.invariantSeparatorsPath}')
                    """} else {""}}
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
                composeOptions {
                    kotlinCompilerExtensionVersion = "${TestUtils.COMPOSE_COMPILER_FOR_TESTS}"
                }
                kotlin {
                    jvmToolchain(17)
                }
                experimentalProperties["android.experimental.enableScreenshotTest"] = true

            }
            // Add test listener to log additional test results for easier debugging when tests failed.
            tasks.withType(Test) {
                addTestListener(new TestListener() {
                    @Override
                    void beforeSuite(TestDescriptor suite) {
                        println "Starting test suite: " + suite.getName()
                    }

                    @Override
                    void afterSuite(TestDescriptor suite, TestResult result) {
                        println "Finished test suite: " + suite.getName() + " with result: " + result.getResultType()
                        result.exception?.printStackTrace()
                    }

                    @Override
                    void beforeTest(TestDescriptor testDescriptor) {
                        println "Starting test: " + testDescriptor.getName()
                    }

                    @Override
                    void afterTest(TestDescriptor testDescriptor, TestResult result) {
                        println "Finished test: " + testDescriptor.getName() + " with result: " + result.getResultType()
                        result.exception?.printStackTrace()
                    }
                })
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
                "src/main/java/com/ParameterProviders.kt", """
                package pkg.name

                import androidx.compose.ui.tooling.preview.PreviewParameterProvider

                class SimplePreviewParameterProvider : PreviewParameterProvider<String> {
                    override val values = sequenceOf(
                        "Primary text", "Secondary text"
                    )
                }
            """.trimIndent()
        )
        addFile("src/screenshotTest/java/com/AnotherPreviewParameterProvider.kt", """
            package pkg.name

            import androidx.compose.ui.tooling.preview.PreviewParameterProvider

            class AnotherPreviewParameterProvider : PreviewParameterProvider<String> {
                override val values = sequenceOf(
                    "text 1", "text 2"
                )
            }
        """.trimIndent())
        addFile(
                "src/screenshotTest/java/com/ExampleTest.kt", """

                package pkg.name

                import androidx.compose.ui.tooling.preview.Preview
                import androidx.compose.ui.tooling.preview.PreviewParameter
                import androidx.compose.runtime.Composable

                class ExampleTest {
                    @Preview(name = "simpleComposable", showBackground = true)
                    @Composable
                    fun simpleComposableTest() {
                        SimpleComposable()
                    }

                    @Preview(name = "simpleComposable", widthDp = 800, heightDp = 800)
                    @Composable
                    fun simpleComposableTest2() {
                        SimpleComposable()
                    }

                    @Preview(name = "with_Background", showBackground = true)
                    @Preview(name = "withoutBackground", showBackground = false)
                    @Composable
                    fun multiPreviewTest() {
                        SimpleComposable()
                    }

                    @Preview(name = "simplePreviewParameterProvider")
                    @Composable
                    fun parameterProviderTest(
                        @PreviewParameter(SimplePreviewParameterProvider::class) data: String
                    ) {
                       SimpleComposable(data)
                    }

                    @Preview
                    @Composable
                    fun multipleParameterProviderTest(
                        @PreviewParameter(SimplePreviewParameterProvider::class) data: String,
                        @PreviewParameter(AnotherPreviewParameterProvider::class) text: String = "!"
                    ) {
                       val stringToDisplay = data + " " + text
                       SimpleComposable(stringToDisplay)
                    }

                    @Preview(name = "invalid/File/Name")
                    @Composable
                    fun previewNameCannotBeUsedAsFileNameTest() {
                        SimpleComposable()
                    }
                }

            """.trimIndent()
        )
        addFile(
                "src/screenshotTest/java/com/TopLevelPreviewTest.kt", """

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
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .with(BooleanOption.USE_ANDROID_X, true)
            .withLoggingLevel(LoggingLevel.LIFECYCLE)

    @Test
    fun runPreviewScreenshotTestWithThreshold() {
        getExecutor().run(":app:updateScreenshotTest")
        //update the preview - tests fail
        val testFile = appProject.projectDir.resolve("src/main/java/com/Example.kt")
        TestFileUtils.searchAndReplace(testFile, "Hello World", "Hello Worid")
        val result = getExecutor().expectFailure().run(":app:validateScreenshotTest")
        result.assertErrorContains("There were failing tests. See the results at: ")

        //set high threshold - tests pass
        TestFileUtils.appendToFile(
            appProject.buildFile,
            """
            android {
                testOptions {
                    screenshotTests {
                        imageDifferenceThreshold = 0.5f
                    }
                }
            }
            """.trimIndent()
        )
        getExecutor().run(":app:validateScreenshotTest")

        //reduce threshold - tests fail
        TestFileUtils.searchAndReplace(appProject.buildFile, "imageDifferenceThreshold = 0.5f", "imageDifferenceThreshold = 0.001f")
        val resultLowThreshold = getExecutor().expectFailure().run(":app:validateScreenshotTest")
        resultLowThreshold.assertErrorContains("There were failing tests. See the results at: ")
    }

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
                    "name": "with_Background",
                    "showBackground": "true"
                  },
                  "previewId": "pkg.name.ExampleTest.multiPreviewTest_with_Background_e1f26d19_da39a3ee"
                },
                {
                  "methodFQN": "pkg.name.ExampleTest.multiPreviewTest",
                  "methodParams": [],
                  "previewParams": {
                    "name": "withoutBackground",
                    "showBackground": "false"
                  },
                  "previewId": "pkg.name.ExampleTest.multiPreviewTest_withoutBackground_5676c0a6_da39a3ee"
                },
                {
                  "methodFQN": "pkg.name.ExampleTest.multipleParameterProviderTest",
                  "methodParams": [
                    {
                      "provider": "pkg.name.AnotherPreviewParameterProvider"
                    },
                    {
                      "provider": "pkg.name.SimplePreviewParameterProvider"
                    }
                  ],
                  "previewParams": {},
                  "previewId": "pkg.name.ExampleTest.multipleParameterProviderTest_da39a3ee_b3bbe100"
                },
                {
                  "methodFQN": "pkg.name.ExampleTest.parameterProviderTest",
                  "methodParams": [
                    {
                      "provider": "pkg.name.SimplePreviewParameterProvider"
                    }
                  ],
                  "previewParams": {
                    "name": "simplePreviewParameterProvider"
                  },
                  "previewId": "pkg.name.ExampleTest.parameterProviderTest_simplePreviewParameterProvider_e3342a25_77e30523"
                },
                {
                  "methodFQN": "pkg.name.ExampleTest.previewNameCannotBeUsedAsFileNameTest",
                  "methodParams": [],
                  "previewParams": {
                    "name": "invalid/File/Name"
                  },
                  "previewId": "pkg.name.ExampleTest.previewNameCannotBeUsedAsFileNameTest_b249e5c1_da39a3ee"
                },
                {
                  "methodFQN": "pkg.name.ExampleTest.simpleComposableTest2",
                  "methodParams": [],
                  "previewParams": {
                    "heightDp": "800",
                    "name": "simpleComposable",
                    "widthDp": "800"
                  },
                  "previewId": "pkg.name.ExampleTest.simpleComposableTest2_simpleComposable_05ad9183_da39a3ee"
                },
                {
                  "methodFQN": "pkg.name.ExampleTest.simpleComposableTest",
                  "methodParams": [],
                  "previewParams": {
                    "name": "simpleComposable",
                    "showBackground": "true"
                  },
                  "previewId": "pkg.name.ExampleTest.simpleComposableTest_simpleComposable_7759f1e3_da39a3ee"
                },
                {
                  "methodFQN": "pkg.name.TopLevelPreviewTestKt.simpleComposableTest_3",
                  "methodParams": [],
                  "previewParams": {
                    "showBackground": "true"
                  },
                  "previewId": "pkg.name.TopLevelPreviewTestKt.simpleComposableTest_3_3d8b4969_da39a3ee"
                }
              ]
            }
        """.trimIndent())
    }

    @Test
    fun runPreviewScreenshotTest() {
        // Generate screenshots to be tested against
        getExecutor().run(":app:updateDebugScreenshotTest")

        val exampleTestReferenceScreenshotDir = appProject.projectDir.resolve("src/debug/screenshotTest/reference/pkg/name/ExampleTest").toPath()
        val topLevelTestReferenceScreenshotDir = appProject.projectDir.resolve("src/debug/screenshotTest/reference/pkg/name/TopLevelPreviewTestKt").toPath()
        assertThat(exampleTestReferenceScreenshotDir.listDirectoryEntries().map { it.name }).containsExactly(
            "simpleComposableTest_simpleComposable_7759f1e3_da39a3ee_0.png",
            "simpleComposableTest2_simpleComposable_05ad9183_da39a3ee_0.png",
            "multiPreviewTest_with_Background_e1f26d19_da39a3ee_0.png",
            "multiPreviewTest_withoutBackground_5676c0a6_da39a3ee_0.png",
            "multipleParameterProviderTest_da39a3ee_b3bbe100_1.png",
            "multipleParameterProviderTest_da39a3ee_b3bbe100_0.png",
            "parameterProviderTest_simplePreviewParameterProvider_e3342a25_77e30523_1.png",
            "parameterProviderTest_simplePreviewParameterProvider_e3342a25_77e30523_0.png",
            "previewNameCannotBeUsedAsFileNameTest_b249e5c1_da39a3ee_0.png",
        )
        assertThat(topLevelTestReferenceScreenshotDir.listDirectoryEntries().map { it.name }).containsExactly(
            "simpleComposableTest_3_3d8b4969_da39a3ee_0.png"
        )

        // Validate previews matches screenshots
        getExecutor().run(":app:validateDebugScreenshotTest")

        // Verify that HTML reports are generated and all tests pass
        val indexHtmlReport = appProject.buildDir.resolve("reports/screenshotTest/preview/debug/index.html")
        val classHtmlReport = appProject.buildDir.resolve("reports/screenshotTest/preview/debug/pkg.name.ExampleTest.html")
        val class2HtmlReport = appProject.buildDir.resolve("reports/screenshotTest/preview/debug/pkg.name.TopLevelPreviewTestKt.html")
        val packageHtmlReport = appProject.buildDir.resolve("reports/screenshotTest/preview/debug/pkg.name.html")
        assertThat(indexHtmlReport).exists()
        assertThat(classHtmlReport).exists()
        val expectedOutput = listOf(
            """<h3 class="success">simpleComposableTest_simpleComposable</h3>""",
            """<h3 class="success">simpleComposableTest2_simpleComposable</h3>""",
            """<h3 class="success">multiPreviewTest_with_Background_{showBackground=true}</h3>""",
            """<h3 class="success">multiPreviewTest_withoutBackground_{showBackground=false}</h3>""",
            """<h3 class="success">multipleParameterProviderTest_[{provider=pkg.name.AnotherPreviewParameterProvider},""",
            """{provider=pkg.name.SimplePreviewParameterProvider}]_0</h3>""",
            """<h3 class="success">multipleParameterProviderTest_[{provider=pkg.name.AnotherPreviewParameterProvider},""",
            """{provider=pkg.name.SimplePreviewParameterProvider}]_1</h3>""",
            """<h3 class="success">previewNameCannotBeUsedAsFileNameTest_invalid/File/Name</h3>""",
            """<h3 class="success">parameterProviderTest_simplePreviewParameterProvider_[{provider=pkg.name.SimplePreviewParameterProvider}]_0</h3>""",
            """<h3 class="success">parameterProviderTest_simplePreviewParameterProvider_[{provider=pkg.name.SimplePreviewParameterProvider}]_1</h3>"""
        )
        var classHtmlReportText = classHtmlReport.readText()
        expectedOutput.forEach { assertThat(classHtmlReportText).contains(it) }
        assertThat(class2HtmlReport.readText()).contains("""<h3 class="success">simpleComposableTest_3</h3>""")
        assertThat(packageHtmlReport).exists()

        // Assert that no diff images were generated because screenshot matched the reference image
        val exampleTestDiffDir = appProject.buildDir.resolve("outputs/screenshotTest-results/preview/debug/diffs/pkg/name/ExampleTest").toPath()
        val topLevelTestDiffDir = appProject.buildDir.resolve("outputs/screenshotTest-results/preview/debug/diffs/pkg/name/TopLevelPreviewTestKt").toPath()
        assert(exampleTestDiffDir.listDirectoryEntries().isEmpty())
        assert(topLevelTestDiffDir.listDirectoryEntries().isEmpty())

        // Update previews to be different from the references
        val testFile = appProject.projectDir.resolve("src/main/java/com/Example.kt")
        TestFileUtils.searchAndReplace(testFile, "Hello World", "HelloWorld ")
        val previewParameterProviderFile = appProject.projectDir.resolve("src/main/java/com/ParameterProviders.kt")
        TestFileUtils.searchAndReplace(previewParameterProviderFile, "Primary text", " Primarytext")

        // Rerun validation task - modified tests should fail and diffs are generated
        getExecutor().expectFailure().run(":app:validateDebugScreenshotTest")

        assertThat(indexHtmlReport).exists()
        assertThat(classHtmlReport).exists()
        val expectedOutputAfterChangingPreviews = listOf(
            "Failed tests",
            """<h3 class="failures">simpleComposableTest_simpleComposable</h3>""",
            """<h3 class="failures">simpleComposableTest2_simpleComposable</h3>""",
            """<h3 class="failures">multiPreviewTest_with_Background_{showBackground=true}</h3>""",
            """<h3 class="failures">multiPreviewTest_withoutBackground_{showBackground=false}</h3>""",
            """<h3 class="success">multipleParameterProviderTest_[{provider=pkg.name.AnotherPreviewParameterProvider},""",
            """{provider=pkg.name.SimplePreviewParameterProvider}]_0</h3>""",
            """<h3 class="success">multipleParameterProviderTest_[{provider=pkg.name.AnotherPreviewParameterProvider},""",
            """{provider=pkg.name.SimplePreviewParameterProvider}]_1</h3>""",
            """<h3 class="failures">previewNameCannotBeUsedAsFileNameTest_invalid/File/Name</h3>""",
            """<h3 class="failures">parameterProviderTest_simplePreviewParameterProvider_[{provider=pkg.name.SimplePreviewParameterProvider}]_0</h3>""",
            """<h3 class="success">parameterProviderTest_simplePreviewParameterProvider_[{provider=pkg.name.SimplePreviewParameterProvider}]_1</h3>"""
        )
        classHtmlReportText = classHtmlReport.readText()
        expectedOutputAfterChangingPreviews.forEach { assertThat(classHtmlReportText).contains(it) }
        assertThat(class2HtmlReport.readText()).contains("""<h3 class="failures">simpleComposableTest_3</h3>""")
        assertThat(packageHtmlReport).exists()

        assertThat(exampleTestDiffDir.listDirectoryEntries().map { it.name }).containsExactly(
            "simpleComposableTest_simpleComposable_7759f1e3_da39a3ee_0.png",
            "simpleComposableTest2_simpleComposable_05ad9183_da39a3ee_0.png",
            "multiPreviewTest_with_Background_e1f26d19_da39a3ee_0.png",
            "multiPreviewTest_withoutBackground_5676c0a6_da39a3ee_0.png",
            "parameterProviderTest_simplePreviewParameterProvider_e3342a25_77e30523_0.png",
            "previewNameCannotBeUsedAsFileNameTest_b249e5c1_da39a3ee_0.png",
        )
        assertThat(topLevelTestDiffDir.listDirectoryEntries().map { it.name }).containsExactly(
            "simpleComposableTest_3_3d8b4969_da39a3ee_0.png"
        )
    }

    @Test
    fun runPreviewScreenshotTestWithMultiModuleProject() {
        // Generate screenshots to be tested against
        verifyClassLoaderSetup(getExecutor().run("updateDebugScreenshotTest"))

        // Validate previews matches screenshots
        verifyClassLoaderSetup(getExecutor().run("validateDebugScreenshotTest"))
    }

    @Test
    fun runUpdateScreenshotTestWithMultiModuleProjectBySingleWorker() {
        // Set the max workers to 1 to let Gradle reuse the same worker daemon process for
        // running PreviewRenderWorkAction more than once. See b/340362066 for more details.
        getExecutor().withArguments(listOf("--max-workers", "1")).run("updateScreenshotTest")
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
                .hasSize(3)
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
    fun runPreviewScreenshotTestWithNoSourceFiles() {
        // Delete test classes so that there are no source files in screenshotTest source set
        appProject.projectDir.resolve("src/screenshotTest/java/com/ExampleTest.kt").toPath().deleteExisting()
        appProject.projectDir.resolve("src/screenshotTest/java/com/TopLevelPreviewTest.kt").toPath().deleteExisting()
        appProject.projectDir.resolve("src/screenshotTest/java/com/AnotherPreviewParameterProvider.kt").toPath().deleteExisting()

        getExecutor().run(":app:updateDebugScreenshotTest")

        val referenceScreenshotDir = appProject.projectDir.resolve("src/debug/screenshotTest/reference").toPath()
        assertThat(referenceScreenshotDir.listDirectoryEntries()).isEmpty()

        val resultsJson = appProject.buildDir.resolve("outputs/screenshotTest-results/preview/debug/results.json")
        assertThat(resultsJson.readText()).contains(""""screenshotResults": []""")

        // Validation and reporting is skipped when there are no source files
        val result2 = getExecutor().run(":app:validateDebugScreenshotTest")
        assertThat(result2.skippedTasks).containsAtLeastElementsIn(
            listOf(":app:validateDebugScreenshotTest", ":app:debugScreenshotReport")
        )

        val indexHtmlReport = appProject.buildDir.resolve("reports/screenshotTest/preview/debug/index.html")
        assertThat(indexHtmlReport).doesNotExist()
    }

    @Test
    fun runPreviewScreenshotTestWithSourceFilesAndNoPreviewsToTest() {
        // Comment out preview tests so that source files exist with no previews to test
        val testFile1 = appProject.projectDir.resolve("src/screenshotTest/java/com/ExampleTest.kt")
        TestFileUtils.replaceLine(testFile1, 1, "/*")
        TestFileUtils.replaceLine(testFile1, testFile1.readLines().size, "*/")
        val testFile2 = appProject.projectDir.resolve("src/screenshotTest/java/com/TopLevelPreviewTest.kt")
        TestFileUtils.replaceLine(testFile2, 1, "/*")
        TestFileUtils.replaceLine(testFile2, testFile2.readLines().size, "*/")

        getExecutor().run(":app:updateDebugScreenshotTest")

        val referenceScreenshotDir = appProject.projectDir.resolve("src/debug/screenshotTest/reference").toPath()
        assertThat(referenceScreenshotDir.listDirectoryEntries()).isEmpty()

        val resultsJson = appProject.buildDir.resolve("outputs/screenshotTest-results/preview/debug/results.json")
        assertThat(resultsJson.readText()).contains(""""screenshotResults": []""")

        // Gradle test tasks fail when there are source files but no tests are executed starting in Gradle 9.0
        val result1 = getExecutor()
            .expectFailure()
            .run(":app:validateDebugScreenshotTest")
        assertThat(result1.skippedTasks).containsAtLeastElementsIn(
            listOf(":app:debugScreenshotReport")
        )

        val indexHtmlReport = appProject.buildDir.resolve("reports/screenshotTest/preview/debug/index.html")
        assertThat(indexHtmlReport).doesNotExist()
    }

    @Test
    fun runPreviewScreenshotTestsWithMissingUiToolingDep() {
        val uiToolingDep =
            "implementation 'androidx.compose.ui:ui-tooling:${TaskManager.COMPOSE_UI_VERSION}'"

        // Verify that no exception is thrown when ui-tooling is added as an screenshotTestImplementation dependency
        val screenshotTestImplementationDep = "screenshotTestImplementation 'androidx.compose.ui:ui-tooling:${TaskManager.COMPOSE_UI_VERSION}'"
        TestFileUtils.searchAndReplace(appProject.buildFile, uiToolingDep, screenshotTestImplementationDep)
        getExecutor().run(":app:updateDebugScreenshotTest")

        // Verify that exception is thrown when ui-tooling dep is missing
        TestFileUtils.searchAndReplace(appProject.buildFile, screenshotTestImplementationDep, "")
        val result =
            getExecutor().expectFailure().run(":app:updateDebugScreenshotTest")
        result.assertErrorContains("Missing required runtime dependency. Please add androidx.compose.ui:ui-tooling as a screenshotTestImplementation dependency.")
    }

    @Test
    fun runScreenshotTestWithMissingRefImageDir() {
        // Verify that tasks runs successfully before any screenshot tasks have been run
        getExecutor().run(":app:tasks")

        val result =
            getExecutor().expectFailure().run(":app:validateDebugScreenshotTest")
        result.assertErrorContains("Reference images missing. Please run the update<variant>ScreenshotTest task to generate the reference images.")
    }

    @Test
    fun runScreenshotTestWithEmptyPreview() {
        val testFile = appProject.projectDir.resolve("src/screenshotTest/java/com/TopLevelPreviewTest.kt")
        TestFileUtils.searchAndReplace(testFile, "SimpleComposable()", "")
        getExecutor().run(":app:tasks")

        val result =
            getExecutor().expectFailure().run(":app:updateDebugScreenshotTest")

        result.assertErrorContains("Cannot update reference images. Rendering failed for") //Exception thrown in Update task
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
        val testFile1 = appProject.projectDir.resolve("src/screenshotTest/java/com/ExampleTest.kt")
        TestFileUtils.replaceLine(testFile1, 1, "/*")
        TestFileUtils.replaceLine(testFile1, testFile1.readLines().size, "*/")

        getExecutor().run(":app:updateScreenshotTest")

        // Verify that reference images are created for both flavors
        val flavor1ReferenceScreenshotDir = appProject.projectDir.resolve("src/flavor1Debug/screenshotTest/reference/pkg/name/TopLevelPreviewTestKt").toPath()
        val flavor2ReferenceScreenshotDir = appProject.projectDir.resolve("src/flavor2Debug/screenshotTest/reference/pkg/name/TopLevelPreviewTestKt").toPath()
        assertThat(flavor1ReferenceScreenshotDir.listDirectoryEntries().single().name)
            .isEqualTo("simpleComposableTest_3_3d8b4969_da39a3ee_0.png")
        assertThat(flavor2ReferenceScreenshotDir.listDirectoryEntries().single().name)
            .isEqualTo("simpleComposableTest_3_3d8b4969_da39a3ee_0.png")

        getExecutor().run(":app:validateScreenshotTest")

        // Verify that HTML reports are generated for each flavor and all tests pass
        val flavor1IndexHtmlReport = appProject.buildDir.resolve("reports/screenshotTest/preview/debug/flavor1/index.html")
        val flavor2IndexHtmlReport = appProject.buildDir.resolve("reports/screenshotTest/preview/debug/flavor2/index.html")
        val flavor1ClassHtmlReport = appProject.buildDir.resolve("reports/screenshotTest/preview/debug/flavor1/pkg.name.TopLevelPreviewTestKt.html")
        val flavor2ClassHtmlReport = appProject.buildDir.resolve("reports/screenshotTest/preview/debug/flavor2/pkg.name.TopLevelPreviewTestKt.html")
        val flavor1PackageHtmlReport = appProject.buildDir.resolve("reports/screenshotTest/preview/debug/flavor1/pkg.name.html")
        val flavor2PackageHtmlReport = appProject.buildDir.resolve("reports/screenshotTest/preview/debug/flavor2/pkg.name.html")
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
        val diffDir1 = appProject.buildDir.resolve("outputs/screenshotTest-results/preview/debug/flavor1/diffs/pkg/name/TopLevelPreviewTestKt").toPath()
        val diffDir2 = appProject.buildDir.resolve("outputs/screenshotTest-results/preview/debug/flavor2/diffs/pkg/name/TopLevelPreviewTestKt").toPath()
        assert(diffDir1.listDirectoryEntries().isEmpty())
        assert(diffDir2.listDirectoryEntries().isEmpty())
    }
}
