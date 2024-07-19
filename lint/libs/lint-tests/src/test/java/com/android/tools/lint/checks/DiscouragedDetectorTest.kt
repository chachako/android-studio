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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.rClass
import com.android.tools.lint.detector.api.Detector

class DiscouragedDetectorTest : AbstractCheckTest() {

  private val discouragedAnnotationStub =
    kotlin(
        "src/androidx/annotation/Discouraged.kt",
        """
        /* HIDE-FROM-DOCUMENTATION */
        package androidx.annotation
        @Retention(AnnotationRetention.SOURCE)
        @Target(
            AnnotationTarget.CONSTRUCTOR,
            AnnotationTarget.FIELD,
            AnnotationTarget.FUNCTION,
            AnnotationTarget.PROPERTY_GETTER,
            AnnotationTarget.PROPERTY_SETTER,
            AnnotationTarget.VALUE_PARAMETER,
            AnnotationTarget.ANNOTATION_CLASS,
            AnnotationTarget.CLASS
        )
        annotation class Discouraged(
            val message: String
        )
        """,
      )
      .indented()

  private val resourcesStub =
    java(
        "src/android/content/res/Resources.java",
        """
            /* HIDE-FROM-DOCUMENTATION */
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

  fun testDiscouragedClassXmlReference() {
    lint()
      .files(
        kotlin(
            "src/com/pkg/Button.kt",
            """
            package com.pkg
            import androidx.annotation.Discouraged
            @Discouraged(message="Don't use this class")
            open class Button
            open class ToggleButton : Button // WARN 1: Referencing discouraged super class
            """,
          )
          .indented(),
        xml(
            "res/layout/activity_main.xml",
            """
            <merge>
                <com.pkg.Button/> <!-- WARN 2: Directly annotated -->
                <com.pkg.ToggleButton/> <!-- WARN 3: Superclass annotated -->
            </merge>
            """,
          )
          .indented(),
        discouragedAnnotationStub,
      )
      .run()
      .expect(
        """
        src/com/pkg/Button.kt:5: Warning: Don't use this class [DiscouragedApi]
        open class ToggleButton : Button // WARN 1: Referencing discouraged super class
                                  ~~~~~~
        res/layout/activity_main.xml:2: Warning: Don't use this class [DiscouragedApi]
            <com.pkg.Button/> <!-- WARN 2: Directly annotated -->
             ~~~~~~~~~~~~~~
        res/layout/activity_main.xml:3: Warning: Don't use this class [DiscouragedApi]
            <com.pkg.ToggleButton/> <!-- WARN 3: Superclass annotated -->
             ~~~~~~~~~~~~~~~~~~~~
        0 errors, 3 warnings
        """
      )
  }

  fun testNested() {
    // Referencing a class that has an outer class that is discouraged
    lint()
      .files(
        kotlin(
            """
            package test.pkg

            import android.app.Activity
            import androidx.annotation.Discouraged

            @Discouraged(message="Don't use this")
            open class Private {
                class MyActivity : Activity()
            }
            open class OkActivity : Activity()
            """
          )
          .indented(),
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="test.pkg" >
                <application>
                    <activity android:name="test.pkg.OkActivity"/>              <!-- OK -->
                    <activity android:name="test.pkg.Private＄MyActivity"/>     <!-- WARN -->
                </application>
            </manifest>
            """
          )
          .indented(),
        discouragedAnnotationStub,
      )
      .run()
      .expect(
        """
        AndroidManifest.xml:5: Warning: Don't use this [DiscouragedApi]
                <activity android:name="test.pkg.Private＄MyActivity"/>     <!-- WARN -->
                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
  }

  fun testDiscouragedManifestClassReference() {
    lint()
      .files(
        java(
            """
            package test.pkg;

            import android.app.Activity;

            import androidx.annotation.Discouraged;

            public class Private {
                @Discouraged(message="Don't use this")
                public static class MyActivity extends Activity {
                }
            }
            """
          )
          .indented(),
        java(
            """
            package test.pkg;

            public class MyInheritedActivity extends Private.MyActivity { // WARN 5
            }
            """
          )
          .indented(),
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <!-- package="test.pkg" -->
                <application>
                    <activity android:name="test.pkg.OkActivity"/>              <!-- OK -->
                    <activity android:name="test.pkg.Private＄MyActivity"/>     <!-- WARN 1 -->
                    <activity android:name="test.pkg.MyInheritedActivity"/>     <!-- WARN 2 -->
                    <activity android:name=".Private＄MyActivity"/>             <!-- WARN 3 -->
                    <activity android:name=".MyInheritedActivity"/>             <!-- WARN 4 -->
                </application>
            </manifest>
            """
          )
          .indented(),
        kts(
            """
              android {
                  namespace = "test.pkg"
              }
              """
          )
          .indented(),
        // Using Gradle, so we need to move stub to the right source set, src/main/java rather than
        // src/
        gradleSourceSet(discouragedAnnotationStub),
      )
      .run()
      .expect(
        """
        src/main/AndroidManifest.xml:5: Warning: Don't use this [DiscouragedApi]
                <activity android:name="test.pkg.Private＄MyActivity"/>     <!-- WARN 1 -->
                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/main/AndroidManifest.xml:6: Warning: Don't use this [DiscouragedApi]
                <activity android:name="test.pkg.MyInheritedActivity"/>     <!-- WARN 2 -->
                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/main/AndroidManifest.xml:7: Warning: Don't use this [DiscouragedApi]
                <activity android:name=".Private＄MyActivity"/>             <!-- WARN 3 -->
                                        ~~~~~~~~~~~~~~~~~~~
        src/main/AndroidManifest.xml:8: Warning: Don't use this [DiscouragedApi]
                <activity android:name=".MyInheritedActivity"/>             <!-- WARN 4 -->
                                        ~~~~~~~~~~~~~~~~~~~~
        src/main/java/test/pkg/MyInheritedActivity.java:3: Warning: Don't use this [DiscouragedApi]
        public class MyInheritedActivity extends Private.MyActivity { // WARN 5
                                                 ~~~~~~~~~~~~~~~~~~
        0 errors, 5 warnings
        """
      )
  }

  private fun gradleSourceSet(kotlinFile: TestFile): TestFile {
    return kotlin(
      "src/main/java/" + kotlinFile.targetRelativePath.removePrefix("src/"),
      kotlinFile.contents.trimIndent(),
    )
  }

  override fun getDetector(): Detector {
    return DiscouragedDetector()
  }
}
