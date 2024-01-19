/*
 * Copyright (C) 2020 The Android Open Source Project
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

@file:Suppress("SpellCheckingInspection")

package com.android.tools.lint.checks.studio

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import org.junit.Test

class ForbiddenStudioCallDetectorTest {

  @Test
  fun testStringIntern() {
    studioLint()
      .files(
        java(
            """
                    package test.pkg;

                    @SuppressWarnings({
                        "MethodMayBeStatic",
                        "ClassNameDiffersFromFileName",
                        "StringOperationCanBeSimplified"
                    })
                    public class Test {
                        public String intern() { return ""; }

                        public void test(String s) {
                            String s1 = this.intern(); // OK
                            String s2 = "foo".intern(); // ERROR
                            String s3 = s.intern(); // ERROR
                            //noinspection NoInterning
                            String s4 = s.intern(); // OK
                        }

                        @SuppressWarnings("NoInterning")
                        public Test() {
                            System.out.println("foo".intern()); // OK
                        }
                    }
                   """
          )
          .indented()
      )
      .issues(ForbiddenStudioCallDetector.INTERN)
      .run()
      .expect(
        """
                src/test/pkg/Test.java:13: Error: Do not intern strings; if reusing strings is truly necessary build a local cache [NoInterning]
                        String s2 = "foo".intern(); // ERROR
                                          ~~~~~~~~
                src/test/pkg/Test.java:14: Error: Do not intern strings; if reusing strings is truly necessary build a local cache [NoInterning]
                        String s3 = s.intern(); // ERROR
                                      ~~~~~~~~
                2 errors, 0 warnings
                """
      )
  }

  @Test
  fun testFilesCopy() {
    studioLint()
      .files(
        java(
            """
                    package test.pkg;
                    import java.io.IOException;
                    import java.io.InputStream;
                    import java.nio.file.Files;
                    import java.nio.file.Path;

                    public class Test {
                        public void test(Path p1, Path p2, InputStream in) throws IOException {
                            Files.copy(p1, p2); // ERROR
                        }
                    }
                    """
          )
          .indented()
      )
      .issues(ForbiddenStudioCallDetector.FILES_COPY)
      .run()
      .expect(
        """
                src/test/pkg/Test.java:9: Error: Do not use java.nio.file.Files.copy(Path, Path). Instead, use FileUtils.copyFile(Path, Path) or Kotlin's File#copyTo(File) [NoNioFilesCopy]
                        Files.copy(p1, p2); // ERROR
                              ~~~~~~~~~~~~
                1 errors, 0 warnings
                """
      )
  }

  @Test
  fun testWhen() {
    studioLint()
      .files(
        kotlin(
            """
                    package test.pkg
                    import org.mockito.Mockito
                    import org.mockito.stubbing.OngoingStubbing

                    fun test(args: OngoingStubbing<String>) {
                        `when`(args)  // OK, not the Mockito when
                        Mockito.`when`(args) // WARN
                    }
                    fun `when`(arg: OngoingStubbing<String>) {}
                    """
          )
          .indented(),
        java(
            """
                    package test.pkg;
                    import org.mockito.Mockito;
                    import org.mockito.stubbing.OngoingStubbing;

                    class Test {
                        void test(OngoingStubbing<String> args) {
                            Mockito.when(args); // OK from Java
                        }
                    }
                    """
          )
          .indented(),
        // Stubs
        java(
            """
                    package org.mockito;
                    import org.mockito.stubbing.OngoingStubbing;
                    public class Mockito {
                        public static <T> OngoingStubbing<T> when(T methodCall) {
                            return null;
                        }
                    }
                    """
          )
          .indented(),
        java(
            """
                    package org.mockito.stubbing;
                    public interface OngoingStubbing<T> {
                    }
                    """
          )
          .indented(),
      )
      .issues(ForbiddenStudioCallDetector.MOCKITO_WHEN)
      .run()
      .expect(
        """
                src/test/pkg/test.kt:7: Error: Do not use Mockito.when from Kotlin; use MocktioKt.whenever instead [MockitoWhen]
                    Mockito.`when`(args) // WARN
                            ~~~~~~~~~~~~
                1 errors, 0 warnings
                """
      )
      .expectFixDiffs(
        """
                Fix for src/test/pkg/test.kt line 7: Use `whenever`:
                @@ -2 +2
                + import com.android.testutils.MockitoKt.whenever
                @@ -7 +8
                -     Mockito.`when`(args) // WARN
                +     whenever(args) // WARN
                """
      )
  }

  @Test
  fun testAddToStdlibStarImport() {
    studioLint()
      .files(
        kotlin(
          """
                    package test.pkg
                    import org.jetbrains.kotlin.utils.addToStdlib.*

                    fun test1(items: List<Iterable<*>>): String {
                      val tmp = items.firstNotNullOf { _ -> "" } // OK
                      return items.firstIsInstanceOrNull() // ERROR 1
                    }

                    fun test2(item: Number): Int? {
                      val tmp: Int? = item.safeAs<Int>() // ERROR 2
                      return tmp?.plus(1)
                    }

                    fun test3(occurrences: Array<Long>): Long {
                      return occurrences.sumByLong { it + 1 } // ERROR 3
                    }
                    """
        ),
        addToStdlibStub,
      )
      .issues(ForbiddenStudioCallDetector.ADD_TO_STDLIB_USAGE)
      .run()
      .expect(
        """
                src/test/pkg/test.kt:7: Warning: Avoid using methods from the unstable addToStdlib package [AddToStdlibUsage]
                                      return items.firstIsInstanceOrNull() // ERROR 1
                                                   ~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/test.kt:11: Warning: Avoid using methods from the unstable addToStdlib package [AddToStdlibUsage]
                                      val tmp: Int? = item.safeAs<Int>() // ERROR 2
                                                           ~~~~~~~~~~~~~
                src/test/pkg/test.kt:16: Warning: Avoid using methods from the unstable addToStdlib package [AddToStdlibUsage]
                                      return occurrences.sumByLong { it + 1 } // ERROR 3
                                                         ~~~~~~~~~~~~~~~~~~~~
                0 errors, 3 warnings
                """
      )
  }

  @Test
  fun testAddToStdlibMultipleImports() {
    studioLint()
      .files(
        kotlin(
          """
                    package test.pkg
                    import org.jetbrains.kotlin.utils.addToStdlib.safeAs
                    import org.jetbrains.kotlin.utils.addToStdlib.sumByLong

                    fun test3(occurrences: Sequence<Long>): Long {
                      return occurrences.sumByLong { it + 1 }  // ERROR 4
                    }

                    fun test2(item: Number): Int? {
                      val tmp: Int? = item.safeAs<Int>()  // ERROR 5
                      return tmp?.plus(1)
                    }

"""
        ),
        addToStdlibStub,
      )
      .issues(ForbiddenStudioCallDetector.ADD_TO_STDLIB_USAGE)
      .run()
      .expect(
        """
                src/test/pkg/test.kt:7: Warning: Avoid using methods from the unstable addToStdlib package [AddToStdlibUsage]
                                      return occurrences.sumByLong { it + 1 }  // ERROR 4
                                                         ~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/test.kt:11: Warning: Avoid using methods from the unstable addToStdlib package [AddToStdlibUsage]
                                      val tmp: Int? = item.safeAs<Int>()  // ERROR 5
                                                           ~~~~~~~~~~~~~
                0 errors, 2 warnings
                """
      )
  }

  private val addToStdlibStub: TestFile =
    kotlin(
        "org/jetbrains/kotlin/utils/addToStdlib.kt",
        """
                package org.jetbrains.kotlin.utils.addToStdlib

                import java.util.*

                inline fun <reified T : Any> Sequence<*>.firstIsInstanceOrNull(): T?
                inline fun <reified T : Any> Iterable<*>.firstIsInstanceOrNull(): T?
                inline fun <reified T : Any> Array<*>.firstIsInstanceOrNull(): T?

                inline fun <T> Iterable<T>.sumByLong(selector: (T) -> Long): Long

                inline fun <reified T : Any> Any?.safeAs(): T?

                """,
      )
      .within("src")
}
