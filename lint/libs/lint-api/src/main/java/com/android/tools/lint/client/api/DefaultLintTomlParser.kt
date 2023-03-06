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

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Severity
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime

/**
 * Simple TOML parser, optimized for lint's needs. Focuses on fault tolerance rather than accepting
 * strictly valid TOML files since we may be parsing files that are actively being edited in the
 * IDE.
 *
 * As an example, TOML has some specific sets of characters allowed in bare keys, whereas here we'll
 * allow more since the grammar isn't ambiguous. When you edit files in IntelliJ, the TOML plugin
 * will perform validation warnings.
 */
internal class DefaultLintTomlParser(
  private val file: File,
  private val source: CharSequence,
  private val onProblem: ((Severity, Location, String) -> Unit)?,
  private val validate: Boolean = onProblem != null,
) {
  private var offset: Int = 0
  private val length = source.length
  private val document = TomlDocument()

  init {
    parseMapElements(document.root)
  }

  internal fun getDocument(): LintTomlDocument = document

  private fun warn(message: String, at: Int) {
    onProblem?.invoke(Severity.WARNING, Location.create(file, source, at, at), message)
  }

  private fun parseMapElements(parent: TomlMapValue, inInlineTable: Boolean = false): TomlMapValue {
    val mapStart = offset
    var currentArray: List<String>

    var target: TomlMapValue = parent
    var lastValue: TomlValue? = null

    while (offset < length) {
      val keyStart = skipToNextToken(false)
      if (offset == length) {
        break
      }
      when (val token = getToken()) {
        "[" -> {
          // Table -- https://toml.io/en/v1.0.0#table
          // (if we were in a value this would be an array, https://toml.io/en/v1.0.0#array)
          // if we are in an inline table, this is invalid
          val start = offset - 1
          val keyStart = offset
          currentArray = parseKey() ?: emptyList()

          val keyEnd = offset
          val close = getToken()
          if (validate) {
            if (close != "]") {
              warn("= missing ]`", keyEnd)
            }
            if (parent.find(currentArray) != null) {
              warn(
                "You cannot define a table (`${currentArray.joinToString(".")}`) more than once",
                start
              )
            }
          }
          if (inInlineTable) {
            if (validate) {
              warn("cannot define a table in an inline table", start)
            }
            continue
          }

          target = parent.ensure(currentArray, validate)
          if (target.getStartOffset() == -1) {
            target.setStartOffset(start)
            if (target.getKey() != null) {
              target.setKeyRange(keyStart, keyEnd)
            }
          }
        }
        "[[" -> {
          // Array of tables -- https://toml.io/en/v1.0.0#array-of-tables
          // if we are in an inline table, this is invalid
          val start = offset - 1
          currentArray = parseKey() ?: emptyList()

          val keyEnd = offset
          val close = getToken()
          if (validate) {
            if (close != "]]") {
              warn("= missing ]]`", keyEnd)
            }
          }

          if (inInlineTable) {
            if (validate) {
              warn("cannot define an array of tables in an inline table", start)
            }
            continue
          }

          val arrayStart = skipToNextToken(false)

          val array = parent.find(currentArray)
          if (array is TomlArrayValue) {
            target = TomlMapValue(array, arrayStart, offset)
            if (array.isLiteral && validate) {
              warn("Attempting to append to a statically defined array is not allowed", start)
            }
            array.add(target)
          } else {
            val into =
              if (currentArray.size > 1) {
                parent.ensure(currentArray.dropLast(1), false)
              } else {
                parent
              }
            val arrayValue =
              TomlArrayValue(
                into,
                arrayStart,
                offset,
                currentArray.lastOrNull() ?: "",
                start,
                keyEnd,
                false
              )
            into.put(currentArray.lastOrNull() ?: "", arrayValue)
            target = TomlMapValue(arrayValue, arrayStart, offset)
            arrayValue.add(target)
          }
        }
        "}" -> {
          // Escape out of parsing inline table
          break
        }
        "," -> {
          // processing inline table entries
          continue
        }
        else -> {
          if (validate) {
            if (token.startsWith("\"\"\"") || token.startsWith("'''")) {
              warn("Multi-line strings not allowed in keys", keyStart)
            }
            if (
              lastValue != null && !inInlineTable && sameLine(lastValue.getEndOffset(), keyStart)
            ) {
              warn("There must be a newline (or EOF) after a key/value pair", keyStart)
            }
          }
          val keys = parseKey(token.tomlStringSourceToString()) ?: continue
          val keyEnd = offset
          val equals = getToken()
          if (equals != "=") {
            if (validate) {
              warn("= missing after key `$token`", keyEnd)
            }
            offset = keyEnd
            continue
          }
          val valueStart = skipToNextToken(true)
          if (offset == length) {
            if (validate) {
              warn("Value missing after =", valueStart)
            }
            continue
          }
          val key = keys.lastOrNull() ?: ""
          val into =
            if (keys.size > 1) {
              target.ensure(keys.dropLast(1), validate)
            } else {
              target
            }
          val value = parseValue(into, key, keyStart, keyEnd, valueStart)
          if (validate && into.map[key] != null && into.map[key] !is TomlMapValue) {
            warn("Defining a key (`$key`) multiple times is invalid", offset)
          }
          value.setKeyRange(keyStart, keyEnd)
          into.put(key, value)
          lastValue = value
        }
      }
    }

    target.setValueRange(mapStart, offset)
    return target
  }

  private fun sameLine(offset1: Int, offset2: Int): Boolean {
    for (i in offset1 until offset2) {
      if (source[i] == '\n') {
        return false
      }
    }
    return true
  }

  private fun parseValue(
    parent: TomlMapValue,
    key: String,
    keyStart: Int,
    keyEnd: Int,
    valueStart: Int
  ): TomlValue {
    val token = getToken(breakOnDot = false)
    if (token == "{") {
      // Inline table -- https://toml.io/en/v1.0.0#inline-table
      if (validate && parent.find(key) != null) {
        warn(
          "Inline tables cannot be used to add keys or sub-tables to an already-defined table",
          valueStart
        )
      }
      val target = parent.ensure(key)
      return parseMapElements(target, inInlineTable = true)
    } else if (token == "[") {
      val arrayStart = skipToNextToken(false)
      val arrayValue = TomlArrayValue(parent, arrayStart, offset, key, keyStart, keyEnd, true)
      while (offset < length) {
        val elementStart = skipToNextToken(false)
        val elementToken = getToken()
        if (elementToken == ",") {
          continue
        } else if (elementToken == "{") {
          // Inline table -- https://toml.io/en/v1.0.0#inline-table
          val map = TomlMapValue(arrayValue, elementStart, offset)
          parseMapElements(map, inInlineTable = true)
          map.setEndOffset(offset)
          arrayValue.add(map)
          continue
        }
        val element = elementToken.removeSuffix(",")
        if (element == "]") {
          break
        }

        if (validate) {
          validateLiteralValue(element, elementStart)
        }
        val value = TomlLiteralValue(arrayValue, element, elementStart, offset)
        arrayValue.add(value)
      }
      arrayValue.setEndOffset(offset)
      return arrayValue
    } else {
      if (validate) {
        validateLiteralValue(token, valueStart)
      }
      return TomlLiteralValue(parent, token, valueStart, offset, key, keyStart, keyEnd)
    }
  }

  private fun validateLiteralValue(literal: String, valueStart: Int) {
    if (!literal.startsWith('"') && !literal.startsWith('\'')) {
      val dotIndex = literal.indexOf('.')
      if (dotIndex == -1) {
        return
      }
      if (
        dotIndex == 0 ||
          dotIndex == literal.length - 1 ||
          !literal[dotIndex - 1].isDigit() ||
          !literal[dotIndex + 1].isDigit()
      ) {
        warn(
          "The decimal point, if used, must be surrounded by at least one digit on each side",
          valueStart
        )
      }
    }
  }

  private fun parseKey(initial: String = ""): List<String>? {
    val fqn = mutableListOf<String>()
    var first = true
    if (initial.isNotBlank()) {
      if (initial == "=") {
        if (validate) {
          warn("Bare key must be non-empty", offset)
        }
        consumeToLineEnd()
        return null
      }
      fqn.add(initial)
      first = false
    }
    while (offset < length) {
      val before = offset
      val token = getToken(!first).tomlStringSourceToString()
      if (token.isEmpty() && !first) {
        if (validate) {
          warn("Key cannot be alone on a line", before)
        }
        return null
      }
      first = false
      if (token == ".") {
        continue
      } else if (token == "=" || token == "]" || token == "]]" || token == "}") {
        offset = before
        break
      }

      fqn.add(token)
    }

    return fqn
  }

  private fun consumeToLineEnd() {
    while (getToken(breakAtNewline = true, breakOnDot = false) != "") {
      // pass
    }
  }

  private fun getToken(breakAtNewline: Boolean = false, breakOnDot: Boolean = true): String {
    skipToNextToken(breakAtNewline)
    if (offset == length || breakAtNewline && source[offset] == '\n') {
      return ""
    }
    when (source[offset++]) {
      '[' -> {
        if (source.startsWith("[", offset)) {
          offset += 1
          return "[["
        }
        return "["
      }
      ']' -> {
        if (source.startsWith("]", offset)) {
          offset += 1
          return "]]"
        }
        return "]"
      }
      ',' -> return ","
      '=' -> return "="
      '{' -> return "{"
      '}' -> return "}"
      '\'' -> {
        val start = offset - 1
        if (source.startsWith("''", offset)) {
          // Multi-line literal strings
          val end = source.indexOf("'''", offset + 2)
          if (end == -1) {
            if (validate) {
              validateStringEnd(start, end)
            }
            offset = length
            return source.substring(start, length)
          }
          offset = end + 3
          // You're allowed to have up to two apostrophes immediately inside the delimiters too
          if (source.startsWith("'", offset)) {
            offset++
          }
          if (source.startsWith("'", offset)) {
            offset++
          }
          if (validate) {
            validateStringEnd(start, offset)
          }
          return source.substring(start, offset)
        }
        // Literal strings
        val end = source.indexOf('\'', offset)
        if (end == -1) {
          if (validate) {
            validateStringEnd(start, end)
          }
          offset = length
          return source.substring(start, length)
        }
        offset = end + 1
        return source.substring(start, offset)
      }
      '"' -> {
        // https://toml.io/en/v1.0.0#string
        val start = offset - 1
        if (source.startsWith("\"\"", offset)) {
          // Multi-line basic strings
          val end = source.indexOf("\"\"\"", offset + 2)
          if (end == -1) {
            if (validate) {
              validateStringEnd(start, end)
            }
            offset = length
            return source.substring(start, length)
          }
          offset = end + 3
          // You're allowed to have up to two quotes immediately inside the delimiters too
          if (source.startsWith("\"", offset)) {
            offset++
          }
          if (source.startsWith("\"", offset)) {
            offset++
          }
          if (validate) {
            validateStringEnd(start, offset)
          }
          return source.substring(start, offset)
        }
        // Basic strings
        while (offset < length) {
          val c = source[offset]
          if (c == '"') {
            ++offset
            if (validate) {
              validateStringEnd(start, offset)
            }
            return source.substring(start, offset)
          } else if (c == '\\') {
            offset++
          }
          offset++
        }
        if (validate) {
          validateStringEnd(start, -1)
        }
        return source.substring(start, offset)
      }
      '+',
      '-',
      '0',
      '1',
      '2',
      '3',
      '4',
      '5',
      '6',
      '7',
      '8',
      '9' -> { // Number or date
        val start = offset - 1
        if (
          source.startsWith("+", start) && source.startsWith("+nan", start) ||
            source.startsWith("+inf", start) ||
            source.startsWith("-", start) && source.startsWith("-nan", start) ||
            source.startsWith("-inf", start)
        ) {
          offset = start + 4
          return source.substring(start, offset)
        }
        while (offset < length) {
          val c = source[offset++]
          if (!c.isNumberOrDateLiteralChar()) {
            offset--
            break
          }
        }

        return source.substring(start, offset)
      }
      else -> {
        // some other token -- key identifier, string, number, ...
        val start = offset - 1

        if (source[start] == '.' && breakOnDot) {
          return "."
        }

        while (offset < length) {
          val c = source[offset++]
          if (c.isWhitespace() || c == ']' || c == '}' || c == '=' || c == ',') {
            offset--
            break
          } else if (c == '.') { // eat but don't include
            return source.substring(start, offset - 1)
          }
        }
        return source.substring(start, offset)
      }
    }
  }

  private fun validateStringEnd(start: Int, end: Int) {
    if (end == -1) {
      if (validate) {
        warn("Unterminated string", start)
      }
    } else if (end < length) {
      val next = source[end]
      if (!next.isWhitespace() && next != '#' && next != ',' && next != '}' && next != ']') {
        if (validate) {
          warn("Unexpected content after string terminator", end)
        }
        val lineEnd = source.indexOf('\n', start)
        offset =
          if (lineEnd != -1) {
            lineEnd + 1
          } else {
            length
          }
      }
    }
  }

  // Skip whitespace and comments up until the next token or line break
  private fun skipToNextToken(breakAtNewline: Boolean = true): Int {
    while (offset < length) {
      val c = source[offset]
      if (c == '\n' && breakAtNewline) {
        return offset
      } else if (!c.isWhitespace()) {
        if (c == '#') {
          while (offset < length && source[offset] != '\n') {
            offset++
          }
          if (!breakAtNewline && offset < length) {
            continue
          }
        }
        break
      }
      offset++
    }

    return offset
  }

  private inner class TomlDocument : LintTomlDocument {
    val root: TomlMapValue = TomlMapValue(null, startOffset = 0, endOffset = source.length)

    override fun getFile(): File {
      return file
    }

    override fun getValue(key: String): TomlValue? {
      return root.find(key)
    }

    override fun getRoot(): LintTomlMapValue {
      return root
    }

    override fun getSource(): CharSequence {
      return source
    }

    override fun getValue(key: List<String>): TomlValue? {
      return root.find(key)
    }
  }

  private open inner class TomlValue(
    val parent: TomlValue?,
    private var startOffset: Int = -1,
    private var endOffset: Int = parent?.getEndOffset() ?: -1,
    private var key: String? = null,
    private var keyStartOffset: Int = -1,
    private var keyEndOffset: Int = -1
  ) : LintTomlValue {
    override fun getDocument(): LintTomlDocument = document

    override fun getKey(): String? = key

    override fun getActualValue(): Any? = null

    override fun getText(): String {
      return source.substring(startOffset, endOffset)
    }

    override fun getStartOffset(): Int {
      return startOffset
    }

    fun setStartOffset(offset: Int) {
      this.startOffset = offset
    }

    fun setEndOffset(offset: Int) {
      this.endOffset = offset
    }

    override fun getEndOffset(): Int {
      return endOffset
    }

    override fun getKeyStartOffset(): Int {
      return keyStartOffset
    }

    override fun getKeyEndOffset(): Int {
      return keyEndOffset
    }

    override fun getFullKey(): String {
      if (parent == null) {
        return key ?: ""
      } else if (key == null) {
        val prefix = parent.getFullKey()
        if (parent is TomlArrayValue) {
          val index = parent.elements.indexOf(this)
          if (index != -1) {
            return "$prefix[$index]"
          }
        }
        return prefix
      } else {
        var prefix = parent.getFullKey()
        if (parent is TomlArrayValue) {
          val index = parent.elements.indexOf(this)
          if (index != -1) {
            prefix += "[$index]"
          }
        }
        if (key == null) {
          return prefix
        }
        if (prefix.isNotEmpty()) {
          return "$prefix.$key"
        }
        return key!!
      }
    }

    fun setValueRange(startOffset: Int, endOffset: Int) {
      this.startOffset = startOffset
      this.endOffset = endOffset
    }

    fun setKeyRange(startOffset: Int, endOffset: Int) {
      this.keyStartOffset = startOffset
      this.keyEndOffset = endOffset
    }

    override fun getLocation(): Location {
      return Location.create(file, source, startOffset, endOffset)
    }

    override fun getKeyLocation(): Location? {
      return if (key != null) {
        Location.create(file, source, keyStartOffset, keyEndOffset)
      } else {
        null
      }
    }

    protected fun updateOffsets(newChild: TomlValue) {
      if (startOffset == -1) {
        startOffset = newChild.startOffset
        endOffset = newChild.endOffset
      } else {
        if (newChild.startOffset < startOffset) {
          startOffset = newChild.startOffset
        }
        if (newChild.endOffset < endOffset) {
          endOffset = newChild.endOffset
        }
      }
    }

    override fun getFullLocation(): Location {
      return Location.create(
        file,
        source,
        if (key != null && keyStartOffset > -1 && keyStartOffset < startOffset) keyStartOffset
        else startOffset,
        endOffset
      )
    }

    override fun next(): LintTomlValue? {
      if (parent is TomlMapValue) {
        val iterator = parent.map.iterator()
        while (iterator.hasNext()) {
          val next = iterator.next()
          if (next.value == this) {
            return if (iterator.hasNext()) {
              iterator.next().value
            } else {
              null
            }
          }
        }
      } else if (parent is TomlArrayValue) {
        val iterator = parent.elements.iterator()
        while (iterator.hasNext()) {
          val next = iterator.next()
          if (next == this) {
            return if (iterator.hasNext()) {
              iterator.next()
            } else {
              null
            }
          }
        }
      }
      return null
    }
  }

  private inner class TomlMapValue(
    parent: TomlValue?,
    startOffset: Int = -1,
    endOffset: Int = parent?.getEndOffset() ?: -1,
    key: String? = null,
    keyStartOffset: Int = -1,
    keyEndOffset: Int = -1
  ) :
    TomlValue(parent, startOffset, endOffset, key, keyStartOffset, keyEndOffset), LintTomlMapValue {
    private val _map: MutableMap<String, TomlValue> =
      LinkedHashMap() // preserve order, as guaranteed by the [getMappedValue] contract.
    val map: Map<String, TomlValue> = _map

    override fun getMappedValues(): Map<String, LintTomlValue> {
      return map
    }

    override fun get(key: String): LintTomlValue? {
      if (!key.contains('.')) {
        return map[key]
      }
      return find(key)
    }

    override fun first(): LintTomlValue? {
      if (map.isEmpty()) {
        return null
      }
      return map[map.keys.first()]
    }

    override fun last(): LintTomlValue? {
      if (map.isEmpty()) {
        return null
      }
      return map[map.keys.last()]
    }

    fun put(key: String, value: TomlValue) {
      assert(value.parent == this)
      _map[key] = value
      updateOffsets(value)
    }

    fun ensure(path: List<String>, validate: Boolean): TomlMapValue {
      return ensure(path, 0, this, validate)
    }

    private fun ensure(
      path: List<String>,
      index: Int,
      parent: TomlValue?,
      validate: Boolean
    ): TomlMapValue {
      if (path.isEmpty()) {
        return this
      }
      val key = path[index]
      val match = _map[key]
      if (index == path.size - 1 && match is TomlMapValue) {
        return match
      }

      // No match, or there is already one of a different type; replace it (this would be invalid
      // TOML,
      // but we allow in-progress editing of invalid sources)
      if (validate && match != null) {
        warn("Table `$key` already specified as a value", parent?.getEndOffset() ?: 0)
      }

      val map = TomlMapValue(parent, key = key)
      _map[key] = map

      return if (index == path.size - 1) {
        map
      } else {
        map.ensure(path, index + 1, this, validate)
      }
    }

    fun ensure(path: String): TomlMapValue {
      if (path.isEmpty()) {
        return this
      }
      return ensure(path.split('.'), false)
    }

    /**
     * Returns the [TomlValue] found by traversing down the list of map values named by the keys in
     * this path string.
     */
    fun find(path: List<String>): TomlValue? {
      return find(path, 0)
    }

    private fun find(path: List<String>, index: Int): TomlValue? {
      if (path.isEmpty()) {
        return this
      }
      val key = path[index]
      val match = _map[key]
      return if (match == null || index == path.size - 1) {
        match
      } else if (match is TomlMapValue) {
        match.find(path, index + 1)
      } else {
        null
      }
    }

    /**
     * Looks up the [TomlValue] found by following the dotted path of key values. Quotes and
     * backslashes should be escaped with a backslash.
     */
    fun find(path: String): TomlValue? {
      if (path.isEmpty()) {
        return this
      } else if (path.contains('"')) {
        // Path contains quoted segments; split this up (and allow nested quotes)
        val list = mutableListOf<String>()
        var i = 0
        var start = 0
        while (i < path.length) {
          val c = path[i++]
          if (c == '.') {
            if (i > start) {
              list.add(path.substring(start, i - 1))
            }
            start = i
          } else if (c == '"') {
            val sb = StringBuilder()
            while (i < path.length) {
              when (val d = path[i++]) {
                '\\' -> {
                  if (i < path.length) {
                    sb.append(path[i++])
                  }
                }
                '"' -> {
                  if (sb.isNotEmpty()) {
                    list.add(sb.toString())
                  }
                  start = i
                  break
                }
                else -> sb.append(d)
              }
            }
          }
        }
        if (i > start) {
          list.add(path.substring(start, i))
        }
        return find(list)
      }
      return find(path.split('.'))
    }

    override fun toString(): String {
      return if (parent == null) {
        "root"
      } else {
        "${javaClass.simpleName}(${map.keys})"
      }
    }
  }

  private inner class TomlArrayValue(
    parent: TomlValue?,
    startOffset: Int = -1,
    endOffset: Int = parent?.getEndOffset() ?: -1,
    key: String? = null,
    keyStartOffset: Int = -1,
    keyEndOffset: Int = -1,
    val isLiteral: Boolean = true,
  ) :
    TomlValue(parent, startOffset, endOffset, key, keyStartOffset, keyEndOffset),
    LintTomlArrayValue {

    override fun getArrayElements(): List<LintTomlValue> {
      return _elements
    }

    fun add(value: TomlValue) {
      assert(value.parent == this)
      _elements.add(value)
      updateOffsets(value)
    }

    private val _elements: MutableList<TomlValue> = mutableListOf()
    val elements: List<TomlValue> = _elements

    override fun toString(): String {
      return "${javaClass.simpleName}(${elements.size} elements)"
    }
  }

  private inner class TomlLiteralValue(
    parent: TomlValue?,
    private val text: String,
    startOffset: Int = -1,
    endOffset: Int = parent?.getEndOffset() ?: -1,
    key: String? = null,
    keyStartOffset: Int = -1,
    keyEndOffset: Int = -1,
  ) :
    TomlValue(parent, startOffset, endOffset, key, keyStartOffset, keyEndOffset),
    LintTomlLiteralValue {
    override fun getText(): String = text

    override fun getActualValue(): Any {
      // Keywords
      when (text) {
        "" -> return text
        SdkConstants.VALUE_TRUE -> return true
        SdkConstants.VALUE_FALSE -> return false
        "inf",
        "+inf" -> return Double.POSITIVE_INFINITY
        "-inf" -> return Double.NEGATIVE_INFINITY
        "nan",
        "-nan",
        "+nan" -> return Double.NaN
      }

      val first = text[0]

      // String literals
      if (first == '"' || first == '\'') {
        return text.tomlStringSourceToString()
      }

      // Hex, Octal or Binary literals
      if (first == '0') {
        try {
          if (text.startsWith("0x")) {
            return java.lang.Long.parseLong(text.substring(2).dropDigitSeparators(), 16)
          } else if (text.startsWith("0o")) {
            return java.lang.Long.parseLong(text.substring(2).dropDigitSeparators(), 8)
          } else if (text.startsWith("0b")) {
            return java.lang.Long.parseLong(text.substring(2).dropDigitSeparators(), 2)
          }
        } catch (e: NumberFormatException) {
          return text
        }
      }

      // Dates
      if (
        (text.contains(":") || text.indexOf('-', 1) != -1) && !text.contains('e', ignoreCase = true)
      ) {
        return parseAsDate() ?: return text
      }

      // Other numbers
      try {
        if (first.isDigit() || first == '+' || first == '-') {
          val digits = text.dropDigitSeparators()
          if (text.contains('e', true)) {
            return digits.toDouble()
          } else if (text.contains('.')) {
            return digits.toDouble()
          }
          return try {
            digits.toInt()
          } catch (e: NumberFormatException) {
            digits.toLong()
          }
        }
      } catch (ignore: NumberFormatException) {}

      return text
    }

    private fun String.dropDigitSeparators(): String {
      // "For large numbers, you may use underscores between digits to enhance readability.
      // Each underscore must be surrounded by at least one digit on each side."
      if (this.contains('_')) {
        val sb = StringBuilder()
        for (i in indices) {
          val c = this[i]
          if (
            c == '_' &&
              i > 0 &&
              this[i - 1].isLetterOrDigit() &&
              i < length - 1 &&
              this[i + 1].isLetterOrDigit()
          ) {
            continue
          }
          sb.append(c)
        }
        return sb.toString()
      }
      return this
    }

    private fun parseAsDate(): Any? {
      // Try to parse the date string as various formats
      for (method in
        listOf(
          Instant::parse,
          OffsetDateTime::parse,
          ZonedDateTime::parse,
          LocalDateTime::parse,
          LocalDate::parse,
          LocalTime::parse
        )) {
        try {
          return method(text)
        } catch (ignore: Throwable) {}
      }

      return null
    }

    override fun toString(): String {
      return "${javaClass.simpleName}(key=${getKey()}, value=${getActualValue()})"
    }
  }
}

