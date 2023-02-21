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

import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Severity
import java.io.File

/** Lint parser for TOML files. */
open class LintTomlParser {
  fun parse(
    file: File,
    contents: CharSequence,
    onProblem: ((Severity, Location, String) -> Unit)? = null
  ): LintTomlDocument {
    return DefaultLintTomlParser(file, contents, onProblem).getDocument()
  }
}

/** A parsed TOML document. */
interface LintTomlDocument {
  /**
   * Returns the underlying file this document is based on.
   *
   * Note that the file contents may not match the document sources; when running in the IDE for
   * example, we'll be parsing the current editor contents, not the most recently saved document. As
   * always, use [LintClient.readFile] to access file content; files are only treated as path
   * references.
   */
  fun getFile(): File
  /** The root map containing the top level key value pairs. */
  fun getRoot(): LintTomlMapValue

  /** The character source the document was parsed from. */
  fun getSource(): CharSequence

  /** Looks up the corresponding value by dotted path. */
  fun getValue(key: List<String>): LintTomlValue?

  /**
   * Looks up the corresponding value by a path; this is just a convenience function for looking up
   * using a flattened string.
   */
  fun getValue(key: String): LintTomlValue?

  /** Runs the given [visitor] on this document. */
  fun accept(context: TomlContext, visitor: TomlScanner) {
    visitor.visitTomlDocument(context, this)
  }
}

/** Represents a value in a TOML value, which may or may not have an associated key. */
interface LintTomlValue {
  /** The document this value belongs to. */
  fun getDocument(): LintTomlDocument

  /**
   * The associated key, if applicable. (This is only the local key in the parent map, not the full
   * key; see [getFullKey] for that.
   */
  fun getKey(): String? = null

  /**
   * Returns the actual value for this value-token.
   *
   * For example, for the TOML file
   *
   *     delay = 1_000
   *
   * this will return java.lang.Integer(1000). Similarly, if called on a String value, you get the
   * actual String (without the surrounding quotes, with escapes applied, etc.)
   *
   * If it cannot produce the correct value, it will return the source text of the value instead.
   * Note also that this method isn't performing full TOML validation, so while for example TOML
   * does not allow leading zeroes in integers, lint will still return the intended integer.
   * Generally, the idea is for lint to try to be able to run on partially broken source since the
   * user may be editing it, and we want to be able to offer guidance, sometimes to help the user
   * make the code valid.
   */
  fun getActualValue(): Any? = null

  /** Returns the source text of this value. */
  fun getText(): String

  /** Gets the next sibling value in the parent map or array */
  fun next(): LintTomlValue?

  /**
   * Returns the start offset of the value. Note that for a key=value pair, this is for the value
   * portion.
   */
  fun getStartOffset(): Int

  /** Returns the end offset of the value. */
  fun getEndOffset(): Int

  /** Returns the start offset of the key for this value, or -1 if it does not have a key. */
  fun getKeyStartOffset(): Int

  /** Returns the end offset of the key for this value, or -1 if it does not have a key. */
  fun getKeyEndOffset(): Int

  /** Returns the location of this value. */
  fun getLocation(): Location

  /** Returns the location of the key of this value, if any. */
  fun getKeyLocation(): Location?

  /** Returns the location of this value, including its key (if applicable) */
  fun getFullLocation(): Location

  /** *Optional* operation to return the fully qualified path of the map value */
  fun getFullKey(): String?
}

/** Represents an array value. */
interface LintTomlArrayValue : LintTomlValue {
  /** The elements in the array. */
  fun getArrayElements(): List<LintTomlValue>
}

/** Represents a map of values. The map iteration order matches the TOML document. */
interface LintTomlMapValue : LintTomlValue {
  /** Returns the values in the map */
  fun getMappedValues(): Map<String, LintTomlValue>
  /** Gets the named value (which is allowed to be dotted path */
  operator fun get(key: String): LintTomlValue?
  /** Gets the first value in the map */
  fun first(): LintTomlValue?
  /** Gets the last value in the map */
  fun last(): LintTomlValue?
}

/** Represents a literal value. */
interface LintTomlLiteralValue : LintTomlValue {
  /**
   * Maps from a TOML source literal (such as `"foo"`) to the corresponding value (such as new
   * String("foo"))
   */
  override fun getActualValue(): Any?
}
