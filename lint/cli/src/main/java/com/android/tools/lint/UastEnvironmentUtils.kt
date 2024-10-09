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
import com.android.tools.lint.UastEnvironment.Configuration.Companion.isKMP
import com.android.tools.lint.UastEnvironment.Module.Variant.Companion.toTargetPlatform
import com.android.tools.lint.detector.api.GraphUtils
import com.android.tools.lint.detector.api.LintModelModuleLibraryProject
import com.android.tools.lint.detector.api.Project
import com.intellij.codeInsight.CustomExceptionHandler
import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.codeInsight.InferredAnnotationsManager
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.mock.MockApplication
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.pom.java.LanguageFeatureProvider
import com.intellij.psi.PsiFile
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.impl.base.util.LibraryUtils
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.decompiler.konan.KlibMetaFileType
import org.jetbrains.kotlin.analysis.project.structure.builder.KtModuleBuilder
import org.jetbrains.kotlin.analysis.project.structure.builder.KtModuleProviderBuilder
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtLibraryModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSdkModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.CompilerSystemProperties
import org.jetbrains.kotlin.cli.common.messages.GradleStyleMessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.library.KLIB_METADATA_FILE_EXTENSION
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.has
import org.jetbrains.kotlin.platform.jvm.JvmPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.diagnostics.DiagnosticSuppressor
import org.jetbrains.uast.UastContext
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.evaluation.UEvaluatorExtension
import org.jetbrains.uast.java.JavaUastLanguagePlugin
import org.jetbrains.uast.kotlin.evaluation.KotlinEvaluatorExtension

internal fun createCommonKotlinCompilerConfig(): CompilerConfiguration {
  val config = CompilerConfiguration()

  config.put(CommonConfigurationKeys.MODULE_NAME, "lint-module")

  // By default, the Kotlin compiler will dispose the application environment when there
  // are no projects left. However, that behavior is poorly tested and occasionally buggy
  // (see KT-45289). So, instead we manage the application lifecycle manually.
  CompilerSystemProperties.KOTLIN_COMPILER_ENVIRONMENT_KEEPALIVE_PROPERTY.value = "true"

  // We're not running compiler checks, but we still want to register a logger
  // in order to see warnings related to misconfiguration.
  val logger = PrintingMessageCollector(System.err, GradleStyleMessageRenderer(), false)
  config.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, logger)

  // The Kotlin compiler uses a fast, ASM-based class file reader.
  // However, Lint still relies on representing class files with PSI.
  config.put(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING, true)

  // We don't bundle .dll files in the Gradle plugin for native file system access;
  // prevent warning logs on Windows when it's not found (see b.android.com/260180).
  System.setProperty("idea.use.native.fs.for.win", "false")

  config.put(JVMConfigurationKeys.NO_JDK, true)

  return config
}

/** Returns a new [LanguageVersionSettings] with KMP enabled. */
fun LanguageVersionSettings.withKMPEnabled(): LanguageVersionSettings {
  return LanguageVersionSettingsImpl(
    this.languageVersion,
    this.apiVersion,
    emptyMap(),
    mapOf(LanguageFeature.MultiPlatformProjects to LanguageFeature.State.ENABLED),
  )
}

internal fun configureProjectEnvironment(
  project: MockProject,
  config: UastEnvironment.Configuration,
) {
  // Annotation support.
  project.registerService(
    ExternalAnnotationsManager::class.java,
    LintExternalAnnotationsManager::class.java,
  )
  project.registerService(
    InferredAnnotationsManager::class.java,
    LintInferredAnnotationsManager::class.java,
  )

  // Java language level.
  val javaLanguageLevel = config.javaLanguageLevel
  if (javaLanguageLevel != null) {
    LanguageLevelProjectExtension.getInstance(project).languageLevel = javaLanguageLevel
  }

  // TODO(b/283351708): Migrate to using UastFacade/UastLanguagePlugin instead,
  //  even including lint checks shipped in a binary form?!
  @Suppress("DEPRECATION") project.registerService(UastContext::class.java, UastContext(project))
}

