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

package com.android.tools.render

import com.android.tools.configurations.Configuration

/**
 * Data required to perform rendering by the standalone rendering library:
 * @param xmlLayout - xml layout to render.
 * @param configurationModifier - a way to apply custom [Configuration] for rendering.
 * @param outputImageName - a name (without extension) of the output image file.
 */
data class RenderRequest(
    val xmlLayout: String,
    val configurationModifier: Configuration.() -> Unit,
    val outputImageName: String,
)
