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
package com.android.tools.lint.checks

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.parseFirst
import com.android.tools.lint.detector.api.JavaContext
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.javadoc.PsiInlineDocTag
import java.io.File
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod
import org.junit.rules.TemporaryFolder

/**
 * This code helps update the big when statement in ServiceCastDetector to figure out the correct
 * set of valid input fields for Context.getSystemService, and the corresponding valid classes the
 * system service can be cast to for the given name.
 *
 * In theory, this should be easy; the `@ServiceName` typedef in Context is supposed to contain the
 * set of valid names. And so should the javadoc on that method. But in practice, these are not
 * always updated correctly. Therefore, we collect information from a number of sources, and then we
 * cross-reference these.
 *
 * This function needs to be pointed both to the `Context.java` class in the framework (for the
 * correct release branch that the data should reflect, e.g. the most recently released SDK branch),
 * as well as the ServiceCastDetector source file itself.
 */
fun main() {
  val serviceCast =
    "tools/base/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks/ServiceCastDetector.kt"
  val viewDetectorFile = resolveWorkspacePath(serviceCast).toFile()
  val contextFile =
    File(
      "${System.getenv("ANDROID_BUILD_TOP")}/frameworks/base/core/java/android/content/Context.java"
    )
  val extractor = ServiceCastDataGenerator(viewDetectorFile, contextFile)
  extractor.analyze()
}

class ServiceCastDataGenerator(private val viewDetectorFile: File, contextSourceFile: File) {
  init {
    if (!viewDetectorFile.isFile) error("$viewDetectorFile does not exist")
    if (!contextSourceFile.isFile) error("$contextSourceFile does not exist")
  }

  private val context: JavaContext

  private val currentMap = mutableMapOf<String, String>()
  private val docFields = mutableMapOf<String, String>()
  private val extractedMap = mutableMapOf<String, String>()
  private val seeMap = mutableMapOf<String, String>()
  private val hiddenTypedefList = mutableListOf<String>()
  private val typedefList = mutableListOf<String>()

  init {
    val temporaryFolder = TemporaryFolder()
    temporaryFolder.create()

    val (context, contextDisposable) =
      parseFirst(
        temporaryFolder = temporaryFolder,
        testFiles = arrayOf(java(contextSourceFile.readText())),
      )
    this.context = context

    extractCurrentCasts()
    extractFromDoc()
    extractFromTypeDef()
    extractFromFields()

    temporaryFolder.delete()
    Disposer.dispose(contextDisposable)
  }

