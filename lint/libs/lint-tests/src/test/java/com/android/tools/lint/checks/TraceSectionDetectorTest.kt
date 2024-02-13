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

import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

class TraceSectionDetectorTest : AbstractCheckTest() {

  override fun lint(): TestLintTask {
    return super.lint()
      // By default, test strict mode. The tests testNonStrictKotlin() and testNonStrictJava()
      // turn this off to check the other behavior
      .configureOption(TraceSectionDetector.STRICT_MODE, true)
  }

  override fun getDetector(): Detector = TraceSectionDetector()

  override fun getIssues(): List<Issue> = listOf(TraceSectionDetector.UNCLOSED_TRACE)

  fun testDocumentationExample() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            fun okay1(foo: Int, bar: Int) {
                Trace.beginSection("OK-1")
                val foobar = foo + bar
                Trace.endSection()
            }

            suspend fun wrong1() {
                Trace.beginSection("Wrong-1")
                suspendingCall()
                Trace.endSection()
            }

            fun wrong2(foo: String, bar: String) {
                Trace.beginSection("Wrong-2")
                // Kotlin does not have checked exceptions. Any function could throw.
                // Adding strings is a function, and therefore could throw an exception
                val foobar = foo + bar
                Trace.endSection()
            }

            fun okay2(foo: String, bar: String) {
                Trace.beginSection("OK-2")
                try {
                  val foobar = foo + bar
                } finally {
                  Trace.endSection()
                }
            }

            suspend fun suspendingCall() { }
            """
          )
          .indented(),
      )
      .run()
      .expect(
        """
          src/test/pkg/test.kt:12: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may suspend [UnclosedTrace]
              Trace.beginSection("Wrong-1")
                    ~~~~~~~~~~~~
          src/test/pkg/test.kt:18: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may throw an exception [UnclosedTrace]
              Trace.beginSection("Wrong-2")
                    ~~~~~~~~~~~~
          0 errors, 2 warnings
          """
      )
  }

  fun testGuardedTrace() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(29),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            fun okay1() {
                if (Trace.isEnabled()) {
                  Trace.beginSection("OK-1")
                }
                safeBlockingCall()
                Trace.endSection()
            }
            fun okay2() {
                Trace.beginSection("OK-2.1")
                if (Trace.isTagEnabled(Trace.TRACE_TAG_APP)) {
                  Trace.beginSection("OK-2.2")
                } else {
                  return
                }
                safeBlockingCall()
                Trace.endSection()
                Trace.endSection()
            }
            fun okay3() {
                Trace.beginSection("OK-3")
                safeBlockingCall()
                if (Trace.isEnabled()) {
                  Trace.endSection()
                }
            }
            fun okay4(name: String) {
                if (Trace.isEnabled()) {
                  Trace.beginSection("OK-$name")
                }
                try {
                  unsafeBlockingCall()
                } finally {
                  if (Trace.isEnabled()) {
                    Trace.endSection()
                  }
                }
            }
            fun safeBlockingCall() { }
            fun unsafeBlockingCall() { error() }
            """
          )
          .indented(),
        traceApiStub,
      )
      .run()
      .expectClean()
  }

  fun testNonStrictKotlin() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            fun testNonStrictKotlin(foo: String, bar: String) {
                Trace.beginSection("OK")
                safeBlockingCall()
                Trace.endSection()

                Trace.beginSection("Wrong-1")
                // This is NOT okay because ControlFlowGraph.isSafe() will inspect the body and see that
                // it likely will throw an error.
                blockingCallThatMightThrow()
                Trace.endSection()

                Trace.beginSection("Wrong-2")
                // Kotlin does not have checked exceptions, and the detector can't see the source of
                // plus(), so it can't check whether it is safe.
                val foobar = foo + bar
                Trace.endSection()
            }
            fun safeBlockingCall() { /* looks safe */ }
            fun blockingCallThatMightThrow() { error("can throw") }
            """
          )
          .indented(),
      )
      .configureOption(TraceSectionDetector.STRICT_MODE, false)
      .run()
      .expect(
        """
src/test/pkg/test.kt:10: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may throw an exception [UnclosedTrace]
    Trace.beginSection("Wrong-1")
          ~~~~~~~~~~~~
