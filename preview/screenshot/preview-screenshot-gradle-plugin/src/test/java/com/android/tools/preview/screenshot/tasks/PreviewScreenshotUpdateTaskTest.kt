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

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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
        val previewsFile = tempDirRule.newFile("previews_discovered.json")
        previewsFile.writeText("""
            {
              "screenshots": [{
                  "methodFQN": "com.example.agptest.ExampleInstrumentedTest.previewThere",
                  "methodParams": [],
                  "previewParams": {
                    "showBackground": "true"
                  },
                  "imageName": "com.example.agptest.ExampleInstrumentedTest.previewThere_3d8b4969_da39a3ee"
                }]
            }
        """.trimIndent())
        val referenceImageDir = tempDirRule.newFolder("references")
        val renderTaskOutputDir = tempDirRule.newFolder("rendered")
        Files.createFile(Paths.get(renderTaskOutputDir.absolutePath).resolve("com.example.agptest.ExampleInstrumentedTest.previewThere_3d8b4969_da39a3ee_0.png"))
        task.referenceImageDir.set(referenceImageDir)
        task.previewFile.set(previewsFile)
        task.renderTaskOutputDir.set(renderTaskOutputDir)

        task.run()
        assert(referenceImageDir.listFiles().isNotEmpty())
    }
}
