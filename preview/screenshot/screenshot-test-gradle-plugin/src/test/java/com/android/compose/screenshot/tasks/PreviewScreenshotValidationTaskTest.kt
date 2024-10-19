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
import org.gradle.api.GradleException
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import kotlin.test.assertFailsWith

class PreviewScreenshotValidationTaskTest {
    @get:Rule
    val tempDirRule = TemporaryFolder()

    private lateinit var task: PreviewScreenshotValidationTask

    @Before
    fun setUp() {
        val project = ProjectBuilder.builder().withProjectDir(tempDirRule.newFolder()).build()
        task = project.tasks.create("validateDebugScreenshotTest", PreviewScreenshotValidationTask::class.java)
    }

    @Test
    fun testReportAnalyticsData() {
        val diffDir = tempDirRule.newFolder("diffs")
        val resultsDir = tempDirRule.newFile("results")
        val referenceImageDir = tempDirRule.newFolder("references")
        val renderOutputDir = tempDirRule.newFolder("rendered")
        val renderOutputFile = tempDirRule.newFile("results.json")
        val previewsFile = tempDirRule.newFile("previews_discovered.json")

        // Copy the same image to rendered output and reference images
        val previewId = "com.example.project.ExampleInstrumentedTest.GreetingPreview_3d8b4969_da39a3ee"
        previewsFile.writeText("""
            {
              "screenshots": [
                {
                  "methodFQN": "com.example.project.ExampleInstrumentedTest.GreetingPreview",
                  "methodParams": [],
                  "previewParams": {
                    "showBackground": "true"
                  },
                  "previewId": "$previewId"
                }
              ]
            }
        """.trimIndent())
        javaClass.getResourceAsStream("circle.png")!!
            .copyTo(referenceImageDir.resolve("$previewId.png").canonicalFile.apply { parentFile!!.mkdirs() }.outputStream())
        javaClass.getResourceAsStream("circle.png")!!
            .copyTo(renderOutputDir.resolve("${previewId}_0.png").canonicalFile.apply { parentFile!!.mkdirs() }.outputStream())
        task.previewFile.set(previewsFile)
        task.diffImageDir.set(diffDir)
        task.renderTaskOutputFile.set(renderOutputFile)
        task.resultsDir.set(resultsDir)

        val analyticsService = spy(object: AnalyticsService() {
            override val buildServiceRegistry: BuildServiceRegistry = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
            override fun getParameters(): Params = mock<Params>(defaultAnswer = Answers.RETURNS_DEEP_STUBS).apply {
                    `when`(androidGradlePluginVersion.get()).thenReturn("")
            }
        })
        task.analyticsService.set(analyticsService)

        assertFailsWith<GradleException>("Reference images missing. Please run the update<variant>ScreenshotTest task to generate the reference images.") {
            task.executeTests()
        }

        verify(analyticsService).recordPreviewScreenshotTestRun(eq(1))
    }
}
