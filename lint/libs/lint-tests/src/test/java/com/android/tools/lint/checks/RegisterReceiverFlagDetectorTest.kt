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

  fun testIntentFilterIsFieldWithProtectedActions() {
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
              private Context mContext;
              private final IntentFilter myIntentFilter;
              private TestClass1(Context context) {
                mContext = context;
                myIntentFilter = new IntentFilter();
                myIntentFilter.addAction(Intent.ACTION_BATTERY_LOW);
                myIntentFilter.addAction(Intent.ACTION_BATTERY_OKAY);
              }
              public void testMethod(BroadcastReceiver receiver) {
                  mContext.registerReceiver(receiver, myIntentFilter);
              }
          }
          """
        )
      )
      .run()
      .expectClean()
  }

  fun testIntentFilterIsFieldWithProtectedActions_multipleRegisterCalls() {
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
              private Context mContext;
              private IntentFilter myIntentFilter;
              private TestClass1(Context context) {
                mContext = context;
                myIntentFilter = new IntentFilter();
                myIntentFilter.addAction(Intent.ACTION_BATTERY_LOW);
                myIntentFilter.addAction(Intent.ACTION_BATTERY_OKAY);
              }
              public void testMethod(BroadcastReceiver receiver) {
                  mContext.registerReceiver(receiver, myIntentFilter);
              }
              public void testMethodTwo(BroadcastReceiver receiver) {
                  mContext.registerReceiver(receiver, myIntentFilter);
              }
          }
          """
        )
      )
      .run()
      .expectClean()
  }

  fun testIntentFilterIsFieldWithProtectedActions_nonPrivate() {
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
              private Context mContext;
              IntentFilter myIntentFilter;
              private TestClass1(Context context) {
                mContext = context;
                myIntentFilter = new IntentFilter();
                myIntentFilter.addAction(Intent.ACTION_BATTERY_LOW);
                myIntentFilter.addAction(Intent.ACTION_BATTERY_OKAY);
              }
              public void testMethod(BroadcastReceiver receiver) {
                  mContext.registerReceiver(receiver, myIntentFilter);
              }
          }
          """
        ),
      )
      .run()
      .expect(
        """
        src/test/pkg/TestClass1.java:17: Warning: receiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for an IntentFilter that cannot be inspected by lint [UnspecifiedRegisterReceiverFlag]
                          mContext.registerReceiver(receiver, myIntentFilter);
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
  }

  fun testIntentFilterIsFieldWithProtectedActions_escapesScope() {
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
              private Context mContext;
              private IntentFilter myIntentFilter;
              private TestClass1(Context context) {
                mContext = context;
                myIntentFilter = new IntentFilter();
                myIntentFilter.addAction(Intent.ACTION_BATTERY_LOW);
                myIntentFilter.addAction(Intent.ACTION_BATTERY_OKAY);
              }
              public void testMethod(BroadcastReceiver receiver) {
                  mContext.registerReceiver(receiver, myIntentFilter);
              }
              public void escape() {
                UtilClass.utilMethod(myIntentFilter);
              }
          }
          """
        ),
        java(
          """
          package test.pkg;
          import android.content.IntentFilter;
          public class UtilClass {
              public void utilMethod(IntentFilter filter) {}
          }
          """
        )
      )
      .run()
      .expect(
        """
        src/test/pkg/TestClass1.java:17: Warning: receiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for an IntentFilter that cannot be inspected by lint [UnspecifiedRegisterReceiverFlag]
                          mContext.registerReceiver(receiver, myIntentFilter);
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
  }

  fun testIntentFilterIsFieldWithProtectedActions_escapesScopeViaPublicGetter() {
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
              private Context mContext;
              private IntentFilter myIntentFilter;
              private TestClass1(Context context) {
                mContext = context;
                myIntentFilter = new IntentFilter();
                myIntentFilter.addAction(Intent.ACTION_BATTERY_LOW);
                myIntentFilter.addAction(Intent.ACTION_BATTERY_OKAY);
              }
              public void testMethod(BroadcastReceiver receiver) {
                  mContext.registerReceiver(receiver, myIntentFilter);
              }
              public IntentFilter getIntentFilter() {
                  return myIntentFilter;
              }
          }
          """
        ),
      )
      .run()
      .expect(
        """
        src/test/pkg/TestClass1.java:17: Warning: receiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for an IntentFilter that cannot be inspected by lint [UnspecifiedRegisterReceiverFlag]
                          mContext.registerReceiver(receiver, myIntentFilter);
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
  }

  fun testIntentFilterIsFieldWithProtectedActions_escapesScopeViaPublicSetter() {
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
              private Context mContext;
              private IntentFilter myIntentFilter;
              private TestClass1(Context context) {
                mContext = context;
                myIntentFilter = new IntentFilter();
                myIntentFilter.addAction(Intent.ACTION_BATTERY_LOW);
                myIntentFilter.addAction(Intent.ACTION_BATTERY_OKAY);
              }
              public void testMethod(BroadcastReceiver receiver) {
                  mContext.registerReceiver(receiver, myIntentFilter);
              }
              public void setMyIntentFilter(IntentFilter intentFilter) {
                myIntentFilter = intentFilter;
              }
          }
          """
        ),
      )
      .run()
      .expect(
        """
        src/test/pkg/TestClass1.java:17: Warning: receiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for an IntentFilter that cannot be inspected by lint [UnspecifiedRegisterReceiverFlag]
                          mContext.registerReceiver(receiver, myIntentFilter);
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
  }

  fun testIntentFilterIsFieldWithUnprotectedActions() {
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
              private Context mContext;
              private IntentFilter myIntentFilter;
              private TestClass1(Context context) {
                mContext = context;
                myIntentFilter = new IntentFilter("foo");
                myIntentFilter.addAction("bar");
              }
              public void testMethod(BroadcastReceiver receiver) {
                  mContext.registerReceiver(receiver, myIntentFilter);
              }
              public void randomMethod() {
                  myIntentFilter.addAction("baz");
              }
          }
          """
        )
      )
      .run()
      .expect(
        """
        src/test/pkg/TestClass1.java:16: Warning: receiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for foo, bar, baz [UnspecifiedRegisterReceiverFlag]
                          mContext.registerReceiver(receiver, myIntentFilter);
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
  }

  fun testIntentFilterIsFieldWithUnprotectedActions_constructedInline() {
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
              private Context mContext;
              private IntentFilter myIntentFilter = new IntentFilter("foo");
              private TestClass1(Context context) {
                mContext = context;
                myIntentFilter.addAction("bar");
              }
              public void testMethod(BroadcastReceiver receiver) {
                  mContext.registerReceiver(receiver, myIntentFilter);
              }
          }
          """
        ),
      )
      .run()
      .expect(
        """
        src/test/pkg/TestClass1.java:15: Warning: receiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for foo, bar [UnspecifiedRegisterReceiverFlag]
                          mContext.registerReceiver(receiver, myIntentFilter);
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
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
      )
      .run()
      .expect(
        """
        src/test/pkg/TestClass1.java:14: Warning: receiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for querty [UnspecifiedRegisterReceiverFlag]
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
      .expectFixDiffs(
        """
        Fix for src/test/pkg/TestClass1.java line 9: Add RECEIVER_NOT_EXPORTED (preferred):
        @@ -9 +9
        -         context.registerReceiver(receiver, filter);
        +         context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        Fix for src/test/pkg/TestClass1.java line 9: Add RECEIVER_EXPORTED:
        @@ -9 +9
        -         context.registerReceiver(receiver, filter);
        +         context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
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
      .expectFixDiffs(
        """
        Fix for src/test/pkg/TestClass1.java line 9: Add RECEIVER_NOT_EXPORTED (preferred):
        @@ -9 +9
        -         context.registerReceiver(receiver, filter, 0);
        +         context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        Fix for src/test/pkg/TestClass1.java line 9: Add RECEIVER_EXPORTED:
        @@ -9 +9
        -         context.registerReceiver(receiver, filter, 0);
        +         context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        """
      )
  }

  fun testOtherFlagsPresent_ExportedFlagsAbsent() {
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
                  context.registerReceiver(receiver, filter, Context.RECEIVER_VISIBLE_TO_INSTANT_APPS);
                  int flags = Context.RECEIVER_VISIBLE_TO_INSTANT_APPS;
                  context.registerReceiver(receiver, filter, flags);
              }
          }
          """
          )
          .indented(),
        kotlin(
            """
          package test.pkg
          import android.content.BroadcastReceiver
          import android.content.Context
          import android.content.Intent
          import android.content.IntentFilter
          class TestClass2 {
              fun testMethod(context: Context, receiver: BroadcastReceiver) {
                  val filter = new IntentFilter("qwerty")
                  context.registerReceiver(receiver, filter, Context.RECEIVER_VISIBLE_TO_INSTANT_APPS)
                  val flags = Context.RECEIVER_VISIBLE_TO_INSTANT_APPS
                  context.registerReceiver(receiver, filter, flags)
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
                context.registerReceiver(receiver, filter, Context.RECEIVER_VISIBLE_TO_INSTANT_APPS);
                                                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/TestClass1.java:11: Warning: receiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for qwerty [UnspecifiedRegisterReceiverFlag]
                context.registerReceiver(receiver, filter, flags);
                                                           ~~~~~
        src/test/pkg/TestClass2.kt:9: Warning: receiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for an IntentFilter that cannot be inspected by lint [UnspecifiedRegisterReceiverFlag]
                context.registerReceiver(receiver, filter, Context.RECEIVER_VISIBLE_TO_INSTANT_APPS)
                                                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/TestClass2.kt:11: Warning: receiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for an IntentFilter that cannot be inspected by lint [UnspecifiedRegisterReceiverFlag]
                context.registerReceiver(receiver, filter, flags)
                                                           ~~~~~
        0 errors, 4 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for src/test/pkg/TestClass1.java line 9: Add RECEIVER_NOT_EXPORTED (preferred):
        @@ -9 +9
        -         context.registerReceiver(receiver, filter, Context.RECEIVER_VISIBLE_TO_INSTANT_APPS);
        +         context.registerReceiver(receiver, filter, Context.RECEIVER_VISIBLE_TO_INSTANT_APPS | Context.RECEIVER_NOT_EXPORTED);
        Fix for src/test/pkg/TestClass1.java line 9: Add RECEIVER_EXPORTED:
        @@ -9 +9
        -         context.registerReceiver(receiver, filter, Context.RECEIVER_VISIBLE_TO_INSTANT_APPS);
        +         context.registerReceiver(receiver, filter, Context.RECEIVER_VISIBLE_TO_INSTANT_APPS | Context.RECEIVER_EXPORTED);
        Fix for src/test/pkg/TestClass1.java line 11: Add RECEIVER_NOT_EXPORTED (preferred):
        @@ -11 +11
        -         context.registerReceiver(receiver, filter, flags);
        +         context.registerReceiver(receiver, filter, flags | Context.RECEIVER_NOT_EXPORTED);
        Fix for src/test/pkg/TestClass1.java line 11: Add RECEIVER_EXPORTED:
        @@ -11 +11
        -         context.registerReceiver(receiver, filter, flags);
        +         context.registerReceiver(receiver, filter, flags | Context.RECEIVER_EXPORTED);
        Fix for src/test/pkg/TestClass2.kt line 9: Add RECEIVER_NOT_EXPORTED (preferred):
        @@ -9 +9
        -         context.registerReceiver(receiver, filter, Context.RECEIVER_VISIBLE_TO_INSTANT_APPS)
        +         context.registerReceiver(receiver, filter, Context.RECEIVER_VISIBLE_TO_INSTANT_APPS or Context.RECEIVER_NOT_EXPORTED)
        Fix for src/test/pkg/TestClass2.kt line 9: Add RECEIVER_EXPORTED:
        @@ -9 +9
        -         context.registerReceiver(receiver, filter, Context.RECEIVER_VISIBLE_TO_INSTANT_APPS)
        +         context.registerReceiver(receiver, filter, Context.RECEIVER_VISIBLE_TO_INSTANT_APPS or Context.RECEIVER_EXPORTED)
        Fix for src/test/pkg/TestClass2.kt line 11: Add RECEIVER_NOT_EXPORTED (preferred):
        @@ -11 +11
        -         context.registerReceiver(receiver, filter, flags)
        +         context.registerReceiver(receiver, filter, flags or Context.RECEIVER_NOT_EXPORTED)
        Fix for src/test/pkg/TestClass2.kt line 11: Add RECEIVER_EXPORTED:
        @@ -11 +11
        -         context.registerReceiver(receiver, filter, flags)
        +         context.registerReceiver(receiver, filter, flags or Context.RECEIVER_EXPORTED)
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
      .expectFixDiffs(
        """
        Fix for src/test/pkg/TestClass1.java line 9: Add RECEIVER_NOT_EXPORTED (preferred):
        @@ -9 +9
        -         context.registerReceiver(receiver, filter);
        +         context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        Fix for src/test/pkg/TestClass1.java line 9: Add RECEIVER_EXPORTED:
        @@ -9 +9
        -         context.registerReceiver(receiver, filter);
        +         context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
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
      .expectFixDiffs(
        """
        Fix for src/test/pkg/TestClass1.kt line 8: Add RECEIVER_NOT_EXPORTED (preferred):
        @@ -11 +11
        -                 })
        +                 }, Context.RECEIVER_NOT_EXPORTED)
        Fix for src/test/pkg/TestClass1.kt line 8: Add RECEIVER_EXPORTED:
        @@ -11 +11
        -                 })
        +                 }, Context.RECEIVER_EXPORTED)
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

  fun testApiLevelU_error() {
    lint()
      .projects(
        project(
          manifest().targetSdk(34),
          kotlin(
              """
            package test.pkg
            import android.content.BroadcastReceiver
            import android.content.Context
            import android.content.Intent
            import android.content.IntentFilter
            class TestClass1 {
                fun test(context: Context, receiver: BroadcastReceiver) {
                    val filter = IntentFilter("qwerty")
                    context.registerReceiver(receiver, filter)
                    val maybeSafeFilter = IntentFilter(Intent.ACTION_BATTERY_OKAY)
                    updateFilter(maybeSafeFilter)
                    context.registerReceiver(receiver, safeFilter)
                }
            }
           """
            )
            .indented(),
        )
      )
      .run()
      .expect(
        """
        src/test/pkg/TestClass1.kt:9: Error: receiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for qwerty [UnspecifiedRegisterReceiverFlag]
                context.registerReceiver(receiver, filter)
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/TestClass1.kt:12: Warning: receiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for an IntentFilter that cannot be inspected by lint [UnspecifiedRegisterReceiverFlag]
                context.registerReceiver(receiver, safeFilter)
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        1 errors, 1 warnings
        """
      )
  }
}
