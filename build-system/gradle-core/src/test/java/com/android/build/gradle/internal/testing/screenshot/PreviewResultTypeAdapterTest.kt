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

package com.android.build.gradle.internal.testing.screenshot

import com.google.gson.GsonBuilder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import com.google.common.truth.Truth.assertThat
import org.gradle.api.GradleException
import org.junit.Assert.assertThrows

@RunWith(JUnit4::class)
class PreviewResultTypeAdapterTest {
    private val gson = GsonBuilder().registerTypeAdapter(PreviewResult::class.java, PreviewResultTypeAdapter()).create()

    @Test
    fun testDeserializePreviewResult() {
        val input =
          """
            {
                "responseCode": 1,
                "previewName": "previewName",
                "message": "some message"
            }
          """.trimIndent()
        val previewResult = gson.fromJson(input, PreviewResult::class.java)

        assertThat(previewResult.responseCode).isEqualTo(1)
        assertThat(previewResult.previewName).isEqualTo("previewName")
        assertThat(previewResult.message).isEqualTo("some message")
    }

    @Test
    fun testDeserializeMalformedJson() {
        val input = """
            {
                "previewName": "previewName",
                "message": "some message"
            }
          """.trimIndent()

        val e = assertThrows(GradleException::class.java) {
            gson.fromJson(input, PreviewResult::class.java)
        }
        assertThat(e.message).contains("Could not read PreviewResult.")
    }

    @Test
    fun testRoundTrip() {
        val expected = PreviewResult(1, "name", "message")

        val jsonString = gson.toJson(expected)
        val actual = gson.fromJson(jsonString, PreviewResult::class.java)

        assertThat(actual).isEqualTo(expected)
    }
}
