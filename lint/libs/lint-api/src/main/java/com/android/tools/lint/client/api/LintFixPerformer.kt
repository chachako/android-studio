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
package com.android.tools.lint.client.api

import com.android.SdkConstants.AAPT_PREFIX
import com.android.SdkConstants.AAPT_URI
import com.android.SdkConstants.ANDROID_NS_NAME
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.APP_PREFIX
import com.android.SdkConstants.ATTR_COLOR
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_RESOURCE_PREFIX
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PACKAGE
import com.android.SdkConstants.ATTR_STYLE
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.DIST_PREFIX
import com.android.SdkConstants.DIST_URI
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.DOT_KTS
import com.android.SdkConstants.TOOLS_PREFIX
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.URI_PREFIX
import com.android.SdkConstants.XLIFF_PREFIX
import com.android.SdkConstants.XLIFF_URI
import com.android.SdkConstants.XMLNS
import com.android.SdkConstants.XMLNS_PREFIX
import com.android.tools.lint.client.api.LintClient.Companion.isUnitTest
import com.android.tools.lint.detector.api.DefaultPosition
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintFix.AnnotateFix
import com.android.tools.lint.detector.api.LintFix.CreateFileFix
import com.android.tools.lint.detector.api.LintFix.GroupType
import com.android.tools.lint.detector.api.LintFix.LintFixGroup
import com.android.tools.lint.detector.api.LintFix.ReplaceString
import com.android.tools.lint.detector.api.LintFix.ReplaceString.Companion.INSERT_BEGINNING
import com.android.tools.lint.detector.api.LintFix.ReplaceString.Companion.INSERT_END
import com.android.tools.lint.detector.api.LintFix.SetAttribute
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Severity
import com.android.utils.PositionXmlParser
import com.android.utils.XmlUtils
import com.intellij.openapi.util.TextRange
import java.io.File
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException
import kotlin.math.max
import kotlin.math.min
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException

