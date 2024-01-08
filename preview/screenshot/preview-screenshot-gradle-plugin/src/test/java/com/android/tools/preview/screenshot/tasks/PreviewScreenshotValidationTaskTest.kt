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

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.preview.screenshot.services.AnalyticsService
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.verify

class PreviewScreenshotValidationTaskTest {
    @get:Rule
    val tempDirRule = TemporaryFolder()

    private lateinit var task: PreviewScreenshotValidationTask

    @Before
    fun setUp() {
        val project = ProjectBuilder.builder().withProjectDir(tempDirRule.newFolder()).build()
        task = project.tasks.create("previewScreenshotDebugAndroidTest", PreviewScreenshotValidationTask::class.java)
    }

    @Test
    fun testImageValidationMatchingImages() {
        val diffDir = tempDirRule.newFolder("diffs")
        val resultsFile = tempDirRule.newFile("results")
        val referenceImageDir = tempDirRule.newFolder("references")
        val renderOutputDir = tempDirRule.newFolder("rendered")
        val previewsFile = tempDirRule.newFile("previews_discovered.json")

        // Copy the same image to rendered output and reference images
        val previewImageName = "com.example.project.ExampleInstrumentedTest.GreetingPreview_3d8b4969_da39a3ee"
        previewsFile.writeText("""
            {
              "screenshots": [
                {
                  "methodFQN": "com.example.project.ExampleInstrumentedTest.GreetingPreview",
                  "methodParams": [],
                  "previewParams": {
                    "showBackground": "true"
                  },
                  "imageName": "$previewImageName"
                }
              ]
            }
        """.trimIndent())
        javaClass.getResourceAsStream("circle.png")!!
            .copyTo(referenceImageDir.resolve("$previewImageName.png").canonicalFile.apply { parentFile!!.mkdirs() }.outputStream())
        javaClass.getResourceAsStream("circle.png")!!
            .copyTo(renderOutputDir.resolve("${previewImageName}_0.png").canonicalFile.apply { parentFile!!.mkdirs() }.outputStream())

        task.previewFile.set(previewsFile)
        task.referenceImageDir.set(referenceImageDir)
        task.diffImageDir.set(diffDir)
        task.renderTaskOutputDir.set(renderOutputDir)
        task.resultsFile.set(resultsFile)
        task.analyticsService.set(mock<AnalyticsService>())

        task.run()

        assertThat(resultsFile.readText().trimIndent()).isEqualTo("""
            <?xml version='1.0' encoding='UTF-8' ?>
            <testsuite name="com.example.project.ExampleInstrumentedTest" tests="1" failures="0" errors="zero" skipped="zero">
              <properties />
              <testcase name="GreetingPreview" classname="com.example.project.ExampleInstrumentedTest">
                <success>PASSED</success>
                <images>
                  <reference path="${referenceImageDir.absolutePath}${File.separator}com.example.project.ExampleInstrumentedTest.GreetingPreview_3d8b4969_da39a3ee.png" />
                  <actual path="${renderOutputDir.absolutePath}${File.separator}com.example.project.ExampleInstrumentedTest.GreetingPreview_3d8b4969_da39a3ee_0.png" />
                  <diff message="Images match!" />
                </images>
              </testcase>
            </testsuite>
        """.trimIndent())
    }

