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

import com.android.tools.lint.UastEnvironment.Companion.getKlibPaths
import com.android.tools.lint.UastEnvironment.Module.Variant
import com.android.tools.lint.detector.api.GraphUtils
import com.android.tools.lint.detector.api.Project
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.mock.MockApplication
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileSetFactory
import com.intellij.pom.java.InternalPersistentJavaLanguageLevelReaderService
import com.intellij.pom.java.LanguageLevel
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.concurrent.withLock
import org.jetbrains.kotlin.analysis.api.impl.base.util.LibraryUtils
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KtResolveExtensionProvider
import org.jetbrains.kotlin.analysis.api.standalone.StandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.decompiled.light.classes.ClsJavaStubByVirtualFileCache
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.builder.KtModuleBuilder
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtLibraryModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSdkModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.analysis.project.structure.impl.getPsiFilesFromPaths
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.platform.wasm.WasmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingK2CompilerPluginRegistrar
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

    override fun addModules(
      modules: List<UastEnvironment.Module>,
      bootClassPaths: Iterable<File>?
    ) {
      this.modules.addAll(modules)
      bootClassPaths?.let(this.classPaths::addAll)
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

@OptIn(ExperimentalCompilerApi::class)
private fun createKotlinCompilerConfig(enableKotlinScripting: Boolean): CompilerConfiguration {
  val config = createCommonKotlinCompilerConfig()

  System.setProperty("psi.sleep.in.validity.check", "false")

  // Registers the scripting compiler plugin to support build.gradle.kts files.
  if (enableKotlinScripting) {
    // TODO: [KtCompilerPluginsProvider] is a preferred way.
    // NB: hacky solution to pass the registrar to the registration point below.
    config.add(
      CompilerPluginRegistrar.COMPILER_PLUGIN_REGISTRARS,
      ScriptingK2CompilerPluginRegistrar()
    )
  }

  return config
}

private fun createAnalysisSession(
  parentDisposable: Disposable,
  config: FirUastEnvironment.Configuration
): StandaloneAnalysisAPISession {
  val analysisSession =
    buildStandaloneAnalysisAPISession(
      applicationDisposable = parentDisposable,
      projectDisposable = parentDisposable,
      withPsiDeclarationFromBinaryModuleProvider = true,
      compilerConfiguration = config.kotlinCompilerConfig,
    ) {
      CoreApplicationEnvironment.registerExtensionPoint(
        project.extensionArea,
        KtResolveExtensionProvider.EP_NAME.name,
        KtResolveExtensionProvider::class.java
      )
      (project as MockProject).registerKtLifetimeTokenProvider()
      registerProjectService(
        ClsJavaStubByVirtualFileCache::class.java,
        ClsJavaStubByVirtualFileCache()
      )

      appLock.withLock {
        // TODO: Avoid creating AA session per test mode, while app env. is not disposed,
        //  which led to duplicate app-level service registration.
        if (application.getServiceIfCreated(VirtualFileSetFactory::class.java) == null) {
          // Note that this app-level service should be initialized before any other entities
          // attempt to instantiate [FilesScope]
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
      }

      buildKtModuleProvider {
        // TODO(b/283271025): what is the platform of module provider for KMP?
        platform = JvmPlatforms.defaultJvmPlatform

        val uastEnvModuleByName = config.modules.associateBy(UastEnvironment.Module::name)
        val uastEnvModuleOrder = // We need to start from the leaves of the dependency
          GraphUtils.reverseTopologicalSort(config.modules.map { it.name }) {
            uastEnvModuleByName[it]!!.directDependencies.map { (depName, _) -> depName }
          }
        val builtKtModuleByName = hashMapOf<String, KtModule>() // incrementally added below
        val configKlibPaths = config.kotlinCompilerConfig.getKlibPaths().map(Path::of)

        uastEnvModuleOrder.forEach { name ->
          val m = uastEnvModuleByName[name]!!

          val mPlatform =
            when (m.variant) {
              Variant.COMMON -> CommonPlatforms.defaultCommonPlatform
              Variant.NATIVE -> NativePlatforms.unspecifiedNativePlatform
              Variant.JS -> JsPlatforms.defaultJsPlatform
              Variant.WASM -> WasmPlatforms.Default
              else -> JvmPlatforms.defaultJvmPlatform
            }

          fun KtModuleBuilder.addModuleDependencies(moduleName: String) {
            addRegularDependency(
              buildKtLibraryModule {
                platform = mPlatform
                val classPaths =
                  if (mPlatform.isJvm()) {
                    // Include boot classpath in [config.classPaths]
                    m.classpathRoots + config.classPaths
                  } else {
                    m.classpathRoots
                  }
                addBinaryRoots(classPaths.map(File::toPath))
                libraryName = "Library for $moduleName"
              }
            )

            m.jdkHome?.let { jdkHome ->
              val jdkHomePath = jdkHome.toPath()
              addRegularDependency(
                buildKtSdkModule {
                  platform = mPlatform
                  addBinaryRoots(LibraryUtils.findClassesFromJdkHome(jdkHomePath, isJre = true))
                  sdkName = "JDK for $moduleName"
                }
              )
            }

            val (moduleKlibPathsRegular, moduleKlibPathsDependsOn) =
              m.klibs.keys.partition { m.klibs[it] == Project.DependencyKind.Regular }

            fun buildKlibModule(klibs: Collection<Path>, name: String) = buildKtLibraryModule {
              platform = mPlatform
              addBinaryRoots(klibs)
              libraryName = name
            }

            val klibRegularDeps =
              (moduleKlibPathsRegular.map(File::toPath) + configKlibPaths).distinct()
            if (klibRegularDeps.isNotEmpty()) {
              addRegularDependency(
                buildKlibModule(klibRegularDeps, "Regular klibs for $moduleName")
              )
            }

            if (moduleKlibPathsDependsOn.isNotEmpty()) {
              addDependsOnDependency(
                buildKlibModule(
                  moduleKlibPathsDependsOn.map(File::toPath),
                  "dependsOn klibs for $moduleName"
                )
              )
            }
          }

          val ktFiles =
            getPsiFilesFromPaths<KtFile>(
              kotlinCoreProjectEnvironment,
              Helper.getSourceFilePaths(
                (m.sourceRoots + m.gradleBuildScripts).map(File::getPath),
                includeDirectoryRoot = true
              )
            )
          val (scriptFiles, _) = ktFiles.partition { it.isScript() }
          // TODO: https://youtrack.jetbrains.com/issue/KT-62161
          //   This must be [KtScriptModule], but until the above YT resolved
          //   add this fake [KtSourceModule] to suppress errors from module lookup.
          if (scriptFiles.isNotEmpty()) {
            addModule(
              buildKtSourceModule {
                addModuleDependencies("Temporary module for scripts in " + m.name)
                platform = mPlatform
                moduleName = m.name
                addSourceRoots(
                  Helper.getSourceFilePaths(
                    scriptFiles.map { it.virtualFilePath },
                    includeDirectoryRoot = false
                  )
                )
              }
            )
          }
          /*
          for (scriptFile in scriptFiles) {
            addModule(
              buildKtScriptModule {
                platform = mPlatform
                file = scriptFile
                addModuleDependencies("Script " + scriptFile.name)
              }
            )
          }
          */

          val ktModule = buildKtSourceModule {
            languageVersionSettings = m.kotlinLanguageLevel
            addModuleDependencies(m.name)
            platform = mPlatform
            moduleName = m.name

            m.directDependencies.forEach { (depName, depKind) ->
              builtKtModuleByName[depName]?.let { depKtModule ->
                when (depKind) {
                  Project.DependencyKind.Regular -> addRegularDependency(depKtModule)
                  Project.DependencyKind.DependsOn -> addDependsOnDependency(depKtModule)
                }
              }
                ?: System.err.println(
                  "Dependency named `$depName` ignored because module not found"
                )
            }

            // NB: This should include both .kt and .java sources if any,
            //  and thus we don't need to specify the reified type for the return file type.
            addSourceRoots(
              Helper.getSourceFilePaths(
                m.sourceRoots.map(File::getPath),
                includeDirectoryRoot = true
              )
            )
          }

          addModule(ktModule)
          builtKtModuleByName[name] = ktModule
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

  configureProjectEnvironment(project, config)
}

private fun configureFirApplicationEnvironment(appEnv: CoreApplicationEnvironment) {
  configureApplicationEnvironment(appEnv) {
    it.addExtension(UastLanguagePlugin.extensionPointName, FirKotlinUastLanguagePlugin())

    it.application.registerService(
      BaseKotlinUastResolveProviderService::class.java,
      FirCliKotlinUastResolveProviderService::class.java
    )
    it.application.registerService(
      FirKotlinUastResolveProviderService::class.java,
      FirCliKotlinUastResolveProviderService::class.java
    )
  }
}

// Copied over from `org.jetbrains.kotlin.analysis.project.structure.impl.KtModuleUtils.kt`
private object Helper {
  fun getSourceFilePaths(
    javaSourceRoots: Collection<String>,
    includeDirectoryRoot: Boolean = false,
  ): Collection<Path> {
    return buildSet {
      javaSourceRoots.forEach { srcRoot ->
        val path = Paths.get(srcRoot)
        if (Files.isDirectory(path)) {
          // E.g., project/app/src
          collectSourceFilePaths(path, this)
          if (includeDirectoryRoot) {
            add(path)
          }
        } else {
          // E.g., project/app/src/some/pkg/main.kt
          add(path)
        }
      }
    }
  }

  /**
   * Collect source file path from the given [root] store them in [result].
   *
   * E.g., for `project/app/src` as a [root], this will walk the file tree and collect all `.kt`,
   * `.kts`, and `.java` files under that folder.
   *
   * Note that this util gracefully skips [IOException] during file tree traversal.
   */
  private fun collectSourceFilePaths(root: Path, result: MutableSet<Path>) {
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
          if (
            ext == KotlinFileType.EXTENSION ||
              ext == KotlinParserDefinition.STD_SCRIPT_SUFFIX ||
              ext == JavaFileType.DEFAULT_EXTENSION
          ) {
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
      }
    )
  }
}
