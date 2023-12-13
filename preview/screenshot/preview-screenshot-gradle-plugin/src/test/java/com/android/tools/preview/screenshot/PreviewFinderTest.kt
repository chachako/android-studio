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

package com.android.tools.preview.screenshot

import com.google.common.truth.Truth
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreviewFinderTest {
    @get:Rule
    val tempDirRule = TemporaryFolder()

    @Test
    fun testFindPreviewsAndSerializeWithEmptyClassPath() {
        val outputFile = tempDirRule.newFile("outputFile")
        findPreviewsAndSerialize(listOf(), outputFile.toPath())

        Truth.assertThat(outputFile.readText()).isEqualTo("""
            {
              "screenshots": []
            }
        """.trimIndent())
    }

    // TODO(b/315048068): Add a unit test that finds previews on the classpath

    @Test
    fun testConfigureInput() {
        val classpath = listOf("path/to/classes.jar","path/to/R.jar")
        val previewsFile = tempDirRule.newFile("previews_discovered.json")
        val cliToolInputFile = tempDirRule.newFile("cli_tools_input.json")
        previewsFile.writeText("""
            {
              "screenshots": [
                {
                  "methodFQN": "com.example.agptest.ExampleInstrumentedTest.previewThere",
                  "methodParams": [],
                  "previewParams": {
                    "showBackground": "true"
                  },
                  "imageName": "com.example.agptest.ExampleInstrumentedTest.previewThere_3d8b4969_da39a3ee"
                }
              ]
            }
        """.trimIndent())
        configureInput(classpath,
            "sdkpath",
            "layoutlibpath",
            "outputFolder",
            "packageName",
            "resourceApkPath",
            cliToolInputFile,
            previewsFile)
        assertEquals(cliToolInputFile.readText(), """
            {
              "sdkPath": "sdkpath",
              "layoutlibPath": "layoutlibpath",
              "outputFolder": "outputFolder",
              "classPath": [
                "path/to/classes.jar",
                "path/to/R.jar"
              ],
              "packageName": "packageName",
              "resourceApkPath": "resourceApkPath",
              "screenshots": [
                {
                  "methodFQN": "com.example.agptest.ExampleInstrumentedTest.previewThere",
                  "methodParams": [],
                  "previewParams": {
                    "showBackground": "true"
                  },
                  "imageName": "com.example.agptest.ExampleInstrumentedTest.previewThere_3d8b4969_da39a3ee"
                }
              ],
              "resultsFileName": "results.json"
            }
        """.trimIndent())
    }
}
