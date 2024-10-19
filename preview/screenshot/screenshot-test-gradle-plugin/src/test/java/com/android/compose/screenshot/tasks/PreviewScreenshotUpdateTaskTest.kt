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

package com.android.compose.screenshot.tasks

import com.android.compose.screenshot.services.AnalyticsService
import com.android.tools.render.compose.ComposeRenderingResult
import com.android.tools.render.compose.ComposeScreenshotResult
import com.android.tools.render.compose.ScreenshotError
import com.android.tools.render.compose.writeComposeRenderingResult
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers
import org.mockito.kotlin.mock
import java.nio.file.Files
import org.gradle.api.GradleException
import java.io.File
import kotlin.io.path.Path
import kotlin.test.assertFailsWith

class PreviewScreenshotUpdateTaskTest {
    @get:Rule
    val tempDirRule = TemporaryFolder()

    private lateinit var task: PreviewScreenshotUpdateTask

    @Before
    fun setUp() {
        val project = ProjectBuilder.builder().withProjectDir(tempDirRule.newFolder()).build()
        task = project.tasks.create("debugPreviewUpdateTest", PreviewScreenshotUpdateTask::class.java)
    }
    @Test
    fun testPreviewScreenshotUpdate() {
        val referenceImageDir = tempDirRule.newFolder("references")

        val renderTaskOutputDir = tempDirRule.newFolder("rendered")
        val imagePath = "com/example/agptest/ExampleInstrumentedTest"
        val renderTaskOutputPath = Path(renderTaskOutputDir.absolutePath + "/" + imagePath)
        Files.createDirectories(renderTaskOutputPath)
        val resultsFile = tempDirRule.newFile("results.json")
        val image1 = "com/example/agptest/ExampleInstrumentedTest/preview_a45d2556_da39a3ee_0.png"
        val image2 = "com/example/agptest/ExampleInstrumentedTest/preview1_da39a3ee_4c0e9d96_0.png"
        val image3 = "com/example/agptest/ExampleInstrumentedTest/preview1_da39a3ee_4c0e9d96_1.png"
        val composeResults = listOf(
            ComposeScreenshotResult("com.example.agptest.ExampleInstrumentedTest.preview_a45d2556_da39a3ee_0", "com.example.agptest.ExampleInstrumentedTest.preview",
                image1, null ),
            ComposeScreenshotResult("com.example.agptest.ExampleInstrumentedTest.preview1_da39a3ee_4c0e9d96_0", "com.example.agptest.ExampleInstrumentedTest.preview1",
                image2, null ),
            ComposeScreenshotResult("com.example.agptest.ExampleInstrumentedTest.preview1_da39a3ee_4c0e9d96_1", "com.example.agptest.ExampleInstrumentedTest.preview1",
                image3, null ))
        val result = ComposeRenderingResult(globalError = null, screenshotResults = composeResults)
        writeComposeRenderingResult(resultsFile.writer(), result)
        Files.createFile(renderTaskOutputDir.toPath().resolve(image1))
        Files.createFile(renderTaskOutputDir.toPath().resolve(image2))
        Files.createFile(renderTaskOutputDir.toPath().resolve(image3))
        task.referenceImageDir.set(referenceImageDir)
        task.renderTaskOutputDir.set(renderTaskOutputDir)
        task.renderTaskResultFile.set(resultsFile)
        task.analyticsService.set(object: AnalyticsService() {
            override val buildServiceRegistry: BuildServiceRegistry =
                mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
            override fun getParameters(): Params = mock()
        })

        task.run()

        val referenceImages = File(referenceImageDir.absolutePath + "/" + imagePath)
        assert(referenceImages.listFiles().isNotEmpty())
        assert(referenceImages.listFiles().size == 3)
    }

    @Test
    fun testPreviewScreenshotUpdateWithNoPreviews() {
        val referenceImageDir = tempDirRule.newFolder("references")
        val renderTaskOutputDir = tempDirRule.newFolder("rendered")
        val resultsFile = tempDirRule.newFile("results.json")
        writeComposeRenderingResult(resultsFile.writer(), ComposeRenderingResult(null, listOf()))
        task.referenceImageDir.set(referenceImageDir)
        task.renderTaskOutputDir.set(renderTaskOutputDir)
        task.renderTaskResultFile.set(resultsFile)
        task.analyticsService.set(object: AnalyticsService() {
            override val buildServiceRegistry: BuildServiceRegistry =
                mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
            override fun getParameters(): Params = mock()
        })

        task.run()
        referenceImageDir.listFiles()?.let { assert(it.isEmpty()) }
    }