    @Test
    fun testImageValidationDifferentImages() {
        val diffDir = tempDirRule.newFolder("diffs")
        val resultsFile = tempDirRule.newFile("results")
        val referenceImageDir = tempDirRule.newFolder("references")
        val renderOutputDir = tempDirRule.newFolder("rendered")
        val previewsFile = tempDirRule.newFile("previews_discovered.json")

        // Copy different images to rendered output and reference images
        val previewImageName = "com.example.project.ExampleInstrumentedTest.GreetingPreview_3d8b4969_da39a3ee"
        previewsFile.writeText("""
            {
              "screenshots": [
                {
                  "methodFQN": "com.example.project.ExampleInstrumentedTest.GreetingPreview",
                  "methodParams": [],
                  "previewParams": {
                    "showBackground": "true"
                  },
                  "imageName": "$previewImageName"
                }
              ]
            }
        """.trimIndent())
        javaClass.getResourceAsStream("circle.png")!!
            .copyTo(referenceImageDir.resolve("$previewImageName.png").canonicalFile.apply { parentFile!!.mkdirs() }.outputStream())
        javaClass.getResourceAsStream("star.png")!!
            .copyTo(renderOutputDir.resolve("${previewImageName}_0.png").canonicalFile.apply { parentFile!!.mkdirs() }.outputStream())

        task.previewFile.set(previewsFile)
        task.referenceImageDir.set(referenceImageDir)
        task.diffImageDir.set(diffDir)
        task.renderTaskOutputDir.set(renderOutputDir)
        task.resultsFile.set(resultsFile)
        task.analyticsService.set(mock<AnalyticsService>())

        val e = assertThrows(GradleException::class.java) {
            task.run()
        }
        assertThat(e.message).isEqualTo("There were failing tests")

        assertThat(resultsFile.readText().trimIndent()).isEqualTo("""
            <?xml version='1.0' encoding='UTF-8' ?>
            <testsuite name="com.example.project.ExampleInstrumentedTest" tests="1" failures="1" errors="zero" skipped="zero">
              <properties />
              <testcase name="GreetingPreview" classname="com.example.project.ExampleInstrumentedTest">
                <failure>FAILED</failure>
                <images>
                  <reference path="${referenceImageDir.absolutePath}${File.separator}com.example.project.ExampleInstrumentedTest.GreetingPreview_3d8b4969_da39a3ee.png" />
                  <actual path="${renderOutputDir.absolutePath}${File.separator}com.example.project.ExampleInstrumentedTest.GreetingPreview_3d8b4969_da39a3ee_0.png" />
                  <diff path="${diffDir.absolutePath}${File.separator}com.example.project.ExampleInstrumentedTest.GreetingPreview_3d8b4969_da39a3ee.png" />
                </images>
              </testcase>
            </testsuite>
        """.trimIndent())
    }

    @Test
    fun testImageValidationNoPreviewsToTest() {
        val diffDir = tempDirRule.newFolder("diffs")
        val resultsFile = tempDirRule.newFile("results")
        val referenceImageDir = tempDirRule.newFolder("references")
        val renderOutputDir = tempDirRule.newFolder("rendered")
        val previewsFile = tempDirRule.newFile("previews_discovered.json")
        previewsFile.writeText("""
            {
              "screenshots": [
              ]
            }
        """.trimIndent())

        task.previewFile.set(previewsFile)
        task.referenceImageDir.set(referenceImageDir)
        task.diffImageDir.set(diffDir)
        task.renderTaskOutputDir.set(renderOutputDir)
        task.resultsFile.set(resultsFile)

        task.run()

        assert(resultsFile.readText().isEmpty())
    }

    @Test
    fun testReportAnalyticsData() {
        val diffDir = tempDirRule.newFolder("diffs")
        val resultsFile = tempDirRule.newFile("results")
        val referenceImageDir = tempDirRule.newFolder("references")
        val renderOutputDir = tempDirRule.newFolder("rendered")
        val previewsFile = tempDirRule.newFile("previews_discovered.json")

        // Copy the same image to rendered output and reference images
        val previewImageName = "com.example.project.ExampleInstrumentedTest.GreetingPreview_3d8b4969_da39a3ee"
        previewsFile.writeText("""
            {
              "screenshots": [
                {
                  "methodFQN": "com.example.project.ExampleInstrumentedTest.GreetingPreview",
                  "methodParams": [],
                  "previewParams": {
                    "showBackground": "true"
                  },
                  "imageName": "$previewImageName"
                }
              ]
            }
        """.trimIndent())
        javaClass.getResourceAsStream("circle.png")!!
            .copyTo(referenceImageDir.resolve("$previewImageName.png").canonicalFile.apply { parentFile!!.mkdirs() }.outputStream())
        javaClass.getResourceAsStream("circle.png")!!
            .copyTo(renderOutputDir.resolve("${previewImageName}_0.png").canonicalFile.apply { parentFile!!.mkdirs() }.outputStream())

        task.previewFile.set(previewsFile)
        task.referenceImageDir.set(referenceImageDir)
        task.diffImageDir.set(diffDir)
        task.renderTaskOutputDir.set(renderOutputDir)
        task.resultsFile.set(resultsFile)

        val analyticsService = mock<AnalyticsService>()
        task.analyticsService.set(analyticsService)

        task.run()

        verify(analyticsService).recordPreviewScreenshotTestRun(eq(1), any())
    }
}
