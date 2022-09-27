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

class UnsafeImplicitIntentDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector = UnsafeImplicitIntentDetector()

    fun testDocumentationExample() {
        lint().files(
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
            ).indented(),
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
            ).indented(),
        ).run().expect(
            """
            src/test/pkg/TestActivity.java:10: Error: This intent matches an internal non-exported component.
            If you are trying to invoke the component matching the action some.fake.action.LAUNCH,
            then you should use an explicit intent. [UnsafeImplicitIntentLaunch]
                    Intent intent = new Intent("some.fake.action.LAUNCH");
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        ).expectFixDiffs("""
            Fix for src/test/pkg/TestActivity.java line 10: Set package name:
            @@ -10 +10
            -         Intent intent = new Intent("some.fake.action.LAUNCH");
            +         Intent intent = new Intent("some.fake.action.LAUNCH").setPackage("test.pkg");
            Fix for src/test/pkg/TestActivity.java line 10: Set class name:
            @@ -10 +10
            -         Intent intent = new Intent("some.fake.action.LAUNCH");
            +         Intent intent = new Intent("some.fake.action.LAUNCH").setClassName("test.pkg", "test.pkg.TestActivity");
        """)
    }

    fun testImplicitIntentMatchesNonExportedComponent_actionSetFromSetter() {
        lint().files(
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
            ).indented(),
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
            ).indented(),
        ).run().expect(
            """
            src/test/pkg/TestActivity.java:11: Error: This intent matches an internal non-exported component.
            If you are trying to invoke the component matching the action some.fake.action.LAUNCH,
            then you should use an explicit intent. [UnsafeImplicitIntentLaunch]
                    intent.setAction("some.fake.action.LAUNCH");
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        ).expectFixDiffs("""
            Fix for src/test/pkg/TestActivity.java line 11: Set package name:
            @@ -11 +11
            -         intent.setAction("some.fake.action.LAUNCH");
            +         intent.setAction("some.fake.action.LAUNCH").setPackage("test.pkg");
            Fix for src/test/pkg/TestActivity.java line 11: Set class name:
            @@ -11 +11
            -         intent.setAction("some.fake.action.LAUNCH");
            +         intent.setAction("some.fake.action.LAUNCH").setClassName("test.pkg", "test.pkg.TestActivity");
        """)
    }

    fun testIntentMatchesNonExportedComponent_explicitViaSetClass_actionSetFromConstructor() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent("some.fake.action.LAUNCH");
                        intent.setClass(this, SomeActivity.class);
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
            ).indented(),
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
            ).indented(),
        ).issues(UnsafeImplicitIntentDetector.ISSUE).run().expectClean()
    }

    fun testIntentMatchesNonExportedComponent_explicitViaSetClassName_actionSetFromConstructor() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent("some.fake.action.LAUNCH");
                        intent.setClassName(this, SomeActivity.class.getCanonicalName());
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
            ).indented(),
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
            ).indented(),
        ).issues(UnsafeImplicitIntentDetector.ISSUE).run().expectClean()
    }

    fun testIntentMatchesNonExportedComponent_explicitViaSetComponent_actionSetFromConstructor() {
        lint().files(
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
            ).indented(),
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
            ).indented(),
        ).issues(UnsafeImplicitIntentDetector.ISSUE).run().expectClean()
    }

    fun testIntentMatchesNonExportedComponent_explicitViaSetPackage_actionSetFromConstructor() {
        lint().files(
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
            ).indented(),
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
            ).indented(),
        ).issues(UnsafeImplicitIntentDetector.ISSUE).run().expectClean()
    }

    fun testIntentMatchesNonExportedComponent_explicitViaSetClass_actionSetFromSetter() {
        lint().files(
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
                        intent.setClass(this, SomeActivity.class);
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
            ).indented(),
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
            ).indented(),
        ).issues(UnsafeImplicitIntentDetector.ISSUE).run().expectClean()
    }

    fun testIntentMatchesNonExportedComponent_explicitViaSetClassName_actionSetFromSetter() {
        lint().files(
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
                        intent.setClassName(this, SomeActivity.class.getCanonicalName());
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
            ).indented(),
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
            ).indented(),
        ).issues(UnsafeImplicitIntentDetector.ISSUE).run().expectClean()
    }

    fun testIntentMatchesNonExportedComponent_explicitViaSetComponent_actionSetFromSetter() {
        lint().files(
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
            ).indented(),
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
            ).indented(),
        ).issues(UnsafeImplicitIntentDetector.ISSUE).run().expectClean()
    }

    fun testIntentMatchesNonExportedComponent_explicitViaSetPackage_actionSetFromSetter() {
        lint().files(
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
            ).indented(),
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
            ).indented(),
        ).issues(UnsafeImplicitIntentDetector.ISSUE).run().expectClean()
    }

    fun testImplicitIntentMatchesNonExportedComponent_actionSetFromConstructorThenSetter() {
        lint().files(
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
            ).indented(),
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
            ).indented(),
        ).run().expect(
            """
            src/test/pkg/TestActivity.java:11: Error: This intent matches an internal non-exported component.
            If you are trying to invoke the component matching the action some.fake.action.LAUNCH,
            then you should use an explicit intent. [UnsafeImplicitIntentLaunch]
                    intent.setAction("some.fake.action.LAUNCH");
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        ).expectFixDiffs("""
            Fix for src/test/pkg/TestActivity.java line 11: Set package name:
            @@ -11 +11
            -         intent.setAction("some.fake.action.LAUNCH");
            +         intent.setAction("some.fake.action.LAUNCH").setPackage("test.pkg");
            Fix for src/test/pkg/TestActivity.java line 11: Set class name:
            @@ -11 +11
            -         intent.setAction("some.fake.action.LAUNCH");
            +         intent.setAction("some.fake.action.LAUNCH").setClassName("test.pkg", "test.pkg.TestActivity");
        """)
    }

    fun testImplicitIntentDoesNotMatchNonExportedComponent_actionSetFromConstructorThenSetter() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent("some.fake.action.LAUNCH");
                        intent.setAction("some.fake.action.NON_EXISTING_ACTION");
                        startActivity(intent);
                    }
                }
            """
            ).indented(),
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
            ).indented(),
        ).issues(UnsafeImplicitIntentDetector.ISSUE).run().expectClean()
    }

    fun testExplicitIntentWithContextAndClassName_actionNotSet() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent(getContext(), SomeOtherActivity.class);
                        startActivity(intent);
                    }
                }

                public class SomeOtherActivity extends Activity {
                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                    }
                }
            """
            ).indented(),
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
            ).indented(),
        ).issues(UnsafeImplicitIntentDetector.ISSUE).run().expectClean()
    }

    fun testDocumentationExample_onTheFlyAnalysis() {
        lint().files(
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
            ).indented(),
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
            ).indented(),
        ).isolated("src/test/pkg/TestActivity.java").run().expect(
            """
            src/test/pkg/TestActivity.java:10: Error: This intent matches an internal non-exported component.
            If you are trying to invoke the component matching the action some.fake.action.LAUNCH,
            then you should use an explicit intent. [UnsafeImplicitIntentLaunch]
                    Intent intent = new Intent("some.fake.action.LAUNCH");
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        ).expectFixDiffs("""
            Fix for src/test/pkg/TestActivity.java line 10: Set package name:
            @@ -10 +10
            -         Intent intent = new Intent("some.fake.action.LAUNCH");
            +         Intent intent = new Intent("some.fake.action.LAUNCH").setPackage("test.pkg");
            Fix for src/test/pkg/TestActivity.java line 10: Set class name:
            @@ -10 +10
            -         Intent intent = new Intent("some.fake.action.LAUNCH");
            +         Intent intent = new Intent("some.fake.action.LAUNCH").setClassName("test.pkg", "test.pkg.TestActivity");
        """)
    }

    fun testImplicitIntentMatchesNonExportedComponent_actionSetFromSetter_onTheFlyAnalysis() {
        lint().files(
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
            ).indented(),
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
            ).indented(),
        ).isolated("src/test/pkg/TestActivity.java").run().expect(
            """
            src/test/pkg/TestActivity.java:11: Error: This intent matches an internal non-exported component.
            If you are trying to invoke the component matching the action some.fake.action.LAUNCH,
            then you should use an explicit intent. [UnsafeImplicitIntentLaunch]
                    intent.setAction("some.fake.action.LAUNCH");
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        ).expectFixDiffs("""
            Fix for src/test/pkg/TestActivity.java line 11: Set package name:
            @@ -11 +11
            -         intent.setAction("some.fake.action.LAUNCH");
            +         intent.setAction("some.fake.action.LAUNCH").setPackage("test.pkg");
            Fix for src/test/pkg/TestActivity.java line 11: Set class name:
            @@ -11 +11
            -         intent.setAction("some.fake.action.LAUNCH");
            +         intent.setAction("some.fake.action.LAUNCH").setClassName("test.pkg", "test.pkg.TestActivity");
        """)
    }

    fun testIntentMatchesNonExportedComponent_explicitViaSetClass_actionSetFromConstructor_onTheFlyAnalysis() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent("some.fake.action.LAUNCH");
                        intent.setClass(this, SomeActivity.class);
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
            ).indented(),
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
            ).indented(),
        ).issues(UnsafeImplicitIntentDetector.ISSUE).isolated("src/test/pkg/TestActivity.java").run().expectClean()
    }

    fun testIntentMatchesNonExportedComponent_explicitViaSetClassName_actionSetFromConstructor_onTheFlyAnalysis() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent("some.fake.action.LAUNCH");
                        intent.setClassName(this, SomeActivity.class.getCanonicalName());
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
            ).indented(),
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
            ).indented(),
        ).issues(UnsafeImplicitIntentDetector.ISSUE).isolated("src/test/pkg/TestActivity.java").run().expectClean()
    }

    fun testIntentMatchesNonExportedComponent_explicitViaSetComponent_actionSetFromConstructor_onTheFlyAnalysis() {
        lint().files(
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
            ).indented(),
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
            ).indented(),
        ).issues(UnsafeImplicitIntentDetector.ISSUE).isolated("src/test/pkg/TestActivity.java").run().expectClean()
    }

    fun testIntentMatchesNonExportedComponent_explicitViaSetPackage_actionSetFromConstructor_onTheFlyAnalysis() {
        lint().files(
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
            ).indented(),
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
            ).indented(),
        ).issues(UnsafeImplicitIntentDetector.ISSUE).isolated("src/test/pkg/TestActivity.java").run().expectClean()
    }

    fun testIntentMatchesNonExportedComponent_explicitViaSetClass_actionSetFromSetter_onTheFlyAnalysis() {
        lint().files(
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
                        intent.setClass(this, SomeActivity.class);
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
            ).indented(),
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
            ).indented(),
        ).issues(UnsafeImplicitIntentDetector.ISSUE).isolated("src/test/pkg/TestActivity.java").run().expectClean()
    }

    fun testIntentMatchesNonExportedComponent_explicitViaSetClassName_actionSetFromSetter_onTheFlyAnalysis() {
        lint().files(
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
                        intent.setClassName(this, SomeActivity.class.getCanonicalName());
                        startActivity(intent);
                    }

                    private static class SomeActivity extends Activity {

                    }
                }
            """
            ).indented(),
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
            ).indented(),
        ).issues(UnsafeImplicitIntentDetector.ISSUE).isolated("src/test/pkg/TestActivity.java").run().expectClean()
    }

    fun testIntentMatchesNonExportedComponent_explicitViaSetComponent_actionSetFromSetter_onTheFlyAnalysis() {
        lint().files(
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
            ).indented(),
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
            ).indented(),
        ).issues(UnsafeImplicitIntentDetector.ISSUE).isolated("src/test/pkg/TestActivity.java").run().expectClean()
    }

    fun testIntentMatchesNonExportedComponent_explicitViaSetPackage_actionSetFromSetter_onTheFlyAnalysis() {
        lint().files(
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
            ).indented(),
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
            ).indented(),
        ).issues(UnsafeImplicitIntentDetector.ISSUE).isolated("src/test/pkg/TestActivity.java").run().expectClean()
    }

    fun testImplicitIntentMatchesNonExportedComponent_actionSetFromConstructorThenSetter_onTheFlyAnalysis() {
        lint().files(
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
            ).indented(),
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
            ).indented(),
        ).isolated("src/test/pkg/TestActivity.java").run().expect(
            """
            src/test/pkg/TestActivity.java:11: Error: This intent matches an internal non-exported component.
            If you are trying to invoke the component matching the action some.fake.action.LAUNCH,
            then you should use an explicit intent. [UnsafeImplicitIntentLaunch]
                    intent.setAction("some.fake.action.LAUNCH");
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        ).expectFixDiffs("""
            Fix for src/test/pkg/TestActivity.java line 11: Set package name:
            @@ -11 +11
            -         intent.setAction("some.fake.action.LAUNCH");
            +         intent.setAction("some.fake.action.LAUNCH").setPackage("test.pkg");
            Fix for src/test/pkg/TestActivity.java line 11: Set class name:
            @@ -11 +11
            -         intent.setAction("some.fake.action.LAUNCH");
            +         intent.setAction("some.fake.action.LAUNCH").setClassName("test.pkg", "test.pkg.TestActivity");
        """)
    }

    fun testImplicitIntentDoesNotMatchNonExportedComponent_actionSetFromConstructorThenSetter_onTheFlyAnalysis() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent("some.fake.action.LAUNCH");
                        intent.setAction("some.fake.action.NON_EXISTING_ACTION");
                        startActivity(intent);
                    }
                }
            """
            ).indented(),
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
            ).indented(),
        ).issues(UnsafeImplicitIntentDetector.ISSUE).isolated("src/test/pkg/TestActivity.java").run().expectClean()
    }

    fun testExplicitIntentWithContextAndClassName_actionNotSet_onTheFlyAnalysis() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = new Intent(getContext(), SomeOtherActivity.class);
                        startActivity(intent);
                    }
                }

                public class SomeOtherActivity extends Activity {
                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                    }
                }
            """
            ).indented(),
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
            ).indented(),
        ).issues(UnsafeImplicitIntentDetector.ISSUE).isolated("src/test/pkg/TestActivity.java").run().expectClean()
    }

    fun testImplicitIntentMatchesNonExportedComponent_actionSetFromSetter_kotlin() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.content.Intent
                import android.app.Activity;

                class TestActivity : Activity {

                    override fun onCreate(savedInstanceState: Bundle) {
                        val intent = Intent()
                        intent.setAction("some.fake.action.LAUNCH")
                        startActivity(intent)
                    }
                }
            """
            ).indented(),
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
            ).indented(),
        ).run().expect(
            """
            src/test/pkg/TestActivity.kt:10: Error: This intent matches an internal non-exported component.
            If you are trying to invoke the component matching the action some.fake.action.LAUNCH,
            then you should use an explicit intent. [UnsafeImplicitIntentLaunch]
                    intent.setAction("some.fake.action.LAUNCH")
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        ).expectFixDiffs("""
            Fix for src/test/pkg/TestActivity.kt line 10: Set package name:
            @@ -10 +10
            -         intent.setAction("some.fake.action.LAUNCH")
            +         intent.setAction("some.fake.action.LAUNCH").setPackage("test.pkg")
            Fix for src/test/pkg/TestActivity.kt line 10: Set class name:
            @@ -10 +10
            -         intent.setAction("some.fake.action.LAUNCH")
            +         intent.setAction("some.fake.action.LAUNCH").setClassName("test.pkg", "test.pkg.TestActivity")
        """)
    }

    fun testImplicitIntentMatchesNonExportedComponent_actionSetFromSetter_kotlin_onTheFlyAnalysis() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.content.Intent
                import android.app.Activity;

                class TestActivity : Activity {

                    override fun onCreate(savedInstanceState: Bundle) {
                        val intent = Intent()
                        intent.setAction("some.fake.action.LAUNCH")
                        startActivity(intent)
                    }
                }
            """
            ).indented(),
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
            ).indented(),
        ).isolated("src/test/pkg/TestActivity.kt").run().expect(
            """
            src/test/pkg/TestActivity.kt:10: Error: This intent matches an internal non-exported component.
            If you are trying to invoke the component matching the action some.fake.action.LAUNCH,
            then you should use an explicit intent. [UnsafeImplicitIntentLaunch]
                    intent.setAction("some.fake.action.LAUNCH")
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        ).expectFixDiffs("""
            Fix for src/test/pkg/TestActivity.kt line 10: Set package name:
            @@ -10 +10
            -         intent.setAction("some.fake.action.LAUNCH")
            +         intent.setAction("some.fake.action.LAUNCH").setPackage("test.pkg")
            Fix for src/test/pkg/TestActivity.kt line 10: Set class name:
            @@ -10 +10
            -         intent.setAction("some.fake.action.LAUNCH")
            +         intent.setAction("some.fake.action.LAUNCH").setClassName("test.pkg", "test.pkg.TestActivity")
        """)
    }

    fun testImplicitIntentMatchesNonExportedComponent_actionSetFromConstructor_kotlin() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.content.Intent
                import android.app.Activity;

                class TestActivity : Activity {

                    override fun onCreate(savedInstanceState: Bundle) {
                        val intent = Intent("some.fake.action.LAUNCH")
                        startActivity(intent)
                    }
                }
            """
            ).indented(),
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
            ).indented(),
        ).run().expect(
            """
            src/test/pkg/TestActivity.kt:9: Error: This intent matches an internal non-exported component.
            If you are trying to invoke the component matching the action some.fake.action.LAUNCH,
            then you should use an explicit intent. [UnsafeImplicitIntentLaunch]
                    val intent = Intent("some.fake.action.LAUNCH")
                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        ).expectFixDiffs("""
            Fix for src/test/pkg/TestActivity.kt line 9: Set package name:
            @@ -9 +9
            -         val intent = Intent("some.fake.action.LAUNCH")
            +         val intent = Intent("some.fake.action.LAUNCH").setPackage("test.pkg")
            Fix for src/test/pkg/TestActivity.kt line 9: Set class name:
            @@ -9 +9
            -         val intent = Intent("some.fake.action.LAUNCH")
            +         val intent = Intent("some.fake.action.LAUNCH").setClassName("test.pkg", "test.pkg.TestActivity")
        """)
    }

    fun testImplicitIntentMatchesNonExportedComponent_actionSetFromConstructor_kotlin_onTheFlyAnalysis() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.content.Intent
                import android.app.Activity;

                class TestActivity : Activity {

                    override fun onCreate(savedInstanceState: Bundle) {
                        val intent = Intent("some.fake.action.LAUNCH")
                        startActivity(intent)
                    }
                }
            """
            ).indented(),
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
            ).indented(),
        ).isolated("src/test/pkg/TestActivity.kt").run().expect(
            """
            src/test/pkg/TestActivity.kt:9: Error: This intent matches an internal non-exported component.
            If you are trying to invoke the component matching the action some.fake.action.LAUNCH,
            then you should use an explicit intent. [UnsafeImplicitIntentLaunch]
                    val intent = Intent("some.fake.action.LAUNCH")
                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        ).expectFixDiffs("""
            Fix for src/test/pkg/TestActivity.kt line 9: Set package name:
            @@ -9 +9
            -         val intent = Intent("some.fake.action.LAUNCH")
            +         val intent = Intent("some.fake.action.LAUNCH").setPackage("test.pkg")
            Fix for src/test/pkg/TestActivity.kt line 9: Set class name:
            @@ -9 +9
            -         val intent = Intent("some.fake.action.LAUNCH")
            +         val intent = Intent("some.fake.action.LAUNCH").setClassName("test.pkg", "test.pkg.TestActivity")
        """)
    }
}
