/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.lint.detector.api

import com.android.SdkConstants.ANDROIDX_APPCOMPAT_LIB_ARTIFACT
import com.android.SdkConstants.ANDROIDX_LEANBACK_ARTIFACT
import com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT
import com.android.SdkConstants.LEANBACK_V17_ARTIFACT
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidVersion
import com.android.support.AndroidxNameUtils
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.ApiConstraint.Companion.max
import com.android.tools.lint.model.LintModelAndroidArtifact
import com.android.tools.lint.model.LintModelArtifactType
import com.android.tools.lint.model.LintModelDependency
import com.android.tools.lint.model.LintModelExternalLibrary
import com.android.tools.lint.model.LintModelModule
import com.android.tools.lint.model.LintModelModuleLibrary
import com.android.tools.lint.model.LintModelModuleType
import com.android.tools.lint.model.LintModelModuleType.DYNAMIC_FEATURE
import com.android.tools.lint.model.LintModelSourceProvider
import com.android.tools.lint.model.LintModelVariant
import com.android.utils.FileUtils
import com.android.utils.XmlUtils
import com.google.common.collect.Lists
import com.google.common.io.Files
import java.io.File
import java.io.IOException
import org.w3c.dom.Document

/**
 * Lint project for a project backed by a [LintModelModule] (which could be an app, a library,
 * dynamic feature, etc.)
 */
