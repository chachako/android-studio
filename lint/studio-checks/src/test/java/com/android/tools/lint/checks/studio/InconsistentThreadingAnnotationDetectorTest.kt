/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles
import org.junit.Test

@Suppress("ClassNameDiffersFromFileName") // For language injections.
class InconsistentThreadingAnnotationDetectorTest {
  companion object {
    private val uiThreadFile: TestFile =
      TestFiles.java(
          """
                    package com.android.annotations.concurrency;

                    import java.lang.annotation.Documented;
                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.lang.annotation.Target;

                    @Documented
                    @Retention(RetentionPolicy.RUNTIME)
                    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE})
                    public @interface UiThread {}
                """
        )
        .indented()

    private val anyThreadFile: TestFile =
      TestFiles.java(
          """
                    package com.android.annotations.concurrency;

                    import java.lang.annotation.Documented;
                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.lang.annotation.Target;

                    @Documented
                    @Retention(RetentionPolicy.RUNTIME)
                    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE})
                    public @interface AnyThread {}
                """
        )
        .indented()

    private val slowThreadFile: TestFile =
      TestFiles.java(
          """
                    package com.android.annotations.concurrency;

                    import java.lang.annotation.Documented;
                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.lang.annotation.Target;

                    @Documented
                    @Retention(RetentionPolicy.RUNTIME)
                    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE})
                    public @interface Slow {}
                """
        )
        .indented()

    private val workerThreadFile: TestFile =
      TestFiles.java(
          """
                    package com.android.annotations.concurrency;

                    import java.lang.annotation.Documented;
                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.lang.annotation.Target;

                    @Documented
                    @Retention(RetentionPolicy.RUNTIME)
                    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE})
                    public @interface WorkerThread {}
                """
        )
        .indented()
  }

  @Test
  fun testMethodLevelAnnotationsJava() {
    studioLint()
      .files(
        anyThreadFile,
        slowThreadFile,
        uiThreadFile,
        workerThreadFile,
        TestFiles.java(
            """
                    package test.pkg;
                    import com.android.annotations.concurrency.WorkerThread;
                    import com.android.annotations.concurrency.UiThread;

                    public interface TestInterface {
                        @UiThread
                        void uiMethod1();

                        @UiThread
                        void uiMethod2();

                        @WorkerThread
                        void workerMethod1();
                    }
                """
          )
          .indented(),
        TestFiles.java(
            """
                    package test.pkg;
                    import com.android.annotations.concurrency.UiThread;

                    public class TestInterfaceImpl implements TestInterface {
                        @UiThread // OK
                        public void annotatedNonInterfaceMethod() {}

                        public void notAnnotatedNonInterfaceMethod() {}

                        @UiThread // OK - consistent with interface
                        @Override
                        public void uiMethod1() {}

                        @Override // WARN - interface method has annotation
                        public void uiMethod2() {}

                        @UiThread // WARN - interface has a different annotation
                        @Override
                        public void workerMethod1() {}
                    }
                """
          )
          .indented()
      )
      .issues(InconsistentThreadingAnnotationDetector.ISSUE)
      .run()
      .expect(
        """
                src/test/pkg/TestInterfaceImpl.java:15: Error: Overridden method needs to have a threading annotation matching the super method's annotation com.android.annotations.concurrency.UiThread [InconsistentThreadingAnnotation]
                    public void uiMethod2() {}
                                ~~~~~~~~~
                src/test/pkg/TestInterfaceImpl.java:19: Error: Method annotation com.android.annotations.concurrency.UiThread doesn't match the super method's annotation com.android.annotations.concurrency.WorkerThread [InconsistentThreadingAnnotation]
                    public void workerMethod1() {}
                                ~~~~~~~~~~~~~
                2 errors, 0 warnings
                """
      )
  }

