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
import com.android.tools.lint.UastEnvironment.Companion.kotlinLibrary
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.mock.MockApplication
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.ClassTypePointerFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNameHelper
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartTypePointerManager
import com.intellij.psi.impl.PsiNameHelperImpl
import com.intellij.psi.impl.smartPointers.PsiClassReferenceTypePointerFactory
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl
import com.intellij.psi.impl.smartPointers.SmartTypePointerManagerImpl
import java.io.File
import kotlin.concurrent.withLock
import org.jetbrains.kotlin.analysis.api.KaAnalysisNonPublicApi
import org.jetbrains.kotlin.analysis.api.descriptors.CliFe10AnalysisFacade
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisFacade
import org.jetbrains.kotlin.analysis.api.descriptors.KaFe10AnalysisHandlerExtension
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinAnnotationsResolverFactory
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProviderFactory
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProviderMerger
import org.jetbrains.kotlin.analysis.api.platform.lifetime.KotlinAlwaysAccessibleLifetimeTokenProvider
import org.jetbrains.kotlin.analysis.api.platform.lifetime.KotlinLifetimeTokenProvider
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinGlobalModificationService
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationTrackerFactory
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinPackageProviderFactory
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinPackageProviderMerger
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinByModulesResolutionScopeProvider
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinResolutionScopeProvider
import org.jetbrains.kotlin.analysis.api.standalone.base.declarations.KotlinFakeClsStubsCache
import org.jetbrains.kotlin.analysis.api.standalone.base.declarations.KotlinStandaloneAnnotationsResolverFactory
import org.jetbrains.kotlin.analysis.api.standalone.base.declarations.KotlinStandaloneDeclarationProviderFactory
import org.jetbrains.kotlin.analysis.api.standalone.base.declarations.KotlinStandaloneDeclarationProviderMerger
import org.jetbrains.kotlin.analysis.api.standalone.base.modification.KotlinStandaloneGlobalModificationService
import org.jetbrains.kotlin.analysis.api.standalone.base.modification.KotlinStandaloneModificationTrackerFactory
import org.jetbrains.kotlin.analysis.api.standalone.base.packages.KotlinStandalonePackageProviderFactory
import org.jetbrains.kotlin.analysis.api.standalone.base.packages.KotlinStandalonePackageProviderMerger
import org.jetbrains.kotlin.analysis.api.standalone.base.projectStructure.AnalysisApiSimpleServiceRegistrar
import org.jetbrains.kotlin.analysis.api.standalone.base.projectStructure.PluginStructureProvider
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltinsVirtualFileProvider
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltinsVirtualFileProviderCliImpl
import org.jetbrains.kotlin.analysis.decompiler.stub.file.ClsKotlinBinaryClassCache
import org.jetbrains.kotlin.analysis.decompiler.stub.file.DummyFileAttributeService
import org.jetbrains.kotlin.analysis.decompiler.stub.file.FileAttributeService
import org.jetbrains.kotlin.analysis.project.structure.builder.KtModuleProviderBuilder
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles.JVM_CONFIG_FILES
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.references.fe10.base.DummyKtFe10ReferenceResolutionHelper
import org.jetbrains.kotlin.references.fe10.base.KtFe10ReferenceResolutionHelper
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCompilerConfigurationComponentRegistrar
import org.jetbrains.kotlin.util.slicedMap.WritableSlice
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.KotlinUastLanguagePlugin
import org.jetbrains.uast.kotlin.KotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.internal.CliKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.internal.UastAnalysisHandlerExtension

/**
 * This class is FE1.0 version of [UastEnvironment].
 *
 * After FIR (Frontend IR) is developed, the old frontend is retroactively named as FE1.0. So is
 * Kotlin UAST based on it.
 */