src/test/pkg/test.kt:16: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may throw an exception [UnclosedTrace]
    Trace.beginSection("Wrong-2")
          ~~~~~~~~~~~~
0 errors, 2 warnings
"""
      )
  }

  fun testStrictJava() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        java(
            """
            package test.pkg;

            import android.os.Trace;

            class Test {
                void doSomething() {
                    Trace.beginSection("Wrong-1");
                    doSomethingThatDeclaresThrow();
                    Trace.endSection();

                    Trace.beginSection("Wrong-2");
                    doSomethingThatCouldThrow(null);
                    Trace.endSection();

                    Trace.beginSection("Wrong-3");
                    doSomethingThatLooksSafe();
                    Trace.endSection();

                    Trace.beginSection("OK-2");
                    // There is no method body to analyze, and the implicit RuntimeException
                    // will be ignored when strict mode is off.
                    doSomethingElseThatCouldThrow();
                    Trace.endSection();
                }
                void doSomethingThatDeclaresThrow() throws RuntimeException {
                    // Even though this throws an unchecked exception, it will still warn when
                    // strict mode is off because we assume that we should be careful about
                    // handling explicitly declared exceptions.
                }
                void doSomethingThatCouldThrow(int[] array) {
                    array[0] = 1;
                }
                void doSomethingThatLooksSafe() { /* looks safe */ }
            }
            """
          )
          .indented(),
      )
      .run()
      .expect(
        """
src/test/pkg/Test.java:7: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may throw an exception [UnclosedTrace]
        Trace.beginSection("Wrong-1");
              ~~~~~~~~~~~~
src/test/pkg/Test.java:11: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may throw an exception [UnclosedTrace]
        Trace.beginSection("Wrong-2");
              ~~~~~~~~~~~~
src/test/pkg/Test.java:15: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may throw an exception [UnclosedTrace]
        Trace.beginSection("Wrong-3");
              ~~~~~~~~~~~~
0 errors, 3 warnings
          """
      )
  }

  fun testNonStrictJava() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        java(
            """
            package test.pkg;

            import android.os.Trace;

            class Test {
                void doSomething() {
                    Trace.beginSection("Wrong");
                    doSomethingThatDeclaresThrow();
                    Trace.endSection();

                    Trace.beginSection("OK-1");
                    // This is okay because strict mode is off. We won't even bother to check if
                    // the method body looks safe.
                    doSomethingThatCouldThrow(null);
                    Trace.endSection();

                    Trace.beginSection("OK-2");
                    doSomethingThatLooksSafe();
                    Trace.endSection();

                    Trace.beginSection("OK-3");
                    // There is no method body to analyze, and the implicit RuntimeException
                    // will be ignored when strict mode is off.
                    doSomethingElseThatCouldThrow();
                    Trace.endSection();
                }
                void doSomethingThatDeclaresThrow() throws RuntimeException {
                    // Even though this throws an unchecked exception, it will still warn when
                    // strict mode is off because we assume that we should be careful about
                    // handling explicitly declared exceptions.
                }
                void doSomethingThatCouldThrow(int[] array) {
                    array[0] = 1;
                }
                void doSomethingThatLooksSafe() { /* looks safe */ }
            }
            """
          )
          .indented(),
      )
      .configureOption(TraceSectionDetector.STRICT_MODE, false)
      .run()
      .expect(
        """
src/test/pkg/Test.java:7: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may throw an exception [UnclosedTrace]
        Trace.beginSection("Wrong");
              ~~~~~~~~~~~~
