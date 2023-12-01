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
 * Result of a screenshot creation. It could be either a path to the successful screenshot image or
 * error or both. The last case happens when we managed to render an image, but we found a number of
 * problems during its rendering that might have affected the fidelity of the result.
 * [resultId] is a string that is designed to uniquely identify the screenshot, currently an image
 * name without extension is used.
 */
data class ComposeScreenshotResult(
    val resultId: String,
    val imagePath: String?,
    val error: ScreenshotError?,
)