class Fe10UastEnvironment
private constructor(
  // Luckily, the Kotlin compiler already has the machinery for creating an IntelliJ
  // application environment (because Kotlin uses IntelliJ to parse Java). So most of
  // the work here is delegated to the Kotlin compiler.
  private val kotlinCompilerEnv: KotlinCoreEnvironment,
  override val projectDisposable: Disposable,
) : UastEnvironment {
  override val coreAppEnv: CoreApplicationEnvironment
    get() = kotlinCompilerEnv.projectEnvironment.environment

  override val ideaProject: MockProject
    get() = kotlinCompilerEnv.projectEnvironment.project

  override val kotlinCompilerConfig: CompilerConfiguration
    get() = kotlinCompilerEnv.configuration

  private val klibs = mutableListOf<KotlinLibrary>()

  class Configuration
  private constructor(override val kotlinCompilerConfig: CompilerConfiguration) :
    UastEnvironment.Configuration {
    override var javaLanguageLevel: LanguageLevel? = null

    // klibs indexed by paths to avoid duplicates
    internal val klibs = hashMapOf<String, KotlinLibrary>()

    override val modules = mutableListOf<UastEnvironment.Module>()
    override val classPaths = mutableSetOf<File>()

    // Legacy merging behavior for Fe 1.0
    override fun addModules(
      modules: List<UastEnvironment.Module>,
      bootClassPaths: Iterable<File>?,
    ) {
      this.modules.addAll(modules)
      bootClassPaths?.let(this.classPaths::addAll)

      kotlinLanguageLevel =
        modules.map(UastEnvironment.Module::kotlinLanguageLevel).reduce { r, t ->
          // TODO: How to accumulate `analysisFlags` and `specificFeatures` ?
          LanguageVersionSettingsImpl(
            r.languageVersion.coerceAtLeast(t.languageVersion),
            r.apiVersion.coerceAtLeast(t.apiVersion),
          )
        }
      UastEnvironment.Configuration.mergeRoots(modules, bootClassPaths).let { (sources, classPaths)
        ->
        val allKlibPaths =
          modules.flatMap { it.klibs.keys.map(File::getAbsolutePath) } +
            kotlinCompilerConfig.getKlibPaths()
        for (p in allKlibPaths) {
          klibs.computeIfAbsent(p, ::kotlinLibrary)
        }
        addSourceRoots(sources.toList())
        addClasspathRoots(classPaths.toList())
      }
    }

    companion object {
      @JvmStatic
      fun create(enableKotlinScripting: Boolean): Configuration =
        Configuration(createKotlinCompilerConfig(enableKotlinScripting))
    }
  }

  /**
   * Analyzes the given files so that PSI/UAST resolve works correctly.
   *
   * For now, only Kotlin files need to be analyzed upfront; Java code is resolved lazily. However,
   * this method must still be called for Java-only projects in order to properly initialize the PSI
   * machinery.
   *
   * Calling this function multiple times clears previous analysis results.
   */
  override fun analyzeFiles(ktFiles: List<File>) {
    val ktPsiFiles = mutableListOf<KtFile>()

    // Convert files to KtFiles.
    val fs = StandardFileSystems.local()
    val psiManager = PsiManager.getInstance(ideaProject)
    for (ktFile in ktFiles) {
      val vFile = fs.findFileByPath(ktFile.absolutePath) ?: continue
      val ktPsiFile = psiManager.findFile(vFile) as? KtFile ?: continue
      ktPsiFiles.add(ktPsiFile)
    }

    // TODO: This is a hack needed because TopDownAnalyzerFacadeForJVM calls
    //  KotlinCoreEnvironment.createPackagePartProvider(), which permanently adds additional
    //  PackagePartProviders to the environment. This significantly slows down resolve over
    //  time. The root issue is that KotlinCoreEnvironment was not designed to be reused
    //  repeatedly for multiple analyses---which we do when checkDependencies=true. This hack
    //  should be removed when we move to a model where UastEnvironment is used only once.
    resetPackagePartProviders()

    val perfManager = kotlinCompilerConfig.get(CLIConfigurationKeys.PERF_MANAGER)
    perfManager?.notifyAnalysisStarted()

    // Run the Kotlin compiler front end.
    // The result is implicitly associated with the IntelliJ project environment.
    // TODO: Consider specifying a sourceModuleSearchScope, which can be used to support
    //  partial compilation by giving the Kotlin compiler access to the compiled output
    //  of the module being analyzed. See KotlinToJVMBytecodeCompiler for an example.
    TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
      ideaProject,
      ktPsiFiles,
      CliBindingTraceForLint(ideaProject),
      kotlinCompilerConfig,
      kotlinCompilerEnv::createPackagePartProvider,
      klibList = klibs,
    )

    perfManager?.notifyAnalysisFinished()
  }

  private fun resetPackagePartProviders() {
    run {
      // Clear KotlinCoreEnvironment.packagePartProviders.
      val field = KotlinCoreEnvironment::class.java.getDeclaredField("packagePartProviders")
      field.isAccessible = true
      val list = field.get(kotlinCompilerEnv) as MutableList<*>
      list.clear()
    }
  }

  companion object {
    @JvmStatic
    fun create(config: Configuration): Fe10UastEnvironment {
      val parentDisposable = Disposer.newDisposable("Fe10UastEnvironment.create")
      val kotlinEnv = createKotlinCompilerEnv(parentDisposable, config)
      return Fe10UastEnvironment(kotlinEnv, parentDisposable).apply {
        klibs.addAll(config.klibs.values)
      }
    }
  }
}

