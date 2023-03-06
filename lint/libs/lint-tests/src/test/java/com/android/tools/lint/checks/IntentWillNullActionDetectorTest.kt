/*
 * Copyright (C) 2023 The Android Open Source Project
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

class IntentWillNullActionDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector = IntentWillNullActionDetector()

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
                        Intent intent = new Intent();
                        startActivity(intent);
                    }
                }
            """
          )
          .indented(),
      )
      .run()
      .expect(
        """
            src/test/pkg/TestActivity.java:10: Warning: This intent has no action set and is not explicit by component.You should either make this intent explicit by component or set an action matching the targeted intent filter. [IntentWithNullActionLaunch]
                    Intent intent = new Intent();
                                    ~~~~~~~~~~~~
            0 errors, 1 warnings
            """
      )
      .expectFixDiffs(
        """
            Fix for src/test/pkg/TestActivity.java line 10: Set action...:
            @@ -10 +10
            -         Intent intent = new Intent();
            +         Intent intent = new Intent().setAction("[your.custom.action]|");
            Fix for src/test/pkg/TestActivity.java line 10: Set class...:
            @@ -10 +10
            -         Intent intent = new Intent();
            +         Intent intent = new Intent().setClassName("[app.package.name]|", "your.classname");
        """
      )
  }

  fun testIntentWithNullAction_actionSetByConstructor() {
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
                        Intent intent = new Intent("some.action");
                        startActivity(intent);
                    }
                }
            """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testIntentWithNullAction_actionSetWithSetAction() {
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
                        Intent intent = new Intent().setAction("some.action");
                        startActivity(intent);
                    }
                }
            """
          )
          .indented(),
      )
      .skipTestModes(TestMode.PARENTHESIZED)
      .run()
      .expectClean()
  }

  fun testIntentWithNullAction_actionSetToNullWithConstructorResetWithSetAction() {
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
                        Intent intent = new Intent(null);
                        intent.setAction("some.action");
                        startActivity(intent);
                    }
                }
            """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testIntentWithNullAction_setComponentExplicitWithInternalPackage() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.ComponentName;
                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        ComponentName componentName = new ComponentName("test.pkg", "some.fake.classname");
                        Intent intent = new Intent();
                        intent.setComponent(componentName);
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
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .skipTestModes(TestMode.PARENTHESIZED)
      .run()
      .expectClean()
  }

  fun testIntentWithNullAction_setComponentExplicitWithExternalPackage() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.ComponentName;
                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        ComponentName componentName = new ComponentName("test.external.pkg", "some.fake.classname");
                        Intent intent = new Intent();
                        intent.setComponent(componentName);
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
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .skipTestModes(TestMode.PARENTHESIZED)
      .run()
      .expectClean()
  }

  fun testIntentWithNullAction_setClassNameWithInternalPackage() {
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
                        intent.setClassName("test.pkg", "some.fake.classname");
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
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .skipTestModes(TestMode.PARENTHESIZED)
      .run()
      .expectClean()
  }

  fun testIntentWithNullAction_setClassNameWithExternalPackage() {
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
                        intent.setClassName("test.external.pkg", "some.fake.classname");
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
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .testModes(TestMode.PARTIAL)
      .run()
      .expectClean()
  }

  fun testIntentWithNullAction_explicitByPackage() {
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
                        intent.setPackage("test.pkg");
                        startActivity(intent);
                    }
                }
            """
          )
          .indented(),
      )
      .run()
      .expect(
        """
            src/test/pkg/TestActivity.java:10: Warning: This intent has no action set and is not explicit by component.You should either make this intent explicit by component or set an action matching the targeted intent filter. [IntentWithNullActionLaunch]
                    Intent intent = new Intent();
                                    ~~~~~~~~~~~~
            0 errors, 1 warnings
            """
      )
      .expectFixDiffs(
        """
            Fix for src/test/pkg/TestActivity.java line 10: Set action...:
            @@ -10 +10
            -         Intent intent = new Intent();
            +         Intent intent = new Intent().setAction("[your.custom.action]|");
            Fix for src/test/pkg/TestActivity.java line 10: Set class...:
            @@ -10 +10
            -         Intent intent = new Intent();
            +         Intent intent = new Intent().setClassName("[app.package.name]|", "your.classname");
        """
      )
  }

  fun testIntentWithNullAction_implicitIntentKotlin() {
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
                        startActivity(intent)
                    }
                }
            """
          )
          .indented(),
      )
      .isolated("src/test/pkg/TestActivity.kt")
      .run()
      .expect(
        """
            src/test/pkg/TestActivity.kt:9: Warning: This intent has no action set and is not explicit by component.You should either make this intent explicit by component or set an action matching the targeted intent filter. [IntentWithNullActionLaunch]
                    val intent = Intent()
                                 ~~~~~~~~
            0 errors, 1 warnings
            """
      )
      .expectFixDiffs(
        """
            Fix for src/test/pkg/TestActivity.kt line 9: Set action...:
            @@ -9 +9
            -         val intent = Intent()
            +         val intent = Intent().setAction("[your.custom.action]|")
            Fix for src/test/pkg/TestActivity.kt line 9: Set class...:
            @@ -9 +9
            -         val intent = Intent()
            +         val intent = Intent().setClassName("[app.package.name]|", "your.classname")
        """
      )
  }

  fun testIntentWithNullAction_implicitIntent_onTheFlyAnalysis() {
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
                        startActivity(intent);
                    }
                }
            """
          )
          .indented(),
      )
      .isolated("src/test/pkg/TestActivity.java")
      .run()
      .expect(
        """
            src/test/pkg/TestActivity.java:10: Warning: This intent has no action set and is not explicit by component.You should either make this intent explicit by component or set an action matching the targeted intent filter. [IntentWithNullActionLaunch]
                    Intent intent = new Intent();
                                    ~~~~~~~~~~~~
            0 errors, 1 warnings
            """
      )
      .expectFixDiffs(
        """
            Fix for src/test/pkg/TestActivity.java line 10: Set action...:
            @@ -10 +10
            -         Intent intent = new Intent();
            +         Intent intent = new Intent().setAction("[your.custom.action]|");
            Fix for src/test/pkg/TestActivity.java line 10: Set class...:
            @@ -10 +10
            -         Intent intent = new Intent();
            +         Intent intent = new Intent().setClassName("[app.package.name]|", "your.classname");
        """
      )
  }

  fun testIntentWithNullAction_actionSetByConstructor_onTheFlyAnalysis() {
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
                        Intent intent = new Intent("some.action");
                        startActivity(intent);
                    }
                }
            """
          )
          .indented(),
      )
      .isolated("src/test/pkg/TestActivity.java")
      .run()
      .expectClean()
  }

  fun testIntentWithNullAction_actionSetWithSetAction_onTheFlyAnalysis() {
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
                        Intent intent = new Intent().setAction("some.action");
                        startActivity(intent);
                    }
                }
            """
          )
          .indented(),
      )
      .isolated("src/test/pkg/TestActivity.java")
      .skipTestModes(TestMode.PARENTHESIZED)
      .run()
      .expectClean()
  }

  fun testIntentWithNullAction_actionSetToNullWithConstructorResetWithSetAction_onTheFlyAnalysis() {
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
                        Intent intent = new Intent(null);
                        intent.setAction("some.action");
                        startActivity(intent);
                    }
                }
            """
          )
          .indented(),
      )
      .isolated("src/test/pkg/TestActivity.java")
      .run()
      .expectClean()
  }

  fun testIntentWithNullAction_setComponentExplicitWithInternalPackage_onTheFlyAnalysis() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.ComponentName;
                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        ComponentName componentName = new ComponentName("test.pkg", "some.fake.classname");
                        Intent intent = new Intent();
                        intent.setComponent(componentName);
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
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .isolated("src/test/pkg/TestActivity.java")
      .skipTestModes(TestMode.PARENTHESIZED)
      .run()
      .expectClean()
  }

  fun testIntentWithNullAction_setComponentExplicitWithExternalPackage_onTheFlyAnalysis() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.ComponentName;
                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        ComponentName componentName = new ComponentName("test.external.pkg", "some.fake.classname");
                        Intent intent = new Intent();
                        intent.setComponent(componentName);
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
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .skipTestModes(TestMode.PARENTHESIZED)
      .isolated("src/test/pkg/TestActivity.java")
      .run()
      .expectClean()
  }

  fun testIntentWithNullAction_setClassNameWithInternalPackage_onTheFlyAnalysis() {
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
                        intent.setClassName("test.pkg", "some.fake.classname");
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
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .skipTestModes(TestMode.PARENTHESIZED)
      .isolated("src/test/pkg/TestActivity.java")
      .run()
      .expectClean()
  }

  fun testIntentWithNullAction_setClassNameWithExternalPackage_onTheFlyAnalysis() {
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
                        intent.setClassName("test.external.pkg", "some.fake.classname");
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
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .testModes(TestMode.PARTIAL)
      .run()
      .expectClean()
  }

  fun testIntentWithNullAction_explicitByPackage_onTheFlyAnalysis() {
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
                        intent.setPackage("test.pkg");
                        startActivity(intent);
                    }
                }
            """
          )
          .indented(),
      )
      .isolated("src/test/pkg/TestActivity.java")
      .run()
      .expect(
        """
            src/test/pkg/TestActivity.java:10: Warning: This intent has no action set and is not explicit by component.You should either make this intent explicit by component or set an action matching the targeted intent filter. [IntentWithNullActionLaunch]
                    Intent intent = new Intent();
                                    ~~~~~~~~~~~~
            0 errors, 1 warnings
            """
      )
      .expectFixDiffs(
        """
            Fix for src/test/pkg/TestActivity.java line 10: Set action...:
            @@ -10 +10
            -         Intent intent = new Intent();
            +         Intent intent = new Intent().setAction("[your.custom.action]|");
            Fix for src/test/pkg/TestActivity.java line 10: Set class...:
            @@ -10 +10
            -         Intent intent = new Intent();
            +         Intent intent = new Intent().setClassName("[app.package.name]|", "your.classname");
        """
      )
  }

  fun testIntentWithNullAction_useOfAHelperMethodWithIntentAsParameter() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.ComponentName;
                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        ComponentName componentName = new ComponentName("test.external.pkg", "some.fake.classname");
                        Intent intent = new Intent();
                        someCall(intent);
                        startActivity(intent);
                    }

                    private void someCall(Intent intent) {

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
                    </application>
                </manifest>
                """
          )
          .indented(),
      )
      .skipTestModes(TestMode.PARENTHESIZED)
      .run()
      .expectClean()
  }
}