// TOML string escaping; see https://toml.io/en/v1.0.0#string
private fun unescape(s: String, skipInitialNewline: Boolean = false): String {
  val sb = StringBuilder()
  val length = s.length
  var i = 0
  if (skipInitialNewline && length > 0 && s[i] == '\n') {
    i++
  }
  while (i < length) {
    val c = s[i++]
    if (c == '\\' && i < s.length) {
      when (val next = s[i++]) {
        '\n' -> {
          // From the toml spec: "When the last non-whitespace character on a
          // line is an unescaped \, it will be trimmed along with all whitespace
          // (including newlines) up to the next non-whitespace character or
          // closing delimiter."
          while (i < length && s[i].isWhitespace()) {
            i++
          }
          continue
        }
        'n' -> sb.append('\n')
        't' -> sb.append('\t')
        'b' -> sb.append('\b')
        'f' -> sb.append('\u000C')
        'r' -> sb.append('\r')
        '"' -> sb.append('\"')
        '\\' -> sb.append('\\')
        'u' -> { // \uXXXX
          if (i <= s.length - 4) {
            try {
              val uc = Integer.parseInt(s.substring(i, i + 4), 16).toChar()
              sb.append(uc)
              i += 4
            } catch (e: NumberFormatException) {
              sb.append(next)
            }
          } else {
            sb.append(next)
          }
        }
        'U' -> { // \UXXXXXXXX
          if (i <= s.length - 8) {
            try {
              val uc = Integer.parseInt(s.substring(i, i + 8), 16).toChar()
              sb.append(uc)
              i += 8
            } catch (e: NumberFormatException) {
              sb.append(next)
            }
          } else {
            sb.append(next)
          }
        }
        else -> sb.append(next)
      }
    } else {
      sb.append(c)
    }
  }
  return sb.toString()
}

