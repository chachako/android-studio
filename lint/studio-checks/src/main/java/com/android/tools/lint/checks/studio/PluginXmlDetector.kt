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
package com.android.tools.lint.checks.studio

import com.android.SdkConstants.EXT_XML
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.OtherFileScanner
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.utils.visitElements
import java.util.EnumSet
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Detects issues in IDE plugin configuration files (plugin.xml). */
class PluginXmlDetector : Detector(), OtherFileScanner {

  companion object {
    val ISSUE =
      Issue.create(
        id = "PluginXmlUnresolvedClass",
        briefDescription = "Unresolved class in IDE plugin config file",
        explanation =
          """
          Plugin configuration files should register extension classes only from the \
          current module (preferred) or from a dependency of the current module. \
          Otherwise, downstream test targets may need to add spurious runtime \
          dependencies in order to avoid `ClassNotFoundException` when the plugin \
          configuration file is loaded.

          To fix this, you generally need to move the XML registration into the \
          same module as the extension class. If you suspect this is a false \
          positive error, reach out to the Android Studio Platform team.
          """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.ERROR,
        androidSpecific = false,
        implementation =
          Implementation(
            PluginXmlDetector::class.java,
            Scope.OTHER_SCOPE,
          ),
      )
  }

  override fun getApplicableFiles(): EnumSet<Scope> = Scope.RESOURCE_FILE_SCOPE

  override fun run(context: Context) {
    if (LintClient.isStudio) return // Superseded by PluginXmlDomInspection in the IDE.

    val file = context.file
    if (file.extension != EXT_XML) return

    val contents = context.client.readFile(file)
    if (!contents.contains("<idea-plugin")) return

    val document = context.client.getXmlDocument(file, contents) ?: return
    val rootElement = document.documentElement ?: return
    if (rootElement.tagName != "idea-plugin") return

    rootElement.visitElements { e ->
      visitElement(context, e)
      false // Keep going.
    }
  }

  private fun visitElement(context: Context, e: Element) {
    if (!e.hasChildNodes() && looksLikeClassRef(e.tagName)) {
      checkClassName(context, e.textContent?.trim(), e)
    }
    val attrMap = e.attributes
    for (i in 0 until attrMap.length) {
      val attr = attrMap.item(i)
      if (looksLikeClassRef(attr.nodeName)) {
        checkClassName(context, attr.nodeValue, attr)
      }
    }
  }

  private fun checkClassName(context: Context, className: String?, node: Node) {
    if (className.isNullOrEmpty()) return
    val evaluator = context.client.getUastParser(context.project).evaluator
    val psiClass = evaluator.findClass(className.replace('$', '.'))
    if (psiClass == null) {
      val shortName = className.substringAfterLast('.')
      context.report(
        ISSUE,
        context.getLocation(node),
        "Class `$shortName` not found in the current module or its dependencies",
      )
    }
  }

  private fun looksLikeClassRef(s: String?): Boolean {
    return s != null &&
      s != "classpath" &&
      CLASS_KEYWORDS.any { keyword -> s.contains(keyword, ignoreCase = true) }
  }
}

/** Keywords that suggest association with a class name. */
private val CLASS_KEYWORDS =
  arrayOf(
    "class",
    "instance",
    "implementation",
    "interface",
    "topic",
    "provider",
  )