  @Test
  fun testMethodLevelAnnotationsKotlin() {
    studioLint()
      .files(
        anyThreadFile,
        slowThreadFile,
        uiThreadFile,
        workerThreadFile,
        TestFiles.java(
            """
                    package test.pkg;
                    import com.android.annotations.concurrency.WorkerThread;
                    import com.android.annotations.concurrency.UiThread;

                    public interface TestInterface {
                        @UiThread
                        void uiMethod1();

                        @UiThread
                        void uiMethod2();

                        @WorkerThread
                        void workerMethod1();
                    }
                """
          )
          .indented(),
        TestFiles.kotlin(
            """
                    package test.pkg
                    import com.android.annotations.concurrency.UiThread

                    class TestInterfaceImpl : TestInterface {
                        @UiThread // OK
                        fun annotatedNonInterfaceMethod() {}

                        fun notAnnotatedNonInterfaceMethod() {}

                        @UiThread // OK - consistent with interface
                        override fun uiMethod1() {}

                        // WARN - interface method has annotation
                        override fun uiMethod2() {}

                        @UiThread // WARN - interface has a different annotation
                        override fun workerMethod1() {}
                    }
                """
          )
          .indented()
      )
      .issues(InconsistentThreadingAnnotationDetector.ISSUE)
      .run()
      .expect(
        """
                src/test/pkg/TestInterfaceImpl.kt:14: Error: Overridden method needs to have a threading annotation matching the super method's annotation com.android.annotations.concurrency.UiThread [InconsistentThreadingAnnotation]
                    override fun uiMethod2() {}
                                 ~~~~~~~~~
                src/test/pkg/TestInterfaceImpl.kt:17: Error: Method annotation com.android.annotations.concurrency.UiThread doesn't match the super method's annotation com.android.annotations.concurrency.WorkerThread [InconsistentThreadingAnnotation]
                    override fun workerMethod1() {}
                                 ~~~~~~~~~~~~~
                2 errors, 0 warnings
                """
      )
  }

  @Test
  fun testTypeLevelAnnotations() {
    studioLint()
      .files(
        anyThreadFile,
        slowThreadFile,
        uiThreadFile,
        workerThreadFile,
        TestFiles.java(
            """
                    package test.pkg;
                    import com.android.annotations.concurrency.WorkerThread;
                    import com.android.annotations.concurrency.UiThread;

                    @UiThread
                    public interface TestInterface {
                        void uiMethod1();

                        void uiMethod2();
                    }
                """
          )
          .indented(),
        TestFiles.java(
            """
                    package test.pkg;
                    import com.android.annotations.concurrency.UiThread;

                    public class TestInterfaceImpl1 implements TestInterface {
                        @UiThread // OK - consistent with interface
                        @Override
                        public void uiMethod1() {}

                        @Override // WARN - interface method has annotation
                        public void uiMethod2() {}
                    }
                """
          )
          .indented(),
        TestFiles.java(
            """
                    package test.pkg;
                    import com.android.annotations.concurrency.UiThread;
                    import com.android.annotations.concurrency.WorkerThread;

                    @UiThread
                    public class TestInterfaceImpl2 implements TestInterface {
                        // OK - class level annotation is consistent with interface's annotation
                        @Override
                        public void uiMethod1() {}

                        @WorkerThread // WARN - annotation inconsistent with the interface annotation
                        @Override
                        public void uiMethod2() {}
                    }
                """
          )
          .indented()
      )
      .issues(InconsistentThreadingAnnotationDetector.ISSUE)
      .run()
      .expect(
        """
                src/test/pkg/TestInterfaceImpl1.java:10: Error: Overridden method needs to have a threading annotation matching the super method's annotation com.android.annotations.concurrency.UiThread [InconsistentThreadingAnnotation]
                    public void uiMethod2() {}
                                ~~~~~~~~~
                src/test/pkg/TestInterfaceImpl2.java:13: Error: Method annotation com.android.annotations.concurrency.WorkerThread doesn't match the super method's annotation com.android.annotations.concurrency.UiThread [InconsistentThreadingAnnotation]
                    public void uiMethod2() {}
                                ~~~~~~~~~
                2 errors, 0 warnings
                """
      )
  }

  @Test
  fun testNestedTypeDoesNotInheritAnnotations() {
    studioLint()
      .files(
        anyThreadFile,
        slowThreadFile,
        uiThreadFile,
        workerThreadFile,
        TestFiles.java(
            """
                    package test.pkg;
                    import com.android.annotations.concurrency.WorkerThread;
                    import com.android.annotations.concurrency.UiThread;

                    @UiThread
                    public class TestInterfaceContainer {
                        public interface TestInterface {
                            void uiMethod1();
                        }
                    }
                """
          )
          .indented(),
        TestFiles.java(
            """
                    package test.pkg;

                    public class TestInterfaceImpl1 implements TestInterfaceContainer.TestInterface {
                        // OK - interface does not set annotations
                        @Override
                        public void uiMethod1() {}
                    }
                """
          )
          .indented(),
        TestFiles.java(
            """
                    package test.pkg;
                    import com.android.annotations.concurrency.UiThread;
                    import com.android.annotations.concurrency.WorkerThread;

                    @UiThread
                    public class TestImplContainer {
                        public class TestInterfaceImpl2 implements TestInterfaceContainer.TestInterface {
                            // OK - there are no annotations neither on the inner class nor the interface
                            @Override
                            public void uiMethod1() {}
                        }
                    }
                """
          )
          .indented()
      )
      .issues(InconsistentThreadingAnnotationDetector.ISSUE)
      .run()
      .expect("No warnings.")
  }
}
