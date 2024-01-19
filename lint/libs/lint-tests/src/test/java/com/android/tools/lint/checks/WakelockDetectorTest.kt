/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector

@Suppress(
  "CallToPrintStackTrace",
  "ConstantValue",
  "CatchMayIgnoreException",
  "RedundantSuppression",
)
class WakelockDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return WakelockDetector()
  }

  fun testAcquireButNoReleaseGlobalAnalysis() {
    val expected =
      """
      src/test/pkg/WakelockActivity1.java:15: Warning: Found a wakelock acquire() but no release() calls anywhere [Wakelock]
              mWakeLock.acquire(); // Never released
              ~~~~~~~~~~~~~~~~~~~
      0 errors, 1 warnings
      """

    lint()
      .files(
        manifest().minSdk(10),
        java(
            """
          package test.pkg;

          import android.app.Activity;
          import android.os.Bundle;
          import android.os.PowerManager;

          public class WakelockActivity1 extends Activity {
              private PowerManager.WakeLock mWakeLock;

              @Override
              public void onCreate(Bundle savedInstanceState) {
                  super.onCreate(savedInstanceState);
                  PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
                  mWakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Test");
                  mWakeLock.acquire(); // Never released
              }
          }
          """
          )
          .indented(),
      )
      .issues(WakelockDetector.ISSUE)
      .run()
      .expect(expected)
  }

  fun testAcquireButNoReleaseIsolatedAnalysis() {
    // Same as testAcquireButNoReleaseGlobalAnalysis but running in isolated mode
    // (e.g. "on the fly" in the editor); no warnings in that case since we're not
    // looking everywhere for the release.
    lint()
      .files(
        manifest().minSdk(10),
        java(
            """
          package test.pkg;

          import android.app.Activity;
          import android.os.Bundle;
          import android.os.PowerManager;

          public class WakelockActivity1 extends Activity {
              private PowerManager.WakeLock mWakeLock;

              @Override
              public void onCreate(Bundle savedInstanceState) {
                  super.onCreate(savedInstanceState);
                  PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
                  mWakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Test");
                  mWakeLock.acquire(); // Never released
              }
          }
          """
          )
          .indented(),
      )
      .isolated("src/test/pkg/WakelockActivity1.java")
      .issues(WakelockDetector.ISSUE)
      .run()
      .expectClean()
  }

  fun testReleaseInRightLifecycleMethod() {
    val expected =
      """
      src/test/pkg/WakelockActivity2.java:13: Warning: Wakelocks should be released in onPause, not onDestroy [Wakelock]
                  mWakeLock.release(); // Should be done in onPause instead
                  ~~~~~~~~~~~~~~~~~~~
      0 errors, 1 warnings
      """

    lint()
      .files(
        manifest().minSdk(10),
        java(
            """
            package test.pkg;

            import android.app.Activity;
            import android.os.PowerManager;

            public class WakelockActivity2 extends Activity {
                private PowerManager.WakeLock mWakeLock;

                @Override
                protected void onDestroy() {
                    super.onDestroy();
                    if (mWakeLock != null && mWakeLock.isHeld()) {
                        mWakeLock.release(); // Should be done in onPause instead
                    }
                }

                @Override
                protected void onPause() {
                    super.onDestroy();
                    if (mWakeLock != null && mWakeLock.isHeld()) {
                        mWakeLock.release(); // OK
                    }
                }
            }
            """
          )
          .indented(),
      )
      .issues(WakelockDetector.ISSUE)
      .run()
      .expect(expected)
  }

  fun testDocumentationExample() {
    val expected =
      """
      src/test/pkg/WakelockActivity3.java:13: Warning: The release() call is not always reached (because of a possible exception in the path acquire() → randomCall() → exit; use try/finally to ensure release is always called) [Wakelock]
              lock.release(); // Should be in finally block
                   ~~~~~~~
      0 errors, 1 warnings
      """

    lint()
      .files(
        manifest().minSdk(10),
        java(
            """
            package test.pkg;

            import android.app.Activity;
            import android.os.PowerManager;

            public class WakelockActivity3 extends Activity {
                void wrongFlow() {
                    PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
                    PowerManager.WakeLock lock =
                            manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Test");
                    lock.acquire();
                    randomCall();
                    lock.release(); // Should be in finally block
                }

                static void randomCall() {
                    System.out.println("test");
                }
            }
            """
          )
          .indented(),
      )
      .issues(WakelockDetector.ISSUE)
      .run()
      .expect(expected)
  }

  fun testNoRelease() {
    lint()
      .files(
        manifest().minSdk(10),
        java(
            """
            package test.pkg;

            import android.os.PowerManager;

            public class LockUtility {
                void acquire(PowerManager.WakeLock lock) {
                    lock.acquire();
                }
            }
            """
          )
          .indented(),
      )
      .incremental("src/test/pkg/LockUtility.java")
      .issues(WakelockDetector.ISSUE)
      .run()
      .expectClean()
  }

  fun testLockIndirection() {
    val expected =
      """
      src/test/pkg/WakelockActivity4.java:10: Warning: The release() call is not always reached (because of a possible exception in the path acquire() → randomCall() → getLock() → exit; use try/finally to ensure release is always called) [Wakelock]
              getLock().release(); // Should be in finally block
                        ~~~~~~~
      0 errors, 1 warnings
      """
    lint()
      .files(
        manifest().minSdk(10),
        java(
            """
            package test.pkg;

            import android.app.Activity;
            import android.os.PowerManager;

            public class WakelockActivity4 extends Activity {
                void wrongFlow2() {
                    getLock().acquire();
                    randomCall();
                    getLock().release(); // Should be in finally block
                }

                private PowerManager.WakeLock mLock;

                PowerManager.WakeLock getLock() {
                    if (mLock == null) {
                        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
                        mLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Test");
                    }

                    return mLock;
                }

                static void randomCall() {
                    System.out.println("test");
                }
            }
            """
          )
          .indented(),
      )
      .issues(WakelockDetector.ISSUE)
      .run()
      .expect(expected)
  }

  fun testThrowingCall() {
    val expected =
      """
      src/test/pkg/WakelockActivity5.java:13: Warning: The release() call is not always reached (because of a possible exception in the path acquire() → randomCall() → exit; use try/finally to ensure release is always called) [Wakelock]
              lock.release(); // Should be in finally block
                   ~~~~~~~
      0 errors, 1 warnings
      """
    lint()
      .files(
        manifest().minSdk(10),
        java(
            """
            package test.pkg;

            import android.app.Activity;
            import android.os.PowerManager;

            public class WakelockActivity5 extends Activity {
                void wrongFlow() {
                    PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
                    PowerManager.WakeLock lock =
                            manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Test");
                    lock.acquire();
                    randomCall();
                    lock.release(); // Should be in finally block
                }

                static void randomCall() {
                    System.out.println("test");
                }
            }
            """
          )
          .indented(),
      )
      .issues(WakelockDetector.ISSUE)
      .run()
      .expect(expected)
  }

  fun testMultipleReleaseMethodSomeMissing() {
    lint()
      .files(
        manifest().minSdk(10),
        kotlin(
            """
            package test.pkg

            import android.app.Activity
            import android.os.PowerManager

            fun test(lock: PowerManager.WakeLock, condition1: Boolean, condition2: Boolean) {
               lock.acquire()
               if (condition1) {
                   // something()
                   lock.release()
               } else if (condition2) {
                  lock.release()
               } else {
                  // something() // missing release!
               }
            }
            """
          )
          .indented(),
      )
      .issues(WakelockDetector.ISSUE)
      .skipTestModes(TestMode.IF_TO_WHEN)
      .run()
      .expect(
        """
        src/test/pkg/test.kt:10: Warning: The release() call is not always reached (can exit the method via path acquire() → if → else if → else → exit; use try/finally to ensure release is always called) [Wakelock]
               lock.release()
                    ~~~~~~~
            src/test/pkg/test.kt:12: <No location-specific message>
              lock.release()
                   ~~~~~~~
        0 errors, 1 warnings
        """
      )
  }

  fun testMultipleReleaseMethods() {
    lint()
      .files(
        manifest().minSdk(10),
        kotlin(
            """
            package test.pkg

            import android.app.Activity
            import android.os.PowerManager

            fun test(lock: PowerManager.WakeLock, condition1: Boolean, condition2: Boolean) {
               lock.acquire()
               if (condition1) {
                   // something()
                   lock.release()
               } else if (condition2) {
                  // something()
                  lock.release()
               } else {
                  lock.release()
               }
            }
            """
          )
          .indented(),
      )
      .skipTestModes(TestMode.IF_TO_WHEN)
      .issues(WakelockDetector.ISSUE)
      .run()
      .expectClean()
  }

  fun testBasicFlows() {
    val expected =
      """
      src/test/pkg/WakelockActivity6.java:17: Warning: The release() call is not always reached (can exit the method via path acquire() → if → === → getTaskId() → then → randomCall() → exit; use try/finally to ensure release is always called) [Wakelock]
                  lock.release(); // ERROR 1
                       ~~~~~~~
      src/test/pkg/WakelockActivity6.java:27: Warning: The release() call is not always reached (can exit the method via path acquire() → if → === → getTaskId() → then → randomCall1() → randomCall2() → exit; use try/finally to ensure release is always called) [Wakelock]
                  lock.release(); // ERROR 2
                       ~~~~~~~
      src/test/pkg/WakelockActivity6.java:64: Warning: The release() call is not always reached (because of a possible exception in the path acquire() → if → < → then → println() → exit; use try/finally to ensure release is always called) [Wakelock]
              lock.release(); // ERROR 3
                   ~~~~~~~
      src/test/pkg/WakelockActivity6.java:73: Warning: The release() call is not always reached (can exit the method via path acquire() → if → !== → getTaskId() → exit; use try/finally to ensure release is always called) [Wakelock]
                  lock.release(); // ERROR 4
                       ~~~~~~~
      0 errors, 4 warnings
      """
    lint()
      .files(
        manifest().minSdk(10),
        java(
            """
            package test.pkg;

            import android.annotation.SuppressLint;
            import android.app.Activity;
            import android.os.PowerManager;
            import android.os.PowerManager.WakeLock;

            public class WakelockActivity6 extends Activity {
                void wrongFlow1() {
                    PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
                    PowerManager.WakeLock lock =
                            manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Test");
                    lock.acquire();
                    if (getTaskId() == 50) {
                        randomCall();
                    } else {
                        lock.release(); // ERROR 1
                    }
                }

                void wrongFlow2(PowerManager.WakeLock lock) {
                    lock.acquire();
                    if (getTaskId() == 50) {
                        randomCall1();
                        randomCall2();
                    } else {
                        lock.release(); // ERROR 2
                    }
                }

                void okFlow1(WakeLock lock) {
                    lock.acquire();
                    try {
                        randomCall();
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        lock.release(); // OK 1
                    }
                }

                public void checkNullGuard(WakeLock lock) {
                    lock.acquire();
                    if (lock != null) {
                        lock.release(); // OK 2
                    }
                }

                @SuppressLint("Wakelock")
                public void checkDisabled1(PowerManager.WakeLock lock) {
                    lock.acquire();
                    randomCall();
                    lock.release(); // Wrong, but disabled via suppress
                }

                void wrongFlow3(WakeLock lock) {
                    int id = getTaskId();
                    lock.acquire();
                    if (id < 50) {
                        System.out.println(1);
                    } else {
                        System.out.println(2);
                    }
                    lock.release(); // ERROR 3
                }

                void wrongConditional() {
                    PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
                    PowerManager.WakeLock lock =
                            manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Test");
                    lock.acquire();
                    if (getTaskId() != 50) {
                        lock.release(); // ERROR 4
                    }
                }

                static void randomCall() {
                    System.out.println("test");
                }
            }
            """
          )
          .indented(),
      )
      .issues(WakelockDetector.ISSUE)
      // Skipped because the block ({ }) shows up as expected in the error message path
      .skipTestModes(TestMode.BODY_REMOVAL)
      .run()
      .expect(expected)
  }

  fun testAnonymousInnerClass() {
    lint()
      .files(
        manifest().minSdk(10),
        java(
            """
            package test.pkg;

            import android.os.PowerManager.WakeLock;

            public class WakelockActivity7 {
                public void test(WakeLock lock) {
                    try {
                        lock.acquire();
                        new Runnable() {
                            public void run() {
                            }
                        };
                    } finally {
                        lock.release();
                    }
                }
            }
            """
          )
          .indented(),
      )
      .issues(WakelockDetector.ISSUE)
      .run()
      .expectClean()
  }

  fun testIsHeld() {
    lint()
      .files(
        manifest().minSdk(10),
        java(
            """
            package test.pkg;

            import android.os.PowerManager.WakeLock;

            public class WakelockTest {
                public void test(WakeLock lock) {
                    //noinspection WakelockTimeout
                    lock.acquire();
                    if (lock.isHeld()) {
                        lock.release();
                    }
                }
            }
            """
          )
          .indented(),
      )
      .issues(WakelockDetector.ISSUE)
      .run()
      .expectClean()
  }

  fun testNoWarningsForAcquireWithTimeout() {
    // Regression test for 66040
    lint()
      .files(
        manifest().minSdk(10),
        java(
            """
            package test.pkg;

            import android.app.Activity;
            import android.os.Bundle;
            import android.os.PowerManager;
            import android.os.PowerManager.WakeLock;

            public class WakelockActivity9 extends Activity {
                private WakeLock mWakeLock;

                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
                    mWakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Test");
                    mWakeLock.acquire(2000L);
                }
            }
            """
          )
          .indented(),
      )
      .issues(WakelockDetector.ISSUE)
      .run()
      .expectClean()
  }

  fun testThrowInCatch() {
    // Regression test for 43212
    lint()
      .files(
        manifest().minSdk(10),
        java(
            """
            package test.pkg;

            import android.app.Activity;
            import android.os.Bundle;
            import android.os.PowerManager;

            public class WakelockActivity10 extends Activity {
                @Override
                public void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
                    PowerManager.WakeLock wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Test");

                    try {
                        wakeLock.acquire();
                        throw new Exception();
                    } catch (Exception e) {

                    } finally {
                        wakeLock.release();
                    }
                }
            }
            """
          )
          .indented(),
      )
      .issues(WakelockDetector.ISSUE)
      .run()
      .expectClean()
  }

  fun testDoNotSetBothFlags() {
    val expected =
      """
      src/test/pkg/PowerManagerFlagTest.java:14: Warning: Should not set both PARTIAL_WAKE_LOCK and ACQUIRE_CAUSES_WAKEUP. If you do not want the screen to turn on, get rid of ACQUIRE_CAUSES_WAKEUP [Wakelock]
              pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK|ACQUIRE_CAUSES_WAKEUP, "Test"); // Bad
                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      0 errors, 1 warnings
      """
    lint()
      .files(
        manifest().minSdk(10),
        java(
            """
            package test.pkg;

            import static android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP;
            import static android.os.PowerManager.FULL_WAKE_LOCK;
            import static android.os.PowerManager.PARTIAL_WAKE_LOCK;
            import android.content.Context;
            import android.os.PowerManager;

            public class PowerManagerFlagTest {
                public void test(Context context) {
                    PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

                    pm.newWakeLock(PARTIAL_WAKE_LOCK, "Test"); // OK
                    pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK|ACQUIRE_CAUSES_WAKEUP, "Test"); // Bad
                    pm.newWakeLock(FULL_WAKE_LOCK|ACQUIRE_CAUSES_WAKEUP, "Test"); // OK
                }
            }
            """
          )
          .indented(),
      )
      .issues(WakelockDetector.ISSUE)
      .run()
      .expect(expected)
  }

  fun testTimeout() {
    lint()
      .files(
        java(
            """
            package test.pkg;
            import android.content.Context;
            import android.os.PowerManager;

            import static android.os.PowerManager.PARTIAL_WAKE_LOCK;

            public abstract class WakelockTest extends Context {
                public PowerManager.WakeLock createWakelock() {
                    PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
                    PowerManager.WakeLock wakeLock = manager.newWakeLock(PARTIAL_WAKE_LOCK, "Test");
                    wakeLock.acquire(); // ERROR
                    return wakeLock;
                }

                public PowerManager.WakeLock createWakelockWithTimeout(long timeout) {
                    PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
                    PowerManager.WakeLock wakeLock = manager.newWakeLock(PARTIAL_WAKE_LOCK, "Test");
                    wakeLock.acquire(timeout); // OK
                    return wakeLock;
                }
            }
            """
          )
          .indented()
      )
      .issues(WakelockDetector.TIMEOUT)
      .run()
      .expect(
        """
        src/test/pkg/WakelockTest.java:11: Warning: Provide a timeout when requesting a wakelock with PowerManager.Wakelock.acquire(long timeout). This will ensure the OS will cleanup any wakelocks that last longer than you intend, and will save your user's battery. [WakelockTimeout]
                wakeLock.acquire(); // ERROR
                ~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for src/test/pkg/WakelockTest.java line 10: Set timeout to 10 minutes:
        @@ -11 +11
        -         wakeLock.acquire(); // ERROR
        +         wakeLock.acquire(10*60*1000L /*10 minutes*/); // ERROR

        """
      )
  }
}
