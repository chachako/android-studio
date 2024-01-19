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

package com.android.tools.lint.checks.infrastructure

import com.android.SdkConstants.ATTR_IGNORE
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.lint.LintIssueDocGenerator
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.XmlParser
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.isKotlin
import com.android.utils.iterator
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiAnnotationOwner
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClassInitializer
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiImportStatementBase
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiPackageStatement
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiParameterList
import com.intellij.psi.util.PsiTreeUtil
import java.io.File
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.w3c.dom.Element

/**
 * Test mode which looks at the expected error locations (from the default test mode) and based on
 * those locations, inserts suppress directives and checks that all the errors are now removed from
 * the report. This helps track down problems with detectors which are not supporting suppressing
 * errors at the report sites.
 *
 * StringFormatDetectorTest.testIncremental StringFormatDetectorTest.testAll
 * UnusedResourceDetectorTest.testMultiProject2
 */
class SuppressibleTestMode :
  SourceTransformationTestMode(
    description = "Suppressible",
    "TestMode.SUPPRESSIBLE",
    "suppressible",
  ) {
  override val diffExplanation: String =
    // first line shorter: expecting to prefix that line with
    // "org.junit.ComparisonFailure: "
    """
        Lint checks are suppressible
        using `@Suppress` and `@SuppressWarnings` (and in XML, `tools:ignore`).
        This requires the incident to be reported with the nearest AST node,
        such that when reporting it can also peek at the source code to see
        if it finds a surrounding suppression directive.

        This test mode introduces suppression directives near all the reported
        locations and then makes sure that the test output is empty (so all
        the warnings have been suppressed).

        In the unlikely event that your lint check is actually doing something
        specific to suppressions, you can turn off this test mode using
        `.skipTestModes($fieldName)`.
        """
      .trimIndent()

  override fun applies(context: TestModeContext): Boolean {
    // No point doing this check for all the false-positive checks (the expectsClean() tests)
    val defaultResults = context.results?.get(DEFAULT) ?: return false
    val output = defaultResults.output
    return getIncidents(output, context.task).any()
  }

  private fun applies(path: String): Boolean {
    return path.endsWith(DOT_KT) || path.endsWith(DOT_JAVA) || path.endsWith(DOT_XML)
  }

  private fun getIncidents(
    lintTextReport: String,
    task: TestLintTask? = null,
  ): List<LintIssueDocGenerator.Companion.ReportedIncident> {
    // If this unit test is referencing lint warnings that are not suppressible,
    // don't attempt to enforce suppressions
    if (
      lintTextReport.contains("is not allowed to be suppressed") &&
        lintTextReport.contains(" [LintError]")
    ) {
      return emptyList()
    }

    val issues = task?.checkedIssues ?: emptyList()

    return LintIssueDocGenerator.getOutputIncidents(lintTextReport)
      // Skip internal instances
      .filter { !it.id.startsWith("_") }
      // Only include problems in XML, Java and Kotlin files
      .filter { applies(it.path) }
      .filter {
        // Skip issues that analyze class files: @Suppress annotations aren't present in the
        // bytecode.
        val issue = issues.firstOrNull { issue -> it.id == issue.id }
        val scope = issue?.implementation?.scope
        scope == null ||
          !(scope.contains(Scope.CLASS_FILE) ||
            scope.contains(Scope.ALL_CLASS_FILES) ||
            scope.contains(Scope.JAVA_LIBRARIES))
      }
  }

  override fun before(context: TestModeContext): Any? {
    val defaultResults =
      context.results?.get(DEFAULT)
        ?: error(
          "The SuppressibleTestMode only works in conjunction with the DEFAULT test mode (which it uses to find error positions to suppress)"
        )

    val client = context.driver?.client ?: TestLintClient().apply { task = context.task }
    val changed = rewrite(defaultResults.output, context.projectFolders, client.getSdkHome())
    return if (changed) null else CANCEL
  }

  fun rewrite(lintTextReport: String, projectFolders: List<File>, sdkHome: File?): Boolean {
    val outputIncidents = getIncidents(lintTextReport)
    if (outputIncidents.isEmpty()) {
      return false
    }

    val paths =
      outputIncidents.map { it.path.dos2unix().removePrefix("../").removePrefix(("../")) }.toSet()
    val pathToFile = mutableMapOf<String, File>()
    projectFolders.forEach { root ->
      root
        .walk()
        .filter { it.isFile && applies(it.path) }
        .forEach {
          for (path in paths) {
            if (it.path.dos2unix().endsWith(path)) {
              pathToFile[path] = it
            }
          }
        }
    }

    var changed = false
    val disposables = mutableListOf<Disposable>()

    try {
      val allContexts = mutableMapOf<File, JavaContext>()
      for (dir in projectFolders) {
        val (javaContexts, disposable) = parse(dir = dir, sdkHome = sdkHome)
        disposables.add(disposable)
        for (javaContext in javaContexts) {
          allContexts[javaContext.file] = javaContext
        }
      }

      for ((path, file) in pathToFile) {
        val source = file.readText()
        val fileIncidents = outputIncidents.filter { it.path.endsWith(path) }
        assert(fileIncidents.isNotEmpty())
        val edits =
          if (path.endsWith(DOT_XML)) {
            rewriteXml(file, source, fileIncidents)
          } else if (path.endsWith(DOT_JAVA) || path.endsWith(DOT_KT)) {
            if (sdkHome != null) {
              val javaContext = allContexts[file] ?: error("Didn't find JavaContext for $file")
              rewriteKotlinOrJava(javaContext, source, fileIncidents)
            } else {
              emptyList()
            }
          } else {
            error("Unexpected path $path")
          }

        if (edits.isNotEmpty()) {
          val edited = Edit.performEdits(source, edits)
          if (edited != source) {
            file.writeText(edited)
            changed = true
          }
        }
      }
    } finally {
      for (disposable in disposables) {
        Disposer.dispose(disposable)
      }
    }

    return changed
  }

  private fun rewriteXml(
    file: File,
    source: String,
    incidents: List<LintIssueDocGenerator.Companion.ReportedIncident>,
  ): List<Edit> {
    val client = TestLintClient()
    val parser = client.xmlParser
    val root = parser.parseXml(source, file)?.documentElement ?: return emptyList()

    val toolsNs = root.getAttributeNode("xmlns:tools")
    if (toolsNs != null && toolsNs.value != TOOLS_URI) {
      // Some incompatible binding of the tools namespace. Unlikely but happens
      // in a few unit tests. We're not going to bother using a different namespace
      // for the suppression tests.
      return emptyList()
    }

    val elements = mutableMapOf<Element, MutableList<String>>()
    for (incident in incidents.sortedBy { it.lineNumber }.reversed()) {
      val lineNumber = incident.lineNumber
      if (lineNumber == -1) {
        continue
      }
      var offset = getOffset(source, lineNumber)
      val column = incident.column
      if (column != -1) {
        offset += column
      }

      val element = root.find(offset, client, file, parser)
      val list = elements[element] ?: mutableListOf<String>().also { elements[element] = it }
      if (!list.contains(incident.id)) {
        list.add(incident.id)
      }
    }

    val edits = mutableListOf<Edit>()
    // Already in reverse document order
    for ((element, ids) in elements) {
      // element.hasAttributeNS(TOOLS_URI, ATTR_IGNORE)
      val attribute = element.getAttributeNodeNS(TOOLS_URI, ATTR_IGNORE)
      if (attribute != null) {
        // Already set; adjust content instead
        val offset = parser.getValueLocation(client, file, attribute).start?.offset
        if (offset != null) {
          val edit = Edit(offset, offset, "${ids.joinToString(",")},", true, 1)
          edits.add(edit)
        } else {
          // XML infra error; cannot check this element
          return emptyList()
        }
      } else {
        // No existing tools:ignore attribute on this element; insert one.
        val start = parser.getNodeStartOffset(client, file, element)
        var i = start
        while (i < source.length) {
          if (source[i] == ' ' || source[i] == '/' || source[i] == '>') {
            // Found the end
            while (
              i < source.length - 1 && source[i].isWhitespace() && source[i + 1].isWhitespace()
            ) {
              i++
            }
            val insert = " tools:ignore=\"${ids.joinToString(",")}\""
            val edit = Edit(i, i, insert, true, 1)
            edits.add(edit)
            break
          }
          i++
        }
      }
    }

    if (edits.isNotEmpty() && !source.contains("xmlns:tools=")) {
      var i = parser.getNodeStartOffset(client, file, root)
      while (i < source.length) {
        if (source[i] == ' ' || source[i] == '/' || source[i] == '>') {
          // Found the end
          val insert = " xmlns:tools=\"http://schemas.android.com/tools\""
          val edit = Edit(i, i, insert, true, 0)
          edits.add(edit)
          break
        }
        i++
      }
    }

    return edits
  }

  private fun Element.find(
    offset: Int,
    client: LintClient,
    file: File,
    parser: XmlParser,
  ): Element {
    for (child in this) {
      val start = parser.getNodeStartOffset(client, file, child)
      if (offset >= start) {
        val end = parser.getNodeEndOffset(client, file, child)
        if (offset < end) {
          return child.find(offset, client, file, parser)
        }
      }
    }

    return this
  }

  private fun rewriteKotlinOrJava(
    context: JavaContext,
    source: String,
    incidents: List<LintIssueDocGenerator.Companion.ReportedIncident>,
  ): List<Edit> {
    val root = context.uastFile ?: return emptyList()

    val elements = mutableMapOf<PsiElement, MutableList<String>>()
    for (incident in incidents.sortedBy { it.lineNumber }.reversed()) {
      val lineNumber = incident.lineNumber
      if (lineNumber == -1) {
        continue
      }
      var offset = getOffset(source, lineNumber)

      val column = incident.column
      if (column != -1) {
        offset += column
      }

      var curr = root.sourcePsi.findElementAt(offset)
      if (curr != null && curr !is PsiComment) {
        curr =
          if (isKotlin(curr)) {
            findKotlinSuppressElement(curr)
          } else {
            findJavaSuppressElement(curr)
          }
      }

      if (curr != null) {
        val list = elements[curr] ?: mutableListOf<String>().also { elements[curr] = it }
        if (!list.contains(incident.id)) {
          list.add(incident.id)
        }
      }
    }

    val edits = mutableListOf<Edit>()

    // Already in reverse document order
    for ((element, ids) in elements) {
      if (element is PsiModifierListOwner && element.hasAnnotation("java.lang.SuppressWarnings")) {
        // Update existing
        val parameters =
          element.modifierList?.findAnnotation("java.lang.SuppressWarnings")?.parameterList
            ?: continue
        val attributes = parameters.attributes
        val count = attributes.size
        val begin = attributes.firstOrNull()?.startOffset ?: continue
        if (count == 1 || ids.size > 1) {
          // Must insert { } around value since there's more than one
          val startCode = "{${ids.joinToString(", ") { "\"$it\"" }}, "
          val edit = Edit(begin, begin, startCode, true, 1)
          edits.add(edit)
          val end = attributes.last().endOffset
          val endCode = "}"
          val endEdit = Edit(end, end, endCode, true, 1)
          edits.add(endEdit)
        } else {
          val code = ids.joinToString(", ") { "\"$it\"" } + ", "
          val edit = Edit(begin, begin, code, true, 1)
          edits.add(edit)
        }
        continue
      } else if (element is KtDeclaration) {
        val annotation =
          element.annotationEntries
            .firstOrNull { it.shortName?.identifier?.contains("Suppress") == true }
            ?.valueArgumentList
        if (annotation != null) {
          val begin = annotation.arguments.first().startOffset
          val code = ids.joinToString(", ") { "\"$it\"" } + ", "
          val edit = Edit(begin, begin, code, true, 1)
          edits.add(edit)
          continue
        }
      }

      val offset = element.startOffset

      val suppress =
        Context.Companion.getSuppressionDirective(
          Context.SUPPRESS_JAVA_COMMENT_PREFIX,
          source,
          offset,
        )
      if (suppress != null) {
        var start = source.lastIndexOf(Context.SUPPRESS_JAVA_COMMENT_PREFIX, offset)
        if (start != -1) {
          start += Context.SUPPRESS_JAVA_COMMENT_PREFIX.length
          val code = ids.joinToString(",") + ","
          val edit = Edit(start, start, code, true, 1)
          edits.add(edit)
          continue
        }
      }

      val code =
        when (element) {
          is PsiAnnotationOwner,
          is PsiModifierListOwner ->
            "@SuppressWarnings(${if (ids.size > 1) "{" else ""}${ids.joinToString(", ") { "\"$it\"" }}${if (ids.size > 1) "}" else ""}) "
          is KtAnnotated ->
            "@${if (element is KtPackageDirective) "file:" else ""}Suppress(${ids.joinToString(", ") { "\"$it\"" }}) "
          else -> {
            val indent = getIndent(source, offset)
            "//noinspection ${ids.joinToString(",")}\n$indent"
          }
        }
      val edit = Edit(offset, offset, code, true, 1)
      edits.add(edit)
    }

    return edits
  }

  private fun getIndent(source: String, offset: Int): String {
    val lineBegin = source.lastIndexOf('\n', offset) + 1
    return source.substring(lineBegin, offset)
  }

  override fun sameOutput(expected: String, actual: String, type: OutputKind): Boolean {
    return getIncidents(actual).none {
      val path = it.path
      (path.endsWith(DOT_KT) || path.endsWith(DOT_JAVA) || path.endsWith(DOT_XML)) &&
        it.lineNumber != -1
    }
  }

  private fun getOffset(source: String, lineNumber: Int): Int {
    // Map to offset
    var offset = 0
    for (i in 1 until lineNumber) {
      val end = source.indexOf('\n', offset)
      if (end == -1) {
        offset = end
        break
      } else {
        offset = end + 1
      }
    }

    return offset
  }

  private fun findKotlinSuppressElement(element: PsiElement): PsiElement? {
    return PsiTreeUtil.findFirstParent(element, true) { it.isKotlinSuppressLintTarget() }
  }

  private fun PsiElement.isKotlinSuppressLintTarget(): Boolean {
    return this is KtDeclaration &&
      this !is KtFunctionLiteral &&
      this !is KtDestructuringDeclaration &&
      (this !is KtParameter || !this.isLambdaParameter) &&
      this !is KtClassInitializer ||
      // We also allow placing suppression via comments on imports and package statements
      this is KtImportDirective ||
      this is KtPackageDirective
  }

  /**
   * Like [findJavaAnnotationTarget], but also includes other PsiElements where we can place
   * suppression comments
   */
  private fun findJavaSuppressElement(element: PsiElement): PsiElement? {
    // In addition to valid annotation targets we can also place suppress directives
    // using comments on import or package statements
    return findJavaAnnotationTarget(element)
      ?: element.getParentOfType<PsiImportStatementBase>(false)
      ?: element.getParentOfType<PsiPackageStatement>(false)
  }

  private fun findJavaAnnotationTarget(element: PsiElement?): PsiModifierListOwner? {
    val modifier = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner::class.java, false)
    return if (
      modifier is PsiClassInitializer ||
        modifier is PsiAnonymousClass ||
        modifier is PsiParameter && modifier.isLambdaParameter()
    ) {
      findJavaAnnotationTarget(modifier.parent)
    } else {
      modifier
    }
  }

  private fun PsiParameter.isLambdaParameter(): Boolean {
    val parent = parent
    return parent is PsiParameterList && parent.getParent() is PsiLambdaExpression
  }
}