@OptIn(ExperimentalCompilerApi::class)
private fun createKotlinCompilerConfig(enableKotlinScripting: Boolean): CompilerConfiguration {
  val config = createCommonKotlinCompilerConfig()

  // Registers the scripting compiler plugin to support build.gradle.kts files.
  if (enableKotlinScripting) {
    config.add(
      ComponentRegistrar.PLUGIN_COMPONENT_REGISTRARS,
      ScriptingCompilerConfigurationComponentRegistrar(),
    )
  }

  return config
}

private fun createKotlinCompilerEnv(
  parentDisposable: Disposable,
  config: Fe10UastEnvironment.Configuration,
): KotlinCoreEnvironment {
  val env =
    KotlinCoreEnvironment.createForProduction(
      parentDisposable,
      config.kotlinCompilerConfig,
      JVM_CONFIG_FILES,
    )
  appLock.withLock { configureFe10ApplicationEnvironment(env.projectEnvironment.environment) }
  configureFe10ProjectEnvironment(env.projectEnvironment, config)

  return env
}

private fun configureFe10ProjectEnvironment(
  env: KotlinCoreProjectEnvironment,
  config: Fe10UastEnvironment.Configuration,
) {
  val project = env.project
  // UAST support.
  AnalysisHandlerExtension.registerExtension(project, UastAnalysisHandlerExtension())
  project.registerService(
    KotlinUastResolveProviderService::class.java,
    CliKotlinUastResolveProviderService::class.java,
  )

  // PsiNameHelper is used by Kotlin UAST.
  project.registerService(PsiNameHelper::class.java, PsiNameHelperImpl::class.java)

  configureProjectEnvironment(project, config)

  configureAnalysisApiServices(env, config)
}

private fun configureAnalysisApiServices(
  env: KotlinCoreProjectEnvironment,
  config: Fe10UastEnvironment.Configuration,
) {
  val project = env.project
  AnalysisApiFe10ServiceRegistrar.registerProjectServices(project)
  AnalysisApiFe10ServiceRegistrar.registerProjectModelServices(project, env.parentDisposable)

  // Analysis API Base, i.e., base services for FE1.0 and FIR
  // But, for FIR, AA session builder already register these
  project.registerService(
    KotlinModificationTrackerFactory::class.java,
    KotlinStandaloneModificationTrackerFactory::class.java,
  )
  project.registerService(
    KotlinGlobalModificationService::class.java,
    KotlinStandaloneGlobalModificationService::class.java,
  )

  project.registerService(
    KotlinLifetimeTokenProvider::class.java,
    KotlinAlwaysAccessibleLifetimeTokenProvider::class.java,
  )

  project.registerService(
    SmartTypePointerManager::class.java,
    SmartTypePointerManagerImpl::class.java,
  )
  project.registerService(SmartPointerManager::class.java, SmartPointerManagerImpl::class.java)

  val projectStructureProvider =
    KtModuleProviderBuilder(env).apply(configureAnalysisApiProjectStructure(config)).build()
  val ktFiles = projectStructureProvider.allSourceFiles.filterIsInstance<KtFile>()

  project.registerService(
    KotlinAnnotationsResolverFactory::class.java,
    KotlinStandaloneAnnotationsResolverFactory(project, ktFiles),
  )
  project.registerService(
    KotlinResolutionScopeProvider::class.java,
    KotlinByModulesResolutionScopeProvider::class.java,
  )

  project.registerService(KotlinProjectStructureProvider::class.java, projectStructureProvider)

  project.registerService(
    KotlinDeclarationProviderFactory::class.java,
    KotlinStandaloneDeclarationProviderFactory(project, ktFiles),
  )
  project.registerService(
    KotlinDeclarationProviderMerger::class.java,
    KotlinStandaloneDeclarationProviderMerger::class.java,
  )
  project.registerService(
    KotlinPackageProviderMerger::class.java,
    KotlinStandalonePackageProviderMerger::class.java,
  )
  project.registerService(
    KotlinPackageProviderFactory::class.java,
    KotlinStandalonePackageProviderFactory(project, ktFiles),
  )
}

