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

/**
 * Result of a screenshot creation. It will contain
 * [previewId] is a string that is designed to uniquely identify the preview, currently an image
 * name without the suffix for method params is used.
 * [methodFQN] fully qualified name of the test method that this result is generated from,
 * [imageName] name of the screenshot image - if successful, image will be created in the output folder with this name, else it is used to identify the exact screenshot that failed
 * and [error] contains info about issues encountered during rendering. It is possible that we managed to render an image, but we found a number of
 * problems during its rendering that might have affected the fidelity of the result; so image and error would both exist
 */
data class ComposeScreenshotResult(
    val previewId: String,
    val methodFQN: String,
    val imageName: String,
    val error: ScreenshotError?,
)
