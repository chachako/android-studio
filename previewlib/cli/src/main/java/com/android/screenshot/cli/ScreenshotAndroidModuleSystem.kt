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

import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.resources.AndroidManifestPackageNameUtils
import com.android.ide.common.util.PathString
import com.android.manifmerger.ManifestSystemProperty
import com.android.projectmodel.ExternalAndroidLibrary
import com.android.projectmodel.RecursiveResourceFolder
import com.android.projectmodel.ResourceFolder
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.CapabilityStatus
import com.android.tools.idea.projectsystem.ClassContent
import com.android.tools.idea.projectsystem.ClassFileFinder
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.DependencyType
import com.android.tools.idea.projectsystem.ManifestOverrides
import com.android.tools.idea.projectsystem.MergedManifestContributors
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.util.toVirtualFile
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.map2Array
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.zip.ZipFile

// TODO: Replace this with the dependencies object.
typealias DepsMap = () -> List<String>

/**
 * This class is responsible for finding class files, as well as loading resources from the dependencies.
 */
class ScreenshotAndroidModuleSystem(
    private val deps: DepsMap,
    val composeProject: ComposeProject,
    val composeModule: ComposeModule
) : AndroidModuleSystem {

    override val module: Module
        get() = composeModule.module

    val filesMap = mutableMapOf<String, MutableList<ClassContent>>()
    override val moduleClassFileFinder: ClassFileFinder
        get() = object : ClassFileFinder {
            override fun findClassFile(fqcn: String): ClassContent? {
                if (filesMap.isEmpty()) {
                    initFilesMap()
                }
                return filesMap[fqcn]?.get(0)
            }

            private fun getClassNameFromRelativePath(path: String): String {
                return path.replace("/", ".").substring(0, path.length - ".class".length)
            }

            private fun initFilesMap() {
                try {
                    deps().map { dep ->
                        val depFile = File(dep)
                        if (depFile.isDirectory) {
                            depFile.walkTopDown().filter {
                                it.isFile && it.extension.equals(".class", ignoreCase = true)
                            }.forEach { classFile ->
                                val relativePath = classFile.relativeTo(depFile).toString()
                                val className = getClassNameFromRelativePath(relativePath)
                                filesMap.computeIfAbsent(className) { mutableListOf() }
                                    .add(ClassContent.loadFromFile(classFile))
                            }
                        } else if (depFile.isFile && depFile.extension.equals(
                                ".jar", ignoreCase = true
                            )
                        ) {
                            ZipFile(dep).use { jar ->
                                val entries = jar.entries()
                                while (entries.hasMoreElements()) {
                                    val entry = entries.nextElement()

                                    if (entry.name.endsWith(".class", ignoreCase = true)) {
                                        val className = getClassNameFromRelativePath(entry.name)
                                        val content = jar.getInputStream(entry).readAllBytes()
                                        filesMap.computeIfAbsent(className) { mutableListOf() }
                                            .add(ClassContent.fromJarEntryContent(depFile, content))
                                    }
                                }
                            }
                        }
                    }
                } catch (ex: Throwable) {
                    ex.printStackTrace()
                }
            }
        }

    override fun getModuleTemplates(targetDirectory: VirtualFile?): List<NamedModuleTemplate> {
        TODO("Not yet implemented")
    }

    override fun analyzeDependencyCompatibility(dependenciesToAdd: List<GradleCoordinate>): Triple<List<GradleCoordinate>, List<GradleCoordinate>, String> {
        TODO("Not yet implemented")
    }

    override fun getRegisteredDependency(coordinate: GradleCoordinate): GradleCoordinate? {
        TODO("Not yet implemented")
    }

    override fun getResolvedDependency(
        coordinate: GradleCoordinate,
        scope: DependencyScopeType
    ): GradleCoordinate? {
        return null
    }

    override fun canRegisterDependency(type: DependencyType): CapabilityStatus {
        TODO("Not yet implemented")
    }

    override fun registerDependency(coordinate: GradleCoordinate) {
        TODO("Not yet implemented")
    }

    override fun registerDependency(coordinate: GradleCoordinate, type: DependencyType) {
        TODO("Not yet implemented")
    }

    fun findPath(root: String, name: String): PathString? {
        val search = ArrayDeque<String>()
        val rootFile = File(root)
        if (!rootFile.isDirectory) {
            return null
        }
        search.addAll(rootFile.list())
        while (!search.isEmpty()) {
            val f = File(search.removeFirst())
            if (f.name == name) {
                return PathString(f.absolutePath)
            } else if (f.isDirectory) {
                search.addAll(f.list())
            }
        }
        return null
    }

    override fun getAndroidLibraryDependencies(scope: DependencyScopeType): Collection<ExternalAndroidLibrary> {
        return (deps().map2Array {
            object : ExternalAndroidLibrary {
                override val address: String
                    get() = File(it).parent
                override val location: PathString?
                    get() = PathString(it)
                override val manifestFile: PathString?
                    get() = PathString(it + "/AndroidManifest.xml")
                override val packageName: String?
                    get() {
                        if (manifestFile?.rawPath == composeProject.lintProject.manifestFiles.first().path) {
                            return composeProject.lintProject.`package`
                        }
                        if (manifestFile != null) {
                            return AndroidManifestPackageNameUtils.getPackageNameFromManifestFile(
                                manifestFile!!
                            )
                        }
                        return null
                    }
                override val resFolder: ResourceFolder?
                    get() = RecursiveResourceFolder(findPath(it, "res")!!)
                override val assetsFolder: PathString?
                    get() = TODO("Not yet implemented")
                override val symbolFile: PathString?
                    get() = TODO("Not yet implemented")
                override val resApkFile: PathString?
                    get() = TODO("Not yet implemented")
                override val hasResources: Boolean
                    get() = findPath(it, "res") != null
            }
        }).toList()
    }

    override fun getResourceModuleDependencies(): List<Module> {
        return deps().flatMap { FileUtils.listFiles(File(it), arrayOf("xml"), true) }
            .filter { it.name.contains("AndroidManifest") }
            .map {
                ComposeModule(composeProject).module
            }
            .toList()
    }

    override fun getDirectResourceModuleDependents(): List<Module> {
        return getResourceModuleDependencies()
    }

    override fun canGeneratePngFromVectorGraphics(): CapabilityStatus {
        TODO("Not yet implemented")
    }

    override fun getManifestOverrides(): ManifestOverrides {
        val override = ManifestOverrides(
            mapOf(
                Pair(
                    ManifestSystemProperty.UsesSdk.MIN_SDK_VERSION,
                    composeProject.lintProject.minSdk.toString()
                ), Pair(
                    ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION,
                    composeProject.lintProject.targetSdk.toString()
                ), Pair(
                    ManifestSystemProperty.Document.PACKAGE, composeProject.lintProject.`package`!!
                )
            )
        )
        return override
    }

    override fun getPackageName(): String? {
        return composeProject.lintProject.`package`
    }

    override fun getResolveScope(scopeType: ScopeType): GlobalSearchScope {
        TODO("Not yet implemented")
    }

    override fun getSampleDataDirectory(): PathString? {
        return null
    }

    override fun getOrCreateSampleDataDirectory(): PathString? {
        return null
    }

    override fun getMergedManifestContributors(): MergedManifestContributors {
        var main: VirtualFile? = null
        var libraryManifest = mutableListOf<VirtualFile>()
        for (file in composeProject.lintProject.manifestFiles) {
            if (main == null) {
                main = file.toVirtualFile()
            } else {
                libraryManifest.add(file.toVirtualFile()!!)
            }
        }
        return MergedManifestContributors(main, listOf(), libraryManifest, listOf(), listOf())
    }

}