/** Support for applying quickfixes directly. */
abstract class LintFixPerformer(
  private val client: LintClient,
  /** Should applied fixes be limited to those marked as safe to be applied automatically? */
  private val requireAutoFixable: Boolean = false,
) {
  abstract fun getSourceText(file: File): CharSequence

  abstract fun log(severity: Severity, message: String)

  private fun getFileData(fileMap: MutableMap<File, PendingEditFile>, file: File): PendingEditFile {
    return fileMap[file]
      ?: run {
        val fileData = PendingEditFile(file)
        fileMap[file] = fileData
        fileData
      }
  }

  fun createFileProvider() = FileProvider()

  inner class FileProvider {
    private val files = mutableMapOf<PendingEditFile, String>()
    private val documents = mutableMapOf<PendingEditFile, Document>()

    fun getFileContents(file: PendingEditFile): String {
      return files[file] ?: getSourceText(file.file).toString().also { files[file] = it }
    }

    fun getXmlDocument(file: PendingEditFile): Document? {
      return documents[file]
        ?: createXmlDocument(file).also {
          if (it != null) {
            documents[file] = it
          }
        }
    }

    /** If this file represents an XML file, returns the XML DOM of the initial content. */
    private fun createXmlDocument(file: PendingEditFile): Document? {
      try {
        val contents = getFileContents(file)
        client.getXmlDocument(file.file, contents)?.let {
          return it
        }
        return PositionXmlParser.parse(contents)
      } catch (e: ParserConfigurationException) {
        handleXmlError(e, file)
      } catch (e: SAXException) {
        handleXmlError(e, file)
      } catch (e: IOException) {
        handleXmlError(e, file)
      }
      return null
    }

    // because Kotlin does not have multi-catch:
    private fun handleXmlError(e: Throwable, file: PendingEditFile) {
      log(Severity.WARNING, "Ignoring $file: Failed to parse XML: $e")
    }
  }

  private fun registerFix(
    fileProvider: FileProvider,
    fileMap: MutableMap<File, PendingEditFile>,
    incident: Incident,
    lintFix: LintFix,
  ) {
    if (addEdits(fileProvider, fileMap, incident, lintFix)) {
      incident.wasAutoFixed = true
    }
  }

  fun fix(incidents: List<Incident>): Boolean {
    val fileProvider = FileProvider()
    val files = findApplicableFixes(fileProvider, incidents)
    return applyEdits(fileProvider, files)
  }

  fun registerFixes(
    incident: Incident,
    fixes: List<LintFix>,
    fileProvider: FileProvider = createFileProvider(),
  ): List<PendingEditFile> {
    val fileMap = mutableMapOf<File, PendingEditFile>()
    for (fix in fixes) {
      if (canAutoFix(fix, requireAutoFixable)) {
        registerFix(fileProvider, fileMap, incident, fix)
      }
    }
    val files = fileMap.values.filter { !it.isEmpty() }.toList()
    for (file in files) {
      cleanup(fileProvider, file)
    }
    return files
  }

  /**
   * Compute cleanup edits (and append those to the edit lists) such that when all the edits are
   * applied, we don't end up with blank lines (that were not formerly blank) and we don't have
   * trailing spaces.
   */
  private fun cleanup(fileProvider: FileProvider, file: PendingEditFile) {
    val edits =
      file.edits.let {
        if (it.size > 1) {
          // Sort & remove duplicates. Duplicates can happen because sometimes there
          // are multiple references to a single problem, and each fix will repeat
          // the same fix.
          //
          // Note that we're sorting in *descending* order. The natural sorting
          // order for edits is reverse, so this basically sorts the edits in
          // *ascending* offset order.
          it.asSequence().sortedDescending().distinct().toMutableList()
        } else {
          it
        }
      }
    val source = fileProvider.getFileContents(file)
    val length = source.length
    for (i in edits.indices) {
      val edit = edits[i]
      // Is this a delete operation? If so there's potential to leave the whole
      // line empty (in which case we want to remove the whole line), or, we may
      // leave trailing space, which we want to delete.
      if (edit.isDelete()) {
        var prevLineStart = -1
        var nextLineEnd = -1
        val next = if (i < edits.size - 1) edits[i + 1] else null
        val max = min(length, next?.startOffset ?: length)
        var j = edit.endOffset
        // Iterate from the end of the current edit up to the beginning of the next
        // edit (or the end of the document). If we come across a non-whitespace
        // character then we know there's no trailing space here, so we move to
        // the next edit. But if we come across a newline character we know that
        // the next edit is not on the same line -- and therefore we can trim
        // whitespace from the end of the line (and maybe the whole line, determined
        // below.)
        while (j < max) {
          val c = source[j]
          if (c == ' ') {
            j++
            continue
          } else if (c == '\n' || c == '\r') {
            nextLineEnd = j
            break
          } else {
            break
          }
        }
        // Figure out if there's only whitespace left on the left hand
        // side of the edit as well -- if so, the entire line can be deleted.
        val prev = if (i > 0) edits[i - 1] else null
        val min = prev?.endOffset ?: 0
        var k = edit.startOffset
        while (k > min) {
          val c = source[k - 1]
          if (c == ' ') {
            k--
            continue
          } else if (c == '\n') {
            prevLineStart = k
            break
          } else {
            break
          }
        }
        // prev
        if (prevLineStart != -1 && nextLineEnd != -1) {
          // Only whitespace on both sides of this edit from line start to line end,
          // so delete the entire line.
          nextLineEnd++ // include \n
          file.edits.add(PendingEdit(edit.fix, edit.endOffset, nextLineEnd, ""))
          if (prevLineStart < edit.startOffset) {
            file.edits.add(PendingEdit(edit.fix, prevLineStart, edit.startOffset, ""))
          }
        } else if (nextLineEnd != -1 && (nextLineEnd > edit.endOffset || k < edit.startOffset)) {
          // Remove trailing whitespace
          if (nextLineEnd > edit.endOffset) {
            file.edits.add(PendingEdit(edit.fix, edit.endOffset, nextLineEnd, ""))
          }
          if (k < edit.startOffset) {
            file.edits.add(PendingEdit(edit.fix, k, edit.startOffset, ""))
          }
        }
      }
    }
  }

  fun fix(incident: Incident, fixes: List<LintFix>): Boolean {
    val fileProvider = FileProvider()
    val fileMap = registerFixes(incident, fixes, fileProvider)
    return applyEdits(fileProvider, fileMap)
  }

  protected abstract fun createBinaryFile(fileData: PendingEditFile, contents: ByteArray)

  protected abstract fun deleteFile(fileData: PendingEditFile)

  protected open fun applyEdits(
    fileData: PendingEditFile,
    edits: List<PendingEdit>,
    applier: (PendingEditFile, PendingEdit) -> Unit,
  ) {
    for (edit in edits) {
      applier(fileData, edit)
    }
  }

  abstract fun applyEdits(
    fileProvider: FileProvider,
    fileData: PendingEditFile,
    edits: List<PendingEdit>,
  )

  fun applyEdits(
    fileProvider: FileProvider,
    files: List<PendingEditFile>,
    performEdits: (PendingEditFile, List<PendingEdit>) -> Unit = { fileData, edits ->
      applyEdits(fileProvider, fileData, edits)
    },
  ): Boolean {
    var appliedEditCount = 0
    var editedFileCount = 0
    val editMap: MutableMap<String, Int> = mutableMapOf()

    for (fileData in files) {
      if (fileData.createBytes != null) {
        createBinaryFile(fileData, fileData.createBytes!!)
        editedFileCount++
        continue
      } else if (fileData.delete) {
        deleteFile(fileData)
        editedFileCount++
        continue
      }
      // Sort fixes in descending order from location, and
      // remove duplicates. Duplicates can happen because sometimes there
      // are multiple references to a single problem, and each fix will repeat
      // the same fix. For example, multiple references to the same private field
      // might all flag that field as generating a synthetic accessor and suggest making
      // it package private, and we only want to perform that modification once, not
      // repeatedly for each field *reference*.
      val edits = fileData.edits.asSequence().sorted().distinct().toList()

      // Look for overlapping edit regions. This can happen if two quickfixes
      // are conflicting.
      if (findConflicts(edits)) {
        continue
      }

      performEdits(fileData, edits)

      // Update stats too
      for (edit in edits) {
        appliedEditCount++
        val key = edit.fixName()
        val count: Int = editMap[key] ?: 0
        editMap[key] = count + 1
      }
      editedFileCount++
    }

    if (editedFileCount > 0) {
      printStatistics(editMap, appliedEditCount, editedFileCount)
      return true
    }

    return false
  }

  open fun printStatistics(
    editMap: MutableMap<String, Int>,
    appliedEditCount: Int,
    editedFileCount: Int,
  ) {}

  private fun findApplicableFixes(
    fileProvider: FileProvider,
    incidents: List<Incident>,
  ): List<PendingEditFile> {
    val fileMap = mutableMapOf<File, PendingEditFile>()
    for (incident in incidents) {
      val data = incident.fix ?: continue
      if (data is LintFixGroup) {
        if (data.type == GroupType.COMPOSITE) {
          // separated out again in applyFix
          var all = true
          for (sub in data.fixes) {
            if (!canAutoFix(sub, requireAutoFixable)) {
              all = false
              break
            }
          }
          if (all) {
            for (sub in data.fixes) {
              registerFix(fileProvider, fileMap, incident, sub)
            }
          }
        }
        // else: for GroupType.ALTERNATIVES, we don't auto fix: user must pick
        // which one to apply.
      } else if (canAutoFix(data, requireAutoFixable)) {
        registerFix(fileProvider, fileMap, incident, data)
      }
    }
    return fileMap.values.toList()
  }

  private fun findConflicts(fixes: List<PendingEdit>): Boolean {
    // Make sure there are no overlaps.
    // Since we have sorted by start offsets we can just scan over
    // the ranges and make sure that each new range ends after the
    // previous fix' end range
    if (fixes.isEmpty()) {
      return false
    }
    var prev = fixes[fixes.size - 1]
    // Since the fixes re sorted in reverse order, here we're processing them
    // in ascending offset order, and seeing if the fix starts before the end
    // of the previous offset which implies an overlap.
    for (index in fixes.size - 2 downTo 0) {
      val fix = fixes[index]
      if (fix.startOffset < prev.endOffset) {
        log(
          Severity.WARNING,
          "Overlapping edits in quickfixes; skipping. " +
            "Involved fixes: ${prev.fix.getDisplayName()} in [" +
            "${prev.startOffset}-${prev.endOffset}] and ${
                fix.fix.getDisplayName()
              } in [${fix.startOffset}-${fix.endOffset}]",
        )
        return true
      }
      prev = fix
    }
    return false
  }

  private fun isValid(
    file: PendingEditFile,
    edits: List<PendingEdit>,
    checker: (PendingEditFile, PendingEdit) -> Boolean,
  ): Boolean {
    return edits.all { checker(file, it) }
  }

  private fun isValid(file: PendingEditFile, edits: List<PendingEdit>, contents: String): Boolean {
    return isValid(file, edits) { _, edit ->
      contents.startsWith(edit.replacement, edit.startOffset)
    }
  }

  private fun addEdits(
    fileProvider: FileProvider,
    fileMap: MutableMap<File, PendingEditFile>,
    incident: Incident,
    lintFix: LintFix,
    isTopLevel: Boolean = true,
  ): Boolean {
    if (lintFix is LintFixGroup && lintFix.type == GroupType.COMPOSITE) {
      var all = true
      var fixes = lintFix.fixes
      val attributeFixes = fixes.count { it is SetAttribute }
      if (attributeFixes > 1) {
        fixes = sortAttributes(fixes)
      }

      for (nested in fixes) {
        if (!addEdits(fileProvider, fileMap, incident, nested, false)) {
          all = false
        }
      }
      return all
    }
    val location = getLocation(incident, lintFix)
    val file = getFileData(fileMap, location.file)
    return when (lintFix) {
      is ReplaceString -> addReplaceString(fileProvider, file, incident, lintFix, location)
      is SetAttribute -> addSetAttribute(fileProvider, file, lintFix, location)
      is AnnotateFix -> addAnnotation(fileProvider, file, incident, lintFix, location)
      is CreateFileFix -> {
        if (isTopLevel || lintFix.selectPattern != null) {
          file.open = true
        }
        addCreateFile(file, lintFix)
      }
      else -> false
    }
  }

  private fun sortAttributes(fixes: List<LintFix>): List<LintFix> {
    // When we're setting multiple attribute fixes, sort them relative to
    // each other such that they get inserted in the preferred order
    val reordered = fixes.filterIsInstance<SetAttribute>().toMutableList()
    reordered.sortBy {
      val prefix = suggestNamespacePrefix(it.namespace) ?: APP_PREFIX
      -rankAttributeNames(prefix, it.attribute)
    }
    return fixes.filter { it !is SetAttribute } + reordered
  }

  private fun addAnnotation(
    fileProvider: FileProvider,
    file: PendingEditFile,
    incident: Incident,
    annotateFix: AnnotateFix,
    fixLocation: Location?,
  ): Boolean {
    val replaceFix =
      createAnnotationFix(
        annotateFix,
        annotateFix.range ?: fixLocation,
        fileProvider.getFileContents(file),
      )
    return addReplaceString(fileProvider, file, incident, replaceFix, fixLocation)
  }

  private fun addCreateFile(file: PendingEditFile, fix: CreateFileFix): Boolean {
    if (fix.delete) {
      file.delete = true
    } else {
      val text = fix.text
      if (text != null) {
        file.createText = true
        file.reformat = fix.reformat
        val (selectStart, selectEnd) = getSelectionDeltas(fix.selectPattern, text, optional = false)
        file.edits.add(PendingEdit(fix, 0, 0, text, selectStart, selectEnd))
      } else {
        file.createBytes = fix.binary
      }
    }
    return true
  }

  private fun addSetAttribute(
    fileProvider: FileProvider,
    file: PendingEditFile,
    setFix: SetAttribute,
    fixLocation: Location?,
  ): Boolean {
    val location = setFix.range ?: fixLocation ?: return false
    val start = location.start ?: return false

    val contents = fileProvider.getFileContents(file)
    val document =
      client.getXmlDocument(file.file, contents)
        ?: fileProvider.getXmlDocument(file)
        ?: return false

    var node: Node? =
      client.xmlParser.findNodeAt(document, start.offset)
        ?: error("No node found at offset " + start.offset)
    if (node != null && node.nodeType == Node.ATTRIBUTE_NODE) {
      node = (node as Attr).ownerElement
    } else if (node != null && node.nodeType != Node.ELEMENT_NODE) {
      // text, comments
      node = node.parentNode
    }
    if (node == null || node.nodeType != Node.ELEMENT_NODE) {
      throw IllegalArgumentException(
        "Didn't find element at offset " +
          start.offset +
          " (line " +
          start.line +
          1 +
          ", column " +
          start.column +
          1 +
          ") in " +
          file.file.path +
          ":\n" +
          contents
      )
    }

    val element = node as Element
    val value = setFix.value
    val namespace = setFix.namespace
    var attributeName = setFix.attribute

    val attr =
      if (namespace != null) {
        element.getAttributeNodeNS(namespace, attributeName)
      } else {
        element.getAttributeNode(attributeName)
      }

    if (value == null) {
      // Delete attribute
      if (attr != null) {
        val startOffset: Int = client.xmlParser.getNodeStartOffset(client, file.file, attr)
        val endOffset: Int = client.xmlParser.getNodeEndOffset(client, file.file, attr)
        // Remove surrounding whitespace too
        val padding = if (contents[endOffset] == ' ') 1 else 0
        file.edits.add(PendingEdit(setFix, startOffset, endOffset + padding, ""))
        return true
      }
      return false
    } else {
      if (attr != null) {
        // Already set; change it
        val startOffset: Int = client.xmlParser.getNodeStartOffset(client, file.file, attr)
        val endOffset: Int = client.xmlParser.getNodeEndOffset(client, file.file, attr)

        if (setFix.point == null && attr.value == value) {
          // Skip setting the attribute if we don't need to select the value and the
          // value is unchanged.
          return false
        }

        val point = setFix.point ?: 0
        val mark = setFix.mark ?: point
        val prefix = attr.name + "=\""
        val replacement = prefix + XmlUtils.toXmlAttributeValue(value) + "\""
        file.edits.add(
          PendingEdit(
            setFix,
            startOffset,
            endOffset,
            replacement,
            // Also select value. The SetAttribute dot/mark properties are relative to
            // the value portion, not the whole attribute.
            if (setFix.point == null) -1 else prefix.length + min(point, mark),
            if (setFix.point == null) -1 else prefix.length + max(point, mark),
          )
        )
        return true
      }

      var prefix: String? = null
      var insertNamespaceDeclaration = false
      if (namespace != null) {
        prefix = document.lookupPrefix(namespace)
        if (prefix == null) {
          insertNamespaceDeclaration = true
          val base = suggestNamespacePrefix(namespace) ?: "ns"
          val root = document.documentElement
          var index = 1
          while (true) {
            prefix = base + if (index == 1) "" else index.toString()
            if (!root.hasAttribute(XMLNS_PREFIX + prefix)) {
              break
            }
            index++
          }
        }
      }

      if (insertNamespaceDeclaration && prefix != null) {
        // Insert prefix declaration
        val namespaceAttribute = XMLNS_PREFIX + prefix
        val rootInsertOffset =
          findAttributeInsertionOffset(file.file, contents, document.documentElement, XMLNS, prefix)
        val padLeft = if (!contents[rootInsertOffset - 1].isWhitespace()) " " else ""
        val padRight = if (contents[rootInsertOffset] != '>') " " else ""
        file.edits.add(
          PendingEdit(
            setFix,
            rootInsertOffset,
            rootInsertOffset,
            "$padLeft$namespaceAttribute=\"$namespace\"$padRight",
          )
        )
      }

      if (namespace != null) {
        attributeName = "$prefix:$attributeName"
      }

      val insertOffset =
        findAttributeInsertionOffset(file.file, contents, element, prefix ?: "", setFix.attribute)

      val padLeft = if (!contents[insertOffset - 1].isWhitespace()) " " else ""
      val padRight = if (contents[insertOffset] != '>') " " else ""

      val leftPart = "$padLeft$attributeName=\""
      val valuePart = XmlUtils.toXmlAttributeValue(value)
      val point = setFix.point ?: 0
      val mark = setFix.mark ?: point
      val rightPart = "\"$padRight"
      file.edits.add(
        PendingEdit(
          setFix,
          insertOffset,
          insertOffset,
          leftPart + valuePart + rightPart,
          // Also select value. The SetAttribute point/mark properties are relative to
          // the value portion, not the whole attribute.
          if (setFix.point == null) -1 else leftPart.length + min(point, mark),
          if (setFix.point == null) -1 else leftPart.length + max(point, mark),
        )
      )

      file.reformat = true
      return true
    }
  }

  private fun findAttributeInsertionOffset(
    file: File,
    xml: String,
    element: Element,
    namespacePrefix: String,
    attributeName: String,
  ): Int {
    val attributes = element.attributes

    // The attributes are not in source order; they're using a parser-dependent
    // internal hash map implementation. Therefore, we need to sort them.
    val attributeList = mutableListOf<Pair<Int, Attr>>()
    for (i in 0 until attributes.length) {
      val attribute: Node = attributes.item(i)
      val offset = client.xmlParser.getNodeStartOffset(client, file, attribute)
      attributeList.add(Pair(offset, attribute as Attr))
    }
    attributeList.sortBy { it.first }

    for ((offset, attribute) in attributeList) {
      val name = attribute.localName ?: attribute.nodeName
      val delta =
        compareAttributeNames(namespacePrefix, attributeName, attribute.prefix ?: "", name)
      if (delta < 0) {
        return offset
      }
    }

    if (attributeList.isNotEmpty()) {
      // After last attribute
      val i = client.xmlParser.getNodeEndOffset(client, file, attributeList.last().second)
      if (xml[i] == ' ') {
        // Skip the space separator as well (if we're simultaneously deleting that
        // attribute it will include deleting the space, which would be an edit
        // conflict.)
        return i + 1
      }
      return i
    }

    // The element doesn't have any attributes. Find the attribute insert
    // location; this is the first character after the tag name (and if that's
    // a space, skip it, but there may not be a space there, as in "<tag>", so
    // callers of this method will have to deal with that.
    val startOffset = client.xmlParser.getNodeStartOffset(client, file, element)
    val tagEnd = startOffset + element.tagName.length
    var offset = tagEnd
    while (offset < xml.length) {
      val c = xml[offset]
      if (Character.isWhitespace(c) || c == '>' || c == '/') {
        return if (c == ' ') offset + 1 else offset
      }
      offset++
    }
    return xml.length
  }

  protected open fun addReplaceString(
    fileProvider: FileProvider,
    file: PendingEditFile,
    incident: Incident,
    replaceFix: ReplaceString,
    fixLocation: Location?,
  ): Boolean {
    val contents = fileProvider.getFileContents(file)
    val oldPattern = replaceFix.oldPattern
    val oldString = replaceFix.oldString
    val location = replaceFix.range ?: fixLocation ?: return false

    val start = location.start ?: return false
    val end = location.end ?: return false
    val adjustedEnd =
      if (
        !(oldString.isNullOrEmpty() && oldPattern.isNullOrEmpty()) && end.offset == start.offset
      ) {
        // Location.create(File) just points to (0,0) instead of (0,length)
        contents.length
      } else {
        end.offset
      }

    var adjustedStart = start.offset
    var found = false

    // Repeatedly do the replacement when replaceFix.globally is set:
    next@ while (adjustedStart <= adjustedEnd) {
      val locationRange = contents.substring(adjustedStart, adjustedEnd)
      var startOffset: Int
      var endOffset: Int
      var replacement = replaceFix.replacement
      var continueOffset: Int

      if (oldString == null && oldPattern == null) {
        // Replace the whole range
        startOffset = adjustedStart
        endOffset = adjustedEnd

        // See if there's nothing left on the line; if so, delete the whole line
        var allSpace = true
        for (element in replacement) {
          if (!Character.isWhitespace(element)) {
            allSpace = false
            break
          }
        }

        if (allSpace) {
          var lineBegin = startOffset
          while (lineBegin > 0) {
            val c = contents[lineBegin - 1]
            if (c == '\n') {
              break
            } else if (!Character.isWhitespace(c)) {
              allSpace = false
              break
            }
            lineBegin--
          }

          var lineEnd = endOffset
          while (lineEnd < contents.length) {
            val c = contents[lineEnd]
            lineEnd++
            if (c == '\n') {
              break
            } else if (!Character.isWhitespace(c)) {
              allSpace = false
              break
            }
          }
          if (allSpace) {
            startOffset = lineBegin
            endOffset = lineEnd
          }
        }
        continueOffset = adjustedEnd + 1
      } else if (oldString != null) {
        val index = locationRange.indexOf(oldString)
        when {
          index != -1 -> {
            startOffset = adjustedStart + index
            endOffset = adjustedStart + index + oldString.length
            continueOffset = endOffset
          }
          oldString == INSERT_BEGINNING -> {
            startOffset = adjustedStart
            endOffset = startOffset
            continueOffset = adjustedEnd + 1
          }
          oldString == INSERT_END -> {
            startOffset = adjustedEnd
            endOffset = startOffset
            continueOffset = adjustedEnd + 1
          }
          replaceFix.optional || replaceFix.globally && found -> break@next
          else ->
            throw IllegalArgumentException(
              "Did not find \"" +
                oldString +
                "\" in \"" +
                locationRange +
                "\" in " +
                client.getDisplayPath(file.file) +
                " as suggested in the quickfix.\n" +
                "\n" +
                "Consider calling ReplaceStringBuilder#range() to set a larger range to\n" +
                "search than the default highlight range.\n" +
                "\n" +
                "(This fix is associated with the issue id `${incident.issue.id}`,\n" +
                "reported via ${incident.issue.implementation.detectorClass.name}.)"
            )
        }
        found = true
      } else {
        assert(oldPattern != null)
        val pattern = oldPattern!!.toPattern()
        val matcher = pattern.matcher(locationRange)
        if (!matcher.find()) {
          if (replaceFix.optional || replaceFix.globally && found) break@next
          throw IllegalArgumentException(
            "Did not match pattern \"" +
              oldPattern +
              "\" in \"" +
              locationRange +
              "\" in " +
              client.getDisplayPath(file.file) +
              " as suggested in the quickfix.\n" +
              "\n" +
              "(This fix is associated with the issue id `${incident.issue.id}`,\n" +
              "reported via ${incident.issue.implementation.detectorClass.name}.)"
          )
        } else {
          startOffset = adjustedStart
          endOffset = startOffset
          continueOffset = startOffset + matcher.end()

          if (matcher.groupCount() > 0) {
            startOffset += matcher.start(1)
            endOffset += matcher.end(1)
          } else {
            startOffset += matcher.start()
            endOffset += matcher.end()
          }

          replacement = replaceFix.expandBackReferences(matcher)
          found = true
        }
      }

      replacement = customizeReplaceString(fileProvider, file, replaceFix, replacement)

      val (selectStart, selectEnd) =
        getSelectionDeltas(replaceFix.selectPattern, replacement, replaceFix.optional)
      val edit =
        PendingEdit(
          replaceFix,
          startOffset,
          endOffset,
          replacement,
          selectStart,
          selectEnd,
          replaceFix.sortPriority,
        )
      file.edits.add(edit)

      if (replaceFix.globally) {
        adjustedStart = continueOffset
      } else {
        break
      }
    }

    if (replaceFix.imports.isNotEmpty()) {
      val list = file.imports ?: mutableListOf<String>().also { file.imports = it }
      for (import in replaceFix.imports) {
        if (!list.contains(import)) list.add(import)
      }
    }

    if (replaceFix.shortenNames) {
      file.shortenReferences = true
    }
    if (replaceFix.reformat) {
      file.reformat = true
    }

    return true
  }

  private fun getSelectionDeltas(
    selectPattern: String?,
    source: String,
    optional: Boolean,
  ): Pair<Int, Int> {
    if (selectPattern != null) {
      val pattern = selectPattern.toPattern()
      val matcher = pattern.matcher(source)
      if (matcher.find(0)) {
        if (matcher.groupCount() > 0) {
          return Pair(matcher.start(1), matcher.end(1))
        } else {
          return Pair(matcher.start(), matcher.end())
        }
      } else if (!optional && isUnitTest) {
        throw IllegalArgumentException(
          "Didn't find selection pattern " + selectPattern + "in " + source
        )
      }
      return Pair(-1, -1)
    }

    return Pair(-1, -1)
  }

  protected open fun customizeReplaceString(
    fileProvider: FileProvider,
    file: PendingEditFile,
    replaceFix: ReplaceString,
    replacement: String,
  ): String {
    return replacement
  }

  fun computeEdits(
    incident: Incident,
    lintFix: LintFix,
    fileProvider: FileProvider = createFileProvider(),
  ): List<PendingEditFile> {
    val fileMap = mutableMapOf<File, PendingEditFile>()
    registerFix(fileProvider, fileMap, incident, lintFix)
    return fileMap.values.toList()
  }

  companion object {
    fun getLocation(incident: Incident, fix: LintFix? = incident.fix): Location {
      return fix?.range ?: incident.location
    }

    fun isEditingFix(fix: LintFix): Boolean {
      return fix is ReplaceString ||
        fix is AnnotateFix ||
        fix is SetAttribute ||
        fix is CreateFileFix
    }

    /** Not all fixes are eligible for auto-fix; this function checks whether a given fix is. */
    fun canAutoFix(lintFix: LintFix): Boolean {
      return canAutoFix(lintFix, true)
    }

    fun canAutoFix(lintFix: LintFix, requireAutoFixable: Boolean): Boolean {
      if (
        !requireAutoFixable && !(lintFix is LintFixGroup && lintFix.type == GroupType.ALTERNATIVES)
      ) {
        return true
      }

      if (lintFix is LintFixGroup) {
        when (lintFix.type) {
          GroupType.ALTERNATIVES ->
            // More than one type: we don't know which to apply
            return false
          GroupType.COMPOSITE -> {
            // All nested fixes must be auto-fixable
            for (nested in lintFix.fixes) {
              if (!canAutoFix(nested, requireAutoFixable)) {
                return false
              }
            }
            return true
          }
        }
      }

      if (!lintFix.robot) {
        return false
      }
      if (!lintFix.independent) {
        // For now. TODO: Support these, via repeated analysis runs
        return false
      }

      return true
    }

    /** Given a namespace URI, suggest a good default XML namespace prefix */
    fun suggestNamespacePrefix(uri: String?): String? {
      return when (uri) {
        null -> ""
        ANDROID_URI -> ANDROID_NS_NAME
        TOOLS_URI -> TOOLS_PREFIX
        XLIFF_URI -> XLIFF_PREFIX
        AAPT_URI -> AAPT_PREFIX
        DIST_URI -> DIST_PREFIX
        AUTO_URI -> APP_PREFIX
        else -> {
          if (uri.startsWith(URI_PREFIX)) {
            return APP_PREFIX
          }
          return null
        }
      }
    }

    fun compareAttributeNames(prefix1: String, n1: String, prefix2: String, n2: String): Int {
      val rank1 = rankAttributeNames(prefix1, n1)
      val rank2 = rankAttributeNames(prefix2, n2)
      val delta = rank1 - rank2
      if (delta != 0) {
        return delta
      }
      return n1.compareTo(n2)
    }

    private fun rankAttributeNames(prefix: String, name: String): Int {
      return when (prefix) {
        // Namespace declarations are always first, tools attributes are always last
        XMLNS -> 0
        TOOLS_PREFIX -> 100 // tools attributes go last

        // We generally put no-namespace attributes second to last (before tools attributes),
        // except for a couple of special cases: package (in manifests), and style and layout
        // (in layout files)
        "" -> {
          if (name == ATTR_PACKAGE || name == ATTR_LAYOUT || name == ATTR_STYLE) 20
          // color sorts to the end; see XmlAttributeSortOrder.getAttributePriority
          else if (name == ATTR_COLOR) {
            95
          } else 90
        }
        AAPT_PREFIX -> 7
        ANDROID_NS_NAME -> {
          if (name == ATTR_ID || name == ATTR_NAME) {
            10
          } else if (name.startsWith(ATTR_LAYOUT_RESOURCE_PREFIX)) {
            if (name == ATTR_LAYOUT_WIDTH) {
              30
            } else if (name == ATTR_LAYOUT_HEIGHT) {
              32
            } else {
              34
            }
          } else {
            40
          }
        }
        DIST_PREFIX -> 45
        APP_PREFIX -> 50

        // Other name spaces: sort alphabetically after app namespace and before the non-namespace
        else -> 60
      }
    }

    fun implicitlyImported(pkg: String): Boolean {
      // See Kotlin spec https://kotlinlang.org/spec/packages-and-imports.html
      return when (pkg) {
        "kotlin",
        "kotlin.jvm",
        "kotlin.annotation",
        "kotlin.collections",
        "kotlin.comparisons",
        "kotlin.io",
        "kotlin.ranges",
        "kotlin.sequences",
        "kotlin.text",
        "kotlin.math",
        "java.lang" -> true
        else -> false
      }
    }

    fun createAnnotationFix(
      fix: AnnotateFix,
      location: Location?,
      contents: String?,
    ): ReplaceString {

      val replaceFixBuilder = LintFix.create().replace().shortenNames().reformat(true)

      var range = location
      // Don't use fully qualified names for implicitly imported packages
      val annotation =
        fix.annotation.let {
          val argStart = it.indexOf('(', 1).let { index -> if (index == -1) it.length else index }
          val packageEnd = it.lastIndexOf('.', argStart)
          if (packageEnd != -1 && implicitlyImported(it.substring(1, packageEnd))) {
            "@" + it.substring(packageEnd + 1)
          } else {
            val fqn = it.substring(1, argStart)
            if (
              contents != null &&
                contents.contains(fqn) &&
                (contents.contains("import $fqn\n") || contents.contains("import $fqn;"))
            ) {
              "@" + it.substring(packageEnd + 1)
            } else {
              it
            }
          }
        }

      // Skip comments
      var oldText = INSERT_BEGINNING
      if (range?.start != null && contents != null) {
        val start = range.start!!
        val startOffset = start.offset
        val isKotlin = range.file.path.endsWith(DOT_KT) || range.file.path.endsWith(DOT_KTS)
        var offset =
          skipCommentsAndWhitespace(contents, startOffset, allowCommentNesting = isKotlin)

        if (fix.replace) {
          val symbolEnd = annotation.substringBefore('(')
          var current = offset
          while (current < contents.length) {
            if (contents[current] == '@') {
              if (
                contents.startsWith(symbolEnd, current) &&
                  current + symbolEnd.length < contents.length &&
                  !contents[current + symbolEnd.length].isJavaIdentifierPart()
              ) {
                // Found the annotation to be replaced!
                offset = current
                // Also remove this one
                val annotationEnd = skipAnnotation(contents, current)
                oldText = contents.substring(current, annotationEnd)
                break
              }
              // Found some other annotation: skip it, and skip whitespace/comments, and look at
              // next annotation
              current = skipAnnotation(contents, current)
              current = skipCommentsAndWhitespace(contents, current)
            } else {
              // We've hit something else (for example "fun" or "public"), so we didn't find the
              // replacement
              // annotation; we're inserting a new one.
              break
            }
          }
        }

        if (annotation.startsWith("@file:")) {
          offset = 0
        }

        if (offset != startOffset) {
          // We skipped past whitespace and/or comments; update the range
          range =
            Location.create(
              range.file,
              DefaultPosition(-1, -1, offset),
              DefaultPosition(-1, -1, max(range.end?.offset ?: -1, startOffset)),
            )
        }
      }

      var replacement = annotation
      if (oldText == INSERT_BEGINNING) {
        replacement += "\n"
        // Add indent?
        if (range != null && contents != null && range.start != null) {
          val start = range.start!!
          val startOffset = start.offset
          var lineBegin = startOffset
          while (lineBegin > 0) {
            val c = contents[lineBegin - 1]
            if (!Character.isWhitespace(c)) {
              break
            } else if (c == '\n' || lineBegin == 1) {
              if (startOffset > lineBegin) {
                val indent = contents.substring(lineBegin, startOffset)
                replacement = annotation + "\n" + indent
              }
              break
            } else lineBegin--
          }
        }
      }

      replaceFixBuilder.text(oldText).with(replacement)

      // Do we have a valid range?
      if (
        range != null &&
          range.end?.offset != null &&
          range.start?.offset != null &&
          range.end!!.offset >= range.start!!.offset
      ) {
        replaceFixBuilder.range(range)
      } else {
        // b/301598518
        if (isUnitTest) {
          error(
            """
            Invalid location $range computed for Lint fix ${fix.getDisplayName()}} during tests.
            This can happen if the location for the fix was not correctly specified.
            """
              .trimIndent()
          )
        }
      }
      return replaceFixBuilder.build() as ReplaceString
    }

    /**
     * Given Java or Kotlin [source] code, and a starting offset, skip any whitespace and comments
     * and return the first offset which is not whitespace or part of a comment. If
     * [allowCommentNesting] is true, block comments can be nested -- this should be true for Kotlin
     * code and false for Java code.
     */
    fun skipCommentsAndWhitespace(
      source: CharSequence,
      start: Int,
      allowCommentNesting: Boolean = true,
      stopAtNewline: Boolean = false,
    ): Int {
      var index = start
      val length = source.length
      while (index < length) {
        val c = source[index++]
        if (c.isWhitespace()) {
          if (c == '\n' && stopAtNewline) {
            return index
          }
          continue
        } else if (c == '/' && index < length) {
          if (source[index] == '/') { // line comment
            index = source.indexOf('\n', index) + 1
            if (index == 0) {
              return length
            }
          } else if (source[index] == '*') {
            // block comment
            index++
            var nesting = 1
            while (index < length) {
              val d = source[index++]
              if (allowCommentNesting && d == '/' && index < length && source[index] == '*') {
                index++
                nesting++
              } else if (d == '*' && index < length && source[index] == '/') {
                index++
                nesting--
                if (nesting == 0) {
                  break
                }
              }
            }
          } else {
            return index - 1
          }
        } else {
          return index - 1
        }
      }
      return index
    }

    /**
     * Given Java or Kotlin [source] code, and a starting offset which points at an annotation
     * declaration (such as `@file:Suppress("test")` or
     * `@SuppressWarnings({"HardCodedStringLiteral", "DialogTitleCapitalization"})`) return the
     * offset of the character after the final closing `)`.
     *
     * This is done by first skipping through the annotation name (e.g. "file:Suppress"), then
     * tracking parenthesis balance, and when we get to 0 parenthesis balance after seeing a `)`
     * we're done. Also, whenever we see a comment, skip it (using [skipCommentsAndWhitespace]), and
     * whenever we see a string or character literal, skip it (so we're not confused by ")" inside a
     * comment for example.)
     */
    fun skipAnnotation(source: CharSequence, start: Int): Int {
      val length = source.length
      assert(source[start] == '@')
      var offset = start + 1
      var balance = 0
      // Skip annotation identifier (which could contain :, as in @file:Suppress)
      while (offset < length && (source[offset].isJavaIdentifierPart() || source[offset] == ':')) {
        offset++
      }
      val next = skipCommentsAndWhitespace(source, offset)
      if (next == length || source[next] != '(') {
        return offset
      }

      offset = next
      while (offset < length) {
        val d = source[offset++]
        if (d == '(' || d == '{') {
          balance++
        } else if (d == ')' || d == '}') {
          balance--
          if (balance == 0) {
            break
          }
        } else if (d == '"' || d == '\'') {
          // skip string and char literals
          while (offset < length) {
            val e = source[offset++]
            if (e == '\\') {
              offset++
            } else if (e == d) {
              break
            }
          }
        } else if (d == '/') {
          offset = skipCommentsAndWhitespace(source, offset - 1)
        }
      }
      return offset
    }
  }

  inner class PendingEditFile(val file: File) {

    /** List of edits to perform in this file */
    val edits: MutableList<PendingEdit> = mutableListOf()

    /** Set of imports to add to the file; see [ReplaceString.imports] for more. */
    var imports: MutableList<String>? = null

    /**
     * Whether we should shorten references in edited regions; see [ReplaceString.shortenNames] for
     * more.
     */
    var shortenReferences: Boolean = false

    /**
     * Whether we should reformat references in edited regions; see [ReplaceString.reformat] for
     * more.
     */
    var reformat: Boolean = false

    /** Whether the file should be opened in the editor. */
    var open: Boolean = false

    /** Whether this file should be deleted. */
    var delete: Boolean = false

    /** Whether this is a new file to be created. */
    var createText: Boolean = false

    /**
     * Whether this is a new file to be created with **binary** content. (In this case, [edits]
     * should always be empty.)
     */
    var createBytes: ByteArray? = null

    /**
     * Returns true if this file ends up having no effect (for example, we created a set attribute
     * where the attribute is already set to the same value so it ends up being a no-op.
     */
    fun isEmpty(): Boolean {
      return edits.isEmpty() && !createText && createBytes == null && !delete
    }

    /** Returns the affected range -- the offsets in the original file where edits begin and end. */
    fun affectedRange(): TextRange {
      if (edits.isEmpty()) {
        return TextRange.EMPTY_RANGE
      }
      return TextRange(edits.minOf { it.startOffset }, edits.maxOf { it.endOffset })
    }
  }

  /** An individual edit to be applied inside a [PendingEditFile]. */
  class PendingEdit(
    /** The fix associated with this edit */
    val fix: LintFix,
    /** Where in the original source to start the edit operation */
    val startOffset: Int,
    /**
     * Where in the original source to end the edit operation. If same as [startOffset], this is an
     * insert operation, otherwise it's a replacement or deletion operation.
     */
    val endOffset: Int,
    /** The string to insert. If empty, this is a deletion operation. */
    val replacement: String,
    /** If not -1, the delta **relative to [startOffset]** to start a selection. */
    val selectStart: Int = -1,
    /** If not -1, the delta **relative to [startOffset]** to end the selection. */
    val selectEnd: Int = -1,
    /** Sorting priority to use for edits that start at the same location. */
    private val sortPriority: Int = -1,
  ) : Comparable<PendingEdit> {

    override fun compareTo(other: PendingEdit): Int {
      val delta = other.startOffset - this.startOffset
      if (delta != 0) {
        return delta
      } else {
        // Same offset: Sort deletions before insertions
        // (we might delete some text and insert some new text as separate
        // operations, e.g. one attribute getting removed, another getting inserted.
        // We need to apply the deletions first since these are referencing the
        // old text, not newly inserted text.)
        val d1 = if (this.isReplace()) 0 else 1
        val d2 = if (other.isReplace()) 0 else 1
        val deleteDelta = d1 - d2
        if (deleteDelta != 0) {
          return deleteDelta
        }
      }

      val sortDelta = other.sortPriority - sortPriority
      if (sortDelta != 0) {
        return sortDelta
      }

      return other.endOffset - this.endOffset
    }

    fun apply(contents: String): String {
      return StringBuilder(contents).replace(startOffset, endOffset, replacement).toString()
    }

    /** Is this edit **only** inserting text? */
    fun isInsert(): Boolean = endOffset == startOffset

    /** Is this edit **only** deleting text? */
    fun isDelete(): Boolean = replacement.isEmpty()

    /** Is this edit both inserting and deleting text? */
    fun isReplace(): Boolean = endOffset > startOffset

    /** Returns the name of the fix associated with this edit */
    fun fixName(): String {
      return fix.getFamilyName() ?: return fix.getDisplayName() ?: fix.javaClass.simpleName
    }

    override fun toString(): String = toString(null)

    fun toString(source: String?): String {
      return when {
        isDelete() ->
          "At $startOffset, delete \"${
            source?.substring(
              startOffset,
              endOffset,
            ) ?: "${endOffset - startOffset} characters"
          }\""
        isInsert() -> "At $startOffset, insert \"$replacement\""
        else ->
          "At $startOffset, change \"${
            source?.substring(
              startOffset,
              endOffset,
            ) ?: "${endOffset - startOffset} characters"
          }\" to \"$replacement\""
      }
    }

    override fun equals(other: Any?): Boolean {
      // (Deliberately does not include sort priority -- content only.)
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as PendingEdit

      if (startOffset != other.startOffset) return false
      if (endOffset != other.endOffset) return false
      if (replacement != other.replacement) return false
      if (selectStart != other.selectStart) return false
      if (selectEnd != other.selectEnd) return false

      return true
    }

    override fun hashCode(): Int {
      // (Deliberately does not include sort priority -- content only.)
      var result = startOffset
      result = 31 * result + endOffset
      result = 31 * result + replacement.hashCode()
      result = 31 * result + selectStart.hashCode()
      result = 31 * result + selectEnd.hashCode()
      return result
    }
  }
}
