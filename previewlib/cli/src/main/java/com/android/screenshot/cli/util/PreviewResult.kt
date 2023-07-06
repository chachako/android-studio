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
    val goldenPath: String? = null,
    val actualPath: String? = null,
    val diffPath: String? = null
) {
}

fun Verify.AnalysisResult.toPreviewResponse(golden: String? = null,
    diff: String? = null,
    actual: String? = null): PreviewResult{
    when (this) {
        is Verify.AnalysisResult.Failed -> {
            return PreviewResult(CODE_FAILURE, message, golden, actual, diff)
        }

        is Verify.AnalysisResult.Passed -> {
            return PreviewResult(CODE_SUCCESS, message, golden, actual, diff)
        }

        is Verify.AnalysisResult.SizeMismatch -> {
            return PreviewResult(CODE_FAILURE, message, golden, actual, diff)
        }

        is Verify.AnalysisResult.MissingGolden -> {
            return PreviewResult(CODE_FAILURE, message, golden, actual, diff)
        }

        else -> {
            return PreviewResult(CODE_ERROR, message, golden, actual, diff)
        }
    }
}

data class Response(val status: Int, val message: String, val previewResults: List<PreviewResult>?) {
}

