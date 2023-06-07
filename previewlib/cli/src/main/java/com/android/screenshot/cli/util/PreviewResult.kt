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
package com.android.screenshot.cli.util

import com.android.screenshot.cli.util.CODE_ERROR
import com.android.screenshot.cli.util.CODE_FAILURE
import com.android.screenshot.cli.util.CODE_SUCCESS
import com.android.screenshot.cli.diff.Verify

data class PreviewResult(
    val responseCode: Int,
    val message: String,
) {

    override fun toString(): String {
        return "<previewResult><code>$responseCode</code><message>$message</message></previewResult>"
    }

}

fun Verify.AnalysisResult.toPreviewResponse(): PreviewResult{
    when (this) {
        is Verify.AnalysisResult.Failed -> {
            return PreviewResult(CODE_FAILURE, message)
        }

        is Verify.AnalysisResult.Passed -> {
            return PreviewResult(CODE_SUCCESS, message)
        }

        is Verify.AnalysisResult.SizeMismatch -> {
            return PreviewResult(CODE_FAILURE, message)
        }

        is Verify.AnalysisResult.MissingGolden -> {
            return PreviewResult(CODE_FAILURE, message)
        }

        else -> {
            return PreviewResult(CODE_ERROR, message)
        }
    }
}

data class Response(val status: Int, val message: String, val previewResults: List<PreviewResult>?) {

    override fun toString(): String {
        val resultsXml = if (previewResults == null) "" else "<previewResults>${previewResults!!.toXmlString()}</previewResults>"
        return "<response><status>$status</status><message>$message</message>$resultsXml</response>"
    }
}
fun List<PreviewResult>.toXmlString(): String {
    var xmlString = ""
    this.forEach { xmlString += it.toString()}
    return xmlString
}

