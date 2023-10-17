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

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginSetBuilder
import com.intellij.mock.MockApplication
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.Closeable
import java.util.concurrent.TimeUnit

/**
 * IJ Framework wrapper, providing root [Disposable], [Project] and a way to register services and
 * extension points.
 * TODO(): Move away from this and use other IJ-independent solution.
 */
internal interface IJFramework : Closeable {
    val disposable: Disposable

    val project: Project

    fun <T : Any> registerService(serviceInterface: Class<T>, serviceImplementation: T)

    fun <T : Any> registerExtensionPoint(epName: ExtensionPointName<T>, className: Class<T>, implClass: T)
}

/** [IJFramework] implementation for the standalone rendering. */
internal class StandaloneFramework(stopExecutor: Boolean) : IJFramework {
    override val disposable = Disposer.newDisposable()
    private val app = MockApplication(disposable)

    init {
        ApplicationManager.setApplication(app, disposable)
        PluginManagerCore.setPluginSet(PluginSetBuilder(emptySet()).createPluginSet())
        if (stopExecutor) {
            Disposer.register(disposable) {
                // Make sure the queue is empty
                AppExecutorUtil.getAppScheduledExecutorService().submit { }.get(60, TimeUnit.SECONDS)
                AppExecutorUtil.shutdownApplicationScheduledExecutorService()
            }
        }
    }

    override val project: Project = MockProject(null, disposable)

    override fun <T : Any> registerService(serviceInterface: Class<T>, serviceImplementation: T) {
        app.registerService(serviceInterface, serviceImplementation)
    }

    override fun <T : Any> registerExtensionPoint(epName: ExtensionPointName<T>, className: Class<T>, implClass: T) {
        Extensions.getRootArea().registerExtensionPoint(epName.name, className.name, ExtensionPoint.Kind.INTERFACE)
        Extensions.getRootArea().getExtensionPoint(epName).registerExtension(implClass, disposable)
    }

    override fun close() {
        Disposer.dispose(disposable)
    }
}
