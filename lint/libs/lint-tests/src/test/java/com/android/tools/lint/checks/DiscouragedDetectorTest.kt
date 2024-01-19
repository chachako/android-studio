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

package com.android.tools.lint.checks

import com.android.tools.lint.checks.infrastructure.TestFiles.rClass
import com.android.tools.lint.detector.api.Detector

class DiscouragedDetectorTest : AbstractCheckTest() {

  private val discouragedAnnotationStub =
    java(
        "src/androidx/annotation/Discouraged.java",
        """
            package androidx.annotation;
            import static java.lang.annotation.ElementType.METHOD;
            import static java.lang.annotation.RetentionPolicy.SOURCE;

            import java.lang.annotation.Retention;
            import java.lang.annotation.Target;

            // Stub annotation for unit test.
            @Retention(SOURCE)
            @Target({METHOD})
            public @interface Discouraged {
                String message() default "";
            }
        """,
      )
      .indented()

  private val resourcesStub =
    java(
        "src/android/content/res/Resources.java",
        """
            package android.content.res;

            import android.util.TypedValue;
            import androidx.annotation.Discouraged;

            public class Resources {

                @Discouraged(message="Use of this function is discouraged. It is more efficient "
                                   + "to retrieve resources by identifier than by name.\n"
                                   + "See `getValue(int id, TypedValue outValue, boolean "
                                   + "resolveRefs)`.")
                public int getValue(String name, TypedValue outValue, boolean resolveRefs) { }

                public int getValue(int id, TypedValue outValue, boolean resolveRefs) { }
            }
        """,
      )
      .indented()

  fun testDocumentationExample() {
    val expected =
      """
            src/test/pkg/Test1.java:9: Warning: Use of this function is discouraged. It is more efficient to retrieve resources by identifier than by name.
            See getValue(int id, TypedValue outValue, boolean resolveRefs). [DiscouragedApi]
                    Resources.getValue("name", testValue, false);
                              ~~~~~~~~
            0 errors, 1 warnings
            """
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.res.Resources;
                import android.util.TypedValue;

                public class Test1 {
                    public void setValue() {
                        TypedValue testValue;
                        Resources.getValue("name", testValue, false);
                        Resources.getValue(0, testValue, false);
                    }
                }
                """
          )
          .indented(),
        resourcesStub,
        discouragedAnnotationStub,
      )
      .run()
      .expect(expected)
  }

  fun test205800560() {
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.app.Activity
                import android.os.Bundle
                import android.widget.TextView
                import androidx.annotation.Discouraged
                import java.util.UUID

                class MainActivity : Activity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContentView(R.layout.activity_main)
                        findViewById<TextView>(R.id.text)?.text = getSomeString()
                    }

                    companion object {
                        @Discouraged(message = "don't use this")
                        fun getSomeString(): String {
                            return UUID.randomUUID().toString()
                        }
                    }
                }
                """
          )
          .indented(),
        rClass("test.pkg", "@layout/activity_main", "@id/text"),
        discouragedAnnotationStub,
      )
      .allowDuplicates()
      .run()
      .expect(
        """
            src/test/pkg/MainActivity.kt:13: Warning: don't use this [DiscouragedApi]
                    findViewById<TextView>(R.id.text)?.text = getSomeString()
                                                              ~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
      )
  }

  fun testDiscouragedAttributes() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">
                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <activity
                            android:maxAspectRatio="1.0"
                            android:screenOrientation="portrait"
                            android:resizeableActivity="false" >
                        </activity>
                        <activity
                            android:resizeableActivity="true" >
                        </activity>
                    </application>
                </manifest>
          """
          )
          .indented()
      )
      .allowDuplicates()
      .run()
      .expect(
        """
        AndroidManifest.xml:7: Warning: Should not restrict activity to maximum or minimum aspect ratio. This may not be suitable for different form factors, causing the app to be letterboxed. [DiscouragedApi]
                    android:maxAspectRatio="1.0"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        AndroidManifest.xml:8: Warning: Should not restrict activity to fixed orientation. This may not be suitable for different form factors, causing the app to be letterboxed. [DiscouragedApi]
                    android:screenOrientation="portrait"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        AndroidManifest.xml:9: Warning: Activity should not be non-resizable. With this setting, apps cannot be used in multi-window or free form mode. [DiscouragedApi]
                    android:resizeableActivity="false" >
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 3 warnings
            """
      )
  }

  fun testScheduleAtFixedRate() {
    lint()
      .files(
        kotlin(
            """
            package com.pkg

            import java.time.Instant
            import java.util.Date
            import java.util.Timer
            import java.util.TimerTask
            import java.util.concurrent.ScheduledExecutorService
            import java.util.concurrent.TimeUnit

            class Main {
              fun bar(): TimerTask {
                TODO()
              }

              fun foo(executor: ScheduledExecutorService, timer: Timer) {
                executor.scheduleAtFixedRate({}, 10, 30, TimeUnit.SECONDS)
                timer.scheduleAtFixedRate(bar(), 10, 30)
                timer.scheduleAtFixedRate(bar(), Date.from(Instant.EPOCH), 30)
              }
            }
            """
          )
          .indented()
      )
      .run()
      .expect(
        """
src/com/pkg/Main.kt:16: Warning: Use of scheduleAtFixedRate is strongly discouraged because it can lead to unexpected behavior when Android processes become cached (tasks may unexpectedly execute hundreds or thousands of times in quick succession when a process changes from cached to uncached); prefer using scheduleWithFixedDelay [DiscouragedApi]
    executor.scheduleAtFixedRate({}, 10, 30, TimeUnit.SECONDS)
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
src/com/pkg/Main.kt:17: Warning: Use of scheduleAtFixedRate is strongly discouraged because it can lead to unexpected behavior when Android processes become cached (tasks may unexpectedly execute hundreds or thousands of times in quick succession when a process changes from cached to uncached); prefer using schedule [DiscouragedApi]
    timer.scheduleAtFixedRate(bar(), 10, 30)
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
src/com/pkg/Main.kt:18: Warning: Use of scheduleAtFixedRate is strongly discouraged because it can lead to unexpected behavior when Android processes become cached (tasks may unexpectedly execute hundreds or thousands of times in quick succession when a process changes from cached to uncached); prefer using schedule [DiscouragedApi]
    timer.scheduleAtFixedRate(bar(), Date.from(Instant.EPOCH), 30)
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
0 errors, 3 warnings
"""
      )
      .expectFixDiffs(
        """
Fix for src/com/pkg/Main.kt line 16: Replace with scheduleWithFixedDelay:
@@ -16 +16
-     executor.scheduleAtFixedRate({}, 10, 30, TimeUnit.SECONDS)
+     executor.scheduleWithFixedDelay({}, 10, 30, TimeUnit.SECONDS)
Fix for src/com/pkg/Main.kt line 17: Replace with schedule:
@@ -17 +17
-     timer.scheduleAtFixedRate(bar(), 10, 30)
+     timer.schedule(bar(), 10, 30)
"""
      )
  }

  override fun getDetector(): Detector {
    return DiscouragedDetector()
  }
}
