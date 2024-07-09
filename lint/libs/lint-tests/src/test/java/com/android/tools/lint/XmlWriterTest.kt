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
package com.android.tools.lint

import com.android.tools.lint.checks.AbstractCheckTest
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.apache.commons.io.output.StringBuilderWriter
import org.jetbrains.uast.UCallExpression

@Suppress("LintDocExample") // Not actually testing a Detector.
class XmlWriterTest : AbstractCheckTest() {
  override fun getDetector() = WritePartialResultsDetector()

  override fun getIssues() = listOf(WritePartialResultsDetector.ISSUE)

  fun testWritePartialResults() {
    // Check that XmlWriter.writePartialResults and XmlWriter.writeConfiguredIssues write out sorted
    // entries, otherwise the output could be nondeterministic (based on HashMap iteration order).
    // We store the real (unwrapped) LintCliClient to a property of
    // WritePartialResultsDetector.Companion so that we can construct an XmlWriter from within the
    // Detector.
    try {
      lint()
        .files(
          kotlin(
              """
              package com.example.app

              fun foo() {
                bar()
              }

              fun bar() {}
              """
            )
            .indented()
        )
        .issues(WritePartialResultsDetector.ISSUE)
        .testModes(TestMode.PARTIAL)
        .clientFactory {
          val client = com.android.tools.lint.checks.infrastructure.TestLintClient()
          WritePartialResultsDetector.realClient = client
          client
        }
        .run()
        .expectClean()
    } finally {
      WritePartialResultsDetector.realClient = null
    }
  }

  class WritePartialResultsDetector : Detector(), SourceCodeScanner {

    var done = false

    override fun getApplicableMethodNames() = listOf("bar")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
      if (done) return
      done = true

      fun createIssue(id: String) =
        Issue.create(
          id,
          "Not applicable",
          "Not applicable",
          Category.MESSAGES,
          5,
          Severity.WARNING,
          Implementation(WritePartialResultsDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )

      val issueToMap: Map<Issue, LintMap> =
        mapOf(
          createIssue("Z") to
            LintMap().apply {
              put("z", 1)
              put("s", true)
              put("a", context.getLocation(node))
              put(
                "m",
                LintMap().apply {
                  put("5", "")
                  put("1", "")
                  put("9", "")
                },
              )
            },
          createIssue("A") to
            LintMap().apply {
              put("Banana", false)
              put("Apple", "yes")
              put("Carrot", 2)
            },
          createIssue("M") to
            LintMap().apply {
              put("foo", "")
              put("bar", "")
              put("zzz", "123")
              put(
                "aaa",
                LintMap().apply {
                  put("UAST", true)
                  put("PSI", 42)
                  put("XML", context.getLocation(node))
                },
              )
            },
        )

      val client = realClient!!
      val writer = StringBuilderWriter()

      XmlWriter(client, XmlFileType.PARTIAL_RESULTS, writer, client.pathVariables)
        .writePartialResults(issueToMap, context.project)

      // Remove the format number.
      val partialResultsOutput =
        writer.toString().replace(Regex("""format="\d+""""), """format="REMOVED"""")

      // The main thing we are checking is that entries are sorted.
      assertEquals(
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <incidents format="REMOVED" by="lint unittest" type="partial_results">
            <map id="A">
                <entry
                    name="Apple"
                    string="yes"/>
                <entry
                    name="Banana"
                    boolean="false"/>
                <entry
                    name="Carrot"
                    int="2"/>
            </map>
            <map id="M">
                    <map id="aaa">
                        <entry
                            name="PSI"
                            int="42"/>
                        <entry
                            name="UAST"
                            boolean="true"/>
                        <location id="XML"
                            file="${"$"}TEST_ROOT/partial/app/src/com/example/app/test.kt"
                            line="4"
                            column="3"
                            startOffset="39"
                            endLine="4"
                            endColumn="8"
                            endOffset="44"/>
                    </map>
                <entry
                    name="bar"
                    string=""/>
                <entry
                    name="foo"
                    string=""/>
                <entry
                    name="zzz"
                    string="123"/>
            </map>
            <map id="Z">
                <location id="a"
                    file="${"$"}TEST_ROOT/partial/app/src/com/example/app/test.kt"
                    line="4"
                    column="3"
                    startOffset="39"
                    endLine="4"
                    endColumn="8"
                    endOffset="44"/>
                    <map id="m">
                        <entry
                            name="1"
                            string=""/>
                        <entry
                            name="5"
                            string=""/>
                        <entry
                            name="9"
                            string=""/>
                    </map>
                <entry
                    name="s"
                    boolean="true"/>
                <entry
                    name="z"
                    int="1"/>
            </map>

        </incidents>

        """
          .trimIndent(),
        partialResultsOutput,
      )

      writer.builder.clear()

      val severityMap: Map<String, Severity> =
        mapOf(
          "Zebra" to Severity.WARNING,
          "Walrus" to Severity.WARNING,
          "Shark" to Severity.IGNORE,
          "Alpaca" to Severity.WARNING,
          "Cheetah" to Severity.ERROR,
          "Gazelle" to Severity.INFORMATIONAL,
        )

      XmlWriter(client, XmlFileType.CONFIGURED_ISSUES, writer, client.pathVariables)
        .writeConfiguredIssues(severityMap)

      // Remove the format number.
      val configuredIssuesOutput =
        writer.toString().replace(Regex("""format="\d+""""), """format="REMOVED"""")

      // The main thing we are checking is that entries are sorted.
      assertEquals(
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <incidents format="REMOVED" by="lint unittest" type="configured_issues">
            <config id="Alpaca" severity="warning"/>
            <config id="Cheetah" severity="error"/>
            <config id="Gazelle" severity="informational"/>
            <config id="Shark" severity="ignore"/>
            <config id="Walrus" severity="warning"/>
            <config id="Zebra" severity="warning"/>

        </incidents>

        """
          .trimIndent(),
        configuredIssuesOutput,
      )
    }

    companion object {
      var realClient: LintCliClient? = null

      val ISSUE =
        Issue.create(
          "_WritesPartialResults",
          "Not applicable",
          "Not applicable",
          Category.MESSAGES,
          5,
          Severity.WARNING,
          Implementation(WritePartialResultsDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )
    }
  }
}
