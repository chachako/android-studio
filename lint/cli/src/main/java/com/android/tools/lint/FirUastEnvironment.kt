/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.mock.MockApplication
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileSetFactory
import com.intellij.pom.java.InternalPersistentJavaLanguageLevelReaderService
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.util.io.URLUtil
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.concurrent.withLock
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.impl.base.util.LibraryUtils
import org.jetbrains.kotlin.analysis.api.lifetime.KtAlwaysAccessibleLifetimeTokenFactory
import org.jetbrains.kotlin.analysis.api.standalone.StandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtLibraryModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSdkModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.analysis.project.structure.impl.getPsiFilesFromPaths
import org.jetbrains.kotlin.cli.common.CompilerSystemProperties
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.FirKotlinUastLanguagePlugin
import org.jetbrains.uast.kotlin.FirKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.internal.FirCliKotlinUastResolveProviderService

/**
 * This class is FIR (or K2) version of [UastEnvironment]
 *
 * K2 is a new compiler name that uses FIR as a frontend. Kotlin UAST is rewritten to use that new
 * frontend via Analysis APIs (backed by FIR), and named as FIR UAST. We may use K2 UAST
 * interchangeably.
 */
class FirUastEnvironment
private constructor(
  override val coreAppEnv: CoreApplicationEnvironment,
  override val ideaProject: MockProject,
  override val kotlinCompilerConfig: CompilerConfiguration,
  override val projectDisposable: Disposable
) : UastEnvironment {

  class Configuration
  private constructor(override val kotlinCompilerConfig: CompilerConfiguration) :
    UastEnvironment.Configuration {
    override var javaLanguageLevel: LanguageLevel? = null

    val modules = mutableListOf<UastEnvironment.Module>()
    val classPaths = mutableSetOf<File>()

    override fun addModules(modules: List<UastEnvironment.Module>, bootClassPaths: Iterable<File>) {
      this.modules.addAll(modules)
      this.classPaths.addAll(bootClassPaths)
    }

    companion object {
      @JvmStatic
      fun create(enableKotlinScripting: Boolean): Configuration =
        Configuration(createKotlinCompilerConfig(enableKotlinScripting))
    }
  }

  /** In FIR UAST, even Kotlin files are analyzed lazily. */
  override fun analyzeFiles(ktFiles: List<File>) {
    // TODO: addKtFilesFromSrcJars ?
  }

  companion object {
    @JvmStatic
    fun create(config: Configuration): FirUastEnvironment {
      val parentDisposable = Disposer.newDisposable("FirUastEnvironment.create")
      val analysisSession = createAnalysisSession(parentDisposable, config)
      return FirUastEnvironment(
        analysisSession.coreApplicationEnvironment,
        analysisSession.mockProject,
        config.kotlinCompilerConfig,
        parentDisposable
      )
    }
  }
}

private fun createKotlinCompilerConfig(enableKotlinScripting: Boolean): CompilerConfiguration {
  val config = createCommonKotlinCompilerConfig()

  System.setProperty("psi.sleep.in.validity.check", "false")

  // TODO: if [enableKotlinScripting], register FIR version of scripting compiler plugin if any

  return config
}

