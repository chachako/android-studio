/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.DOT_KTS
import com.android.tools.lint.client.api.LintFixPerformer.Companion.skipAnnotation
import com.android.tools.lint.client.api.LintFixPerformer.Companion.skipCommentsAndWhitespace
import java.util.regex.Pattern

/**
 * A pair of package name and class name inferred from Java or Kotlin source code. The [source] is
 * the source code, and the [extension] is the file extension (including the leading dot) which
 * states whether this is a Kotlin source file, a Java source file, a Groovy source file, etc.
 */
open class ClassName(val source: String, val extension: String = DOT_JAVA) {
  val packageName: String?
  val className: String?
  val jvmName: String?

  init {
    // Remove comments and annotations which can occur at the top
    // of the file (before the class declaration) where words in
    // the comment or string arguments to annotations can look
    // like a class definition and confuse the regular expression lookups.
    val cleaned = cleanup(source, extension)
    packageName = getPackage(cleaned)
    className = getClassName(cleaned)
    jvmName = if (extension == DOT_JAVA) className else getJvmName(cleaned) ?: className
  }

  private fun cleanup(contents: String, extension: String): String {
    val length = contents.length
    val sb = StringBuilder(length)
    var current = 0
    val allowCommentNesting = extension == DOT_KT || extension == DOT_KTS
    while (current < length) {
      val skipped = skipCommentsAndWhitespace(contents, current, allowCommentNesting)
      if (skipped > current) {
        sb.append(' ')
        current = skipped
        if (current == length) {
          break
        }
      }
      val c = contents[current]
      if (c.isWhitespace()) {
        sb.append(c)
      } else if (
        c == '@' &&
          !contents.startsWith("@interface", current) &&
          !contents.startsWith("@file:JvmName", current) &&
          !contents.startsWith("@file:kotlin.jvm.JvmName", current)
      ) {
        val afterAnnotation = skipAnnotation(contents, current)
        sb.append(' ')
        current = afterAnnotation
        continue
      } else {
        sb.append(c)
      }
      current++
    }
    return sb.toString()
  }

  fun packageNameWithDefault() = packageName ?: ""

  fun relativePath(extension: String = this.extension): String {
    val name = (className ?: jvmName ?: "test") + extension
    return when {
      packageName != null -> packageName.replace('.', '/') + '/' + name
      else -> name
    }
  }
}

@Suppress("RegExpSimplifiable") // the proposed simplification does not work; tests fail
private val PACKAGE_PATTERN = Pattern.compile("""package\s+([\S&&[^;]]*)""")

private val CLASS_PATTERN =
  Pattern.compile(
    """(\bclass\b|\binterface\b|\benum class\b|\benum\b|\bobject\b|\brecord\b)+?\s*([^\s:(]+)""",
    Pattern.MULTILINE,
  )

private val JVM_NAME_PATTERN =
  Pattern.compile("""@file:(kotlin.jvm.)?JvmName\(\"(.+)\"\)""", Pattern.MULTILINE)

private fun getJvmName(source: String): String? {
  val matcher = JVM_NAME_PATTERN.matcher(source)
  return if (matcher.find()) {
    matcher.group(2).trim()
  } else {
    null
  }
}

private fun getPackage(source: String): String? {
  val matcher = PACKAGE_PATTERN.matcher(source)
  return if (matcher.find()) {
    matcher.group(1).trim()
  } else {
    null
  }
}

private fun getClassName(source: String): String? {
  val matcher = CLASS_PATTERN.matcher(source.replace('\n', ' '))
  var start = 0
  while (matcher.find(start)) {
    val cls = matcher.group(2)
    val groupStart = matcher.start(1)

    // Make sure this "class" reference isn't part of an annotation on the class
    // referencing a class literal -- Foo.class, or in Kotlin, Foo::class.java)
    if (groupStart == 0 || source[groupStart - 1] != '.' && source[groupStart - 1] != ':') {
      val trimmed = cls.trim()
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
