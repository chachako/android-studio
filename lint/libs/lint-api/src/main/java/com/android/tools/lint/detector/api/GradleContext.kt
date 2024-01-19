/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.lint.client.api.GradleVisitor
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.LintTomlValue
import com.intellij.psi.CommonClassNames
import java.io.File
import java.util.regex.Pattern
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UastBinaryOperator

/** Context for analyzing a particular file. */
open class GradleContext
constructor(
  /** Visitor to use to analyze the file. */
  val gradleVisitor: GradleVisitor,

  /** the driver running through the checks */
  driver: LintDriver,

  /** the project to run lint on which contains the given file */
  project: Project,

  /**
   * The main project if this project is a library project, or null if this is not a library
   * project. The main project is the root project of all library projects, not necessarily the
   * directly including project.
   */
  main: Project?,

  /** the file to be analyzed */
  file: File,
) : Context(driver, project, main, file) {

  /** Returns a location for the given range. */
  fun getLocation(cookie: Any): Location = gradleVisitor.createLocation(this, cookie)

  fun isSuppressedWithComment(cookie: Any, issue: Issue): Boolean {
    val startOffset = gradleVisitor.getStartOffset(this, cookie)
    return startOffset >= 0 && isSuppressedWithComment(startOffset, issue)
  }

  @Deprecated(message = "unused", replaceWith = ReplaceWith(expression = "cookie"))
  fun getPropertyKeyCookie(cookie: Any): Any = cookie

  @Deprecated(message = "unused", replaceWith = ReplaceWith(expression = "cookie"))
  fun getPropertyPairCookie(cookie: Any): Any = cookie

  /**
   * Looks up the associated TOML value known to Gradle. This is typically used with
   * gradle/libs.versions.toml from the root directory to specify collections of libraries.
   *
   * The [key] is the (potentially dotted) path of keys into tables. This will return a pair of
   * value and [Location] for that key-value pair, or null if not found. If [source] is false, the
   * value returned will be the actual value of the key (which for example will resolve references,
   * and will return for example a Boolean if the value is true); if not (which is the default), it
   * will return the source text of the TOML value.
   */
  open fun getTomlValue(key: String, source: Boolean = true): LintTomlValue? = null

  open fun getTomlValue(key: List<String>, source: Boolean = true): LintTomlValue? = null

  /**
   * Reports an issue applicable to a given source location. The source location is used as the
   * scope to check for suppress lint annotations.
   *
   * @param issue the issue to report
   * @param cookie the node scope the error applies to. The lint infrastructure will check whether
   *   there are suppress annotations on this node (or its enclosing nodes) and if so suppress the
   *   warning without involving the client.
   * @param location the location of the issue, or null if not known
   * @param message the message for this warning
   * @param fix optional data to pass to the IDE for use by a quickfix.
   */
  fun report(issue: Issue, cookie: Any, location: Location, message: String, fix: LintFix? = null) {
    val incident = Incident(issue, cookie, location, message, fix)
    driver.client.report(this, incident)
  }

  companion object {
    fun getStringLiteralValue(value: String, valueCookie: Any): String? {
      if (value.length > 2) {
        if (value.startsWith('\'') && value.endsWith('\'')) { // Groovy strings
          return value.removeSurrounding("'")
        }
        if (value.startsWith('"')) {
          if (valueCookie is UExpression) { // KTS
            return getKotlinStringLiteralValue(valueCookie)
          }
          if (value.endsWith('"')) {
            return value.removeSurrounding("\"")
          }
        }
        if (
          valueCookie is UPolyadicExpression &&
            valueCookie.operator == UastBinaryOperator.PLUS &&
            valueCookie.getExpressionType()?.canonicalText == CommonClassNames.JAVA_LANG_STRING
        ) {
          return getKotlinStringLiteralValue(valueCookie)
        }
      }

      return null
    }

    /**
     * Given a Kotlin AST node which represents a string, returns the string value as best it can,
     * and tries to format it as substitution strings (e.g. `"group:artifact:" + version` is
     * returned as `"group:artifact:${version}"`. It will also change raw strings into templated
     * strings.
     */
    private fun getKotlinStringLiteralValue(expression: UExpression): String {
      val sourcePsi =
        expression.sourcePsi ?: return expression.asSourceString().removeSurrounding("\"")
      val text = sourcePsi.text
      if (expression is ULiteralExpression) {
        val value = expression.value
        if (value is String) {
          return value
        }
      } else if (
        expression is UPolyadicExpression && expression.operator == UastBinaryOperator.PLUS
      ) {
        val sb = StringBuilder()
        for (part in expression.operands) {
          appendIntoKotlinString(sb, part)
        }
        return sb.toString()
      }

      return text
    }

    private fun appendIntoKotlinString(sb: StringBuilder, expression: UExpression) {
      if (expression is ULiteralExpression && expression.value is String) {
        sb.append(expression.value as String)
      } else if (
        expression is UPolyadicExpression && expression.operator == UastBinaryOperator.PLUS
      ) {
        for (part in expression.operands) {
          appendIntoKotlinString(sb, part)
        }
      } else if (expression is UParenthesizedExpression) {
        appendIntoKotlinString(sb, expression.expression)
      } else {
        val constant = ConstantEvaluator.evaluateString(null, expression, false)
        if (constant != null) {
          sb.append(constant)
        } else {
          sb.append("\${")
          sb.append(getKotlinStringLiteralValue(expression))
          sb.append("}")
        }
      }
    }

    fun getIntLiteralValue(value: String, defaultValue: Int): Int {
      return try {
        Integer.parseInt(value)
      } catch (e: NumberFormatException) {
        defaultValue
      }
    }

    private val DIGITS = Pattern.compile("\\d+")

    fun isNonNegativeInteger(token: String): Boolean {
      return DIGITS.matcher(token).matches()
    }

    fun isStringLiteral(token: String): Boolean {
      return token.startsWith("\"") && token.endsWith("\"") ||
        token.startsWith("'") && token.endsWith("'")
    }
  }
}