  fun analyze() {
    val allNames =
      (currentMap.keys + docFields.keys + extractedMap.keys + typedefList + seeMap.keys)
        .toSortedSet()

    val description =
      """
      There are a number of different clues to the names which are supported by getSystemService.
      Unfortunately when system service are added, not all the expected places are updated correctly
      (e.g. javadoc, the ServiceName typedef, etc), so we're collecting data from different sources.

      Legend:
      Today: Service name already in ServiceCastDetector today
      Field: There is a field in Context which looks like a service name
      Typedef: The @ServiceName typedef explicitly lists this field
      MethodDoc: The name is mentioned in the dt/dd definition javadoc for getSystemService
      See: The name is mentioned in the @see pairs at the bottom of the javadoc for getSystemService
      """
        .trimIndent()
    println(description)

    val format = "%-35s %-10s %-10s %-10s %-10s %-10s"
    val header =
      String.format(format, "Key", "Today", "Field", "MethodDoc", "See", "Typedef").trimEnd()
    val separator = "-".repeat(header.length)
    println(header)
    println(separator)
    fun Boolean.answer(): String = if (this) "X" else " "
    for (name in allNames) {
      val today = currentMap.containsKey(name).answer()
      val field = docFields.containsKey(name).answer()
      val methodDoc = extractedMap.containsKey(name).answer()
      val see = seeMap.containsKey(name).answer()
      val typedef = typedefList.contains(name).answer()
      println(String.format(format, name, today, field, methodDoc, see, typedef).trimEnd())
    }
    println(separator)

    // The new names are the ones found as fields in the class (mentioning the
    // system service), or in the javadoc for getSystemService (either as a
    // definition list or as a @see), but filtered by actual fields appearing
    // in the jar
    val newNames =
      (docFields.keys + extractedMap.keys + typedefList + seeMap.keys).toSortedSet().filter {
        docFields.contains(it)
      }

    // Make sure they're all covered
    val currentKeys = currentMap.keys.sorted()
    for (currentKey in currentKeys) {
      if (!newNames.contains(currentKey)) {
        println(
          "Warning: Key `$currentKey` is in our current service cast map but is missing " +
            "from the newly extracted data; has it been deleted?"
        )
      }
    }

    // Check extracted service map consistency
    val serviceMap = LinkedHashMap<String, String>()
    for (name in newNames) {
      val values = mutableSetOf<String>()
      docFields[name]?.let { values.add(it) }
      extractedMap[name]?.let { values.add(it) }
      seeMap[name]?.let { values.add(it) }
      currentMap[name]?.let { values.add(it) }

      if (name == "CLIPBOARD_SERVICE") {
        // We have registrations for android.content.ClipboardManager and
        // android.text.ClipboardManager
        serviceMap[name] = "android.text.ClipboardManager"
      } else if (name == "WALLPAPER_SERVICE") {
        serviceMap[name] = "android.app.WallpaperManager"
      } else if (values.size != 1) {
        println(
          "Warning 2: Unexpected extracted service map for key `$name`: Expected exactly one value, but found `$values`"
        )
      } else {
        serviceMap[name] = values.single()
      }
    }

    for ((name, service) in serviceMap) {
      if (!currentMap.contains(name)) {
        println("Warning: New service to be handled: `$name` -> `$service`")
      }
    }

    val switchString = StringBuilder()
    switchString.append("when (value) {\n")
    for ((name, service) in serviceMap) {
      if (name == "CLIPBOARD_SERVICE") {
        switchString.append(
          "        // also allow @Deprecated android.content.ClipboardManager, see isClipboard\n"
        )
      }
      switchString.append("        \"$name\" -> \"$service\"\n")
    }
    switchString.append("        else -> null\n")
    switchString.append("      }")

    // Replace existing
    val t = viewDetectorFile.readText()
    val index = t.indexOf("when (value) {")
    val end = t.indexOf("}", index + 1)
    if (index == -1 || end == -1) error("Couldn't find existing switch")
    val replaced = t.substring(0, index) + switchString.toString() + t.substring(end + 1)
    viewDetectorFile.writeText(replaced)
    println("Updated the switch table in $viewDetectorFile")

    println(
      "\n\nAdd the following names to the @ServiceName typedef in the platform Context.java class:"
    )
    for ((name, _) in serviceMap) {
      if (!typedefList.contains(name) && !hiddenTypedefList.contains(name)) {
        println("            $name,")
      }
    }

    println(
      "\n\nUnhide the following fields from the existing @ServiceName in the platform Context.java class:"
    )
    for ((name, _) in serviceMap) {
      if (!typedefList.contains(name) && hiddenTypedefList.contains(name)) {
        println("            //@hide: $name,")
      }
    }
  }

  /** Extracts the current key=>value pairs defined in ServiceCastDetector */
  private fun extractCurrentCasts() {
    if (!viewDetectorFile.isFile()) {
      error("Can't find current source file implementation")
    }
    val source = viewDetectorFile.readText()
    val index = source.indexOf("fun getExpectedType(")
    if (index == -1) {
      error("Couldn't find the `fun getExpectedType(value: String?): String? {` method")
    }
    var prev = ""
    val lines = mutableListOf<String>()
    for (line in source.substring(index).lines()) {
      val trimmed = line.trim()
      if (line.contains(" -> ") && !line.startsWith("else")) {
        lines.add(trimmed)
      } else if (prev.endsWith(" ->")) {
        lines.add("$prev $trimmed")
      }
      prev = line
    }
    for (line in lines) {
      val splits = line.split("->")
      val key = splits[0].trim().substringAfter('"').substringBefore('"')
      val value = splits[1].trim().substringAfter('"').substringBefore('"')
      currentMap[key] = value
    }
  }

  /**
   * Extracts the key=>value pairs from the javadoc on the getSystemService method. It defines it in
   * two ways: first as a `<dt>`/`<dd>` definition list, and then also as pairs of `@see` tags.
   */
  private fun extractFromDoc() {
    // Find the javadoc for the getSystemService(String) method in Context and extract its doc.
    val getContextMethod =
      context.uastFile!!.classes[0].uastDeclarations.single {
        it is UMethod &&
          it.name == "getSystemService" &&
          it.uastParameters.size == 1 &&
          it.uastParameters[0].type.canonicalText == "java.lang.String"
      }
    val doc = (getContextMethod.sourcePsi as PsiMethod).docComment
    val s = doc?.text ?: error("Couldn't find method doc")
    var i = 0
    val n = s.length
    val map = extractedMap

    while (i < n) {
      val dt = s.indexOf("<dt>", i)
      if (dt == -1) {
        break
      }

      // Example:
      // *  <dt> {@link #WIFI_P2P_SERVICE} ("wifip2p")
      // *  <dd> A {@link android.net.wifi.p2p.WifiP2pManager WifiP2pManager} for management of
      var dtStart = s.indexOf("{@link #", dt)
      if (dtStart == -1) error("Couldn't find <dt> link")
      dtStart += "{@link #".length
      val dtEnd = s.indexOfAny(charArrayOf('}', ' '), dtStart)
      if (dtEnd == -1) error("Couldn't find end of <dt> link")

      val dd = s.indexOf("<dd>", dtEnd + 1)
      if (dd == -1) {
        error("Missing <dd> for <dt>")
      }

      var ddStart = s.indexOf("{@link ", dd)
      if (ddStart == -1) error("Couldn't find <dd> link")
      ddStart += "{@link ".length
      val ddEnd = s.indexOfAny(charArrayOf('}', ' '), ddStart)
      if (ddEnd == -1) error("Couldn't find end of <dt> link")
      val key = s.substring(dtStart, dtEnd)
      val value = s.substring(ddStart, ddEnd)

      map[key] = value
      i = ddEnd
    }

    // Check @see maps
    val lines =
      s.lines()
        .map { it.trim() }
        .filter { it.startsWith("* @see ") }
        .map { it.removePrefix("* @see ").trim() }
    var prev = ""
    for (line in lines) {
      if (prev.startsWith("#") && !line.startsWith("#")) {
        seeMap[prev.removePrefix("#")] = line
      }
      prev = line
    }
  }

