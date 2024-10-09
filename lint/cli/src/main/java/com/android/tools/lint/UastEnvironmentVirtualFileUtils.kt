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
import kotlin.io.path.isDirectory
import org.jetbrains.kotlin.analysis.project.structure.builder.KtBinaryModuleBuilder
import org.jetbrains.kotlin.analysis.project.structure.builder.KtSourceModuleBuilder
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.parsing.KotlinParserDefinition

private class VirtualFileWrapper(val file: VirtualFile) : File(file.path)

/**
 * Convert [VirtualFile] into [File] without losing information, as far as [UastEnvironment] is
 * concerned
 */
fun VirtualFile.asFile(): File = VirtualFileWrapper(this)

// Copied over from `org.jetbrains.kotlin.analysis.project.structure.impl.KtModuleUtils.kt`

internal fun getSourceFilePaths(
  javaSourceRoots: Collection<File>,
  includeDirectoryRoot: Boolean = false,
): PathCollection =
  getFilePaths(javaSourceRoots, sourceFileExtensions::contains, includeDirectoryRoot)

/**
 * Return a [PathCollection] of paths beneath [roots], whose extensions satisfy [isExtensionWanted]
 */
private fun getFilePaths(
  roots: Collection<File>,
  isExtensionWanted: (String?) -> Boolean,
  includeDirectoryRoot: Boolean = false,
): PathCollection {
  val physicalPaths = hashSetOf<Path>()
  val physicalDirectoryPaths = hashSetOf<Path>()
  val virtualFiles = hashSetOf<VirtualFile>()
  val virtualDirectories = hashSetOf<VirtualFile>()

  fun fromFile(root: File) {
    val path = Paths.get(root.path)
    when {
      Files.isDirectory(path) -> {
        collectFilePaths(path, physicalPaths, isExtensionWanted) // E.g., project/app/src
        if (includeDirectoryRoot) physicalDirectoryPaths.add(path)
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
    if (root.isDirectory && includeDirectoryRoot) virtualDirectories.add(root)
  }

  for (root in roots) {
    when (root) {
      is VirtualFileWrapper -> fromVirtualFile(root.file)
      else -> fromFile(root)
    }
  }
  return PathCollection(physicalPaths, physicalDirectoryPaths, virtualFiles, virtualDirectories)
}

/**
 * Recover [PathCollection] from [File]s, some of which may have been created from [asFile]. This
 * prevents failure from extracting a [Path] our of a non-physical file.
 */
internal fun Iterable<File>.toPathCollection(): PathCollection {
  val physicalFilePaths = hashSetOf<Path>()
  val physicalDirectoryPaths = hashSetOf<Path>()
  val virtualFiles = hashSetOf<VirtualFile>()
  val virtualDirectories = hashSetOf<VirtualFile>()
  for (file in this) {
    when (file) {
      is VirtualFileWrapper -> {
        if (file.file.isDirectory) {
          virtualDirectories.add(file.file)
        } else {
          virtualFiles.add(file.file)
        }
      }
      else -> {
        if (file.isDirectory) {
          physicalDirectoryPaths.add(Paths.get(file.path))
        } else {
          physicalFilePaths.add(Paths.get(file.path))
        }
      }
    }
  }
  return PathCollection(physicalFilePaths, physicalDirectoryPaths, virtualFiles, virtualDirectories)
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
  val physicalFiles: Collection<Path>,
  val physicalDirectories: Collection<Path>,
  val virtualFiles: Collection<VirtualFile>,
  val virtualDirectories: Collection<VirtualFile>,
) {
  fun isEmpty(): Boolean =
    physicalFiles.isEmpty() &&
      physicalDirectories.isEmpty() &&
      virtualFiles.isEmpty() &&
      virtualDirectories.isEmpty()

  fun hasFiles(): Boolean = physicalFiles.isNotEmpty() || virtualFiles.isNotEmpty()

  fun isNotEmpty(): Boolean = !isEmpty()

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
    return PathCollection(
      physicalFiles.filter(keepPhysical),
      physicalDirectories.filter(keepPhysical),
      virtualFiles.filter(keepVirtual),
      virtualDirectories.filter(keepVirtual),
    )
  }

  operator fun plus(paths: Collection<Path>): PathCollection {
    val newPhysicalFiles = physicalFiles.toMutableSet()
    val newPhysicalDirectories = physicalDirectories.toMutableSet()
    for (path in paths) {
      if (path.isDirectory()) {
        newPhysicalDirectories.add(path)
      } else {
        newPhysicalFiles.add(path)
      }
    }
    return PathCollection(
      newPhysicalFiles,
      newPhysicalDirectories,
      virtualFiles,
      virtualDirectories,
    )
  }

  override fun toString(): String {
    return buildString {
      appendLine("PathCollection {")
      append("  physical: ")
      (physicalDirectories + physicalFiles).joinTo(buffer = this, prefix = "[", postfix = "]")
      appendLine()
      append("  virtual: ")
      (virtualDirectories + virtualFiles).joinTo(buffer = this, prefix = "[", postfix = "]")
      appendLine()
      appendLine("}")
    }
  }
}

internal fun KtSourceModuleBuilder.addSourcePaths(paths: PathCollection) {
  addSourceRoots(paths.physicalDirectories)
  addSourceRoots(paths.physicalFiles)
  addSourceVirtualFiles(paths.virtualDirectories)
  addSourceVirtualFiles(paths.virtualFiles)
}

internal fun KtBinaryModuleBuilder.addBinaryPaths(paths: PathCollection) {
  addBinaryRoots(paths.physicalDirectories)
  addBinaryRoots(paths.physicalFiles)
  addBinaryVirtualFiles(paths.virtualDirectories)
  addBinaryVirtualFiles(paths.virtualFiles)
}

private val sourceFileExtensions =
  arrayOf(
    KotlinFileType.EXTENSION,
    KotlinParserDefinition.STD_SCRIPT_SUFFIX,
    JavaFileType.DEFAULT_EXTENSION,
  )
