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

import com.android.testutils.TestUtils
import com.android.tools.lint.LintCliFlags.ERRNO_SUCCESS
import com.android.tools.lint.checks.AbstractCheckTest.SUPPORT_ANNOTATIONS_JAR
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.dos2unix
import com.google.common.base.Charsets
import com.google.common.io.Files
import java.io.File
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@Suppress("LintDocExample")
class UastEnvironmentSourceSetTest {

  @Test
  fun testSelectiveInput() {
    // Regression test for b/347624107
    val root = temp.newFolder().canonicalFile.absoluteFile
    val projects =
      lint()
        .files(
          SUPPORT_ANNOTATIONS_JAR,
          java(
              "src/Foo.java",
              """
                  import androidx.annotation.RequiresApi;
                  public class Foo {
                    @RequiresApi(24)
                    public static String foo(String x) {
                      return x;
                    }
                  }
                  """,
            )
            .indented(),
          kotlin(
              // NB: not under `src` to make the test pass in K2.
              // Put this under `src` will cause b/347624107#comment12
              "expected/Foo.kt",
              """
                  public open class Foo {
                    companion object {
                      @JvmStatic
                      public fun foo(x: String): String {
                        return x
                      }
                    }
                  }
                  """,
            )
            .indented(),
          java(
              "src/Bar.java",
              """
                  import static Foo.foo;
                  public class Bar {
                    public String bar(String x) {
                      return foo(x);
                    }
                  }
                  """,
            )
            .indented(),
        )
        .createProjects(root)

    @Language("XML")
    val descriptor =
      """
        <project>
          <sdk dir='${TestUtils.getSdk()}'/>
          <module name="app" android="true" library="false">
            <classpath jar="libs/support-annotations.jar" />
            <!-- This test works even without Foo.java... -->
            <!-- src file="src/Foo.java" /-->
            <!-- Intentionally miss Foo.kt to see if Foo.foo in Bar.java refers to Foo.java, not Foo.kt -->
            <!-- src file="src/Foo.kt" /-->
            <src file="src/Bar.java" />
          </module>
        </project>
      """

    Files.asCharSink(File(root, "project.xml"), Charsets.UTF_8).write(descriptor)
    MainTest.checkDriver(
      "src/Bar.java:4: Error: Call requires API level 24 (current min is 1): foo [NewApi]\n" +
        "    return foo(x);\n" +
        "           ~~~\n" +
        "1 errors, 0 warnings",
      "",

      // Expected exit code
      ERRNO_SUCCESS,

      // Args
      arrayOf("--check", "NewApi", "--project", File(root, "project.xml").path),
      { it.dos2unix() },
      null,
    )
  }

  @After
  fun tearDown() {
    UastEnvironment.disposeApplicationEnvironment()
  }

  companion object {
    @ClassRule @JvmField var temp = TemporaryFolder()
  }
}