@OptIn(KaImplementationDetail::class)
internal fun configureAnalysisApiProjectStructure(
  config: UastEnvironment.Configuration
): KtModuleProviderBuilder.() -> Unit = {
  val isKMP = config.isKMP
  // The platform of the module provider, not individual modules
  platform = if (isKMP) CommonPlatforms.defaultCommonPlatform else JvmPlatforms.defaultJvmPlatform

  val uastEnvModuleByProject = config.modules.associateBy(UastEnvironment.Module::project)
  val uastEnvModuleOrder = // We need to start from the leaves of the dependency
    GraphUtils.reverseTopologicalSort(uastEnvModuleByProject.keys) {
      uastEnvModuleByProject[it]!!.directDependencies.map { (depProject, _) -> depProject }
    }
  val builtKtModuleByProject = hashMapOf<Project, KaModule>() // incrementally added below
  val configKlibPaths = config.kotlinCompilerConfig.getKlibPaths().map(Path::of)

  val projectsWithNoSource = mutableSetOf<Project>()
  for (proj in uastEnvModuleOrder) {
    val m = uastEnvModuleByProject[proj]!!
    val mPlatform = m.variant.toTargetPlatform()

    // NB: this walks through the entire directories as source roots and build scripts
    // Therefore, we call this only once here and use them with necessary filtering at use-site.
    val sourceFilePaths =
      getSourceFilePaths(m.sourceRoots + m.gradleBuildScripts, includeDirectoryRoot = true)

    // b/371220733: AGP library modules might refer to `out` directory w/o source files
    if (proj is LintModelModuleLibraryProject && !sourceFilePaths.hasFiles()) {
      projectsWithNoSource.add(proj)
      continue
    }

    val classPaths =
      if (mPlatform.has<JvmPlatform>()) {
          // Include boot classpath in [config.classPaths], except for non-JVM modules
          m.classpathRoots + config.classPaths
        } else {
          m.classpathRoots
        }
        .toPathCollection()

    fun KtModuleBuilder.addModuleDependencies(moduleName: String) {
      if (classPaths.isNotEmpty()) {
        addRegularDependency(
          buildKtLibraryModule {
            platform = mPlatform
            addBinaryPaths(classPaths)
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

      fun buildKlibModule(klibs: PathCollection, name: String) = buildKtLibraryModule {
        platform = mPlatform
        addBinaryPaths(klibs)
        libraryName = name
      }

      val klibRegularDeps = moduleKlibPathsRegular.toPathCollection() + configKlibPaths
      if (klibRegularDeps.isNotEmpty()) {
        addRegularDependency(buildKlibModule(klibRegularDeps, "Regular klibs for $moduleName"))
      }

      if (moduleKlibPathsDependsOn.isNotEmpty()) {
        addDependsOnDependency(
          buildKlibModule(
            moduleKlibPathsDependsOn.toPathCollection(),
            "dependsOn klibs for $moduleName",
          )
        )
      }
    }

    val scripts = sourceFilePaths.filter<KtFile>(kotlinCoreProjectEnvironment, KtFile::isScript)
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

    val ktModule =
      when {
        m.sourceRoots.isNotEmpty() -> {
          buildKtSourceModule {
            languageVersionSettings =
              if (isKMP) m.kotlinLanguageLevel.withKMPEnabled() else m.kotlinLanguageLevel
            addModuleDependencies(m.name)
            platform = mPlatform
            moduleName = m.name

            for ((depProj, depKind) in m.directDependencies) {
              if (depProj in projectsWithNoSource) {
                // [Project] w/o source files has been skipped.
                continue
              }
              builtKtModuleByProject[depProj]?.let { depKtModule ->
                when (depKind) {
                  Project.DependencyKind.Regular -> addRegularDependency(depKtModule)
                  Project.DependencyKind.DependsOn -> addDependsOnDependency(depKtModule)
                }
              }
                ?: System.err.println(
                  "Dependency named `${depProj.name}` (pkg: `${depProj.`package`}`) ignored because module not found"
                )
            }

            addSourcePaths(
              sourceFilePaths.filter<PsiFile>(kotlinCoreProjectEnvironment) { file ->
                // If it's [KtFile], filter out (build) script files
                // since they were already created as a separate module
                file !is KtFile || !file.isScript()
              }
            )
          }
        }
        m.classpathRoots.isNotEmpty() -> {
          buildKtLibraryModule {
            platform = mPlatform
            addBinaryPaths(m.classpathRoots.toPathCollection())
            libraryName = m.name
          }
        }
        else -> continue
      }

    addModule(ktModule)
    builtKtModuleByProject[proj] = ktModule
  }
}

// In parallel builds the Kotlin compiler will reuse the application environment
// (see KotlinCoreEnvironment.getOrCreateApplicationEnvironmentForProduction).
// So we need a lock to ensure that we only configure the application environment once.
internal val appLock = ReentrantLock()
private var appConfigured = false

internal fun configureApplicationEnvironment(
  appEnv: CoreApplicationEnvironment,
  configurator: (CoreApplicationEnvironment) -> Unit,
) {
  check(appLock.isHeldByCurrentThread)

  if (appConfigured) return

  if (!Logger.isInitialized()) {
    Logger.setFactory(::IdeaLoggerForLint)
  }

  // Mark the registry as loaded, otherwise there are warnings upon registry value lookup.
  Registry.markAsLoaded()

  // The Kotlin compiler does not use UAST, so we must configure it ourselves.
  CoreApplicationEnvironment.registerApplicationExtensionPoint(
    UastLanguagePlugin.EP,
    UastLanguagePlugin::class.java,
  )
  CoreApplicationEnvironment.registerApplicationExtensionPoint(
    UEvaluatorExtension.EXTENSION_POINT_NAME,
    UEvaluatorExtension::class.java,
  )
  appEnv.addExtension(UastLanguagePlugin.EP, JavaUastLanguagePlugin())

  appEnv.addExtension(UEvaluatorExtension.EXTENSION_POINT_NAME, KotlinEvaluatorExtension())

  configurator(appEnv)

  // These extensions points seem to be needed too, probably because Lint
  // triggers different IntelliJ code paths than the Kotlin compiler does.
  CoreApplicationEnvironment.registerApplicationExtensionPoint(
    CustomExceptionHandler.KEY,
    CustomExceptionHandler::class.java,
  )
  CoreApplicationEnvironment.registerApplicationExtensionPoint(
    DiagnosticSuppressor.EP_NAME,
    DiagnosticSuppressor::class.java,
  )
  CoreApplicationEnvironment.registerApplicationExtensionPoint(
    LanguageFeatureProvider.EXTENSION_POINT_NAME,
    LanguageFeatureProvider::class.java,
  )

  appEnv.registerFileType(KlibMetaFileType, KLIB_METADATA_FILE_EXTENSION)

  appConfigured = true
  Disposer.register(appEnv.parentDisposable, Disposable { appConfigured = false })
}

internal fun reRegisterProgressManager(application: MockApplication) {
  // The ProgressManager service is registered early in CoreApplicationEnvironment, we need to
  // remove it first.
  application.picoContainer.unregisterComponent(ProgressManager::class.java.name)
  application.registerService(
    ProgressManager::class.java,
    object : CoreProgressManager() {
      override fun doCheckCanceled() {
        // Do nothing
      }

      override fun isInNonCancelableSection() = true
    },
  )
}

// Most Logger.error() calls exist to trigger bug reports but are
// otherwise recoverable. E.g. see commit 3260e41111 in the Kotlin compiler.
// Thus we want to log errors to stderr but not throw exceptions (similar to the IDE).
private class IdeaLoggerForLint(category: String) : DefaultLogger(category) {
  override fun error(message: String?, t: Throwable?, vararg details: String?) {
    if (IdeaLoggerForLint::class.java.desiredAssertionStatus()) {
      throw AssertionError(message, t)
    } else {
      if (shouldDumpExceptionToStderr()) {
        System.err.println("ERROR: " + message + detailsToString(*details) + attachmentsToString(t))
        t?.printStackTrace(System.err)
      }
    }
  }
}
