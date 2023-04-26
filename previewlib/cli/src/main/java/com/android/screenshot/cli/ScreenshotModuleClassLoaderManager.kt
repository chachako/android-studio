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

import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.rendering.classloading.loaders.ProjectSystemClassLoader
import com.android.tools.rendering.ModuleRenderContext
import com.android.tools.rendering.classloading.ClassTransform
import com.android.tools.rendering.classloading.ModuleClassLoader
import com.android.tools.rendering.classloading.ModuleClassLoaderManager
import com.android.tools.rendering.classloading.NopModuleClassLoadedDiagnostics
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import org.jetbrains.android.uipreview.ClassBinaryCacheManager.Companion.getInstance
import java.lang.ref.WeakReference
import java.nio.file.Path
import java.util.function.Supplier

class ScreenshotModuleClassLoaderManager(private val dependencies: Dependencies) :
    ModuleClassLoaderManager {

    // This is a hack, the resource system needs to know where the R classes are located, so that
    // it can load the resource ids properly, using this class loader seemed to be the right
    // thing to do, however its unclear if there is a better way to create this once and pass it
    // around.
    var lastClassLoader: ScreenshotModuleClassLoader? = null

    override fun getShared(
        parent: ClassLoader?,
        moduleRenderContext: ModuleRenderContext,
        holder: Any,
        additionalProjectTransformation: ClassTransform,
        additionalNonProjectTransformation: ClassTransform,
        onNewModuleClassLoader: Runnable
    ): ModuleClassLoader {
        val diagnostics = NopModuleClassLoadedDiagnostics
        val screenshotModuleClassLoaderImpl = ScreenshotModuleClassLoaderImpl(
            createDefaultProjectSystemClassLoader(moduleRenderContext.module, moduleRenderContext.fileProvider),
            parent,
            additionalProjectTransformation,
            additionalNonProjectTransformation,
            getInstance().getCache(moduleRenderContext.module),
            diagnostics,
            dependencies)
        lastClassLoader = ScreenshotModuleClassLoader(parent, screenshotModuleClassLoaderImpl, diagnostics)
        return lastClassLoader!!
    }

    override fun getShared(
        parent: ClassLoader?,
        moduleRenderContext: ModuleRenderContext,
        holder: Any
    ): ModuleClassLoader =
        getShared(parent, moduleRenderContext, holder, ClassTransform.identity, ClassTransform.identity) { }

    override fun getPrivate(
        parent: ClassLoader?,
        moduleRenderContext: ModuleRenderContext,
        holder: Any,
        additionalProjectTransformation: ClassTransform,
        additionalNonProjectTransformation: ClassTransform
    ): ModuleClassLoader {
        val diagnostics = NopModuleClassLoadedDiagnostics
        val screenshotModuleClassLoaderImpl = ScreenshotModuleClassLoaderImpl(
            createDefaultProjectSystemClassLoader(moduleRenderContext.module, moduleRenderContext.fileProvider),
            parent,
            additionalProjectTransformation,
            additionalNonProjectTransformation,
            getInstance().getCache(moduleRenderContext.module),
            diagnostics,
            dependencies)
        lastClassLoader = (ScreenshotModuleClassLoader(parent, screenshotModuleClassLoaderImpl, diagnostics))
        return lastClassLoader!!
    }

    override fun getPrivate(
        parent: ClassLoader?,
        moduleRenderContext: ModuleRenderContext,
        holder: Any
    ): ModuleClassLoader = getPrivate(parent, moduleRenderContext, holder, ClassTransform.identity, ClassTransform.identity)

    override fun release(moduleClassLoader: ModuleClassLoader, holder: Any) {
    }

    override fun clearCache(module: Module) {
    }

    private fun createDefaultProjectSystemClassLoader(
        theModule: Module, psiFileProvider: Supplier<PsiFile?>
    ): ProjectSystemClassLoader {
        val moduleRef = WeakReference(theModule)
        return ProjectSystemClassLoader label@{ fqcn: String? ->
            val module = moduleRef.get()
            if (module == null || module.isDisposed) return@label null
            val virtualFile = psiFileProvider.get()?.virtualFile
            module.getModuleSystem()
                .getClassFileFinderForSourceFile(virtualFile)
                .findClassFile(fqcn!!)
        }
    }
}
