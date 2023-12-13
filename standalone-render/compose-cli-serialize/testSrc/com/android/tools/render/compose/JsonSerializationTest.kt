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

package com.android.tools.render.compose

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringWriter

class JsonSerializationTest {
    @Test
    fun testJsonToComposeRendering() {
        // language=json
        val jsonString = """
            {
              "sdkPath": "/path/to/sdk",
              "layoutlibPath": "/path/to/layout/lib",
              "outputFolder": "/path/to/output/folder",
              "classPath": [
                "/path/to/lib1.jar",
                "/path/to/lib2.jar",
                "/path/to/classes"
              ],
              "packageName": "com.my.package",
              "resourceApkPath": "/path/to/resource.apk",
              "screenshots": [
                {
                  "methodFQN": "com.my.package.ClKt.Method1",
                  "methodParams": [],
                  "previewParams": {},
                  "imageName": "/path/to/image/pattern/name"
                }
              ],
              "resultsFileName": "my_results.json"
            }
        """.trimIndent()

        val composeRendering = readComposeRenderingJson(jsonString.reader())

        val expectedComposeRendering = ComposeRendering(
            "/path/to/sdk",
            "/path/to/layout/lib",
            "/path/to/output/folder",
            listOf("/path/to/lib1.jar", "/path/to/lib2.jar", "/path/to/classes"),
            "com.my.package",
            "/path/to/resource.apk",
            listOf(
                ComposeScreenshot(
                    "com.my.package.ClKt.Method1",
                    emptyList(),
                    emptyMap(),
                    "/path/to/image/pattern/name",
                )
            ),
            "my_results.json"
        )

        assertEquals(expectedComposeRendering, composeRendering)
    }

    @Test
    fun testJsonToScreenshots() {
        // language=json
        val jsonString = """
            {
              "screenshots": [
                {
                  "methodFQN": "com.my.package.ClKt.Method1",
                  "methodParams": [
                    {
                      "provider": "com.my.package2.SomeParameterProvider"
                    }
                  ],
                  "previewParams": {
                    "name": "Dark theme",
                    "uiMode": "32"
                  },
                  "imageName": "/path/to/image/pattern/name"
                },
                {
                  "methodFQN": "com.my.package.Cl2Kt.Method3",
                  "methodParams": [],
                  "previewParams": {
                    "name": "Light theme"
                  },
                  "imageName": "/path/to/image/pattern/name"
                },
                {
                  "methodFQN": "com.my.package.Cl3Kt.Method5",
                  "methodParams": [
                    {
                      "provider": "com.my.package2.SomeOtherParameterProvider"
                    }
                  ],
                  "previewParams": {},
                  "imageName": "/path/to/image/pattern/name"
                }
              ]
            }
        """.trimIndent()


        val screenshots = readComposeScreenshotsJson(jsonString.reader())

        assertEquals(
            listOf(
                ComposeScreenshot(
                    "com.my.package.ClKt.Method1",
                    listOf(mapOf("provider" to "com.my.package2.SomeParameterProvider")),
                    mapOf("name" to "Dark theme", "uiMode" to "32"),
                    "/path/to/image/pattern/name",
                ),
                ComposeScreenshot(
                    "com.my.package.Cl2Kt.Method3",
                    emptyList(),
                    mapOf("name" to "Light theme"),
                    "/path/to/image/pattern/name",
                ),
                ComposeScreenshot(
                    "com.my.package.Cl3Kt.Method5",
                    listOf(mapOf("provider" to "com.my.package2.SomeOtherParameterProvider")),
                    emptyMap(),
                    "/path/to/image/pattern/name",
                )
            ),
            screenshots
        )

    }

    @Test
    fun testComposeRenderingToJsonAndBack() {
        val screenshot = ComposeScreenshot(
            "com.my.package.ClKt.Method1",
            emptyList(),
            emptyMap(),
            "/path/to/image/pattern/name",
        )
        val composeRendering = ComposeRendering(
            "/path/to/sdk",
            "/path/to/layout/lib",
            "/path/to/output/folder",
            listOf("/path/to/lib1.jar", "/path/to/lib2.jar", "/path/to/classes"),
            "com.my.package",
            "/path/to/resource.apk",
            listOf(screenshot),
            "my_results.json",
        )

        val stringWriter = StringWriter()

        writeComposeRenderingToJson(stringWriter, composeRendering)

        val restoredComposeRendering = readComposeRenderingJson(stringWriter.toString().reader())

        assertEquals(composeRendering, restoredComposeRendering)
    }

    @Test
    fun testScreenshotsToJsonAndBack() {
        val screenshots = listOf(
            ComposeScreenshot(
                "com.my.package.ClKt.Method1",
                listOf(mapOf("provider" to "com.my.package2.SomeParameterProvider")),
                mapOf("name" to "Dark theme", "uiMode" to "32"),
                "/path/to/image/pattern/name",
            ),
            ComposeScreenshot(
                "com.my.package.Cl2Kt.Method3",
                emptyList(),
                mapOf("name" to "Light theme"),
                "/path/to/image/pattern/name",
            ),
            ComposeScreenshot(
                "com.my.package.Cl3Kt.Method5",
                listOf(mapOf("provider" to "com.my.package2.SomeOtherParameterProvider")),
                emptyMap(),
                "/path/to/image/pattern/name",
            )
        )

        val stringWriter = StringWriter()
        writeComposeScreenshotsToJson(stringWriter, screenshots)

        val restoredScreenshots = readComposeScreenshotsJson(stringWriter.toString().reader())

        assertEquals(screenshots, restoredScreenshots)
    }

