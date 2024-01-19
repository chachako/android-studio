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

import com.android.tools.lint.checks.infrastructure.ProjectDescription
import com.android.tools.lint.detector.api.Detector

class UnsafeImplicitIntentDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector = UnsafeImplicitIntentDetector()

  fun testDocumentationExample() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent("some.fake.action.LAUNCH");
                        startActivity(intent);
                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .run()
      .expect(
        """
            src/test/pkg/TestActivity.java:10: Error: The intent action some.fake.action.LAUNCH (used to start an activity) matches the intent filter of a non-exported component test.pkg.TestActivity from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                    Intent intent = new Intent("some.fake.action.LAUNCH");
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
      .expectFixDiffs(
        """
        Fix for src/test/pkg/TestActivity.java line 10: Set class name:
        @@ -10 +10
        -         Intent intent = new Intent("some.fake.action.LAUNCH");
        +         Intent intent = new Intent("some.fake.action.LAUNCH").setClassName([/* TODO: provide the application ID. For example: */ getPackageName()]|, "test.pkg.TestActivity");
        Fix for src/test/pkg/TestActivity.java line 10: Set package name:
        @@ -10 +10
        -         Intent intent = new Intent("some.fake.action.LAUNCH");
        +         Intent intent = new Intent("some.fake.action.LAUNCH").setPackage([/* TODO: provide the application ID. For example: */ getPackageName()]|);
        """
      )
  }

  fun testImplicitIntentIgnoredIfUnused() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent("some.fake.action.LAUNCH");
                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testImplicitIntentMatchesNonExportedComponent_actionSetFromSetter() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent();
                        intent.setAction("some.fake.action.LAUNCH");
                        startActivity(intent);
                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .run()
      .expect(
        """
            src/test/pkg/TestActivity.java:11: Error: The intent action some.fake.action.LAUNCH (used to start an activity) matches the intent filter of a non-exported component test.pkg.TestActivity from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                    intent.setAction("some.fake.action.LAUNCH");
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
      .expectFixDiffs(
        """
            Fix for src/test/pkg/TestActivity.java line 11: Set class name:
            @@ -11 +11
            -         intent.setAction("some.fake.action.LAUNCH");
            +         intent.setAction("some.fake.action.LAUNCH").setClassName([/* TODO: provide the application ID. For example: */ getPackageName()]|, "test.pkg.TestActivity");
            Fix for src/test/pkg/TestActivity.java line 11: Set package name:
            @@ -11 +11
            -         intent.setAction("some.fake.action.LAUNCH");
            +         intent.setAction("some.fake.action.LAUNCH").setPackage([/* TODO: provide the application ID. For example: */ getPackageName()]|);
        """
      )
  }

  fun testIntentMatchesNonExportedComponent_explicitViaSetClass_actionSetFromConstructor() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;
                import android.content.Context;

                public class TestActivity extends Activity {

                    public void foo(Context context) {
                        Intent intent = new Intent("some.fake.action.LAUNCH");
                        intent.setClass(context, SomeActivity.class);
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".SomeActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .issues(UnsafeImplicitIntentDetector.ISSUE)
      .run()
      .expectClean()
  }

  fun testIntentMatchesNonExportedComponent_explicitViaSetClassName_actionSetFromConstructor() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;
                import android.content.Context;

                public class TestActivity extends Activity {

                   public void foo(Context context) {
                        Intent intent = new Intent("some.fake.action.LAUNCH");
                        intent.setClassName(context, SomeActivity.class.getName());
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".SomeActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .issues(UnsafeImplicitIntentDetector.ISSUE)
      .run()
      .expectClean()
  }

  fun testIntentMatchesNonExportedComponent_explicitViaSetComponent_actionSetFromConstructor() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;
                import android.content.ComponentName;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent("some.fake.action.LAUNCH");
                        intent.setComponent(new ComponentName("test.pkg", "test.pkg.TestActivity.SomeActivity"));
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".SomeActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .issues(UnsafeImplicitIntentDetector.ISSUE)
      .run()
      .expectClean()
  }

  fun testIntentMatchesNonExportedComponent_explicitViaSetPackage_actionSetFromConstructor() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent("some.fake.action.LAUNCH");
                        intent.setPackage("test.pkg");
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".SomeActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .issues(UnsafeImplicitIntentDetector.ISSUE)
      .run()
      .expectClean()
  }

  fun testIntentMatchesNonExportedComponent_explicitViaSetClass_actionSetFromSetter() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;
                import android.content.Context;

                public class TestActivity extends Activity {

                    public void foo(Context context) {
                        Intent intent = new Intent();
                        intent.setAction("some.fake.action.LAUNCH");
                        intent.setClass(context, SomeActivity.class);
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".SomeActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .issues(UnsafeImplicitIntentDetector.ISSUE)
      .run()
      .expectClean()
  }

  fun testIntentMatchesNonExportedComponent_explicitViaSetClassName_actionSetFromSetter() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;
                import android.content.Context;

                public class TestActivity extends Activity {

                    public void foo(Context context) {
                        Intent intent = new Intent();
                        intent.setAction("some.fake.action.LAUNCH");
                        intent.setClassName(context, SomeActivity.class.getName());
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".SomeActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .issues(UnsafeImplicitIntentDetector.ISSUE)
      .run()
      .expectClean()
  }

  fun testIntentMatchesNonExportedComponent_explicitViaSetComponent_actionSetFromSetter() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;
                import android.content.ComponentName;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent();
                        intent.setAction("some.fake.action.LAUNCH");
                        intent.setComponent(new ComponentName("test.pkg", "test.pkg.TestActivity.SomeActivity"));
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".SomeActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .issues(UnsafeImplicitIntentDetector.ISSUE)
      .run()
      .expectClean()
  }

  fun testIntentMatchesNonExportedComponent_explicitViaSetPackage_actionSetFromSetter() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent();
                        intent.setAction("some.fake.action.LAUNCH");
                        intent.setPackage("test.pkg");
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".SomeActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .issues(UnsafeImplicitIntentDetector.ISSUE)
      .run()
      .expectClean()
  }

  fun testImplicitIntentMatchesNonExportedComponent_actionSetFromConstructorThenSetter() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent("some.fake.action.NON_EXISTING_ACTION");
                        intent.setAction("some.fake.action.LAUNCH");
                        startActivity(intent);
                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .run()
      .expect(
        """
            src/test/pkg/TestActivity.java:11: Error: The intent action some.fake.action.LAUNCH (used to start an activity) matches the intent filter of a non-exported component test.pkg.TestActivity from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                    intent.setAction("some.fake.action.LAUNCH");
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
      .expectFixDiffs(
        """
        Fix for src/test/pkg/TestActivity.java line 11: Set class name:
        @@ -11 +11
        -         intent.setAction("some.fake.action.LAUNCH");
        +         intent.setAction("some.fake.action.LAUNCH").setClassName([/* TODO: provide the application ID. For example: */ getPackageName()]|, "test.pkg.TestActivity");
        Fix for src/test/pkg/TestActivity.java line 11: Set package name:
        @@ -11 +11
        -         intent.setAction("some.fake.action.LAUNCH");
        +         intent.setAction("some.fake.action.LAUNCH").setPackage([/* TODO: provide the application ID. For example: */ getPackageName()]|);
        """
      )
  }

  fun testImplicitIntentMatchesNonExportedComponent_actionSetMultipleTimes() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent("some.fake.action.A");
                        if (intent.getBooleanExtra("a", false)) {
                          intent.setAction("some.fake.action.B");
                        } else {
                          intent.setAction("some.fake.action.C");
                        }
                        startActivity(intent);
                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.A" />
                            <action android:name="some.fake.action.B" />
                            <action android:name="some.fake.action.C" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .issues(UnsafeImplicitIntentDetector.ISSUE)
      .run()
      .expect(
        """
        src/test/pkg/TestActivity.java:10: Error: The intent action some.fake.action.A (used to start an activity) matches the intent filter of a non-exported component test.pkg.TestActivity from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                Intent intent = new Intent("some.fake.action.A");
                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/TestActivity.java:12: Error: The intent action some.fake.action.B (used to start an activity) matches the intent filter of a non-exported component test.pkg.TestActivity from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                  intent.setAction("some.fake.action.B");
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/TestActivity.java:14: Error: The intent action some.fake.action.C (used to start an activity) matches the intent filter of a non-exported component test.pkg.TestActivity from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                  intent.setAction("some.fake.action.C");
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        3 errors, 0 warnings
        """
      )
      .expectFixDiffs(
        """
          Fix for src/test/pkg/TestActivity.java line 10: Set class name:
          @@ -10 +10
          -         Intent intent = new Intent("some.fake.action.A");
          +         Intent intent = new Intent("some.fake.action.A").setClassName([/* TODO: provide the application ID. For example: */ getPackageName()]|, "test.pkg.TestActivity");
          Fix for src/test/pkg/TestActivity.java line 10: Set package name:
          @@ -10 +10
          -         Intent intent = new Intent("some.fake.action.A");
          +         Intent intent = new Intent("some.fake.action.A").setPackage([/* TODO: provide the application ID. For example: */ getPackageName()]|);
          Fix for src/test/pkg/TestActivity.java line 12: Set class name:
          @@ -12 +12
          -           intent.setAction("some.fake.action.B");
          +           intent.setAction("some.fake.action.B").setClassName([/* TODO: provide the application ID. For example: */ getPackageName()]|, "test.pkg.TestActivity");
          Fix for src/test/pkg/TestActivity.java line 12: Set package name:
          @@ -12 +12
          -           intent.setAction("some.fake.action.B");
          +           intent.setAction("some.fake.action.B").setPackage([/* TODO: provide the application ID. For example: */ getPackageName()]|);
          Fix for src/test/pkg/TestActivity.java line 14: Set class name:
          @@ -14 +14
          -           intent.setAction("some.fake.action.C");
          +           intent.setAction("some.fake.action.C").setClassName([/* TODO: provide the application ID. For example: */ getPackageName()]|, "test.pkg.TestActivity");
          Fix for src/test/pkg/TestActivity.java line 14: Set package name:
          @@ -14 +14
          -           intent.setAction("some.fake.action.C");
          +           intent.setAction("some.fake.action.C").setPackage([/* TODO: provide the application ID. For example: */ getPackageName()]|);
          """
      )
  }

  fun testExplicitIntentWithContextAndClassName_actionNotSet() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;
                import android.content.Context;

                public class TestActivity extends Activity {

                    public void foo(Context context) {
                        Intent intent = new Intent(context, SomeOtherActivity.class);
                        startActivity(intent);
                    }
                }

                class SomeOtherActivity extends Activity {
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                        <activity android:name=".SomeOtherActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.other.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .issues(UnsafeImplicitIntentDetector.ISSUE)
      .run()
      .expectClean()
  }

  fun testDocumentationExample_onTheFlyAnalysis() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent("some.fake.action.LAUNCH");
                        startActivity(intent);
                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .isolated("src/test/pkg/TestActivity.java")
      .run()
      .expect(
        """
            src/test/pkg/TestActivity.java:10: Error: The intent action some.fake.action.LAUNCH (used to start an activity) matches the intent filter of a non-exported component test.pkg.TestActivity from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                    Intent intent = new Intent("some.fake.action.LAUNCH");
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
      .expectFixDiffs(
        """
        Fix for src/test/pkg/TestActivity.java line 10: Set class name:
        @@ -10 +10
        -         Intent intent = new Intent("some.fake.action.LAUNCH");
        +         Intent intent = new Intent("some.fake.action.LAUNCH").setClassName([/* TODO: provide the application ID. For example: */ getPackageName()]|, "test.pkg.TestActivity");
        Fix for src/test/pkg/TestActivity.java line 10: Set package name:
        @@ -10 +10
        -         Intent intent = new Intent("some.fake.action.LAUNCH");
        +         Intent intent = new Intent("some.fake.action.LAUNCH").setPackage([/* TODO: provide the application ID. For example: */ getPackageName()]|);
        """
      )
  }

  fun testImplicitIntentMatchesNonExportedComponent_actionSetFromSetter_onTheFlyAnalysis() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent();
                        intent.setAction("some.fake.action.LAUNCH");
                        startActivity(intent);
                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .isolated("src/test/pkg/TestActivity.java")
      .run()
      .expect(
        """
            src/test/pkg/TestActivity.java:11: Error: The intent action some.fake.action.LAUNCH (used to start an activity) matches the intent filter of a non-exported component test.pkg.TestActivity from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                    intent.setAction("some.fake.action.LAUNCH");
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
      .expectFixDiffs(
        """
            Fix for src/test/pkg/TestActivity.java line 11: Set class name:
            @@ -11 +11
            -         intent.setAction("some.fake.action.LAUNCH");
            +         intent.setAction("some.fake.action.LAUNCH").setClassName([/* TODO: provide the application ID. For example: */ getPackageName()]|, "test.pkg.TestActivity");
            Fix for src/test/pkg/TestActivity.java line 11: Set package name:
            @@ -11 +11
            -         intent.setAction("some.fake.action.LAUNCH");
            +         intent.setAction("some.fake.action.LAUNCH").setPackage([/* TODO: provide the application ID. For example: */ getPackageName()]|);
        """
      )
  }

  fun testIntentMatchesNonExportedComponent_explicitViaSetClass_actionSetFromConstructor_onTheFlyAnalysis() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;
                import android.content.Context;

                public class TestActivity extends Activity {

                    public void foo(Context context) {
                        Intent intent = new Intent("some.fake.action.LAUNCH");
                        intent.setClass(context, SomeActivity.class);
                        startActivity(intent);
                    }
                }

                class SomeActivity extends Activity {
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".SomeActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .issues(UnsafeImplicitIntentDetector.ISSUE)
      .isolated("src/test/pkg/TestActivity.java")
      .run()
      .expectClean()
  }

  fun testIntentMatchesNonExportedComponent_explicitViaSetClassName_actionSetFromConstructor_onTheFlyAnalysis() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;
                import android.content.Context;

                public class TestActivity extends Activity {

                    public void foo(Context context) {
                        Intent intent = new Intent("some.fake.action.LAUNCH");
                        intent.setClassName(context, SomeActivity.class.getName());
                        startActivity(intent);
                    }
                }

                class SomeActivity extends Activity {
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".SomeActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .issues(UnsafeImplicitIntentDetector.ISSUE)
      .isolated("src/test/pkg/TestActivity.java")
      .run()
      .expectClean()
  }

  fun testIntentMatchesNonExportedComponent_explicitViaSetComponent_actionSetFromConstructor_onTheFlyAnalysis() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;
                import android.content.ComponentName;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent("some.fake.action.LAUNCH");
                        intent.setComponent(new ComponentName("test.pkg", "test.pkg.TestActivity.SomeActivity"));
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".SomeActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .issues(UnsafeImplicitIntentDetector.ISSUE)
      .isolated("src/test/pkg/TestActivity.java")
      .run()
      .expectClean()
  }

  fun testIntentMatchesNonExportedComponent_explicitViaSetPackage_actionSetFromConstructor_onTheFlyAnalysis() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent("some.fake.action.LAUNCH");
                        intent.setPackage("test.pkg");
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".SomeActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .issues(UnsafeImplicitIntentDetector.ISSUE)
      .isolated("src/test/pkg/TestActivity.java")
      .run()
      .expectClean()
  }

  fun testIntentMatchesNonExportedComponent_explicitViaSetClass_actionSetFromSetter_onTheFlyAnalysis() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;
                import android.content.Context;

                public class TestActivity extends Activity {

                    public void foo(Context context) {
                        Intent intent = new Intent();
                        intent.setAction("some.fake.action.LAUNCH");
                        intent.setClass(context, SomeActivity.class);
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".SomeActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .issues(UnsafeImplicitIntentDetector.ISSUE)
      .isolated("src/test/pkg/TestActivity.java")
      .run()
      .expectClean()
  }

  fun testIntentMatchesNonExportedComponent_explicitViaSetClassName_actionSetFromSetter_onTheFlyAnalysis() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;
                import android.content.Context;

                public class TestActivity extends Activity {

                    public void foo(Context context) {
                        Intent intent = new Intent();
                        intent.setAction("some.fake.action.LAUNCH");
                        intent.setClassName(context, SomeActivity.class.getName());
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".SomeActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .issues(UnsafeImplicitIntentDetector.ISSUE)
      .isolated("src/test/pkg/TestActivity.java")
      .run()
      .expectClean()
  }

  fun testIntentMatchesNonExportedComponent_explicitViaSetComponent_actionSetFromSetter_onTheFlyAnalysis() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;
                import android.content.ComponentName;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent();
                        intent.setAction("some.fake.action.LAUNCH");
                        intent.setComponent(new ComponentName("test.pkg", "test.pkg.TestActivity.SomeActivity"));
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".SomeActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .issues(UnsafeImplicitIntentDetector.ISSUE)
      .isolated("src/test/pkg/TestActivity.java")
      .run()
      .expectClean()
  }

  fun testIntentMatchesNonExportedComponent_explicitViaSetPackage_actionSetFromSetter_onTheFlyAnalysis() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent();
                        intent.setAction("some.fake.action.LAUNCH");
                        intent.setPackage("test.pkg");
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".SomeActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .issues(UnsafeImplicitIntentDetector.ISSUE)
      .isolated("src/test/pkg/TestActivity.java")
      .run()
      .expectClean()
  }

  fun testImplicitIntentMatchesNonExportedComponent_actionSetFromConstructorThenSetter_onTheFlyAnalysis() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent("some.fake.action.NON_EXISTING_ACTION");
                        intent.setAction("some.fake.action.LAUNCH");
                        startActivity(intent);
                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .isolated("src/test/pkg/TestActivity.java")
      .run()
      .expect(
        """
            src/test/pkg/TestActivity.java:11: Error: The intent action some.fake.action.LAUNCH (used to start an activity) matches the intent filter of a non-exported component test.pkg.TestActivity from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                    intent.setAction("some.fake.action.LAUNCH");
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
      .expectFixDiffs(
        """
        Fix for src/test/pkg/TestActivity.java line 11: Set class name:
        @@ -11 +11
        -         intent.setAction("some.fake.action.LAUNCH");
        +         intent.setAction("some.fake.action.LAUNCH").setClassName([/* TODO: provide the application ID. For example: */ getPackageName()]|, "test.pkg.TestActivity");
        Fix for src/test/pkg/TestActivity.java line 11: Set package name:
        @@ -11 +11
        -         intent.setAction("some.fake.action.LAUNCH");
        +         intent.setAction("some.fake.action.LAUNCH").setPackage([/* TODO: provide the application ID. For example: */ getPackageName()]|);
        """
      )
  }

  fun testImplicitIntentMatchesNonExportedComponent_actionSetMultipleTimes_onTheFlyAnalysis() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent("some.fake.action.A");
                        if (intent.getBooleanExtra("a", false)) {
                          intent.setAction("some.fake.action.B");
                        } else {
                          intent.setAction("some.fake.action.C");
                        }
                        startActivity(intent);
                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.A" />
                            <action android:name="some.fake.action.B" />
                            <action android:name="some.fake.action.C" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .issues(UnsafeImplicitIntentDetector.ISSUE)
      .isolated("src/test/pkg/TestActivity.java")
      .run()
      .expect(
        """
        src/test/pkg/TestActivity.java:10: Error: The intent action some.fake.action.A (used to start an activity) matches the intent filter of a non-exported component test.pkg.TestActivity from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                Intent intent = new Intent("some.fake.action.A");
                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/TestActivity.java:12: Error: The intent action some.fake.action.B (used to start an activity) matches the intent filter of a non-exported component test.pkg.TestActivity from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                  intent.setAction("some.fake.action.B");
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/TestActivity.java:14: Error: The intent action some.fake.action.C (used to start an activity) matches the intent filter of a non-exported component test.pkg.TestActivity from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                  intent.setAction("some.fake.action.C");
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        3 errors, 0 warnings
        """
      )
      .expectFixDiffs(
        """
          Fix for src/test/pkg/TestActivity.java line 10: Set class name:
          @@ -10 +10
          -         Intent intent = new Intent("some.fake.action.A");
          +         Intent intent = new Intent("some.fake.action.A").setClassName([/* TODO: provide the application ID. For example: */ getPackageName()]|, "test.pkg.TestActivity");
          Fix for src/test/pkg/TestActivity.java line 10: Set package name:
          @@ -10 +10
          -         Intent intent = new Intent("some.fake.action.A");
          +         Intent intent = new Intent("some.fake.action.A").setPackage([/* TODO: provide the application ID. For example: */ getPackageName()]|);
          Fix for src/test/pkg/TestActivity.java line 12: Set class name:
          @@ -12 +12
          -           intent.setAction("some.fake.action.B");
          +           intent.setAction("some.fake.action.B").setClassName([/* TODO: provide the application ID. For example: */ getPackageName()]|, "test.pkg.TestActivity");
          Fix for src/test/pkg/TestActivity.java line 12: Set package name:
          @@ -12 +12
          -           intent.setAction("some.fake.action.B");
          +           intent.setAction("some.fake.action.B").setPackage([/* TODO: provide the application ID. For example: */ getPackageName()]|);
          Fix for src/test/pkg/TestActivity.java line 14: Set class name:
          @@ -14 +14
          -           intent.setAction("some.fake.action.C");
          +           intent.setAction("some.fake.action.C").setClassName([/* TODO: provide the application ID. For example: */ getPackageName()]|, "test.pkg.TestActivity");
          Fix for src/test/pkg/TestActivity.java line 14: Set package name:
          @@ -14 +14
          -           intent.setAction("some.fake.action.C");
          +           intent.setAction("some.fake.action.C").setPackage([/* TODO: provide the application ID. For example: */ getPackageName()]|);
          """
      )
  }

  fun testExplicitIntentWithContextAndClassName_actionNotSet_onTheFlyAnalysis() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;
                import android.content.Context;

                public class TestActivity extends Activity {

                    public void foo(Context context) {
                        Intent intent = new Intent(context, TestActivity.class);
                        startActivity(intent);
                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                        <activity android:name=".SomeOtherActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.other.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .issues(UnsafeImplicitIntentDetector.ISSUE)
      .isolated("src/test/pkg/TestActivity.java")
      .run()
      .expectClean()
  }

  fun testImplicitIntentMatchesNonExportedComponent_actionSetFromSetter_kotlin() {
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.content.Intent
                import android.app.Activity

                class TestActivity : Activity {

                    override fun onCreate(savedInstanceState: Bundle) {
                        val intent = Intent()
                        intent.setAction("some.fake.action.LAUNCH")
                        startActivity(intent)
                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .run()
      .expect(
        """
            src/test/pkg/TestActivity.kt:10: Error: The intent action some.fake.action.LAUNCH (used to start an activity) matches the intent filter of a non-exported component test.pkg.TestActivity from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                    intent.setAction("some.fake.action.LAUNCH")
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
      .expectFixDiffs(
        """
        Fix for src/test/pkg/TestActivity.kt line 10: Set class name:
        @@ -10 +10
        -         intent.setAction("some.fake.action.LAUNCH")
        +         intent.setAction("some.fake.action.LAUNCH").setClassName([/* TODO: provide the application ID. For example: */ packageName]|, "test.pkg.TestActivity")
        Fix for src/test/pkg/TestActivity.kt line 10: Set package name:
        @@ -10 +10
        -         intent.setAction("some.fake.action.LAUNCH")
        +         intent.setAction("some.fake.action.LAUNCH").setPackage([/* TODO: provide the application ID. For example: */ packageName]|)
        """
      )
  }

  fun testImplicitIntentMatchesNonExportedComponent_actionSetFromSetter_kotlin_onTheFlyAnalysis() {
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.content.Intent
                import android.app.Activity

                class TestActivity : Activity {

                    override fun onCreate(savedInstanceState: Bundle) {
                        val intent = Intent()
                        intent.setAction("some.fake.action.LAUNCH")
                        startActivity(intent)
                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .isolated("src/test/pkg/TestActivity.kt")
      .run()
      .expect(
        """
            src/test/pkg/TestActivity.kt:10: Error: The intent action some.fake.action.LAUNCH (used to start an activity) matches the intent filter of a non-exported component test.pkg.TestActivity from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                    intent.setAction("some.fake.action.LAUNCH")
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
      .expectFixDiffs(
        """
        Fix for src/test/pkg/TestActivity.kt line 10: Set class name:
        @@ -10 +10
        -         intent.setAction("some.fake.action.LAUNCH")
        +         intent.setAction("some.fake.action.LAUNCH").setClassName([/* TODO: provide the application ID. For example: */ packageName]|, "test.pkg.TestActivity")
        Fix for src/test/pkg/TestActivity.kt line 10: Set package name:
        @@ -10 +10
        -         intent.setAction("some.fake.action.LAUNCH")
        +         intent.setAction("some.fake.action.LAUNCH").setPackage([/* TODO: provide the application ID. For example: */ packageName]|)
        """
      )
  }

  fun testImplicitIntentMatchesNonExportedComponent_actionSetFromConstructor_kotlin() {
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.content.Intent
                import android.app.Activity

                class TestActivity : Activity {

                    override fun onCreate(savedInstanceState: Bundle) {
                        val intent = Intent("some.fake.action.LAUNCH")
                        startActivity(intent)
                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .run()
      .expect(
        """
            src/test/pkg/TestActivity.kt:9: Error: The intent action some.fake.action.LAUNCH (used to start an activity) matches the intent filter of a non-exported component test.pkg.TestActivity from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                    val intent = Intent("some.fake.action.LAUNCH")
                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
      .expectFixDiffs(
        """
        Fix for src/test/pkg/TestActivity.kt line 9: Set class name:
        @@ -9 +9
        -         val intent = Intent("some.fake.action.LAUNCH")
        +         val intent = Intent("some.fake.action.LAUNCH").setClassName([/* TODO: provide the application ID. For example: */ packageName]|, "test.pkg.TestActivity")
        Fix for src/test/pkg/TestActivity.kt line 9: Set package name:
        @@ -9 +9
        -         val intent = Intent("some.fake.action.LAUNCH")
        +         val intent = Intent("some.fake.action.LAUNCH").setPackage([/* TODO: provide the application ID. For example: */ packageName]|)
        """
      )
  }

  fun testImplicitIntentMatchesNonExportedComponent_actionSetFromConstructor_kotlin_onTheFlyAnalysis() {
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.content.Intent
                import android.app.Activity

                class TestActivity : Activity {

                    override fun onCreate(savedInstanceState: Bundle) {
                        val intent = Intent("some.fake.action.LAUNCH")
                        startActivity(intent)
                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.LAUNCH" />
                            <category android:name="android.intent.category.LAUNCHER" />
                          </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .isolated("src/test/pkg/TestActivity.kt")
      .run()
      .expect(
        """
            src/test/pkg/TestActivity.kt:9: Error: The intent action some.fake.action.LAUNCH (used to start an activity) matches the intent filter of a non-exported component test.pkg.TestActivity from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                    val intent = Intent("some.fake.action.LAUNCH")
                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
      .expectFixDiffs(
        """
        Fix for src/test/pkg/TestActivity.kt line 9: Set class name:
        @@ -9 +9
        -         val intent = Intent("some.fake.action.LAUNCH")
        +         val intent = Intent("some.fake.action.LAUNCH").setClassName([/* TODO: provide the application ID. For example: */ packageName]|, "test.pkg.TestActivity")
        Fix for src/test/pkg/TestActivity.kt line 9: Set package name:
        @@ -9 +9
        -         val intent = Intent("some.fake.action.LAUNCH")
        +         val intent = Intent("some.fake.action.LAUNCH").setPackage([/* TODO: provide the application ID. For example: */ packageName]|)
        """
      )
  }

  fun testImplicitIntentMatchesNonExportedComponent_multipleProjects() {
    lint()
      .projects(
        project()
          .name("lib")
          .type(ProjectDescription.Type.LIBRARY)
          .files(
            kotlin(
                """
                    package test.pkg.lib

                    import android.content.Intent
                    import android.app.Activity

                    class TestActivityLib : Activity {

                        override fun onCreate(savedInstanceState: Bundle) {
                            startActivity(Intent().setAction("some.fake.action.A"))
                            sendBroadcast(Intent().setAction("some.fake.action.B"))
                        }
                    }
                """
              )
              .indented()
          ),
        project()
          .name("app")
          .dependsOn("lib")
          .type(ProjectDescription.Type.APP)
          .files(
            kotlin(
                """
                    package test.pkg.app

                    import android.content.Intent
                    import android.app.Activity

                    class TestActivityApp : Activity {

                        override fun onCreate(savedInstanceState: Bundle) {
                            startActivity(Intent().setAction("some.fake.action.C"))
                            sendBroadcast(Intent().setAction("some.fake.action.D"))
                        }
                    }
                """
              )
              .indented(),
            manifest(
                """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                            package="test.pkg">
                        <application>
                            <activity android:name=".MyActivity" android:exported="true">
                              <intent-filter>
                                <action android:name="some.fake.action.A" />
                                <action android:name="some.fake.action.C" />
                              </intent-filter>
                            </activity>
                            <receiver android:name=".MyReceiver" android:exported="false">
                              <intent-filter>
                                <action android:name="some.fake.action.B" />
                                <action android:name="some.fake.action.D" />
                              </intent-filter>
                            </receiver>
                        </application>
                    </manifest>
                    """
              )
              .indented(),
          ),
      )
      .run()
      .expect(
        """
            src/test/pkg/app/TestActivityApp.kt:10: Error: The intent action some.fake.action.D (used to send a broadcast) matches the intent filter of a non-exported component test.pkg.MyReceiver from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                    sendBroadcast(Intent().setAction("some.fake.action.D"))
                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            ../lib/src/test/pkg/lib/TestActivityLib.kt:10: Error: The intent action some.fake.action.B (used to send a broadcast) matches the intent filter of a non-exported component test.pkg.MyReceiver from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                    sendBroadcast(Intent().setAction("some.fake.action.B"))
                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
      )
      .expectFixDiffs(
        """
            Fix for src/test/pkg/app/TestActivityApp.kt line 10: Set class name:
            @@ -10 +10
            -         sendBroadcast(Intent().setAction("some.fake.action.D"))
            +         sendBroadcast(Intent().setAction("some.fake.action.D").setClassName([/* TODO: provide the application ID. For example: */ packageName]|, "test.pkg.MyReceiver"))
            Fix for src/test/pkg/app/TestActivityApp.kt line 10: Set package name:
            @@ -10 +10
            -         sendBroadcast(Intent().setAction("some.fake.action.D"))
            +         sendBroadcast(Intent().setAction("some.fake.action.D").setPackage([/* TODO: provide the application ID. For example: */ packageName]|))
            Fix for lib/src/test/pkg/lib/TestActivityLib.kt line 10: Set class name:
            @@ -10 +10
            -         sendBroadcast(Intent().setAction("some.fake.action.B"))
            +         sendBroadcast(Intent().setAction("some.fake.action.B").setClassName([/* TODO: provide the application ID. For example: */ packageName]|, "test.pkg.MyReceiver"))
            Fix for lib/src/test/pkg/lib/TestActivityLib.kt line 10: Set package name:
            @@ -10 +10
            -         sendBroadcast(Intent().setAction("some.fake.action.B"))
            +         sendBroadcast(Intent().setAction("some.fake.action.B").setPackage([/* TODO: provide the application ID. For example: */ packageName]|))
            """
      )
  }

  fun testImplicitIntentMultipleProjectsWithManifestAndRegisteredReceivers() {
    lint()
      .projects(
        project()
          .name("lib")
          .type(ProjectDescription.Type.LIBRARY)
          .files(
            kotlin(
                """
                    package com.lib

                    import android.annotation.SuppressLint
                    import android.content.Intent
                    import android.content.IntentFilter
                    import android.app.Activity
                    import android.content.Context

                    class LibActivity : Activity {

                        fun foo(context: Context) {
                            startActivity(Intent("com.lib.manifest.receiver.action")) // 1
                            sendBroadcast(Intent("com.lib.manifest.receiver.action")) // 2
                            startActivity(Intent("com.lib.manifest.receiver.action2")) // 3
                            sendBroadcast(Intent("com.lib.manifest.receiver.action2")) // 4

                            startActivity(Intent("com.lib.dynamic.receiver.action")) // 5
                            sendBroadcast(Intent("com.lib.dynamic.receiver.action")) // 6
                            startActivity(Intent("com.lib.dynamic.receiver.action2")) // 7
                            sendBroadcast(Intent("com.lib.dynamic.receiver.action2")) // 8

                            startActivity(Intent("com.app.manifest.receiver.action")) // 9
                            sendBroadcast(Intent("com.app.manifest.receiver.action")) // 10
                            startActivity(Intent("com.app.manifest.receiver.action2")) // 11
                            sendBroadcast(Intent("com.app.manifest.receiver.action2")) // 12

                            startActivity(Intent("com.app.dynamic.receiver.action")) // 13
                            sendBroadcast(Intent("com.app.dynamic.receiver.action")) // 14
                            startActivity(Intent("com.app.dynamic.receiver.action2")) // 15
                            sendBroadcast(Intent("com.app.dynamic.receiver.action2")) // 16

                            val intentFilter = IntentFilter.create("com.lib.dynamic.receiver.action", "")
                            intentFilter.addAction("com.lib.dynamic.receiver.action2")
                            registerReceiver(context, intentFilter, Context.RECEIVER_NOT_EXPORTED)
                        }

                        @SuppressLint("UnsafeImplicitIntentLaunch")
                        fun aFunction() {
                            startActivity(Intent("com.lib.manifest.receiver.action")) // ignored
                        }
                    }
                """
              )
              .indented(),
            manifest(
                """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.lib">
                        <application>
                            <receiver android:name="x.lib.Receiver" android:exported="false">
                              <intent-filter>
                                <action android:name="com.lib.manifest.receiver.action" />
                                <action android:name="com.lib.manifest.receiver.action2" />
                              </intent-filter>
                            </receiver>
                        </application>
                    </manifest>
                    """
              )
              .indented(),
          ),
        project()
          .name("app")
          .dependsOn("lib")
          .type(ProjectDescription.Type.APP)
          .files(
            java(
              """
              package androidx.core.content;

              import android.content.Context;
              import android.content.BroadcastReceiver;
              import android.content.IntentFilter;

              public class ContextCompat {
                public static final int RECEIVER_NOT_EXPORTED = 4;
                public static Intent registerReceiver(Context context, BroadcastReceiver receiver, IntentFilter filter, int flags) {

                }
              }
              """
            ),
            kotlin(
                """
                    package com.app

                    import android.annotation.SuppressLint
                    import android.content.Intent
                    import android.content.IntentFilter
                    import android.app.Activity
                    import androidx.core.content.ContextCompat

                    class AppActivity : Activity {

                        override fun onCreate(savedInstanceState: Bundle) {
                            startActivity(Intent("com.lib.manifest.receiver.action")) // 17
                            sendBroadcast(Intent("com.lib.manifest.receiver.action")) // 18
                            startActivity(Intent("com.lib.manifest.receiver.action2")) // 19
                            sendBroadcast(Intent("com.lib.manifest.receiver.action2")) // 20

                            startActivity(Intent("com.lib.dynamic.receiver.action")) // 21
                            sendBroadcast(Intent("com.lib.dynamic.receiver.action")) // 22
                            startActivity(Intent("com.lib.dynamic.receiver.action2")) // 23
                            sendBroadcast(Intent("com.lib.dynamic.receiver.action2")) // 24

                            startActivity(Intent("com.app.manifest.receiver.action")) // 25
                            sendBroadcast(Intent("com.app.manifest.receiver.action")) // 26
                            startActivity(Intent("com.app.manifest.receiver.action2")) // 27
                            sendBroadcast(Intent("com.app.manifest.receiver.action2")) // 28

                            startActivity(Intent("com.app.dynamic.receiver.action")) // 29
                            sendBroadcast(Intent("com.app.dynamic.receiver.action")) // 30
                            startActivity(Intent("com.app.dynamic.receiver.action2")) // 31
                            sendBroadcast(Intent("com.app.dynamic.receiver.action2")) // 32

                            val intentFilter = IntentFilter.create("com.app.dynamic.receiver.action", "")
                            intentFilter.addAction("com.app.dynamic.receiver.action2")
                            ContextCompat.registerReceiver(
                              this,
                              null,
                              intentFilter,
                              ContextCompat.RECEIVER_NOT_EXPORTED,
                            )
                        }

                        @SuppressLint("UnsafeImplicitIntentLaunch")
                        fun aFunction() {
                            startActivity(Intent("com.app.manifest.receiver.action")) // ignored
                        }
                    }
                """
              )
              .indented(),
            manifest(
                """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.app">
                        <application>
                            <receiver android:name="x.app.Receiver" android:exported="false">
                              <intent-filter>
                                <action android:name="com.app.manifest.receiver.action" />
                                <action android:name="com.app.manifest.receiver.action2" />
                              </intent-filter>
                            </receiver>
                        </application>
                    </manifest>
                    """
              )
              .indented(),
          ),
      )
      .run()
      .expect(
        """
src/com/app/AppActivity.kt:13: Error: The intent action com.lib.manifest.receiver.action (used to send a broadcast) matches the intent filter of a non-exported component x.lib.Receiver from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
        sendBroadcast(Intent("com.lib.manifest.receiver.action")) // 18
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
src/com/app/AppActivity.kt:15: Error: The intent action com.lib.manifest.receiver.action2 (used to send a broadcast) matches the intent filter of a non-exported component x.lib.Receiver from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
        sendBroadcast(Intent("com.lib.manifest.receiver.action2")) // 20
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
src/com/app/AppActivity.kt:18: Error: The intent action com.lib.dynamic.receiver.action (used to send a broadcast) matches the intent filter of a non-exported receiver, registered via a call to Context.registerReceiver, or similar. If you are trying to invoke this specific receiver via the action then you should use Intent.setPackage(<APPLICATION_ID>). [UnsafeImplicitIntentLaunch]
        sendBroadcast(Intent("com.lib.dynamic.receiver.action")) // 22
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
src/com/app/AppActivity.kt:20: Error: The intent action com.lib.dynamic.receiver.action2 (used to send a broadcast) matches the intent filter of a non-exported receiver, registered via a call to Context.registerReceiver, or similar. If you are trying to invoke this specific receiver via the action then you should use Intent.setPackage(<APPLICATION_ID>). [UnsafeImplicitIntentLaunch]
        sendBroadcast(Intent("com.lib.dynamic.receiver.action2")) // 24
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
src/com/app/AppActivity.kt:23: Error: The intent action com.app.manifest.receiver.action (used to send a broadcast) matches the intent filter of a non-exported component x.app.Receiver from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
        sendBroadcast(Intent("com.app.manifest.receiver.action")) // 26
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
src/com/app/AppActivity.kt:25: Error: The intent action com.app.manifest.receiver.action2 (used to send a broadcast) matches the intent filter of a non-exported component x.app.Receiver from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
        sendBroadcast(Intent("com.app.manifest.receiver.action2")) // 28
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
src/com/app/AppActivity.kt:28: Error: The intent action com.app.dynamic.receiver.action (used to send a broadcast) matches the intent filter of a non-exported receiver, registered via a call to Context.registerReceiver, or similar. If you are trying to invoke this specific receiver via the action then you should use Intent.setPackage(<APPLICATION_ID>). [UnsafeImplicitIntentLaunch]
        sendBroadcast(Intent("com.app.dynamic.receiver.action")) // 30
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
src/com/app/AppActivity.kt:30: Error: The intent action com.app.dynamic.receiver.action2 (used to send a broadcast) matches the intent filter of a non-exported receiver, registered via a call to Context.registerReceiver, or similar. If you are trying to invoke this specific receiver via the action then you should use Intent.setPackage(<APPLICATION_ID>). [UnsafeImplicitIntentLaunch]
        sendBroadcast(Intent("com.app.dynamic.receiver.action2")) // 32
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
../lib/src/com/lib/LibActivity.kt:13: Error: The intent action com.lib.manifest.receiver.action (used to send a broadcast) matches the intent filter of a non-exported component x.lib.Receiver from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
        sendBroadcast(Intent("com.lib.manifest.receiver.action")) // 2
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
../lib/src/com/lib/LibActivity.kt:15: Error: The intent action com.lib.manifest.receiver.action2 (used to send a broadcast) matches the intent filter of a non-exported component x.lib.Receiver from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
        sendBroadcast(Intent("com.lib.manifest.receiver.action2")) // 4
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
../lib/src/com/lib/LibActivity.kt:18: Error: The intent action com.lib.dynamic.receiver.action (used to send a broadcast) matches the intent filter of a non-exported receiver, registered via a call to Context.registerReceiver, or similar. If you are trying to invoke this specific receiver via the action then you should use Intent.setPackage(<APPLICATION_ID>). [UnsafeImplicitIntentLaunch]
        sendBroadcast(Intent("com.lib.dynamic.receiver.action")) // 6
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
../lib/src/com/lib/LibActivity.kt:20: Error: The intent action com.lib.dynamic.receiver.action2 (used to send a broadcast) matches the intent filter of a non-exported receiver, registered via a call to Context.registerReceiver, or similar. If you are trying to invoke this specific receiver via the action then you should use Intent.setPackage(<APPLICATION_ID>). [UnsafeImplicitIntentLaunch]
        sendBroadcast(Intent("com.lib.dynamic.receiver.action2")) // 8
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
../lib/src/com/lib/LibActivity.kt:23: Error: The intent action com.app.manifest.receiver.action (used to send a broadcast) matches the intent filter of a non-exported component x.app.Receiver from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
        sendBroadcast(Intent("com.app.manifest.receiver.action")) // 10
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
../lib/src/com/lib/LibActivity.kt:25: Error: The intent action com.app.manifest.receiver.action2 (used to send a broadcast) matches the intent filter of a non-exported component x.app.Receiver from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
        sendBroadcast(Intent("com.app.manifest.receiver.action2")) // 12
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
../lib/src/com/lib/LibActivity.kt:28: Error: The intent action com.app.dynamic.receiver.action (used to send a broadcast) matches the intent filter of a non-exported receiver, registered via a call to Context.registerReceiver, or similar. If you are trying to invoke this specific receiver via the action then you should use Intent.setPackage(<APPLICATION_ID>). [UnsafeImplicitIntentLaunch]
        sendBroadcast(Intent("com.app.dynamic.receiver.action")) // 14
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
../lib/src/com/lib/LibActivity.kt:30: Error: The intent action com.app.dynamic.receiver.action2 (used to send a broadcast) matches the intent filter of a non-exported receiver, registered via a call to Context.registerReceiver, or similar. If you are trying to invoke this specific receiver via the action then you should use Intent.setPackage(<APPLICATION_ID>). [UnsafeImplicitIntentLaunch]
        sendBroadcast(Intent("com.app.dynamic.receiver.action2")) // 16
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
16 errors, 0 warnings
            """
      )
  }

  fun testImplicitIntentDifferentTypesOfComponents() {
    // Checks that an intent/action can be used to start an activity, send a
    // broadcast, and start a service, and that warnings will be triggered if
    // and only if there is a non-exported component of the corresponding type.
    // Services are always ignored, as an exception is thrown for implicit
    // service intents since Lollipop
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent();
                        intent.setAction("some.fake.action.A");
                        startActivity(intent);
                        sendBroadcast(intent);
                        startService(intent);

                        Intent intentActivity = new Intent("some.fake.action.MyActivity");

                        startActivity(intentActivity);
                        sendBroadcast(intentActivity);
                        startService(intentActivity);

                        Intent intentBroadcast = new Intent("some.fake.action.MyReceiver");

                        startActivity(intentBroadcast);
                        sendBroadcast(intentBroadcast);
                        startService(intentBroadcast);

                        Intent intentService = new Intent("some.fake.action.MyService");

                        startActivity(intentService);
                        sendBroadcast(intentService);
                        startService(intentService);
                    }
                }
            """
          )
          .indented(),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="com.app">
                    <application>
                        <activity android:name="x.app.MyActivity" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.A" />

                            <action android:name="some.fake.action.MyActivity" />

                          </intent-filter>
                        </activity>
                        <receiver android:name="x.app.MyReceiver" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.A" />

                            <action android:name="some.fake.action.MyReceiver" />
                          </intent-filter>
                        </receiver>
                        <service android:name="x.app.MyService" android:exported="false">
                          <intent-filter>
                            <action android:name="some.fake.action.A" />

                            <action android:name="some.fake.action.MyService" />
                          </intent-filter>
                        </service>
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .run()
      .expect(
        """
            src/test/pkg/TestActivity.java:11: Error: The intent action some.fake.action.A (used to send a broadcast) matches the intent filter of a non-exported component x.app.MyReceiver from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                    intent.setAction("some.fake.action.A");
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestActivity.java:11: Error: The intent action some.fake.action.A (used to start an activity) matches the intent filter of a non-exported component x.app.MyActivity from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                    intent.setAction("some.fake.action.A");
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestActivity.java:16: Error: The intent action some.fake.action.MyActivity (used to start an activity) matches the intent filter of a non-exported component x.app.MyActivity from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                    Intent intentActivity = new Intent("some.fake.action.MyActivity");
                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestActivity.java:22: Error: The intent action some.fake.action.MyReceiver (used to send a broadcast) matches the intent filter of a non-exported component x.app.MyReceiver from a manifest. If you are trying to invoke this specific component via the action then you should make the intent explicit by calling Intent.set{Component,Class,ClassName}. [UnsafeImplicitIntentLaunch]
                    Intent intentBroadcast = new Intent("some.fake.action.MyReceiver");
                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            4 errors, 0 warnings
            """
      )
  }
}