    @Test
    fun testPreviewScreenshotUpdateWithErrors() {
        val referenceImageDir = tempDirRule.newFolder("references")
        val imagePath = "com/example/agptest/ExampleInstrumentedTest"
        val referenceImageDirPath = Path(referenceImageDir.absolutePath + "/" + imagePath)
        Files.createDirectories(referenceImageDirPath)
        val renderTaskOutputDir = tempDirRule.newFolder("rendered")
        val renderTaskOutputPath = Path(renderTaskOutputDir.absolutePath + "/" + imagePath)
        Files.createDirectories(renderTaskOutputPath)
        val resultsFile = tempDirRule.newFile("results.json")
        val image1 = "com/example/agptest/ExampleInstrumentedTest/preview_a45d2556_da39a3ee_0.png"
        val image2 = "com/example/agptest/ExampleInstrumentedTest/preview1_da39a3ee_4c0e9d96_0.png"
        val image1Ref = "com/example/agptest/ExampleInstrumentedTest/preview_a45d2556_da39a3ee_0.png"
        val image2Ref = "com/example/agptest/ExampleInstrumentedTest/preview1_da39a3ee_4c0e9d96_0.png"
        val composeRenderingResult = listOf(ComposeScreenshotResult("com.example.agptest.ExampleInstrumentedTest.preview_a45d2556_da39a3ee_0", "com.example.agptest.ExampleInstrumentedTest.preview", image1,null ),
            ComposeScreenshotResult("com.example.agptest.ExampleInstrumentedTest.preview1_da39a3ee_4c0e9d96_0", "com.example.agptest.ExampleInstrumentedTest.preview1", image2, null ),
            ComposeScreenshotResult("com.example.agptest.ExampleInstrumentedTest.preview1_da39a3ee_4c0e9d96_1", "com.example.agptest.ExampleInstrumentedTest.preview1","preview1_da39a3ee_4c0e9d96_1.png", ScreenshotError("ERROR", "MESSAGE", "STACK_TRACE", listOf(), listOf(), listOf())))
        writeComposeRenderingResult(resultsFile.writer(), ComposeRenderingResult(null, composeRenderingResult))
        Files.createFile(renderTaskOutputDir.toPath().resolve(image1))
        Files.createFile(renderTaskOutputDir.toPath().resolve(image2))
        Files.createFile(referenceImageDir.toPath().resolve(image2Ref))
        Files.createFile(referenceImageDir.toPath().resolve(image1Ref))
        task.referenceImageDir.set(referenceImageDir)
        task.renderTaskOutputDir.set(renderTaskOutputDir)
        task.renderTaskResultFile.set(resultsFile)
        task.analyticsService.set(object: AnalyticsService() {
            override val buildServiceRegistry: BuildServiceRegistry =
                mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
            override fun getParameters(): Params = mock()
        })
        val composeScreenshot = composeRenderingResult[2]

        assertFailsWith<GradleException>("Cannot update reference images. Rendering failed for ${composeScreenshot.previewId}. Error: ${composeScreenshot.error!!.message}. Check ${resultsFile.absolutePath} for additional info") {
            task.run()
        }

        val referenceImages = File(referenceImageDir.absolutePath + "/" + imagePath)
        assert(referenceImages.listFiles().size == 2)
    }

    @Test
    fun testPreviewScreenshotUpdateWithEmptyPreview() {
        val referenceImageDir = tempDirRule.newFolder("references")
        val renderTaskOutputDir = tempDirRule.newFolder("rendered")
        val resultsFile = tempDirRule.newFile("results.json")
        val imagePath = "com/example/agptest/com.example.agptest.ExampleInstrumentedTest.preview_a45d2556_da39a3ee_0.png"
        val composeRenderingResult = listOf(ComposeScreenshotResult("com.example.agptest.ExampleInstrumentedTest.preview_a45d2556_da39a3ee_0", "com.example.agptest.ExampleInstrumentedTest.preview",
            imagePath, ScreenshotError("SUCCESS", "Nothing to render in Preview. Cannot generate image", "", listOf(), listOf(), listOf())) )
        writeComposeRenderingResult(resultsFile.writer(), ComposeRenderingResult(null, composeRenderingResult))
        task.referenceImageDir.set(referenceImageDir)
        task.renderTaskOutputDir.set(renderTaskOutputDir)
        task.renderTaskResultFile.set(resultsFile)
        task.analyticsService.set(object: AnalyticsService() {
            override val buildServiceRegistry: BuildServiceRegistry =
                mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
            override fun getParameters(): Params = mock()
        })
        val composeScreenshot = composeRenderingResult[0]
        assertFailsWith<GradleException>("Cannot update reference images. Rendering failed for ${composeScreenshot.imagePath.substringBeforeLast(".")}. Error: ${composeScreenshot.error!!.message}. Check ${resultsFile.absolutePath} for additional info") {
            task.run()
        }
        referenceImageDir.listFiles()?.let { assert(it.isEmpty()) }
    }
}
