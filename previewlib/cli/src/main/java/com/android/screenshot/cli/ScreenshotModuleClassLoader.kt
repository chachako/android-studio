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
package com.android.screenshot.cli

import com.android.layoutlib.reflection.TrackingThreadLocal.Companion.clearThreadLocals
import com.android.tools.idea.rendering.classloading.findMethodLike
import com.android.tools.rendering.RenderService
import com.android.tools.rendering.classloading.ModuleClassLoader
import com.android.tools.rendering.classloading.ModuleClassLoaderDiagnosticsRead
import com.android.tools.rendering.classloading.ModuleClassLoaderDiagnosticsWrite
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.android.uipreview.StudioModuleClassLoader
import java.net.URL
import java.util.Enumeration
import java.util.concurrent.atomic.AtomicBoolean

class ScreenshotModuleClassLoader(parent: ClassLoader?, loader: ScreenshotModuleClassLoaderImpl,
    val diagnostics: ModuleClassLoaderDiagnosticsWrite
) : ModuleClassLoader(parent, loader)  {

    private val LOG: Logger = Logger.getInstance(ScreenshotModuleClassLoader::class.java)
    private val myImpl = loader

    private val _isDisposed = AtomicBoolean(false)
    override val isDisposed: Boolean
        get() = _isDisposed.get()

    /**
     * Package name used to "re-package" certain classes that would conflict with the ones in the Studio class loader.
     * This applies to all packages defined in [StudioModuleClassLoader.PACKAGES_TO_RENAME].
     */
    val INTERNAL_PACKAGE = "_layoutlib_._internal_."
    private val ourDisposeService =
        AppExecutorUtil.createBoundedApplicationPoolExecutor("ModuleClassLoader Dispose Thread", 1)

    private fun waitForCoroutineThreadToStop() {
        try {
            val defaultExecutorClass =
                findLoadedClass(INTERNAL_PACKAGE + "kotlinx.coroutines.DefaultExecutor")
                    ?: return
            // Kotlin bytecode generation converts isThreadPresent property into isThreadPresent$kotlinx_coroutines_core() method
            val isThreadPresentMethod = defaultExecutorClass.findMethodLike("isThreadPresent")
            if (isThreadPresentMethod == null) {
                LOG.warn("Method to check coroutine thread existence is not found.")
                return
            }
            val instanceField = defaultExecutorClass.getDeclaredField("INSTANCE")
            val defaultExecutorObj = instanceField[null]
            isThreadPresentMethod.isAccessible = true
            // DefaultExecutor thread has DEFAULT_KEEP_ALIVE of 1000ms. We expect it to disappear after at most of 1100ms waiting.
            val ITERATIONS = 11
            for (i in 0..ITERATIONS) {
                if (!(isThreadPresentMethod.invoke(defaultExecutorObj) as Boolean)) {
                    return
                }
                if (i != ITERATIONS) {
                    Thread.sleep(100)
                }
            }
            LOG.warn("DefaultExecutor thread is still running")
        } catch (t: Throwable) {
            LOG.warn(t)
        }
    }
    fun dispose() {
        _isDisposed.set(true)
        ourDisposeService.submit {
            waitForCoroutineThreadToStop()
            val threadLocals: Set<ThreadLocal<*>>? =
                clearThreadLocals(this)
            if (threadLocals == null || threadLocals.isEmpty()) {
                return@submit
            }

            // Because we are clearing-up ThreadLocals, the code must run on the Layoutlib Thread
            RenderService.getRenderAsyncActionExecutor().runAsyncAction {
                for (threadLocal in threadLocals) {
                    try {
                        threadLocal.remove()
                    } catch (e: Exception) {
                        LOG.warn(e) // Failure detected here will most probably cause a memory leak
                    }
                }
            }
        }
    }

    override val stats: ModuleClassLoaderDiagnosticsRead
        get() = diagnostics
    override val isUserCodeUpToDate: Boolean
        get() = true

    override fun getResource(name: String?): URL? {
        return myImpl.getResource(name)
    }
    override fun getResources(name: String?): Enumeration<URL> {
        return myImpl.getResources(name)
    }

    override fun onBeforeLoadClass(fqcn: String) {
        diagnostics.classLoadStart(fqcn)
    }

    override fun onAfterLoadClass(fqcn: String, loaded: Boolean, durationMs: Long) {
        diagnostics.classLoadedEnd(fqcn, durationMs)
    }

    override fun onBeforeFindClass(fqcn: String) {
        diagnostics.classFindStart(fqcn)
    }

    override fun onAfterFindClass(fqcn: String, found: Boolean, durationMs: Long) {
        diagnostics.classFindEnd(
            fqcn, found,
            durationMs
        )
    }
}

