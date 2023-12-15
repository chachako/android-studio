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

package com.android.tools.lint

import com.android.SdkConstants
import com.android.tools.lint.client.api.LintFixPerformer
import com.android.tools.lint.client.api.LintFixPerformer.Companion.skipCommentsAndWhitespace
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Severity
import java.io.File
import java.io.PrintWriter
import java.util.TreeMap
import kotlin.math.min
import org.jetbrains.annotations.VisibleForTesting

/** Support for applying quickfixes directly. */
open class LintCliFixPerformer(
  private val client: LintCliClient,
  /** Whether to emit statistics about number of files modified and number of edits applied. */
  private val printStatistics: Boolean = true,
  /** Should applied fixes be limited to those marked as safe to be applied automatically? */
  requireAutoFixable: Boolean = true,
  /**
   * Should we include markers in the applied files like indicators for the marker and selection?
   */
  private val includeMarkers: Boolean = false,
  /** Should we also add import statements? */
  private val updateImports: Boolean = includeMarkers,
  /**
   * Whether to perform shortening of all symbols in the replacement string, not just imported
   * symbpls.
   */
  private val shortenAll: Boolean = includeMarkers
) : LintFixPerformer(client, requireAutoFixable) {
  override fun getSourceText(file: File): CharSequence {
    return client.getSourceText(file)
  }

  override fun log(severity: Severity, message: String) {
    client.log(Severity.WARNING, null, message)
  }

  protected open fun writeFile(file: File, contents: String) {
    writeFile(file, contents.toByteArray(Charsets.UTF_8))
  }

  protected open fun writeFile(file: File, contents: ByteArray?) {
    if (contents == null) {
      file.delete()
    } else {
      file.parentFile?.mkdirs()
      file.writeBytes(contents)
    }
  }

  override fun createBinaryFile(fileData: PendingEditFile, contents: ByteArray) {
    writeFile(fileData.file, contents)
  }

  override fun deleteFile(fileData: PendingEditFile) {
    writeFile(fileData.file, null)
  }

  override fun applyEdits(fileData: PendingEditFile, edits: List<PendingEdit>) {
    var fileContents = fileData.initialText

    // First selection in the source (edits are sorted in reverse order so pick the last one)
    val firstSelection = edits.lastOrNull { it.selectStart != -1 }

    applyEdits(fileData, edits) { _, edit ->
      fileContents = edit.apply(fileContents)
      if (includeMarkers && edit.selectStart != -1 && edit.selectEnd != -1) {
        if (edit !== firstSelection) {
          // We can only select one range. If multiple edits are marked for selection,
          // only pick the first one. We can easily end up in scenarios like this,
          // for example in the FontDetector, where we have a fix to set all missing
          // attributes; these use the fix().set().todo() call, and todo will default
          // to selecting the "TODO" token, but we only want to select the first one.
        } else {
          fileContents =
            injectSelection(
              fileContents,
              edit.startOffset + edit.selectStart,
              edit.startOffset + edit.selectEnd
            )
        }
      }
    }
    writeFile(fileData.file, fileContents)
  }

  /**
   * Indicates caret position with a `|` and the selection range using square brackets if set by the
   * fix.
   */
  private fun injectSelection(
    fileContents: String,
    selectionStartOffset: Int,
    selectionEndOffset: Int
  ): String {
    assert(includeMarkers)
    if (selectionStartOffset == -1) {
      return fileContents
    }
    return StringBuilder(fileContents)
      .apply {
        if (selectionEndOffset == selectionStartOffset) {
          replace(selectionEndOffset, selectionEndOffset, "|")
        } else {
          replace(selectionEndOffset, selectionEndOffset, "]|")
          replace(selectionStartOffset, selectionStartOffset, "[")
        }
      }
      .toString()
  }

  override fun printStatistics(
    editMap: MutableMap<String, Int>,
    appliedEditCount: Int,
    editedFileCount: Int
  ) {
    if (printStatistics && editedFileCount > 0) {
      val printWriter = PrintWriter(System.out, true, Charsets.UTF_8)
      printStatistics(printWriter, editMap, appliedEditCount, editedFileCount)
    }
  }

  protected open fun printStatistics(
    writer: PrintWriter,
    editMap: MutableMap<String, Int>,
    appliedEditCount: Int,
    editedFileCount: Int
  ) {
    if (editMap.keys.size == 1) {
      writer.println(
        "Applied $appliedEditCount edits across $editedFileCount files for this fix: ${editMap.keys.first()}"
      )
    } else {
      writer.println("Applied $appliedEditCount edits across $editedFileCount files")
      editMap.forEach { (name, count) -> writer.println("$count: $name") }
    }
  }

  private class ImportInfo {
    var packageStatement: Pair<String, Int>? = null
    val staticImports = TreeMap<String, Int>()
    val nonStaticImports = TreeMap<String, Int>()

    fun qualifiedNames(): Set<String> = staticImports.keys + nonStaticImports.keys

    fun names(): Set<String> = qualifiedNames().map { it.substringAfterLast('.') }.toSet()
  }

  override fun customizeReplaceString(
    file: PendingEditFile,
    replaceFix: LintFix.ReplaceString,
    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") originalReplacement: String
  ): String {
    var replacement = originalReplacement
    val contents: String = file.initialText

    // Are we in a unit-testing scenario? If so, perform some cleanup of imports etc.
    // (which is normally handled by the IDE)
    if (updateImports || shortenAll && replaceFix.shortenNames) {
      val addImports = replaceFix.imports.toMutableList()

      val isJava = file.file.path.endsWith(SdkConstants.DOT_JAVA)
      val allowCommentNesting = !isJava
      val importInfo: ImportInfo =
        getExistingImports(contents, allowCommentNesting = allowCommentNesting)
      if (replaceFix.shortenNames) {
        // This isn't fully shortening names, it's only removing fully qualified
        // names for symbols already imported.
        //
        // Also, this will not correctly handle some conflicts. This is only used
        // for unit testing lint fixes, not for actually operating on code; for
        // that we're using IntelliJ's built-in import cleanup when running in the
        // IDE. Examples of things that can go wrong:
        // * Wildcard imports. We'll just remove the package prefix in this case,
        //   but this would not take into account whether there is a different
        //   import which should take precedence. (We could sort these to the end
        //   to help mitigate such that we'll apply the more specific one.)
        // * Conflicts with local symbols; there may be a super class defining a
        //   nested inner class which would take precedence over the file import;
        //   here, we'll just assume that removing the prefix would resolve to the
        //   import -- to fix this we'll need the symbol table.
        // * We might be replacing text in a non-code context (such as a string or
        //   comment) -- though it's highly unlikely the quickfix would have been
        //   marked for reference shortening in that case.
        val removePrefix: MutableSet<String> = HashSet()
        val qualifiedNames = importInfo.qualifiedNames()
        for (symbol in qualifiedNames) {
          removePrefix.add(symbol.removeSuffix("*"))
        }
        val names = importInfo.names()
        // Collect newly qualified names in the replacement code snippet and insert it
        // This is a bit inaccurate so only do under tests
        if (shortenAll) {
          importInfo.packageStatement?.first?.let { removePrefix.add("$it.") }
          for (import in collectNames(replacement, allowCommentNesting = !isJava)) {
            if (qualifiedNames.contains(import)) {
              continue
            }
            val dot = import.indexOfDotUpperCase()
            if (dot == -1) {
              continue
            }
            val name = import.substring(dot + 1)
            if (names.contains(name)) {
              // Conflicts with already imported name
              continue
            }
            val pkg = import.substring(0, dot + 1)
            if (removePrefix.contains(pkg)) {
              // Conflicts with wildcard
              continue
            }

            // Import the symbol (but if it's something like
            // java.nio.charset.StandardCharsets.UTF_8,
            // import StandardCharsets, not the UTF_8 constant -- e.g. import the first symbol.)
            val nameEnd = import.indexOf('.', dot + 1).let { if (it != -1) it else import.length }
            val importName = import.substring(0, nameEnd)

            removePrefix.add(importName)
            if (
              !addImports.contains(importName) &&
                !implicitlyImported(importName.substringBeforeLast('.'))
            ) {
              addImports.add(importName)
            }
          }
        }
        for (full in removePrefix) {
          var clz = full
          if (replacement.contains(clz)) {
            val isWildcard = clz.endsWith(".")
            if (!isWildcard) {
              val index = clz.lastIndexOf('.')
              if (index == -1) {
                continue
              }
              // If it's a wildcard make sure there's no existing import conflict!
              clz = clz.substring(0, index + 1)
            }
            replacement = removePackage(replacement, clz, names, isWildcard)
          }
        }
      }

      if (addImports.isNotEmpty()) {
        // Collect existing imports
        val nonstaticImports = importInfo.nonStaticImports
        val staticImports = importInfo.staticImports

        // Insert in reverse order to make sure that if we have multiple imports
        // that go in the same place in the existing import order, they end
        // up in ascending alphabetical order
        for (import in addImports.sortedDescending()) {
          val isMethod = import[import.lastIndexOf('.') + 1].isLowerCase()
          val isStaticImport = isJava && isMethod

          if (!nonstaticImports.contains(import) && !staticImports.contains(import)) {
            var insertOffset = -1

            val imports =
              if (isStaticImport && staticImports.isNotEmpty()) staticImports else nonstaticImports
            for ((imported, importedOffset) in imports) {
              if (imported > import) {
                insertOffset = importedOffset
                break
              }
            }
            if (insertOffset == -1) {
              // Insert after all import statements
              if (imports.isNotEmpty()) {
                val last = imports.maxOf { it.value }
                val lineEnd =
                  contents.indexOf('\n', last).let { if (it == -1) contents.length else it }
                // What if end begins block comment? This is unlikely, but just to be safe,
                // make sure there isn't the beginning of a block comment on this line. It
                // could also terminate here, but in this case, we'll just insert before
                // this import
                insertOffset =
                  if (contents.subSequence(last, lineEnd).contains("/*")) {
                    last
                  } else {
                    min(lineEnd + 1, contents.length)
                  }
              } else {
                // No imports: place after package statement Note that there might be
                // no package statement -- and we can't just skip comments to find
                // the real beginning since we may have annotations on the package
                // statement (in package-info files) or at the file level (in Kotlin
                // files). As long as we skip comments, we're likely to find this as the
                // first symbols named "package" or "import" (though it's conceivable
                // but unlikely that it could be something like "@Source("package
                // test.pkg") which would require properly skipping strings as
                // well - or even something more complicated like "@Source("test
                // ${compute("package test.pkg")}") where we're in nested string contexts.)
                var offset = 0
                val length = contents.length
                while (offset < length) {
                  offset = skipCommentsAndWhitespace(contents, offset, allowCommentNesting)
                  if (offset == length) {
                    insertOffset = length
                    break
                  } else if (contents.startsWith("package", offset)) {
                    insertOffset = min(contents.lineEnd(offset) + 1, contents.length)
                    break
                  } else if (contents[offset] == '@') {
                    offset = skipAnnotation(contents, offset)
                  } else {
                    insertOffset = contents.lineBegin(offset)
                    break
                  }
                }
              }
            }
            val importStatement =
              if (isJava) {
                "import ${if (isStaticImport) "static " else ""}$import;\n"
              } else {
                // Kotlin
                "import $import\n"
              }

            file.edits.add(
              PendingEdit(replaceFix, contents, insertOffset, insertOffset, importStatement)
            )
          }
        }
      }
    }
    return replacement
  }

  /**
   * Given a package [prefix] and a Java/Kotlin source fragment, removes the package prefix from any
   * fully qualified references with that package prefix. The reason we can't just use
   * [String.replace] is that we only want to replace prefixes in the same package, not in any sub
   * packages.
   *
   * For example, given the package prefix `p1.p2`, for the source string `p1.p2.p3.Class1,
   * `p1.p2.Class2`, this method will return `p1.p2.p3.Class1, Class2`.
   */
  private fun removePackage(
    source: String,
    prefix: String,
    names: Set<String>,
    isWildcard: Boolean
  ): String {
    if (prefix.isEmpty()) {
      return source
    }

    // Checks whether the symbol starting at offset [next] references
    // the [prefix] package and not potentially some subpackage of it
    fun isPackageMatchAt(next: Int): Boolean {
      var i = next + prefix.length
      while (i < source.length) {
        val c = source[i++]
        if (c == '.') {
          return false
        } else if (!c.isJavaIdentifierPart()) {
          return true
        }
      }
      return true
    }

    val sb = StringBuilder()
    var index = 0
    while (true) {
      val next = source.indexOf(prefix, index)
      sb.append(source.substring(index, if (next == -1) source.length else next))
      if (next == -1) {
        break
      }
      index = next + prefix.length
      if (isWildcard) {
        var nameEnd = index
        while (nameEnd < source.length && source[nameEnd].isJavaIdentifierPart()) {
          nameEnd++
        }
        val name = source.substring(index, nameEnd)
        if (names.contains(name)) {
          sb.append(source.substring(next, index))
          continue
        }
      }
      if ((index == source.length || !source[index].isUpperCase()) && !isPackageMatchAt(next)) {
        sb.append(source.substring(next, index))
      }
    }

    return sb.toString()
  }

  private fun getExistingImports(contents: CharSequence, allowCommentNesting: Boolean): ImportInfo {
    val info = ImportInfo()
    val imports = info.nonStaticImports
    val staticImports = info.staticImports
    var index = 0
    val length = contents.length
    while (index < length) {
      index = skipCommentsAndWhitespace(contents, index, allowCommentNesting)
      if (index == length) {
        break
      }
      val c = contents[index++]
      if (c.isWhitespace()) {
        continue
      } else if (c == 'p' && contents.startsWith("package", index - 1)) {
        val start = index - 1
        index = start + "package".length
        while (index < length && contents[index].isWhitespace()) {
          index++
        }
        val symbolStart = index
        while (
          index < length &&
            contents[index] != '\n' &&
            contents[index] != ';' &&
            contents[index] != '/'
        ) {
          index++
        }
        // back up over any trailing spaces
        while (index > 0 && contents[index - 1] == ' ') {
          index--
        }
        val symbol = contents.substring(symbolStart, index)
        info.packageStatement = Pair(symbol, start)

        // Skip package statement
        index = contents.indexOf('\n', index) + 1
        if (index == 0) {
          break
        }
      } else if (c == 'i' && contents.startsWith("import", index - 1)) {
        val start = index - 1
        index = start + "import".length
        while (index < length && contents[index].isWhitespace()) {
          index++
        }
        var isStatic = false
        if (
          contents.startsWith("static", index) &&
            index + "static".length < length &&
            contents[index + "static".length].isWhitespace()
        ) {
          index += "static".length
          isStatic = true
        }
        while (index < length && contents[index].isWhitespace()) {
          index++
        }
        val symbolStart = index
        // Find end of imported symbol (e.g. in Java, ';', and possibly avoiding
        // trailing line comments too). Allow spaces inside (e.g. import "java .
        // util . List"; allowed but not common.)
        while (
          index < length &&
            contents[index] != '\n' &&
            contents[index] != ';' &&
            contents[index] != '/'
        ) {
          index++
        }
        // back up over any trailing spaces
        while (index > 0 && contents[index - 1] == ' ') {
          index--
        }
        val symbol = contents.substring(symbolStart, index)
        if (isStatic) {
          staticImports[symbol] = start
        } else {
          imports[symbol] = start
        }
        index = contents.indexOf('\n', index) + 1
        if (index == 0) {
          break
        }
      } else if (c == '@') {
        index = skipAnnotation(contents, index - 1)
      } else {
        break
      }
    }

    // If static imports are mixed with non-static imports just put them all together
    val classImportsStart = if (imports.isNotEmpty()) imports.minOf { it.value } else 0
    if (!staticImports.all { it.value < classImportsStart }) {
      for ((key, value) in staticImports) {
        imports[key] = value
      }
      staticImports.clear()
    }
    return info
  }
}

