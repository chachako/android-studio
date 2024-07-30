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
import com.android.tools.lint.UastEnvironment.Module.Variant.Companion.toTargetPlatform
import com.android.tools.lint.detector.api.GraphUtils
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.uast.DecompiledPsiDeclarationProvider
import com.android.tools.lint.uast.KotlinPsiDeclarationProviderFactory
import com.android.tools.lint.uast.KotlinStaticPsiDeclarationProviderFactory
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.mock.MockApplication
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.pom.java.LanguageLevel
import java.io.File
import java.nio.file.Path
import kotlin.concurrent.withLock
import org.jetbrains.kotlin.analysis.api.impl.base.util.LibraryUtils
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KtResolveExtensionProvider
import org.jetbrains.kotlin.analysis.api.standalone.StandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.decompiled.light.classes.ClsJavaStubByVirtualFileCache
import org.jetbrains.kotlin.analysis.project.structure.builder.KtModuleBuilder
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtLibraryModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSdkModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.has
import org.jetbrains.kotlin.platform.jvm.JvmPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingK2CompilerPluginRegistrar
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.FirKotlinUastLanguagePlugin
import org.jetbrains.uast.kotlin.FirKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.internal.FirCliKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.internal.FirKotlinUastLibraryPsiProviderService

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
  override val projectDisposable: Disposable,
) : UastEnvironment {

  class Configuration
  private constructor(override val kotlinCompilerConfig: CompilerConfiguration) :
    UastEnvironment.Configuration {
    override var javaLanguageLevel: LanguageLevel? = null

    val modules = mutableListOf<UastEnvironment.Module>()
    val classPaths = mutableSetOf<File>()

    val isKMP: Boolean
      get() {
        return modules.mapTo(mutableSetOf()) { it.variant }.size > 1
      }

    override fun addModules(
      modules: List<UastEnvironment.Module>,
      bootClassPaths: Iterable<File>?,
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
  override fun analyzeFiles(ktFiles: List<File>) {}

  companion object {
    @JvmStatic
    fun create(config: Configuration): FirUastEnvironment {
      val parentDisposable = Disposer.newDisposable("FirUastEnvironment.create")
      val analysisSession = createAnalysisSession(parentDisposable, config)
      return FirUastEnvironment(
        analysisSession.coreApplicationEnvironment,
        analysisSession.mockProject,
        config.kotlinCompilerConfig,
        parentDisposable,
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
      ScriptingK2CompilerPluginRegistrar(),
    )
  }

  return config
}

private fun createAnalysisSession(
  parentDisposable: Disposable,
  config: FirUastEnvironment.Configuration,
): StandaloneAnalysisAPISession {
  val isKMP = config.isKMP
  val analysisSession =
    buildStandaloneAnalysisAPISession(
      projectDisposable = parentDisposable,
      compilerConfiguration = config.kotlinCompilerConfig,
    ) {
      CoreApplicationEnvironment.registerExtensionPoint(
        project.extensionArea,
        KtResolveExtensionProvider.EP_NAME.name,
        KtResolveExtensionProvider::class.java,
      )
      registerProjectService(
        ClsJavaStubByVirtualFileCache::class.java,
        ClsJavaStubByVirtualFileCache(),
      )

      appLock.withLock {
        // TODO: Avoid creating AA session per test mode, while app env. is not disposed,
        //  which led to duplicate app-level service registration.
        // We need to re-register Application-level service before AA session is built.
        reRegisterProgressManager(application as MockApplication)
      }

      buildKtModuleProvider {
        // The platform of the module provider, not individual modules
        platform =
          if (isKMP) CommonPlatforms.defaultCommonPlatform else JvmPlatforms.defaultJvmPlatform

        val uastEnvModuleByName = config.modules.associateBy(UastEnvironment.Module::name)
        val uastEnvModuleOrder = // We need to start from the leaves of the dependency
          GraphUtils.reverseTopologicalSort(config.modules.map { it.name }) {
            uastEnvModuleByName[it]!!.directDependencies.map { (depName, _) -> depName }
          }
        val builtKtModuleByName = hashMapOf<String, KaModule>() // incrementally added below
        val configKlibPaths = config.kotlinCompilerConfig.getKlibPaths().map(Path::of)

        uastEnvModuleOrder.forEach { name ->
          val m = uastEnvModuleByName[name]!!
          val mPlatform = m.variant.toTargetPlatform()

          fun KtModuleBuilder.addModuleDependencies(moduleName: String) {
            val classPaths =
              if (mPlatform.has<JvmPlatform>()) {
                // Include boot classpath in [config.classPaths], except for non-JVM modules
                m.classpathRoots + config.classPaths
              } else {
                m.classpathRoots
              }
            if (classPaths.isNotEmpty()) {
              addRegularDependency(
                buildKtLibraryModule {
                  platform = mPlatform
                  addBinaryRoots(classPaths.map(File::toPath))
                  libraryName = "Library for $moduleName"
                }
              )
            }

            // Not necessary to set up JDK dependency for non-JVM modules
            if (mPlatform.has<JvmPlatform>()) {
              m.jdkHome?.let { jdkHome ->
                val jdkHomePath = jdkHome.toPath()
                addRegularDependency(
                  buildKtSdkModule {
                    platform = mPlatform
                    addBinaryRoots(LibraryUtils.findClassesFromJdkHome(jdkHomePath, isJre = true))
                    libraryName = "JDK for $moduleName"
                  }
                )
              }
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
                  "dependsOn klibs for $moduleName",
                )
              )
            }
          }

          val scripts =
            getSourceFilePaths(m.sourceRoots + m.gradleBuildScripts, includeDirectoryRoot = true)
              .filter<KtFile>(kotlinCoreProjectEnvironment, KtFile::isScript)
          // TODO: https://youtrack.jetbrains.com/issue/KT-62161
          //   This must be [KtScriptModule], but until the above YT resolved
          //   add this fake [KtSourceModule] to suppress errors from module lookup.
          if (!scripts.isEmpty()) {
            addModule(
              buildKtSourceModule {
                addModuleDependencies("Temporary module for scripts in " + m.name)
                platform = mPlatform
                moduleName = m.name
                addSourcePaths(scripts)
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
            languageVersionSettings =
              if (isKMP) m.kotlinLanguageLevel.withKMPEnabled() else m.kotlinLanguageLevel
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
            addSourcePaths(getSourceFilePaths(m.sourceRoots, includeDirectoryRoot = true))
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
  config: UastEnvironment.Configuration,
) {
  val project = analysisAPISession.mockProject

  configureProjectEnvironment(project, config)

  project.registerService(
    KotlinPsiDeclarationProviderFactory::class.java,
    KotlinStaticPsiDeclarationProviderFactory::class.java,
  )
}

private fun configureFirApplicationEnvironment(appEnv: CoreApplicationEnvironment) {
  configureApplicationEnvironment(appEnv) {
    it.addExtension(UastLanguagePlugin.EP, FirKotlinUastLanguagePlugin())

    it.application.registerService(
      FirKotlinUastLibraryPsiProviderService::class.java,
      DecompiledPsiDeclarationProvider::class.java,
    )

    it.application.registerService(
      BaseKotlinUastResolveProviderService::class.java,
      FirCliKotlinUastResolveProviderService::class.java,
    )
    it.application.registerService(
      FirKotlinUastResolveProviderService::class.java,
      FirCliKotlinUastResolveProviderService::class.java,
    )
  }
}
