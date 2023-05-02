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

import com.android.tools.idea.rendering.classloading.loaders.AsmTransformingLoader
import com.android.tools.idea.rendering.classloading.loaders.ClassBinaryCacheLoader
import com.android.tools.idea.rendering.classloading.loaders.ClassLoaderLoader
import com.android.tools.idea.rendering.classloading.loaders.FakeSavedStateRegistryLoader
import com.android.tools.idea.rendering.classloading.loaders.ListeningLoader
import com.android.tools.idea.rendering.classloading.loaders.MultiLoader
import com.android.tools.idea.rendering.classloading.loaders.NameRemapperLoader
import com.android.tools.idea.rendering.classloading.loaders.ProjectSystemClassLoader
import com.android.tools.idea.rendering.classloading.loaders.RecyclerViewAdapterLoader
import com.android.tools.rendering.classloading.ClassTransform
import com.android.tools.rendering.classloading.ModuleClassLoaderDiagnosticsWrite
import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.URLUtil
import org.jetbrains.android.sdk.StudioEmbeddedRenderTarget
import org.jetbrains.android.uipreview.ClassBinaryCache
import org.jetbrains.android.uipreview.PseudoClassLocatorForLoader
import org.jetbrains.android.uipreview.createUrlClassLoader
import org.objectweb.asm.ClassWriter
import java.io.File
import java.net.URL
import java.nio.file.Path
import java.util.Collections
import java.util.Enumeration
import java.util.concurrent.ConcurrentHashMap

