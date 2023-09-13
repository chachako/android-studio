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

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import com.google.common.truth.Truth.assertThat
import org.gradle.api.GradleException
import org.junit.Assert.assertThrows

@RunWith(JUnit4::class)
class ResponseTypeAdapterTest {

    @Test
    fun testDeserializeResponse() {
        val input =
           """
            {
                "status": 1,
                "message": "some message",
                "previewResults": [
                {
                    "responseCode": 1,
                    "previewName": "previewName",
                    "message": "some message"
                } ]
            }
          """.trimIndent()
        val response = ResponseTypeAdapter().fromJson(input)

        assertThat(response.status).isEqualTo(1)
        assertThat(response.message).isEqualTo("some message")
        assertThat(response.previewResults).isEqualTo(listOf(PreviewResult(1, "previewName", "some message")))
    }

    @Test
    fun testDeserializeMalformedJson() {
        val input = """
            {
                "status": 1,
                "previewResults": [
                {
                    "responseCode": 1,
                    "previewName": "previewName",
                    "message": "some message"
                } ]
            }
          """.trimIndent()
        val e = assertThrows(GradleException::class.java) {
            ResponseTypeAdapter().fromJson(input)
        }
        assertThat(e.message).contains("Could not read Response.")
    }

    @Test
    fun testRoundTrip() {
        val expectedPreviewList = listOf(PreviewResult(1, "name", "message"), PreviewResult(2, "name2", "message2"))
        val expectedResponse = Response(1, "message", expectedPreviewList)

        val jsonString = ResponseTypeAdapter().toJson(expectedResponse)
        val actualResponse = ResponseTypeAdapter().fromJson(jsonString)

        assertThat(actualResponse).isEqualTo(expectedResponse)
    }
}