    @Test
    fun testJsonToComposeRenderingResult_GlobalError() {
        // language=json
        val jsonString = """
            {
              "globalError": "Error message\nStack trace line 1\nStackTrace line 2"
            }
        """.trimIndent()

        val composeRenderingResult = readComposeRenderingResultJson(jsonString.reader())

        val expectedComposeRenderingResult = ComposeRenderingResult(
            """
                Error message
                Stack trace line 1
                StackTrace line 2
            """.trimIndent(),
            emptyList()
        )

        assertEquals(expectedComposeRenderingResult, composeRenderingResult)
    }

    @Test
    fun testJsonToComposeRenderingResult_ScreenshotResults() {
        // language=json
        val jsonString = """
            {
              "screenshotResults": [
                {
                  "resultId": "resultId1",
                  "imagePath": "/path/to/image.png"
                },
                {
                  "resultId": "resultId2",
                  "error": {
                      "status": "ERROR_RENDER_TASK",
                      "message": "Error message",
                      "stackTrace": "Error message\nStack trace line 1\nStackTrace line 2",
                      "problems": [],
                      "brokenClasses": [],
                      "missingClasses": []
                  }
                },
                {
                  "resultId": "resultId3",
                  "error": {
                      "status": "SUCCESS",
                      "message": "",
                      "stackTrace": "",
                      "problems": [
                        {
                          "html": "<html>Some error description</html>"
                        },
                        {
                          "html": "<html>Some other error description</html>",
                          "stackTrace": "Other error message\nStack trace line 1\nStackTrace line 2"
                        }
                      ],
                      "brokenClasses": [
                        {
                          "className": "com.baz.Qwe",
                          "stackTrace": "Error message\nStack trace line 1\nStackTrace line 2"
                        }
                      ],
                      "missingClasses": ["com.foo.Bar"]
                  },
                  "imagePath": "/path/to/image3.png"
                }
              ]
            }
        """.trimIndent()

        val composeRenderingResult = readComposeRenderingResultJson(jsonString.reader())

        val expectedComposeRenderingResult = ComposeRenderingResult(
            null,
            listOf(
                ComposeScreenshotResult("resultId1", "/path/to/image.png", null),
                ComposeScreenshotResult("resultId2", null, ScreenshotError(
                    "ERROR_RENDER_TASK",
                    "Error message",
                    """
                        Error message
                        Stack trace line 1
                        StackTrace line 2
                    """.trimIndent(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                )),
                ComposeScreenshotResult("resultId3", "/path/to/image3.png", ScreenshotError(
                    "SUCCESS", "", "",
                    listOf(
                        RenderProblem("<html>Some error description</html>", null),
                        RenderProblem(
                            "<html>Some other error description</html>",
                            """
                                Other error message
                                Stack trace line 1
                                StackTrace line 2
                            """.trimIndent())
                    ),
                    listOf(BrokenClass(
                        "com.baz.Qwe",
                        """
                            Error message
                            Stack trace line 1
                            StackTrace line 2
                        """.trimIndent(),
                    )),
                    listOf("com.foo.Bar"),
                )),
            )
        )

        assertEquals(expectedComposeRenderingResult, composeRenderingResult)
    }

    @Test
    fun testComposeRenderingResultToJsonAndBack_GlobalError() {
        val composeRenderingResult = ComposeRenderingResult(
            """
                Error message
                Stack trace line 1
                StackTrace line 2
            """.trimIndent(),
            emptyList()
        )

        val stringWriter = StringWriter()
        writeComposeRenderingResult(stringWriter, composeRenderingResult)

        val restoredComposeRenderingResult = readComposeRenderingResultJson(stringWriter.toString().reader())

        assertEquals(composeRenderingResult, restoredComposeRenderingResult)
    }

    @Test
    fun testComposeRenderingResultToJsonAndBack_ScreenshotResults() {
        val composeRenderingResult = ComposeRenderingResult(
            null,
            listOf(
                ComposeScreenshotResult("resultId1", "/path/to/image.png", null),
                ComposeScreenshotResult("resultId2", null, ScreenshotError(
                    "ERROR_RENDER_TASK",
                    "Error message",
                    """
                        Error message
                        Stack trace line 1
                        StackTrace line 2
                    """.trimIndent(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                )),
                ComposeScreenshotResult("resultId3", "/path/to/image3.png", ScreenshotError(
                    "SUCCESS", "", "",
                    listOf(
                        RenderProblem("<html>Some error description</html>", null),
                        RenderProblem(
                            "<html>Some other error description</html>",
                            """
                                Other error message
                                Stack trace line 1
                                StackTrace line 2
                            """.trimIndent())
                    ),
                    listOf(BrokenClass(
                        "com.baz.Qwe",
                        """
                            Error message
                            Stack trace line 1
                            StackTrace line 2
                        """.trimIndent(),
                    )),
                    listOf("com.foo.Bar"),
                )),
            )
        )

        val stringWriter = StringWriter()
        writeComposeRenderingResult(stringWriter, composeRenderingResult)

        val restoredComposeRenderingResult = readComposeRenderingResultJson(stringWriter.toString().reader())


        assertEquals(composeRenderingResult, restoredComposeRenderingResult)
    }
}