  /**
   * Extract the allowed constant names from the `@ServiceName` typedef definition in the Context
   * class.
   */
  private fun extractFromTypeDef() {
    val serviceName = context.uastFile!!.classes[0].innerClasses.single { it.name == "ServiceName" }
    val typeDefAnnotation =
      serviceName.uAnnotations.single { it.qualifiedName?.endsWith("StringDef") == true }
    val typeDef = typeDefAnnotation.sourcePsi?.text ?: error("Missing annotation")
    val names = typedefList
    val lines = typeDef.lines()
    for (line in lines) {
      val trimmed = line.trim()
      if (trimmed.isEmpty() || !trimmed[0].isJavaIdentifierStart() || !trimmed.endsWith(",")) {
        if (trimmed.startsWith("//@hide: ")) {
          hiddenTypedefList.add(trimmed.removePrefix("//@hide: ").removeSuffix(","))
        }
        continue
      }
      val name = trimmed.removeSuffix(",").trim()
      if (name.any { it != '_' && !it.isJavaIdentifierPart() }) {
        continue
      }
      names.add(name)
    }
  }

  /**
   * Extracts the key=>value pairs from all the String field declaration fields in Context.java
   * where the comment talks about this string being a key for getSystemService.
   */
  private fun extractFromFields() {
    val imports = context.uastFile!!.imports
    for (field in context.uastFile!!.classes[0].uastDeclarations.filterIsInstance<UField>()) {
      val psiField = field.javaPsi as? PsiField
      val doc: PsiDocComment = psiField?.docComment ?: continue
      val docText = doc.text ?: continue
      if (docText.contains("@hide")) {
        continue
      }
      if (
        !docText.contains("@see #getSystemService(String)") &&
          !docText.contains("Use with {@link #getSystemService(String)}")
      ) {
        continue
      }
      val name = field.name

      val inline = doc.descriptionElements.filterIsInstance<PsiInlineDocTag>()

      var serviceClass: String? = null
      for (tag in inline) {
        if (tag.name == "link") {
          val text = tag.text
          val value =
            text
              .replace("*", "")
              .substringAfter("@link")
              .trimStart()
              .substringBeforeLast("}")
              .substringBefore(" ")
          if (value.startsWith("#getSystemService")) {
            // Usual intro description ("Use with {@link #getSystemService(String)} to retrieve a
            // ...")
          } else {
            if (serviceClass == null) {
              serviceClass = value
              if (!value.contains(".")) {
                val suffix = ".$serviceClass"
                val imported =
                  imports.firstOrNull {
                    val imp = it.importReference
                    imp?.sourcePsi?.text?.endsWith(suffix) == true
                  }
                if (imported != null) {
                  serviceClass =
                    imported.importReference?.sourcePsi?.text
                      ?: error("Unexpectedly couldn't get fully qualified name")
                } else {
                  error("Unable to resolve the import for $value for field $name")
                }
              }
            }
          }
        }
      }

      if (serviceClass == null) {
        val text =
          docText
            .removePrefix("/**")
            .removeSuffix("*/")
            .lines()
            .joinToString(" ") { it.trim().removePrefix("* ") }
            .trim()
        val prefix = "Use with {@link #getSystemService(String)} to retrieve a "
        if (text.startsWith(prefix)) {
          var e = prefix.length
          while (e < text.length) {
            if (!text[e].isJavaIdentifierPart() && text[e] != '.') {
              break
            }
            e++
          }
          serviceClass = text.substring(prefix.length, e)
        }
      }

      if (serviceClass != null) {
        docFields[name] = serviceClass
      }
    }
  }
}