private fun Char.isNumberOrDateLiteralChar(): Boolean {
  // This is more permissive than the spec
  val c = this
  return c.isDigit() ||
    c == '.' ||
    c == '+' ||
    c == '-' || // scientific notation (or leading +/-)
    c == '_' || // digit separator
    c == 'o' ||
    c == 'x' ||
    c in 'a'..'f' ||
    c in 'A'..'F' || // octal & hex
    c == 'e' ||
    c == 'E' || // scientific notation
    c == ':' ||
    c == 'T' ||
    c == 'Z' // date
}

private fun String.tomlStringSourceToString(): String {
  val valueSource = this
  when {
    valueSource.isEmpty() -> {
      return valueSource
    }
    valueSource.startsWith("\"\"\"") -> {
      val body = valueSource.removeSurrounding("\"\"\"")
      return unescape(body, skipInitialNewline = true)
    }
    valueSource.startsWith("\"") -> {
      return unescape(valueSource.removeSurrounding("\""))
    }
    valueSource.startsWith("'''") -> {
      var body = valueSource.removeSurrounding("'''")
      if (body.startsWith("\n")) {
        // Leading newlines in """ and ''' strings should be removed
        body = body.substring(1)
      }
      return body
    }
    valueSource.startsWith("'") -> {
      return valueSource.removeSurrounding("'")
    }
    else -> return valueSource
  }
}