open class LintModelModuleProject(
  client: LintClient,
  dir: File,
  referenceDir: File,
  private val variant: LintModelVariant,
  mergedManifest: File?
) : Project(client, dir, referenceDir) {
  private val model: LintModelModule
    get() = variant.module

  init {
    gradleProject = true
    mergeManifests = true
    // Initialized after all projects are available by [resolveDependencies]
    directLibraries = mutableListOf()
    manifestMinSdks = ApiConstraint.ALL
    mergedManifest?.let { readManifest(it) }
    manifestMinSdk = variant.minSdkVersion

    // TODO: We need a Gradle DSL for SDK extensions. Until we have one, we'll instead use the
    // merged
    // manifest (which we read above) to specify the API vector. If we have a multi-dimensional
    // constraint,
    // the user has specified multiple extensions in the manifest and we'll use it; otherwise we'll
    // point
    // to the Gradle DSL minSdkVersion instead. Once we have a Gradle DSL, we should unconditionally
    // initialize this from the Gradle model.
    if (manifestMinSdks.getConstraints().size <= 1) {
      manifestMinSdks =
        manifestMinSdk?.let { ApiConstraint.get(it.featureLevel) } ?: ApiConstraint.ALL
    }
    manifestTargetSdk = variant.targetSdkVersion
  }

  override fun readManifest(document: Document) {
    super.readManifest(document)

    // Make sure the minSdkVersion is picked up from the build model, not the manifest
    manifestMinSdks =
      max(manifestMinSdks, ApiConstraint.get(minSdkVersion.featureLevel), either = false)
  }

  override fun toString(): String {
    return getName()
  }

  override fun isGradleProject(): Boolean {
    return model.agpVersion != null || super.isGradleProject()
  }

  fun setExternalLibrary(external: Boolean) {
    externalLibrary = external
  }

  fun addDirectLibrary(project: Project) {
    directLibraries.add(project)
  }

  private fun readManifest(manifest: File) {
    if (manifest.exists()) {
      try {
        val xml = manifest.readText()
        val document = XmlUtils.parseDocumentSilently(xml, true)
        document?.let { readManifest(it) }
      } catch (e: IOException) {
        client.log(e, "Could not read manifest %1\$s", manifest)
      }
    }
  }

  override fun initialize() {
    // Deliberately not calling super; that code is for ADT compatibility
  }

  private val sourceProviders: List<LintModelSourceProvider>
    get() = variant.sourceProviders

  private val testSourceProviders: List<LintModelSourceProvider>
    get() = variant.testSourceProviders

  private val testFixturesSourceProviders: List<LintModelSourceProvider>
    get() = variant.testFixturesSourceProviders

  override fun getBuildModule(): LintModelModule = variant.module
  override fun getBuildVariant(): LintModelVariant = variant
  override fun isLibrary(): Boolean =
    model.type === LintModelModuleType.LIBRARY || model.type === LintModelModuleType.JAVA_LIBRARY

  override fun isAndroidProject(): Boolean = type != LintModelModuleType.JAVA_LIBRARY
  override fun hasDynamicFeatures(): Boolean =
    model.type === LintModelModuleType.APP && model.dynamicFeatures.isNotEmpty()

  override fun getManifestFiles(): List<File> {
    if (manifestFiles == null) {
      manifestFiles =
        sourceProviders.flatMap { provider ->
          provider.manifestFiles.filter {
            it.exists()
          } // model returns path whether or not it exists
        }
    }
    return manifestFiles
  }

  override fun getProguardFiles(): List<File> {
    if (proguardFiles == null) {
      proguardFiles = variant.proguardFiles + variant.consumerProguardFiles
      // proguardFiles.addAll(container.config.getTestProguardFiles())
    }
    return proguardFiles
  }

  override fun getResourceFolders(): List<File> {
    if (resourceFolders == null) {
      resourceFolders = Lists.newArrayList()
      sourceProviders.forEach { provider ->
        // model returns path whether or not it exists
        provider.resDirectories
          .asSequence()
          .filter { it.exists() }
          .forEach { resourceFolders.add(it) }
      }
    }
    return resourceFolders
  }

  override fun getGeneratedResourceFolders(): List<File> {
    if (generatedResourceFolders == null) {
      val artifact = variant.artifact
      generatedResourceFolders =
        if (artifact is LintModelAndroidArtifact) {
          artifact.generatedResourceFolders.asSequence().filter { it.exists() }.toList()
        } else {
          listOf()
        }
    }
    return generatedResourceFolders
  }

  override fun getAssetFolders(): List<File> {
    if (assetFolders == null) {
      assetFolders = Lists.newArrayList()
      sourceProviders.forEach { provider ->
        // model returns path whether or not it exists
        provider.assetsDirectories
          .asSequence()
          .filter { it.exists() }
          .forEach { assetFolders.add(it) }
      }
    }
    return assetFolders
  }

  override fun getJavaSourceFolders(): List<File> {
    if (javaSourceFolders == null) {
      javaSourceFolders = Lists.newArrayList()
      sourceProviders.forEach { provider ->
        // model returns path whether or not it exists
        provider.javaDirectories
          .asSequence()
          .filter { it.exists() }
          .forEach { javaSourceFolders.add(it) }
      }
    }
    return javaSourceFolders
  }

  override fun getGeneratedSourceFolders(): List<File> {
    if (generatedSourceFolders == null) {
      val artifact = variant.artifact
      generatedSourceFolders =
        if (artifact is LintModelAndroidArtifact) {
          artifact.generatedSourceFolders.asSequence().filter { it.exists() }.toList()
        } else {
          listOf()
        }
    }
    return generatedSourceFolders
  }

  override fun getTestSourceFolders(): List<File> {
    if (testSourceFolders == null) {
      testSourceFolders = getInstrumentationTestSourceFolders() + getUnitTestSourceFolders()
      // add test sources which are neither instrumentation nor unit tests
      testSourceProviders
        .filter { !it.isInstrumentationTest() && !it.isUnitTest() }
        .forEach { provider ->
          // model returns path whether or not it exists
          provider.javaDirectories
            .asSequence()
            .filter { it.exists() }
            .forEach { testSourceFolders.add(it) }
        }
    }
    return testSourceFolders
  }

  override fun getInstrumentationTestSourceFolders(): List<File> {
    if (instrumentationTestSourceFolders == null) {
      instrumentationTestSourceFolders = mutableListOf()
      testSourceProviders
        .filter { it.isInstrumentationTest() }
        .forEach { provider ->
          // model returns path whether or not it exists
          provider.javaDirectories
            .asSequence()
            .filter { it.exists() }
            .forEach { instrumentationTestSourceFolders.add(it) }
        }
    }
    return instrumentationTestSourceFolders
  }

  override fun getUnitTestSourceFolders(): List<File> {
    if (unitTestSourceFolders == null) {
      unitTestSourceFolders = mutableListOf()
      testSourceProviders
        .filter { it.isUnitTest() }
        .forEach { provider ->
          // model returns path whether or not it exists
          provider.javaDirectories
            .asSequence()
            .filter { it.exists() }
            .forEach { unitTestSourceFolders.add(it) }
        }
    }
    return unitTestSourceFolders
  }

  override fun getJavaClassFolders(): List<File> {
    if (javaClassFolders == null) {
      javaClassFolders = ArrayList(3) // common: javac, kotlinc, R.jar
      for (outputClassFolder in variant.artifact.classOutputs) {
        if (outputClassFolder.exists()) {
          javaClassFolders.add(outputClassFolder)
        }
      }
      if (javaClassFolders.isEmpty() && isLibrary) {
        // For libraries we build the release variant instead
        for (variant in model.variants) {
          if (variant != this.variant) {
            var found = false
            for (outputClassFolder in variant.artifact.classOutputs) {
              if (outputClassFolder.exists()) {
                javaClassFolders.add(outputClassFolder)
                found = true
              }
            }
            if (found) {
              break
            }
          }
        }
      }
    }
    return javaClassFolders
  }

  override fun getJavaLibraries(includeProvided: Boolean): List<File> {
    return if (includeProvided) {
      if (javaLibraries == null) {
        // TODO: Why direct here and all in test libraries? And shouldn't
        // this be tied to checkDependencies somehow? If we're creating
        // project from the android libraries then I'll get the libraries there
        // right?
        val dependencies = variant.artifact.dependencies
        val direct = dependencies.compileDependencies.roots
        javaLibraries = Lists.newArrayListWithExpectedSize(direct.size)
        for (graphItem in direct) {
          val library = graphItem.findLibrary() ?: continue
          if (library !is LintModelExternalLibrary) continue
          library.addJars(javaLibraries, false)
        }
      }
      javaLibraries
    } else {
      // Skip provided libraries?
      if (nonProvidedJavaLibraries == null) {
        val dependencies = variant.artifact.dependencies
        val direct = dependencies.packageDependencies.roots
        nonProvidedJavaLibraries = Lists.newArrayListWithExpectedSize(direct.size)
        for (graphItem in direct) {
          val library = graphItem.findLibrary() ?: continue
          if (library !is LintModelExternalLibrary) continue
          library.addJars(nonProvidedJavaLibraries, true)
        }
      }
      nonProvidedJavaLibraries
    }
  }

  override fun getTestLibraries(): List<File> {
    if (testLibraries == null) {
      testLibraries = Lists.newArrayListWithExpectedSize(6)
      variant.androidTestArtifact?.let { artifact ->
        for (library in artifact.dependencies.getAll()) {
          // Note that we don't filter out AndroidLibraries here like
          // // for getJavaLibraries, but we need to include them
          // for tests since we don't keep them otherwise
          // (TODO: Figure out why)
          if (library !is LintModelExternalLibrary) continue
          library.addJars(testLibraries, false)
        }
      }
      variant.testArtifact?.let { artifact ->
        for (library in artifact.dependencies.getAll()) {
          if (library !is LintModelExternalLibrary) continue
          library.addJars(testLibraries, false)
        }
      }
    }
    return testLibraries
  }

  override fun getTestFixturesSourceFolders(): MutableList<File> {
    if (testFixturesSourceFolders == null) {
      testFixturesSourceFolders = Lists.newArrayList()
      testFixturesSourceProviders.forEach { provider ->
        // model returns path whether or not it exists
        provider.javaDirectories
          .filter { it.exists() }
          .forEach { testFixturesSourceFolders.add(it) }
      }
    }
    return testFixturesSourceFolders
  }

  override fun getTestFixturesLibraries(): MutableList<File> {
    if (testFixturesLibraries == null) {
      testFixturesLibraries = Lists.newArrayList()
      variant.testFixturesArtifact?.let { artifact ->
        artifact.dependencies.getAll().filterIsInstance<LintModelExternalLibrary>().forEach {
          it.addJars(testFixturesLibraries, false)
        }
      }
    }
    return testFixturesLibraries
  }

  override fun getPackage(): String? {
    if (pkg == null) { // only used as a fallback in case manifest somehow is null
      val packageName = variant.`package`
      if (packageName != null) {
        return packageName
      }
    }
    return pkg // from manifest
  }

  override fun getMinSdkVersion(): AndroidVersion {
    return manifestMinSdk
      ?: run {
        val minSdk = variant.minSdkVersion ?: super.getMinSdkVersion() // from manifest
        manifestMinSdk = minSdk
        minSdk
      }
  }

  override fun getMinSdkVersions(): ApiConstraint {
    return manifestMinSdks
      ?: run {
        // TODO: When Gradle DSL supports extension levels switch to initializing these here.
        // (Note that we also initialize this in init {} where we consult the merged manifest
        // for extra tags.
        val minSdks =
          variant.minSdkVersion?.let { ApiConstraint.get(it.featureLevel) }
            ?: super.getMinSdkVersions() // from manifest
        manifestMinSdks = minSdks
        minSdks
      }
  }

  override fun getTargetSdkVersion(): AndroidVersion {
    return manifestTargetSdk
      ?: run {
        val targetSdk = variant.targetSdkVersion ?: minSdkVersion
        manifestTargetSdk = targetSdk
        targetSdk
      }
  }

  override fun getBuildSdk(): Int {
    if (buildSdk == -1) {
      val compileTarget = model.compileTarget
      val version = AndroidTargetHash.getPlatformVersion(compileTarget)
      buildSdk = version?.featureLevel ?: super.getBuildSdk()
    }
    return buildSdk
  }

  override fun getBuildTargetHash(): String? {
    return model.compileTarget
  }

  override fun dependsOn(artifact: String): Boolean? {
    @Suppress("MoveVariableDeclarationIntoWhen") // also used in else
    val id = AndroidxNameUtils.getCoordinateMapping(artifact)
    return when (id) {
      ANDROIDX_APPCOMPAT_LIB_ARTIFACT -> {
        if (appCompat == null) {
          val a = variant.artifact
          appCompat =
            a.findCompileDependency(ANDROIDX_APPCOMPAT_LIB_ARTIFACT) != null ||
              a.findCompileDependency(APPCOMPAT_LIB_ARTIFACT) != null
        }
        appCompat
      }
      ANDROIDX_LEANBACK_ARTIFACT -> {
        if (leanback == null) {
          val a = variant.artifact
          leanback =
            a.findCompileDependency(ANDROIDX_LEANBACK_ARTIFACT) != null ||
              a.findCompileDependency(LEANBACK_V17_ARTIFACT) != null
        }
        leanback
      }
      else ->
        if (variant.artifact.findCompileDependency(artifact) != null) true else super.dependsOn(id)
    }
  }

  override fun getMergedManifest(): Document? {
    val manifest = variant.mergedManifest ?: return super.getMergedManifest()
    return try {
      val xml: String = Files.asCharSource(manifest, Charsets.UTF_8).read()
      val document = XmlUtils.parseDocumentSilently(xml, true)
      if (document == null) {
        client.log(null, "Could not read %1\$s", manifest)
        return null
      }
      // Note for later that we'll need to resolve locations from
      // the merged manifest
      val manifestMergeReport = variant.manifestMergeReport
      manifestMergeReport?.let { client.resolveMergeManifestSources(document, it) }
      document
    } catch (ioe: IOException) {
      client.log(ioe, "Could not read %1\$s", manifest)
      null
    }
  }

  override fun getPartialResultsDir(): File? {
    return variant.partialResultsDir ?: super.getPartialResultsDir()
  }

  override fun equals(other: Any?): Boolean {
    val thisPartialResultsDir = partialResultsDir
    val otherPartialResultDir = (other as? LintModelModuleProject)?.partialResultsDir
    val samePartialResultsDir =
      if (thisPartialResultsDir == null) {
        otherPartialResultDir == null
      } else {
        otherPartialResultDir != null &&
          FileUtils.isSameFile(thisPartialResultsDir, otherPartialResultDir)
      }

    return super.equals(other) && samePartialResultsDir
  }

  companion object {
    /**
     * Given a collection of model projects, set up the lint project dependency lists based on the
     * dependencies found in the underlying models.
     */
    @JvmStatic
    fun resolveDependencies(
      projects: Collection<LintModelModuleProject>,
      reporting: Boolean
    ): List<LintModelModuleProject> {
      // Record project names such that we can resolve dependencies once all the
      // projects have been initialized
      val projectMap: MutableMap<String, LintModelModuleProject> = HashMap()
      for (project in projects) {
        if (project.variant.artifact.type != LintModelArtifactType.MAIN) {
          continue
        }
        val module = project.model
        val modulePath = module.modulePath
        assert(projectMap[modulePath] == null)
        projectMap[modulePath] = project
      }

      for (project: LintModelModuleProject in projects) {
        val variant = project.buildVariant
        // Similar to dynamic features (below), we reverse the dependency between
        // test components and main components
        if (variant.artifact.type != LintModelArtifactType.MAIN) {
          val modulePath = variant.module.modulePath
          projectMap[modulePath]?.addDirectLibrary(project)
          continue
        }
        val roots = variant.artifact.dependencies.compileDependencies.roots
        for (dependency: LintModelDependency in roots) {
          val library = dependency.findLibrary()
          if (library is LintModelModuleLibrary) {
            val dependsOn = projectMap[library.projectPath]
            if (dependsOn != null) {
              if (
                reporting && project.type == DYNAMIC_FEATURE && dependsOn.type != DYNAMIC_FEATURE
              ) {
                // When reporting, reverse the dependencies such that
                // we treat the consuming app module as the root and we merge
                // dynamic feature lint results into it instead of the other way.
                dependsOn.addDirectLibrary(project)
              } else {
                project.addDirectLibrary(dependsOn)
              }
            }
          }
        }
      }

      val roots = LinkedHashSet<LintModelModuleProject>()
      projects.forEach { roots.add(it) }
      for (project: LintModelModuleProject in projects) {
        for (dependency in project.getDirectLibraries()) {
          roots.remove(dependency)
        }
      }

      return roots.toList()
    }
  }
}

/**
 * Adds all the jar files from this library into the given list, skipping provided libraries if
 * requested.
 */
fun LintModelExternalLibrary.addJars(list: MutableList<File>, skipProvided: Boolean) {
  if (skipProvided && provided) {
    return
  }

  for (jar in jarFiles) {
    if (!list.contains(jar)) {
      if (jar.exists()) {
        list.add(jar)
      }
    }
  }
}
