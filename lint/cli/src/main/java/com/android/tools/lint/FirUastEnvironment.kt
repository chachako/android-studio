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
import kotlin.concurrent.withLock
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KaResolveExtensionProvider
import org.jetbrains.kotlin.analysis.api.standalone.StandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.decompiled.light.classes.ClsJavaStubByVirtualFileCache
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
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

    override val modules = mutableListOf<UastEnvironment.Module>()
    override val classPaths = mutableSetOf<File>()

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

@OptIn(KaExperimentalApi::class)
private fun createAnalysisSession(
  parentDisposable: Disposable,
  config: FirUastEnvironment.Configuration,
): StandaloneAnalysisAPISession {
  val analysisSession =
    buildStandaloneAnalysisAPISession(
      projectDisposable = parentDisposable,
      compilerConfiguration = config.kotlinCompilerConfig,
    ) {
      CoreApplicationEnvironment.registerExtensionPoint(
        project.extensionArea,
        KaResolveExtensionProvider.EP_NAME.name,
        KaResolveExtensionProvider::class.java,
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

      buildKtModuleProvider(configureAnalysisApiProjectStructure(config))
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

  val psiDeclarationProviderFactory =
    KotlinStaticPsiDeclarationProviderFactory(
      project,
      analysisAPISession.coreApplicationEnvironment.jarFileSystem,
    )

  project.registerService(
    KotlinPsiDeclarationProviderFactory::class.java,
    psiDeclarationProviderFactory,
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
