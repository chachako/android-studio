/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.lint.client.api

import com.android.SdkConstants.ATTR_FILE
import com.android.SdkConstants.ATTR_FORMAT
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LINE
import com.android.SdkConstants.ATTR_MESSAGE
import com.android.SdkConstants.TAG_ISSUE
import com.android.SdkConstants.TAG_ISSUES
import com.android.SdkConstants.TAG_LOCATION
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Position
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.describeCounts
import com.android.utils.CharSequenceReader
import com.android.utils.XmlUtils.toXmlAttributeValue
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import java.io.File
import java.io.IOException
import java.io.Writer
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * A lint baseline is a collection of warnings for a project that have been obtained from a previous
 * run of lint. These warnings are then exempt from reporting. This lets you set a "baseline" with a
 * known set of issues that you haven't attempted to fix yet, but then be alerted whenever new
 * issues crop up.
 */
class LintBaseline(
  /** Client to use for file reading, path variables, logging, etc */
  private val client: LintClient,

  /**
   * The file to read the baselines from, and if [writeOnClose] is set, to write to when the
   * baseline is [close]'ed.
   */
  var file: File
) {

  /** The number of errors that have been matched from the baseline. */
  var foundErrorCount: Int = 0
    private set

  /** The number of warnings that have been matched from the baseline. */
  var foundWarningCount: Int = 0
    private set

  /** The total number of issues contained in this baseline. */
  var totalCount: Int = 0
    private set

  /** Map from message to [Entry] */
  private val messageToEntry = ArrayListMultimap.create<String, Entry>(100, 20)

  private val idToMessages = HashMap<String, MutableSet<String>>(30)

  /**
   * Whether we should write the baseline file when the baseline is closed, if the baseline file
   * doesn't already exist. We don't always do this because for example when lint is run from
   * Gradle, and it's analyzing multiple variants, it does its own merging (across variants) of the
   * results first and then writes that, via the XML reporter.
   */
  var writeOnClose: Boolean = false
    set(writeOnClose) {
      if (writeOnClose) {
        val count = if (totalCount > 0) totalCount + 10 else 30
        entriesToWrite = ArrayList(count)
      }
      field = writeOnClose
    }

  /**
   * Whether the baseline, when configured to write results into the file, will include all found
   * issues, or only issues that are already known. The difference here is whether we're initially
   * creating the baseline (or resetting it), or whether we're trying to only remove fixed issues.
   */
  var removeFixed: Boolean = false

  /** If true, line numbers are omitted when writing out the baseline file. */
  var omitLineNumbers: Boolean = false

  /**
   * If non-null, a list of issues to write back out to the baseline file when the baseline is
   * closed.
   */
  var entriesToWrite: MutableList<ReportedEntry>? = null

  /**
   * Returns the number of issues that appear to have been fixed (e.g. are present in the baseline
   * but have not been matched.)
   */
  val fixedCount: Int
    get() = totalCount - foundErrorCount - foundWarningCount

  /** Custom attributes defined for this baseline. */
  private var attributes: MutableMap<String, String>? = null

  init {
    readBaselineFile()
  }

  /**
   * Checks if we should report baseline activity (filtered out issues, found fixed issues etc and
   * if so reports them.)
   */
  internal fun reportBaselineIssues(driver: LintDriver, project: Project) {
    if (foundErrorCount > 0 || foundWarningCount > 0) {
      val client = driver.client
      val baselineFile = file
      val message =
        describeBaselineFilter(
          foundErrorCount,
          foundWarningCount,
          getDisplayPath(client, project, baselineFile)
        )
      LintClient.report(
        client,
        IssueRegistry.BASELINE_USED,
        message,
        file = baselineFile,
        project = project,
        driver = driver
      )
    }

    val fixedCount = fixedCount
    if (fixedCount > 0 && !(writeOnClose && removeFixed)) {
      val client = driver.client
      val baselineFile = file
      val ids = Maps.newHashMap<String, Int>()
      for (entry in messageToEntry.values()) {
        val id = entry.issueId
        if (IssueRegistry.isDeletedIssueId(id)) {
          continue
        }
        var count: Int? = ids[id]
        if (count == null) {
          count = 1
        } else {
          count += 1
        }
        ids[id] = count
      }
      if (ids.isEmpty()) {
        return
      }
      val sorted = Lists.newArrayList(ids.keys)
      sorted.sort()
      val issueTypes = StringBuilder()
      for (id in sorted) {
        if (issueTypes.isNotEmpty()) {
          issueTypes.append(", ")
        }
        issueTypes.append(id)
        val count = ids[id]
        if (count != null && count > 1) {
          issueTypes.append(" (").append(count.toString()).append(")")
        }
      }

      // Keep in sync with isFixedMessage() below
      var message =
        String.format(
          "%1\$d errors/warnings were listed in the " +
            "baseline file (%2\$s) but not found in the project; perhaps they have " +
            "been fixed?",
          fixedCount,
          TextFormat.TEXT.convertTo(getDisplayPath(client, project, baselineFile), TextFormat.RAW)
        )
      if (
        LintClient.isGradle &&
          project.buildModule != null &&
          project.buildModule?.lintOptions?.checkDependencies == false
      ) {
        message +=
          " Another possible explanation is that lint recently stopped " +
            "analyzing (and including results from) dependent projects by default. " +
            "You can turn this back on with " +
            "`android.lintOptions.checkDependencies=true`."
      }
      message += " Unmatched issue types: $issueTypes"

      LintClient.report(
        client,
        IssueRegistry.BASELINE_FIXED,
        message,
        file = baselineFile,
        project = project,
        driver = driver
      )
    }
  }

  /**
   * Checks whether the given [incident] is present in this baseline, and if so marks it as used
   * such that a second call will not find it.
   *
   * When issue analysis is done you can call [foundErrorCount] and [foundWarningCount] to get a
   * count of the warnings or errors that were matched during the run, and [fixedCount] to get a
   * count of the issues that were present in the baseline that were not matched (e.g. have been
   * fixed.)
   *
   * Returns true if this error was found in the baseline and marked as used, and false if this
   * issue is not already part of the baseline.
   */
  fun findAndMark(incident: Incident): Boolean {
    val issue = incident.issue
    val location = incident.location
    val message = incident.message
    val severity = incident.severity
    val found = findAndMark(issue, location, message, severity, null)

    if (writeOnClose && (!removeFixed || found)) {
      if (entriesToWrite != null && shouldBaseline(issue.id)) {
        val project = incident.project
        entriesToWrite!!.add(ReportedEntry(incident))
      }
    }

    return found
  }

  private fun findAndMark(
    issue: Issue,
    location: Location,
    message: String,
    severity: Severity?,
    alreadyChecked: MutableSet<String>?
  ): Boolean {
    if (message.isEmpty()) {
      return false
    }

    val entries = messageToEntry[message]
    if (entries == null || entries.isEmpty()) {
      // Sometimes messages are changed in lint; try to gracefully handle this via #sameMessage
      val messages = idToMessages[issue.id]
      if (
        !messages.isNullOrEmpty() &&
          (messages.size > 1 || messages.size == 1 && messages.first() != message)
      ) {
        val checked = alreadyChecked ?: mutableSetOf<String>().apply { add(message) }
        for (oldMessage in messages) {
          if (checked.add(oldMessage) && sameMessage(issue, message, oldMessage)) {
            if (findAndMark(issue, location, oldMessage, severity, checked)) {
              return true
            }
          }
        }
      }

      return false
    }

    val file = location.file
    val path = file.path
    val issueId = issue.id
    for (entry in entries) {
      entry ?: continue
      if (
        entry.issueId == issueId ||
          IssueRegistry.isDeletedIssueId(entry.issueId) &&
            IssueRegistry.getNewId(entry.issueId) == issueId
      ) {
        if (isSamePathSuffix(path, entry.path) || isSameGradleCachePath(client, path, entry.path)) {
          // Remove all linked entries. We don't loop through all the locations;
          // they're allowed to vary over time, we just assume that all entries
          // for the same warning should be cleared.
          var curr = entry
          while (curr.previous != null) {
            curr = curr.previous
          }
          while (curr != null) {
            val currMessage = curr.message
            messageToEntry.remove(currMessage, curr)
            val remaining = messageToEntry[currMessage]
            if (remaining == null || remaining.isEmpty()) {
              idToMessages[issue.id]?.remove(currMessage)
            }
            curr = curr.next
          }

          if ((severity ?: issue.defaultSeverity).isError) {
            foundErrorCount++
          } else {
            foundWarningCount++
          }

          return true
        }
      }
    }

    return false
  }

  /**
   * Sometimes the exact message format for a given error shifts over time, for example when we
   * decide to make it clearer. Since baselines are primarily matched by the error message, any
   * format change would mean the recorded issue in the baseline no longer matches the error, and
   * the same error is now shown as a new error. To prevent this, the baseline mechanism will call
   * this method to check if two messages represent the same error, and if so, we'll continue to
   * match them. This jump table should record the various changes in error messages over time.
   */
  fun sameMessage(issue: Issue, new: String, old: String): Boolean {
    return when (issue.id) {
      "InvalidPackage" -> sameSuffixFrom("not included in", new, old)
      // See https://issuetracker.google.com/68802305
      "IconDensities" -> true
      // Error message changed but details aren't important; b/169615369
      "UselessLeaf" -> true
      // Error message changed but details aren't important; b/218579133
      "NonResizeableActivity" -> true
      // See 181170484
      "ScopedStorage" -> sameSuffixFrom("MANAGE_EXTERNAL_STORAGE", new, old)
      // See 168897210
      "SmallSp" -> sameSuffixFrom("sp:", new, old)
      "BatteryLife" -> {
        // Changed URL within error string
        val s = "Use of REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
        old.startsWith(s) && new.startsWith(s)
      }
      "ContentDescription" -> {
        // Removed [Accessibility] prefix at some point
        stringsEquivalent(old.removePrefix("[Accessibility] "), new)
      }
      "HardcodedText" -> {
        // Removed [I18N] prefix at some point
        stringsEquivalent(old.removePrefix("[I18N] "), new)
      }
      "NewApi",
      "InlinedApi",
      "UnusedAttribute" -> {
        // For the API messages we only want to match them on the signature --
        // the exact API requirement can change -- not just for API finalization
        // (e.g. from "Android U" to "34") but also when added to SDK extensions.
        // Most API errors will put the key symbol that is being referred to in
        // backticks, so we'll just match those spans.
        if (
          new.contains('`') &&
            symbolsMatch(old, new) &&
            // Also make sure we match call/field/class etc. prefix, e.g.
            //     Call requires 3: foo
            //     Field requires 3: foo
            // are referring to different things
            old.regionMatches(0, new, 0, 4)
        ) {
          true
        } else {
          val suffix = " (called from "
          stringsEquivalent(old.substringBeforeLast(suffix), new.substringBeforeLast(suffix)) { s, i
            ->
            s.tokenPrecededBy("min is ", i) ||
              s.tokenPrecededBy("API level ", i) ||
              s.tokenPrecededBy("version ", i)
          }
        }
      }
      "WebpUnsupported",
      "OverrideAbstract",
      "GetLocales" ->
        stringsEquivalent(old, new) { s, i -> s.tokenPrecededBy("minSdkVersion is ", i) }
      "FontValidation" ->
        stringsEquivalent(old, new) { s, i -> s.tokenPrecededBy("minSdkVersion", i, '=') }
      "RestrictedApi" -> {
        val index1 = old.indexOf('(')
        val index2 = new.indexOf('(')
        if (index1 != -1) {
          index1 == index2 && old.regionMatches(0, new, 0, index1)
        } else {
          stringsEquivalent(old, new)
        }
      }
      // Changed error messages to no longer include absolute paths: b/220161119
      "IconMissingDensityFolder",
      "IconXmlAndPng" -> sameWithAbsolutePath(new, old)
      "MissingQuantity" -> {
        sameSuffixFrom("should also be defined", new, old)
      }
      "RtlCompat" ->
        stringsEquivalent(old, new) { s, i -> s.tokenPrecededBy("project specifies ", i) }

      // Sometimes we just append (or remove trailing period in error messages, now
      // flagged by lint)
      else -> {
        stringsEquivalent(old, new) ||
          issue.implementation.detectorClass
            .getDeclaredConstructor()
            .newInstance()
            .sameMessage(issue, new, old)
      }
    }
  }

  /** Returns a custom attribute previously persistently set with [setAttribute] */
  fun getAttribute(name: String): String? {
    return attributes?.get(name)
  }

  /**
   * Set a custom attribute on this baseline (which is persisted and can be retrieved later with
   * [getAttribute])
   */
  fun setAttribute(name: String, value: String) {
    val attributes =
      attributes
        ?: run {
          val newMap = mutableMapOf<String, String>()
          attributes = newMap
          newMap
        }
    attributes[name] = value
  }

  /** Read in the XML report. */
  private fun readBaselineFile() {
    val xml = client.readFile(file)
    if (xml.isEmpty()) {
      return
    }

    try {
      val parser = KXmlParser()
      parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
      parser.setInput(CharSequenceReader(xml))

      var issue: String? = null
      var message: String? = null
      var path: String? = null
      var currentEntry: Entry? = null

      val pathVariables = client.pathVariables

      while (parser.next() != XmlPullParser.END_DOCUMENT) {
        val eventType = parser.eventType
        if (eventType == XmlPullParser.END_TAG) {
          val tag = parser.name
          if (tag == TAG_LOCATION) {
            if (issue != null && message != null && path != null) {
              path = pathVariables.fromPathString(path).path ?: path
              val entry = Entry(issue, message, path)
              if (currentEntry != null) {
                currentEntry.next = entry
              }
              entry.previous = currentEntry
              currentEntry = entry
              messageToEntry.put(entry.message, entry)
              val messages: MutableSet<String> =
                idToMessages[issue] ?: HashSet<String>().also { idToMessages[issue!!] = it }
              messages.add(message)
            }
          } else if (tag == TAG_ISSUE) {
            if (issue != null && !IssueRegistry.isDeletedIssueId(issue)) {
              totalCount++
            }
            issue = null
            message = null
            path = null
            currentEntry = null
          }
        } else if (eventType != XmlPullParser.START_TAG) {
          continue
        }

        var i = 0
        val n = parser.attributeCount
        while (i < n) {
          val name = parser.getAttributeName(i)
          val value = parser.getAttributeValue(i)
          when (name) {
            ATTR_ID -> issue = value
            ATTR_MESSAGE ->
              if (parser.depth == 2) message = value // else: depth=3: location-specific message
            ATTR_FILE -> path = value
            // For now not reading ATTR_LINE; not used for baseline entry matching
            // ATTR_LINE -> line = value
            ATTR_FORMAT,
            "by" -> {} // not currently interesting, don't store
            else -> {
              if (parser.depth == 1) {
                setAttribute(name, value)
              }
            }
          }
          i++
        }
      }
    } catch (e: XmlPullParserException) {
      client.log(e, null)
    }
  }

  /** Finishes writing the baseline. */
  fun close() {
    if (writeOnClose) {
      write(file)
    }
  }

  /** Writes out the baseline listing exactly the incidents that were reported */
  fun write(file: File) {
    val parentFile = file.parentFile
    if (parentFile != null && !parentFile.exists()) {
      val mkdirs = parentFile.mkdirs()
      if (!mkdirs) {
        client.log(null, "Couldn't create %1\$s", parentFile)
        return
      }
    }

    try {
      file.bufferedWriter().use { writer ->
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        // Format 4: added urls= attribute with all more info links, comma separated
        writer.write("<")
        writer.write(TAG_ISSUES)
        writer.write(" format=\"5\"")
        val revision = client.getClientDisplayRevision()
        if (revision != null) {
          writer.write(String.format(" by=\"lint %1\$s\"", revision))
        }
        attributes?.let { map ->
          map
            .asSequence()
            .sortedBy { it.key }
            .forEach { writer.write(" ${it.key}=\"${toXmlAttributeValue(it.value)}\"") }
        }
        writer.write(">\n")

        totalCount = 0
        if (entriesToWrite != null) {
          entriesToWrite!!.sort()
          for (entry in entriesToWrite!!) {
            entry.write(writer, client)
            totalCount++
          }
        }

        writer.write("\n</")
        writer.write(TAG_ISSUES)
        writer.write(">\n")
        writer.close()
      }
    } catch (ioe: IOException) {
      client.log(ioe, null)
    }
  }

  /**
   * Lightweight wrapper for a [Location] to avoid holding on to locations for too long, since for
   * example the [Location.source] field (but also fields in subclasses of [Location] and
   * [Position]) can reference large data structures like PSI.
   */
  class LightLocation(location: Location) {
    val file: File = location.file
    val line: Int = location.start?.line ?: -1
    val column: Int = location.start?.column ?: -1
    val secondary: LightLocation? = location.secondary?.let { LightLocation(it) }
  }

  /**
   * Entries that have been reported during this lint run. We only create these when we need to
   * write a baseline file (since we need to sort them before writing out the result file, to ensure
   * stable files.)
   */
  class ReportedEntry(val incident: Incident) : Comparable<ReportedEntry> {
    val issue: Issue = incident.issue
    val project: Project? = incident.project
    val location: LightLocation = LightLocation(incident.location)
    val message: String = incident.message

    override fun compareTo(other: ReportedEntry): Int {
      // Sort by category, then by priority, then by id,
      // then by file, then by line
      val categoryDelta = issue.category.compareTo(other.issue.category)
      if (categoryDelta != 0) {
        return categoryDelta
      }
      // DECREASING priority order
      val priorityDelta = other.issue.priority - issue.priority
      if (priorityDelta != 0) {
        return priorityDelta
      }
      val id1 = issue.id
      val id2 = other.issue.id
      val idDelta = id1.compareTo(id2)
      if (idDelta != 0) {
        return idDelta
      }
      val file = location.file
      val otherFile = other.location.file
      val fileDelta = file.name.compareTo(otherFile.name)
      if (fileDelta != 0) {
        return fileDelta
      }

      val line = location.line
      val otherLine = other.location.line

      if (line != otherLine) {
        return line - otherLine
      }

      var delta = message.compareTo(other.message)
      if (delta != 0) {
        return delta
      }

      delta = file.compareTo(otherFile)
      if (delta != 0) {
        return delta
      }

      val secondary1 = location.secondary
      val secondaryFile1 = secondary1?.file
      val secondary2 = other.location.secondary
      val secondaryFile2 = secondary2?.file
      if (secondaryFile1 != null) {
        return if (secondaryFile2 != null) {
          secondaryFile1.compareTo(secondaryFile2)
        } else {
          -1
        }
      } else if (secondaryFile2 != null) {
        return 1
      }

      // This handles the case where you have a huge XML document without newlines,
      // such that all the errors end up on the same line.
      if (line != -1 && otherLine != -1) {
        delta = location.column - other.location.column
        if (delta != 0) {
          return delta
        }
      }

      return 0
    }
  }

  /** Given the report of an issue, add it to the baseline being built in the XML writer. */
  private fun ReportedEntry.write(writer: Writer, client: LintClient) {
    try {
      writer.write("\n")
      indent(writer, 1)
      writer.write("<")
      writer.write(TAG_ISSUE)
      writeAttribute(writer, 2, ATTR_ID, issue.id)

      writeAttribute(writer, 2, ATTR_MESSAGE, message)

      writer.write(">\n")
      var currentLocation: LightLocation? = location
      while (currentLocation != null) {
        //
        //
        //
        // IMPORTANT: Keep this format compatible with the XML report format
        //            encoded by the XmlReporter! That way XML reports and baseline
        //            files can be mix & matched. (Compatible=subset.)
        //
        //
        indent(writer, 2)
        writer.write("<")
        writer.write(TAG_LOCATION)
        val path =
          PrettyPaths.getPath(
            currentLocation.file,
            project,
            client,
            useUnixPaths = true,
            tryPathVariables = true,
            pathVariables = client.pathVariables,
            preferRelativePathOverPathVariables = false,
            allowParentRelativePaths = false,
            preferRelativeOverAbsolute = true
          )
        writeAttribute(writer, 3, ATTR_FILE, path)
        val line = currentLocation.line
        if (line >= 0 && !omitLineNumbers) {
          // +1: Line numbers internally are 0-based, report should be
          // 1-based.
          writeAttribute(writer, 3, ATTR_LINE, (line + 1).toString())
        }

        writer.write("/>\n")
        currentLocation = currentLocation.secondary
      }
      indent(writer, 1)
      writer.write("</")
      writer.write(TAG_ISSUE)
      writer.write(">\n")
    } catch (ioe: IOException) {
      client.log(ioe, null)
    }
  }

  /**
   * Entry loaded from the baseline file. Note that for an error with multiple locations, there may
   * be multiple entries; these are linked by next/previous fields.
   */
  private class Entry(val issueId: String, val message: String, val path: String) {
    /**
     * An issue can have multiple locations; we create a separate entry for each but we link them
     * together such that we can mark them all fixed.
     */
    var next: Entry? = null
    var previous: Entry? = null
  }

  companion object {
    const val VARIANT_ALL = "all"
    const val VARIANT_FATAL = "fatal"

    /**
     * Given an issue, determines whether it should be included in a baseline. Lint errors should
     * not be baselined - see b/297095583.
     */
    fun shouldBaseline(id: String): Boolean {
      return id != IssueRegistry.LINT_ERROR.id &&
        id != IssueRegistry.LINT_WARNING.id &&
        id != IssueRegistry.BASELINE_USED.id &&
        id != IssueRegistry.BASELINE_FIXED.id
    }

    fun describeBaselineFilter(errors: Int, warnings: Int, baselineDisplayPath: String): String {
      val counts = describeCounts(errors, warnings, comma = false, capitalize = true)
      val escapedPath = TextFormat.TEXT.convertTo(baselineDisplayPath, TextFormat.RAW)
      // Keep in sync with isFilteredMessage() below
      return if (errors + warnings == 1) {
        "$counts was filtered out because it is listed in the baseline file, $escapedPath\n"
      } else {
        "$counts were filtered out because they are listed in the baseline file, $escapedPath\n"
      }
    }

    /**
     * If a path like
     * "$GRADLE_USER_HOME/caches/transforms-3/4366a02f2b10003dc48387e903833c2d/transformed/leakcanary-android-core-2.8.1/jars/classes.jar"
     * makes it into the baseline, we should compare the part of the path string inside the cache
     * instead of the whole path which can change on each test run/machine. See b/238892319
     */
    fun isSameGradleCachePath(client: LintClient, path1: String, path2: String): Boolean {
      val gradleCachePath = "${"$"}GRADLE_USER_HOME/caches/"
      val cacheRelativePaths =
        listOf(path1, path2).map { path ->
          // First we see if the path lies inside $GRADLE_USER_HOME/caches/
          val pathWithPathVariables =
            client.pathVariables.toPathStringIfMatched(path) ?: return false
          if (!pathWithPathVariables.startsWith(gradleCachePath)) return false
          pathWithPathVariables.removePrefix(gradleCachePath)
        }

      // Gradle cache paths have an ID somewhere in them that changes across test runs/machines
      // Like caches/transforms-3/ID/transformed/leakcanary-android-core-2.8.1/jars/classes.jar
      // or caches/artifacts-4/group/name/ID/jars/name-version.jar
      // We don't want to be too tightly coupled to exactly where this ID shows up in the path,
      // so we take the two paths and see if they differ by either zero or one directory names.
      val chunks = cacheRelativePaths.map { it.split("/") }
      if (chunks[0].size != chunks[1].size) return false

      var diffFound = false
      for (i in chunks[0].indices) {
        if (chunks[0][i] != chunks[1][i]) {
          if (diffFound) return false
          diffFound = true
        }
      }

      return true
    }

    /** Like path.endsWith(suffix), but considers \\ and / identical. */
    fun isSamePathSuffix(path: String, suffix: String): Boolean {
      var i = path.length - 1
      var j = suffix.length - 1

      var begin = 0
      while (begin < j) {
        val c = suffix[begin]
        if (c != '.' && c != '/' && c != '\\') {
          break
        }
        begin++
      }

      if (j - begin > i) {
        return false
      }

      while (j > begin) {
        var c1 = path[i]
        var c2 = suffix[j]
        if (c1 != c2) {
          if (c1 == '\\') {
            c1 = '/'
          }
          if (c2 == '\\') {
            c2 = '/'
          }
          if (c1 != c2) {
            return false
          }
        }
        i--
        j--
      }

      return true
    }

    private fun getDisplayPath(client: LintClient, project: Project?, file: File): String {
      var path = file.path
      if (project == null) {
        return path
      }
      if (path.startsWith(project.referenceDir.path)) {
        var chop = project.referenceDir.path.length
        if (path.length > chop && path[chop] == File.separatorChar) {
          chop++
        }
        path = path.substring(chop)
        if (path.isEmpty()) {
          path = file.name
        }
      } else if (file.isAbsolute && file.exists()) {
        path = client.getRelativePath(project.referenceDir, file) ?: file.path
      }

      return path
    }

    /** Checks whether two strings end in the same way, from the given start string. */
    private fun sameSuffixFrom(target: String, new: String, old: String): Boolean {
      val i1 = new.indexOf(target)
      val i2 = old.indexOf(target)
      return i1 != -1 &&
        i2 != -1 &&
        stringsEquivalent(new, old, i1 + target.length, i2 + target.length)
    }

    /**
     * Returns true if these two strings appear to be the same except the [full] string has a single
     * absolute path somewhere in the middle which is only a relative path in the [relative] string.
     * For example, `relative="The file res does not exist"` and `full="The file C:\path\to\res does
     * not exist"`.
     *
     * If [prefix] and or [suffix] are non-empty, they must also be matched in the strings.
     */
    fun sameWithAbsolutePath(
      relative: String,
      full: String,
      prefix: String = "",
      suffix: String = ""
    ): Boolean {
      if (
        !relative.startsWith(prefix) ||
          !full.startsWith(prefix) ||
          !relative.endsWith(suffix) ||
          !full.endsWith(suffix)
      ) {
        return false
      }
      if (relative.length > full.length) {
        return false
      }
      val first = prefixMatchLength(relative, full)
      val last = suffixMatchLength(relative, full)
      val relativeLength = relative.length - first - last
      return relative.regionMatches(
        first,
        full,
        full.length - last - relativeLength,
        relativeLength
      )
    }

    /** Return the index of the first character where the strings [a] and [b] differ */
    fun prefixMatchLength(a: String, b: String): Int {
      for (i in a.indices) {
        if (i == b.length) {
          return i
        }
        val ac = a[i]
        val bc = b[i]
        if (ac != bc) {
          return i
        }
      }
      return a.length
    }

    /**
     * Return the index **from the end of both strings** where the first characters in the strings
     * [a] and [b] differ.
     */
    fun suffixMatchLength(a: String, b: String): Int {
      var ai = a.length - 1
      var bi = b.length - 1
      var index = 0
      while (ai >= 0 && bi >= 0) {
        val ac = a[ai--]
        val bc = b[bi--]
        if (ac != bc) {
          break
        }
        index++
      }
      return index
    }

    /**
     * Compares two string messages from lint and returns true if they're equivalent, which will be
     * true if they only vary by suffix or presence of ` characters or spaces. This is done to
     * handle the case where we tweak the message format over time to either append extra
     * information or to add better formatting (e.g. to put backticks around symbols) or to remove
     * trailing periods from single sentence error messages. Lint is recently suggesting these edits
     * to lint checks -- and we want baselines to continue to match in the presence of these edits.
     */
    fun stringsEquivalent(s1: String, s2: String, start1: Int = 0, start2: Int = 0): Boolean {
      var i1 = start1
      var i2 = start2
      val n1 = s1.length
      val n2 = s2.length

      if (start1 == n1 || start2 == n2) {
        return true
      }
      while (true) {
        val c1 = s1[i1]
        val c2 = s2[i2]
        if (c1 != c2) {
          while (i1 < n1 && (s1[i1] == '`' || s1[i1] == ' ')) {
            i1++
          }
          while (i2 < n2 && (s2[i2] == '`' || s2[i2] == ' ')) {
            i2++
          }
          if (i1 == n1 || i2 == n2) {
            return true
          }
          if (s1[i1] != s2[i2]) {
            // The delta happened inside an HTTP URL. At this point, consider
            // the two strings equivalent (even if they have a different suffix
            // after the
            val http = s1.lastIndexOf("http", i1)
            if (http != -1) {
              val blank1 = s1.indexOf(' ', http)
              val blank2 = s2.indexOf(' ', http)
              if (blank1 == -1 || blank2 == -1) {
                return true
              } else if (i1 < blank1) {
                i1 = blank1
                i2 = blank2
                continue
              }
            }
            if ((s1[i1] == '/' || s1[i1] == '\\') && (s2[i2] == '/' || s2[i2] == '\\')) {
              // Allow differing path separators
            } else {
              return false
            }
          }
        }
        i1++
        i2++
        if (i1 == n1 || i2 == n2) {
          return true
        }
      }
    }

    /**
     * If a string contains symbols (such as a method name or a fully qualified name, possibly with
     * dots or # as separators) then make sure that both strings contain the same symbols in the
     * same order.
     */
    fun symbolsMatch(s1: String, s2: String): Boolean {
      var symbolStart = s1.indexOf('`')
      if (symbolStart == -1) {
        return false
      }
      var symbolStart2 = 0
      while (symbolStart != -1) {
        val symbolEnd = s1.indexOf('`', symbolStart + 1)
        if (symbolEnd == -1) {
          return false
        }
        symbolStart2 = s2.indexOf('`', symbolStart2)
        if (symbolStart2 == -1) {
          return false
        }
        val symbolEnd2 = s2.indexOf('`', symbolStart2 + 1)
        if (
          symbolEnd2 == -1 ||
            symbolEnd2 - symbolStart2 != symbolEnd - symbolStart ||
            !s1.regionMatches(symbolStart, s2, symbolStart2, symbolEnd - symbolStart)
        ) {
          return false
        }
        symbolStart = s1.indexOf('`', symbolEnd + 1)
        symbolStart2 = symbolEnd2 + 1
      }

      return true
    }

    fun String.tokenPrecededBy(prev: String, offset: Int, separator: Char = ' '): Boolean {
      if (offset < 0 || offset >= this.length) {
        throw IndexOutOfBoundsException("index: $offset, size: ${this.length}")
      }
      var i = offset
      // Move back to the start of the current token at offset.
      // For backwards compatibility also handle trailing whitespace in [prev].
      while (i > 0 && this[i] != separator && this[i - 1] != ' ') {
        i--
      }
      i -= prev.length
      return if (i < 0) {
        false
      } else {
        this.regionMatches(i, prev, 0, prev.length, false)
      }
    }

    /**
     * Compares two error messages and whenever there is a difference, it consults the [skipTokenAt]
     * function to ask whether the next token (letters and digits) can be skipped in each before
     * resuming the comparison
     */
    fun stringsEquivalent(s1: String, s2: String, skipTokenAt: (String, Int) -> Boolean): Boolean {
      var i1 = 0
      var i2 = 0
      val n1 = s1.length
      val n2 = s2.length

      while (true) {
        while (i1 < n1 && (s1[i1].isWhitespace() || s1[i1] == '`')) i1++
        while (i2 < n2 && (s2[i2].isWhitespace() || s2[i2] == '`')) i2++
        if (i1 >= n1) {
          return i2 >= n2
        } else if (i2 >= n2) {
          return false
        }
        if (s1[i1] != s2[i2]) {
          if (skipTokenAt(s1, i1)) {
            while (i1 < n1 && !s1[i1].isWhitespace()) i1++
            while (i2 < n2 && !s2[i2].isWhitespace()) i2++
          } else {
            return false
          }
        } else {
          i1++
          i2++
        }
      }
    }

    @Throws(IOException::class)
    private fun writeAttribute(writer: Writer, indent: Int, name: String, value: String) {
      writer.write("\n")
      indent(writer, indent)
      writer.write(name)
      writer.write("=\"")
      writer.write(toXmlAttributeValue(value))
      writer.write("\"")
    }

    @Throws(IOException::class)
    private fun indent(writer: Writer, indent: Int) {
      for (level in 0 until indent) {
        writer.write("    ")
      }
    }
  }
}
