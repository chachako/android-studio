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
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

class RegisterReceiverFlagDetectorTest : AbstractCheckTest() {

  override fun getDetector(): Detector = RegisterReceiverFlagDetector()

  override fun getIssues(): List<Issue> =
    listOf(RegisterReceiverFlagDetector.RECEIVER_EXPORTED_FLAG)

  fun testProtectedBroadcast() {
    lint()
      .files(
        java(
            """
                    package test.pkg;
                    import android.content.BroadcastReceiver;
                    import android.content.Context;
                    import android.content.Intent;
                    import android.content.IntentFilter;
                    public class TestClass1 {
                        public void testMethod(Context context, BroadcastReceiver receiver) {
                            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                            context.registerReceiver(receiver, filter);
                        }
                    }
                   """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testProtectedBroadcastCreate() {
    lint()
      .files(
        java(
            """
                    package test.pkg;
                    import android.content.BroadcastReceiver;
                    import android.content.Context;
                    import android.content.Intent;
                    import android.content.IntentFilter;
                    public class TestClass1 {
                        public void testMethod(Context context, BroadcastReceiver receiver) {
                            IntentFilter filter =
                                    IntentFilter.create(Intent.ACTION_BATTERY_CHANGED, "foo/bar");
                            context.registerReceiver(receiver, filter);
                        }
                    }
                   """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testMultipleProtectedBroadcasts() {
    lint()
      .files(
        java(
            """
                    package test.pkg;
                    import android.content.BroadcastReceiver;
                    import android.content.Context;
                    import android.content.Intent;
                    import android.content.IntentFilter;
                    public class TestClass1 {
                        public void testMethod(Context context, BroadcastReceiver receiver) {
                            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                            filter.addAction(Intent.ACTION_BATTERY_LOW);
                            filter.addAction(Intent.ACTION_BATTERY_OKAY);
                            context.registerReceiver(receiver, filter);
                        }
                    }
                   """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testSubsequentFilterModification() {
    lint()
      .files(
        java(
            """
                    package test.pkg;
                    import android.content.BroadcastReceiver;
                    import android.content.Context;
                    import android.content.Intent;
                    import android.content.IntentFilter;
                    public class TestClass1 {
                        public void testMethod(Context context, BroadcastReceiver receiver) {
                            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                            filter.addAction(Intent.ACTION_BATTERY_LOW);
                            filter.addAction(Intent.ACTION_BATTERY_OKAY);
                            context.registerReceiver(receiver, filter);
                            filter.addAction("querty");
                            context.registerReceiver(receiver, filter);
                        }
                    }
                   """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                src/test/pkg/TestClass1.java:13: Warning: receiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for querty [UnspecifiedRegisterReceiverFlag]
                        context.registerReceiver(receiver, filter);
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
      )
  }

  fun testNullReceiver() {
    lint()
      .files(
        java(
            """
                    package test.pkg;
                    import android.content.BroadcastReceiver;
                    import android.content.Context;
                    import android.content.Intent;
                    import android.content.IntentFilter;
                    public class TestClass1 {
                        public void testMethod(Context context) {
                            IntentFilter filter = new IntentFilter("qwerty");
                            context.registerReceiver(null, filter);
                        }
                    }
                   """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testExportedFlagPresent() {
    lint()
      .files(
        java(
            """
                    package test.pkg;
                    import android.content.BroadcastReceiver;
                    import android.content.Context;
                    import android.content.Intent;
                    import android.content.IntentFilter;
                    public class TestClass1 {
                        public void testMethod(Context context, BroadcastReceiver receiver) {
                            IntentFilter filter = new IntentFilter("qwerty");
                            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
                        }
                    }
                   """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testNotExportedFlagPresent() {
    lint()
      .files(
        java(
            """
                    package test.pkg;
                    import android.content.BroadcastReceiver;
                    import android.content.Context;
                    import android.content.Intent;
                    import android.content.IntentFilter;
                    public class TestClass1 {
                        public void testMethod(Context context, BroadcastReceiver receiver) {
                            IntentFilter filter = new IntentFilter("qwerty");
                            context.registerReceiver(receiver, filter,
                                    Context.RECEIVER_NOT_EXPORTED);
                        }
                    }
                   """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testDocumentationExampleFlagArgumentAbsent() {
    lint()
      .files(
        java(
            """
                    package test.pkg;
                    import android.content.BroadcastReceiver;
                    import android.content.Context;
                    import android.content.Intent;
                    import android.content.IntentFilter;
                    public class TestClass1 {
                        public void testMethod(Context context, BroadcastReceiver receiver) {
                            IntentFilter filter = new IntentFilter("qwerty");
                            context.registerReceiver(receiver, filter);
                        }
                    }
                   """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                src/test/pkg/TestClass1.java:9: Warning: receiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for qwerty [UnspecifiedRegisterReceiverFlag]
                        context.registerReceiver(receiver, filter);
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
      )
  }

  fun testExportedFlagsAbsent() {
    lint()
      .files(
        java(
            """
                    package test.pkg;
                    import android.content.BroadcastReceiver;
                    import android.content.Context;
                    import android.content.Intent;
                    import android.content.IntentFilter;
                    public class TestClass1 {
                        public void testMethod(Context context, BroadcastReceiver receiver) {
                            IntentFilter filter = new IntentFilter("qwerty");
                            context.registerReceiver(receiver, filter, 0);
                        }
                    }
                   """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                    src/test/pkg/TestClass1.java:9: Warning: receiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for qwerty [UnspecifiedRegisterReceiverFlag]
                            context.registerReceiver(receiver, filter, 0);
                                                                       ~
                    0 errors, 1 warnings
                """
      )
  }

  fun testExportedFlagVariable() {
    lint()
      .files(
        java(
            """
                    package test.pkg;
                    import android.content.BroadcastReceiver;
                    import android.content.Context;
                    import android.content.Intent;
                    import android.content.IntentFilter;
                    public class TestClass1 {
                        public void testMethod(Context context, BroadcastReceiver receiver) {
                            IntentFilter filter = new IntentFilter("qwerty");
                            var flags = Context.RECEIVER_EXPORTED;
                            context.registerReceiver(receiver, filter, flags);
                        }
                    }
                   """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testUnknownFilter() {
    lint()
      .files(
        java(
            """
                    package test.pkg;
                    import android.content.BroadcastReceiver;
                    import android.content.Context;
                    import android.content.Intent;
                    import android.content.IntentFilter;
                    public class TestClass1 {
                        public void testMethod(Context context, BroadcastReceiver receiver,
                                IntentFilter filter) {
                            context.registerReceiver(receiver, filter);
                        }
                    }
                   """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                    src/test/pkg/TestClass1.java:9: Warning: receiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for an IntentFilter that cannot be inspected by lint [UnspecifiedRegisterReceiverFlag]
                            context.registerReceiver(receiver, filter);
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    0 errors, 1 warnings
                """
      )
  }

  fun testFilterEscapes() {
    lint()
      .files(
        java(
            """
                    package test.pkg;
                    import android.content.BroadcastReceiver;
                    import android.content.Context;
                    import android.content.Intent;
                    import android.content.IntentFilter;
                    public class TestClass1 {
                        public void testMethod(Context context, BroadcastReceiver receiver) {
                            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                            updateFilter(filter);
                            context.registerReceiver(receiver, filter);
                        }
                    }
                   """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                src/test/pkg/TestClass1.java:10: Warning: receiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for an IntentFilter that cannot be inspected by lint [UnspecifiedRegisterReceiverFlag]
                        context.registerReceiver(receiver, filter);
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
      )
  }

  fun testInlineFilter() {
    lint()
      .files(
        java(
            """
                    package test.pkg;
                    import android.content.BroadcastReceiver;
                    import android.content.Context;
                    import android.content.Intent;
                    import android.content.IntentFilter;
                    public class TestClass1 {
                        public void testMethod(Context context, BroadcastReceiver receiver) {
                            context.registerReceiver(receiver,
                                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                        }
                    }
                   """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testInlineFilterApply() {
    lint()
      .files(
        kotlin(
            """
                    package test.pkg
                    import android.content.BroadcastReceiver
                    import android.content.Context
                    import android.content.Intent
                    import android.content.IntentFilter
                    class TestClass1 {
                        fun test(context: Context, receiver: BroadcastReceiver) {
                            context.registerReceiver(receiver,
                                    IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply {
                                        addAction("qwerty")
                                    })
                        }
                    }
                   """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                src/test/pkg/TestClass1.kt:8: Warning: receiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for qwerty [UnspecifiedRegisterReceiverFlag]
                        context.registerReceiver(receiver,
                        ^
                0 errors, 1 warnings
                """
      )
  }

  fun testFilterVariableApply() {
    lint()
      .files(
        kotlin(
            """
                    package test.pkg
                    import android.content.BroadcastReceiver
                    import android.content.Context
                    import android.content.Intent
                    import android.content.IntentFilter
                    class TestClass1 {
                        fun test(context: Context, receiver: BroadcastReceiver) {
                            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply {
                                addAction("qwerty")
                            }
                            context.registerReceiver(receiver, filter)
                        }
                    }
                   """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                src/test/pkg/TestClass1.kt:11: Warning: receiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for qwerty [UnspecifiedRegisterReceiverFlag]
                        context.registerReceiver(receiver, filter)
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
      )
  }

  fun testFilterVariableApply2() {
    lint()
      .files(
        kotlin(
            """
                    package test.pkg
                    import android.content.BroadcastReceiver
                    import android.content.Context
                    import android.content.Intent
                    import android.content.IntentFilter
                    class TestClass1 {
                        fun test(context: Context, receiver: BroadcastReceiver) {
                            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply {
                                addAction(Intent.ACTION_BATTERY_OKAY)
                            }
                            context.registerReceiver(receiver, filter.apply {
                                addAction("qwerty")
                            })
                        }
                    }
                   """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                src/test/pkg/TestClass1.kt:11: Warning: receiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for qwerty [UnspecifiedRegisterReceiverFlag]
                        context.registerReceiver(receiver, filter.apply {
                        ^
                0 errors, 1 warnings
                """
      )
  }

  fun testFilterComplexChain() {
    lint()
      .files(
        kotlin(
            """
                    package test.pkg
                    import android.content.BroadcastReceiver
                    import android.content.Context
                    import android.content.Intent
                    import android.content.IntentFilter
                    class TestClass1 {
                        fun test(context: Context, receiver: BroadcastReceiver) {
                            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply {
                                addAction(Intent.ACTION_BATTERY_OKAY)
                            }
                            val filter2 = filter
                            val filter3 = filter2.apply {
                                addAction(Intent.ACTION_BATTERY_LOW)
                            }
                            context.registerReceiver(receiver, filter3)
                            val filter4 = filter3.apply {
                                addAction("qwerty")
                            }
                            context.registerReceiver(receiver, filter4)
                        }
                    }
                   """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                src/test/pkg/TestClass1.kt:19: Warning: receiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for qwerty [UnspecifiedRegisterReceiverFlag]
                        context.registerReceiver(receiver, filter4)
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
      )
  }
}
