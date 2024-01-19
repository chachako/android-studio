/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.lint.checks

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector

class NoOpDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return NoOpDetector()
  }

  fun testDocumentationExample() {
    lint()
      .files(
        kotlin(
            """
                class Test {
                    fun test(s: String, o: Any) {
                       s === o                // ERROR 1
                       o.toString()           // ERROR 2
                       s.length               // ERROR 3
                    }
                }
                """
          )
          .indented()
      )
      .run()
      .expect(
        """
                src/Test.kt:3: Warning: This reference is unused: s === o [NoOp]
                       s === o                // ERROR 1
                       ~~~~~~~
                src/Test.kt:4: Warning: This call result is unused: toString [NoOp]
                       o.toString()           // ERROR 2
                       ~~~~~~~~~~~~
                src/Test.kt:5: Warning: This reference is unused: length [NoOp]
                       s.length               // ERROR 3
                         ~~~~~~
                0 errors, 3 warnings
                """
      )
  }

  @Suppress("RemoveRedundantCallsOfConversionMethods", "DefaultAnnotationParam")
  fun testProblems() {
    lint()
      .files(
        kotlin(
            """
                    package test.pkg
                    class Foo {
                        var a: Int = 1
                        var b: Int = 5
                    }

                    fun test(foo: Foo, bar: Bar, buffer: java.nio.ByteBuffer, node: org.w3c.dom.Node, opt: java.util.Optional<String>) {
                        val x = foo.b // OK 1
                        foo.b // WARN 1
                        bar.bar // WARN 2
                        bar.text // WARN 3
                        bar.getText() // WARN 4
                        bar.getFoo() // WARN 5
                        bar.getFoo2("") // OK 2
                        bar.getFoo3() // OK 3
                        bar.getFoo4() // OK 4
                        bar.getFoo5() // WARN 6
                        //noinspection ResultOfMethodCallIgnored
                        bar.getFoo5() // OK 5
                        bar.getBar() // WARN 7
                        "".toString() // WARN 8
                        java.io.File("").path // WARN 9
                        java.io.File("").getPath() // WARN 10
                        buffer.getShort() // OK 6
                        buffer.short // OK 7
                        synchronized (node.getOwnerDocument()) { // OK 8
                        }
                        bar.getPreferredSize() // OK 9
                        bar.computeRange() // WARN 11
                        bar.computeRange2() //OK 10
                        bar.getFoo5().not() // WARN 12
                        if (foo.a > foo.b) foo.a else foo.b // WARN 13 a and b
                        val max = if (foo.a > foo.b) foo.a else foo.b // OK 12
                        // In the future, consider including this but now used for lots of side effect methods
                        // like future computations etc
                        opt.get()

                        // In an earlier version we included
                        //   methodName == "build" && method.containingClass?.name?.contains("Builder") == true
                        // as well -- but this had a number of false positives; build in many usages have side
                        // effects - they don't just return the result.
                        Gradle().build() // OK 13
                        MyBuilder().build() // consider flagging
                    }
                    class Gradle { fun build(): String = "done" }
                    class MyBuilder { fun build(): String = "done"}
                """
          )
          .indented(),
        java(
            """
                    package test.pkg;
                    public class Bar {
                        public String getBar() { return "hello"; }
                        public String getText() { return getBar(); }
                        public String getFoo() { return field; }
                        public String getFoo2(String s) { return field; }
                        public void getFoo3() { field = "world"; }
                        public int getFoo4() { return field2++; }
                        public boolean getFoo5() { return !field3; }
                        public boolean getFoo6() { return !field3; }
                        private String field;
                        private int field2 = 0;
                        private boolean field3 = false;
                        public int getPreferredSize() { return null; }
                        @org.jetbrains.annotations.Contract(pure=true)
                        public int computeRange() { return 0; }
                        @org.jetbrains.annotations.Contract(pure=false)
                        public int computeRange2() { return 0; }
                    }
                    """
          )
          .indented(),
        kotlin(
            """
                    package com.android.tools.idea.imports
                    class MavenClassRegistryManager {
                      companion object {
                        private var foo: Int = 10
                        @JvmStatic
                        fun getInstance(): Any = foo
                      }
                    }
                    class AutoRefresherForMavenClassRegistry {
                      override fun runActivity() {
                        // Start refresher
                        MavenClassRegistryManager.getInstance()
                      }
                    }
                    """
          )
          .indented(),
        kotlin(
            """
                    package com.android.tools.lint.checks.infrastructure
                    import org.junit.Test
                    class GradleModelMockerTest {
                        @Test(expected = AssertionError::class)
                        fun testFailOnUnexpected() {
                            val mocker = Mocker()
                            mocker.getLintModule()
                        }
                    }
                    class Mocker {
                        private val module: String = "test"
                        fun getLintModule(): String {
                            return module
                        }
                    }
                    """
          )
          .indented(),
        java(
            """
                    import java.net.HttpURLConnection;
                    public class TestStreams {
                        public void test(HttpURLConnection urlConnection) throws Exception {
                            urlConnection.getInputStream(); // has side effect, so is not a no-op (shouldn't be flagged)
                        }
                    }
                    """
          )
          .indented(),
        java(
            """
                    package com.android.annotations;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    public @interface VisibleForTesting {
                        enum Visibility { PROTECTED, PACKAGE, PRIVATE }
                        Visibility visibility() default Visibility.PRIVATE;
                    }
                    """
          )
          .indented(),
        java(
            """
                    package org.junit;
                    public @interface Test {
                        Class<? extends Throwable> expected() default None.class;
                        long timeout() default 0L;
                    }
                    """
          )
          .indented(),
        java(
            """
                    package org.jetbrains.annotations;
                    import java.lang.annotation.*;
                    public @interface Contract {
                      String value() default "";
                      boolean pure() default false;
                    }
                    """
          )
          .indented(),
      )
      .configureOption(NoOpDetector.ASSUME_PURE_GETTERS, true)
      .run()
      .expect(
        """
                src/test/pkg/Foo.kt:9: Warning: This reference is unused: b [NoOp]
                    foo.b // WARN 1
                        ~
                src/test/pkg/Foo.kt:10: Warning: This reference is unused: bar [NoOp]
                    bar.bar // WARN 2
                        ~~~
                src/test/pkg/Foo.kt:11: Warning: This reference is unused: text [NoOp]
                    bar.text // WARN 3
                        ~~~~
                src/test/pkg/Foo.kt:12: Warning: This call result is unused: getText [NoOp]
                    bar.getText() // WARN 4
                    ~~~~~~~~~~~~~
                src/test/pkg/Foo.kt:13: Warning: This call result is unused: getFoo [NoOp]
                    bar.getFoo() // WARN 5
                    ~~~~~~~~~~~~
                src/test/pkg/Foo.kt:17: Warning: This call result is unused: getFoo5 [NoOp]
                    bar.getFoo5() // WARN 6
                    ~~~~~~~~~~~~~
                src/test/pkg/Foo.kt:20: Warning: This call result is unused: getBar [NoOp]
                    bar.getBar() // WARN 7
                    ~~~~~~~~~~~~
                src/test/pkg/Foo.kt:21: Warning: This call result is unused: toString [NoOp]
                    "".toString() // WARN 8
                    ~~~~~~~~~~~~~
                src/test/pkg/Foo.kt:22: Warning: This reference is unused: path [NoOp]
                    java.io.File("").path // WARN 9
                                     ~~~~
                src/test/pkg/Foo.kt:23: Warning: This call result is unused: getPath [NoOp]
                    java.io.File("").getPath() // WARN 10
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/Foo.kt:29: Warning: This call result is unused: computeRange [NoOp]
                    bar.computeRange() // WARN 11
                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/Foo.kt:31: Warning: This call result is unused: not [NoOp]
                    bar.getFoo5().not() // WARN 12
                    ~~~~~~~~~~~~~~~~~~~
                src/test/pkg/Foo.kt:32: Warning: This reference is unused: a [NoOp]
                    if (foo.a > foo.b) foo.a else foo.b // WARN 13 a and b
                                           ~
                src/test/pkg/Foo.kt:32: Warning: This reference is unused: b [NoOp]
                    if (foo.a > foo.b) foo.a else foo.b // WARN 13 a and b
                                                      ~
                0 errors, 14 warnings
                """
      )
  }

  @Suppress("StringOperationCanBeSimplified", "ResultOfMethodCallIgnored")
  fun testStringMethods() {
    lint()
      .files(
        kotlin(
            """
                class Test {
                    fun test(s: String, o: Any) {
                       o.toString()           // ERROR 1
                       s.trim()               // ERROR 2
                       s.subSequence(0)       // ERROR 3
                       s.length               // ERROR 4
                    }
                }
                """
          )
          .indented(),
        java(
            """
                    import java.util.Locale;
                    class Test2 {
                        void test(String s) {
                            s.toString();                // ERROR 5
                            s.toLowerCase();             // ERROR 6
                            s.toLowerCase(Locale.ROOT);  // ERROR 7
                            s.length();                  // ERROR 8
                        }

                        public void testStringCopy(String prefix, String relativeResourcePath) {
                            int prefixLength = prefix.length();
                            int pathLength = relativeResourcePath.length();
                            char[] result = new char[prefixLength + pathLength];
                            prefix.getChars(0, prefixLength, result, 0); // OK 1 -- not a getter
                            relativeResourcePath.getChars(0, pathLength, result, prefixLength); // OK 2
                        }
                    }
                """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                src/Test.kt:3: Warning: This call result is unused: toString [NoOp]
                       o.toString()           // ERROR 1
                       ~~~~~~~~~~~~
                src/Test.kt:4: Warning: This call result is unused: trim [NoOp]
                       s.trim()               // ERROR 2
                       ~~~~~~~~
                src/Test.kt:5: Warning: This call result is unused: subSequence [NoOp]
                       s.subSequence(0)       // ERROR 3
                       ~~~~~~~~~~~~~~~~
                src/Test.kt:6: Warning: This reference is unused: length [NoOp]
                       s.length               // ERROR 4
                         ~~~~~~
                src/Test2.java:4: Warning: This call result is unused: toString [NoOp]
                        s.toString();                // ERROR 5
                        ~~~~~~~~~~~~
                src/Test2.java:5: Warning: This call result is unused: toLowerCase [NoOp]
                        s.toLowerCase();             // ERROR 6
                        ~~~~~~~~~~~~~~~
                src/Test2.java:6: Warning: This call result is unused: toLowerCase [NoOp]
                        s.toLowerCase(Locale.ROOT);  // ERROR 7
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/Test2.java:7: Warning: This call result is unused: length [NoOp]
                        s.length();                  // ERROR 8
                        ~~~~~~~~~~
                0 errors, 8 warnings
                """
      )
  }

  @Suppress("RemoveRedundantCallsOfConversionMethods")
  fun testBuiltinImmutables() {
    lint()
      .files(
        kotlin(
            """
                class Test {
                    fun test(s: String, o: Any) {
                        Integer.valueOf("5")                       // ERROR 1
                        java.lang.Boolean.valueOf("true")          // ERROR 2
                        java.lang.Integer.toHexString(5)           // ERROR 3
                        java.lang.Float.compare(1f, 2f)            // ERROR 4

                        java.lang.Integer.valueOf("5").inc()       // ERROR 5
                        3.toInt()                                  // ERROR 6
                        true.not()                                 // ERROR 7
                        3f.toLong()                                // ERROR 8
                    }
                    fun test(b: Boolean, i: Int) {
                        b.or(b)     // ERROR 9
                        i.and(i)    // ERROR 10
                    }
                }
                """
          )
          .indented()
      )
      .run()
      .expect(
        """
                src/Test.kt:3: Warning: This call result is unused: valueOf [NoOp]
                        Integer.valueOf("5")                       // ERROR 1
                        ~~~~~~~~~~~~~~~~~~~~
                src/Test.kt:4: Warning: This call result is unused: valueOf [NoOp]
                        java.lang.Boolean.valueOf("true")          // ERROR 2
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/Test.kt:5: Warning: This call result is unused: toHexString [NoOp]
                        java.lang.Integer.toHexString(5)           // ERROR 3
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/Test.kt:6: Warning: This call result is unused: compare [NoOp]
                        java.lang.Float.compare(1f, 2f)            // ERROR 4
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/Test.kt:8: Warning: This call result is unused: inc [NoOp]
                        java.lang.Integer.valueOf("5").inc()       // ERROR 5
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/Test.kt:9: Warning: This call result is unused: toInt [NoOp]
                        3.toInt()                                  // ERROR 6
                        ~~~~~~~~~
                src/Test.kt:10: Warning: This call result is unused: not [NoOp]
                        true.not()                                 // ERROR 7
                        ~~~~~~~~~~
                src/Test.kt:11: Warning: This call result is unused: toLong [NoOp]
                        3f.toLong()                                // ERROR 8
                        ~~~~~~~~~~~
                src/Test.kt:14: Warning: This call result is unused: or [NoOp]
                        b.or(b)     // ERROR 9
                        ~~~~~~~
                src/Test.kt:15: Warning: This call result is unused: and [NoOp]
                        i.and(i)    // ERROR 10
                        ~~~~~~~~
                0 errors, 10 warnings
                """
      )
  }

  fun testLiterals() {
    lint()
      .files(
        kotlin(
            """
                class Test {
                    fun test(s: String, o: Any) {
                        true
                        1
                        "test"
                    }
                }
                """
          )
          .indented()
      )
      .skipTestModes(TestMode.UI_INJECTION_HOST)
      .run()
      .expect(
        """
                src/Test.kt:3: Warning: This reference is unused: true [NoOp]
                        true
                        ~~~~
                src/Test.kt:4: Warning: This reference is unused: 1 [NoOp]
                        1
                        ~
                src/Test.kt:5: Warning: This reference is unused: test [NoOp]
                        "test"
                         ~~~~
                0 errors, 3 warnings
                """
      )
  }

  @Suppress("SimplifyBooleanWithConstants")
  fun testBinaryOperators() {
    lint()
      .files(
        kotlin(
            """
                class Test {
                    fun test(s: String, o: Any) {
                       s === o                // ERROR 1
                       1 + 2                  // ERROR 2
                       x = 0                  // OK 1
                       test() || test()       // OK 2
                       pure() || true         // ERROR 3
                    }
                    private var x = -1
                    fun test(): Boolean { println("side effect") }
                    fun pure(): Boolean { return true }
                }
                """
          )
          .indented()
      )
      .run()
      .expect(
        """
                src/Test.kt:3: Warning: This reference is unused: s === o [NoOp]
                       s === o                // ERROR 1
                       ~~~~~~~
                src/Test.kt:4: Warning: This reference is unused: 1 + 2 [NoOp]
                       1 + 2                  // ERROR 2
                       ~~~~~
                src/Test.kt:7: Warning: This reference is unused: pure() || true [NoOp]
                       pure() || true         // ERROR 3
                       ~~~~~~~~~~~~~~
                0 errors, 3 warnings
                """
      )
  }

  fun testDeliberateThrow1() {
    // Based on the no-op scenario `OtherOperationType.valueOf` call in
    // com.android.manifmerger.XmlElement
    lint()
      .files(
        java(
            """
                import java.util.Locale;

                public class EnumSideEffect {
                    enum Test { A, B }
                    void test(String[] names) {
                        for (String name : names) {
                            try {
                                Test.valueOf(name);
                                continue;
                            } catch (IllegalArgumentException e) {
                                e.printStackTrace();
                            }
                            System.out.println("test");
                        }
                    }
                }
                """
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testDeliberateThrow2() {
    // Based on the no-op scenario `OtherOperationType.valueOf` call in
    // com.android.manifmerger.XmlElement
    lint()
      .files(
        java(
            """
                package org.junit;

                public class Assert {
                    public static void fail(String message) {
                        throw new AssertionError(message);
                    }
                }
                """
          )
          .indented(),
        kotlin(
          """
                import org.junit.Assert

                class Test : Assert() {
                    lateinit var clientName: String
                    fun test() {
                        try {
                            clientName
                            fail("Expected accessing client name before initialization to fail")
                        } catch (t: UninitializedPropertyAccessException) {
                            // pass
                        }
                    }
                }
                """
        ),
      )
      .run()
      .expectClean()
  }

  fun testQualifiedReferences() {
    lint()
      .files(
        kotlin(
            """
                class Node {
                    lateinit var next: Node
                    override val prev: Node
                      get() = TODO()
                }
                fun test(node: Node) {
                    node                       // ERROR 1
                    node.next                  // ERROR 2
                    node.next.next.next.next   // ERROR 3
                    node.next.prev.next        // ERROR 4 (prev may have side effect, but not last next)
                    node.prev                  // OK 1
                }
                """
          )
          .indented()
      )
      .configureOption(NoOpDetector.ASSUME_PURE_GETTERS, true)
      .run()
      .expect(
        """
                src/Node.kt:7: Warning: This reference is unused: node [NoOp]
                    node                       // ERROR 1
                    ~~~~
                src/Node.kt:8: Warning: This reference is unused: next [NoOp]
                    node.next                  // ERROR 2
                         ~~~~
                src/Node.kt:9: Warning: This reference is unused: next [NoOp]
                    node.next.next.next.next   // ERROR 3
                                        ~~~~
                src/Node.kt:10: Warning: This reference is unused: next [NoOp]
                    node.next.prev.next        // ERROR 4 (prev may have side effect, but not last next)
                                   ~~~~
                0 errors, 4 warnings
                """
      )
  }

  fun testNoGettersWithOptionOff() {
    lint()
      .files(
        kotlin(
            """
                class Node {
                    lateinit var next: Node
                }
                fun test(node: Node) {
                    node.next
                }
                """
          )
          .indented()
      )
      .configureOption(NoOpDetector.ASSUME_PURE_GETTERS, false)
      .run()
      .expectClean()
  }

  fun testPropertyAccessOfJavaMethod() {
    lint()
      .files(
        java(
            """
                public abstract class Parent {
                    public int getChildCount() {
                        throw new UnsupportedOperationException();
                    }
                }
                """
          )
          .indented(),
        kotlin(
            """
                class Child : Parent() {
                    fun expandNode() {
                        childCount
                    }
                }
                """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  @Suppress("RedundantUnitExpression")
  fun testRedundantUnit() {
    // In many cases there are explicit "Unit" expressions at the end of a lambda etc;
    // these are often redundant (and IntelliJ already flags them as such), so we'll
    // consider these deliberate choices to be explicit about the return value rather
    // than redundant constructs.
    lint()
      .files(
        kotlin(
          """
                package com.example.myapplication

                fun onIssuesChange(parentDisposable: Any?, listener: () -> Unit) {
                }

                class Test {
                    fun test() {
                        onIssuesChange(this) {
                            Unit
                        }
                    }
                }
                """
        )
      )
      .run()
      .expectClean()
  }

  @Suppress("CatchMayIgnoreException")
  fun testCanonicalize() {
    // First, should skip because try/catch.
    // Second, should skip because method throws exceptions!
    lint()
      .files(
        kotlin(
            """
                import java.io.File
                import java.lang.RuntimeException

                fun check(file: File) {
                    try {
                        file.parentFile
                    } catch (e: RuntimeException) {
                    }
                }
                """
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testThrowsException() {
    lint()
      .files(
        kotlin(
            """
                import java.io.File
                fun checkCanonicalize(file: File) {
                    // If a method throws exceptions, we may be relying on side effects
                    file.canonicalFile
                }
                """
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testUnresolvedLambda() {
    // Regression test for b/254674801
    lint()
      .files(
        kotlin(
            """
                fun test() {
                    "test".someFilter { it == 'a' }
                }
                """
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testAtomicIntegers() {
    lint()
      .files(
        java(
            """
                import java.util.concurrent.atomic.AtomicInteger;
                import java.util.concurrent.atomic.AtomicLong;

                public class JavaTest {
                    public void testIntegers(AtomicInteger i, AtomicLong l) {
                        l.getAndIncrement(); // OK 1
                        i.getAndIncrement(); // OK 2
                    }
                }
                """
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testStaticFieldInitialization() {
    // Regression test for b/232719934
    lint()
      .files(
        kotlin(
            """
                open class UastBinaryExpressionWithTypeKind(val name: String) {
                    companion object {
                        @JvmField
                        val UNKNOWN = UastBinaryExpressionWithTypeKind("<unknown>")
                    }
                }

                class ApiDetectorKotlin {
                    init {
                        UastBinaryExpressionWithTypeKind.UNKNOWN // trigger UastBinaryExpressionWithTypeKind.<clinit>
                    }
                }
                """
          )
          .indented(),
        java(
            """
                class ApiDetectorJava {
                    static {
                        UastBinaryExpressionWithTypeKind.UNKNOWN.getName(); // trigger UastBinaryExpressionWithTypeKind.<clinit>
                    }
                }
                """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testObjectInitialization() {
    lint()
      .files(
        kotlin(
            """
                fun connectUiAutomation(init: Boolean) {
                    if (init) {
                        ShellImpl // force initialization
                    }
                }

                private object ShellImpl
                """
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  @Suppress("CatchMayIgnoreException")
  fun testTryParse() {
    lint()
      .files(
        java(
            """
                class TryParse {
                    public void test(String s) {
                        try {
                            Integer.parseInt(s.substring(4), 16);
                            return s;
                        } catch (NumberFormatException e) {}

                        try {
                            Double.parseDouble(entryValue);
                            return entryValue;
                        } catch (NumberFormatException e) {
                        }

                        String[] parts = s.split(",");
                        if (parts.length == 2) {
                            try {
                                // Ensure both parts are doubles.
                                Double.parseDouble(parts[0]);
                                Double.parseDouble(parts[1]);
                                return true;
                            } catch (NumberFormatException e) {
                                // Values are not Doubles.
                            }
                        }
                    }
                }
                """
          )
          .indented(),
        kotlin(
          """
                internal fun parseIntValue(value: String): IntValue? {
                    try {
                        if (value.startsWith("0x")) {
                            Integer.parseUnsignedInt(value.substring(2), 16)
                        } else {
                            Integer.parseInt(value)
                        }
                    } catch (ex: NumberFormatException) {
                        return null
                    }
                    return IntValue(value)
                }
                """
        ),
      )
      .run()
      .expectClean()
  }

  fun testMutableStateOf() {
    lint()
      .files(
        kotlin(
            """
                package androidx.compose.runtime
                interface State<out T> {
                    val value: T
                }
                interface MutableState<T> : State<T> {
                    override var value: T
                    operator fun component1(): T
                    operator fun component2(): (T) -> Unit
                }
                """
          )
          .indented(),
        kotlin(
            """
                import androidx.compose.runtime.MutableState
                fun test(redrawSignal: MutableState<Unit>) {
                    redrawSignal.value // <-- value read to redraw if needed
                }
                """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  @Suppress("IntroduceWhenSubject")
  fun testElseReturn() {
    lint()
      .files(
        kotlin(
            """
                fun test(s: String, b: Boolean): Int {
                    when {
                        s == "1" -> return 1
                        s == "2" -> b || return 0
                    }
                    return -1
                }
                """
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testSuper() {
    // Deliberately ignoring explicit super calls
    lint()
      .files(
        java(
            """
                package test.pkg;
                public class Parent {
                    public Parent() {
                    }
                    public String getFoo() { return "foo"; }
                }
                """
          )
          .indented(),
        java(
            """
                package test.pkg;
                public class Child extends Parent {
                    public Child() {
                    }
                    public String getFoo() {
                        super.getFoo();
                    }
                }
                """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testLazyInitialization() {
    lint()
      .files(
        kotlin(
            """
                import java.util.logging.Level
                import java.util.logging.Logger

                private val initLog by lazy {
                    val root = Logger.getLogger("")
                    if (root.handlers.isEmpty()) {
                        root.level = Level.INFO
                    }
                }

                private fun doSomething() {
                    initLog
                }
                """
          )
          .indented()
      )
      .run()
      .expectClean()
  }
}