0 errors, 1 warnings
          """
      )
  }

  fun testSystemApis() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            fun okay1(foo: Int, bar: Int) {
                Trace.traceBegin(Trace.TRACE_TAG_APP, "OK-1")
                val foobar = foo + bar
                Trace.traceEnd(Trace.TRACE_TAG_APP)
            }

            fun okay2(foo: Int, bar: Int) {
                Trace.traceBegin(Trace.TRACE_TAG_APP, "OK-1")
                val foobar = foo + bar
                Trace.traceEnd(Trace.TRACE_TAG_APP)
            }

            fun wrong1() {
                Trace.traceBegin(Trace.TRACE_TAG_APP, "Wrong-1")
                // While this is technically okay (endSection() uses the Trace.TRACE_TAG_APP tag, but that's
                // an implementation detail. The lint check does not check the value of the tag passed to
                // traceBegin(). The assumption is that all calls to traceBegin should correspond to calls to
                // traceEnd, and all calls to beginSection should correspond to calls to endSection
                Trace.endSection()
            }

            fun wrong2(foo: String, bar: String) {
                Trace.traceBegin(Trace.TRACE_TAG_APP, "Wrong-2")
                // Kotlin does not have checked exceptions. Any function could throw.
                // Adding strings is a function, and therefore could throw an exception
                val foobar = foo + bar
                Trace.traceEnd(Trace.TRACE_TAG_APP)
            }

            fun okay2(foo: String, bar: String) {
                Trace.traceBegin(Trace.TRACE_TAG_APP, "OK-2")
                try {
                  val foobar = foo + bar
                } finally {
                  Trace.traceEnd(Trace.TRACE_TAG_APP)
                }
            }

            suspend fun suspendingCall() { }
            """
          )
          .indented(),
        traceApiStub,
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:18: Warning: The traceBegin() call is not always closed with a matching traceEnd() because the code in between may return early [UnclosedTrace]
            Trace.traceBegin(Trace.TRACE_TAG_APP, "Wrong-1")
                  ~~~~~~~~~~
        src/test/pkg/test.kt:27: Warning: The traceBegin() call is not always closed with a matching traceEnd() because the code in between may throw an exception [UnclosedTrace]
            Trace.traceBegin(Trace.TRACE_TAG_APP, "Wrong-2")
                  ~~~~~~~~~~
        0 errors, 2 warnings
        """
      )
  }

  fun testAndroidXApis() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import androidx.tracing.Trace

            fun okay1(foo: Int, bar: Int) {
                Trace.beginSection("OK-1")
                val foobar = foo + bar
                Trace.endSection()
            }

            fun wrong1() {
                Trace.beginSection("Wrong-1")
                // wrong endSection() call owner:
                android.os.Trace.endSection()
            }

            fun wrong2(foo: String, bar: String) {
                Trace.beginSection("Wrong-2")
                // Kotlin does not have checked exceptions. Any function could throw.
                // Adding strings is a function, and therefore could throw an exception
                val foobar = foo + bar
                Trace.endSection()
            }

            fun okay2(foo: String, bar: String) {
                Trace.beginSection("OK-2")
                try {
                  val foobar = foo + bar
                } finally {
                  Trace.endSection()
                }
            }

            fun okayAndWrong() {
                // If these all used the same API variant, this would be Wrong, but because the
                // next beginSection() call is a platform API instead of an AndroidX API, it won't
                // increment the open-trace-sections counter
                Trace.beginSection("OK-3")
                android.os.Trace.beginSection("Wrong-3") // this is wrong because it's not closed
                Trace.endSection()
            }

            suspend fun suspendingCall() { }
            """
          )
          .indented(),
        traceApiStub,
        androidxTraceStub,
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:12: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may return early [UnclosedTrace]
            Trace.beginSection("Wrong-1")
                  ~~~~~~~~~~~~
        src/test/pkg/test.kt:18: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may throw an exception [UnclosedTrace]
            Trace.beginSection("Wrong-2")
                  ~~~~~~~~~~~~
        src/test/pkg/test.kt:39: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may return early [UnclosedTrace]
            android.os.Trace.beginSection("Wrong-3") // this is wrong because it's not closed
                             ~~~~~~~~~~~~
        0 errors, 3 warnings
        """
      )
  }

  fun testSimpleBlockingCall() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            fun simpleAndWrong() {
                Trace.beginSection("Wrong")
                blockingCall()
                Trace.endSection()
            }

            fun blockingCall() { error("can throw") }
            """
          )
          .indented(),
      )
      .run()
      .expect(
        """
          src/test/pkg/test.kt:6: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may throw an exception [UnclosedTrace]
              Trace.beginSection("Wrong")
                    ~~~~~~~~~~~~
          0 errors, 1 warnings
          """
      )
  }

  fun testSimpleSuspendCall() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            suspend fun simpleAndWrong() {
                try {
                  Trace.beginSection("Wrong")
                  suspendingCall()
                  suspendingCall(1)
                  suspendingCall(1, "a")
                } finally {
                    Trace.endSection()
                }
            }

            suspend fun suspendingCall() { }
            suspend fun suspendingCall(a: Int) { }
            suspend fun suspendingCall(a: Int, b: String) { }
            """
          )
          .indented(),
      )
      .run()
      .expect(
        """
          src/test/pkg/test.kt:7: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may suspend [UnclosedTrace]
                Trace.beginSection("Wrong")
                      ~~~~~~~~~~~~
          0 errors, 1 warnings
          """
      )
  }

  fun testTryCatchVariations() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            fun okay1() {
                Trace.beginSection("OK-1")
                try {
                  blockingCall()
                } catch (_: Throwable) {
                } finally {
                  Trace.endSection()
                }
            }

            fun okay2() {
                try {
                  Trace.beginSection("OK-2")
                  blockingCall()
                } finally {
                  Trace.endSection()
                }
            }

            fun okay3() {
                try {
                  try {
                    Trace.beginSection("OK-3")
                  } finally {
                    blockingCall()
                  }
                } finally {
                  Trace.endSection()
                }
            }

            suspend fun okay4() {
                try {
                  Trace.beginSection("OK-4")
                } catch (_: Throwable) {
                  // Even though there is a suspending call, this is "OK" according to the lint check.
                  // That's expected, because we don't explore exceptions caused by calling beginSection()
                  // and endSection(). We assume those calls never throw exceptions.
                  suspendingCall()
                } finally {
                  Trace.endSection()
                }
            }

            fun wrong1() {
                Trace.beginSection("Wrong-1")
                try {
                  blockingCall()
                } catch (e: Throwable) {
                  e.printStackTrace()
                } finally {
                }
                // This is wrong for two reasons: 1) `e.printStackTrace()` could itself throw an exception, and
                // 2) we can't verify that all possible exceptions are caught. The only way to guarantee a
                // `Trace.endSection()` is called is by using a `finally` block.
                Trace.endSection()
            }

            // This is similar to okay2(), except we are making a suspending call instead of a blocking one
            suspend fun wrong2() {
                try {
                  Trace.beginSection("Wrong-2")
                  // This is wrong because if the function suspends, we effectively return early with a continuation
                  // without calling the `finally` block. The `finally` block is only called after the suspending call is
                  // finished.
                  suspendingCall()
                } finally {
                  Trace.endSection()
                }
            }

            suspend fun wrong3() {
                try {
                  Trace.beginSection("Wrong-3")
                  blockingCall()
                } catch (_: Throwable) {
                  suspendingCall()
                  Trace.endSection()
                } finally {
                  Trace.endSection()
                }
            }

            fun wrong4() {
                try {
                  Trace.beginSection("Wrong-4")
                } finally {
                  // The blocking call could throw an exception, and there is nothing here to catch it
                  blockingCall()
                  Trace.endSection()
                }
            }

            fun blockingCall() { error("can throw") }
            suspend fun suspendingCall() { }
            """
          )
          .indented(),
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:50: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may throw an exception [UnclosedTrace]
            Trace.beginSection("Wrong-1")
                  ~~~~~~~~~~~~
        src/test/pkg/test.kt:66: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may suspend [UnclosedTrace]
              Trace.beginSection("Wrong-2")
                    ~~~~~~~~~~~~
        src/test/pkg/test.kt:78: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may suspend [UnclosedTrace]
              Trace.beginSection("Wrong-3")
                    ~~~~~~~~~~~~
        src/test/pkg/test.kt:90: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may throw an exception [UnclosedTrace]
              Trace.beginSection("Wrong-4")
                    ~~~~~~~~~~~~
        0 errors, 4 warnings
        """
      )
  }

  fun testDeeplyNestedContrivedLogic() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            class FakeClass<T>(private val storedVal: T) {
                suspend fun await(): T {
                    suspendingCall()
                    return storedVal
                }
            }

            suspend fun traceDeeplyNestedSuspensionPoint() {
              try {
                Trace.beginSection("Wrong")
                val testString = "Hello, world!"
                testString.let {
                  it.also {
                    it.apply {
                      with(this) {
                        val myVal = FakeClass(testString).await()
                      }
                    }
                  }
                }
              } finally {
                Trace.endSection()
              }
            }

            suspend fun suspendingCall() { }
            """
          )
          .indented(),
      )
      .run()
      .expect(
        """
          src/test/pkg/FakeClass.kt:14: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may suspend [UnclosedTrace]
              Trace.beginSection("Wrong")
                    ~~~~~~~~~~~~
          0 errors, 1 warnings
          """
      )
  }

  fun testTraceIfElseVariations() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            fun wrong1(a: Int, b: Int, c: Int) {
                Trace.beginSection("Wrong-1")
                when (a) {
                    b -> Trace.endSection()
                    c -> { }
                    else -> Trace.endSection()
                }
            }

            fun okay1(a: Int, b: Int) {
                Trace.beginSection("OK-1")
                if (a == b) {
                  Trace.endSection()
                } else {
                  Trace.endSection()
                }
            }

            fun okay2(a: Int, b: Int) {
                Trace.beginSection("OK-2.1")
                if (a == b) {
                  Trace.endSection()
                } else {
                  try {
                    Trace.beginSection("OK-2.2")
                    blockingCall()
                  } finally {
                    Trace.endSection()
                    Trace.endSection()
                  }
                }
            }

            fun okay3(a: Int, b: Int) {
                  Trace.beginSection("OK-3.1")
                  if (a == b) {
                    Trace.beginSection("OK-3.2")
                    Trace.endSection()
                  }
                  Trace.endSection()
            }

            fun blockingCall() { error("can throw") }
            """
          )
          .indented(),
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:6: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may return early [UnclosedTrace]
            Trace.beginSection("Wrong-1")
                  ~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
  }

  /** This test demonstrates behavior that is wrong that the lint check might miss. */
  fun testUndetectedWrongTraceUsage() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            fun okayAndWrong4(a: Int, b: Int) {
                  try {
                    Trace.beginSection("OK-4.1")
                    if (a == b) {
                      Trace.beginSection("OK-4.2")
                      blockingCall()
                    }
                  } finally {
                    // When a == b, two traces begin in the try block, but only one section is closed in the
                    // finally block. Even though this is technically incorrect, the lint check is not
                    // capable of knowing which way it will branch based on how the conditionals are evaluated.
                    if (a != b) Trace.endSection()
                    Trace.endSection()
                  }
            }

            fun blockingCall() { error("can throw") }
            """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testIfToWhen() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            fun wrong(a: Int, b: Int) {
                  Trace.beginSection("Wrong")
                  if (a == b) {
                    Trace.beginSection("OK")
                    Trace.endSection()
                  }
            }
            """
          )
          .indented(),
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:6: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may return early [UnclosedTrace]
              Trace.beginSection("Wrong")
                    ~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
  }

  fun testNestedBeginSections() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            fun okFlow() {
                  Trace.beginSection("OK")
                  Trace.beginSection("OK")
                  Trace.endSection()
                  Trace.endSection()
            }
            """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  /**
   * Demonstrates cases in which there are too many calls to endSection() to show that the
   * [TraceSectionDetector] does not check for this.
   */
  fun testTooManyEndSections() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            fun unclosedTraceSection(a: Int, b: Int) {
                  Trace.beginSection("section-1")
                  Trace.beginSection("section-2")
                  Trace.endSection()
                  Trace.endSection()
                  Trace.endSection()
            }
            """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testEndBeforeBegin() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            fun wrongOrder() {
                  Trace.endSection()
                  Trace.beginSection("Wrong")
            }
            """
          )
          .indented(),
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:7: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may return early [UnclosedTrace]
              Trace.beginSection("Wrong")
                    ~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
  }

  fun testManyIssues() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            suspend fun test1(a: Int, b: Int) {
                  Trace.beginSection("Wrong-1")
                  try {
                    Trace.beginSection("Wrong-2")
                  } finally {
                    blockingCall()
                    Trace.endSection()
                  }
                  Trace.beginSection("Wrong-3")
                  if (a == b) {
                    Trace.endSection()
                  }
                  Trace.beginSection("Wrong-4")
                  suspendingCall()
                  Trace.endSection()
            }

            fun test2(a: Int, b: Int) {
                  Trace.beginSection("Wrong-5")
                  try {
                    Trace.beginSection("Wrong-6")
                  } finally {
                    blockingCall()
                    Trace.endSection()
                  }
                  Trace.beginSection("Wrong-7")
                  if (a == b) {
                    Trace.endSection()
                  }
            }

            fun blockingCall() { error("can throw") }
            suspend fun suspendingCall() { }
            """
          )
          .indented(),
      )
      .run()
      .expect(
        """
