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

package com.android.tools.preview.screenshot.tasks

import com.android.testutils.MockitoKt.mock
import com.android.tools.preview.screenshot.services.AnalyticsService
import com.android.tools.render.compose.ComposeRenderingResult
import com.android.tools.render.compose.ComposeScreenshotResult
import com.android.tools.render.compose.writeComposeRenderingResult
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers
import org.mockito.Mockito.withSettings
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

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
        val resultsFile = File(renderTaskOutputDir, "results.json")
        val path1 = Paths.get(renderTaskOutputDir.absolutePath).resolve("com.example.agptest.ExampleInstrumentedTest.preview_a45d2556_da39a3ee_0.png")
        val path2 = Paths.get(renderTaskOutputDir.absolutePath).resolve("com.example.agptest.ExampleInstrumentedTest.preview1_da39a3ee_4c0e9d96_0.png")
        val path3 = Paths.get(renderTaskOutputDir.absolutePath).resolve("com.example.agptest.ExampleInstrumentedTest.preview1_da39a3ee_4c0e9d96_1.png")
        val composeResults = listOf(ComposeScreenshotResult("com.example.agptest.ExampleInstrumentedTest.preview_a45d2556_da39a3ee_0", path1.toString(), null ),
            ComposeScreenshotResult("com.example.agptest.ExampleInstrumentedTest.preview1_da39a3ee_4c0e9d96_0", path2.toString(), null ),
            ComposeScreenshotResult("com.example.agptest.ExampleInstrumentedTest.preview1_da39a3ee_4c0e9d96_1", path3.toString(), null ))
        val result = ComposeRenderingResult(globalError = null, screenshotResults = composeResults)
        writeComposeRenderingResult(resultsFile.writer(), result)
        Files.createFile(path1)
        Files.createFile(path2)
        Files.createFile(path3)
        task.referenceImageDir.set(referenceImageDir)
        task.renderTaskOutputDir.set(renderTaskOutputDir)
        task.analyticsService.set(object: AnalyticsService() {
            override val buildServiceRegistry: BuildServiceRegistry = mock(
                withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS))
            override fun getParameters(): Params = mock()
        })

        task.run()
        assert(referenceImageDir.listFiles().isNotEmpty())
        assert(referenceImageDir.listFiles().size == 3)
    }

    @Test
    fun testPreviewScreenshotUpdateWithNoPreviews() {
        val referenceImageDir = tempDirRule.newFolder("references")
        val renderTaskOutputDir = tempDirRule.newFolder("rendered")
        task.referenceImageDir.set(referenceImageDir)
        task.renderTaskOutputDir.set(renderTaskOutputDir)
        task.analyticsService.set(object: AnalyticsService() {
            override val buildServiceRegistry: BuildServiceRegistry = mock(
                withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS))
            override fun getParameters(): Params = mock()
        })

        task.run()
        assert(referenceImageDir.listFiles().isEmpty())
    }
}
