/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_XML
import com.android.tools.lint.LintIssueDocGenerator.Companion.ReportedIncident
import com.android.tools.lint.LintIssueDocGenerator.IssueData
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.TextFormat
import java.io.File
import kotlin.math.max
import kotlin.math.min

// Support for extracting example metadata for issues into a JSONL file.

fun appendExample(jsonFile: File?, issueData: IssueData) {
  jsonFile ?: return
  val example = issueData.example ?: return
  val output = example.expected ?: return // null output: suppress example, skip it

  val max = 8192
  val tooLarge = example.files.firstOrNull { it.source.length > max }
  if (tooLarge != null) {
    println(
      "Error: Example too large for ${issueData.issue.id} (from example ${example.testClass}#${example.testMethod}, " +
        "source file ${tooLarge.path} of length ${tooLarge.source.length}"
    )
    return
  }

  appendExample(
    jsonFile,
    example.testClass,
    example.testMethod,
    output,
    example.files
      .mapNotNull {
        if (it.path == null) {
          null
        } else Pair(it.path, it.source)
      }
      .toList(),
    issueData.issue,
    issueData,
  )
}

private fun String.stripRepeatedBlankLines(): String {
  // Allow a blank line but not more than one (unless at the beginning or end)
  val sb = StringBuilder(this.length)
  var lastBlank = true
  for (line in this.lines()) {
    val blank = line.isBlank()
    if (!blank || !lastBlank) {
      sb.append(line).append('\n')
    }
    lastBlank = blank
  }
  sb.setLength(max(0, sb.length - 1))
  return sb.toString()
}

private fun appendExample(
  file: File,
  className: String,
  methodName: String,
  expected: String,
  files: List<Pair<String, String>>,
  primaryIssue: Issue,
  issueData: IssueData,
) {
  val incidents: List<ReportedIncident> =
    LintIssueDocGenerator.getOutputIncidents(expected)
      // Don't include internal-only issues
      .filter { !it.id.startsWith("_") }
  if (incidents.isEmpty()) {
    return
  }

  // Strip out comments
  val shiftedLineNumbers = mutableMapOf<ReportedIncident, ReportedIncident>()

  // Strip comments
  val filesWithoutComments =
    files.map { pathAndSource ->
      val path = pathAndSource.first
      val original = pathAndSource.second
      val extension = ".${path.substringAfterLast(".")}"

      // Remove comments, and if comments are removed, also go and adjust
      // all the line numbers in the incidents list correspondingly
      val source = stripComments(original, extension).stripRepeatedBlankLines()
      if (source.length < original.length) {
        for (incident in incidents) {
          val sourceLine = incident.sourceLine1 ?: ""
          if (incident.path == path && sourceLine.isNotBlank()) {
            val strippedSourceLine = stripComments(sourceLine, extension).trim()
            if (strippedSourceLine.isEmpty()) {
              // The error line seems to be on a comment; for these cases
              // we cannot remove the comments -- give up and use originals
              appendExample(file, className, methodName, incidents, files, primaryIssue, issueData)
              return
            }

            val targetLineNumber = incident.lineNumber
            // Count how many occurrences until we find this line in the original source
            var count = 0
            var offset = 0
            var line = 1

            while (true) {
              val start = offset
              offset = original.indexOf(strippedSourceLine, offset)
              if (offset == -1) {
                break
              } else {
                count++
                offset += strippedSourceLine.length
                for (c in start until offset) {
                  if (original[c] == '\n') {
                    line++
                  }
                }
                if (line >= targetLineNumber) {
                  break
                }
              }
            }

            // Now find the same line the same number of times in the stripped file
            offset = 0
            while (true) {
              offset = source.indexOf(strippedSourceLine, offset)
              if (offset == -1) {
                break
              } else {
                count--
                if (count == 0) {
                  val lineNumber = source.getLineNumber(offset)
                  if (lineNumber != incident.lineNumber) {
                    val copy = incident.copy(lineNumber = lineNumber)
                    shiftedLineNumbers[incident] = copy
                  }
                  break
                }
                offset += strippedSourceLine.length
              }
            }
          }
        }

        Pair(path, source)
      } else {
        pathAndSource
      }
    }

  val incidentsWithShiftedLineNumbers = incidents.map { shiftedLineNumbers[it] ?: it }

  appendExample(
    file,
    className,
    methodName,
    incidentsWithShiftedLineNumbers,
    filesWithoutComments,
    primaryIssue,
    issueData,
  )
}