src/test/pkg/test.kt:6: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may suspend [UnclosedTrace]
      Trace.beginSection("Wrong-1")
            ~~~~~~~~~~~~
src/test/pkg/test.kt:8: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may throw an exception [UnclosedTrace]
        Trace.beginSection("Wrong-2")
              ~~~~~~~~~~~~
src/test/pkg/test.kt:13: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may suspend [UnclosedTrace]
      Trace.beginSection("Wrong-3")
            ~~~~~~~~~~~~
src/test/pkg/test.kt:17: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may suspend [UnclosedTrace]
      Trace.beginSection("Wrong-4")
            ~~~~~~~~~~~~
src/test/pkg/test.kt:23: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may return early [UnclosedTrace]
      Trace.beginSection("Wrong-5")
            ~~~~~~~~~~~~
src/test/pkg/test.kt:25: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may throw an exception [UnclosedTrace]
        Trace.beginSection("Wrong-6")
              ~~~~~~~~~~~~
src/test/pkg/test.kt:30: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may return early [UnclosedTrace]
      Trace.beginSection("Wrong-7")
            ~~~~~~~~~~~~
0 errors, 7 warnings
        """
      )
  }

  fun testSuspendAfterTryFinally() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            suspend fun manyIssues() {
                  Trace.beginSection("Wrong-1")
                  try {
                    Trace.beginSection("Wrong-2")
                  } finally {
                    blockingCall()
                    Trace.endSection()
                  }
                  suspendingCall()
                  Trace.endSection()
            }

            fun blockingCall() { error("can throw") }
            suspend fun suspendingCall() { }
            """
          )
          .indented(),
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:6: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may suspend [UnclosedTrace]
              Trace.beginSection("Wrong-1")
                    ~~~~~~~~~~~~
        src/test/pkg/test.kt:8: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may throw an exception [UnclosedTrace]
                Trace.beginSection("Wrong-2")
                      ~~~~~~~~~~~~
        0 errors, 2 warnings
        """
      )
  }

  fun testNestedLogic() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            fun nestedLogic(a: Int, b: Int, c: Int) {
                  try {
                    Trace.beginSection("Wrong-1")
                    if (a == b) {
                      Trace.beginSection("Wrong-1")
                      if (a == c) {
                        Trace.beginSection("OK")
                        return
                      }
                      Trace.endSection()
                    }
                  } finally {
                    Trace.endSection()
                  }
            }

            fun blockingCall() { error("can throw") }
            """
          )
          .indented(),
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:7: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may throw an exception [UnclosedTrace]
                Trace.beginSection("Wrong-1")
                      ~~~~~~~~~~~~
        src/test/pkg/test.kt:9: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may throw an exception [UnclosedTrace]
                  Trace.beginSection("Wrong-1")
                        ~~~~~~~~~~~~
        0 errors, 2 warnings
        """
      )
  }

  fun testWrongLoop() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            fun runLoop() {
                Trace.beginSection("Wrong")
                for (i in 0..10) {
                  try {
                    Trace.beginSection("OK")
                    blockingCall()
                  } finally {
                    Trace.endSection()
                  }
                }
                Trace.endSection()
            }

            fun blockingCall() { error("can throw") }
            """
          )
          .indented(),
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:6: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may throw an exception [UnclosedTrace]
            Trace.beginSection("Wrong")
                  ~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
  }

  fun testOkLoop() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            fun runLoop() {
                try {
                  Trace.beginSection("OK-1")
                  for (i in 0..10) {
                    try {
                      Trace.beginSection("OK-2")
                      blockingCall()
                    } finally {
                      Trace.endSection()
                    }
                  }
                } finally {
                  Trace.endSection()
                }
            }

            fun blockingCall() { error("can throw") }
            """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testOkNestedTryCatch() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            fun runTryCatch() {
                try {
                  Trace.beginSection("OK-1")
                  blockingCall("call-1")
                  try {
                    Trace.beginSection("OK-2")
                    blockingCall("call-2")
                    try {
                      Trace.beginSection("OK-3")
                      blockingCall("call-3")
                    } finally {
                      var finallyName = "finally-3a"
                      Trace.endSection()
                      finallyName = "finally-3b"
                    }
                  } finally {
                    var finallyName = "finally-2a"
                    Trace.endSection()
                    finallyName = "finally-2b"
                  }
                } finally {
                  var finallyName = "finally-1a"
                  Trace.endSection()
                  finallyName = "finally-1b"
                }
            }

            fun blockingCall() { error("can throw") }
            """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  /** Test that possible exceptions from beginSection() and endSection() calls are ignored. */
  fun testOkNestedTraceSections() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            fun threeNestedTraceSections() {
                Trace.beginSection("OK-1")
                Trace.beginSection("OK-2")
                Trace.beginSection("OK-3")
                Trace.endSection()
                Trace.endSection()
                Trace.endSection()
            }
            """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testBadNestedTraceSections() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import android.os.Trace

            fun threeBadlyNestedTraceSections() {
                Trace.beginSection("Wrong-1")
                blockingCall("call-1")
                Trace.beginSection("Wrong-2")
                blockingCall("call-2")
                Trace.beginSection("Wrong-3")
                blockingCall("call-3")
                Trace.endSection()
                Trace.endSection()
                Trace.endSection()
            }

            fun blockingCall() { error("can throw") }
            """
          )
          .indented(),
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:6: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may throw an exception [UnclosedTrace]
            Trace.beginSection("Wrong-1")
                  ~~~~~~~~~~~~
        src/test/pkg/test.kt:8: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may throw an exception [UnclosedTrace]
            Trace.beginSection("Wrong-2")
                  ~~~~~~~~~~~~
        src/test/pkg/test.kt:10: Warning: The beginSection() call is not always closed with a matching endSection() because the code in between may throw an exception [UnclosedTrace]
            Trace.beginSection("Wrong-3")
                  ~~~~~~~~~~~~
        0 errors, 3 warnings
        """
      )
  }

  /**
   * This test ensures that similarly named third party methods don't trigger the lint check or
   * cause crashes.
   */
  fun testThirdPartyTraceMethods() {
    lint()
      .files(
        classpath(),
        manifest().minSdk(18),
        kotlin(
            """
            package test.pkg

            import com.thirdparty.Helper
            import com.thirdparty.Trace
            import com.thirdparty.Util

            fun thirdPartyTraceMethods() {
                Trace.beginSection("OK-1")
                blockingCall("call-1")
                val helper = Helper()
                helper.beginSection("OK-2")
                blockingCall("call-2")
                Util.beginSection("OK-3")
                blockingCall("call-3")
                Util.endSection()
                helper.endSection()
                Trace.endSection()
            }

            fun blockingCall() { error("can throw") }
            """
          )
          .indented(),
        *thirdPartyTraceStubs,
      )
      .run()
      .expectClean()
  }

  private val traceApiStub =
    java(
        """
        package android.os;
        public final class Trace {
            public static final long TRACE_TAG_APP = 1L << 12;
            public static boolean isEnabled() {}
            public static boolean isTagEnabled(long traceTag) {}
            public static void beginSection(String sectionName) {}
            public static void endSection() {}
            public static void traceBegin(long traceTag, String methodName) {}
            public static void traceEnd(long traceTag) {}
        }
        """
      )
      .indented()

  private val androidxTraceStub =
    java(
        """
        package androidx.tracing;
        public final class Trace {
            public static void beginSection(String sectionName) {}
            public static void endSection() {}
        }
        """
      )
      .indented()

  private val thirdPartyTraceStubs =
    arrayOf(
      java(
          """
          package com.thirdparty;
          public final class Trace {
              public static void beginSection(String sectionName) {}
              public static void endSection() {}
          }
          """
        )
        .indented(),
      java(
          """
          package com.thirdparty;
          public final class Util {
              public static void beginSection(String sectionName) {}
              public static void endSection() {}
          }
          """
        )
        .indented(),
      java(
          """
          package com.thirdparty;
          public final class Helper {
              public void beginSection(String sectionName) {}
              public void endSection() {}
          }
          """
        )
        .indented(),
    )
}