private fun configureFe10ApplicationEnvironment(appEnv: CoreApplicationEnvironment) {
  configureApplicationEnvironment(appEnv) {
    it.addExtension(UastLanguagePlugin.EP, KotlinUastLanguagePlugin())

    it.application.registerService(
      BaseKotlinUastResolveProviderService::class.java,
      CliKotlinUastResolveProviderService::class.java,
    )

    KotlinCoreEnvironment.underApplicationLock {
      if (it.application.getServiceIfCreated(KotlinFakeClsStubsCache::class.java) == null) {
        it.application.registerService(KotlinFakeClsStubsCache::class.java)
        it.application.registerService(
          BuiltinsVirtualFileProvider::class.java,
          BuiltinsVirtualFileProviderCliImpl::class.java,
        )
        it.application.registerService(ClsKotlinBinaryClassCache::class.java)
        it.application.registerService(
          FileAttributeService::class.java,
          DummyFileAttributeService::class.java,
        )
      }
      AnalysisApiFe10ServiceRegistrar.registerApplicationServices(it.application)
    }

    reRegisterProgressManager(it.application)
  }
}

// A Kotlin compiler BindingTrace optimized for Lint.
private class CliBindingTraceForLint(project: Project) : CliBindingTrace(project) {
  override fun <K, V> record(slice: WritableSlice<K, V>, key: K, value: V) {
    // Copied from NoScopeRecordCliBindingTrace.
    when (slice) {
      BindingContext.LEXICAL_SCOPE,
      BindingContext.DATA_FLOW_INFO_BEFORE -> return
    }
    super.record(slice, key, value)
  }

  // Lint does not need compiler checks, so disable them to improve performance slightly.
  override fun wantsDiagnostics(): Boolean = false

  override fun report(diagnostic: Diagnostic) {
    // Even with wantsDiagnostics=false, some diagnostics still come through. Ignore them.
    // Note: this is a great place to debug errors such as unresolved references.
  }
}

@OptIn(KaAnalysisNonPublicApi::class)
private object AnalysisApiFe10ServiceRegistrar : AnalysisApiSimpleServiceRegistrar() {
  private const val PLUGIN_RELATIVE_PATH = "/META-INF/analysis-api/analysis-api-fe10.xml"

  override fun registerApplicationServices(application: MockApplication) {
    PluginStructureProvider.registerApplicationServices(application, PLUGIN_RELATIVE_PATH)
    application.registerService(
      KtFe10ReferenceResolutionHelper::class.java,
      DummyKtFe10ReferenceResolutionHelper,
    )
    val applicationArea = application.extensionArea
    if (!applicationArea.hasExtensionPoint(ClassTypePointerFactory.EP_NAME)) {
      CoreApplicationEnvironment.registerApplicationExtensionPoint(
        ClassTypePointerFactory.EP_NAME,
        ClassTypePointerFactory::class.java,
      )
      applicationArea
        .getExtensionPoint(ClassTypePointerFactory.EP_NAME)
        .registerExtension(PsiClassReferenceTypePointerFactory(), application)
    }
  }

  override fun registerProjectExtensionPoints(project: MockProject) {
    AnalysisHandlerExtension.registerExtensionPoint(project)
    PluginStructureProvider.registerProjectExtensionPoints(project, PLUGIN_RELATIVE_PATH)
  }

  override fun registerProjectServices(project: MockProject) {
    PluginStructureProvider.registerProjectServices(project, PLUGIN_RELATIVE_PATH)
    PluginStructureProvider.registerProjectListeners(project, PLUGIN_RELATIVE_PATH)
  }

  override fun registerProjectModelServices(project: MockProject, disposable: Disposable) {
    project.apply { registerService(Fe10AnalysisFacade::class.java, CliFe10AnalysisFacade()) }
    AnalysisHandlerExtension.registerExtension(project, KaFe10AnalysisHandlerExtension())
  }
}
