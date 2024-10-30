/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.rendering.RenderLogger
import com.android.tools.rendering.RenderService
import com.android.tools.rendering.RenderTask
import com.android.tools.sdk.AndroidTargetData
import com.android.tools.sdk.EmbeddedRenderTarget
import org.junit.rules.ExternalResource
import java.util.concurrent.TimeUnit

/**
 * Rule to use for tests doing rendering.
 * It ensures the state of the RenderService is cleaned between tests.
 */
class RenderTestRule : ExternalResource() {
  override fun before() {
    super.before()
    RenderService.shutdownRenderExecutor(5)
    EmbeddedRenderTarget.resetRenderTarget()
    RenderService.initializeRenderExecutor()
    AndroidTargetData.clearCache()

  }

  override fun after() {
    RenderLogger.resetFidelityErrorsFilters()
    RenderTask.getDisposeService().submit {}.get(30, TimeUnit.SECONDS)
    RenderService.shutdownRenderExecutor(30)
    super.after()
  }
}
