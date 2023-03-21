/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.screenshot.cli

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.tools.lint.CliConfiguration
import com.android.tools.lint.LintCliClient
import com.android.tools.lint.LintCliFlags
import com.android.tools.lint.ProjectMetadata
import com.android.tools.lint.client.api.Configuration
import com.android.tools.lint.client.api.ConfigurationHierarchy
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.LintRequest
import com.android.tools.lint.computeMetadata
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintModelModuleProject
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.guessGradleLocation
import com.android.tools.lint.model.LintModelModule
import com.android.tools.lint.model.PathVariables
import com.android.utils.XmlUtils
import com.google.common.io.ByteStreams
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.w3c.dom.Document
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.util.EnumSet
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

class Main {

    private var sdkHomePath: File? = null
    private var jdkHomePath: File? = null

    private val flags = LintCliFlags()
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Main().run(args)
        }
    }

    fun run(args: Array<String>) {
        val argumentState = ArgumentState()
        val client: LintCliClient = MainLintClient(flags, argumentState)
    }

    inner class ArgumentState {

        @Nullable
        var clientVersion: String? = null

        @Nullable
        var clientName: String? = null

        @Nullable
        var javaLanguageLevel: LanguageLevel? = null

        @Nullable
        var kotlinLanguageLevel: LanguageVersionSettings? = null

        @NonNull
        var modules: List<LintModelModule> = ArrayList()

        @Nullable
        var variantName: String? = null

        // Mapping from file path prefix to URL. Applies only to HTML reports
        @Nullable
        var urlMap: String? = null

        @NonNull
        var files: List<File> = ArrayList()

        @Nullable
        var pathVariables: PathVariables? = null

        @Nullable
        var desugaredMethodsPaths: List<String>? = null

        @NonNull
        var mode: LintDriver.DriverMode = LintDriver.DriverMode.GLOBAL
    }

    inner class MainLintClient(flags: LintCliFlags, private val argumentState: ArgumentState) :
        LintCliClient(flags, CLIENT_CLI) {

        private var unexpectedGradleProject: Project? = null

        override fun createDriver(
            registry: IssueRegistry, request: LintRequest
        ): LintDriver {
            val driver: LintDriver = super.createDriver(registry, request)
            val project: Project? = unexpectedGradleProject
            if (project != null) {
                val message = java.lang.String.format(
                    "\"`%1\$s`\" is a Gradle project. To correctly "
                            + "analyze Gradle projects, you should run \"`gradlew lint`\" "
                            + "instead.",
                    project.name
                )
                val location: Location = guessGradleLocation(this, project.dir, null)
                report(
                    this, IssueRegistry.LINT_ERROR, message, driver, project, location, null
                )
            }
            return driver
        }

        override fun createProject(dir: File, referenceDir: File): Project {
            val project: Project = super.createProject(dir, referenceDir)
            if (project.isGradleProject) {
                // Can't report error yet; stash it here so we can report it after the
                // driver has been created
                unexpectedGradleProject = project
            }
            return project
        }

        override fun getJavaLanguageLevel(project: Project): LanguageLevel {
            return argumentState.javaLanguageLevel ?: super.getJavaLanguageLevel(project)
        }

        override fun getKotlinLanguageLevel(project: Project): LanguageVersionSettings {
            return argumentState.kotlinLanguageLevel ?: super.getKotlinLanguageLevel(project)
        }

        override fun getConfiguration(
            project: Project, driver: LintDriver?
        ): Configuration {
            if (project.isGradleProject && project !is LintModelModuleProject) {
                // Don't report any issues when analyzing a Gradle project from the
                // non-Gradle runner; they are likely to be false, and will hide the
                // real problem reported above. We also need to turn off overrides
                // and fallbacks such that we don't inherit any re-enabled issues etc.
                val configurations: ConfigurationHierarchy = configurations
                configurations.overrides = null
                configurations.fallback = null
                return object : CliConfiguration(configurations, flags, true) {
                    override fun getDefinedSeverity(
                        issue: Issue,
                        source: Configuration,
                        visibleDefault: Severity
                    ): Severity {
                        return if (issue === IssueRegistry.LINT_ERROR) Severity.FATAL else Severity.IGNORE
                    }

                    override fun isIgnored(context: Context, incident: Incident): Boolean {
                        // If you've deliberately ignored IssueRegistry.LINT_ERROR
                        // don't flag that one either
                        val issue: Issue = incident.issue
                        if ((issue === IssueRegistry.LINT_ERROR
                                    && LintCliClient(flags, clientName)
                                .isSuppressed(IssueRegistry.LINT_ERROR))
                        ) {
                            return true
                        } else if ((issue === IssueRegistry.LINT_WARNING
                                    && LintCliClient(flags, clientName)
                                .isSuppressed(IssueRegistry.LINT_WARNING))
                        ) {
                            return true
                        }
                        return (issue !== IssueRegistry.LINT_ERROR
                                && issue !== IssueRegistry.LINT_WARNING)
                    }
                }
            }
            return super.getConfiguration(project, driver)
        }

        private fun readSrcJar(file: File): ByteArray? {
            val path = file.path
            val srcJarIndex = path.indexOf("srcjar!")
            if (srcJarIndex != -1) {
                val jarFile = File(path.substring(0, srcJarIndex + 6))
                if (jarFile.exists()) {
                    try {
                        ZipFile(jarFile).use { zipFile ->
                            val name: String =
                                path.substring(srcJarIndex + 8).replace(File.separatorChar, '/')
                            val entry: ZipEntry? = zipFile.getEntry(name)
                            if (entry != null) {
                                try {
                                    zipFile.getInputStream(entry).use { `is` ->
                                        return ByteStreams.toByteArray(
                                            `is`
                                        )
                                    }
                                } catch (e: Exception) {
                                    log(e, null)
                                }
                            }
                        }
                    } catch (e: ZipException) {
                        // com.android.tools.lint.Main.this.log(e, "Could not unzip %1$s", jarFile);
                    } catch (e: IOException) {
                        // com.android.tools.lint.Main.this.log(e, "Could not read %1$s", jarFile);
                    }
                }
            }
            return null
        }

        override fun readFile(file: File): CharSequence {
            // .srcjar file handle?
            val srcJarBytes = readSrcJar(file)
            return if (srcJarBytes != null) {
                String(srcJarBytes, Charsets.UTF_8)
            } else super.readFile(file)
        }

        @Throws(IOException::class)
        override fun readBytes(file: File): ByteArray {
            // .srcjar file handle?
            return (readSrcJar(file))!!
        }

        private var metadata: ProjectMetadata? = null
        override fun configureLintRequest(lintRequest: LintRequest) {
            super.configureLintRequest(lintRequest)
            val descriptor: File? = flags.projectDescriptorOverride
            if (descriptor != null) {
                metadata = computeMetadata(this, descriptor)
                val clientName: String? = metadata!!.clientName
                if (clientName != null) {
                    LintCliClient(clientName) // constructor has side effect
                }
                val projects: List<Project> = metadata!!.projects
                if (projects.isNotEmpty()) {
                    lintRequest.setProjects(projects)
                    if (metadata!!.sdk != null) {
                        sdkHomePath = metadata!!.sdk
                    }
                    if (metadata!!.jdk!= null) {
                        jdkHomePath = metadata!!.jdk
                    }
                    if (metadata!!.baseline != null) {
                        flags.baselineFile = metadata!!.baseline
                    }
                    val scope: EnumSet<Scope> = EnumSet.copyOf(Scope.ALL)
                    if (metadata!!.incomplete) {
                        scope.remove(Scope.ALL_CLASS_FILES)
                        scope.remove(Scope.ALL_JAVA_FILES)
                        scope.remove(Scope.ALL_RESOURCE_FILES)
                    }
                    lintRequest.setScope(scope)
                    lintRequest.setPlatform(metadata!!.platforms)
                }
            }
        }

        override fun findRuleJars(project: Project): Iterable<File> {
            if (metadata != null) {
                val jars: List<File>? = metadata!!.lintChecks[project]
                if (jars != null) {
                    return jars
                }
            }
            return super.findRuleJars(project)
        }

        override fun findGlobalRuleJars(driver: LintDriver?, warnDeprecated: Boolean): List<File> {
            if (metadata != null) {
                val jars: List<File> = metadata!!.globalLintChecks
                if (jars.isNotEmpty()) {
                    return jars
                }
            }
            return super.findGlobalRuleJars(driver, warnDeprecated)
        }

        override fun getCacheDir(name: String?, create: Boolean): File? {
            if (metadata != null) {
                var dir: File? = metadata!!.cache
                if (dir != null) {
                    if (name != null) {
                        dir = File(dir, name)
                    }
                    if (create && !dir.exists()) {
                        if (!dir.mkdirs()) {
                            return null
                        }
                    }
                    return dir
                }
            }
            return super.getCacheDir(name, create)
        }

        override fun getMergedManifest(project: Project): Document? {
            if (metadata != null) {
                val manifest: File? = metadata!!.mergedManifests[project]
                if (manifest != null && manifest.exists()) {
                    try {
                        // We can't call
                        //   resolveMergeManifestSources(document, manifestReportFile)
                        // here since we don't have the merging log.
                        return XmlUtils.parseUtfXmlFile(manifest, true)
                    } catch (e: IOException) {
                        log(e, "Could not read/parse %1\$s", manifest)
                    } catch (e: SAXException) {
                        log(e, "Could not read/parse %1\$s", manifest)
                    }
                }
            }
            return super.getMergedManifest(project)
        }

        override fun getSdkHome(): File? {
            return if (sdkHomePath != null) {
                sdkHomePath
            } else super.getSdkHome()
        }

        override fun getJdkHome(project: Project?): File? {
            return if (jdkHomePath != null) {
                jdkHomePath
            } else super.getJdkHome(project)
        }

        override fun addBootClassPath(
            knownProjects: Collection<Project>, files: MutableSet<File>
        ): Boolean {
            if (metadata != null && metadata!!.jdkBootClasspath.isNotEmpty()) {
                var isAndroid = false
                for (project in knownProjects) {
                    if (project.isAndroidProject) {
                        isAndroid = true
                        break
                    }
                }
                if (!isAndroid) {
                    files.addAll(metadata!!.jdkBootClasspath)
                    return true
                }
                val ok: Boolean = super.addBootClassPath(knownProjects, files)
                if (!ok) {
                    files.addAll(metadata!!.jdkBootClasspath)
                }
                return ok
            }
            return super.addBootClassPath(knownProjects, files)
        }

        override fun getExternalAnnotations(projects: Collection<Project>): List<File> {
            val externalAnnotations: MutableList<File> = super.getExternalAnnotations(projects).toMutableList()
            if (metadata != null) {
                externalAnnotations.addAll(metadata!!.externalAnnotations)
            }
            return externalAnnotations
        }
    }
}
