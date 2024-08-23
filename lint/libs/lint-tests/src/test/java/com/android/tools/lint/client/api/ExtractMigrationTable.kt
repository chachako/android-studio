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
package com.android.tools.lint.client.api

import com.android.SdkConstants.DOT_KT
import com.android.testutils.TestUtils
import com.android.tools.lint.client.api.LintFixPerformer.Companion.skipAnnotation
import com.android.tools.lint.detector.api.ClassContext.Companion.getInternalName
import com.android.tools.lint.stripComments
import java.io.ByteArrayInputStream
import java.io.File
import java.util.jar.JarInputStream
import java.util.regex.Pattern

/**
 * Code to extract analysis API compatibility typealiases, intended to be used in the jar bytecode
 * migration
 */
fun main() {
  val map = mutableMapOf<String, String>()
  val currentSources =
    File(
      TestUtils.getWorkspaceRoot().toFile(),
      "prebuilts/tools/common/lint-psi/kotlin-compiler/kotlin-compiler-sources.jar",
    )

  JarInputStream(ByteArrayInputStream(currentSources.readBytes())).use { jis ->
    var entry = jis.nextJarEntry
    while (entry != null) {
      val name = entry.name
      if (
        name.endsWith(DOT_KT) &&
          !entry.isDirectory &&
          name.startsWith("org/jetbrains/kotlin/analysis/api/")
      ) {
        val text = String(jis.readAllBytes(), Charsets.UTF_8)
        extract(text, map)
      }
      entry = jis.nextJarEntry
    }
  }

  val entries = map.entries.sortedBy { it.key }
  for ((key, value) in entries) {
    println(
      "        \"${getInternalName(key).replace("$", "\\$")}\" -> \"${getInternalName(value).replace("$", "\\$")}\""
    )
  }
}

@Suppress("RegExpSimplifiable")
private val PACKAGE_PATTERN = Pattern.compile("""package\s+([\S&&[^;]]*)""")
@Suppress("RegExpSimplifiable")
private val IMPORT_PATTERN = Pattern.compile("""import\s+([\S&&[^;]]*)""")
private val TYPEALIAS_PATTERN = Pattern.compile("""typealias\s+(.*)\s*=\s*(.*)""")

private fun extract(text: String, map: MutableMap<String, String>) {
  val source = stripComments(text, DOT_KT)

  val matcher = PACKAGE_PATTERN.matcher(source)
  val pkg =
    if (matcher.find()) {
      matcher.group(1).trim()
    } else {
      null
    }

  val imports = mutableMapOf<String, String>()
  val importMatcher = IMPORT_PATTERN.matcher(source)
  var offset = 0
  while (importMatcher.find(offset)) {
    val fqn = importMatcher.group(1).trim()
    val name = fqn.substringAfterLast('.')
    imports[name] = fqn
    offset = importMatcher.end(1)
  }

  offset = 0
  val typeAliasMatcher = TYPEALIAS_PATTERN.matcher(source)
  while (typeAliasMatcher.find(offset)) {

    offset = typeAliasMatcher.end(2)

    val name = typeAliasMatcher.group(1).trim().substringBefore("<")
    // Known cases to skip (not used for backwards compatibility,
    // but for other type reuse within AA)
    when (name) {
      "KaScopeNameFilter" -> continue
    }

    var alias = typeAliasMatcher.group(2).trim().substringBefore("<")
    if (alias.startsWith("@")) {
      val end = skipAnnotation(alias, 0)
      if (end > 0) {
        alias = alias.substring(end).trim()
      }
    }

    val fqn =
      if (alias.contains(".")) {
        alias
      } else {
        imports[alias] ?: "$pkg.$alias"
      }

    map["$pkg.$name"] = fqn
  }
}
