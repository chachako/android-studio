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

package com.android.tools.render.framework

import com.android.tools.rendering.RenderService
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginSetBuilder
import com.intellij.mock.MockApplication
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil

/** [IJFramework] implementation for the standalone rendering. */
object IJFramework : Disposable {
    private val application = MockApplication(this)

    init {
        ApplicationManager.setApplication(application, this)
        PluginManagerCore.setPluginSet(PluginSetBuilder(emptySet())
            .createPluginSetWithEnabledModulesMap())
    }

    fun createProject(): Project {
        return MockProject(null, application)
    }

    fun <T : Any> registerService(
        serviceInterface: Class<T>, serviceImplementation: T, disposable: Disposable) {
        application.registerService(serviceInterface, serviceImplementation, disposable)
    }

    override fun dispose() {
        RenderService.shutdownRenderExecutor()
        AppExecutorUtil.shutdownApplicationScheduledExecutorService()
    }
}
