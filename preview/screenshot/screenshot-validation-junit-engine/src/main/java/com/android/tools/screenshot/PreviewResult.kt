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

package com.android.tools.screenshot

import com.android.tools.render.compose.ImagePathOrMessage
import java.nio.file.Path

/*
* Result of a rendered preview that indicates if the preview was rendered successfully
*/
data class PreviewResult(
    val responseCode: Int,
    val previewName: String,
    val durationInSeconds: Float,
    val message: String? = null,
    val referenceImage: ImagePathOrMessage,
    val actualImage: ImagePathOrMessage,
    val diffImage: ImagePathOrMessage

) {
}

fun Verify.AnalysisResult.toPreviewResponse(code: Int, name: String, durationInSeconds: Float, reference: ImagePathOrMessage,
                                            actual: ImagePathOrMessage,
                                            diff: ImagePathOrMessage): PreviewResult {
    return PreviewResult(code, name, durationInSeconds, message, reference, actual, diff)
}
