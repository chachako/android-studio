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
package com.android.tools.lint

import com.google.common.io.Files as GoogleFiles
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import org.jetbrains.kotlin.analysis.project.structure.builder.KtSourceModuleBuilder
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.parsing.KotlinParserDefinition

// TODO(b/347072049): might be confused with upstream IntelliJ's VirtualFileWrapper
class VirtualFileWrapper(internal val file: VirtualFile) : File(file.path)

// Copied over from `org.jetbrains.kotlin.analysis.project.structure.impl.KtModuleUtils.kt`

internal fun getSourceFilePaths(
  javaSourceRoots: Collection<File>,
  includeDirectoryRoot: Boolean = false,
): PathCollection =
  getFilePaths(javaSourceRoots, sourceFileExtensions::contains, includeDirectoryRoot)

internal fun getFilePaths(
  roots: Collection<File>,
  isExtensionWanted: (String?) -> Boolean,
  includeDirectoryRoot: Boolean = false,
): PathCollection {
  val physicalPaths = hashSetOf<Path>()
  val virtualFiles = hashSetOf<VirtualFile>()

  fun fromFile(root: File) {
    val path = Paths.get(root.path)
    when {
      Files.isDirectory(path) -> {
        collectFilePaths(path, physicalPaths, isExtensionWanted) // E.g., project/app/src
        if (includeDirectoryRoot) physicalPaths.add(path)
      }
      else -> physicalPaths.add(path) // E.g., project/app/src/some/pkg/main.kt
    }
  }

  fun fromVirtualFile(root: VirtualFile) {
    /** This mirrors [collectFilePaths] below with something equivalent to [Files.walkFileTree] */
    fun visit(file: VirtualFile) {
      when {
        file.isDirectory -> file.children?.forEach(::visit)
        isExtensionWanted(file.extension) -> virtualFiles.add(file)
      }
    }
    visit(root)
    if (root.isDirectory && includeDirectoryRoot) virtualFiles.add(root)
  }

  for (root in roots) {
    when (root) {
      is VirtualFileWrapper -> fromVirtualFile(root.file)
      else -> fromFile(root)
    }
  }
  return PathCollection(physicalPaths, virtualFiles)
}

/**
 * Collect source file path from the given [root] store them in [result].
 *
 * E.g., for `project/app/src` as a [root], this will walk the file tree and collect all `.kt`,
 * `.kts`, and `.java` files under that folder.
 *
 * Note that this util gracefully skips [IOException] during file tree traversal.
 */
private fun collectFilePaths(
  root: Path,
  result: MutableSet<Path>,
  isExtensionWanted: (String?) -> Boolean,
) {
  // NB: [Files#walk] throws an exception if there is an issue during IO.
  // With [Files#walkFileTree] with a custom visitor, we can take control of exception handling.
  Files.walkFileTree(
    root,
    object : SimpleFileVisitor<Path>() {
      override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
        return if (Files.isReadable(dir)) FileVisitResult.CONTINUE else FileVisitResult.SKIP_SUBTREE
      }

      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) return FileVisitResult.CONTINUE
        if (isExtensionWanted(GoogleFiles.getFileExtension(file.fileName.toString()))) {
          result.add(file)
        }
        return FileVisitResult.CONTINUE
      }

      override fun visitFileFailed(file: Path, exc: IOException?): FileVisitResult {
        // TODO: report or log [IOException]?
        // NB: this intentionally swallows the exception, hence fail-safe.
        // Skipping subtree doesn't make any sense, since this is not a directory.
        // Skipping sibling may drop valid file paths afterward, so we just continue.
        return FileVisitResult.CONTINUE
      }
    },
  )
}

internal class PathCollection(
  val physical: Collection<Path>,
  val virtual: Collection<VirtualFile>,
) {
  fun isEmpty(): Boolean = physical.isEmpty() && virtual.isEmpty()

  /** Retain the paths that can be retrieved as [F] satisfying [keepFile] */
  inline fun <reified F : PsiFileSystemItem> filter(
    kotlinCoreProjectEnvironment: KotlinCoreProjectEnvironment,
    crossinline keepFile: (F) -> Boolean,
  ): PathCollection {
    val keepVirtual: (VirtualFile) -> Boolean =
      with(PsiManager.getInstance(kotlinCoreProjectEnvironment.project)) {
        { vFile ->
          val file = if (vFile.isDirectory) findDirectory(vFile) else findFile(vFile)
          file is F && keepFile(file)
        }
      }
    val keepPhysical: (Path) -> Boolean =
      with(kotlinCoreProjectEnvironment.environment.localFileSystem) {
        { path ->
          val vFile = findFileByPath(path.toString())
          vFile != null && keepVirtual(vFile)
        }
      }
    return PathCollection(physical.filter(keepPhysical), virtual.filter(keepVirtual))
  }
}

internal fun KtSourceModuleBuilder.addSourcePaths(paths: PathCollection) {
  addSourceRoots(paths.physical)
  addSourceVirtualFiles(paths.virtual)
}

private val sourceFileExtensions =
  arrayOf(
    KotlinFileType.EXTENSION,
    KotlinParserDefinition.STD_SCRIPT_SUFFIX,
    JavaFileType.DEFAULT_EXTENSION,
  )
