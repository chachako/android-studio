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
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileSetFactory
import com.intellij.pom.java.InternalPersistentJavaLanguageLevelReaderService
import com.intellij.pom.java.LanguageLevel
import java.io.File
import kotlin.concurrent.withLock
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.KtAlwaysAccessibleLifetimeTokenFactory
import org.jetbrains.kotlin.analysis.api.standalone.StandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.cli.common.CompilerSystemProperties
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtElement
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
      buildKtModuleProviderByCompilerConfiguration(config.kotlinCompilerConfig)
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
