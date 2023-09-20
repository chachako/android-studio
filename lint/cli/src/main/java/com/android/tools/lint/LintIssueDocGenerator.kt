/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.SdkConstants.DOT_AAR
import com.android.SdkConstants.DOT_CLASS
import com.android.SdkConstants.DOT_GRADLE
import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.DOT_KTS
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.DOT_ZIP
import com.android.SdkConstants.FN_LINT_JAR
import com.android.SdkConstants.TAG_MANIFEST
import com.android.SdkConstants.TAG_RESOURCES
import com.android.ide.common.gradle.Version
import com.android.ide.common.repository.GoogleMavenRepository
import com.android.ide.common.repository.GradleCoordinate
import com.android.resources.ResourceFolderType
import com.android.support.AndroidxNameUtils
import com.android.tools.lint.LintCliClient.Companion.printWriter
import com.android.tools.lint.LintCliFlags.ERRNO_ERRORS
import com.android.tools.lint.LintCliFlags.ERRNO_SUCCESS
import com.android.tools.lint.LintCliFlags.ERRNO_USAGE
import com.android.tools.lint.checks.BuiltinIssueRegistry
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.JarFileIssueRegistry
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintJarVerifier
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Option
import com.android.tools.lint.detector.api.Platform
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.describeApi
import com.android.tools.lint.detector.api.formatList
import com.android.tools.lint.detector.api.readUrlData
import com.android.tools.lint.detector.api.readUrlDataAsString
import com.android.tools.lint.detector.api.splitPath
import com.android.utils.SdkUtils.wrap
import com.android.utils.XmlUtils
import com.android.utils.iterator
import com.google.common.io.ByteStreams
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.psi.PsiManager
import java.io.ByteArrayInputStream
import java.io.File
import java.io.File.pathSeparator
import java.io.File.separator
import java.io.File.separatorChar
import java.io.FileNotFoundException
import java.io.IOException
import java.io.PrintWriter
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Date
import java.util.EnumSet
import java.util.Locale
import java.util.TreeMap
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import java.util.regex.Pattern
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.w3c.dom.Element

/**
 * Generates markdown pages documenting lint checks.
 *
 * There is more documentation for this tool in lint/docs/internal/document-checks.md.html
 */