class ScreenshotModuleClassLoaderImpl (
    private val projectSystemLoader: ProjectSystemClassLoader,
    private val parentClassLoader: ClassLoader?,
    val projectTransforms: ClassTransform,
    nonProjectTransforms: ClassTransform,
    binaryCache: ClassBinaryCache,
    private val diagnostics: ModuleClassLoaderDiagnosticsWrite,
    additionalExternalLibraries : Dependencies)
    : UserDataHolderBase(), DelegatingClassLoader.Loader, Disposable {
    private val loader: DelegatingClassLoader.Loader

    private val parentLoader = parentClassLoader?.let { ClassLoaderLoader(it) }
    private val externalLibraries = additionalLibraries + additionalExternalLibraries.classLoaderDependencies
    private val onClassRewrite = { fqcn: String, timeMs: Long, size: Int -> diagnostics.classRewritten(fqcn, size, timeMs) }
    private val _projectLoadedClassNames: MutableSet<String> = Collections.newSetFromMap(
        ConcurrentHashMap()
    )
    private val _nonProjectLoadedClassNames: MutableSet<String> = Collections.newSetFromMap(
        ConcurrentHashMap()
    )
    private val additionalLibraries: List<Path>
        get() {
            val layoutlibDistributionPath = StudioEmbeddedRenderTarget.getEmbeddedLayoutLibPath()
                ?: return emptyList() // Error is already logged by getEmbeddedLayoutLibPath
            val relativeCoroutineLibPath = FileUtil.toSystemIndependentName("data/layoutlib-extensions.jar")
            return arrayListOf(File(layoutlibDistributionPath, relativeCoroutineLibPath).toPath())
        }


    private val externalLibrariesClassLoader = createUrlClassLoader(externalLibraries)

    private fun createProjectLoader(loader: DelegatingClassLoader.Loader,
        dependenciesLoader: DelegatingClassLoader.Loader?,
        onClassRewrite: (String, Long, Int) -> Unit) = AsmTransformingLoader(
        projectTransforms,
        ListeningLoader(loader, onAfterLoad = { fqcn, _ ->
            _projectLoadedClassNames.add(fqcn) }),
        PseudoClassLocatorForLoader(
            listOfNotNull(projectSystemLoader, dependenciesLoader, parentLoader).asSequence(),
            parentClassLoader),
        ClassWriter.COMPUTE_FRAMES,
        onClassRewrite
    )

    private fun createNonProjectLoader(nonProjectTransforms: ClassTransform,
        binaryCache: ClassBinaryCache,
        externalLibraries: List<Path>,
        onClassLoaded: (String) -> Unit,
        onClassRewrite: (String, Long, Int) -> Unit): DelegatingClassLoader.Loader {
        val externalLibrariesClassLoader = createUrlClassLoader(externalLibraries)
        // Non project classes loading pipeline
        val nonProjectTransformationId = nonProjectTransforms.id
        // map of fqcn -> library path used to be able to insert classes into the ClassBinaryCache
        val fqcnToLibraryPath = mutableMapOf<String, String>()
        val jarLoader = NameRemapperLoader(
            ClassLoaderLoader(externalLibrariesClassLoader) { fqcn, path, _ ->
                URLUtil.splitJarUrl(path)?.first?.let { libraryPath -> fqcnToLibraryPath[fqcn] = libraryPath }
            }
        ) { name: String -> name }
        // Loads a fake saved state registry, when [ViewTreeLifecycleOwner] requests a mocked lifecycle.
        // See also ViewTreeLifecycleTransform to check when this fake class gets created.
        val fakeSavedStateRegistryLoader = FakeSavedStateRegistryLoader(jarLoader)

        // Tree of the class Loaders:
        // Each node of this tree checks if it can load the current class, it delegates to its subtree otherwise.
        return ListeningLoader(
            delegate = ClassBinaryCacheLoader(
                delegate = ListeningLoader(
                    delegate = AsmTransformingLoader(
                        transform = nonProjectTransforms,
                        delegate = fakeSavedStateRegistryLoader,
                        pseudoClassLocator = PseudoClassLocatorForLoader(
                            loaders = listOfNotNull(jarLoader, parentLoader).asSequence(),
                            fallbackClassloader = parentClassLoader
                        ),
                        asmFlags = ClassWriter.COMPUTE_MAXS,
                        onRewrite = onClassRewrite),
                    onAfterLoad = { fqcn, bytes ->
                        onClassLoaded(fqcn)
                        // Map the fqcn to the library path and insert the class into the class binary cache
                        fqcnToLibraryPath[fqcn]?.let { libraryPath ->
                            binaryCache.put(fqcn, nonProjectTransformationId, libraryPath, bytes)
                        }
                    }),
                transformationId = nonProjectTransformationId,
                binaryCache = binaryCache),
            onBeforeLoad = {
                if (it == "kotlinx.coroutines.android.AndroidDispatcherFactory") {
                    // Hide this class to avoid the coroutines in the project loading the AndroidDispatcherFactory for now.
                    // b/162056408
                    //
                    // Throwing an exception here (other than ClassNotFoundException) will force the FastServiceLoader to fallback
                    // to the regular class loading. This allows us to inject our own DispatcherFactory, specific to Layoutlib.
                    throw IllegalArgumentException("AndroidDispatcherFactory not supported by layoutlib")
                }
            }
        )
    }

    init {
        val nonProjectLoader = createNonProjectLoader(nonProjectTransforms,
                                                      binaryCache,
                                                      externalLibraries,
                                                      { _nonProjectLoadedClassNames.add(it) },
                                                      onClassRewrite)
        // Project classes loading pipeline
        val projectLoader = createProjectLoader(projectSystemLoader, nonProjectLoader, onClassRewrite)
        val allLoaders = listOfNotNull(
            projectLoader,
            nonProjectLoader,
            RecyclerViewAdapterLoader()
        )
        loader = MultiLoader(allLoaders)
    }

    override fun loadClass(fqcn: String): ByteArray? = loader.loadClass(fqcn)

    override fun dispose() {

    }

    fun getResources(name: String?): Enumeration<URL> = externalLibrariesClassLoader.getResources(name)
    fun getResource(name: String?): URL? = externalLibrariesClassLoader.getResource(name)
}
