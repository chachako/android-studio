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
              ]
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
        )

        assertEquals(expectedComposeRendering, composeRendering)
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
        )

        val stringWriter = StringWriter()

        writeComposeRenderingToJson(composeRendering, stringWriter)

        val restoredComposeRendering = readComposeRenderingJson(stringWriter.toString().reader())

        assertEquals(composeRendering, restoredComposeRendering)
    }
}