/**
 * Collects fully qualified names in the given code sample (skipping references in comments and
 * string literals)
 */
@VisibleForTesting
fun collectNames(code: String, allowCommentNesting: Boolean): Set<String> {
  val set = mutableSetOf<String>()
  var offset = 0
  val length = code.length
  while (offset < length) {
    val c = code[offset]
    if (c.isWhitespace() || c == '/') {
      offset = skipCommentsAndWhitespace(code, offset, allowCommentNesting)
    } else if (c == '\'' || c == '"') {
      offset = skipStringLiteral(code, offset)
    } else if (!c.isJavaIdentifierStart()) {
      offset++
    } else {
      // Found identifier start
      val start = offset++
      var isQualified = false
      while (offset < length) {
        val ch = code[offset]
        if (ch == '.' && offset < length - 1) {
          if (code[offset + 1].isUpperCase()) {
            isQualified = true
          }
        } else if (!ch.isJavaIdentifierPart()) {
          break
        }
        offset++
      }
      if (isQualified && !code[start].isUpperCase()) {
        set.add(code.substring(start, offset))
      }
    }
  }
  return set
}

/**
 * Given Java or Kotlin [source] code, and a starting offset which points at a string or character
 * literal, return the offset of the character after the final closing character.
 */
@VisibleForTesting
fun skipStringLiteral(source: CharSequence, start: Int): Int {
  val first = source[start]
  val length = source.length
  if (first == '"' || first == '\'') {
    if (source.startsWith("\"\"\"", start)) {
      // TODO: Handle substitutions, ${} ?
      // Raw string
      val end = source.indexOf("\"\"\"", start + 3)
      if (end != -1) {
        return end + 3
      } else {
        return source.length
      }
    }
    var offset = start + 1
    while (offset < length) {
      val c = source[offset++]
      if (c == '\\') {
        offset++
      } else if (c == first) {
        return offset
      }
    }
  } else {
    error("Only call on strings.")
  }
  return length
}

/** Returns the index of the first dot followed by an uppercase character. */
private fun CharSequence.indexOfDotUpperCase(): Int {
  for (i in this.indices) {
    if (this[i] == '.' && i < length && this[i + 1].isUpperCase()) {
      return i
    }
  }
  return -1
}

private fun CharSequence.lineEnd(start: Int): Int {
  return this.indexOf('\n', start).let { if (it == -1) length else it }
}

private fun CharSequence.lineBegin(start: Int): Int {
  return this.lastIndexOf('\n', start).let { if (it == -1) 0 else it + 1 }
}