private fun createAnalysisSession(
  parentDisposable: Disposable,
  config: FirUastEnvironment.Configuration
): StandaloneAnalysisAPISession {
  // [configureApplicationEnvironment] will register app disposable and dispose it at
  // [UastEnvironment#disposeApplicationEnvironment].
  // I.e., we manage the application lifecycle manually.
  CompilerSystemProperties.KOTLIN_COMPILER_ENVIRONMENT_KEEPALIVE_PROPERTY.value = "true"

  val analysisSession =
    buildStandaloneAnalysisAPISession(
      applicationDisposable = parentDisposable,
      projectDisposable = parentDisposable,
      withPsiDeclarationFromBinaryModuleProvider = true
    ) {
      val theProject = project
      val thePlatform = JvmPlatforms.defaultJvmPlatform // TODO(b/283271025)
      // TODO: Avoid creating AA session per test mode, while app env. is not disposed,
      //  which led to duplicate app-level service registration.
      if (application.getServiceIfCreated(VirtualFileSetFactory::class.java) == null) {
        // Note that this app-level service should be initialized before any other entities attempt
        // to instantiate [FilesScope]
        // For FIR UAST, the first attempt will be made while building the module structure below.
        registerApplicationService(VirtualFileSetFactory::class.java, LintVirtualFileSetFactory)
      }
      // This app-level service should be registered before building project structure
      // which attempt to read JvmRoots for java files
      if (
        application.getServiceIfCreated(
          InternalPersistentJavaLanguageLevelReaderService::class.java
        ) == null
      ) {
        registerApplicationService(
          InternalPersistentJavaLanguageLevelReaderService::class.java,
          InternalPersistentJavaLanguageLevelReaderService.DefaultImpl()
        )
      }
      // We need to re-register Application-level service before AA session is built.
      reRegisterProgressManager(application as MockApplication)

      buildKtModuleProvider {
        platform = thePlatform
        project = theProject
        config.modules.forEach { m ->
          addModule(
            buildKtSourceModule {
              addRegularDependency(
                buildKtLibraryModule {
                  contentScope = ProjectScope.getLibrariesScope(theProject)
                  platform = thePlatform
                  project = theProject
                  binaryRoots =
                    when {
                      m.isAndroid -> m.classpathRoots + config.classPaths
                      else -> m.classpathRoots
                    }.map(File::toPath)
                  libraryName = "Library for ${m.name}"
                }
              )

              m.jdkHome?.let { jdkHome ->
                val vfm = VirtualFileManager.getInstance()
                val jdkHomePath = jdkHome.toPath()
                val jdkHomeVirtualFile = vfm.findFileByNioPath(jdkHomePath)

                addRegularDependency(
                  buildKtSdkModule {
                    contentScope = GlobalSearchScope.fileScope(theProject, jdkHomeVirtualFile)
                    platform = thePlatform
                    project = theProject
                    binaryRoots =
                      LibraryUtils.findClassesFromJdkHome(jdkHomePath).map {
                        Paths.get(URLUtil.extractPath(it))
                      }
                    sdkName = "JDK for ${m.name}"
                  }
                )
              }

              val ktFiles =
                getPsiFilesFromPaths<KtFile>(
                  theProject,
                  Helper.getSourceFilePaths(
                    m.sourceRoots.map(File::getPath),
                    includeDirectoryRoot = true
                  )
                )
              contentScope = TopDownAnalyzerFacadeForJVM.newModuleSearchScope(theProject, ktFiles)
              platform = thePlatform
              project = theProject
              moduleName = m.name
              addSourceRoots(ktFiles)
            }
          )
        }
      }
    }
  appLock.withLock {
    configureFirApplicationEnvironment(analysisSession.coreApplicationEnvironment)
  }
  configureFirProjectEnvironment(analysisSession, config)

  return analysisSession
}

private fun configureFirProjectEnvironment(
  analysisAPISession: StandaloneAnalysisAPISession,
  config: UastEnvironment.Configuration
) {
  val project = analysisAPISession.mockProject

  project.registerService(
    FirKotlinUastResolveProviderService::class.java,
    FirCliKotlinUastResolveProviderService::class.java
  )

  configureProjectEnvironment(project, config)
}

private fun configureFirApplicationEnvironment(appEnv: CoreApplicationEnvironment) {
  configureApplicationEnvironment(appEnv) {
    it.addExtension(UastLanguagePlugin.extensionPointName, FirKotlinUastLanguagePlugin())

    it.application.registerService(
      BaseKotlinUastResolveProviderService::class.java,
      FirCliKotlinUastResolveProviderService::class.java
    )
  }
}

// Lint version of `analyzeForUast` in
// `org.jetbrains.uast.kotlin.internal.firKotlinInternalUastUtils`
inline fun <R> analyzeForLint(useSiteKtElement: KtElement, action: KtAnalysisSession.() -> R): R =
  analyze(useSiteKtElement, KtAlwaysAccessibleLifetimeTokenFactory, action)

// Copied over from `org.jetbrains.kotlin.analysis.project.structure.impl.KtModuleUtils.kt`
private object Helper {
  fun getSourceFilePaths(
    javaSourceRoots: Collection<String>,
    includeDirectoryRoot: Boolean = false,
  ): Set<String> {
    return buildSet {
      javaSourceRoots.forEach { srcRoot ->
        val path = Paths.get(srcRoot)
        if (Files.isDirectory(path)) {
          // E.g., project/app/src
          collectSourceFilePaths(path, this)
          if (includeDirectoryRoot) {
            add(srcRoot)
          }
        } else {
          // E.g., project/app/src/some/pkg/main.kt
          add(srcRoot)
        }
      }
    }
  }

  /**
   * Collect source file path from the given [root] store them in [result].
   *
   * E.g., for `project/app/src` as a [root], this will walk the file tree and collect all `.kt` and
   * `.java` files under that folder.
   *
   * Note that this util gracefully skips [IOException] during file tree traversal.
   */
  private fun collectSourceFilePaths(root: Path, result: MutableSet<String>) {
    // NB: [Files#walk] throws an exception if there is an issue during IO.
    // With [Files#walkFileTree] with a custom visitor, we can take control of exception handling.
    Files.walkFileTree(
      root,
      object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
          return if (Files.isReadable(dir)) FileVisitResult.CONTINUE
          else FileVisitResult.SKIP_SUBTREE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
          if (!Files.isRegularFile(file) || !Files.isReadable(file)) return FileVisitResult.CONTINUE
          val ext = com.google.common.io.Files.getFileExtension(file.fileName.toString())
          if (ext == KotlinFileType.EXTENSION || ext == JavaFileType.DEFAULT_EXTENSION) {
            result.add(file.toString())
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
      }
    )
  }
}