private fun appendExample(
  jsonFile: File,
  className: String,
  methodName: String,
  incidents: List<ReportedIncident>,
  files: List<Pair<String, String>>,
  primaryIssue: Issue,
  issueData: IssueData,
) {
  val (primaryIncidents, remainingIncidents) = incidents.partition { it.id == primaryIssue.id }
  if (primaryIncidents.isEmpty()) {
    return
  }

  if (exampleIncludesHints(files, primaryIssue)) {
    return
  }

  val sb = StringBuilder()
  sb.append("{\n")
  sb.append("    \"id\": \"${primaryIssue.id}\",\n")
  sb.append("    \"summary\": \"${primaryIssue.getDescription().escapeJson()}\",\n")
  val rawExplanation = primaryIssue.getExplanation(TextFormat.RAW)
  sb.append("    \"explanation\": \"${rawExplanation.escapeJson()}\",\n")

  fun appendFileList(files: List<Pair<String, String>>) {
    for (i in files.indices) {
      val file = files[i]
      val contents = file.second
      val targetPath = file.first
      val markdownLanguage = pathToMarkdownLanguage(targetPath)
      sb.append("        {\n")
      sb.append("            \"path\": \"${targetPath.escapeJson()}\",\n")
      if (markdownLanguage != null) {
        sb.append("            \"type\": \"${markdownLanguage.escapeJson()}\",\n")
      }
      sb.append("            \"contents\": \"${contents.escapeJson()}\"\n")
      sb.append("        }")
      if (i < files.size - 1) {
        sb.append(',')
      }
      sb.append("\n")
    }
  }

  val (mainFiles, supportFiles) =
    files.partition { file ->
      val path = file.first
      incidents.any { it.path.endsWith(path) || path.endsWith(it.path) }
    }
  if (mainFiles.isEmpty()) {
    return
  }

  sb.append("    \"main-files\": [\n")
  appendFileList(mainFiles)
  sb.append("    ],\n")
  if (supportFiles.isNotEmpty()) {
    sb.append("    \"support-files\": [\n")
    appendFileList(supportFiles)
    sb.append("    ],\n")
  }

  fun describeIncidents(incidents: List<ReportedIncident>): List<String> {
    return incidents.map { incident ->
      val json = StringBuilder()
      json.append("      {\n")
      json.append("        \"file\": \"${incident.path.escapeJson()}\",\n")
      json.append("        \"lineNumber\": \"${incident.lineNumber}\",\n")
      var line = incident.sourceLine1 ?: ""
      if (line.contains("//")) {
        line = stripComments(line, DOT_JAVA)
      } else if (line.contains("<!--")) {
        line = stripComments(line, DOT_XML)
      }
      json.append("        \"lineContents\": \"${line.trim().escapeJson()}\",\n")
      val message =
        incident.message.escapeJson().let {
          if (it.lastOrNull()?.isLetterOrDigit() == true) "$it." else it
        }
      json.append("        \"message\": \"$message\"\n")
      json.append("      }")
      json.toString()
    }
  }

  sb.append("    \"target-issues\": [\n")
  sb.append(describeIncidents(primaryIncidents).joinToString(",\n"))
  sb.append("\n")
  sb.append("    ],\n")

  if (remainingIncidents.isNotEmpty()) {
    sb.append("    \"other-issues\": [\n")
    sb.append(describeIncidents(remainingIncidents).joinToString(",\n"))
    sb.append("\n")
    sb.append("    ],\n")
  }

  if (issueData.copyrightYear != -1) {
    sb.append("    \"year\": \"${issueData.copyrightYear}\",\n")
  }
  sb.append("    \"severity\": \"${primaryIssue.defaultSeverity.toName()}\",\n")
  sb.append("    \"category\": \"${primaryIssue.category.fullName.escapeJson()}\",\n")
  sb.append(
    "    \"documentation\": \"${"https://googlesamples.github.io/android-custom-lint-rules/checks/${primaryIssue.id}.md.html".escapeJson()}\",\n"
  )
  sb.append("    \"priority\": \"${primaryIssue.priority}\",\n")
  sb.append("    \"enabled-by-default\": \"${primaryIssue.isEnabledByDefault()}\",\n")
  val library = issueData.library()
  if (library != null) {
    sb.append("    \"library\": \"${library.id.escapeJson()}\",\n")
  } else if (issueData.isBuiltIn()) {
    sb.append("    \"library\": \"built-in\",\n")
  }

  val languages =
    primaryIncidents
      .mapNotNull { pathToMarkdownLanguage(it.path) }
      .toSet()
      .sorted()
      .joinToString(", ")
  if (languages.isNotBlank()) {
    sb.append("    \"languages\": \"${languages.escapeJson()}\",\n")
  }
  if (primaryIssue.moreInfo.isNotEmpty()) {
    sb.append("    \"more-info-urls\": [\n")
    sb.append(primaryIssue.moreInfo.joinToString(",\n") { "        \"${it.escapeJson()}\"" })
    sb.append("\n    ],\n")
  }
  sb.append("    \"android-specific\": \"${primaryIssue.isAndroidSpecific()}\",\n")
  sb.append(
    "    \"source\": \"${className.substringAfterLast('.').escapeJson()}.${methodName.escapeJson()}\"\n"
  )

  sb.append("}")

  val eval = sb.toString()
  if (!jsonFile.isFile) {
    jsonFile.parentFile?.mkdirs()
    jsonFile.writeText(eval)
  } else {
    jsonFile.appendText(",\n$eval")
  }
}

private fun exampleIncludesHints(files: List<Pair<String, String>>, primaryIssue: Issue): Boolean {
  val regex = Regex("(broken|error[^a-zA-Z]|\\bok\\b|_ok|ok_|wrong|missing)")
  for ((path, source) in files) {
    val matchResult = regex.find(source)
    if (matchResult != null) {
      val group = matchResult.groups[0]!!
      val windowSize = 10
      val sourceWindowStart = max(0, group.range.first - windowSize)
      val sourceWindowEnd = min(source.length, group.range.last + windowSize)
      val sourceWindow =
        "..." + source.substring(sourceWindowStart, sourceWindowEnd).replace("\n", "\\n") + "..."
      println(
        "WARNING: Test file $path for ${primaryIssue.id} may be leaking problem through names: `${matchResult.value}` in `$sourceWindow`"
      )
      return true
    }
  }
  return false
}

private fun String.getLineNumber(offset: Int): Int {
  var lineNumber = 1
  for (i in 0 until offset) {
    if (this[i] == '\n') lineNumber++
  }
  return lineNumber
}

private fun String.escapeJson(): String {
  val sb = StringBuilder(this.length + 5)
  for (c in this) {
    when (c) {
      '\\' -> sb.append("\\\\")
      '\"' -> sb.append("\\\"")
      '\n' -> sb.append("\\n")
      '\t' -> sb.append("    ")
      '\r',
      '\b' -> error("shouldn't use this in output")
      else -> sb.append(c)
    }
  }
  return sb.toString()
}
