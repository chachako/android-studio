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

import com.android.tools.rendering.ModuleRenderContext
import com.android.tools.rendering.classloading.loaders.CachingClassLoaderLoader
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import java.util.function.Supplier

/** [ModuleRenderContext] for standalone rendering, where classes do not change. */
class StandaloneRenderContext : ModuleRenderContext {
    override val fileProvider: Supplier<PsiFile?> = Supplier { null }
    override val isDisposed: Boolean = false
    override val module: Module
        get() = throw UnsupportedOperationException("Should not be called in standalone rendering")

    /** Since classes do not change this returns an empty [CachingClassLoaderLoader]. */
    override fun createInjectableClassLoaderLoader(): CachingClassLoaderLoader {
        return object : CachingClassLoaderLoader {
            override fun loadClass(fqcn: String): ByteArray? = null
        }
    }
}
