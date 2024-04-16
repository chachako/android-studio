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

import com.android.annotations.concurrency.GuardedBy
import com.android.layoutlib.LayoutlibClassLoader
import com.android.tools.rendering.ModuleRenderContext
import com.android.tools.rendering.classloading.ClassTransform
import com.android.tools.rendering.classloading.ClassesTracker
import com.android.tools.rendering.classloading.CodeExecutionTrackerTransform
import com.android.tools.rendering.classloading.ModuleClassLoader
import com.android.tools.rendering.classloading.ModuleClassLoaderDiagnosticsRead
import com.android.tools.rendering.classloading.ModuleClassLoaderManager
import com.android.tools.rendering.classloading.Preloader
import com.android.tools.rendering.classloading.PseudoClassLocatorForLoader
import com.android.tools.rendering.classloading.loaders.AsmTransformingLoader
import com.android.tools.rendering.classloading.loaders.ClassLoaderLoader
import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader
import com.android.tools.rendering.classloading.loaders.MultiLoader
import com.google.common.cache.CacheBuilder
import com.intellij.openapi.module.Module
import com.intellij.util.lang.UrlClassLoader
import com.android.tools.rendering.classloading.toClassTransform
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.Path

/**
 * [ModuleClassLoaderManager] for the [ModuleClassLoader]s used in the standalone rendering.
 * Currently, it is a thin wrapper around [UrlClassLoader].
 * TODO(): Apply the bytecode processing similar to the one used in Studio.
 */
internal class StandaloneModuleClassLoaderManager(
    private val classPath: List<String>,
    private val projectClassPath: List<String>,
) : ModuleClassLoaderManager<ModuleClassLoader> {

    private val preloader = Preloader(
        createClassLoader(LayoutlibClassLoader(this::class.java.classLoader)),
        Executors.newSingleThreadExecutor(),
        COMPOSE_CLASSES
    )

    /**
     * A loader responsible for loading all the classes in out-of-studio version of
     * [ModuleClassLoader].
     * TODO: Merge this with ModuleClassLoaderImpl
     */
    private class DefaultLoader(
        parent: ClassLoader?,
        classPath: List<String>,
        projectClassPath: List<String>,
    ) : DelegatingClassLoader.Loader {
        val classesToPaths = mutableMapOf<String, String>()
        private val classTransforms = toClassTransform()
        private val projectClassTransforms = toClassTransform(
            { CodeExecutionTrackerTransform(it, CLASSES_TRACKER_KEY) }
        )

        private val loader: DelegatingClassLoader.Loader
        init {
            val projectClassPathSet = projectClassPath.toSet()
            val depsClassPath = classPath.filter { !projectClassPathSet.contains(it) }
            val parentLoader = parent?.let { ClassLoaderLoader(it) }
            val depsClassLoader = UrlClassLoader.build()
                .useCache(false)
                .parent(parent)
                .files(depsClassPath.map { Path(it) })
                .get()
            val depsClassLoaderLoader = ClassLoaderLoader(depsClassLoader)
            val depsLoader = AsmTransformingLoader(
                classTransforms,
                depsClassLoaderLoader,
                PseudoClassLocatorForLoader(
                    listOfNotNull(depsClassLoaderLoader, parentLoader).asSequence(),
                    parent
                )
            )
            val projectClassLoader = UrlClassLoader.build()
                .useCache(false)
                .parent(parent)
                .files(projectClassPath.map { Path(it) })
                .get()
            val projectClassLoaderLoader = ClassLoaderLoader(projectClassLoader) { fqcn, path, _ ->
                classesToPaths[fqcn] = path
            }
            val projectLoader = AsmTransformingLoader(
                projectClassTransforms,
                projectClassLoaderLoader,
                PseudoClassLocatorForLoader(
                    listOfNotNull(projectClassLoaderLoader, depsLoader, parentLoader).asSequence(),
                    parent
                )
            )
            loader = MultiLoader(projectLoader, depsLoader)
        }

        override fun loadClass(fqcn: String): ByteArray? = loader.loadClass(fqcn)
    }

    class DefaultModuleClassLoader private constructor(
        parent: ClassLoader?,
        private val loader: DefaultLoader
    ) : ModuleClassLoader(parent, loader) {
        constructor(parent: ClassLoader?, classPath: List<String>, projectClassPath: List<String>) :
                this(parent, DefaultLoader(parent, classPath, projectClassPath))

        private val loadedClasses = mutableSetOf<String>()
        override val stats: ModuleClassLoaderDiagnosticsRead =
            object : ModuleClassLoaderDiagnosticsRead {
                override val classesFound: Long = 0
                override val accumulatedFindTimeMs: Long = 0
                override val accumulatedRewriteTimeMs: Long = 0
            }
        override val isUserCodeUpToDate: Boolean = true
        override fun hasLoadedClass(fqcn: String): Boolean =
            loadedClasses.contains(fqcn)

        override val projectLoadedClasses: Set<String> = emptySet()
        override val nonProjectLoadedClasses: Set<String>
            get() = loadedClasses
        override val projectClassesTransform: ClassTransform = ClassTransform.identity
        override val nonProjectClassesTransform: ClassTransform = ClassTransform.identity

        override fun dispose() { }

        override val isDisposed: Boolean = false
        override fun isCompatibleParentClassLoader(parent: ClassLoader?): Boolean = true

        override fun areDependenciesUpToDate(): Boolean = true

        override fun onAfterLoadClass(fqcn: String, loaded: Boolean, durationMs: Long) {
            if (loaded) {
                loadedClasses.add(fqcn)
            }
        }
        val classesToPaths: Map<String, String>
            get() = loader.classesToPaths
    }

    private fun createClassLoader(parent: ClassLoader?): DefaultModuleClassLoader {
        return DefaultModuleClassLoader(parent, classPath, projectClassPath)
    }

    private val sharedClassLoadersLock = ReentrantLock()
    @GuardedBy("sharedClassLoadersLock")
    private val sharedClassLoaders =
        CacheBuilder
            .newBuilder()
            .maximumSize(10)
            .build<ClassLoader?, DefaultModuleClassLoader>()

    override fun getShared(
        parent: ClassLoader?,
        moduleRenderContext: ModuleRenderContext,
        additionalProjectTransformation: ClassTransform,
        additionalNonProjectTransformation: ClassTransform,
        onNewModuleClassLoader: Runnable
    ): ModuleClassLoaderManager.Reference<ModuleClassLoader> {
        val classLoader = sharedClassLoadersLock.withLock {
            sharedClassLoaders.get(parent) { preloader.getClassLoader() }
        }
        return ModuleClassLoaderManager.Reference(this, classLoader)
    }

    override fun getPrivate(
        parent: ClassLoader?,
        moduleRenderContext: ModuleRenderContext,
        additionalProjectTransformation: ClassTransform,
        additionalNonProjectTransformation: ClassTransform
    ): ModuleClassLoaderManager.Reference<ModuleClassLoader> {
        return ModuleClassLoaderManager.Reference(this, createClassLoader(parent))
    }

    override fun release(moduleClassLoaderReference: ModuleClassLoaderManager.Reference<*>) { }

    override fun clearCache(module: Module) { }

    companion object {
        /**
         * A key used for identifying classes loaded by the [DefaultModuleClassLoader] in
         * [ClassesTracker], both for writing with [ClassesTracker.trackClass] and for retrieving
         * with [ClassesTracker.getClasses].
         */
        const val CLASSES_TRACKER_KEY = ""
    }
}