class LintIssueDocGenerator(
  private val output: File,
  private val registryMap: Map<IssueRegistry, String?>,
  private val onlyIssues: List<String>,
  private val singleDoc: Boolean,
  private val format: DocFormat,
  private val includeStats: Boolean,
  private val sourcePath: Map<String, List<File>>,
  private val testPath: Map<String, List<File>>,
  private val includeIndices: Boolean,
  private val includeSuppressInfo: Boolean,
  private val includeExamples: Boolean,
  private val includeSourceLinks: Boolean,
  private val includeSeverityColor: Boolean
) {
  private val allIssues: List<Issue> = registryMap.keys.flatMap { it.issues }

  private val issueToRegistry: Map<String, List<IssueRegistry>> =
    registryMap.keys
      .flatMap { registry -> registry.issues.map { issue -> issue.id to registry } }
      .groupBy({ it.first }, { it.second })

  private val aliasMap: Map<IssueRegistry, Map<String, Issue>>

  init {
    val map = mutableMapOf<IssueRegistry, MutableMap<String, Issue>>()
    for (registry in registryMap.keys) {
      val aliases = mutableMapOf<String, Issue>()
      for (issue in registry.issues) {
        issue.getAliases()?.forEach { aliases[it] = issue }
      }
      map[registry] = aliases
    }
    this.aliasMap = map
  }

  private val singleIssueDetectors: Set<Issue>

  init {
    val all = mutableMapOf<Class<*>, Int>()
    val singleIssueDetectors: MutableSet<Issue> = mutableSetOf()
    for (registry in registryMap.keys) {
      for (issue in registry.issues) {
        val clz = issue.implementation.detectorClass
        all[clz] = (all[clz] ?: 0) + 1
      }
      for (issue in registry.issues) {
        val clz = issue.implementation.detectorClass
        val count = all[clz] ?: 0
        if (count == 1) {
          singleIssueDetectors.add(issue)
        }
      }
    }
    this.singleIssueDetectors = singleIssueDetectors
  }

  private val knownIds: Set<String> = allIssues.map { it.id }.toSet()
  private val issues = allIssues.filter { !skipIssue(it) }.toList()
  private var environment: UastEnvironment = createUastEnvironment()
  private val issueMap = analyzeSource()

  fun generate() {
    checkIssueFilter()

    if (singleDoc) {
      writeSinglePage()
    } else {
      for (issue in issues.filter { !skipIssue(it) }) {
        writeIssuePage(issue)
      }

      if (includeIndices) {
        for (type in IndexType.values()) {
          writeIndexPage(type)
        }
      }

      for ((registry, artifact) in registryMap) {
        artifact ?: continue // e.g. built-in checks; no artifact page
        writeArtifactPage(registry, artifact, registry.vendor)
      }

      if (includeStats) {
        writeStatsPage()
      }

      for (registry in registryMap.keys) {
        for (id in registry.deletedIssues.filter { !skipIssue(it) }) {
          writeDeletedIssuePage(registry, id)
        }
      }
    }

    disposeUastEnvironment()
  }

  private fun checkIssueFilter() {
    for (id in onlyIssues) {
      if (!knownIds.contains(id)) {
        println("Warning: The issue registry does not contain an issue with the id `$id`")
      }
    }
  }

  private fun skipIssue(issue: Issue): Boolean = skipIssue(issue.id)

  private fun skipIssue(id: String): Boolean {
    return onlyIssues.isNotEmpty() && !onlyIssues.contains(id)
  }

  private fun writeSinglePage() {
    val sb = StringBuilder()
    sb.append(format.header)

    sb.append("# Lint Issues\n")

    if (registryMap.size == 1 && registryMap.keys.first() is BuiltinIssueRegistry) {
      sb.append(
        "This document lists the built-in issues for Lint. Note that lint also reads additional\n"
      )
      sb.append(
        "checks directly bundled with libraries, so this is a subset of the checks lint will\n"
      )
      sb.append("perform.\n")
    }

    val categories = getCategories(issues)

    if (includeStats) {
      writeStats(sb, categories, issues)
    }

    for (category in categories.keys.sorted()) {
      sb.append("\n## ${category.fullName}\n\n")
      val categoryIssues = categories[category]?.sorted() ?: continue
      for (issue in categoryIssues) {
        describeIssue(sb, issue)
      }
    }

    sb.append(format.footer)
    output.writeText(sb.trim().toString())
  }

  private fun getCategories(issues: List<Issue>): HashMap<Category, MutableList<Issue>> {
    val categories = HashMap<Category, MutableList<Issue>>()
    for (issue in issues) {
      val category = issue.category
      val list = categories[category] ?: ArrayList<Issue>().also { categories[category] = it }
      list.add(issue)
    }
    return categories
  }

  private fun getVendors(issues: List<Issue>): HashMap<Vendor, MutableList<Issue>> {
    val unknown = Vendor("Unknown Vendor")
    val vendors = HashMap<Vendor, MutableList<Issue>>()
    for (issue in issues) {
      val vendor = issue.vendor ?: issue.registry?.vendor ?: unknown
      val list = vendors[vendor] ?: ArrayList<Issue>().also { vendors[vendor] = it }
      list.add(issue)
    }
    return vendors
  }

  private fun getYears(issues: List<Issue>): HashMap<Int, MutableList<Issue>> {
    val years = HashMap<Int, MutableList<Issue>>()
    for (issue in issues) {
      val year = issueMap[issue.id]?.copyrightYear ?: -1
      val list = years[year] ?: ArrayList<Issue>().also { years[year] = it }
      list.add(issue)
    }
    return years
  }

  private fun writeStats(
    sb: StringBuilder,
    categories: HashMap<Category, MutableList<Issue>>,
    issues: List<Issue>
  ) {
    sb.append("\n## Vital Stats\n")

    sb.append(
      "Current As Of\n:   ${DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDateTime.now())}\n"
    )
    sb.append("Category Count\n:   ${categories.keys.size}\n")
    sb.append("Issue Count\n:   ${issues.size}\n")
    sb.append(
      "Informational\n:   ${issues.count { it.defaultSeverity == Severity.INFORMATIONAL }}\n"
    )
    sb.append("Warnings\n:   ${issues.count { it.defaultSeverity == Severity.WARNING }}\n")
    sb.append("Errors\n:   ${issues.count { it.defaultSeverity == Severity.ERROR }}\n")
    sb.append("Fatal\n:   ${issues.count { it.defaultSeverity == Severity.FATAL }}\n")
    sb.append("Disabled By Default\n:   ${issues.count { !it.isEnabledByDefault() }}\n")
    sb.append("Android Specific\n:   ${issues.count { it.isAndroidSpecific() }}\n")
    sb.append("General\n:   ${issues.count { !it.isAndroidSpecific() }}\n")
  }

  private fun wrap(text: String): String {
    val lineWidth = 72
    val lines = text.split("\n")
    val sb = StringBuilder()
    var inPreformat = false
    var prev = ""
    for (line in lines) {
      if (line.startsWith("```")) {
        inPreformat = !inPreformat
        sb.append(line).append('\n')
      } else if (inPreformat) {
        sb.append(line).append('\n')
      } else if (line.isBlank()) {
        sb.append('\n')
      } else if (line.length < lineWidth) {
        sb.append(line).append('\n')
      } else {
        if (line.isListItem()) {
          if (format == DocFormat.MARKDEEP && prev.isNotBlank() && !prev.isListItem()) {
            sb.append('\n')
          }
          val hangingIndent = "  "
          val nextLineWidth = lineWidth - hangingIndent.length
          sb.append(wrap(line, lineWidth, nextLineWidth, hangingIndent, false))
        } else {
          sb.append(wrap(line, lineWidth, lineWidth, "", false))
        }
      }
      prev = line
    }

    return sb.toString()
  }

  private fun writeIssuePage(issue: Issue) {
    assert(!singleDoc)
    val file = File(output, getFileName(issue, format))
    val sb = StringBuilder(1000)
    sb.append(format.header)
    describeIssue(sb, issue)
    sb.append("\n")
    sb.append(format.footer)
    file.writeText(sb.trim().toString())
  }

  private fun writeDeletedIssuePage(registry: IssueRegistry, id: String) {
    assert(!singleDoc)
    val file = File(output, getFileName(id, format))
    val sb = StringBuilder(1000)
    sb.append(format.header)
    if (format == DocFormat.MARKDEEP) {
      sb.append("(#)")
    } else {
      sb.append("#")
    }
    sb.append(" $id\n\n")

    val now = aliasMap[registry]?.get(id)

    if (now != null) {
      sb.append(wrap("This issue id is an alias for [${now.id}](${now.id}${format.extension})."))
    } else {
      sb.append(
        wrap(
          "The issue for this id has been deleted or marked obsolete and can " + "now be ignored."
        )
      )
    }

    registry.vendor?.let { vendor ->
      vendor.vendorName?.let { "Vendor: $it\n" }
      vendor.identifier?.let { "Identifier: $it\n" }
      vendor.contact?.let { "Contact: $it\n" }
      vendor.feedbackUrl?.let { "Feedback: $it\n" }
    }
    sb.append("\n(Additional metadata not available.)\n")
    sb.append(format.footer)
    file.writeText(sb.trim().toString())
  }

  private enum class IndexType(val label: String, val filename: String) {
    ALPHABETICAL("Alphabetical", "index"),
    CATEGORY("By category", "categories"),
    VENDOR("By vendor", "vendors"),
    SEVERITY("By severity", "severity"),
    YEAR("By year", "year"),
    ARTIFACTS("Libraries", "libraries")
  }

  private fun getXmlValue(element: Element?, vararg path: String): String? {
    var current = element ?: return null
    loop@ for (name in path) {
      for (child in current) {
        val childTag = child.tagName
        if (childTag == name) {
          current = child
          continue@loop
        }
      }
      return null
    }

    return current.textContent.trim()
  }

  private fun writeArtifactPage(registry: IssueRegistry, artifact: String, vendor: Vendor?) {
    val library = (registry as? DocIssueRegistry)?.library ?: return

    val sb = StringBuilder(1000)

    val name: String?
    val description: String?
    val license: String?

    val pom = library.versions.values.firstOrNull()?.pom ?: ""
    if (pom.isNotBlank()) {
      val document = XmlUtils.parseDocumentSilently(pom, true)?.documentElement
      name = getXmlValue(document, "name")
      description =
        getXmlValue(document, "description")?.let {
          wrap(it).trim().split("\n").joinToString("\n:   ")
        }
      val licenseName = getXmlValue(document, "licenses", "license", "name")
      val licenseUrl = getXmlValue(document, "licenses", "license", "url")
      license =
        if (licenseName != null && licenseUrl != null) {
          "[$licenseName]($licenseUrl)"
        } else {
          licenseName
        }
    } else {
      name = null
      description = null
      license = null
    }

    val projectUrl = (library as? MavenCentralLibrary)?.projectUrl?.ifBlank { null }

    // TODO: Convert TextFormat.RAW to TextFormat.MARKDEEP
    val isMarkDeep = this.format == DocFormat.MARKDEEP
    val vendorInfo =
      listOfNotNull(
          name?.let { "Name" to escapeXml(it) },
          description?.let { "Description" to escapeXml(it) },
          projectUrl?.let { "Project" to escapeXml(it) },
          license?.let { "License" to escapeXml(it) },
          vendor?.vendorName?.let { "Vendor" to it },
          vendor?.identifier?.let { "Identifier" to it },
          vendor?.contact?.let {
            if (vendor != IssueRegistry.AOSP_VENDOR) "Contact" to it else null
          },
          vendor?.feedbackUrl?.let { "Feedback" to it },
        )
        .toTypedArray()
    val artifactMap = arrayOf("Artifact" to artifact)
    val atLeastMap =
      arrayOf(
        "Min" to "Lint ${describeApi(registry.minApi)}",
        "Compiled" to "Lint ${describeApi(registry.api)}"
      )

    val array = arrayOf(*vendorInfo, *atLeastMap, *artifactMap)

    val table = if (isMarkDeep) markdeepTable(*array) else markdownTable(*array)
    if (singleDoc) {
      sb.append("###")
    } else {
      if (isMarkDeep) {
        sb.append("(#)")
      } else {
        sb.append("#")
      }
    }

    val artifactName = artifact.substringBeforeLast(':')
    sb.append(" $artifactName\n\n")
    sb.append(table)
    sb.append("\n")

    sb.append("(##) Included Issues\n\n")

    val issues = mutableListOf<Pair<String, String>>()
    var keyWidth = 0
    var valueWidth = 0
    for (issue in registry.issues) {
      val key = "[${issue.id}](${getFileName(issue, format)})"
      val value = issue.getBriefDescription(TextFormat.RAW)
      keyWidth = max(keyWidth, key.length)
      valueWidth = max(valueWidth, value.length)
      issues.add(Pair(key, value))
    }
    sb.append("|%-${keyWidth}s|%-${valueWidth}s|\n".format("Issue Id", "Issue Description"))
    sb.append("|${"-".repeat(keyWidth)}|${"-".repeat(valueWidth)}|\n")
    for ((key, value) in issues) {
      sb.append("|%-${keyWidth}s|%-${valueWidth}s|\n".format(key, value))
    }
    sb.append("\n")
    addLibraryInclude(sb, artifact, library.lintLibrary)
    sb.append("\n\n")

    if (library.versions.size > 1) {
      val versionTable = StringBuilder()

      versionTable.append(
        "" +
          "| Version            | Date     | Issues | Compatible | Compiled      | Requires |\n" +
          "|-------------------:|----------|-------:|------------|--------------:|---------:|\n"
      )

      val client = createClient()

      var incompatibilityNumber = 0
      val incompatibilities = mutableMapOf<String, Int>()

      val versionIssues = mutableListOf<Pair<String, List<String>>>()

      for ((v, entry) in registry.library.versions.asSequence().sortedByDescending { it.key }) {
        val versionString = v.toString()
        val bytes = entry.jarBytes
        val file = File.createTempFile(artifact, DOT_JAR)
        file.deleteOnExit()
        file.writeBytes(bytes)
        val r =
          JarFileIssueRegistry.get(client, listOf(file), skipVerification = true).firstOrNull()
            ?: continue

        versionIssues.add(versionString to r.issues.map { it.id }.sorted())

        val verifier = LintJarVerifier(client, file, bytes)
        val compatible =
          if (verifier.isCompatible()) {
            "Yes"
          } else {
            val message =
              verifier.describeFirstIncompatibleReference() +
                if (verifier.isInaccessible()) {
                  " is not accessible"
                } else {
                  " is not available"
                }
            val num =
              incompatibilities[message]
                ?: run {
                  incompatibilities[message] = ++incompatibilityNumber
                  incompatibilityNumber
                }

            "No[^$num]"
          }

        // Also check bytecode level; when built with recent versions of Android
        var minApiFromBytecodeLevel = -1
        val registries = JarFileIssueRegistry.findRegistries(client, listOf(file))
        if (registries.size == 1) {
          JarFile(file).use {
            val registryClass = registries.keys.first()
            val registryEntry = it.getJarEntry(registryClass.replace('.', '/') + DOT_CLASS)
            if (registryEntry != null) {
              val stream = it.getInputStream(registryEntry)
              val registryBytes = stream.readAllBytes()
              if (registryBytes.size >= 8) {
                val bytecodeLevel = (registryBytes[6].toInt() shl 8) + registryBytes[7].toInt()
                if (bytecodeLevel >= 61) { // 61: Java 17
                  minApiFromBytecodeLevel = 14 // lint api level 14: AGP 8.0
                }
                // We could also check for JDK 11 (api level 11), since very old versions
                // of lint can't handle that, but those versions aren't relevant anymore at this
                // point.
              }
            }
          }
        }

        try {
          versionTable.append(
            "|%20s|%10s|%8s|%12s|%15s|%10s|\n"
              .format(
                v.toString(),
                entry.date,
                r.issues.size.toString(),
                compatible,
                describeApi(r.api),
                describeApi(max(r.minApi, minApiFromBytecodeLevel)),
              )
          )
        } catch (ignore: Throwable) {}
      }

      if (incompatibilities.isNotEmpty()) {
        versionTable.append("\nCompatibility Problems:\n\n")
        for ((message, number) in incompatibilities.entries.sortedBy { it.value }) {
          versionTable.append("[^$number]: $message  \n")
        }
      }

      sb.append("(##) Changes\n\n")
      var prev = emptyList<String>()

      versionIssues.reverse()

      for ((versionString, versionIds) in versionIssues) {
        val added = versionIds.filter { !prev.contains(it) }
        val removed = prev.filter { !versionIds.contains(it) }
        if (added.isNotEmpty() || removed.isNotEmpty()) {
          val itemString = StringBuilder("* $versionString:")
          if (prev.isEmpty()) {
            itemString.append(" First version includes ${added.joinToString()}.")
          } else {
            if (added.isNotEmpty()) {
              itemString.append(" Adds ${added.joinToString()}.")
            }
            if (removed.isNotEmpty()) {
              itemString.append(" Removes ${removed.joinToString()}.")
            }
          }
          sb.append(wrap(itemString.toString()))
        }

        prev = versionIds
      }

      sb.append("\n(##) Version Compatibility\n\n")
      sb.append("There are multiple older versions available of this library:\n\n")
      sb.append(versionTable.toString())
    }

    sb.append("\n")

    sb.append(format.footer)
    val outputFile = File(output, getArtifactPageName(library.id))
    outputFile.writeText(sb.trim().toString())
  }

  private fun writeIndexPage(type: IndexType) {
    val sb = StringBuilder()
    sb.append(format.header)
    if (format == DocFormat.MARKDEEP) {
      sb.append("(#) ")
    } else {
      sb.append("# ")
    }
    sb.append("Lint Issue Index\n\n")
    sb.append("Order: ")

    val bullet = "* "

    for (t in IndexType.values()) {
      if (t != type) {
        sb.append("[${t.label}]")
        sb.append("(${t.filename}${format.extension})")
      } else {
        sb.append(t.label)
      }
      sb.append(" | ")
    }
    sb.setLength(sb.length - 3) // truncate last " | "
    sb.append("\n")

    if (type == IndexType.CATEGORY) {
      val categories = getCategories(issues)
      for (category in categories.keys.sorted()) {
        val categoryIssues = categories[category]?.sorted() ?: continue
        sb.append("\n$bullet${category.fullName.replace(":", ": ")} (${categoryIssues.size})\n\n")
        for (issue in categoryIssues) {
          val id = issue.id
          val summary = issue.getBriefDescription(TextFormat.RAW)
          sb.append("  - [$id: $summary]($id${format.extension})\n")
        }
      }
    } else if (type == IndexType.ALPHABETICAL) {
      sb.append("\n")
      for (issue in issues.sortedBy { it.id }) {
        val id = issue.id
        val summary = issue.getBriefDescription(TextFormat.RAW)
        sb.append("  - [$id: $summary]($id${format.extension})")
        val registries = issueToRegistry[id]
        val artifact = issue.registry?.let { registryMap[it] }
        if (artifact != null && registries != null && registries.size > 1) {
          sb.append(" (from $artifact)")
        }
        sb.append("\n")
      }
    } else if (type == IndexType.SEVERITY) {
      for (severity in Severity.values().reversed()) {
        val applicable = issues.filter { it.defaultSeverity == severity }.toList()
        if (applicable.isNotEmpty()) {
          sb.append("\n$bullet${severity.description} (${applicable.size})\n\n")
          for (issue in applicable.sorted()) {
            val id = issue.id
            val summary = issue.getBriefDescription(TextFormat.RAW)
            sb.append("  - [$id: $summary]($id${format.extension})\n")
          }
        }
      }

      val disabled = issues.filter { !it.isEnabledByDefault() }.sortedBy { it.id }.toList()
      if (disabled.isNotEmpty()) {
        sb.append("\n${bullet}Disabled By Default (${disabled.size})\n\n")
        for (id in disabled) {
          sb.append("  - [$id]($id${format.extension})\n")
        }
      }
    } else if (type == IndexType.VENDOR) {
      val vendors = getVendors(issues)
      for (vendor in vendors.keys.sortedBy { it.describe(TextFormat.RAW).lowercase(Locale.US) }) {
        val vendorIssues = vendors[vendor]?.sortedBy { it.id } ?: continue
        val vendorName = vendor.vendorName
        val identifier = vendor.identifier
        sb.append("\n$bullet")
        if (
          vendor == IssueRegistry.AOSP_VENDOR &&
            vendors[vendor]?.first()?.registry is BuiltinIssueRegistry
        ) {
          sb.append("Built In (${vendorIssues.size})")
        } else {
          sb.append(vendorName ?: vendor.contact ?: identifier ?: vendor.feedbackUrl)
          if (vendorName != null && identifier != null && !vendorName.contains(identifier)) {
            sb.append(" ($identifier)")
          }
          sb.append(" (${vendorIssues.size})")
        }
        sb.append("\n\n")
        for (issue in vendorIssues) {
          val id = issue.id
          val summary = issue.getBriefDescription(TextFormat.RAW)
          sb.append("  - [$id: $summary]($id${format.extension})\n")
        }
      }
    } else if (type == IndexType.YEAR) {
      val years = getYears(issues)
      for (year in years.keys.sortedByDescending { it }) {
        val issuesFromYear = years[year]?.sortedBy { it.id } ?: continue
        sb.append("\n$bullet")
        if (year == -1) {
          sb.append("Unknown (${issuesFromYear.size})")
        } else {
          sb.append("$year (${issuesFromYear.size})")
        }
        sb.append("\n\n")
        for (issue in issuesFromYear) {
          val id = issue.id
          val summary = issue.getBriefDescription(TextFormat.RAW)
          sb.append("  - [$id: $summary]($id${format.extension})\n")
        }
      }
    } else if (type == IndexType.ARTIFACTS) {
      val aars = mutableListOf<DocIssueRegistry>()
      val jars = mutableListOf<DocIssueRegistry>()
      for (registry in registryMap.keys) {
        if (registry is DocIssueRegistry) {
          val library = registry.library
          val list =
            if (library.lintLibrary || library.id.contains("-lint") || library.id.contains(":lint"))
              jars
            else aars
          list.add(registry)
        }
      }

      fun listLibraries(registries: List<DocIssueRegistry>) {
        for (registry in registries) {
          sb.append("\n$bullet")
          val count = registry.issues.size
          val name = registry.library.id
          sb.append("[$name](${getArtifactPageName(name)}) ($count checks)")
        }
        sb.append("\n\n")
      }

      if (jars.isNotEmpty() || aars.isNotEmpty()) {
        sb.append("\n")
      }

      if (jars.isNotEmpty()) {
        sb.append("Lint-specific libraries:\n")
        listLibraries(jars)
      }
      if (aars.isNotEmpty()) {
        sb.append("Android archive libraries which also contain bundled lint checks:\n")
        listLibraries(aars)
      }
    }

    if (type != IndexType.ARTIFACTS) {
      for (registry in registryMap.keys) {
        val deleted = registry.deletedIssues.filter { !skipIssue(it) }
        if (deleted.isNotEmpty()) {
          sb.append("\n${bullet}Withdrawn or Obsolete Issues (${deleted.size})\n\n")
          for (id in deleted) {
            sb.append("  - [$id]($id${format.extension})\n")
          }
        }
      }
    }

    sb.append(format.footer)
    File(output, "${type.filename}${format.extension}").writeText(sb.trim().toString())
  }

  private fun writeStatsPage() {
    val categories = getCategories(issues)
    val sb = StringBuilder()
    sb.append(format.header)
    writeStats(sb, categories, issues)
    sb.append(format.footer)
    File(output, "stats${format.extension}").writeText(sb.trim().toString().trimIndent())
  }

  @Suppress("DefaultLocale")
  private fun describeIssue(sb: StringBuilder, issue: Issue) {
    val id = issue.id
    val vendor = issue.vendor ?: issue.registry?.vendor
    val implementation = issue.implementation

    // TODO: Convert TextFormat.RAW to TextFormat.MARKDEEP
    val isMarkDeep = this.format == DocFormat.MARKDEEP
    val format = TextFormat.RAW
    val description = issue.getBriefDescription(format)

    val enabledByDefault = issue.isEnabledByDefault()
    // Note that priority isn't very useful in practice (the one remaining
    // use is to include it in result sorting) so we don't include it here
    val severity = issue.defaultSeverity.description
    val category = issue.category.fullName.replace(":", ": ")
    val vendorInfo =
      listOfNotNull(
          vendor?.vendorName?.let { "Vendor" to it },
          vendor?.identifier?.let { "Identifier" to it },
          vendor?.contact?.let {
            if (vendor != IssueRegistry.AOSP_VENDOR) "Contact" to it else null
          },
          vendor?.feedbackUrl?.let { "Feedback" to it },
        )
        .toTypedArray()
    val explanation =
      issue.getExplanation(format).let {
        if (it.trimEnd().lastOrNull()?.isLetter() == true) {
          "$it."
        } else {
          it
        }
      }
    val copyrightYear = issueMap[issue.id]?.copyrightYear ?: -1
    val copyrightYearInfo =
      if (copyrightYear != -1) {
        arrayOf("Copyright Year" to copyrightYear.toString())
      } else {
        emptyArray()
      }

    val platforms =
      when (issue.platforms) {
        Platform.ANDROID_SET -> "Android"
        Platform.JDK_SET -> "JDK"
        else -> "Any"
      }

    val appliesTo = mutableListOf<String>()
    val scope = implementation.scope
    for (type in scope) {
      val desc =
        when (type!!) {
          Scope.ALL_RESOURCE_FILES,
          Scope.RESOURCE_FILE -> "resource files"
          Scope.BINARY_RESOURCE_FILE -> "binary resource files"
          Scope.RESOURCE_FOLDER -> "resource folders"
          Scope.ALL_JAVA_FILES,
          Scope.JAVA_FILE -> "Kotlin and Java files"
          Scope.ALL_CLASS_FILES,
          Scope.CLASS_FILE -> "class files"
          Scope.TOML_FILE -> "TOML files"
          Scope.MANIFEST -> "manifest files"
          Scope.PROGUARD_FILE -> "shrinking configuration files"
          Scope.JAVA_LIBRARIES -> "library bytecode"
          Scope.GRADLE_FILE -> "Gradle build files"
          Scope.PROPERTY_FILE -> "property files"
          Scope.TEST_SOURCES -> "test sources"
          Scope.OTHER -> continue
        }
      if (!appliesTo.contains(desc)) {
        appliesTo.add(desc)
      }
    }
    val scopeDescription =
      if (!scope.contains(Scope.OTHER) && scope != Scope.ALL) {
        formatList(appliesTo, useConjunction = true).replaceFirstChar(Char::titlecase)
      } else {
        null
      }

    val enable =
      if (!enabledByDefault) {
        "**This issue is disabled by default**; use `--enable $id`"
      } else {
        null
      }

    val onTheFly = canAnalyzeInEditor(issue)
    val inEditor =
      if (onTheFly) "This check runs on the fly in the IDE editor"
      else "This check can *not* run live in the IDE editor"

    val moreInfo = issue.moreInfo
    if (moreInfo.isNotEmpty()) {
      // Ensure that the links are unique
      if (moreInfo.size != moreInfo.toSet().size) {
        println("Warning: Multiple identical moreInfo links for issue $id")
      }
    }

    val registry = issue.registry
    val library = (registry as? DocIssueRegistry)?.library
    val deleted = registry?.deletedIssues ?: emptyList()
    val aliasMap = registry?.let { aliasMap[it] } ?: emptyMap()
    val aliases =
      aliasMap
        .filter { it.value == issue }
        .map {
          val name = if (deleted.contains(it.key)) "Previously" else "Alias"
          Pair(name, it.key)
        }
        .toTypedArray()
    val moreInfoUrls = moreInfo.map { Pair("See", it) }.toTypedArray()
    val sourceUrls = if (includeSourceLinks) findSourceFiles(issue) else emptyArray()
    val artifact = registry?.let { registryMap[it] }
    val artifactId = artifact?.substringBeforeLast(":")

    val artifactMap =
      if (artifactId != null) {
        val artifactUrl = "[$artifactId](${getArtifactPageName(artifactId)})\n"
        arrayOf("Artifact" to artifactUrl)
      } else emptyArray()
    val atLeastMap =
      if (registry !is BuiltinIssueRegistry && registry != null) {
        arrayOf(
          "Min" to "Lint ${describeApi(registry.minApi)}",
          "Compiled" to "Lint ${describeApi(registry.api)}"
        )
      } else {
        emptyArray()
      }

    val array =
      arrayOf(
        "Id" to "`$id`",
        *aliases,
        "Summary" to description,
        "Note" to enable,
        "Severity" to severity,
        "Category" to category,
        "Platform" to platforms,
        *vendorInfo,
        *atLeastMap,
        *artifactMap,
        "Affects" to scopeDescription,
        "Editing" to inEditor,
        *moreInfoUrls,
        *sourceUrls,
        *copyrightYearInfo
      )

    val table = if (isMarkDeep) markdeepTable(*array) else markdownTable(*array)
    if (singleDoc) {
      sb.append("###")
    } else {
      if (isMarkDeep) {
        sb.append("(#)")
      } else {
        sb.append("#")
      }
    }
    sb.append(" $description\n")
    sb.append("\n")

    if (isMarkDeep && includeSeverityColor) {
      when (issue.defaultSeverity) {
        Severity.FATAL ->
          sb.append(
            """
                        !!! ERROR: $description
                           This is an error, and is also enforced at build time when
                           supported by the build system. For Android this means it will
                           run during release builds.
                    """
              .trimIndent()
          )
        Severity.ERROR ->
          sb.append(
            """
                        !!! ERROR: $description
                           This is an error.
                    """
              .trimIndent()
          )
        Severity.WARNING ->
          sb.append(
            """
                        !!! WARNING: $description
                           This is a warning.
                    """
              .trimIndent()
          )
        Severity.INFORMATIONAL ->
          sb.append(
            """
                        !!! Tip: $description
                           Advice from this check is just a tip.
                    """
              .trimIndent()
          )
        Severity.IGNORE -> {}
      }
      sb.append("\n\n")
    }

    sb.append(table)
    sb.append("\n")

    sb.append(wrap(explanation))
    sb.append("\n")

    val issueData = issueMap[id]
    if (issueData?.quickFixable == true) {
      sb.append(
        "!!! Tip\n" + "   This lint check has an associated quickfix available in the IDE.\n\n"
      )
    }

    val options = issue.getOptions()
    if (options.isNotEmpty()) {
      writeOptions(sb, issue, options)
    }

    if (includeExamples) {
      issueData?.example?.let { example ->
        writeExample(sb, issueData, example, issue)
        sb.append("\n")
      }
    }

    val others = issueToRegistry[id] ?: emptyList()
    if (others.size > 1) {
      val registryNames = mutableMapOf<IssueRegistry, String>()
      for (r in others) {
        if (r !is BuiltinIssueRegistry) {
          val desc =
            registryMap[r]?.let { "$id from $it" }
              ?: if (vendor?.vendorName != null && vendor.identifier != null)
                "$id from ${vendor.vendorName} ${vendor.identifier}"
              else null ?: (vendor?.vendorName ?: vendor?.identifier)?.let { "$id from $it" }
          desc?.let { registryNames[r] = it }
        }
      }

      val detectorClass = issue.implementation.detectorClass.name
      val sameDetector = mutableListOf<Issue>()
      val differentDetector = mutableListOf<Issue>()

      for (r in others) {
        val conflict = r.issues.first { it.id == id }
        val list =
          if (conflict.implementation.detectorClass.name == detectorClass) {
            sameDetector
          } else {
            differentDetector
          }
        list.add(conflict)
      }

      fun listIssues(conflicts: List<Issue>) {
        sb.append("* $id: ${issue.getBriefDescription(TextFormat.RAW)} (this issue)\n")
        for (conflict in conflicts) {
          val builtin = if (conflict.registry is BuiltinIssueRegistry) "$id: Built-in" else null
          val desc =
            builtin
              ?: registryNames[conflict.registry]
              ?: "$id: ${conflict.getBriefDescription(TextFormat.RAW)}"
          sb.append("* [$desc](${getFileName(conflict, this.format)})\n")
        }
        sb.append("\n\n")
      }

      if (differentDetector.size > 1) {
        sb.append("(##) Conflicts\n\n")
        sb.append(
          wrap(
            "This issue id has also been used by other, unrelated lint checks. Issue id's must be unique, so you cannot " +
              "combine these libraries. Also defined in:"
          )
        )
        listIssues(differentDetector)
      }
      if (sameDetector.size > 1) {
        sb.append("(##) Repackaged\n\n")
        sb.append(
          wrap(
            "This lint check appears to have been packaged in other artifacts as well. Issue id's must be unique, so you cannot " +
              "combine these libraries. Also defined in:"
          )
        )
        listIssues(sameDetector)
      }
    }

    if (artifactId != null) {
      addLibraryInclude(sb, artifact, library?.lintLibrary ?: false)
      sb.append(
        "\n\n[Additional details about ${artifactId}](${getArtifactPageName(artifactId)}).\n"
      )
    }

    if (includeSuppressInfo) {
      writeSuppressInfo(sb, issue, id, issueData?.suppressExample)
    }
  }

  private fun addLibraryInclude(sb: StringBuilder, artifact: String, lintLibrary: Boolean) {
    sb.append("(##) Including\n\n")

    val endorsed = !artifact.startsWith("androidx.") && !artifact.startsWith("com.google")
    val version = artifact.substringAfterLast(':')
    val artifactId = artifact.substringAfter(':').substringBefore(':')
    val groupId = artifact.substringBefore(':')
    sb.append(
      """
      !!!
         This is not a built-in check. To include it, add the below dependency
         to your project.${if (endorsed) """ This lint check is included in the lint documentation,
         but the Android team may or may not agree with its recommendations.""" else ""}
      """
        .trimIndent()
    )
    sb.append("\n\n")

    val dependency = if (lintLibrary) "lintChecks" else "implementation"

    sb.append(
      """
      ```
      // build.gradle.kts
      $dependency("$artifact")

      // build.gradle
      $dependency '$artifact'

      // build.gradle.kts with version catalogs:
      $dependency(libs.$artifactId)

      # libs.versions.toml
      [versions]
      $artifactId = "$version"
      [libraries]
      $artifactId = {
          module = "$groupId:$artifactId",
          version.ref = "$artifactId"
      }
      ```

      $version is the version this documentation was generated from;
      there may be newer versions available.
      """
        .trimIndent()
    )
  }

  private fun getArtifactPageName(artifactName: String): String {
    val filename = artifactName.replace(':', '_').replace('.', '_')
    return "${filename}${this.format.extension}"
  }

  private fun writeOptions(sb: StringBuilder, issue: Issue, options: List<Option>) {
    sb.append("(##) Options\n\n")
    sb.append("You can configure this lint checks using the following options:\n\n")
    for (option in options) {
      sb.append("(###) ").append(option.name).append("\n\n")
      sb.append(option.getDescription()).append(".\n")
      val explanation = option.getExplanation() ?: ""
      if (explanation.isNotBlank()) {
        sb.append(explanation).append("\n")
      }
      sb.append("\n")
      val defaultValue = option.defaultAsString()
      if (defaultValue != null) {
        sb.append("Default is ").append(defaultValue).append(".\n")
      }
      sb.append("\n")
      sb.append("Example `lint.xml`:\n\n")
      sb.append(
        "" +
          "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~xml linenumbers\n" +
          "&lt;lint&gt;\n" +
          "    &lt;issue id=\"${issue.id}\"&gt;\n" +
          "        &lt;option name=\"${option.name}\" value=\"${defaultValue ?: "some string"}\" /&gt;\n" +
          "    &lt;/issue&gt;\n" +
          "&lt;/lint&gt;\n" +
          "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n\n"
      )
    }
  }

  private fun writeCodeLine(
    sb: StringBuilder,
    language: String = "",
    lineNumbers: Boolean = false
  ) {
    if (format == DocFormat.MARKDEEP) {
      val max = 70 - language.length - if (lineNumbers) " linenumbers".length else 0
      for (i in 0 until max) {
        sb.append('~')
      }
      sb.append(language)
      if (lineNumbers) {
        sb.append(" linenumbers")
      }
      sb.append('\n')
    } else {
      sb.append("```$language\n")
    }
  }

  private fun writeExample(
    sb: StringBuilder,
    issueData: IssueData,
    example: Example,
    issue: Issue
  ) {
    sb.append("(##) Example\n")
    sb.append('\n')
    sb.append("Here is an example of lint warnings produced by this check:\n")
    writeCodeLine(sb, "text")
    sb.append(example.output)
    writeCodeLine(sb)
    sb.append('\n')

    if (example.files.size == 1) {
      sb.append("Here is the source file referenced above:\n\n")
    } else {
      sb.append("Here are the relevant source files:\n\n")
    }
    writeSourceFiles(issue, example, sb)

    if (issueData.testUrl != null && includeSourceLinks) {
      sb.append(
        "" +
          "You can also visit the\n" +
          "[source code](${issueData.testUrl})\n" +
          "for the unit tests for this check to see additional scenarios.\n"
      )
    }

    if (example.inferred) {
      writeInferredExampleMessage(sb, example, issue)
    }
  }

  private fun writeSourceFiles(issue: Issue, example: Example, sb: StringBuilder) {
    for (file in example.files) {
      val contents =
        file.source.let {
          // A lot of builtin tests use the older android.support.annotation namespace,
          // but in tests we want to show the more recommended name androidx.annotation.
          // We only perform this substitution for built-in checks where we're sure
          // the checks aren't doing anything android.support.annotation-specific.
          if (issue.registry is BuiltinIssueRegistry) {
            convertToAndroidX(it)
          } else {
            it
          }
        }
      val lang = file.language

      sb.append("`${file.path}`:\n")
      writeCodeLine(sb, lang, lineNumbers = true)
      sb.append(contents).append('\n')
      writeCodeLine(sb)
      sb.append("\n")
    }
  }

  private fun convertToAndroidX(original: String): String {
    var s = original.replace("android.support.annotation.", "androidx.annotation.")
    if (!s.contains("android.support.")) {
      return s
    }
    while (true) {
      val matcher = ANDROID_SUPPORT_SYMBOL_PATTERN.matcher(s)
      if (!matcher.find()) {
        return s
      }

      val name = matcher.group(0)
      val newName = AndroidxNameUtils.getNewName(name)
      if (newName == name) {
        // Couldn't find a replacement; give up
        return s
      }
      s = s.substring(0, matcher.start()) + newName + s.substring(matcher.end())
    }
  }

  private fun writeInferredExampleMessage(sb: StringBuilder, example: Example, issue: Issue) {
    sb.append(
      "\nThe above example was automatically extracted from the first unit test\n" +
        "found for this lint check, `${example.testClass}.${example.testMethod}`.\n"
    )
    val vendor = issue.vendor ?: issue.registry?.vendor
    if (vendor != null) {
      sb.append("To report a problem with this extracted sample, ")
      val url = vendor.feedbackUrl
      if (url != null) {
        sb.append("visit\n$url.\n")
      } else {
        val contact = vendor.contact ?: vendor.vendorName
        sb.append("contact\n$contact.\n")
      }
    }
  }

  private fun writeSuppressInfo(sb: StringBuilder, issue: Issue, id: String, example: Example?) {
    sb.append("(##) Suppressing\n\n")

    val suppressNames = issue.suppressNames
    if (suppressNames != null) {
      if (suppressNames.isEmpty()) {
        sb.append(
          wrap(
            "This check has been explicitly marked as **not suppressible** by the check " +
              "author. This is usually only done in cases where a company has a check in place to " +
              "enforce a no-exceptions policy."
          )
        )
      } else {
        sb.append(
          wrap(
            "This check has been explicitly exempted from the normal suppression " +
              "mechanisms in lint (`@Suppress`, `lint.xml`, baselines, etc). However, it can be " +
              "disabled by annotating the element with " +
              "${suppressNames.toList().sorted().joinToString(" or ") { "`$it`" }}."
          )
        )
      }
      return
    }

    fun listIndent(s: String) = wrap(s, 70, "  ")

    val issueScope = issue.implementation.scope
    sb.append("You can suppress false positives using one of the following mechanisms:\n")

    val issueData = issueMap[issue.id]
    val language =
      issueData
        ?.suppressExample
        ?.files
        ?.firstOrNull { containsSuppress(it.source, issue) }
        ?.language
        ?: issueData?.example?.files?.firstOrNull { containsSuppress(it.source, issue) }?.language
        ?: issueData?.example?.files?.firstOrNull()?.language

    val kotlinOrJava = issueScope.contains(Scope.JAVA_FILE)
    val resourceFile = issueScope.contains(Scope.RESOURCE_FILE)
    val manifestFile = issueScope.contains(Scope.MANIFEST)
    val gradle = issueScope.contains(Scope.GRADLE_FILE)
    val properties = issueScope.contains(Scope.PROPERTY_FILE)

    var annotation: String? = null
    var comment: String? = null
    var attribute: String? = null

    val detector =
      try {
        issue.implementation.detectorClass.getDeclaredConstructor().newInstance()
      } catch (ignore: Throwable) {
        null
      }

    if (kotlinOrJava) {
      val statement =
        detector?.getApplicableMethodNames()?.firstOrNull()?.let { "$it(...)" }
          ?: detector?.getApplicableConstructorTypes()?.firstOrNull()?.let {
            "new ${it.substring(it.lastIndexOf('.') + 1)}(...)"
          }
          ?: "problematicStatement()"

      annotation =
        """
                        * Using a suppression annotation like this on the enclosing
                          element:

                          ```kt
                          // Kotlin
                          @Suppress("$id")
                          fun method() {
                             ${statement.replace("new ", "")}
                          }
                          ```

                          or

                          ```java
                          // Java
                          @SuppressWarnings("$id")
                          void method() {
                             $statement;
                          }
                          ```
                """
          .trimIndent()
    }

    if (kotlinOrJava || gradle) {
      comment =
        """
                        * Using a suppression comment like this on the line above:

                          ```kt
                          //noinspection $id
                          problematicStatement()
                          ```
                """
          .trimIndent()
    } else if (properties) {
      comment =
        """
                        * Using a suppression comment like this on the line above:

                          ```kt
                          #noinspection $id
                          key = problematic-value
                          ```
                """
          .trimIndent()
    }

    if (resourceFile || manifestFile) {
      attribute =
        listIndent(
            "" +
              "* Adding the suppression attribute `tools:ignore=\"$id\"` on the " +
              "problematic XML element (or one of its enclosing elements). You may " +
              "also need to add the following namespace declaration on the root " +
              "element in the XML file if it's not already there: " +
              "`xmlns:tools=\"http://schemas.android.com/tools\"`."
          )
          .trim()

      val tag: String? = detector?.getApplicableElements()?.firstOrNull()
      if (tag != null) {
        val root =
          if (resourceFile) {
            when {
              (detector as? ResourceXmlDetector)?.appliesTo(ResourceFolderType.VALUES) == true -> {
                TAG_RESOURCES
              }
              manifestFile -> {
                TAG_MANIFEST
              }
              else -> {
                tag
              }
            }
          } else {
            TAG_MANIFEST
          }
        val snippet = StringBuilder("\n\n")
        snippet.append("  ```xml\n")
        snippet.append("  $lt?xml version=\"1.0\" encoding=\"UTF-8\"?$gt\n")
        snippet.append("  $lt")
        snippet.append(root)
        snippet.append(" xmlns:tools=\"http://schemas.android.com/tools\"")
        if (root != tag) {
          snippet.append("$gt\n")
          snippet.append("      ...\n")
          snippet.append("      $lt")
          snippet.append(tag)
          snippet.append(" ")
          detector.getApplicableAttributes()?.firstOrNull()?.let {
            snippet.append(it).append("=\"...\" ")
          }
        } else {
          snippet.append("\n      ")
        }
        snippet.append("tools:ignore=\"").append(id).append("\" ...")
        if (root != tag) {
          snippet.append("/")
        }
        snippet.append("$gt\n")

        snippet.append("    ...\n")

        snippet.append("  $lt/")
        snippet.append(root)
        snippet.append("$gt\n")
        snippet.append("  ```")
        attribute += snippet
      }
    }

    val lintXml =
      """<?xml version="1.0" encoding="UTF-8"?>
                      <lint>
                          <issue id="$id" severity="ignore" />
                      </lint>"""

    val general =
      """
                    * Using a special `lint.xml` file in the source tree which turns off
                      the check in that folder and any sub folder. A simple file might look
                      like this:
                      ```xml
                      ${escapeXml(lintXml)}
                      ```
                      Instead of `ignore` you can also change the severity here, for
                      example from `error` to `warning`. You can find additional
                      documentation on how to filter issues by path, regular expression and
                      so on
                      [here](https://googlesamples.github.io/android-custom-lint-rules/usage/lintxml.md.html).

                    * In Gradle projects, using the DSL syntax to configure lint. For
                      example, you can use something like
                      ```gradle
                      lintOptions {
                          disable '$id'
                      }
                      ```
                      In Android projects this should be nested inside an `android { }`
                      block.

                    * For manual invocations of `lint`, using the `--ignore` flag:
                      ```
                      $ lint --ignore $id ...`
                      ```

                    * Last, but not least, using baselines, as discussed
                      [here](https://googlesamples.github.io/android-custom-lint-rules/usage/baselines.md.html).
            """
        .trimIndent()

    // Start with most likely suppress language, based on examples (because in some
    // cases the detector considers multiple types of files but only flags issues
    // in one file type, so we want to make it maximally likely that the message
    // here meets that expectation)
    val sorted =
      when (language) {
        "java",
        "kotlin" -> listOfNotNull(annotation, comment, attribute, general)
        "xml" -> listOfNotNull(attribute, annotation, comment, general)
        "groovy" -> listOfNotNull(comment, annotation, attribute, general)
        else -> listOfNotNull(annotation, comment, attribute, general)
      }

    sorted.forEach { sb.append('\n').append(it).append('\n') }

    if (example != null) {
      sb.append("\n(###) Suppress Example\n\n")
      writeSourceFiles(issue, example, sb)
    }
  }

  private fun findSourceFiles(root: File): List<File> {
    val files = mutableListOf<File>()
    fun addSourceFile(file: File) {
      if (file.isFile) {
        val path = file.path
        if (path.endsWith(DOT_KT) || path.endsWith(DOT_JAVA)) {
          files.add(file)
        }
      } else if (file.isDirectory) {
        file.listFiles()?.forEach { addSourceFile(it) }
      }
    }
    addSourceFile(root)
    return files
  }

  private fun findSourceFiles():
    Pair<Map<String, Map<File, List<File>>>, Map<String, Map<File, List<File>>>> {
    return Pair(findSourceFiles(sourcePath), findSourceFiles(testPath))
  }

  /**
   * Map from URL prefixes (where an empty string is allowed) to source roots to relative files
   * below that root
   */
  private fun findSourceFiles(
    prefixMap: Map<String, List<File>>
  ): Map<String, Map<File, List<File>>> {
    val map = mutableMapOf<String, MutableMap<File, MutableList<File>>>()
    for ((prefix, path) in prefixMap) {
      for (dir in path) {
        val sources = findSourceFiles(dir)
        val dirMap =
          map[prefix] ?: mutableMapOf<File, MutableList<File>>().also { map[prefix] = it }
        val list = dirMap[dir] ?: mutableListOf<File>().also { dirMap[dir] = it }
        list.addAll(sources)
      }
    }
    return map
  }

  private fun initializeSources(
    issueData: IssueData,
    sources: Pair<Map<String, Map<File, List<File>>>, Map<String, Map<File, List<File>>>>
  ) {
    println("Analyzing ${issueData.issue.id}")
    val (sourceFiles, testFiles) = sources
    val (detectorClass, detectorName) = getDetectorRelativePath(issueData.issue)

    findSource(detectorClass, detectorName, sourceFiles) { file, url ->
      issueData.sourceUrl = url
      if (file != null) {
        issueData.detectorSourceFile = file
        issueData.detectorSource = file.readText()
        issueData.copyrightYear = findCopyrightYear(file)
      }
    }

    findSource(detectorClass, detectorName + "Test", testFiles) { file, url ->
      issueData.testUrl = url
      if (file != null) {
        issueData.detectorTestSourceFile = file
        issueData.detectorTestSource = file.readText()
        initializeExamples(issueData)
      }
    }
  }

  private fun findCopyrightYear(file: File): Int {
    val lines = file.readLines()
    return findCopyrightYear(lines)
  }

  private fun findSourceFiles(issue: Issue): Array<Pair<String, String>> {
    val issueData = issueMap[issue.id] ?: return emptyArray()
    return listOfNotNull(
        issueData.sourceUrl?.let { url -> Pair("Implementation", "[Source Code]($url)") },
        issueData.testUrl?.let { url -> Pair("Tests", "[Source Code]($url)") }
      )
      .toTypedArray()
  }

  private fun getDetectorRelativePath(issue: Issue): Pair<Class<out Detector>, String> {
    val detectorClass = issue.implementation.detectorClass
    val detectorName =
      detectorClass.name.replace('.', '/').let {
        val innerClass = it.indexOf('$')
        if (innerClass == -1) it else it.substring(0, innerClass)
      }
    return Pair(detectorClass, detectorName)
  }

  private fun findSource(
    detectorClass: Class<out Detector>,
    detectorName: String,
    sourcePath: Map<String, Map<File, List<File>>>,
    store: (file: File?, url: String?) -> Unit
  ) {
    val relative = detectorName.replace('/', separatorChar)
    val relativeKt = relative + DOT_KT
    val relativeJava = relative + DOT_JAVA
    for ((prefix, path) in sourcePath) {
      for ((root, files) in path) {
        for (file in files) {
          val filePath = file.path
          if (filePath.endsWith(relativeKt) || filePath.endsWith(relativeJava)) {
            assert(file.path.startsWith(root.path) && file.path.length > root.path.length)
            val urlRelative = file.path.substring(root.path.length).replace(separatorChar, '/')
            // TODO: escape URL characters in the path?
            val url =
              if (prefix.endsWith("/") && urlRelative.startsWith("/")) {
                prefix + urlRelative.substring(1)
              } else {
                prefix + urlRelative
              }
            store(file, if (prefix.isEmpty()) null else url)
            return
          }
        }
      }
    }

    // Fallback for when we don't have sources but we have a source URI
    for ((prefix, path) in sourcePath) {
      if (prefix.isEmpty()) {
        continue
      }
      if (path.isEmpty()) {
        // If there is a prefix, but no URI, the assumption is that the relative
        // path for the detector should be appended to the URI as is
        val full =
          if (detectorClass.isKotlinClass()) {
            relative + DOT_KT
          } else {
            relative + DOT_JAVA
          }
        store(null, prefix + full)
        return
      }
    }
  }

  private fun Class<*>.isKotlinClass(): Boolean =
    this.declaredAnnotations.any { it.annotationClass == Metadata::class }

  private fun initializeExamples(issueData: IssueData) {
    val detectorTestSource = issueData.detectorTestSource
    val detectorTestSourceFile = issueData.detectorTestSourceFile
    if (detectorTestSource == null || detectorTestSourceFile == null) {
      return
    }

    val source: File =
      if (detectorTestSourceFile.isAbsolute) {
        detectorTestSourceFile
      } else {
        val file = File(System.getProperty("java.io.tmpdir"), detectorTestSourceFile.name)
        file.writeText(detectorTestSource)
        file
      }
    val ktFiles = if (source.path.endsWith(DOT_KT)) listOf(source) else emptyList()
    environment.analyzeFiles(ktFiles)
    val local = StandardFileSystems.local()
    val virtualFile =
      local.findFileByPath(source.path) ?: error("Could not find virtual file for $source")
    val psiFile =
      PsiManager.getInstance(environment.ideaProject).findFile(virtualFile)
        ?: error("Could not find PSI file for $source")
    val file = psiFile.toUElementOfType<UFile>() ?: error("Could not create UAST file for $source")

    val methods: MutableList<UMethod> = mutableListOf()

    for (testClass in file.classes) {
      val className = testClass.javaPsi.name ?: continue
      if (!className.endsWith("Test")) {
        continue
      }

      for (method in testClass.methods) {
        methods.add(method)
      }
    }

    val id = issueData.issue.id
    val curated1 = setOf("testDocumentationExample$id", "testExample$id", "example$id")
    val curated2 = setOf("testDocumentationExample", "testExample", "example", "testSample")
    val preferred = setOf("testBasic", "test")

    fun UMethod.rank(): Int {
      val name = this.name
      return when {
        curated1.contains(name) -> 1
        curated2.contains(name) -> 2
        preferred.contains(name) -> 3
        else -> 4
      }
    }

    val suppressExampleNames =
      setOf("testSuppressExample", "testSuppressExample$id", "suppressExample")
    val suppressExample = methods.firstOrNull { suppressExampleNames.contains(it.name) }

    if (suppressExample != null) {
      methods.remove(suppressExample)
      issueData.suppressExample =
        findExampleInMethod(suppressExample, issueData, inferred = false, suppress = true)
    }

    methods.sortWith(
      object : Comparator<UMethod> {
        override fun compare(o1: UMethod, o2: UMethod): Int {
          val rank1 = o1.rank()
          val rank2 = o2.rank()
          val delta = rank1 - rank2
          if (delta != 0) {
            return delta
          }
          return (o1.sourcePsi?.startOffset ?: 0) - (o2.sourcePsi?.startOffset ?: 0)
        }
      }
    )

    for (method in methods) {
      val inferred = method.rank() >= 3
      issueData.example = findExampleInMethod(method, issueData, inferred, false)
      if (issueData.example != null) {
        break
      }
    }
  }

  private fun findExampleInMethod(
    method: UMethod,
    issueData: IssueData,
    inferred: Boolean,
    suppress: Boolean
  ): Example? {
    val issue = issueData.issue
    var example: Example? = null
    method.accept(
      object : AbstractUastVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
          val name = node.methodName ?: node.methodIdentifier?.name
          if (example == null && name == "expect") {
            example = findExample(issue, method, node, inferred, suppress)
          }
          if ((name == "expectFixDiffs" || name == "verifyFixes") && singleIssueDetector(issue)) {
            // If we find a unit test for quickfixes, we assume that this issue
            // has a quickfix (though we only do this when a detector analyzes a
            // single issue, since the quickfix output has id in the output
            // to attribute the fix to one issue id or another)
            node.valueArguments.firstOrNull()?.let {
              val output = evaluateString(it)
              if (output != null && output.contains("Show URL for")) {
                // Quickfix just mentions URLs; let's not consider this a "fix" to
                // highlight in the report
                return super.visitCallExpression(node)
              }
            }
            issueData.quickFixable = true
          }
          return super.visitCallExpression(node)
        }
      }
    )

    return example
  }

  private fun singleIssueDetector(issue: Issue): Boolean {
    return singleIssueDetectors.contains(issue)
  }

  private fun createUastEnvironment(): UastEnvironment {
    val config = UastEnvironment.Configuration.create()
    config.addSourceRoots(testPath.values.flatten())

    val libs = mutableListOf<File>()
    val classPath: String = System.getProperty("java.class.path")
    for (path in classPath.split(pathSeparator)) {
      val file = File(path)
      val name = file.name
      if (name.endsWith(DOT_JAR)) {
        libs.add(file)
      } else if (
        !file.path.endsWith("android.sdktools.base.lint.checks-base") &&
          !file.path.endsWith("android.sdktools.base.lint.studio-checks")
      ) {
        libs.add(file)
      }
    }
    config.addClasspathRoots(libs)

    return UastEnvironment.create(config)
  }

  private fun disposeUastEnvironment() {
    environment.dispose()
  }

  private fun analyzeSource(): Map<String, IssueData> {
    val map = mutableMapOf<String, IssueData>()

    if (sourcePath.isNotEmpty() || testPath.isNotEmpty()) {
      println("Searching through source path")
    }
    val sources = this.findSourceFiles()

    for (issue in issues) {
      val data = IssueData(issue)
      data.quickFixable = Reporter.hasAutoFix(issue)
      map[issue.id] = data

      if (!(includeSuppressInfo || includeExamples || includeSourceLinks)) {
        continue
      }
      initializeSources(data, sources)
    }

    for (data in map.values) {
      if (
        data.sourceUrl == null || data.detectorSource == null && data.detectorTestSource == null
      ) {
        data.initialize()
      }
    }

    return map
  }

  // Given an expect() call in a lint unit test, returns the example metadata
  private fun findExample(
    issue: Issue,
    method: UMethod,
    node: UCallExpression,
    inferred: Boolean,
    suppress: Boolean
  ): Example? {
    val valueArgument = node.valueArguments.firstOrNull() ?: return null
    val expected = evaluateString(valueArgument) ?: return null
    if (!suppress && expected.contains("No warnings")) {
      return null
    }

    val map = computeResultMap(issue.id, expected)
    if (!suppress && map.isEmpty()) {
      return null
    }

    var example: Example? = null
    node.receiver?.accept(
      object : AbstractUastVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
          val name = node.methodName ?: node.methodIdentifier?.name
          if (example == null && name == "files") {
            example = findExample(node, map, issue, method, inferred, suppress)
          }
          return super.visitCallExpression(node)
        }
      }
    )

    return example
  }

  private fun getTestFileDeclaration(argument: UExpression?): UCallExpression? {
    argument ?: return null

    if (argument is UParenthesizedExpression) {
      return getTestFileDeclaration(argument.expression)
    }

    if (argument is UCallExpression) {
      val name = argument.methodName ?: argument.methodIdentifier?.name
      if (name == "compiled") {
        // use the source instead
        return getTestFileDeclaration(argument.valueArguments[1])
      }
      return argument
    }

    if (argument is UQualifiedReferenceExpression) {
      val selector = argument.selector
      return if (
        selector is UCallExpression &&
          (selector.methodName ?: selector.methodIdentifier?.name) == "indented"
      ) {
        getTestFileDeclaration(selector.receiver)
      } else {
        getTestFileDeclaration(selector)
      }
    }

    if (argument is USimpleNameReferenceExpression) {
      val element = argument.resolve()?.toUElement()
      if (element is UVariable) {
        return getTestFileDeclaration(element.uastInitializer)
      }
    }

    return null
  }

  private fun findExample(
    node: UCallExpression,
    outputMap: MutableMap<String, MutableMap<Int, MutableList<String>>>,
    issue: Issue,
    method: UMethod,
    inferred: Boolean,
    suppress: Boolean
  ): Example? {
    val exampleFiles = mutableListOf<ExampleFile>()
    for (argument in node.valueArguments) {
      val testFile = getTestFileDeclaration(argument) ?: continue
      val fileType = testFile.methodName ?: testFile.methodIdentifier?.name
      val testArgs = testFile.valueArguments
      if (testArgs.isNotEmpty()) {
        val source = evaluateString(testArgs.last()) ?: continue
        var path: String? = null
        if (testArgs.size > 1) {
          val first = testArgs.first()
          path = evaluateString(first)
          if (path == null) {
            // Attempt some other guesses
            val text = first.sourcePsi?.text
            if (text?.uppercase(Locale.US)?.contains("MANIFEST") == true) {
              path = "AndroidManifest.xml"
              if (outputMap.containsKey("src/main/AndroidManifest.xml"))
                path = "src/main/AndroidManifest.xml"
            }
          }
        } else {
          if (fileType == "gradle") {
            path = "build.gradle"
          } else {
            var relative: String? = null
            when (fileType) {
              "kotlin" -> {
                val className = ClassName(source)
                relative = className.relativePath(DOT_KT)
              }
              "java" -> {
                val className = ClassName(source)
                relative = className.relativePath(DOT_JAVA)
              }
              "manifest" -> {
                relative = "AndroidManifest.xml"
              }
            }
            if (relative != null) {
              path = "src/$relative"
              // Handle Gradle source set conversion, e.g. due to the
              // presence of a Gradle file a manifest is located in src/main/
              // instead of / (and a Java file in src/main/java/ instead of just src/, etc.)
              if (outputMap[path] == null) {
                for (p in outputMap.keys) {
                  if (p.endsWith(relative) && (!p.contains("/") || p.endsWith("/$relative"))) {
                    path = p
                    break
                  }
                }
              }
            }
          }
        }

        if (source.contains("HIDE-FROM-DOCUMENTATION")) {
          continue
        }

        val lang = getLanguage(path)
        val contents: String = if (lang == "xml") escapeXml(source) else source
        val exampleFile =
          ExampleFile(path, contents.split("\n").joinToString("\n") { it.trimEnd() }, lang)
        exampleFiles.add(exampleFile)
      }
    }

    if (suppress) {
      if (exampleFiles.any { containsSuppress(it.source, issue) }) {
        return Example(
          testClass = issue.implementation.detectorClass.simpleName,
          testMethod = method.name,
          files = exampleFiles,
          output = null,
          inferred = inferred
        )
      }
      return null
    }

    val errors = StringBuilder()

    for (exampleFile in exampleFiles) {
      val path = exampleFile.path ?: continue
      val warnings =
        outputMap[path]
          ?: run {
            val empty: Map<Int, List<String>> = emptyMap()
            var m = empty
            for ((p, v) in outputMap) {
              if (p.endsWith(path)) {
                m = v
                break
              }
            }
            // folder errors? Error message just shows folder path but specific source files
            // are *in* that folder
            if (m === empty) {
              for ((p, v) in outputMap) {
                if (path.startsWith(p)) {
                  m = v
                  break
                }
              }
            }
            m
          }

      if (inferred && warnings.isEmpty()) {
        continue
      }

      // Found warnings for this file!
      for (line in warnings.keys.sorted()) {
        val list = warnings[line] ?: continue
        val wrapped = if (line != -1) wrap("$path:$line:${list[0]}") else wrap("$path:${list[0]}")
        errors.append(wrapped).append('\n')

        // Dedent the source code on the error line (as well as the corresponding underline)
        if (list.size >= 3) {
          var sourceLine = list[1]
          var underline = list[2]
          val index1 = sourceLine.indexOfFirst { !it.isWhitespace() }
          val index2 = underline.indexOfFirst { !it.isWhitespace() }
          val index = min(index1, index2)
          val minIndent = 4
          if (index > minIndent) {
            sourceLine = sourceLine.substring(0, minIndent) + sourceLine.substring(index)
            underline = underline.substring(0, minIndent) + underline.substring(index)
          }
          errors.appendXml(sourceLine).append('\n')
          if (this.format == DocFormat.MARKDEEP) {
            errors.append(underline.replace('~', '-')).append('\n')
          } else {
            errors.append(underline)
          }
        }
        errors.append("\n\n")
      }

      if (inferred) {
        return Example(
          testClass = issue.implementation.detectorClass.simpleName,
          testMethod = method.name,
          files = listOf(exampleFile),
          output = errors.toString().trimEnd() + "\n",
          inferred = true
        )
      }
    }

    if (!inferred && exampleFiles.isNotEmpty()) {
      return Example(
        testClass = issue.implementation.detectorClass.simpleName,
        testMethod = method.name,
        files = exampleFiles,
        output = errors.toString().trimEnd() + "\n",
        inferred = false
      )
    }

    return null
  }

  private fun evaluateString(element: UElement): String? {
    val evaluator = ConstantEvaluator()
    evaluator.allowUnknowns()
    evaluator.allowFieldInitializers()
    val value = evaluator.evaluate(element) as? String ?: return null
    return value.trimIndent().replace('＄', '$')
  }

  private fun containsSuppress(source: String, issue: Issue): Boolean {
    return source.contains(issue.id) &&
      (source.contains("@Suppress") ||
        source.contains(":ignore") ||
        source.contains("noinspection"))
  }

  private fun getLanguage(path: String?): String {
    path ?: return "text"
    return if (path.endsWith(DOT_KT) || path.endsWith(DOT_KTS)) "kotlin"
    else if (path.endsWith(DOT_JAVA)) "java"
    else if (path.endsWith(DOT_GRADLE)) "groovy" else if (path.endsWith(DOT_XML)) "xml" else ""
  }

  private val lt = if (format == DocFormat.MARKDEEP) "&lt;" else "<"
  private val gt = if (format == DocFormat.MARKDEEP) "&gt;" else ">"

  private fun StringBuilder.appendXml(s: String): StringBuilder {
    append(escapeXml(s))
    return this
  }

  private fun escapeXml(s: String): String {
    return if (format == DocFormat.MARKDEEP) {
      s.replace("<", "&lt;").replace(">", "&gt;")
    } else {
      s
    }
  }

  private fun canAnalyzeInEditor(issue: Issue): Boolean {
    val implementation = issue.implementation
    val allScopes: Array<EnumSet<Scope>> = implementation.analysisScopes + implementation.scope
    return allScopes.any { Scope.checkSingleFile(it) }
  }

  companion object {
    const val SKIP_NETWORK = false

    private val MESSAGE_PATTERN: Pattern =
      Pattern.compile(
        """([^\n]+): (Error|Warning|Information): (.+?) \[([^]]+?)]$""",
        Pattern.MULTILINE or Pattern.DOTALL
      )
    private val LOCATION_PATTERN: Pattern = Pattern.compile("""(.+):(\d+)""")
    private val YEAR_PATTERN = Pattern.compile("""\b(\d\d\d\d)\b""")
    private val ANDROID_SUPPORT_SYMBOL_PATTERN =
      Pattern.compile("\\b(android.support.[a-zA-Z0-9_.]+)\\b")
    /** Pattern recognizing lint quickfix test output messages */
    private val FIX_PATTERN: Pattern = Pattern.compile("((Fix|Data) for .* line )(\\d+)(: .+)")

    @Suppress("SpellCheckingInspection")
    private const val AOSP_CS =
      "https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-main"

    private val PACKAGE_PATTERN = Pattern.compile("""package\s+(\S&&[^;]*)""")

    private val CLASS_PATTERN =
      Pattern.compile(
        """(\bclass\b|\binterface\b|\benum class\b|\benum\b|\bobject\b)+?\s*([^\s:(]+)""",
        Pattern.MULTILINE
      )

    private val NUMBER_PATTERN = Pattern.compile("^\\d+\\. ")

    private val thirdPartyChecks: List<MavenCentralLibrary> =
      listOf(
        MavenCentralLibrary(
          "com.slack.lint",
          "slack-lint-checks",
          Type.JAR,
          projectUrl = "https://github.com/slackhq/slack-lints",
          sourceUrl =
            "https://github.com/slackhq/slack-lints/blob/main/slack-lint-checks/src/main/java",
          testUrl =
            "https://github.com/slackhq/slack-lints/tree/main/slack-lint-checks/src/test/java",
          sourceContentUrl =
            "https://raw.githubusercontent.com/slackhq/slack-lints/main/slack-lint-checks/src/main/java",
          testContentUrl =
            "https://raw.githubusercontent.com/slackhq/slack-lints/main/slack-lint-checks/src/test/java",
          lintLibrary = true
        ),
        MavenCentralLibrary(
          "com.slack.lint.compose",
          "compose-lint-checks",
          Type.JAR,
          projectUrl = "https://github.com/slackhq/compose-lints",
          sourceUrl =
            "https://github.com/slackhq/compose-lints/tree/main/compose-lint-checks/src/main/java",
          testUrl =
            "https://github.com/slackhq/compose-lints/tree/main/compose-lint-checks/src/test/java",
          sourceContentUrl =
            "https://raw.githubusercontent.com/slackhq/compose-lints/main/compose-lint-checks/src/main/java",
          testContentUrl =
            "https://raw.githubusercontent.com/slackhq/compose-lints/main/compose-lint-checks/src/test/java",
          lintLibrary = true
        ),
        MavenCentralLibrary(
          "com.vanniktech",
          "lint-rules-android",
          Type.AAR,
          projectUrl = "https://github.com/vanniktech/lint-rules",
          sourceUrl =
            "https://github.com/vanniktech/lint-rules/tree/master/lint-rules-android-lint/src/main/java",
          testUrl =
            "https://github.com/vanniktech/lint-rules/tree/master/lint-rules-android-lint/src/test/java",
          sourceContentUrl =
            "https://raw.githubusercontent.com/vanniktech/lint-rules/master/lint-rules-android-lint/src/main/java",
          testContentUrl =
            "https://raw.githubusercontent.com/vanniktech/lint-rules/master/lint-rules-android-lint/src/test/java",
          lintLibrary = true
        ),
        MavenCentralLibrary(
          "com.vanniktech",
          "lint-rules-kotlin",
          Type.AAR,
          projectUrl = "https://github.com/vanniktech/lint-rules",
          sourceUrl =
            "https://github.com/vanniktech/lint-rules/tree/master/lint-rules-kotlin-lint/src/main/java",
          testUrl =
            "https://github.com/vanniktech/lint-rules/tree/master/lint-rules-kotlin-lint/src/test/java",
          sourceContentUrl =
            "https://raw.githubusercontent.com/vanniktech/lint-rules/master/lint-rules-kotlin-lint/src/main/java",
          testContentUrl =
            "https://raw.githubusercontent.com/vanniktech/lint-rules/master/lint-rules-kotlin-lint/src/test/java",
          lintLibrary = true
        ),
        MavenCentralLibrary(
          "com.vanniktech",
          "lint-rules-rxjava2",
          Type.AAR,
          projectUrl = "https://github.com/vanniktech/lint-rules",
          sourceUrl =
            "https://github.com/vanniktech/lint-rules/tree/master/lint-rules-rxjava2-lint/src/main/java",
          testUrl =
            "https://github.com/vanniktech/lint-rules/tree/master/lint-rules-rxjava2-lint/src/test/java",
          sourceContentUrl =
            "https://raw.githubusercontent.com/vanniktech/lint-rules/master/lint-rules-rxjava2-lint/src/main/java",
          testContentUrl =
            "https://raw.githubusercontent.com/vanniktech/lint-rules/master/lint-rules-rxjava2-lint/src/test/java",
          lintLibrary = true
        ),
        MavenCentralLibrary(
          "com.google.dagger",
          "dagger-lint",
          Type.JAR,
          projectUrl = "https://github.com/google/dagger",
          sourceUrl = "https://github.com/google/dagger/tree/master/java",
          testUrl = "https://github.com/google/dagger/tree/master/javatests",
          sourceContentUrl = "https://raw.githubusercontent.com/google/dagger/master/java",
          testContentUrl = "https://raw.githubusercontent.com/google/dagger/master/javatests",
          lintLibrary = false
        ),
        MavenCentralLibrary(
          "com.jakewharton.timber",
          "timber",
          Type.AAR,
          projectUrl = "https://github.com/JakeWharton/timber",
          sourceUrl = "https://github.com/JakeWharton/timber/tree/trunk/timber-lint/src/main/java",
          testUrl = "https://github.com/JakeWharton/timber/tree/trunk/timber-lint/src/test/java",
          sourceContentUrl =
            "https://raw.githubusercontent.com/JakeWharton/timber/trunk/timber-lint/src/main/java",
          testContentUrl =
            "https://raw.githubusercontent.com/JakeWharton/timber/trunk/timber-lint/src/test/java",
          lintLibrary = false
        ),
        MavenCentralLibrary(
          "com.uber.autodispose2",
          "autodispose-lint",
          Type.JAR,
          projectUrl = "https://github.com/uber/AutoDispose",
          sourceUrl =
            "https://github.com/uber/AutoDispose/tree/main/static-analysis/autodispose-lint/src/main/kotlin",
          testUrl =
            "https://github.com/uber/AutoDispose/tree/main/static-analysis/autodispose-lint/src/test/kotlin",
          sourceContentUrl =
            "https://raw.githubusercontent.com/uber/AutoDispose/main/static-analysis/autodispose-lint/src/main/kotlin",
          testContentUrl =
            "https://raw.githubusercontent.com/uber/AutoDispose/main/static-analysis/autodispose-lint/src/test/kotlin",
          lintLibrary = true
        ),
      )

    private fun createClient(): LintCliClient {
      return if (LintClient.isClientNameInitialized() && LintClient.isUnitTest) {
        LintCliClient(LintClient.CLIENT_UNIT_TESTS)
      } else {
        LintCliClient("generate-docs")
      }
    }

    /**
     * Given lint unit test output, returns a map from file path to line number to list of errors
     * for that line
     */
    @VisibleForTesting
    fun computeResultMap(
      issueId: String,
      expected: String
    ): MutableMap<String, MutableMap<Int, MutableList<String>>> {
      val map = HashMap<String, MutableMap<Int, MutableList<String>>>()

      for (incident in getOutputIncidents(expected)) {
        if (incident.id == issueId) {
          val strings = ArrayList<String>()
          strings.add(incident.severity + ": " + incident.message + " [$issueId]")
          incident.sourceLine1?.let { strings.add(it) }
          incident.sourceLine2?.let { strings.add(it) }
          val path = incident.path
          val lineNumber = incident.lineNumber
          val lineNumberMap =
            map[path] ?: HashMap<Int, MutableList<String>>().also { map[path] = it }
          lineNumberMap[lineNumber] = strings
        }
      }

      return map
    }

    /**
     * Given lint test output (lint CLI text report output), return the corresponding lines
     * (including only the error lines, not for example source code snippets)
     */
    fun getOutputLines(output: String): List<String> {
      return getOutputIncidents(output).map { it.message }
    }

    fun findCopyrightYear(lines: List<String>): Int {
      for (line in lines) {
        if (
          line.contains("opyright") ||
            line.contains("(C)") ||
            line.contains("(c)") ||
            line.contains('\u00a9') // Copyright unicode symbol, ©
        ) {
          val matcher = YEAR_PATTERN.matcher(line)
          var start = 0
          var maxYear = -1
          // Match on all years on the line and take the max -- that way we handle
          //   (c) 2020 Android
          //   Copyright 2007-2020 Android
          //   © 2018, 2019, 2020, 2021 Android
          // etc.
          while (matcher.find(start)) {
            val year = matcher.group(1).toInt()
            maxYear = max(maxYear, year)
            start = matcher.end()
          }

          if (maxYear != -1) {
            return maxYear
          }
        }
      }

      // Couldn't find a copyright
      val prefix = lines.subList(0, min(8, lines.size)).joinToString("\n")
      if (
        !prefix.contains("vannik") && !prefix.contains("timber.lint")
      ) { // known to not contain years in copyrights
        println("Couldn't find copyright year in $prefix\n\n")
      }

      return -1
    }

    data class ReportedIncident(
      /** Relative path, always using unix file separators */
      val path: String,
      val severity: String,
      /** Line number (0-based) */
      val lineNumber: Int,
      /** Column number (0-based) */
      val column: Int,
      /** The lint error message */
      val message: String,
      /** Lint issue id */
      val id: String,
      /** First source line, if present */
      val sourceLine1: String?,
      /** Second source line, if present */
      val sourceLine2: String?
    )

    fun getOutputIncidents(output: String): List<ReportedIncident> {
      val list = mutableListOf<ReportedIncident>()
      var index = 0
      val matcher = MESSAGE_PATTERN.matcher(output)
      while (true) {
        if (!matcher.find(index)) {
          break
        }
        index = matcher.end()

        // group 2 is severity, group 3 is message and group 4 is the id
        val path: String
        val lineNumber: Int
        var column: Int = -1
        val location = matcher.group(1)
        val locationMatcher = LOCATION_PATTERN.matcher(location)
        if (locationMatcher.find()) {
          path = locationMatcher.group(1)
          lineNumber = locationMatcher.group(2).toInt()
        } else {
          path = location
          lineNumber = -1
        }
        val nextStart = index + 1
        val nextEnd = output.indexOf('\n', nextStart).let { if (it == -1) output.length else it }
        val sourceLine1 = output.substring(nextStart, nextEnd)
        // Text from "Error:" and on
        var sourceLine2: String? = null
        // no source included in error output?
        if (!MESSAGE_PATTERN.matcher(sourceLine1).matches()) {
          val nextStart2 = min(output.length, nextEnd + 1)
          val nextEnd2 =
            output.indexOf('\n', nextStart2).let { if (it == -1) output.length else it }
          sourceLine2 = output.substring(nextStart2, nextEnd2)
          column = sourceLine2.indexOfFirst { !it.isWhitespace() }
        }

        val severity = matcher.group(2)
        val id = matcher.group(4).substringBefore(' ') // Sometimes output includes library source
        val message = matcher.group(3)
        list.add(
          ReportedIncident(
            path,
            severity,
            lineNumber,
            column,
            message,
            id,
            sourceLine1,
            sourceLine2
          )
        )
      }

      return list
    }

    /** Given lint unit test quickfix output, return just the fix lines. */
    fun getFixLines(output: String): List<String> {
      return output
        .lines()
        .mapNotNull {
          val matcher = FIX_PATTERN.matcher(it)
          if (matcher.matches()) matcher.group(0) else null
        }
        .toList()
    }

    private fun String.isListItem(): Boolean {
      return startsWith("- ") ||
        startsWith("* ") ||
        startsWith("+ ") ||
        firstOrNull()?.isDigit() == true && NUMBER_PATTERN.matcher(this).find()
    }

    private fun getRegistry(client: LintCliClient, jarFile: File): IssueRegistry? {
      val registries = JarFileIssueRegistry.get(client, listOf(jarFile), skipVerification = true)
      return registries.firstOrNull()
    }

    private fun getRegistries(
      registryMap: Map<IssueRegistry, String?>,
      includeBuiltins: Boolean
    ): Map<IssueRegistry, String?> {
      return if (includeBuiltins) {
        val builtIns = mapOf<IssueRegistry, String?>(BuiltinIssueRegistry() to null)
        builtIns + registryMap
      } else {
        registryMap
      }
    }

    private fun findStudioSource(): File? {
      val root = System.getenv("ADT_SOURCE_TREE")
      if (root != null) {
        return File(root)
      }

      val source = LintIssueDocGenerator::class.java.protectionDomain.codeSource
      if (source != null) {
        val location = source.location
        try {
          var dir: File? = File(location.toURI())
          while (dir != null) {
            if (File(dir, "tools/base/lint").isDirectory) {
              return dir
            }
            dir = dir.parentFile
          }
        } catch (ignore: Exception) {}
      }

      return null
    }

    /**
     * Add some URLs for pointing to the AOSP source code if nothing has been specified for the
     * built-in checks. If we have access to the source code, we provide a source path for more
     * accurate search; we only include a test fallback if we're not in unit tests since in unit
     * tests we may or may not find the source code based on the build system the test is run from.
     */
    @Suppress("SpellCheckingInspection")
    private fun addAospUrls(
      sourcePath: MutableMap<String, MutableList<File>>,
      testPath: MutableMap<String, MutableList<File>>
    ) {
      if (!LintClient.isUnitTest) {
        val lintRoot = findStudioSource()?.let { File("tools/base/lint") }
        if (lintRoot != null) {
          testPath["$AOSP_CS:lint/libs/lint-tests/src/test/java/"] =
            mutableListOf(File("$lintRoot/libs/lint-tests/src/test/java"))
          sourcePath["$AOSP_CS:lint/libs/lint-checks/src/main/java/"] =
            mutableListOf(
              File(
                "$lintRoot/libs/lint-checks/src/main/java",
                "$lintRoot/studio-checks/src/main/java"
              )
            )
        }
      } else {
        // We can't have a default URL for tests because we have no way to look up
        // whether it's in Kotlin or Java since it's not on the classpath (which
        // is needed to make the URL suffix correct)
        sourcePath["$AOSP_CS:lint/libs/lint-checks/src/main/java/"] = mutableListOf()
      }
      // TODO: offer some kind of search-based fallback? e.g. for AlarmDetector, a URL like this:
      // https://cs.android.com/search?q=AlarmDetector.kt&sq=&ss=android-studio%2Fplatform%2Ftools%2Fbase
    }

    @JvmStatic
    fun main(args: Array<String>) {
      val exitCode = run(args)
      exitProcess(exitCode)
    }

    @JvmStatic
    fun run(args: Array<String>, fromLint: Boolean = false): Int {
      var format = DocFormat.MARKDEEP
      var singleDoc = false
      var jarPath: String? = null
      var includeStats = false
      var includeIndices = true
      var outputPath: String? = null
      val issues = mutableListOf<String>()
      val sourcePath = mutableMapOf<String, MutableList<File>>()
      val testPath = mutableMapOf<String, MutableList<File>>()
      var includeSuppressInfo = true
      var includeExamples = true
      var includeSourceLinks = true
      var includeBuiltins = false
      var includeSeverityColor = true
      var searchGmaven = false
      var searchMavenCentral = false

      var index = 0
      while (index < args.size) {
        when (val arg = args[index]) {
          ARG_HELP,
          "-h",
          "-?" -> {
            printUsage(fromLint)
            return ERRNO_USAGE
          }
          ARG_SINGLE_DOC -> {
            singleDoc = true
            includeSuppressInfo = false
          }
          ARG_INCLUDE_BUILTINS -> includeBuiltins = true
          ARG_MD -> format = DocFormat.MARKDOWN
          ARG_NO_SEVERITY -> includeSeverityColor = false
          ARG_INCLUDE_STATS -> includeStats = true
          ARG_NO_INDEX -> includeIndices = false
          ARG_NO_SUPPRESS_INFO -> includeSuppressInfo = false
          ARG_NO_EXAMPLES -> includeExamples = false
          ARG_NO_SOURCE_LINKS -> includeSourceLinks = false
          ARG_GMAVEN -> searchGmaven = true
          ARG_MAVEN_CENTRAL -> searchMavenCentral = true
          ARG_LINT_JARS -> {
            if (index == args.size - 1) {
              System.err.println("Missing lint jar path")
              return ERRNO_ERRORS
            }
            val path = args[++index]
            if (jarPath != null) {
              jarPath += pathSeparator + path
            } else {
              jarPath = path
            }
          }
          ARG_ISSUES -> {
            if (index == args.size - 1) {
              System.err.println("Missing list of lint issue id's")
              return ERRNO_ERRORS
            }
            val issueList = args[++index]
            issues.addAll(issueList.split(","))
          }
          ARG_SOURCE_URL -> {
            if (index == args.size - 1) {
              System.err.println("Missing source URL prefix")
              return ERRNO_ERRORS
            }
            val prefix =
              args[++index].let {
                if (it.isEmpty()) it else if (it.last().isLetter()) "$it/" else it
              }
            if (index == args.size - 1) {
              System.err.println("Missing source path")
              return ERRNO_ERRORS
            }
            val path = args[++index]
            val list = sourcePath[prefix] ?: ArrayList<File>().also { sourcePath[prefix] = it }
            list.addAll(
              splitPath(path).map {
                val file = File(it)
                // UAST does not support relative paths
                if (file.isAbsolute) file else file.absoluteFile
              }
            )
          }
          ARG_TEST_SOURCE_URL -> {
            if (index == args.size - 1) {
              System.err.println("Missing source URL prefix")
              return ERRNO_ERRORS
            }
            val prefix =
              args[++index].let {
                if (it.isEmpty()) it else if (it.last().isLetter()) "$it/" else it
              }
            if (index == args.size - 1) {
              System.err.println("Missing source path")
              return ERRNO_ERRORS
            }
            val path = args[++index]
            val list = testPath[prefix] ?: ArrayList<File>().also { testPath[prefix] = it }
            list.addAll(
              splitPath(path).map {
                val file = File(it)
                // UAST does not support relative paths
                if (file.isAbsolute) file else file.absoluteFile
              }
            )
          }
          ARG_OUTPUT -> {
            if (index == args.size - 1) {
              System.err.println("Missing source path")
              return ERRNO_ERRORS
            }
            val path = args[++index]
            if (outputPath != null) {
              println("Only one output expected; found both $outputPath and $path")
              return ERRNO_ERRORS
            }
            outputPath = path
          }
          else -> {
            println("Unknown flag $arg")
            printUsage(fromLint)
            return ERRNO_ERRORS
          }
        }
        index++
      }

      if (outputPath == null) {
        println("Must specify an output file or folder")
        printUsage(fromLint)
        return ERRNO_ERRORS
      }

      // Collect registries, using jarpath && for (path in splitPath(args[++index])) {

      val output = File(outputPath)
      if (output.exists()) {
        if (!singleDoc) {
          println("$output already exists")
        } else if (output.exists()) {
          // Ok to delete a specific file but not a whole directory in case
          // it's not clear whether you specify a new directory or the
          // directory to place the output in, e.g. home.
          output.delete()
        }
      } else if (!singleDoc) {
        val created = output.mkdirs()
        if (!created) {
          println("Couldn't create $output")
          exitProcess(10)
        }
      }

      val client = createClient()
      val registries: Map<IssueRegistry, String?> =
        if (jarPath == null && !searchGmaven) {
          println(
            "Note: No lint jars specified: creating documents for the built-in lint checks instead ($ARG_INCLUDE_BUILTINS)"
          )
          includeBuiltins = true
          emptyMap()
        } else {
          val gmavenMap =
            if (searchGmaven) findLintIssueRegistriesFromGmaven(client) else emptyMap()
          val mavenCentralMap =
            if (searchMavenCentral) findLintIssueRegistriesFromMavenCentral(client) else emptyMap()
          val jarMap =
            if (jarPath != null)
              findLintIssueRegistries(
                client,
                jarPath,
                { id, lintLibrary -> GmavenLibrary(id, lintLibrary) }
              )
            else emptyMap()
          jarMap + gmavenMap + mavenCentralMap
        }

      val registryMap = getRegistries(registries, includeBuiltins)

      if (sourcePath.isEmpty() && registryMap is BuiltinIssueRegistry) {
        addAospUrls(sourcePath, testPath)
      }

      val generator =
        LintIssueDocGenerator(
          output,
          registryMap,
          issues,
          singleDoc,
          format,
          includeStats,
          sourcePath,
          testPath,
          includeIndices,
          includeSuppressInfo,
          includeExamples,
          includeSourceLinks,
          includeSeverityColor
        )
      generator.generate()

      println("Wrote issue docs to $output${if (!singleDoc) separator else ""}")
      return ERRNO_SUCCESS
    }

    private fun findLintIssueRegistriesFromGmaven(
      client: LintCliClient
    ): Map<IssueRegistry, String?> {
      val cacheDir = File(getGmavenCache(), "m2repository")
      populateGmavenOfflineRepo(client, cacheDir)
      return findLintIssueRegistries(
        client,
        cacheDir.path,
        { id, lintLibrary -> GmavenLibrary(id, lintLibrary) },
        searchWithinArchives = false
      )
    }

    private fun findLintIssueRegistriesFromMavenCentral(
      client: LintCliClient
    ): Map<IssueRegistry, String?> {
      val cacheDir = File(getMavenCentralCache(), "m2repository")
      populateMavenCentralOfflineRepo(client, cacheDir)

      return findLintIssueRegistries(
        client,
        cacheDir.path,
        { id, _ ->
          thirdPartyChecks.firstOrNull { it.id == id }
            ?: error("Couldn't find registration info for $id")
        },
        searchWithinArchives = true,
        allJarsAreLintChecks = true
      )
    }

    private fun populateMavenCentralOfflineRepo(client: LintCliClient, cacheDir: File) {
      if (SKIP_NETWORK) return
      for (library in thirdPartyChecks) {
        val versions = library.getVersions(client)
        for (version in versions) {
          val dir =
            File(
              library.group.replace('.', separatorChar) +
                separator +
                library.artifact +
                separator +
                version
            )
          val extension = library.type.getExtension()
          val name = library.artifact + "-" + version + extension
          val file = File(cacheDir, dir.path + separator + name)

          if (file.exists()) {
            println("Cached ${library.group}:${library.artifact}:$version in $dir:$name")
            continue
          }

          val downloaded = downloadMavenFile(library, version) ?: continue
          file.parentFile.mkdirs()
          file.writeBytes(downloaded)
          println(
            "Stored ${library.group}:${library.artifact}:$version: ${downloaded.size} bytes in $dir:$name"
          )
          val pomUrl = library.getUrl(version, ".pom")
          val pom = downloadMavenFile(library, version, pomUrl)
          if (pom != null) {
            val pomFile = File(file.path.substringBeforeLast('.') + ".pom")
            pomFile.writeBytes(pom)
            writePomDate(pomUrl, File(file.path.substringBeforeLast('.') + ".date"))
          }
        }
      }
    }

    private fun downloadMavenFile(
      library: MavenCentralLibrary,
      version: String,
      url: URL = library.getUrl(version)
    ): ByteArray? {
      if (SKIP_NETWORK) return null

      try {
        return url.readBytes()
      } catch (e: IOException) {
        val urlString = url.toExternalForm()
        val dot = urlString.lastIndexOf('.')
        try {
          return URL(urlString.substring(0, dot) + "-release" + urlString.substring(dot))
            .readBytes()
        } catch (e2: IOException) {
          if (urlString.endsWith(DOT_JAR)) {
            try {
              // Try looking for .aar instead
              return URL(urlString.removeSuffix(DOT_JAR) + DOT_AAR).readBytes()
            } catch (ignore: IOException) {}
          }

          if (
            library.group == "com.vanniktech" &&
              library.artifact == "lint-rules-android" &&
              version == "0.7.0"
          ) {
            // looks like this version didn't include a library, only sources & javadocs:
            // https://search.maven.org/artifact/com.vanniktech/lint-rules-android/0.7.0/aar
            return null
          }
          println("Couldn't find ${library.group}:${library.artifact}:$version at $url: $e")
        }
      }

      return null
    }

    /**
     * Uses the [GoogleMavenRepository] to fetch the various lint libraries from maven.google.com.
     * It also inserts empty files for all the remote artifacts that aren't lint jars, such that
     * running it multiple times only fetches new artifacts.
     */
    private fun populateGmavenOfflineRepo(client: LintCliClient, cacheDir: File) {
      if (SKIP_NETWORK) return

      val repository: GoogleMavenRepository =
        object : GoogleMavenRepository(cacheDir.toPath()) {
          public override fun readUrlData(url: String, timeout: Int): ByteArray? =
            readUrlData(client, url, timeout)

          public override fun error(throwable: Throwable, message: String?) =
            client.log(throwable, message)
        }

      for (group in repository.getGroups().sorted()) {
        // Note that as of today, only AndroidX libraries ship with lint.jars so
        // you can run this test much faster by skipping any group that doesn't start with
        // "androidx."
        for (artifact in repository.getArtifacts(group).sorted()) {
          println("Checking artifact $group:$artifact")
          val versions = repository.getVersions(group, artifact).sorted().reversed()
          for (version in versions) {
            val groupPath = group.replace('.', '/')
            val relative = "$groupPath/$artifact/$version/$artifact-$version"
            val url = "https://dl.google.com/android/maven2/$relative.aar"
            val aarTarget = File(cacheDir, "$relative.aar")
            aarTarget.parentFile?.mkdirs()
            val lintTarget = File(cacheDir, "$relative-lint.jar")
            lintTarget.parentFile?.mkdirs()

            if (!lintTarget.isFile) {
              // Don't try again:
              lintTarget.createNewFile()

              if (!aarTarget.isFile) {
                // Download and create it
                aarTarget.createNewFile() // or don't try again if it doesn't exist
                try {
                  println("Read $url")
                  val bytes =
                    try {
                      readUrlData(client, url, 30 * 1000)
                    } catch (e: FileNotFoundException) {
                      aarTarget.createNewFile() // don't try again if it doesn't exist
                      continue
                    }
                  var lintJarBytes: ByteArray? = null
                  if (bytes != null) {
                    aarTarget.writeBytes(bytes)

                    // Try to get the POM
                    val pomUrl =
                      "https://dl.google.com/android/maven2/$groupPath/$artifact/$version/$artifact-$version.pom"
                    val pom =
                      try {
                        readUrlData(client, pomUrl, 30 * 1000)
                      } catch (e: IOException) {
                        null
                      }
                    if (pom != null) {
                      val pomTarget = File(cacheDir, "$relative.pom")
                      pomTarget.writeBytes(pom)
                      writePomDate(URL(pomUrl), File(cacheDir, "$relative.date"))
                    }

                    // Try to extract lint.jar
                    JarFile(aarTarget).use { jarFile ->
                      val lintJar = jarFile.getJarEntry("lint.jar")
                      if (lintJar != null) {
                        jarFile.getInputStream(lintJar).use { stream ->
                          lintJarBytes = ByteStreams.toByteArray(stream)
                        }
                      }
                    }
                  }
                  if (lintJarBytes == null) {
                    // No lint jar: stop checking these versions
                    lintTarget.createNewFile()

                    // Also write empty files for all older files so we don't keep trying
                    for (v in versions) {
                      val versionKey = "$groupPath/$artifact/$v/$artifact-$v"
                      val olderLintJar = File(cacheDir, "$versionKey-lint.jar")
                      if (!olderLintJar.isFile) {
                        olderLintJar.parentFile?.mkdirs()
                        olderLintJar.createNewFile()
                        val olderAar = File(cacheDir, "$versionKey.aar")
                        olderAar.createNewFile()
                      }
                    }

                    // Also null out the AAR file; we don't need to sit on a non-zero sized file
                    // here
                    aarTarget.delete()
                    aarTarget.createNewFile()
                    break
                  }
                  lintTarget.writeBytes(lintJarBytes!!)
                } catch (ignore: Throwable) {
                  // Couldn't read URL -- probably not an aar but a jar
                  continue
                }
              }
            }
          }
        }
      }

      println("Done searching remote index.")
    }

    private fun writePomDate(pomUrl: URL, dateTarget: File) {
      val connection = pomUrl.openConnection() as HttpURLConnection
      connection.requestMethod = "HEAD"
      if (connection.responseCode == 200) {
        val lastModifiedHeader = connection.getHeaderField("Last-Modified")
        if (lastModifiedHeader != null) {
          val lastModifiedDate =
            SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z").parse(lastModifiedHeader)!!.time
          val localDate = Date(lastModifiedDate)
          val localTime = LocalDateTime.ofInstant(localDate.toInstant(), ZoneOffset.UTC)
          dateTarget.writeText(
            "%4d/%02d/%02d".format(localTime.year, localTime.monthValue, localTime.dayOfMonth)
          )
        }
        connection.disconnect()
      }
    }

    private fun findLintIssueRegistries(
      client: LintCliClient,
      jarPath: String,
      libraryFactory: (String, Boolean) -> Library,
      searchWithinArchives: Boolean = true,
      allJarsAreLintChecks: Boolean = false
    ): Map<IssueRegistry, String?> {
      val into =
        mutableMapOf<String, LibraryVersionEntry>() // Issue registry to (optional) maven artifact
      splitPath(jarPath).forEach { path ->
        addLintIssueRegistries(into, File(path), searchWithinArchives, allJarsAreLintChecks)
      }

      val artifacts = mutableMapOf<String, Library>()
      val artifactIds = into.keys.map { it.substringBeforeLast(':') }.toSet()

      for (entry in into.values) {
        var id = entry.id
        // Multiplatform: creates -android artifacts; you're not supposed
        // to directly include this in your dependency, so remove it here
        // (but we can't just ignore these entries because at least for
        // AndroidX, they've *moved* the issues from :x to :x-android,
        // so without the below adjustment we'd see issues disappearing from
        // :x and appearing in :x-android.)
        if (id.endsWith("-android")) {
          val withoutSuffix = id.removeSuffix("-android")
          if (artifactIds.contains(withoutSuffix)) {
            id = withoutSuffix
          }
        }
        val artifact =
          artifacts[id] ?: libraryFactory(id, entry.lintLibrary).also { artifacts[id] = it }
        artifact.addVersion(entry)
      }

      for (library in artifacts.values) {
        library.removeOldPreviews()
      }

      // Write our byte arrays as jar files we can map to
      val result = mutableMapOf<IssueRegistry, String?>()

      for ((_, library) in artifacts) {
        val versions = library.versions.keys

        val latest = versions.firstOrNull()
        val bytes = library.versions[latest]!!.jarBytes
        val key = library.id
        val file = File.createTempFile(key.replace(':', '_'), DOT_JAR)
        file.deleteOnExit()
        file.writeBytes(bytes)
        val registry = getRegistry(client, file)
        if (registry == null) {
          println(
            "Error: Unexpectedly got null issue registry for $key with $file and version $latest"
          )
          continue
        }
        val wrapped = DocIssueRegistry(registry, library)
        result[wrapped] = "$key:$latest"
      }
      return result
    }

    /** Data about a library which can have multiple versions, each one a [LibraryVersionEntry] */
    abstract class Library(
      /** Maven group:artifact for this library */
      val id: String,
      /**
       * If true, this is a lint jar library (which you would use via `lintChecks`); if false, it's
       * an AAR library (usually not lint related) which also includes a lint payload
       */
      val lintLibrary: Boolean
    ) {
      val versions: MutableMap<Version, LibraryVersionEntry> = TreeMap { o1, o2 ->
        -o1.compareTo(o2)
      }
      var registry: IssueRegistry? = null

      fun addVersion(entry: LibraryVersionEntry) {
        versions[entry.version] = entry
      }

      fun removeOldPreviews() {
        val relevantVersions = versionsWithoutOldPreviews()
        val remove = versions.keys.filter { !relevantVersions.contains(it) }
        for (version in remove) {
          versions.remove(version)
        }
      }

      abstract fun getUrl(
        view: Boolean,
        test: Boolean,
        issue: Issue,
        detectorPath: String,
        sourceSet: String = "java",
        extension: String = ".kt"
      ): String

      private fun versionsWithoutOldPreviews(): List<Version> {
        val v = versions.keys
        return v.filter { version ->
          if (version.isPreview) {
            val key = version.toString()
            val index = key.indexOf('-')
            val stable = Version.parse(key.substring(0, index))
            !versions.containsKey(stable)
          } else {
            true
          }
        }
      }
    }

    class GmavenLibrary(id: String, lintLibrary: Boolean) : Library(id, lintLibrary) {
      override fun getUrl(
        view: Boolean,
        test: Boolean,
        issue: Issue,
        detectorPath: String,
        sourceSet: String,
        extension: String
      ): String {
        val url = StringBuilder()
        if (view) {
          url.append(
            "https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:/"
          )
        } else {
          url.append(
            "https://android.googlesource.com/platform/frameworks/support/+/androidx-main/"
          )
        }

        var m =
          id
            .removePrefix("androidx.")
            .replace(':', '/')
            .replace('.', '/')
            .removeSuffix("-android") + "-lint"
        // workaround: androidx generally places lint checks in a predictable place except for one
        // weird exception. Work around this for now (and get androidx updated.)
        if (m == "work/work-runtime-lint") {
          m = "work/work-lint"
        }
        url.append(m)
        url.append("/src/")
        if (test) {
          url.append("test")
        } else {
          url.append("main")
        }
        url.append('/')
        url.append(sourceSet)
        url.append('/')

        val path =
          if (test) {
            // A few androidx detectors put their tests in weird places; work around this:
            when (detectorPath) {
              "androidx/fragment/lint/UnsafeFragmentLifecycleObserverDetector" -> {
                when (issue.id) {
                  "FragmentLiveDataObserve" ->
                    "androidx/fragment/lint/FragmentLiveDataObserveDetector"
                  "FragmentAddMenuProvider" -> "androidx/fragment/lint/AddMenuProviderDetector"
                  "FragmentBackPressedCallback" ->
                    "androidx/fragment/lint/BackPressedDispatcherCallbackDetector"
                  else -> detectorPath
                }
              }
              "androidx/lifecycle/lint/NonNullableMutableLiveDataDetector" ->
                "androidx/lifecycle/livedata/core/lint/NonNullableMutableLiveDataDetector"
              "androidx/lifecycle/lint/LifecycleWhenChecks" ->
                "androidx/lifecycle/runtime/lint/LifecycleWhenChecks"
              "androidx/lifecycle/lint/RepeatOnLifecycleDetector" ->
                "androidx/lifecycle/runtime/lint/RepeatOnLifecycleDetector"
              "androidx/recyclerview/lint/InvalidSetHasFixedSizeDetector" ->
                "androidx/recyclerview/lint/InvalidSetHasFixedSize"
              "androidx/activity/compose/lint/ActivityResultLaunchDetector" ->
                "androidx/activity/compose/lint/ActivityResultLaunchDetector"
              "androidx/activity/lint/ActivityResultFragmentVersionDetector" ->
                "androidx/activity/lint/ActivityResultFragmentVersionDetector"
              "androidx/compose/ui/lint/ComposedModifierDetector" ->
                "androidx/compose/ui/lint/ComposedModifierDetector"
              "androidx/work/lint/BadConfigurationProviderIssueDetector" ->
                "androidx/work/lint/BadConfigurationProvider"
              "androidx/startup/lint/InitializerConstructorDetector" ->
                "androidx/startup/lint/InitializerConstructor"
              "androidx/startup/lint/EnsureInitializerMetadataDetector" ->
                "androidx/startup/lint/EnsureInitializerMetadata"
              "androidx/compose/ui/test/manifest/lint/GradleDebugConfigurationDetector" ->
                // Typo: androix
                "androix/compose/ui/test/manifest/lint/GradleDebugConfigurationDetector"
              else -> {
                // All the appcompat lint check tests are in the wrong package; fix that here
                if (
                  detectorPath.startsWith("androidx/appcompat/") &&
                    !detectorPath.startsWith("androidx/appcompat/lint/")
                ) {
                  "androidx/appcompat/lint/" + detectorPath.substring("androidx/appcompat/".length)
                } else {
                  detectorPath
                }
              }
            }
          } else {
            detectorPath
          }

        url.append(path)
        if (test) {
          url.append("Test")
        }
        url.append(extension)

        if (!view) {
          // gitiles doesn't support fetching text directly, but we can get it as base64 encoded
          // data
          url.append("?format=TEXT")
        }

        return url.toString()
      }
    }

    enum class Type {
      JAR,
      AAR;

      fun getExtension(): String {
        return when (this) {
          JAR -> DOT_JAR
          AAR -> DOT_AAR
        }
      }
    }

    class MavenCentralLibrary(
      val group: String,
      val artifact: String,
      val type: Type = Type.JAR,
      val projectUrl: String = "",
      val sourceUrl: String = "",
      val testUrl: String = "",
      val sourceContentUrl: String = "",
      val testContentUrl: String = "",
      lintLibrary: Boolean
    ) : Library("$group:$artifact", lintLibrary) {
      override fun getUrl(
        view: Boolean,
        test: Boolean,
        issue: Issue,
        detectorPath: String,
        sourceSet: String,
        extension: String
      ): String {
        val url = StringBuilder()
        if (view) {
          if (test) {
            url.append(testUrl)
          } else {
            url.append(sourceUrl)
          }
        } else {
          if (test) {
            url.append(testContentUrl)
          } else {
            url.append(sourceContentUrl)
          }
        }
        url.append('/')

        val path =
          if (test) {
            // A few detectors put their tests in weird places; work around this:
            when (detectorPath) {
              // Stuff from the vanniktech checks
              "com/vanniktech/lintrules/kotlin/KotlinRequireNotNullUseMessageDetector" ->
                "com/vanniktech/lintrules/rxjava2/KotlinRequireNotNullUseMessageDetector"

              // Stuff from the slack lint checks:
              "slack/lint/mocking/ErrorProneDoNotMockDetector" ->
                "slack/lint/mocking/DoNotMockUsageDetector"
              "slack/lint/mocking/MockDetector" -> "slack/lint/mocking/DoNotMockMockDetector"
              else -> {
                detectorPath
              }
            }
          } else {
            detectorPath
          }

        url.append(path)
        if (test) url.append("Test")
        url.append(extension)
        return url.toString()
      }

      fun getVersions(client: LintClient): List<String> {
        val gc = GradleCoordinate.parseCoordinateString("$group:$artifact:+")!!
        return getLatestVersionFromRemoteRepo(client, gc)
      }

      fun getUrl(version: String, extension: String) =
        URL(getUrl(version).toExternalForm().substringBeforeLast('.') + extension)

      fun getUrl(version: String): URL {
        val groupPath = group.replace('.', '/')
        val ext = type.getExtension()
        return URL(
          "https://search.maven.org/remotecontent?filepath=$groupPath/$artifact/$version/$artifact-$version$ext"
        )
        // https://search.maven.org/remotecontent?filepath=com/faithlife/android-lint/1.1.6/android-lint-1.1.6.aar
        // https://search.maven.org/remotecontent?filepath=com/faithlife/android-lint/1.1.5/android-lint-1.1.5-debug.aar
        // https://search.maven.org/remotecontent?filepath=com/faithlife/android-lint/1.1.5/android-lint-1.1.5-release.aar
        // https://search.maven.org/remotecontent?filepath=com/vanniktech/lint-rules-android/0.22.0/lint-rules-android-0.22.0.aar
        // https://search.maven.org/remotecontent?filepath=com/vanniktech/lint-rules-android/0.22.0/lint-rules-android-0.22.0.jar
        // https://search.maven.org/remotecontent?filepath=com/github/guilhe/styling-lint/2.0.1/styling-lint-2.0.1.aar
      }

      private fun getLatestVersionFromRemoteRepo(
        client: LintClient,
        dependency: GradleCoordinate
      ): List<String> {
        val groupId = dependency.groupId
        val artifactId = dependency.artifactId
        val query = StringBuilder()
        val encoding = Charsets.UTF_8.name()
        try {
          query.append("https://search.maven.org/solrsearch/select?q=g:%22")
          query.append(URLEncoder.encode(groupId, encoding))
          query.append("%22+AND+a:%22")
          query.append(URLEncoder.encode(artifactId, encoding))
        } catch (e: UnsupportedEncodingException) {
          return emptyList()
        }
        query.append("%22&core=gav")
        query.append("&wt=json")

        val response =
          try {
            println("Reading $query")
            readUrlDataAsString(client, query.toString(), 20000) ?: return emptyList()
          } catch (e: SocketTimeoutException) {
            println("Couldn't download $query; read timed out")
            return emptyList()
          }

        // Look for version info:  This is just a cheap skim of the above JSON results.
        var index = response.indexOf("\"response\"")
        val versions = mutableListOf<String>()
        while (index != -1) {
          index = response.indexOf("\"v\":", index)
          if (index != -1) {
            index += 4
            val start = response.indexOf('"', index) + 1
            val end = response.indexOf('"', start + 1)
            if (start in 0 until end) {
              val substring = response.substring(start, end)
              try {
                // Try parsing to see if it's a valid version
                Version.parse(substring)
                versions.add(substring)
              } catch (ignore: IllegalArgumentException) {
                // Not a version
              }
            }
          }
        }

        return versions
      }
    }

    /** Data about a specific version of the library */
    class LibraryVersionEntry(
      val artifact: String,
      val jarBytes: ByteArray,
      val pom: String,
      val date: String,
      val lintLibrary: Boolean
    ) {
      val id = artifact.substringBeforeLast(':')
      val version = Version.parse(artifact.substringAfterLast(':').removeSuffix("-lint"))
      var registry: IssueRegistry? = null
    }

    private fun addLintIssueRegistry(
      into: MutableMap<String, LibraryVersionEntry>,
      file: File,
      artifact: String?,
      bytes: ByteArray = file.readBytes()
    ) {
      artifact ?: return
      val base = file.path.removeSuffix(".jar").removeSuffix(".aar").removeSuffix("-lint")
      val pomFile = File("$base.pom")
      val pom = if (pomFile.isFile) pomFile.readText() else ""
      val dateFile = File("$base.date")
      val date =
        if (dateFile.isFile) {
          dateFile.readText()
        } else {
          ""
        }
      val lintLibrary = !File("$base.aar").exists()
      into[artifact] = LibraryVersionEntry(artifact, bytes, pom, date, lintLibrary)
    }

    private fun addLintIssueRegistries(
      into: MutableMap<String, LibraryVersionEntry>,
      file: File,
      searchWithinArchives: Boolean,
      allJarsAreLintChecks: Boolean
    ) {
      if (file.isDirectory) {
        val children = file.listFiles() ?: return
        children.forEach { child ->
          addLintIssueRegistries(into, child, searchWithinArchives, allJarsAreLintChecks)
        }
      } else if (file.isFile) {
        // file length of 0 means it's not a valid jar but is deliberately there
        // as a cache entry to record the fact that this library does *not*
        // have a nested lint issue registry
        val fileName = file.name
        if (fileName.endsWith(DOT_JAR) && file.length() > 0) {
          val artifact =
            if (fileName.count { it == ':' } == 2) {
              fileName.removeSuffix(DOT_JAR).removeSuffix(DOT_AAR)
            } else {
              file.path.path2maven()
            }
          addLintIssueRegistry(into, file, artifact)
        } else if (searchWithinArchives && fileName.endsWith(DOT_AAR) && file.length() > 0) {
          JarFile(file).use { jarFile ->
            val lintJar = jarFile.getJarEntry("lint.jar")
            if (lintJar != null) {
              jarFile.getInputStream(lintJar).use { stream ->
                val bytes = ByteStreams.toByteArray(stream)
                val artifact =
                  if (fileName.count { it == ':' } == 2) {
                    fileName.removeSuffix(DOT_AAR)
                  } else {
                    file.path.path2maven()
                  }
                addLintIssueRegistry(into, file, artifact, bytes)
              }
            }
          }
        } else if (searchWithinArchives && fileName.endsWith(DOT_ZIP)) {
          ZipFile(file).use { zipFile ->
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
              val zipEntry = entries.nextElement()
              if (zipEntry.isDirectory) {
                continue
              }
              val name = zipEntry.name
              if (name.endsWith(DOT_JAR) && name.contains("lint")) {
                zipFile.getInputStream(zipEntry).use { stream ->
                  val bytes = ByteStreams.toByteArray(stream)
                  addLintIssueRegistry(into, file, name.path2maven(), bytes)
                }
              } else if (name.endsWith(DOT_AAR)) {
                zipFile.getInputStream(zipEntry).use { stream ->
                  val aarBytes = ByteStreams.toByteArray(stream)
                  JarInputStream(ByteArrayInputStream(aarBytes)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                      val entryName = entry.name
                      if (entryName == FN_LINT_JAR) {
                        val bytes = ByteStreams.toByteArray(zis)
                        addLintIssueRegistry(into, file, name.path2maven(), bytes)
                      }
                      entry = zis.nextEntry
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    private fun String.path2maven(): String? {
      // m2repository/androidx/resourceinspection/resourceinspection-annotation/1.1.0-alpha01/resourceinspection-annotation-1.1.0-alpha01.jar.sha1
      //    => androidx.resourceinspection:resourceinspection-annotation:1.1.0-alpha01
      val name = this.replace("\\", "/")
      val index = name.indexOf("m2repository/").let { if (it == -1) name.indexOf("m2/") else it }
      if (index != -1) {
        return name.substring(index).split("/").let {
          it.subList(1, it.size - 3).joinToString(".") +
            ":" +
            it[it.size - 3] +
            ":" +
            it[it.size - 2]
        }
      }

      return null
    }

    private class DocIssueRegistry(original: IssueRegistry, val library: Library) :
      IssueRegistry() {
      override val issues: List<Issue> = original.issues
      override val api: Int = original.api
      override val minApi: Int = original.minApi
      override val maxApi: Int = original.maxApi
      override val vendor: Vendor? = original.vendor
      override val deletedIssues: List<String> = original.deletedIssues
      override val isUpToDate: Boolean = true

      override fun cacheable(): Boolean = true

      init {
        for (issue in issues) {
          issue.registry = this
        }
      }
    }

    private const val ARG_HELP = "--help"
    private const val ARG_SINGLE_DOC = "--single-doc"
    private const val ARG_MD = "--md"
    private const val ARG_LINT_JARS = "--lint-jars"
    private const val ARG_ISSUES = "--issues"
    private const val ARG_SOURCE_URL = "--source-url"
    private const val ARG_TEST_SOURCE_URL = "--test-url"
    private const val ARG_INCLUDE_STATS = "--include-stats"
    private const val ARG_NO_SUPPRESS_INFO = "--no-suppress-info"
    private const val ARG_NO_EXAMPLES = "--no-examples"
    private const val ARG_NO_SOURCE_LINKS = "--no-links"
    private const val ARG_INCLUDE_BUILTINS = "--builtins"
    private const val ARG_NO_SEVERITY = "--no-severity"
    private const val ARG_NO_INDEX = "--no-index"
    private const val ARG_OUTPUT = "--output"
    /** Whether to search maven.google.com for lint libraries to include */
    private const val ARG_GMAVEN = "--gmaven"
    /** Whether to search mavenCentral for lint libraries to include */
    private const val ARG_MAVEN_CENTRAL = "--maven-central"

    private fun markdownTable(vararg rows: Pair<String, String?>): String {
      val sb = StringBuilder()
      var first = true

      var keyWidth = 0
      var rhsWidth = 0
      for (row in rows) {
        val value = row.second
        value ?: continue
        val key = row.first
        keyWidth = max(keyWidth, key.length)
        rhsWidth = max(rhsWidth, value.length)
      }
      keyWidth = min(keyWidth, 15)

      val formatString = "%-${keyWidth}s | %s\n"

      for (row in rows) {
        val value = row.second ?: continue
        val key = row.first
        val formatted = String.format(formatString, key, value)
        sb.append(formatted)
        if (first) {
          // Need a separator to get markdown to treat this as a table
          first = false
          for (i in 0 until keyWidth + 1) sb.append('-')
          sb.append('|')
          for (i in 0 until min(72 - keyWidth - 2, rhsWidth + 1)) sb.append('-')
          sb.append('\n')
        }
      }

      return sb.toString()
    }

    // In Markdeep, a definition list looks better than a table
    private fun markdeepTable(vararg rows: Pair<String, String?>): String {
      val sb = StringBuilder()
      for (row in rows) {
        val value = row.second ?: continue
        val key = row.first

        sb.append("$key\n:   $value\n")
      }

      return sb.toString()
    }

    fun printUsage(fromLint: Boolean, out: PrintWriter = System.out.printWriter()) {
      val command = if (fromLint) "lint --generate-docs" else "lint-issue-docs-generator"
      out.println("Usage: $command [flags] --output <directory or file>]")
      out.println()
      out.println("Flags:")
      out.println()
      Main.printUsage(
        out,
        arrayOf(
          ARG_HELP,
          "This message.",
          "$ARG_OUTPUT <dir>",
          "Sets the path to write the documentation to. Normally a directory, unless $ARG_SINGLE_DOC " +
            "is also specified",
          ARG_SINGLE_DOC,
          "Instead of writing one page per issue into a directory, write a single page containing " +
            "all the issues",
          ARG_MD,
          "Write to plain Markdown (.md) files instead of Markdeep (.md.html)",
          ARG_INCLUDE_BUILTINS,
          "Generate documentation for the built-in issues. This is implied if $ARG_LINT_JARS is not specified",
          "$ARG_LINT_JARS <jar-path>",
          "Read the lint issues from the specific path (separated by $pathSeparator of custom jar files",
          "$ARG_ISSUES [issues]",
          "Limits the issues documented to the specific (comma-separated) list of issue id's",
          "$ARG_SOURCE_URL <url-prefix> <path>",
          "Searches for the detector source code under the given source folder or folders separated by " +
            "semicolons, and if found, prefixes the path with the given URL prefix and includes " +
            "this source link in the issue documentation.",
          "$ARG_TEST_SOURCE_URL <url-prefix> <path>",
          "Like $ARG_SOURCE_URL, but for detector unit tests instead. These must be named the same as " +
            "the detector class, plus `Test` as a suffix.",
          ARG_NO_INDEX,
          "Do not include index files",
          ARG_NO_SUPPRESS_INFO,
          "Do not include suppression information",
          ARG_NO_EXAMPLES,
          "Do not include examples pulled from unit tests, if found",
          ARG_NO_SOURCE_LINKS,
          "Do not include hyperlinks to detector source code",
          ARG_NO_SEVERITY,
          "Do not include the red, orange or green informational boxes showing the severity of each issue",
        ),
        false
      )
    }

    /**
     * Copied from com.android.tools.lint.checks.infrastructure.ClassName in lint's testing library,
     * but with the extra logic to insert "test.kt" as the default name for Kotlin tests without a
     * top level class
     */
    class ClassName(source: String) {
      val packageName: String?
      val className: String?

      init {
        val withoutComments = stripComments(source)
        packageName = getPackage(withoutComments)
        className = getClassName(withoutComments)
      }

      fun relativePath(extension: String): String? {
        return when {
          className == null ->
            if (DOT_KT == extension)
              if (packageName != null) packageName.replace('.', '/') + "/test.kt" else "test.kt"
            else null
          packageName != null -> packageName.replace('.', '/') + '/' + className + extension
          else -> className + extension
        }
      }

      @Suppress("LocalVariableName")
      private fun stripComments(source: String, stripLineComments: Boolean = true): String {
        val sb = StringBuilder(source.length)
        var state = 0
        val INIT = 0
        val INIT_SLASH = 1
        val LINE_COMMENT = 2
        val BLOCK_COMMENT = 3
        val BLOCK_COMMENT_ASTERISK = 4
        val IN_STRING = 5
        val IN_STRING_ESCAPE = 6
        val IN_CHAR = 7
        val AFTER_CHAR = 8
        for (c in source) {
          when (state) {
            INIT -> {
              when (c) {
                '/' -> state = INIT_SLASH
                '"' -> {
                  state = IN_STRING
                  sb.append(c)
                }
                '\'' -> {
                  state = IN_CHAR
                  sb.append(c)
                }
                else -> sb.append(c)
              }
            }
            INIT_SLASH -> {
              when {
                c == '*' -> state = BLOCK_COMMENT
                c == '/' && stripLineComments -> state = LINE_COMMENT
                else -> {
                  state = INIT
                  sb.append('/') // because we skipped it in init
                  sb.append(c)
                }
              }
            }
            LINE_COMMENT -> {
              when (c) {
                '\n' -> state = INIT
              }
            }
            BLOCK_COMMENT -> {
              when (c) {
                '*' -> state = BLOCK_COMMENT_ASTERISK
              }
            }
            BLOCK_COMMENT_ASTERISK -> {
              state =
                when (c) {
                  '/' -> INIT
                  '*' -> BLOCK_COMMENT_ASTERISK
                  else -> BLOCK_COMMENT
                }
            }
            IN_STRING -> {
              when (c) {
                '\\' -> state = IN_STRING_ESCAPE
                '"' -> state = INIT
              }
              sb.append(c)
            }
            IN_STRING_ESCAPE -> {
              sb.append(c)
              state = IN_STRING
            }
            IN_CHAR -> {
              if (c != '\\') {
                state = AFTER_CHAR
              }
              sb.append(c)
            }
            AFTER_CHAR -> {
              sb.append(c)
              if (c == '\\') {
                state = INIT
              }
            }
          }
        }

        return sb.toString()
      }
    }

    fun getPackage(source: String): String? {
      val matcher = PACKAGE_PATTERN.matcher(source)
      return if (matcher.find()) {
        matcher.group(1).trim { it <= ' ' }
      } else {
        null
      }
    }

    fun getClassName(source: String): String? {
      val matcher = CLASS_PATTERN.matcher(source.replace('\n', ' '))
      var start = 0
      while (matcher.find(start)) {
        val cls = matcher.group(2)
        val groupStart = matcher.start(1)

        // Make sure this "class" reference isn't part of an annotation on the class
        // referencing a class literal -- Foo.class, or in Kotlin, Foo::class.java)
        if (groupStart == 0 || source[groupStart - 1] != '.' && source[groupStart - 1] != ':') {
          val trimmed = cls.trim { it <= ' ' }
          val typeParameter = trimmed.indexOf('<')
          return if (typeParameter != -1) {
            trimmed.substring(0, typeParameter)
          } else {
            trimmed
          }
        }
        start = matcher.end(2)
      }

      return null
    }

    private fun getCacheDir(): File {
      val root = File("${System.getProperty("user.home")}/Desktop/doc-cache")
      if (root.isDirectory) {
        return root
      }
      return File(System.getProperty("java.io.tmpdir"), "lint-doc-cache")
    }

    fun getSourceCache(): File {
      val dir = File(getCacheDir(), "sources")
      if (!dir.isDirectory) {
        dir.mkdirs()
      }
      return dir
    }

    fun getGmavenCache(): File {
      val dir = File(getCacheDir(), "gmaven")
      if (!dir.isDirectory) {
        dir.mkdirs()
      }
      return dir
    }

    private fun getMavenCentralCache(): File {
      val dir = File(getCacheDir(), "mavenCentral")
      if (!dir.isDirectory) {
        dir.mkdirs()
      }
      return dir
    }
  }

  class ExampleFile(val path: String?, val source: String, val language: String)

  class Example(
    val testClass: String,
    val testMethod: String,
    val files: List<ExampleFile>,
    val output: String?,
    val inferred: Boolean = true
  )

  inner class IssueData(val issue: Issue) {
    var detectorSource: String? = null
    var detectorTestSource: String? = null
    var detectorSourceFile: File? = null
    var detectorTestSourceFile: File? = null

    var sourceUrl: String? = null
    var testUrl: String? = null

    var example: Example? = null
    var suppressExample: Example? = null
    var quickFixable: Boolean = false
    var copyrightYear: Int = -1

    fun library(): Library? = (issue.registry as? DocIssueRegistry)?.library

    fun initialize() {
      val library = library() ?: return
      val detectorClass = issue.implementation.detectorClass
      val detectorPath =
        detectorClass.name.replace('.', '/').let {
          val innerClass = it.indexOf('$')
          if (innerClass == -1) it else it.substring(0, innerClass)
        }

      findDetectorSource(library, detectorPath, test = false) { viewUrl, sourceFile, source ->
        sourceUrl = viewUrl
        detectorSourceFile = sourceFile
        detectorSource = source
        copyrightYear = findCopyrightYear(source.lines())
      }
      findDetectorSource(library, detectorPath, test = true) { viewUrl, sourceFile, source ->
        testUrl = viewUrl
        detectorTestSourceFile = sourceFile
        detectorTestSource = source
        initializeExamples(this)
      }
    }

    private fun findDetectorSource(
      library: Library,
      detectorPath: String,
      test: Boolean,
      assign: (String, File, String) -> Unit
    ) {
      var url: String? = null
      val cacheDir = getSourceCache()
      val testResult =
        // We don't know if the detector is in a .kt or .java file, and we also don't know if it's
        // in
        // src/main/java or
        // src/main/kotlin, so look in all these combinations (results are cached)
        findDetectorSource(library, detectorPath, test, "java", ".kt", cacheDir)?.also {
          url = library.getUrl(true, test, issue, detectorPath, "java", ".kt")
        }
          ?: findDetectorSource(library, detectorPath, test, "java", ".java", cacheDir)?.also {
            url = library.getUrl(true, test, issue, detectorPath, "java", ".java")
          }
          ?: findDetectorSource(library, detectorPath, test, "kotlin", ".kt", cacheDir)?.also {
            url = library.getUrl(true, test, issue, detectorPath, "kotlin", ".kt")
          }
          ?: run {
            println("Couldn't find ${if (test) "test " else ""}sources for $detectorPath")
            null
          }
      if (testResult != null) {
        assign(url!!, testResult.first, testResult.second)
      }
    }

    private fun findDetectorSource(
      library: Library,
      detectorPath: String,
      test: Boolean,
      sourceSet: String,
      extension: String,
      cacheDir: File
    ): Pair<File, String>? {
      val relative =
        (if (test) "test" else "main") +
          "/" +
          sourceSet +
          "/" +
          detectorPath +
          (if (test) "Test" else "") +
          extension

      val cached = File(cacheDir, relative)
      if (cached.isFile) {
        if (cached.length() == 0L) {
          return null
        }
        return cached to cached.readText()
      }

      if (SKIP_NETWORK) return null

      cached.parentFile.mkdirs()
      val url = library.getUrl(view = false, test, issue, detectorPath, sourceSet, extension)
      print("Reading $url")
      val bytes =
        try {
          URL(url).readBytes()
        } catch (exception: IOException) {
          println(": failed")
          cached.createNewFile()
          return null
        }
      println(": OK")
      var source = String(bytes)
      if (url.endsWith("?format=TEXT")) {
        source = String(Base64.getDecoder().decode(source))
      }

      cached.writeText(source)
      return cached to source
    }
  }

  private fun getFileName(issueId: String, format: DocFormat): String =
    "$issueId${format.extension}"

  private fun getFileName(issue: Issue, format: DocFormat): String {
    val fileName = getFileName(issue.id, format)
    // If there are multiple issues with the same id, link them together
    val registries = issueToRegistry[issue.id]!!
    if (registries.size > 1) {
      val index = registries.indexOf(issue.registry)
      if (index > 0) {
        return "${issue.id}-${index+1}${format.extension}"
      }
    }

    return fileName
  }

  enum class DocFormat(val extension: String, val header: String = "", val footer: String = "") {
    @Suppress("SpellCheckingInspection")
    MARKDEEP(
      extension = ".md.html",
      header = "<meta charset=\"utf-8\">\n",
      footer =
        "<!-- Markdeep: -->" +
          "<style class=\"fallback\">body{visibility:hidden;white-space:pre;font-family:monospace}</style>" +
          "<script src=\"markdeep.min.js\" charset=\"utf-8\"></script>" +
          "<script src=\"https://morgan3d.github.io/markdeep/latest/markdeep.min.js\" charset=\"utf-8\"></script>" +
          "<script>window.alreadyProcessedMarkdeep||(document.body.style.visibility=\"visible\")</script>"
    ),
    MARKDOWN(".md"),
    HTML(".html")
  }
}
