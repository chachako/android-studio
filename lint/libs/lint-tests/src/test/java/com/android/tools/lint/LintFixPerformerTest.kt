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

package com.android.tools.lint

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.AUTO_URI
import com.android.tools.lint.checks.LintDetectorDetector
import com.android.tools.lint.checks.infrastructure.TestLintClient
import com.android.tools.lint.checks.infrastructure.dos2unix
import com.android.tools.lint.client.api.LintFixPerformer
import com.android.tools.lint.client.api.LintFixPerformer.Companion.canAutoFix
import com.android.tools.lint.client.api.LintFixPerformer.Companion.compareAttributeNames
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.utils.toSystemLineSeparator
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LintFixPerformerTest {
  @get:Rule var temporaryFolder = TemporaryFolder()

  private fun fix() = LintFix.create()

  fun check(
    file: File,
    source: String,
    vararg fixes: LintFix,
    expected: String,
    expectedOutput: String? = null,
    expectedFailure: String? = null,
    includeMarkers: Boolean = false,
    updateImports: Boolean = true,
    shortenAll: Boolean = includeMarkers,
    location: Location? = null,
    requireAutoFixable: Boolean = true,
  ) {
    val client = TestLintClient()
    if (requireAutoFixable) {
      for (fix in fixes) {
        assertTrue(canAutoFix(fix))
      }
    }
    var after: String = source
    var output = ""
    val printStatistics = expectedOutput != null
    val performer =
      object :
        LintCliFixPerformer(
          client,
          printStatistics,
          requireAutoFixable = requireAutoFixable,
          includeMarkers = includeMarkers,
          updateImports = updateImports,
          shortenAll = shortenAll
        ) {
        override fun writeFile(file: File, contents: String) {
          after = contents
        }

        override fun printStatistics(
          writer: PrintWriter,
          editMap: MutableMap<String, Int>,
          appliedEditCount: Int,
          editedFileCount: Int
        ) {
          val stringWriter = StringWriter()
          val collector = PrintWriter(stringWriter)
          super.printStatistics(collector, editMap, appliedEditCount, editedFileCount)
          output = stringWriter.toString()
        }
      }
    val testIncident =
      Incident().apply {
        issue =
          Issue.create(
            "_FixPerformerTestIssue",
            "Sample",
            "Sample",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            Implementation(LintDetectorDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
          )
        if (location != null) {
          this.location = location
        }
      }
    try {
      performer.fix(testIncident, fixes.toList())
    } catch (e: Throwable) {
      if (expectedFailure != null) {
        assertEquals(
          expectedFailure.trimIndent().trim(),
          e.message?.replace(file.path, file.name)?.replace(file.path.dos2unix(), file.name)
        )
      } else {
        throw e
      }
    }

    assertEquals(expected.trimIndent().trim(), after.trim())
    if (expectedOutput != null) {
      assertEquals(expectedOutput.trimIndent().trim().toSystemLineSeparator(), output.trim())
    }
  }

  private fun getFileAndRange(
    fileName: String,
    source: String,
    startOffset: Int = 0,
    endOffset: Int = source.length
  ): Pair<File, Location> {
    val file = temporaryFolder.newFile(fileName)
    file.writeText(source)
    val range = Location.create(file, source, startOffset, endOffset)
    return Pair(file, range)
  }

  @Test
  fun testSingleReplace() {
    val source =
      """
      First line.
      Second line.
      Third line.
      """
        .trimIndent()
    val (file, range) = getFileAndRange("test.txt", source)
    val fix = fix().replace().text("Second").range(range).with("2nd").autoFix().build()
    check(
      file,
      source,
      fix,
      expected = """
            First line.
            2nd line.
            Third line.""",
      expectedOutput = "Applied 1 edits across 1 files for this fix: Replace with 2nd"
    )
  }

  @Test
  fun testRepeatedReplaceText() {
    val source =
      """
      First line.
      Second line.
      Third line.
      """
        .trimIndent()
    val (file, range) = getFileAndRange("test.txt", source)
    val fix =
      fix().replace().text("line").range(range).with("sentence").repeatedly().autoFix().build()
    check(
      file,
      source,
      fix,
      expected =
        """
        First sentence.
        Second sentence.
        Third sentence.
        """,
      expectedOutput = "Applied 3 edits across 1 files for this fix: Replace with sentence"
    )
  }

  @Test
  fun testRepeatedReplacePattern() {
    val source =
      """
      First line.
      Second line.
      Third line.
      """
        .trimIndent()
    val (file, range) = getFileAndRange("test.txt", source)
    val fix =
      fix()
        .replace()
        .pattern("(\\b(.+) (line)(.))")
        .range(range)
        .with("\\k<3>: \\k<2>!")
        .repeatedly()
        .autoFix()
        .build()
    check(
      file,
      source,
      fix,
      expected = """
        line: First!
        line: Second!
        line: Third!
        """,
      expectedOutput = "Applied 3 edits across 1 files for this fix: Replace with \\k<3>: \\k<2>!"
    )
  }

  @Test
  fun testImports() {
    val source =
      """
      @file:Suppress("UsePropertyAccessSyntax")
      package test.pkg;

      import android.app.Activity; /* Comment here */
      import static com.android.tools.SdkUtils.method; // line suffix

      class Test {
      }
      """
        .trimIndent()

    val (file, range) = getFileAndRange("Test.java", source)
    val fix =
      fix()
        .replace()
        .text("Test")
        .range(range)
        .with("MainActivity")
        .imports("java.util.List", "java.io.File", "absolutely.First", "zzz.definitely.Last")
        .autoFix()
        .build()
    check(
      file,
      source,
      fix,
      expected =
        """
        @file:Suppress("UsePropertyAccessSyntax")
        package test.pkg;

        import absolutely.First;
        import android.app.Activity; /* Comment here */
        import static com.android.tools.SdkUtils.method; // line suffix
        import java.io.File;
        import java.util.List;
        import zzz.definitely.Last;

        class MainActivity {
        }
        """,
      expectedOutput = "Applied 5 edits across 1 files for this fix: Replace with MainActivity",
    )
  }

  @Test
  fun testKeepStaticImportsSorted() {
    val source =
      """
      package test.pkg;

      import static com.android.tools.SdkUtils.method;
      import static java.util.Collections.size;
      import android.app.Activity;

      class Test {
      }
      """
        .trimIndent()

    val (file, range) = getFileAndRange("Test.java", source)
    val fix =
      fix()
        .replace()
        .text("Test")
        .range(range)
        .with("MainActivity")
        .imports("java.util.List", "java.io.File.delete", "android.app.Activity.isDestroyed")
        .autoFix()
        .build()
    check(
      file,
      source,
      fix,
      expected =
        """
        package test.pkg;

        import static android.app.Activity.isDestroyed;
        import static com.android.tools.SdkUtils.method;
        import static java.io.File.delete;
        import static java.util.Collections.size;
        import android.app.Activity;
        import java.util.List;

        class MainActivity {
        }
        """,
      expectedOutput = "Applied 4 edits across 1 files for this fix: Replace with MainActivity",
    )
  }

  @Test
  fun testInsertFirstImport() {
    val source =
      """
      class Test {
      }
      """
        .trimIndent()

    val (file, range) = getFileAndRange("Test.java", source)
    val fix =
      fix()
        .replace()
        .text("Test")
        .range(range)
        .with("MainActivity")
        .imports("java.util.List")
        .autoFix()
        .build()
    check(
      file,
      source,
      fix,
      expected =
        """
        import java.util.List;
        class MainActivity {
        }
        """,
      expectedOutput = "Applied 2 edits across 1 files for this fix: Replace with MainActivity",
    )
  }

  @Test
  fun testInsertFirstImport2() {
    val source =
      """
      /*
      package test.pkg;
      import java.util.List;
      */
      package test.pkg; // Line comment
      class Test {
      }
      """
        .trimIndent()

    val (file, range) = getFileAndRange("Test.java", source)
    val fix =
      fix()
        .replace()
        .text("Test")
        .range(range)
        .with("MainActivity")
        .imports("java.util.List")
        .autoFix()
        .build()
    check(
      file,
      source,
      fix,
      expected =
        """
        /*
        package test.pkg;
        import java.util.List;
        */
        package test.pkg; // Line comment
        import java.util.List;
        class MainActivity {
        }
        """,
      expectedOutput = "Applied 2 edits across 1 files for this fix: Replace with MainActivity",
    )
  }

  @Test
  fun testInsertFirstImport3() {
    val source =
      """
      // Comment
      @file:Suppress("UsePropertyAccessSyntax", "UNUSED_VARIABLE", "unused", "UNUSED_PARAMETER", "DEPRECATION", "(")
      /**
      Import like this:
      package test.pkg;
      import java.util.List;
      */
      package test.pkg // Line comment
      class Test {
      }
      """
        .trimIndent()

    val (file, range) = getFileAndRange("Test.kt", source)
    val fix =
      fix()
        .replace()
        .text("Test")
        .range(range)
        .with("MainActivity")
        .imports("java.util.List.delete")
        .autoFix()
        .build()
    check(
      file,
      source,
      fix,
      expected =
        """
        // Comment
        @file:Suppress("UsePropertyAccessSyntax", "UNUSED_VARIABLE", "unused", "UNUSED_PARAMETER", "DEPRECATION", "(")
        /**
        Import like this:
        package test.pkg;
        import java.util.List;
        */
        package test.pkg // Line comment
        import java.util.List.delete
        class MainActivity {
        }
        """,
      expectedOutput = "Applied 2 edits across 1 files for this fix: Replace with MainActivity",
    )
  }

  @Test
  fun testShorten() {
    // Make sure we don't shorten symbols from the package or wildcard imports if
    // there are other explicit imports of the same symbol name.
    val source =
      """
      package test.pkg;

      import android.app.Application;
      import static com.android.tools.SdkUtils.method;
      import java.util.*;
      import java.util.concurrent.ExecutionException;
      import java.util.concurrent.Future;

      class Test {
      }
      """
        .trimIndent()

    val (file, range) = getFileAndRange("Test.java", source)
    val fix =
      fix()
        .replace()
        .text("Test {")
        .range(range)
        .with(
          "MainActivity extends android.app.Activity implements test.pkg.MyInterface, test.pkg.Future, java.util.List, java.util.ExecutionException, my.pkg.SomeQualifiedName {\n" +
            "    Charset charset = Charset.defaultCharset();\n" +
            "    Object locale = java.util.Locale.ROOT;\n" +
            "    java.nio.charset.Charset charset = java.nio.charset.StandardCharsets.UTF_8;\n" +
            "    java.nio.charset.Charset charset2 = kotlin.text.Charsets.UTF_8;\n" +
            "    @androidx.annotation.ChecksSdkIntAtLeast(api=android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH)\n" +
            "    public void test() { }"
        )
        .shortenNames()
        .imports("android.app.Activity")
        .autoFix()
        .build()

    // Just apply the quickfix; no imports added, no references shortened
    check(
      file,
      source,
      fix,
      expected =
        """
        package test.pkg;

        import android.app.Application;
        import static com.android.tools.SdkUtils.method;
        import java.util.*;
        import java.util.concurrent.ExecutionException;
        import java.util.concurrent.Future;

        class MainActivity extends android.app.Activity implements test.pkg.MyInterface, test.pkg.Future, java.util.List, java.util.ExecutionException, my.pkg.SomeQualifiedName {
            Charset charset = Charset.defaultCharset();
            Object locale = java.util.Locale.ROOT;
            java.nio.charset.Charset charset = java.nio.charset.StandardCharsets.UTF_8;
            java.nio.charset.Charset charset2 = kotlin.text.Charsets.UTF_8;
            @androidx.annotation.ChecksSdkIntAtLeast(api=android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            public void test() { }
        }
        """,
      includeMarkers = false,
      updateImports = false
    )

    // Now update imports -- and rewrite code in the replacement to use the new and existing imports
    // for shortening. Don't infer additional imports from fully qualified names in the sources.
    check(
      file,
      source,
      fix,
      expected =
        """
        package test.pkg;

        import android.app.Activity;
        import android.app.Application;
        import static com.android.tools.SdkUtils.method;
        import java.util.*;
        import java.util.concurrent.ExecutionException;
        import java.util.concurrent.Future;

        class MainActivity extends android.app.Activity implements test.pkg.MyInterface, test.pkg.Future, List, java.util.ExecutionException, my.pkg.SomeQualifiedName {
            Charset charset = Charset.defaultCharset();
            Object locale = Locale.ROOT;
            java.nio.charset.Charset charset = java.nio.charset.StandardCharsets.UTF_8;
            java.nio.charset.Charset charset2 = kotlin.text.Charsets.UTF_8;
            @androidx.annotation.ChecksSdkIntAtLeast(api=android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            public void test() { }
        }
        """,
      includeMarkers = false,
      updateImports = true
    )

    // Finally, apply imports and reference shortening, but also infer additional fully qualified
    // names in the replacement snippet and import those as well.
    check(
      file,
      source,
      fix,
      expected =
        """
        package test.pkg;

        import android.app.Activity;
        import android.app.Application;
        import android.os.Build;
        import androidx.annotation.ChecksSdkIntAtLeast;
        import static com.android.tools.SdkUtils.method;
        import java.nio.charset.Charset;
        import java.nio.charset.StandardCharsets;
        import java.util.*;
        import java.util.concurrent.ExecutionException;
        import java.util.concurrent.Future;
        import my.pkg.SomeQualifiedName;

        class MainActivity extends Activity implements MyInterface, test.pkg.Future, List, java.util.ExecutionException, SomeQualifiedName {
            Charset charset = Charset.defaultCharset();
            Object locale = Locale.ROOT;
            Charset charset = StandardCharsets.UTF_8;
            Charset charset2 = Charsets.UTF_8;
            @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            public void test() { }
        }
        """,
      includeMarkers = true,
      updateImports = true
    )
  }

  @Test
  fun testDoNotShortenStrings() {
    // Make sure we don't shorten references in strings.
    val source =
      """
      package test.pkg;

      class Test {
      }
      """
        .trimIndent()

    val (file, range) = getFileAndRange("Test.java", source, source.indexOf("class Test"))
    val fix =
      fix()
        .annotate(
          "@android.annotation.EnforcePermission(allOf={\n" +
            "// android.permission.READ_CONTACTS is necessary because of X\n" +
            "\"android.permission.READ_CONTACTS\", \"android.permission.WRITE_CONTACTS\"})"
        )
        .range(range)
        .autoFix()
        .build()

    // Just apply the quickfix; no imports added, no references shortened
    check(
      file,
      source,
      fix,
      expected =
        """
        package test.pkg;
        import android.annotation.EnforcePermission;

        @EnforcePermission(allOf={
        // android.permission.READ_CONTACTS is necessary because of X
        "android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS"})
        class Test {
        }
        """
          .trimIndent(),
      includeMarkers = true,
      updateImports = true
    )
  }

  @Test
  fun testInvalidTextReplaceFix() {
    val source =
      """
      First line.
      """
        .trimIndent()

    val (file, range) = getFileAndRange("source.txt", source)
    val fix = fix().replace().text("Not Present").range(range).with("2nd").autoFix().build()
    check(
      file,
      source,
      fix,
      expected = "First line.",
      expectedFailure =
        """
        Did not find "Not Present" in "First line." in source.txt as suggested in the quickfix.

        Consider calling ReplaceStringBuilder#range() to set a larger range to
        search than the default highlight range.

        (This fix is associated with the issue id `_FixPerformerTestIssue`,
        reported via com.android.tools.lint.checks.LintDetectorDetector.)
        """
    )
  }

  @Test
  fun testInvalidRegexReplaceFix() {
    val source = "First line."
    val (file, range) = getFileAndRange("source.txt", source)
    val fix = fix().replace().pattern("(Not Present)").range(range).with("2nd").autoFix().build()
    check(
      file,
      source,
      fix,
      expected = "First line.",
      expectedFailure =
        """
        Did not match pattern "(Not Present)" in "First line." in source.txt as suggested in the quickfix.

        (This fix is associated with the issue id `_FixPerformerTestIssue`,
        reported via com.android.tools.lint.checks.LintDetectorDetector.)
        """
    )
  }

  @Test
  fun testLineCleanup() {
    // Regression test for b/185853711
    val source =
      """
      import android.util.Log;
      public class Test {
      }
      """
        .trimIndent()

    val startOffset = source.indexOf("public")
    val (file, range) =
      getFileAndRange("Test.java", source, startOffset, startOffset + "public".length)
    val fix = fix().replace().range(range).with("").autoFix().build()
    check(
      file,
      source,
      fix,
      expected = """
        import android.util.Log;
         class Test {
        }
        """,
      expectedOutput = "Applied 1 edits across 1 files for this fix: Delete"
    )
  }

  @Test
  fun testMultipleReplaces() {
    // Ensures we reorder edits correctly
    val source =
      """
      First line.
      Second line.
      Third line.
      """
        .trimIndent()

    val (file, range) = getFileAndRange("test.txt", source)
    val fix1 = fix().replace().text("Third").range(range).with("3rd").autoFix().build()
    val fix2 = fix().replace().text("First").range(range).with("1st").autoFix().build()
    val fix3 = fix().replace().text("Second").range(range).with("2nd").autoFix().build()
    check(
      file,
      source,
      fix1,
      fix2,
      fix3,
      expected = """
            1st line.
            2nd line.
            3rd line.""",
      expectedOutput =
        """
        Applied 3 edits across 1 files
        1: Replace with 3rd
        1: Replace with 2nd
        1: Replace with 1st
        """
    )
  }

  @Test
  fun testXmlSetAttribute() {
    @Language("XML")
    val source =
      """
      <root>
          <element1 attribute1="value1" />
          <element2 attribute1="value1" attribute2="value2"/>
      </root>
      """
        .trimIndent()

    val (file, range) =
      getFileAndRange("test.xml", source, source.indexOf("attribute1"), source.length)
    val fix = fix().set(ANDROID_URI, "new_attribute", "new value").range(range).autoFix().build()
    check(
      file,
      source,
      fix,
      // language=XML
      expected =
        """
        <root xmlns:android="http://schemas.android.com/apk/res/android">
            <element1 android:new_attribute="new value" attribute1="value1" />
            <element2 attribute1="value1" attribute2="value2"/>
        </root>
        """
    )
  }

  @Test
  fun testXmlSetAttributeEscapedValue() {
    // Make sure that for selection we include the whole range, including escaped values
    @Language("XML")
    val source =
      """
      <root>
          <element1 attribute1="value1" />
      </root>
      """
        .trimIndent()

    val (file, range) =
      getFileAndRange("test.xml", source, source.indexOf("attribute1"), source.length)
    val fix =
      fix()
        .set(ANDROID_URI, "new_attribute", "a < b & c > d")
        .selectAll()
        .range(range)
        .autoFix()
        .build()
    check(
      file,
      source,
      fix,
      // language=XML
      expected =
        """
        <root xmlns:android="http://schemas.android.com/apk/res/android">
            <element1 android:new_attribute="[a &lt; b &amp; c > d]|" attribute1="value1" />
        </root>
        """,
      includeMarkers = true
    )
  }

  @Test
  fun testXmlSetAttributeToSame() {
    @Language("XML")
    val source =
      """
      <root>
          <element1 attribute1="value1" />
          <element2 attribute1="value1" attribute2="value2"/>
      </root>
      """
        .trimIndent()

    val (file, range) =
      getFileAndRange("test.xml", source, source.indexOf("attribute2"), source.length)
    val fix = fix().set(null, "attribute2", "value2").range(range).autoFix().build()
    check(
      file,
      source,
      fix,
      // language=XML
      expected =
        """
        <root>
            <element1 attribute1="value1" />
            <element2 attribute1="value1" attribute2="value2"/>
        </root>
        """,
      expectedOutput = ""
    )
  }

  @Test
  fun testXmlSetAttributeOrder1() {
    @Language("XML")
    val source =
      """
      <root xmlns:android="http://schemas.android.com/apk/res/android">
          <element1 android:layout_width="wrap_content" android:width="foo" />
      </root>
      """
        .trimIndent()

    val (file, range) =
      getFileAndRange("test.xml", source, source.indexOf("element1"), source.length)
    val fix = fix().set(ANDROID_URI, "layout_height", "wrap_content").range(range).autoFix().build()
    check(
      file,
      source,
      fix,
      // language=XML
      expected =
        """
        <root xmlns:android="http://schemas.android.com/apk/res/android">
            <element1 android:layout_width="wrap_content" android:layout_height="wrap_content" android:width="foo" />
        </root>
        """
    )
  }

  @Test
  fun testXmlSetAttributeOrder2() {
    @Language("XML")
    val source =
      """
      <root xmlns:android="http://schemas.android.com/apk/res/android">
          <element1 android:layout_width="wrap_content" android:width="foo" />
      </root>
      """
        .trimIndent()

    val (file, range) =
      getFileAndRange("test.xml", source, source.indexOf("element1"), source.length)
    val fix = fix().set(ANDROID_URI, "id", "@+id/my_id").range(range).autoFix().build()
    check(
      file,
      source,
      fix,
      // language=XML
      expected =
        """
        <root xmlns:android="http://schemas.android.com/apk/res/android">
            <element1 android:id="@+id/my_id" android:layout_width="wrap_content" android:width="foo" />
        </root>
        """
    )
  }

  @Test
  fun testXmlSetAttributeOrder3() {
    @Language("XML")
    val source =
      """
      <root xmlns:android="http://schemas.android.com/apk/res/android">
          <element1 android:layout_weight="1.0" android:width="foo" />
      </root>
      """
        .trimIndent()

    val (file, range) =
      getFileAndRange("test.xml", source, source.indexOf("element1"), source.length)
    // When we insert multiple attributes ensure that they're sorted relative to each other too,
    // not just relative to the existing attributes
    val fix =
      fix()
        .name("Set multiple attributes")
        .composite(
          fix().set(ANDROID_URI, "layout_width", "wrap_content").range(range).autoFix().build(),
          fix().set(ANDROID_URI, "z-order", "5").range(range).autoFix().build(),
          fix().set(ANDROID_URI, "layout_height", "wrap_content").range(range).autoFix().build()
        )
    check(
      file,
      source,
      fix,
      // language=XML
      expected =
        """
        <root xmlns:android="http://schemas.android.com/apk/res/android">
            <element1 android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_weight="1.0" android:width="foo" android:z-order="5" />
        </root>
        """
    )
  }

  @Test
  fun testXmlDeleteAttribute() {
    @Language("XML")
    val source =
      """
      <root>
          <element1 attribute1="value1" />
          <element2 attribute1="value1" attribute2="value2"/>
      </root>
      """
        .trimIndent()

    val (file, range) =
      getFileAndRange("test.xml", source, source.indexOf("element2"), source.length)
    val fix = fix().unset(null, "attribute2").range(range).autoFix().build()
    check(
      file,
      source,
      fix,
      // language=XML
      expected =
        """
        <root>
            <element1 attribute1="value1" />
            <element2 attribute1="value1" />
        </root>
        """
    )
  }

  @Test
  fun testXmlDeleteAttributeNamespace() {
    @Language("XML")
    val source =
      """
      <root xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:tools="http://schemas.android.com/tools">
          <element2 attribute1="value1" android:attribute1="value1" tools:attribute1="value1" />
      </root>
      """
        .trimIndent()

    val (file, range) =
      getFileAndRange("test.xml", source, source.indexOf("element2"), source.length)
    val fix = fix().unset(ANDROID_URI, "attribute1").range(range).autoFix().build()
    check(
      file,
      source,
      fix,
      // language=XML
      expected =
        """
        <root xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:tools="http://schemas.android.com/tools">
            <element2 attribute1="value1" tools:attribute1="value1" />
        </root>
        """
    )
  }

  @Test
  fun testXmlDeleteAttributePrefixNamespace() {
    @Language("XML")
    val source =
      """
      <root xmlns:a="http://schemas.android.com/apk/res/android"
            xmlns:tools="http://schemas.android.com/tools">
          <element2 attribute1="value1" a:attribute1="value1" tools:attribute1="value1" />
      </root>
      """
        .trimIndent()

    val (file, range) =
      getFileAndRange("test.xml", source, source.indexOf("element2"), source.length)
    val fix = fix().unset(ANDROID_URI, "attribute1").range(range).autoFix().build()
    check(
      file,
      source,
      fix,
      expected =
        // language=XML
        """
        <root xmlns:a="http://schemas.android.com/apk/res/android"
              xmlns:tools="http://schemas.android.com/tools">
            <element2 attribute1="value1" tools:attribute1="value1" />
        </root>
        """
    )
  }

  @Test
  fun testXmlReplaceAttribute1() {
    @Language("XML")
    val source =
      """
      <root xmlns:a="http://schemas.android.com/apk/res/android"
            xmlns:tools="http://schemas.android.com/tools">
          <element2 a:attribute1="value1" tools:attribute1="value1" />
      </root>
      """
        .trimIndent()

    val (file, range) =
      getFileAndRange("test.xml", source, source.indexOf("element2"), source.length)
    val fix =
      fix()
        .replaceAttribute(ANDROID_URI, "attribute1", "value1", AUTO_URI)
        .range(range)
        .autoFix()
        .build()
    assertEquals("Update to `app:attribute1`", fix.getDisplayName())
    check(
      file,
      source,
      fix,
      expected =
        // language=XML
        """
        <root xmlns:a="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto" xmlns:tools="http://schemas.android.com/tools">
            <element2 app:attribute1="value1" tools:attribute1="value1" />
        </root>
        """
    )

    assertEquals(
      "Update to `attribute2`",
      fix()
        .replaceAttribute(ANDROID_URI, "attribute1", "value1", null, "attribute2")
        .build()
        .getDisplayName()
    )
    assertEquals(
      "Drop namespace prefix",
      fix().replaceAttribute(ANDROID_URI, "attribute1", "value1", null).build().getDisplayName()
    )
  }

  @Test
  fun testXmlReplaceAttribute2() {
    @Language("XML")
    val source =
      """
      <root>
          <element2 attribute1="value1" />
      </root>
      """
        .trimIndent()

    val (file, range) =
      getFileAndRange("test.xml", source, source.indexOf("element2"), source.length)
    val fix =
      fix()
        .replaceAttribute(null, "attribute1", "value1", null, "attribute2")
        .range(range)
        .autoFix()
        .build()
    assertEquals("Update to `attribute2`", fix.getDisplayName())
    check(
      file,
      source,
      fix,
      expected =
        // language=XML
        """
        <root>
            <element2 attribute2="value1" />
        </root>
        """
    )
  }

  @Test
  fun testXmlComposite() {
    @Language("XML")
    val source =
      """
      <root>
          <element1 attribute1="value1" />
          <element2 attribute1="value1" attribute2="value2"/>
      </root>
      """
        .trimIndent()

    val (file, range) =
      getFileAndRange("test.xml", source, source.indexOf("attribute1"), source.length)
    val unsetFix = fix().unset(null, "attribute1").range(range).build()
    val setFix = fix().set(ANDROID_URI, "new_attribute", "new value").range(range).build()

    // Make sure we complain if you don't set a proper name
    try {
      fix().composite(setFix, unsetFix).autoFix()
      fail("Expected failure for missing display name")
    } catch (e: IllegalStateException) {
      assertEquals(
        "You should explicitly set a display name for composite group actions; " +
          "unlike string replacement, set attribute, etc. it cannot produce a good default on its own",
        e.message
      )
    }

    val fix = fix().name("Set multiple attributes").composite(setFix, unsetFix).autoFix()
    check(
      file,
      source,
      fix,
      // language=XML
      expected =
        """
        <root xmlns:android="http://schemas.android.com/apk/res/android">
            <element1 android:new_attribute="new value" />
            <element2 attribute1="value1" attribute2="value2"/>
        </root>
        """
    )
  }

  @Test
  fun testXmlComposite2() {
    @Language("xml")
    val source =
      """
      <android.support.v7.widget.GridLayout xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:tools="http://schemas.android.com/tools"
          android:layout_width="match_parent"
          android:layout_height="match_parent"
          tools:ignore="HardcodedText">

          <TextView
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:layout_column="1"
              android:text="2" />

      </android.support.v7.widget.GridLayout>
      """
        .trimIndent()

    val (file, range) =
      getFileAndRange(
        "test.xml",
        source,
        startOffset = source.indexOf("android:layout_column"),
        endOffset = source.indexOf("android:layout_column") + "android:layout_column".length
      )
    val fix =
      fix()
        .name("Update to app:layout_column")
        .composite(
          fix().set().attribute("layout_column").value("1").namespace(AUTO_URI).autoFix().build(),
          fix()
            .set()
            .attribute("layout_column")
            .value(null)
            .namespace(ANDROID_URI)
            .autoFix()
            .build(),
        )
    check(
      file,
      source,
      fix,
      expected =
        "" +
          "<android.support.v7.widget.GridLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
          "    xmlns:app=\"http://schemas.android.com/apk/res-auto\" xmlns:tools=\"http://schemas.android.com/tools\"\n" +
          "    android:layout_width=\"match_parent\"\n" +
          "    android:layout_height=\"match_parent\"\n" +
          "    tools:ignore=\"HardcodedText\">\n" +
          "\n" +
          "    <TextView\n" +
          "        android:layout_width=\"wrap_content\"\n" +
          "        android:layout_height=\"wrap_content\"\n" +
          "        android:text=\"2\" app:layout_column=\"1\" />\n" +
          "\n" +
          "</android.support.v7.widget.GridLayout>\n",
      expectedOutput =
        "Applied 5 edits across 1 files\n" +
          "2: Set layout_column=\"1\"\n" +
          "3: Delete layout_column",
      location = range
    )
  }

  @Test
  fun testAttributeSorting() {
    val list =
      listOf(
        "xmlns:android",
        "android:layout_width",
        "android:layout_height",
        "package",
        "style",
        "android:width",
        "android:name",
        "xmlns:tools",
        "android:id",
        "app:my_attr",
        "layout",
        "random",
        "app:other_attr",
        "color",
        "tools:ignore",
        "tools:targetApi",
        "xliff:name"
      )
    val shuffled = list.shuffled()
    val comparator =
      Comparator<String> { a1, a2 ->
        val prefix1 = a1.substringBefore(':', "")
        val name1 = a1.substringAfter(':')
        val prefix2 = a2.substringBefore(':', "")
        val name2 = a2.substringAfter(':')
        compareAttributeNames(prefix1, name1, prefix2, name2)
      }
    val sorted = shuffled.sortedWith(comparator)
    assertEquals(
      "" +
        "xmlns:android\n" +
        "xmlns:tools\n" +
        "android:id\n" +
        "android:name\n" +
        "layout\n" +
        "package\n" +
        "style\n" +
        "android:layout_width\n" +
        "android:layout_height\n" +
        "android:width\n" +
        "app:my_attr\n" +
        "app:other_attr\n" +
        "xliff:name\n" +
        "random\n" +
        "color\n" +
        "tools:ignore\n" +
        "tools:targetApi",
      sorted.joinToString("\n")
    )
  }

  @Test
  fun testXmlRenameTag() {
    @Language("XML")
    val source =
      """
      <root xmlns:android="http://schemas.android.com/apk/res/android">
          <element1 android:layout_width="wrap_content" android:width="foo">
              <element2 />
          </element1>
      </root>
      """
        .trimIndent()

    val (file, range) =
      getFileAndRange(
        "test.xml",
        source,
        source.indexOf("<element1"),
        source.indexOf("</element1>") + "</element1>".length
      )
    val fix = fix().renameTag("element1", "newElement", range).autoFix().build()
    check(
      file,
      source,
      fix,
      // language=XML
      expected =
        """
        <root xmlns:android="http://schemas.android.com/apk/res/android">
            <newElement android:layout_width="wrap_content" android:width="foo">
                <element2 />
            </newElement>
        </root>
        """,
      requireAutoFixable = false
    )
  }

  @Test
  fun testShortenNames() {
    // Regression test for b/https://issuetracker.google.com/241573146
    @Language("java")
    val source =
      """
      package test.pkg;
      import android.graphics.drawable.Drawable;
      import android.graphics.Outline;

      class Test {
          static void getOutline() {
          }

          static void getOutline2() {
          }
      }
      """
        .trimIndent()

    val (file, range) = getFileAndRange("Test.java", source)
    val fix =
      fix()
        .replace()
        .text("()")
        .with("(android.graphics.drawable.Drawable drawable, android.graphics.Outline outline)")
        .repeatedly()
        .shortenNames()
        .autoFix()
        .range(range)
        .build()
    check(
      file,
      source,
      fix,
      expected =
        """
        package test.pkg;
        import android.graphics.drawable.Drawable;
        import android.graphics.Outline;

        class Test {
            static void getOutline(Drawable drawable, Outline outline) {
            }

            static void getOutline2(Drawable drawable, Outline outline) {
            }
        }
        """,
      expectedOutput =
        "Applied 2 edits across 1 files for this fix: Replace with (android.graphics.drawable.Drawable drawable, android.graphics.Outline outline)",
      includeMarkers = true
    )
  }

  @Test
  fun testAnnotate() {
    @Language("Java")
    val source =
      """
      public class Test {
          /** Comment */
          public void test() { }
      }
      """
        .trimIndent()

    val (file, range) =
      getFileAndRange("Test.java", source, source.indexOf("/** Comment"), source.length)
    val fix =
      fix().annotate("androidx.annotation.UiThread", null, null).range(range).autoFix().build()
    check(
      file,
      source,
      fix,
      expected =
        // language=Java
        """
        import androidx.annotation.UiThread;
        public class Test {
            /** Comment */
            @UiThread
            public void test() { }
        }
        """,
      includeMarkers = true
    )
  }

  @Test
  fun testAnnotate2() {
    val source =
      """
      package p1.p2
      /** My Property */
      @Suppress("SomeInspection1")
      const val someProperty = ""
      """
        .trimIndent()

    val (file, range) = getFileAndRange("test.kt", source, startOffset = source.indexOf("/**"))

    val fix =
      fix()
        .annotate("@kotlin.Suppress(\"SomeInspection2\")", null, null, false)
        .range(range)
        .build()
    check(
      file,
      source,
      fix,
      expected =
        """
        package p1.p2
        /** My Property */
        @Suppress("SomeInspection2")
        @Suppress("SomeInspection1")
        const val someProperty = ""
        """
          .trimIndent(),
      requireAutoFixable = false
    )
  }

  @Test
  fun testAnnotateReplace() {
    val source =
      """
      package p1.p2
      /** My Property */
      @Suppress("SomeInspection1")
      const val someProperty = ""
      """
        .trimIndent()

    val (file, range) = getFileAndRange("test.kt", source, startOffset = source.indexOf("/**"))

    val fix =
      fix()
        .name("Add annotations")
        .composite(
          fix()
            .annotate("@kotlin.Suppress(\"SomeInspection2\")", null, null, true)
            .range(range)
            .build()
        )
    check(
      file,
      source,
      fix,
      expected =
        """
        package p1.p2
        /** My Property */
        @Suppress("SomeInspection2")
        const val someProperty = ""
        """
          .trimIndent(),
      requireAutoFixable = false
    )
  }

  @Test
  fun testAnnotateFile() {
    val source =
      """
      package p1.p2
      /** My Property */
      @Suppress("SomeInspection1")
      const val someProperty = ""
      """
        .trimIndent()

    val (file, range) = getFileAndRange("test.kt", source, startOffset = source.indexOf("/**"))

    val fix =
      fix()
        .name("Add annotations")
        .annotate("@file:Suppress(\"SomeInspection2\")", null, null, true)
        .range(range)
        .build()
    check(
      file,
      source,
      fix,
      expected =
        """
        @file:Suppress("SomeInspection2")
        package p1.p2
        /** My Property */
        @Suppress("SomeInspection1")
        const val someProperty = ""
        """
          .trimIndent(),
      requireAutoFixable = false
    )
  }

  @Test
  fun testAnnotateNoNewline() {
    val source =
      """
      package p1.p2
      const val someProperty = ""
      """
        .trimIndent()

    val (file, range) =
      getFileAndRange("test.kt", source, startOffset = source.indexOf("const val"))

    val fix =
      fix()
        .name("Add annotations")
        .annotate("@Suppress(\"SomeInspection1\")", null, null, true)
        .range(range)
        .build()
    check(
      file,
      source,
      fix,
      expected =
        """
        package p1.p2
        @Suppress("SomeInspection1")
        const val someProperty = ""
        """
          .trimIndent(),
      requireAutoFixable = false
    )
  }

  @Test
  fun testAnnotateComplex() {
    // Checks that we really find the first non-whitespace non-comment line to insert the new
    // annotation (and that we respect import statements)
    @Language("KT")
    val source =
      """
      import androidx.annotation.UiThread
      class Test {
          /** Comment
            *
            * /* nested comment 1 /* nested nested comment */ */
            *    */

          // Also line comment

          @ExistingAnnotation(1)
          inline fun test() {
          }
      }
      """
        .trimIndent()

    val (file, range) =
      getFileAndRange("test.kt", source, source.indexOf("/** Comment"), source.length)
    val fix =
      fix().annotate("androidx.annotation.UiThread", null, null).range(range).autoFix().build()
    check(
      file,
      source,
      fix,
      expected =
        // language=KT
        """
      import androidx.annotation.UiThread
      class Test {
          /** Comment
            *
            * /* nested comment 1 /* nested nested comment */ */
            *    */

          // Also line comment

          @UiThread
          @ExistingAnnotation(1)
          inline fun test() {
          }
      }
      """
    )
  }

  @Test
  fun testWhitespaceCleanup() {
    val source =
      """
      class Test {
          public void test() {
              // Comment
          }
      }
      """
        .trimIndent()

    val (file, range) = getFileAndRange("Test.java", source)
    val fix =
      fix()
        .name("Remove method")
        .replace()
        .text("public void test() {\n" + "        // Comment\n" + "    }")
        .range(range)
        .with("")
        .autoFix()
        .build()
    check(
      file,
      source,
      fix,
      expected = """
        class Test {
        }
        """,
      expectedOutput = "Applied 3 edits across 1 files for this fix: Remove method",
    )
  }

  @Test
  fun testTrailingWhitespaceCleanup() {
    val source =
      """
      class Test { // comment
      }
      """
        .trimIndent()

    val (file, range) = getFileAndRange("Test.java", source)
    val fix =
      fix()
        .name("Remove comment")
        .replace()
        .text("// comment")
        .range(range)
        .with("")
        .autoFix()
        .build()
    check(
      file,
      source,
      fix,
      expected = """
        class Test {
        }
        """,
      expectedOutput = "Applied 2 edits across 1 files for this fix: Remove comment",
    )
  }

  private fun checkSkip(source: CharSequence, method: (CharSequence, Int) -> Int) {
    val index = source.indexOf('|')
    assertEquals(index, method(source.substring(0, index) + source.substring(index + 1), 0))
  }

  @Test
  fun testSkipComments() {
    checkSkip(
      " /* this /* is nested */ */ // line\n|test",
      LintFixPerformer.Companion::skipCommentsAndWhitespace
    )
  }

  @Test
  fun testSkipAnnotations() {
    checkSkip("@annotation| ", LintFixPerformer.Companion::skipAnnotation)
    checkSkip("@annotation|;next", LintFixPerformer.Companion::skipAnnotation)
    checkSkip("@annotation(5)| test", LintFixPerformer.Companion::skipAnnotation)
    checkSkip("@annotation(5,(6))| test", LintFixPerformer.Companion::skipAnnotation)
    checkSkip("@annotation(5 /*)*/)| test", LintFixPerformer.Companion::skipAnnotation)
    checkSkip("@annotation(5 //\n)| test", LintFixPerformer.Companion::skipAnnotation)
    checkSkip("@annotation(5, \")\")| test", LintFixPerformer.Companion::skipAnnotation)
    checkSkip("@annotation(5, \"\\\")\")| test", LintFixPerformer.Companion::skipAnnotation)
    checkSkip("@annotation(5, ')')| test", LintFixPerformer.Companion::skipAnnotation)
    checkSkip("@annotation(5, '\\')')| test", LintFixPerformer.Companion::skipAnnotation)
  }

  @Test
  fun testSkipStrings() {
    checkSkip("'c'|next", ::skipStringLiteral)
    checkSkip("'\\u1234'|next", ::skipStringLiteral)
    checkSkip("'\\''|next", ::skipStringLiteral)
    checkSkip("\"string\"|next", ::skipStringLiteral)
    checkSkip("\"string\\\"\"|next", ::skipStringLiteral)
    checkSkip("\"\"\"test\"\"test\"\"\"|next", ::skipStringLiteral)
  }

  private fun checkShorten(expected: String, source: String, allowCommentNesting: Boolean = true) {
    assertEquals(expected, collectNames(source, allowCommentNesting).toList().sorted().toString())
  }

  @Test
  fun testCollectNames() {
    checkShorten(
      "[foo.Bar]",
      "/* not.Code /* nested */ not.Code */ val x = \"not.Code\"; val y = foo.Bar"
    )
    checkShorten("[foo.Bar]", "val x = \"\\\"not.Code\\\"\"; val y = foo.Bar")
  }
}
