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

import com.android.tools.lint.detector.api.Detector

class PendingIntentMutableImplicitDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector = PendingIntentMutableImplicitDetector()

  fun testDocumentationExample() {
    lint()
      .projects(
        project(
          manifest().targetSdk(34),
          java(
              """
              package test.pkg;

              import android.app.PendingIntent;
              import android.content.Intent;
              import android.net.Uri;

              public class PendingIntentJavaTest {
                Uri mUri;
                protected void test() {
                  PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_MUTABLE);
                  Intent mIntent = new Intent("TEST", mUri);
                  PendingIntent.getService(null, 0, mIntent, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, { new Intent(), mIntent }, PendingIntent.FLAG_MUTABLE);
                }
              }
              """
            )
            .indented(),
          kotlin(
              """
              package test.pkg

              import android.app.PendingIntent
              import android.content.Intent
              import android.net.Uri

              class PendingIntentKotlinTest {
                val mUri: Uri
                fun test() {
                  PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_MUTABLE)
                  val mIntent = Intent("TEST", mUri)
                  PendingIntent.getService(null, 0, mIntent, PendingIntent.FLAG_MUTABLE)
                }
              }
              """
            )
            .indented()
        )
      )
      .run()
      .expect(
        """
        src/test/pkg/PendingIntentJavaTest.java:10: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:11: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:13: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, mIntent, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:14: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivities(null, 0, { new Intent(), mIntent }, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:10: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:11: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:13: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, mIntent, PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        7 errors, 0 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for src/test/pkg/PendingIntentJavaTest.java line 10: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -10 +10
        -     PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 10: Add FLAG_NO_CREATE:
        @@ -10 +10
        -     PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 11: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -11 +11
        -     PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 11: Add FLAG_NO_CREATE:
        @@ -11 +11
        -     PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 13: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -13 +13
        -     PendingIntent.getService(null, 0, mIntent, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getService(null, 0, mIntent, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 13: Add FLAG_NO_CREATE:
        @@ -13 +13
        -     PendingIntent.getService(null, 0, mIntent, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getService(null, 0, mIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 14: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -14 +14
        -     PendingIntent.getActivities(null, 0, { new Intent(), mIntent }, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, { new Intent(), mIntent }, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 14: Add FLAG_NO_CREATE:
        @@ -14 +14
        -     PendingIntent.getActivities(null, 0, { new Intent(), mIntent }, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, { new Intent(), mIntent }, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 10: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -10 +10
        -     PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 10: Add FLAG_NO_CREATE:
        @@ -10 +10
        -     PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 11: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -11 +11
        -     PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 11: Add FLAG_NO_CREATE:
        @@ -11 +11
        -     PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 13: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -13 +13
        -     PendingIntent.getService(null, 0, mIntent, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getService(null, 0, mIntent, PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 13: Add FLAG_NO_CREATE:
        @@ -13 +13
        -     PendingIntent.getService(null, 0, mIntent, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getService(null, 0, mIntent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        """
      )
  }

  fun testMutableAndImplicit_inline_complains() {
    lint()
      .projects(
        project(
          manifest().targetSdk(34),
          java(
              """
              package test.pkg;

              import android.app.PendingIntent;
              import android.content.Intent;
              import android.net.Uri;

              public class PendingIntentJavaTest {
                Uri mUri;
                protected void test() {
                  PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getService(null, 0, new Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, { new Intent(), new Intent("TEST") }, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, new Intent[] { new Intent("TEST"), new Intent("TEST", mUri) }, PendingIntent.FLAG_MUTABLE);
                }
              }
              """
            )
            .indented(),
          kotlin(
              """
              package test.pkg

              import android.app.PendingIntent
              import android.content.Intent
              import android.net.Uri

              class PendingIntentKotlinTest {
                val mUri: Uri
                fun test() {
                  PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE)
                }
              }
              """
            )
            .indented()
        )
      )
      .run()
      .expect(
        """
        src/test/pkg/PendingIntentJavaTest.java:10: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:11: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:12: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, new Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:13: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivities(null, 0, { new Intent(), new Intent("TEST") }, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:14: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivities(null, 0, new Intent[] { new Intent("TEST"), new Intent("TEST", mUri) }, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:10: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:11: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:12: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        8 errors, 0 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for src/test/pkg/PendingIntentJavaTest.java line 10: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -10 +10
        -     PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 10: Add FLAG_NO_CREATE:
        @@ -10 +10
        -     PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 11: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -11 +11
        -     PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 11: Add FLAG_NO_CREATE:
        @@ -11 +11
        -     PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 12: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -12 +12
        -     PendingIntent.getService(null, 0, new Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getService(null, 0, new Intent("TEST", mUri), PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 12: Add FLAG_NO_CREATE:
        @@ -12 +12
        -     PendingIntent.getService(null, 0, new Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getService(null, 0, new Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 13: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -13 +13
        -     PendingIntent.getActivities(null, 0, { new Intent(), new Intent("TEST") }, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, { new Intent(), new Intent("TEST") }, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 13: Add FLAG_NO_CREATE:
        @@ -13 +13
        -     PendingIntent.getActivities(null, 0, { new Intent(), new Intent("TEST") }, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, { new Intent(), new Intent("TEST") }, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 14: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -14 +14
        -     PendingIntent.getActivities(null, 0, new Intent[] { new Intent("TEST"), new Intent("TEST", mUri) }, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, new Intent[] { new Intent("TEST"), new Intent("TEST", mUri) }, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 14: Add FLAG_NO_CREATE:
        @@ -14 +14
        -     PendingIntent.getActivities(null, 0, new Intent[] { new Intent("TEST"), new Intent("TEST", mUri) }, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, new Intent[] { new Intent("TEST"), new Intent("TEST", mUri) }, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 10: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -10 +10
        -     PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 10: Add FLAG_NO_CREATE:
        @@ -10 +10
        -     PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 11: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -11 +11
        -     PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 11: Add FLAG_NO_CREATE:
        @@ -11 +11
        -     PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 12: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -12 +12
        -     PendingIntent.getService(null, 0, Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getService(null, 0, Intent("TEST", mUri), PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 12: Add FLAG_NO_CREATE:
        @@ -12 +12
        -     PendingIntent.getService(null, 0, Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getService(null, 0, Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        """
      )
  }

  fun testMutableAndImplicit_variable_complains() {
    lint()
      .projects(
        project(
          manifest().targetSdk(34),
          java(
              """
              package test.pkg;

              import android.app.PendingIntent;
              import android.content.Intent;
              import android.net.Uri;

              public class PendingIntentJavaTest {
                Uri mUri;
                protected void test() {
                  Intent intentOne = new Intent();
                  Intent intentTwo = new Intent("TEST");
                  Intent intentThree = new Intent("TEST", mUri);
                  PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getService(null, 0, intentThree, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, { intentOne, intentTwo }, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, new Intent[] {  intentOne, intentThree }, PendingIntent.FLAG_MUTABLE);
                  Intent[] intentsWithInitializer = new Intent[] {intentOne, intentThree};
                  PendingIntent.getActivities(null, 0, intentsWithInitializer, PendingIntent.FLAG_MUTABLE);
                  Intent[] intentsWithDimensions = new Intent[2];
                  intentsWithDimensions[0] = new Intent();
                  intentsWithDimensions[1] = intentOne;
                  PendingIntent.getActivities(null, 0, intentsWithDimensions, PendingIntent.FLAG_MUTABLE);
                }
              }
              """
            )
            .indented(),
          kotlin(
              """
              package test.pkg

              import android.app.PendingIntent
              import android.content.Intent
              import android.net.Uri

              class PendingIntentKotlinTest {
                val mUri: Uri
                fun test() {
                  val intentOne = Intent()
                  val intentTwo = Intent("TEST")
                  val intentThree = Intent("TEST", mUri)
                  PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, intentThree, PendingIntent.FLAG_MUTABLE)
                }
              }
              """
            )
            .indented()
        )
      )
      .run()
      .expect(
        """
        src/test/pkg/PendingIntentJavaTest.java:13: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:14: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:15: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, intentThree, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:16: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivities(null, 0, { intentOne, intentTwo }, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:17: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivities(null, 0, new Intent[] {  intentOne, intentThree }, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:19: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivities(null, 0, intentsWithInitializer, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:23: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivities(null, 0, intentsWithDimensions, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:13: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:14: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:15: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, intentThree, PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        10 errors, 0 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for src/test/pkg/PendingIntentJavaTest.java line 13: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -13 +13
        -     PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 13: Add FLAG_NO_CREATE:
        @@ -13 +13
        -     PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 14: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -14 +14
        -     PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 14: Add FLAG_NO_CREATE:
        @@ -14 +14
        -     PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 15: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -15 +15
        -     PendingIntent.getService(null, 0, intentThree, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getService(null, 0, intentThree, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 15: Add FLAG_NO_CREATE:
        @@ -15 +15
        -     PendingIntent.getService(null, 0, intentThree, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getService(null, 0, intentThree, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 16: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -16 +16
        -     PendingIntent.getActivities(null, 0, { intentOne, intentTwo }, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, { intentOne, intentTwo }, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 16: Add FLAG_NO_CREATE:
        @@ -16 +16
        -     PendingIntent.getActivities(null, 0, { intentOne, intentTwo }, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, { intentOne, intentTwo }, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 17: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -17 +17
        -     PendingIntent.getActivities(null, 0, new Intent[] {  intentOne, intentThree }, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, new Intent[] {  intentOne, intentThree }, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 17: Add FLAG_NO_CREATE:
        @@ -17 +17
        -     PendingIntent.getActivities(null, 0, new Intent[] {  intentOne, intentThree }, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, new Intent[] {  intentOne, intentThree }, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 19: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -19 +19
        -     PendingIntent.getActivities(null, 0, intentsWithInitializer, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, intentsWithInitializer, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 19: Add FLAG_NO_CREATE:
        @@ -19 +19
        -     PendingIntent.getActivities(null, 0, intentsWithInitializer, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, intentsWithInitializer, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 23: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -23 +23
        -     PendingIntent.getActivities(null, 0, intentsWithDimensions, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, intentsWithDimensions, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 23: Add FLAG_NO_CREATE:
        @@ -23 +23
        -     PendingIntent.getActivities(null, 0, intentsWithDimensions, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, intentsWithDimensions, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 13: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -13 +13
        -     PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 13: Add FLAG_NO_CREATE:
        @@ -13 +13
        -     PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 14: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -14 +14
        -     PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 14: Add FLAG_NO_CREATE:
        @@ -14 +14
        -     PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 15: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -15 +15
        -     PendingIntent.getService(null, 0, intentThree, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getService(null, 0, intentThree, PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 15: Add FLAG_NO_CREATE:
        @@ -15 +15
        -     PendingIntent.getService(null, 0, intentThree, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getService(null, 0, intentThree, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        """
      )
  }

  fun testMutableAndExplicitConstructors_inline_isClean() {
    lint()
      .projects(
        project(
          manifest().targetSdk(34),
          java(
              """
              package test.pkg;

              import android.app.PendingIntent;
              import android.content.Context;
              import android.content.Intent;
              import android.net.Uri;

              public class PendingIntentJavaTest {
                Context mContext;
                Uri mUri;
                protected void test() {
                  // Intent(Context, Class)
                  PendingIntent.getActivity(null, 0, new Intent(mContext, PendingIntentJavaTest.class), PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getBroadcast(null, 0, new Intent(mContext, PendingIntentJavaTest.class), PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getService(null, 0, new Intent(mContext, PendingIntentJavaTest.class), PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, { new Intent(mContext, PendingIntentJavaTest.class), new Intent(mContext, PendingIntentJavaTest.class) }, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, new Intent[] { new Intent(mContext, PendingIntentJavaTest.class), new Intent(mContext, PendingIntentJavaTest.class) }, PendingIntent.FLAG_MUTABLE);

                  // Intent(String, Uri, Context, Class)
                  PendingIntent.getActivity(null, 0, new Intent("TEST", mUri, mContext, PendingIntentJavaTest.class), PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getBroadcast(null, 0, new Intent("TEST", mUri, mContext, PendingIntentJavaTest.class), PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getService(null, 0, new Intent("TEST", mUri, mContext, PendingIntentJavaTest.class), PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, { new Intent("TEST", mUri, mContext, PendingIntentJavaTest.class), new Intent("TEST", mUri, mContext, PendingIntentJavaTest.class) }, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, new Intent[] { new Intent("TEST", mUri, mContext, PendingIntentJavaTest.class), new Intent("TEST", mUri, mContext, PendingIntentJavaTest.class) }, PendingIntent.FLAG_MUTABLE);
                }
              }
              """
            )
            .indented(),
          kotlin(
              """
              package test.pkg

              import android.app.PendingIntent
              import android.content.Context
              import android.content.Intent
              import android.net.Uri

              class PendingIntentKotlinTest {
                val mUri: Uri
                val mContext: Context
                fun test() {
                  // Intent(Context, Class)
                  PendingIntent.getActivity(null, 0, Intent(mContext, PendingIntentKotlinTest::class.java), PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getBroadcast(null, 0, Intent(mContext, PendingIntentKotlinTest::class.java), PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, Intent(mContext, PendingIntentKotlinTest::class.java), PendingIntent.FLAG_MUTABLE)

                  // Intent(String, Uri, Context, Class)
                  PendingIntent.getActivity(null, 0, Intent("TEST", mUri, mContext, PendingIntentKotlinTest::class.java), PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getBroadcast(null, 0, Intent("TEST", mUri, mContext, PendingIntentKotlinTest::class.java), PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, Intent("TEST", mUri, mContext, PendingIntentKotlinTest::class.java), PendingIntent.FLAG_MUTABLE)
                }
              }
              """
            )
            .indented()
        )
      )
      .run()
      .expectClean()
  }

  fun testMutableAndExplicitConstructors_variable_isClean() {
    lint()
      .projects(
        project(
          manifest().targetSdk(34),
          java(
              """
              package test.pkg;

              import android.app.PendingIntent;
              import android.content.Context;
              import android.content.Intent;
              import android.net.Uri;

              public class PendingIntentJavaTest {
                Context mContext;
                Uri mUri;
                protected void test() {
                  // Intent(Context, Class)
                  Intent intentOne = new Intent(mContext, PendingIntentJavaTest.class);
                  PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getBroadcast(null, 0, intentOne, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getService(null, 0, intentOne, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, { intentOne, intentOne }, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, new Intent[] {  intentOne, intentOne }, PendingIntent.FLAG_MUTABLE);
                  Intent[] intentsOneWithInitializer = new Intent[] {intentOne, intentOne};
                  PendingIntent.getActivities(null, 0, intentsOneWithInitializer, PendingIntent.FLAG_MUTABLE);
                  Intent[] intentsOneWithDimensions = new Intent[2];
                  intentsOneWithDimensions[0] = new Intent(mContext, PendingIntentJavaTest.class);
                  intentsOneWithDimensions[1] = intentOne;
                  PendingIntent.getActivities(null, 0, intentsOneWithDimensions, PendingIntent.FLAG_MUTABLE);

                  // Intent(String, Uri, Context, Class)
                  Intent intentTwo =  new Intent("TEST", mUri, mContext, PendingIntentJavaTest.class);
                  PendingIntent.getActivity(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getService(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, { intentTwo, intentTwo }, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, new Intent[] { intentTwo, intentTwo }, PendingIntent.FLAG_MUTABLE);
                  Intent[] intentsTwoWithInitializer = new Intent[] { intentTwo, intentTwo };
                  PendingIntent.getActivities(null, 0, intentsTwoWithInitializer, PendingIntent.FLAG_MUTABLE);
                  Intent[] intentsTwoWithDimensions = new Intent[2];
                  intentsTwoWithDimensions[0] = new Intent("TEST", mUri, mContext, PendingIntentJavaTest.class);
                  intentsTwoWithDimensions[1] = intentTwo;
                  PendingIntent.getActivities(null, 0, intentsTwoWithDimensions, PendingIntent.FLAG_MUTABLE);
                }
              }
              """
            )
            .indented(),
          kotlin(
              """
              package test.pkg

              import android.app.PendingIntent
              import android.content.Context
              import android.content.Intent
              import android.net.Uri

              class PendingIntentKotlinTest {
                val mUri: Uri
                val mContext: Context
                fun test() {
                  // Intent(Context, Class)
                  val intentOne = Intent(mContext, PendingIntentKotlinTest::class.java)
                  PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getBroadcast(null, 0, intentOne, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, intentOne, PendingIntent.FLAG_MUTABLE)

                  // Intent(String, Uri, Context, Class)
                  val intentTwo = Intent("TEST", mUri, mContext, PendingIntentKotlinTest::class.java)
                  PendingIntent.getActivity(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE)
                }
              }
            """
            )
            .indented()
        )
      )
      .run()
      .expectClean()
  }

  fun testMutableAndExplicitChangedToImplicit_complains() {
    lint()
      .projects(
        project(
          manifest().targetSdk(34),
          java(
              """
              package test.pkg;

              import android.app.PendingIntent;
              import android.content.Context;
              import android.content.Intent;
              import android.net.Uri;

              public class PendingIntentJavaTest {
                Context mContext;
                Uri mUri;
                protected void test() {
                  Intent intentOne = new Intent(mContext, PendingIntentJavaTest.class);
                  intentOne.setPackage(null);
                  PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE); // does not complain
                  intentOne.setComponent(null);
                  PendingIntent.getBroadcast(null, 0, intentOne, PendingIntent.FLAG_MUTABLE); // complains
                  intentOne.setPackage("package");
                  PendingIntent.getService(null, 0, intentOne, PendingIntent.FLAG_MUTABLE); // does not complain
                  PendingIntent.getActivities(null, 0, { intentOne.setPackage(null), intentOne }, PendingIntent.FLAG_MUTABLE); // complains
                  PendingIntent.getActivities(null, 0, new Intent[] {  intentOne, intentOne }, PendingIntent.FLAG_MUTABLE); // complains
                  intentOne.setClassName(mContext, "class");
                  Intent[] intentsOneWithInitializer = new Intent[] {intentOne, intentOne};
                  PendingIntent.getActivities(null, 0, intentsOneWithInitializer, PendingIntent.FLAG_MUTABLE); // does not complain
                  intentOne.setComponent(null);
                  Intent[] intentsOneWithDimensions = new Intent[2];
                  intentsOneWithDimensions[0] = new Intent(mContext, PendingIntentJavaTest.class);
                  intentsOneWithDimensions[1] = intentOne;
                  PendingIntent.getActivities(null, 0, intentsOneWithDimensions, PendingIntent.FLAG_MUTABLE); // complains
                  Intent intentTwo = new Intent().setPackage("package");
                  PendingIntent.getActivity(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE); // does not complain
                  intentTwo.setPackage(null);
                  PendingIntent.getActivity(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE); // complains
                }
              }
              """
            )
            .indented(),
          kotlin(
              """
              package test.pkg

              import android.app.PendingIntent
              import android.content.Context
              import android.content.Intent
              import android.net.Uri

              class PendingIntentKotlinTest {
                val mUri: Uri
                val mContext: Context
                fun test() {
                  val intentOne = Intent(mContext, PendingIntentKotlinTest::class.java)
                  intentOne.setPackage(null)
                  PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE) // does not complain
                  intentOne.setComponent(null)
                  PendingIntent.getBroadcast(null, 0, intentOne, PendingIntent.FLAG_MUTABLE) // complains
                  intentOne.setPackage("package")
                  PendingIntent.getService(null, 0, intentOne, PendingIntent.FLAG_MUTABLE) // does not complain
                  PendingIntent.getService(null, 0, intentOne.setPackage(null), PendingIntent.FLAG_MUTABLE) // complains
                }
              }
            """
            )
            .indented()
        )
      )
      .run()
      .expect(
        """
        src/test/pkg/PendingIntentJavaTest.java:16: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getBroadcast(null, 0, intentOne, PendingIntent.FLAG_MUTABLE); // complains
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:19: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivities(null, 0, { intentOne.setPackage(null), intentOne }, PendingIntent.FLAG_MUTABLE); // complains
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:20: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivities(null, 0, new Intent[] {  intentOne, intentOne }, PendingIntent.FLAG_MUTABLE); // complains
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:28: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivities(null, 0, intentsOneWithDimensions, PendingIntent.FLAG_MUTABLE); // complains
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:32: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivity(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE); // complains
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:16: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getBroadcast(null, 0, intentOne, PendingIntent.FLAG_MUTABLE) // complains
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:19: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, intentOne.setPackage(null), PendingIntent.FLAG_MUTABLE) // complains
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        7 errors, 0 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for src/test/pkg/PendingIntentJavaTest.java line 16: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -16 +16
        -     PendingIntent.getBroadcast(null, 0, intentOne, PendingIntent.FLAG_MUTABLE); // complains
        +     PendingIntent.getBroadcast(null, 0, intentOne, PendingIntent.FLAG_IMMUTABLE); // complains
        Fix for src/test/pkg/PendingIntentJavaTest.java line 16: Add FLAG_NO_CREATE:
        @@ -16 +16
        -     PendingIntent.getBroadcast(null, 0, intentOne, PendingIntent.FLAG_MUTABLE); // complains
        +     PendingIntent.getBroadcast(null, 0, intentOne, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE); // complains
        Fix for src/test/pkg/PendingIntentJavaTest.java line 19: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -19 +19
        -     PendingIntent.getActivities(null, 0, { intentOne.setPackage(null), intentOne }, PendingIntent.FLAG_MUTABLE); // complains
        +     PendingIntent.getActivities(null, 0, { intentOne.setPackage(null), intentOne }, PendingIntent.FLAG_IMMUTABLE); // complains
        Fix for src/test/pkg/PendingIntentJavaTest.java line 19: Add FLAG_NO_CREATE:
        @@ -19 +19
        -     PendingIntent.getActivities(null, 0, { intentOne.setPackage(null), intentOne }, PendingIntent.FLAG_MUTABLE); // complains
        +     PendingIntent.getActivities(null, 0, { intentOne.setPackage(null), intentOne }, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE); // complains
        Fix for src/test/pkg/PendingIntentJavaTest.java line 20: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -20 +20
        -     PendingIntent.getActivities(null, 0, new Intent[] {  intentOne, intentOne }, PendingIntent.FLAG_MUTABLE); // complains
        +     PendingIntent.getActivities(null, 0, new Intent[] {  intentOne, intentOne }, PendingIntent.FLAG_IMMUTABLE); // complains
        Fix for src/test/pkg/PendingIntentJavaTest.java line 20: Add FLAG_NO_CREATE:
        @@ -20 +20
        -     PendingIntent.getActivities(null, 0, new Intent[] {  intentOne, intentOne }, PendingIntent.FLAG_MUTABLE); // complains
        +     PendingIntent.getActivities(null, 0, new Intent[] {  intentOne, intentOne }, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE); // complains
        Fix for src/test/pkg/PendingIntentJavaTest.java line 28: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -28 +28
        -     PendingIntent.getActivities(null, 0, intentsOneWithDimensions, PendingIntent.FLAG_MUTABLE); // complains
        +     PendingIntent.getActivities(null, 0, intentsOneWithDimensions, PendingIntent.FLAG_IMMUTABLE); // complains
        Fix for src/test/pkg/PendingIntentJavaTest.java line 28: Add FLAG_NO_CREATE:
        @@ -28 +28
        -     PendingIntent.getActivities(null, 0, intentsOneWithDimensions, PendingIntent.FLAG_MUTABLE); // complains
        +     PendingIntent.getActivities(null, 0, intentsOneWithDimensions, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE); // complains
        Fix for src/test/pkg/PendingIntentJavaTest.java line 32: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -32 +32
        -     PendingIntent.getActivity(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE); // complains
        +     PendingIntent.getActivity(null, 0, intentTwo, PendingIntent.FLAG_IMMUTABLE); // complains
        Fix for src/test/pkg/PendingIntentJavaTest.java line 32: Add FLAG_NO_CREATE:
        @@ -32 +32
        -     PendingIntent.getActivity(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE); // complains
        +     PendingIntent.getActivity(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE); // complains
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 16: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -16 +16
        -     PendingIntent.getBroadcast(null, 0, intentOne, PendingIntent.FLAG_MUTABLE) // complains
        +     PendingIntent.getBroadcast(null, 0, intentOne, PendingIntent.FLAG_IMMUTABLE) // complains
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 16: Add FLAG_NO_CREATE:
        @@ -16 +16
        -     PendingIntent.getBroadcast(null, 0, intentOne, PendingIntent.FLAG_MUTABLE) // complains
        +     PendingIntent.getBroadcast(null, 0, intentOne, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE) // complains
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 19: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -19 +19
        -     PendingIntent.getService(null, 0, intentOne.setPackage(null), PendingIntent.FLAG_MUTABLE) // complains
        +     PendingIntent.getService(null, 0, intentOne.setPackage(null), PendingIntent.FLAG_IMMUTABLE) // complains
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 19: Add FLAG_NO_CREATE:
        @@ -19 +19
        -     PendingIntent.getService(null, 0, intentOne.setPackage(null), PendingIntent.FLAG_MUTABLE) // complains
        +     PendingIntent.getService(null, 0, intentOne.setPackage(null), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE) // complains
        """
      )
  }

  fun testImmutableAndImplicit_isClean() {
    lint()
      .projects(
        project(
          manifest().targetSdk(34),
          java(
              """
              package test.pkg;

              import android.app.PendingIntent;
              import android.content.Intent;
              import android.net.Uri;

              public class PendingIntentJavaTest {
                Uri mUri;
                protected void test() {
                  Intent intentOne = new Intent();
                  Intent intentTwo = new Intent("TEST");
                  Intent intentThree = new Intent("TEST", mUri);
                  PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_IMMUTABLE);
                  PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_IMMUTABLE);
                  PendingIntent.getService(null, 0, intentThree, PendingIntent.FLAG_IMMUTABLE);
                  PendingIntent.getActivities(null, 0, { intentOne, intentTwo }, PendingIntent.FLAG_IMMUTABLE);
                  PendingIntent.getActivities(null, 0, new Intent[] {  intentOne, intentThree }, PendingIntent.FLAG_IMMUTABLE);
                  Intent[] intentsWithInitializer = new Intent[] {intentOne, intentThree};
                  PendingIntent.getActivities(null, 0, intentsWithInitializer, PendingIntent.FLAG_IMMUTABLE);
                  Intent[] intentsWithDimensions = new Intent[2];
                  intentsWithDimensions[0] = new Intent();
                  intentsWithDimensions[1] = intentOne;
                  PendingIntent.getActivities(null, 0, intentsWithDimensions, PendingIntent.FLAG_IMMUTABLE);
                }
              }
              """
            )
            .indented(),
          kotlin(
              """
              package test.pkg

              import android.app.PendingIntent
              import android.content.Intent
              import android.net.Uri

              class PendingIntentKotlinTest {
                val mUri: Uri
                fun test() {
                  val intentOne = Intent()
                  val intentTwo = Intent("TEST")
                  val intentThree = Intent("TEST", mUri)
                  PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_IMMUTABLE)
                  PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_IMMUTABLE)
                  PendingIntent.getService(null, 0, intentThree, PendingIntent.FLAG_IMMUTABLE)
                }
              }
            """
            )
            .indented()
        )
      )
      .run()
      .expectClean()
  }

  fun testMutableNoCreateAndImplicit_isClean() {
    lint()
      .projects(
        project(
          manifest().targetSdk(34),
          java(
              """
              package test.pkg;

              import android.app.PendingIntent;
              import android.content.Intent;
              import android.net.Uri;

              public class PendingIntentJavaTest {
                Uri mUri;
                protected void test() {
                  Intent intentOne = new Intent();
                  Intent intentTwo = new Intent("TEST");
                  Intent intentThree = new Intent("TEST", mUri);
                  PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
                  PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
                  PendingIntent.getService(null, 0, intentThree, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
                  PendingIntent.getActivities(null, 0, { intentOne, intentTwo }, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
                  PendingIntent.getActivities(null, 0, new Intent[] {  intentOne, intentThree }, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
                  Intent[] intentsWithInitializer = new Intent[] {intentOne, intentThree};
                  PendingIntent.getActivities(null, 0, intentsWithInitializer, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
                  Intent[] intentsWithDimensions = new Intent[2];
                  intentsWithDimensions[0] = new Intent();
                  intentsWithDimensions[1] = intentOne;
                  PendingIntent.getActivities(null, 0, intentsWithDimensions, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
                }
              }
              """
            )
            .indented(),
          kotlin(
              """
              package test.pkg

              import android.app.PendingIntent
              import android.content.Intent
              import android.net.Uri

              class PendingIntentKotlinTest {
                val mUri: Uri
                fun test() {
                  val intentOne = Intent()
                  val intentTwo = Intent("TEST")
                  val intentThree = Intent("TEST", mUri)
                  PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
                  PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
                  PendingIntent.getService(null, 0, intentThree, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
                }
              }
            """
            )
            .indented()
        )
      )
      .run()
      .expectClean()
  }

  fun testMutableAllowUnsafeImplicitAndImplicit_isClean() {
    lint()
      .projects(
        project(
          manifest().targetSdk(34),
          java(
              """
              package test.pkg;

              import android.app.PendingIntent;
              import android.content.Intent;
              import android.net.Uri;

              public class PendingIntentJavaTest {
                Uri mUri;
                protected void test() {
                  Intent intentOne = new Intent();
                  Intent intentTwo = new Intent("TEST");
                  Intent intentThree = new Intent("TEST", mUri);
                  PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);
                  PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);
                  PendingIntent.getService(null, 0, intentThree, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);
                  PendingIntent.getActivities(null, 0, { intentOne, intentTwo }, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);
                  PendingIntent.getActivities(null, 0, new Intent[] {  intentOne, intentThree }, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);
                  Intent[] intentsWithInitializer = new Intent[] {intentOne, intentThree};
                  PendingIntent.getActivities(null, 0, intentsWithInitializer, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);
                  Intent[] intentsWithDimensions = new Intent[2];
                  intentsWithDimensions[0] = new Intent();
                  intentsWithDimensions[1] = intentOne;
                  PendingIntent.getActivities(null, 0, intentsWithDimensions, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);
                }
              }
              """
            )
            .indented(),
          kotlin(
              """
              package test.pkg

              import android.app.PendingIntent
              import android.content.Intent
              import android.net.Uri

              class PendingIntentKotlinTest {
                val mUri: Uri
                fun test() {
                  val intentOne = Intent()
                  val intentTwo = Intent("TEST")
                  val intentThree = Intent("TEST", mUri)
                  PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT)
                  PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT)
                  PendingIntent.getService(null, 0, intentThree, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT)
                }
              }
            """
            )
            .indented()
        )
      )
      .run()
      .expectClean()
  }

  fun testMutableAndExplicitSet_isClean() {
    lint()
      .projects(
        project(
          manifest().targetSdk(34),
          java(
              """
              package test.pkg;

              import android.app.PendingIntent;
              import android.content.ComponentName;
              import android.content.Context;
              import android.content.Intent;
              import android.net.Uri;

              public class PendingIntentJavaTest {
                ComponentName mComponent;
                Context mContext;
                Uri mUri;
                protected void test() {
                  Intent intentOne = new Intent().setPackage("test.pkg");
                  Intent intentTwo = new Intent("TEST");
                  intentTwo.setComponent(mComponent);
                  Intent intentThree = new Intent("TEST", mUri);
                  PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getService(null, 0, intentThree.setClassName(mContext, "TEST"), PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, { intentOne, intentTwo }, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, new Intent[] {  intentOne, intentThree }, PendingIntent.FLAG_MUTABLE);
                  Intent[] intentsWithInitializer = new Intent[] {intentOne, intentThree};
                  PendingIntent.getActivities(null, 0, intentsWithInitializer, PendingIntent.FLAG_MUTABLE);
                  Intent[] intentsWithDimensions = new Intent[2];
                  intentsWithDimensions[0] = new Intent().setClass(mContext, PendingIntentJavaTest.class);
                  intentsWithDimensions[1] = intentOne;
                  PendingIntent.getActivities(null, 0, intentsWithDimensions, PendingIntent.FLAG_MUTABLE);
                }
              }
              """
            )
            .indented(),
          kotlin(
              """
              package test.pkg

              import android.app.PendingIntent
              import android.content.ComponentName
              import android.content.Context
              import android.content.Intent
              import android.net.Uri

              class PendingIntentKotlinTest {
                val mUri: Uri
                val mComponent: ComponentName
                val mContext: Context
                fun test() {
                  val intentOne = Intent().setPackage("test.pkg")
                  val intentTwo = Intent("TEST")
                  intentTwo.setComponent(mComponent)
                  val intentThree = Intent("TEST", mUri)
                  PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, intentThree.setClassName(mContext, "TEST"), PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, Intent().setClassName(mContext, "TEST"), PendingIntent.FLAG_MUTABLE)
                  val intentFour = Intent().let { it.setPackage("test.pkg") }
                  val intentFive = Intent("TEST").apply {
                    setComponent(mComponent)
                    setPackage("test.pkg")
                  }.setData(mUri)
                  val intentSix = Intent("TEST", mUri)
                  PendingIntent.getActivity(null, 0, intentFour, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getBroadcast(null, 0, intentFive.run { setData(mUri) }, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, with(intentSix) { setClassName(mContext, "TEST") }, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, with(Intent()) { setClassName(mContext, "TEST") }, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, Intent().also { it.setClassName(mContext, "TEST") }, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, with(Intent()) { intentSix }, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, Intent().run { intentSix }, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, Intent().let { intentSix }, PendingIntent.FLAG_MUTABLE)
                  // TODO: b/311405051 uncomment when DFA is improved
                  // PendingIntent.getService(null, 0, with(block =  { setClassName(mContext, "TEST") }, receiver = Intent()), PendingIntent.FLAG_MUTABLE)
                  // val intentSeven = Intent()
                  // with(intentSeven) { setClassName(mContext, "TEST") }
                  // PendingIntent.getActivity(null, 0, intentSeven, PendingIntent.FLAG_MUTABLE)
                }
              }
              """
            )
            .indented()
        )
      )
      .run()
      .expectClean()
  }

  fun testMutableAndNonExplicitSet_complains() {
    lint()
      .projects(
        project(
          manifest().targetSdk(34),
          java(
              """
              package test.pkg;

              import android.app.PendingIntent;
              import android.content.ComponentName;
              import android.content.Context;
              import android.content.Intent;
              import android.net.Uri;

              public class PendingIntentJavaTest {
                ComponentName mComponent;
                Context mContext;
                Uri mUri;
                protected void test() {
                  Intent intentOne = new Intent().setAction("TEST");
                  Intent intentTwo = new Intent("TEST");
                  intentTwo.setData(mUri);
                  Intent intentThree = new Intent("TEST", mUri);
                  PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getService(null, 0, intentThree.setIdentifier("TEST"), PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, { intentOne, intentTwo }, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, new Intent[] { intentOne, intentThree }, PendingIntent.FLAG_MUTABLE);
                  Intent intentFour = new Intent().setAction("TEST");
                  Intent intentFive = new Intent("TEST");
                  intentFive.setData(mUri);
                  Intent[] intentsWithInitializer = new Intent[] { intentFour, intentFive };
                  PendingIntent.getActivities(null, 0, intentsWithInitializer, PendingIntent.FLAG_MUTABLE);
                  Intent[] intentsWithDimensions = new Intent[2];
                  intentsWithDimensions[0] = new Intent().setAction("TEST");
                  intentsWithDimensions[1] = intentOne;
                  PendingIntent.getActivities(null, 0, intentsWithDimensions, PendingIntent.FLAG_MUTABLE);
                }
              }
              """
            )
            .indented(),
          kotlin(
              """
              package test.pkg

              import android.app.PendingIntent
              import android.content.ComponentName
              import android.content.Context
              import android.content.Intent
              import android.net.Uri

              class PendingIntentKotlinTest {
                val mUri: Uri
                val mComponent: ComponentName
                val mContext: Context
                fun test() {
                  val intentOne = Intent().setAction("TEST")
                  val intentTwo = Intent("TEST")
                  intentTwo.setData(mUri)
                  val intentThree = Intent("TEST", mUri)
                  PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, intentThree.setIdentifier("TEST"), PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, Intent().setAction("TEST"), PendingIntent.FLAG_MUTABLE)
                  val intentFour = Intent().let { it.setAction("TEST") }
                  val intentFive = Intent("1").apply { setData(mUri) }.setIdentifier("TEST")
                  val intentSix = Intent("TEST", mUri)
                  PendingIntent.getActivity(null, 0, intentFour, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getBroadcast(null, 0, intentFive.run { setAction("TEST") }, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, with(intentSix) { setIdentifier("TEST") }, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, with(Intent()) { setIdentifier("TEST") }, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, Intent().also { it.setAction("TEST") }, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, with(block = { setIdentifier("TEST") }, receiver = Intent()), PendingIntent.FLAG_MUTABLE)
                  val intentSeven = Intent()
                  with(intentSeven) { setIdentifier("TEST") }
                  PendingIntent.getActivity(null, 0, intentSeven, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, with(Intent()) { intentSix }, PendingIntent.FLAG_MUTABLE)  // does not complain
                  PendingIntent.getService(null, 0, Intent().run { intentSix }, PendingIntent.FLAG_MUTABLE)    // does not complain
                  PendingIntent.getService(null, 0, Intent().let { intentSix }, PendingIntent.FLAG_MUTABLE)    // does not complain
                 }
              }
              """
            )
            .indented()
        )
      )
      .run()
      .expect(
        """
        src/test/pkg/PendingIntentJavaTest.java:18: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:19: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:20: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, intentThree.setIdentifier("TEST"), PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:21: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivities(null, 0, { intentOne, intentTwo }, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:22: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivities(null, 0, new Intent[] { intentOne, intentThree }, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:27: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivities(null, 0, intentsWithInitializer, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:31: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivities(null, 0, intentsWithDimensions, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:18: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:19: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:20: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, intentThree.setIdentifier("TEST"), PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:21: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, Intent().setAction("TEST"), PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:25: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivity(null, 0, intentFour, PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:26: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getBroadcast(null, 0, intentFive.run { setAction("TEST") }, PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:27: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, with(intentSix) { setIdentifier("TEST") }, PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:28: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, with(Intent()) { setIdentifier("TEST") }, PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:29: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, Intent().also { it.setAction("TEST") }, PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:30: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, with(block = { setIdentifier("TEST") }, receiver = Intent()), PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:33: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivity(null, 0, intentSeven, PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        18 errors, 0 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for src/test/pkg/PendingIntentJavaTest.java line 18: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -18 +18
        -     PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 18: Add FLAG_NO_CREATE:
        @@ -18 +18
        -     PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 19: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -19 +19
        -     PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 19: Add FLAG_NO_CREATE:
        @@ -19 +19
        -     PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 20: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -20 +20
        -     PendingIntent.getService(null, 0, intentThree.setIdentifier("TEST"), PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getService(null, 0, intentThree.setIdentifier("TEST"), PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 20: Add FLAG_NO_CREATE:
        @@ -20 +20
        -     PendingIntent.getService(null, 0, intentThree.setIdentifier("TEST"), PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getService(null, 0, intentThree.setIdentifier("TEST"), PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 21: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -21 +21
        -     PendingIntent.getActivities(null, 0, { intentOne, intentTwo }, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, { intentOne, intentTwo }, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 21: Add FLAG_NO_CREATE:
        @@ -21 +21
        -     PendingIntent.getActivities(null, 0, { intentOne, intentTwo }, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, { intentOne, intentTwo }, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 22: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -22 +22
        -     PendingIntent.getActivities(null, 0, new Intent[] { intentOne, intentThree }, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, new Intent[] { intentOne, intentThree }, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 22: Add FLAG_NO_CREATE:
        @@ -22 +22
        -     PendingIntent.getActivities(null, 0, new Intent[] { intentOne, intentThree }, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, new Intent[] { intentOne, intentThree }, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 27: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -27 +27
        -     PendingIntent.getActivities(null, 0, intentsWithInitializer, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, intentsWithInitializer, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 27: Add FLAG_NO_CREATE:
        @@ -27 +27
        -     PendingIntent.getActivities(null, 0, intentsWithInitializer, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, intentsWithInitializer, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 31: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -31 +31
        -     PendingIntent.getActivities(null, 0, intentsWithDimensions, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, intentsWithDimensions, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 31: Add FLAG_NO_CREATE:
        @@ -31 +31
        -     PendingIntent.getActivities(null, 0, intentsWithDimensions, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, intentsWithDimensions, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 18: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -18 +18
        -     PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 18: Add FLAG_NO_CREATE:
        @@ -18 +18
        -     PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 19: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -19 +19
        -     PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 19: Add FLAG_NO_CREATE:
        @@ -19 +19
        -     PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 20: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -20 +20
        -     PendingIntent.getService(null, 0, intentThree.setIdentifier("TEST"), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getService(null, 0, intentThree.setIdentifier("TEST"), PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 20: Add FLAG_NO_CREATE:
        @@ -20 +20
        -     PendingIntent.getService(null, 0, intentThree.setIdentifier("TEST"), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getService(null, 0, intentThree.setIdentifier("TEST"), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 21: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -21 +21
        -     PendingIntent.getService(null, 0, Intent().setAction("TEST"), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getService(null, 0, Intent().setAction("TEST"), PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 21: Add FLAG_NO_CREATE:
        @@ -21 +21
        -     PendingIntent.getService(null, 0, Intent().setAction("TEST"), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getService(null, 0, Intent().setAction("TEST"), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 25: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -25 +25
        -     PendingIntent.getActivity(null, 0, intentFour, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getActivity(null, 0, intentFour, PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 25: Add FLAG_NO_CREATE:
        @@ -25 +25
        -     PendingIntent.getActivity(null, 0, intentFour, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getActivity(null, 0, intentFour, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 26: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -26 +26
        -     PendingIntent.getBroadcast(null, 0, intentFive.run { setAction("TEST") }, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getBroadcast(null, 0, intentFive.run { setAction("TEST") }, PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 26: Add FLAG_NO_CREATE:
        @@ -26 +26
        -     PendingIntent.getBroadcast(null, 0, intentFive.run { setAction("TEST") }, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getBroadcast(null, 0, intentFive.run { setAction("TEST") }, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 27: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -27 +27
        -     PendingIntent.getService(null, 0, with(intentSix) { setIdentifier("TEST") }, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getService(null, 0, with(intentSix) { setIdentifier("TEST") }, PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 27: Add FLAG_NO_CREATE:
        @@ -27 +27
        -     PendingIntent.getService(null, 0, with(intentSix) { setIdentifier("TEST") }, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getService(null, 0, with(intentSix) { setIdentifier("TEST") }, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 28: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -28 +28
        -     PendingIntent.getService(null, 0, with(Intent()) { setIdentifier("TEST") }, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getService(null, 0, with(Intent()) { setIdentifier("TEST") }, PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 28: Add FLAG_NO_CREATE:
        @@ -28 +28
        -     PendingIntent.getService(null, 0, with(Intent()) { setIdentifier("TEST") }, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getService(null, 0, with(Intent()) { setIdentifier("TEST") }, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 29: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -29 +29
        -     PendingIntent.getService(null, 0, Intent().also { it.setAction("TEST") }, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getService(null, 0, Intent().also { it.setAction("TEST") }, PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 29: Add FLAG_NO_CREATE:
        @@ -29 +29
        -     PendingIntent.getService(null, 0, Intent().also { it.setAction("TEST") }, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getService(null, 0, Intent().also { it.setAction("TEST") }, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 30: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -30 +30
        -     PendingIntent.getService(null, 0, with(block = { setIdentifier("TEST") }, receiver = Intent()), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getService(null, 0, with(block = { setIdentifier("TEST") }, receiver = Intent()), PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 30: Add FLAG_NO_CREATE:
        @@ -30 +30
        -     PendingIntent.getService(null, 0, with(block = { setIdentifier("TEST") }, receiver = Intent()), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getService(null, 0, with(block = { setIdentifier("TEST") }, receiver = Intent()), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 33: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -33 +33
        -     PendingIntent.getActivity(null, 0, intentSeven, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getActivity(null, 0, intentSeven, PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 33: Add FLAG_NO_CREATE:
        @@ -33 +33
        -     PendingIntent.getActivity(null, 0, intentSeven, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getActivity(null, 0, intentSeven, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        """
      )
  }

  fun testMutableImplicitEscaped_isClean() {
    lint()
      .projects(
        project(
          manifest().targetSdk(34),
          java(
              """
              package test.pkg;

              import android.app.PendingIntent;
              import android.content.ComponentName;
              import android.content.Context;
              import android.content.Intent;
              import android.net.Uri;

              public class PendingIntentJavaTest {
                ComponentName mComponent;
                Context mContext;
                Uri mUri;
                protected void test() {
                  Intent intentOne = new Intent().setAction("TEST");
                  foo(intentOne);
                  Intent intentTwo = new Intent("TEST");
                  intentTwo.bar(mUri);
                  Intent intentThree = new Intent("TEST", mUri);
                  PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivity(null, 0, foob(new Intent()), PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getService(null, 0, intentThree.fooBar("TEST"), PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, { intentOne, intentTwo }, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, new Intent[] {  intentOne, intentThree }, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, { new Intent(), new Intent() }.clone(), PendingIntent.FLAG_MUTABLE);
                  Intent[] intentsWithInitializer = new Intent[] {intentOne, intentThree};
                  PendingIntent.getActivities(null, 0, intentsWithInitializer, PendingIntent.FLAG_MUTABLE);
                  Intent[] intentsWithDimensions = new Intent[2];
                  intentsWithDimensions[0] = new Intent().fooBar("TEST");
                  intentsWithDimensions[1] = intentOne;
                  PendingIntent.getActivities(null, 0, intentsWithDimensions, PendingIntent.FLAG_MUTABLE);
                }
                private Intent foob(Intent intent) {
                  return intent;
                }
              }
              """
            )
            .indented(),
          kotlin(
              """
              package test.pkg

              import android.app.PendingIntent
              import android.content.ComponentName
              import android.content.Context
              import android.content.Intent
              import android.net.Uri

              class PendingIntentKotlinTest {
                val mUri: Uri
                val mComponent: ComponentName
                val mContext: Context
                fun test() {
                  val intentOne = Intent().setAction("TEST")
                  intentOne.foo()
                  val intentTwo = Intent("TEST")
                  intentTwo.setData(mUri)
                  bar(intentTwo)
                  val intentThree = Intent("TEST", mUri)
                  PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getBroadcast(null, 0, intentTwo, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, intentThree.fooBar("TEST"), PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, Intent().fooBar("TEST"), PendingIntent.FLAG_MUTABLE)
                  val intentFour = Intent().let {
                    it.setAction("TEST")
                    it.xyz()
                  }
                  val intentFive = Intent("1").apply { setData(mUri) }.setIdentifier("TEST").apply { bar(this) }
                  val intentSix = Intent("TEST", mUri)
                  PendingIntent.getActivity(null, 0, intentFour, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getBroadcast(null, 0, intentFive.run {
                    setPackage(null)
                    setComponent(null)
                  }, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, with(intentSix) { xyz() }, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, with(Intent()) { bar(this) }, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, Intent().apply { abc(this) }, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, Intent().apply { def(this) }, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, Intent().also { it.foo() }, PendingIntent.FLAG_MUTABLE)
                }

                fun Intent.foo() {
                  this.setAction("foo")
                }
                fun bar(intent: Intent): Intent {
                  return intent.setPackage("pkg")
                }
                fun Intent.xyz(): Intent {
                  return this.setAction("foo")
                }
                fun Intent.abc(intent: Intent) {
                  intent.setAction("foo")
                }
                fun def(intent: Intent) {
                  intent.setAction("foo")
                }
              }
              """
            )
            .indented()
        )
      )
      .run()
      .expectClean()
  }

  fun testMutableAndImplicit_argMethodCallClassVar_isClean() {
    lint()
      .projects(
        project(
          manifest().targetSdk(34),
          java(
              """
              package test.pkg;

              import android.app.PendingIntent;
              import android.content.Intent;

              public class PendingIntentJavaTest {
                Intent mIntent = new Intent();
                protected void test(Intent intentArg) {
                  PendingIntent.getActivity(null, 0, getIntent(), PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getBroadcast(null, 0, intentArg, PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getService(null, 0, mIntent, PendingIntent.FLAG_MUTABLE);
                }

                private Intent getIntent() {
                  return new Intent();
                }
              }
              """
            )
            .indented(),
          kotlin(
              """
              package test.pkg

              import android.app.PendingIntent
              import android.content.Intent

              class PendingIntentKotlinTest {
                val mIntent = Intent()
                fun test(intent: Intent) {
                  PendingIntent.getActivity(null, 0, getIntent(), PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getBroadcast(null, 0, intentArg, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, mIntent, PendingIntent.FLAG_MUTABLE)
                }

                fun getIntent(): Intent {
                  return Intent()
                }
              }
              """
            )
            .indented()
        )
      )
      .run()
      .expectClean()
  }

  fun testReassignmentToVariableAndNewObject() {
    lint()
      .projects(
        project(
          manifest().targetSdk(34),
          java(
              """
              package test.pkg;

              import android.app.PendingIntent;
              import android.content.Intent;

              public class PendingIntentJavaTest {
                protected void test() {
                  Intent mIntent = new Intent();
                  mIntent.setPackage("test.pkg");
                  Intent kIntent = mIntent;
                  PendingIntent.getActivity(null, 0, kIntent, PendingIntent.FLAG_MUTABLE);
                  kIntent = new Intent();
                  PendingIntent.getActivity(null, 0, kIntent, PendingIntent.FLAG_MUTABLE);
                  mIntent = new Intent();
                  PendingIntent.getActivity(null, 0, mIntent, PendingIntent.FLAG_MUTABLE);
                  Intent explicitIntent = new Intent(mContext, PendingIntentJavaTest.class);
                  Intent[] arrayIntent = new Intent[2];
                  arrayIntent[0] = new Intent();
                  arrayIntent[1] = explicitIntent;
                  arrayIntent[0] = explicitIntent;
                  PendingIntent.getActivities(null, 0, arrayIntent, PendingIntent.FLAG_MUTABLE);
                  arrayIntent[1] = new Intent();
                  PendingIntent.getActivities(null, 0, arrayIntent, PendingIntent.FLAG_MUTABLE);
                  arrayIntent[1] = explicitIntent;
                  arrayIntent = new Intent[1];
                  arrayIntent[0] = new Intent();
                  PendingIntent.getActivities(null, 0, arrayIntent, PendingIntent.FLAG_MUTABLE);
                }
              }
              """
            )
            .indented(),
          kotlin(
              """
              package test.pkg

              import android.app.PendingIntent
              import android.content.Intent

              class PendingIntentKotlinTest {
                fun test(intent: Intent) {
                  var mIntent = Intent()
                  mIntent.setPackage("test.pkg")
                  var kIntent = mIntent
                  PendingIntent.getActivity(null, 0, kIntent, PendingIntent.FLAG_MUTABLE)
                  kIntent = Intent()
                  PendingIntent.getActivity(null, 0, kIntent, PendingIntent.FLAG_MUTABLE)
                  mIntent = Intent()
                  PendingIntent.getActivity(null, 0, mIntent, PendingIntent.FLAG_MUTABLE)
                }
              }
              """
            )
            .indented()
        )
      )
      .run()
      .expect(
        """
        src/test/pkg/PendingIntentJavaTest.java:13: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivity(null, 0, kIntent, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:15: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivity(null, 0, mIntent, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:23: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivities(null, 0, arrayIntent, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:27: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivities(null, 0, arrayIntent, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:13: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivity(null, 0, kIntent, PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:15: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivity(null, 0, mIntent, PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        6 errors, 0 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for src/test/pkg/PendingIntentJavaTest.java line 13: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -13 +13
        -     PendingIntent.getActivity(null, 0, kIntent, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivity(null, 0, kIntent, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 13: Add FLAG_NO_CREATE:
        @@ -13 +13
        -     PendingIntent.getActivity(null, 0, kIntent, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivity(null, 0, kIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 15: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -15 +15
        -     PendingIntent.getActivity(null, 0, mIntent, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivity(null, 0, mIntent, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 15: Add FLAG_NO_CREATE:
        @@ -15 +15
        -     PendingIntent.getActivity(null, 0, mIntent, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivity(null, 0, mIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 23: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -23 +23
        -     PendingIntent.getActivities(null, 0, arrayIntent, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, arrayIntent, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 23: Add FLAG_NO_CREATE:
        @@ -23 +23
        -     PendingIntent.getActivities(null, 0, arrayIntent, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, arrayIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 27: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -27 +27
        -     PendingIntent.getActivities(null, 0, arrayIntent, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, arrayIntent, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 27: Add FLAG_NO_CREATE:
        @@ -27 +27
        -     PendingIntent.getActivities(null, 0, arrayIntent, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, arrayIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 13: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -13 +13
        -     PendingIntent.getActivity(null, 0, kIntent, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getActivity(null, 0, kIntent, PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 13: Add FLAG_NO_CREATE:
        @@ -13 +13
        -     PendingIntent.getActivity(null, 0, kIntent, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getActivity(null, 0, kIntent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 15: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -15 +15
        -     PendingIntent.getActivity(null, 0, mIntent, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getActivity(null, 0, mIntent, PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 15: Add FLAG_NO_CREATE:
        @@ -15 +15
        -     PendingIntent.getActivity(null, 0, mIntent, PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getActivity(null, 0, mIntent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        """
      )
  }

  fun testTargetSdkBelowThirtyFourIsWarning() {
    lint()
      .projects(
        project(
          manifest().targetSdk(33),
          java(
              """
              package test.pkg;

              import android.app.PendingIntent;
              import android.content.Intent;
              import android.net.Uri;

              public class PendingIntentJavaTest {
                Uri mUri;
                protected void test() {
                  PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getService(null, 0, new Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, { new Intent(), new Intent("TEST") }, PendingIntent.FLAG_MUTABLE);
                }
              }
              """
            )
            .indented(),
          kotlin(
              """
              package test.pkg

              import android.app.PendingIntent
              import android.content.Intent
              import android.net.Uri

              class PendingIntentKotlinTest {
                val mUri: Uri
                fun test(intent: Intent) {
                  PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE)
                }
              }
              """
            )
            .indented()
        )
      )
      .run()
      .expect(
        """
        src/test/pkg/PendingIntentJavaTest.java:10: Warning: Mutable implicit PendingIntent will throw an exception once this app starts targeting Android 14 or above, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:11: Warning: Mutable implicit PendingIntent will throw an exception once this app starts targeting Android 14 or above, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:12: Warning: Mutable implicit PendingIntent will throw an exception once this app starts targeting Android 14 or above, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, new Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:13: Warning: Mutable implicit PendingIntent will throw an exception once this app starts targeting Android 14 or above, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivities(null, 0, { new Intent(), new Intent("TEST") }, PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:10: Warning: Mutable implicit PendingIntent will throw an exception once this app starts targeting Android 14 or above, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:11: Warning: Mutable implicit PendingIntent will throw an exception once this app starts targeting Android 14 or above, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:12: Warning: Mutable implicit PendingIntent will throw an exception once this app starts targeting Android 14 or above, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 7 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for src/test/pkg/PendingIntentJavaTest.java line 10: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -10 +10
        -     PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 10: Add FLAG_NO_CREATE:
        @@ -10 +10
        -     PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 11: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -11 +11
        -     PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 11: Add FLAG_NO_CREATE:
        @@ -11 +11
        -     PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 12: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -12 +12
        -     PendingIntent.getService(null, 0, new Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getService(null, 0, new Intent("TEST", mUri), PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 12: Add FLAG_NO_CREATE:
        @@ -12 +12
        -     PendingIntent.getService(null, 0, new Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getService(null, 0, new Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 13: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -13 +13
        -     PendingIntent.getActivities(null, 0, { new Intent(), new Intent("TEST") }, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, { new Intent(), new Intent("TEST") }, PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 13: Add FLAG_NO_CREATE:
        @@ -13 +13
        -     PendingIntent.getActivities(null, 0, { new Intent(), new Intent("TEST") }, PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getActivities(null, 0, { new Intent(), new Intent("TEST") }, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 10: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -10 +10
        -     PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 10: Add FLAG_NO_CREATE:
        @@ -10 +10
        -     PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 11: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -11 +11
        -     PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 11: Add FLAG_NO_CREATE:
        @@ -11 +11
        -     PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 12: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -12 +12
        -     PendingIntent.getService(null, 0, Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getService(null, 0, Intent("TEST", mUri), PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 12: Add FLAG_NO_CREATE:
        @@ -12 +12
        -     PendingIntent.getService(null, 0, Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getService(null, 0, Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        """
      )
  }

  fun testTargetSdkBelowTwentyThreeIsClean() {
    lint()
      .projects(
        project(
          manifest().targetSdk(22),
          java(
              """
              package test.pkg;

              import android.app.PendingIntent;
              import android.content.Intent;
              import android.net.Uri;

              public class PendingIntentJavaTest {
                Uri mUri;
                protected void test() {
                  PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getService(null, 0, new Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getActivities(null, 0, { new Intent(), new Intent("TEST") }, PendingIntent.FLAG_MUTABLE);
                }
              }
              """
            )
            .indented(),
          kotlin(
              """
              package test.pkg

              import android.app.PendingIntent
              import android.content.Intent
              import android.net.Uri

              class PendingIntentKotlinTest {
                val mUri: Uri
                fun test(intent: Intent) {
                  PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, Intent("TEST", mUri), PendingIntent.FLAG_MUTABLE)
                }
              }
              """
            )
            .indented()
        )
      )
      .run()
      .expectClean()
  }

  fun testMutableAndImplicitWithOtherFlagsAndVariable_complains() {
    lint()
      .projects(
        project(
          manifest().targetSdk(34),
          java(
              """
              package test.pkg;

              import android.app.PendingIntent;
              import android.content.Intent;
              import android.net.Uri;

              public class PendingIntentJavaTest {
                Uri mUri;
                protected void test() {
                  int mMutable = PendingIntent.FLAG_MUTABLE;
                  int mMutableAndCancel = PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE;
                  PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ONE_SHOT);
                  PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
                  PendingIntent.getService(null, 0, new Intent("TEST", mUri), mMutableAndCancel);
                  PendingIntent.getService(null, 0, new Intent("TEST", mUri), mMutable);
                  PendingIntent.getActivities(null, 0, { new Intent(), new Intent("TEST") }, mMutable | PendingIntent.FLAG_UPDATE_CURRENT);
                  PendingIntent.getActivities(null, 0, new Intent[] { new Intent("TEST"), new Intent("TEST", mUri) }, PendingIntent.FLAG_UPDATE_CURRENT | mMutable);
                }
              }
              """
            )
            .indented(),
          kotlin(
              """
              package test.pkg

              import android.app.PendingIntent
              import android.content.Intent
              import android.net.Uri

              class PendingIntentKotlinTest {
                val mUri: Uri
                val mMutable: Int = PendingIntent.FLAG_MUTABLE
                val mMutableAndCancel = PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
                fun test() {
                  PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT)
                  PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, Intent("TEST", mUri), mMutableAndCancel)
                  PendingIntent.getService(null, 0, Intent("TEST", mUri), mMutable)
                  PendingIntent.getService(null, 0, Intent("TEST", mUri), mMutable or PendingIntent.FLAG_UPDATE_CURRENT)
                  PendingIntent.getService(null, 0, Intent("TEST", mUri), PendingIntent.FLAG_UPDATE_CURRENT or mMutable)
                }
              }
              """
            )
            .indented()
        )
      )
      .run()
      .expect(
        """
        src/test/pkg/PendingIntentJavaTest.java:12: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ONE_SHOT);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:13: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:14: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, new Intent("TEST", mUri), mMutableAndCancel);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:15: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, new Intent("TEST", mUri), mMutable);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:16: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivities(null, 0, { new Intent(), new Intent("TEST") }, mMutable | PendingIntent.FLAG_UPDATE_CURRENT);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentJavaTest.java:17: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivities(null, 0, new Intent[] { new Intent("TEST"), new Intent("TEST", mUri) }, PendingIntent.FLAG_UPDATE_CURRENT | mMutable);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:12: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:13: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:14: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, Intent("TEST", mUri), mMutableAndCancel)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:15: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, Intent("TEST", mUri), mMutable)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:16: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, Intent("TEST", mUri), mMutable or PendingIntent.FLAG_UPDATE_CURRENT)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/PendingIntentKotlinTest.kt:17: Error: Mutable implicit PendingIntent will throw an exception, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit [MutableImplicitPendingIntent]
            PendingIntent.getService(null, 0, Intent("TEST", mUri), PendingIntent.FLAG_UPDATE_CURRENT or mMutable)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        12 errors, 0 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for src/test/pkg/PendingIntentJavaTest.java line 12: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -12 +12
        -     PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ONE_SHOT);
        +     PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 12: Add FLAG_NO_CREATE:
        @@ -12 +12
        -     PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ONE_SHOT);
        +     PendingIntent.getActivity(null, 0, new Intent(), PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 13: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -13 +13
        -     PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 13: Add FLAG_NO_CREATE:
        @@ -13 +13
        -     PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
        +     PendingIntent.getBroadcast(null, 0, new Intent("TEST"), PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 14: Add FLAG_NO_CREATE:
        @@ -14 +14
        -     PendingIntent.getService(null, 0, new Intent("TEST", mUri), mMutableAndCancel);
        +     PendingIntent.getService(null, 0, new Intent("TEST", mUri), mMutableAndCancel | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 15: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -15 +15
        -     PendingIntent.getService(null, 0, new Intent("TEST", mUri), mMutable);
        +     PendingIntent.getService(null, 0, new Intent("TEST", mUri), PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 15: Add FLAG_NO_CREATE:
        @@ -15 +15
        -     PendingIntent.getService(null, 0, new Intent("TEST", mUri), mMutable);
        +     PendingIntent.getService(null, 0, new Intent("TEST", mUri), mMutable | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 16: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -16 +16
        -     PendingIntent.getActivities(null, 0, { new Intent(), new Intent("TEST") }, mMutable | PendingIntent.FLAG_UPDATE_CURRENT);
        +     PendingIntent.getActivities(null, 0, { new Intent(), new Intent("TEST") }, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 16: Add FLAG_NO_CREATE:
        @@ -16 +16
        -     PendingIntent.getActivities(null, 0, { new Intent(), new Intent("TEST") }, mMutable | PendingIntent.FLAG_UPDATE_CURRENT);
        +     PendingIntent.getActivities(null, 0, { new Intent(), new Intent("TEST") }, mMutable | PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 17: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -17 +17
        -     PendingIntent.getActivities(null, 0, new Intent[] { new Intent("TEST"), new Intent("TEST", mUri) }, PendingIntent.FLAG_UPDATE_CURRENT | mMutable);
        +     PendingIntent.getActivities(null, 0, new Intent[] { new Intent("TEST"), new Intent("TEST", mUri) }, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Fix for src/test/pkg/PendingIntentJavaTest.java line 17: Add FLAG_NO_CREATE:
        @@ -17 +17
        -     PendingIntent.getActivities(null, 0, new Intent[] { new Intent("TEST"), new Intent("TEST", mUri) }, PendingIntent.FLAG_UPDATE_CURRENT | mMutable);
        +     PendingIntent.getActivities(null, 0, new Intent[] { new Intent("TEST"), new Intent("TEST", mUri) }, PendingIntent.FLAG_UPDATE_CURRENT | mMutable | PendingIntent.FLAG_NO_CREATE);
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 12: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -12 +12
        -     PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT)
        +     PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 12: Add FLAG_NO_CREATE:
        @@ -12 +12
        -     PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT)
        +     PendingIntent.getActivity(null, 0, Intent(), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 13: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -13 +13
        -     PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 13: Add FLAG_NO_CREATE:
        @@ -13 +13
        -     PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE)
        +     PendingIntent.getBroadcast(null, 0, Intent("TEST"), PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 14: Add FLAG_NO_CREATE:
        @@ -14 +14
        -     PendingIntent.getService(null, 0, Intent("TEST", mUri), mMutableAndCancel)
        +     PendingIntent.getService(null, 0, Intent("TEST", mUri), mMutableAndCancel or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 15: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -15 +15
        -     PendingIntent.getService(null, 0, Intent("TEST", mUri), mMutable)
        +     PendingIntent.getService(null, 0, Intent("TEST", mUri), PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 15: Add FLAG_NO_CREATE:
        @@ -15 +15
        -     PendingIntent.getService(null, 0, Intent("TEST", mUri), mMutable)
        +     PendingIntent.getService(null, 0, Intent("TEST", mUri), mMutable or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 16: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -16 +16
        -     PendingIntent.getService(null, 0, Intent("TEST", mUri), mMutable or PendingIntent.FLAG_UPDATE_CURRENT)
        +     PendingIntent.getService(null, 0, Intent("TEST", mUri), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 16: Add FLAG_NO_CREATE:
        @@ -16 +16
        -     PendingIntent.getService(null, 0, Intent("TEST", mUri), mMutable or PendingIntent.FLAG_UPDATE_CURRENT)
        +     PendingIntent.getService(null, 0, Intent("TEST", mUri), mMutable or PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_NO_CREATE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 17: Replace FLAG_MUTABLE with FLAG_IMMUTABLE:
        @@ -17 +17
        -     PendingIntent.getService(null, 0, Intent("TEST", mUri), PendingIntent.FLAG_UPDATE_CURRENT or mMutable)
        +     PendingIntent.getService(null, 0, Intent("TEST", mUri), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        Fix for src/test/pkg/PendingIntentKotlinTest.kt line 17: Add FLAG_NO_CREATE:
        @@ -17 +17
        -     PendingIntent.getService(null, 0, Intent("TEST", mUri), PendingIntent.FLAG_UPDATE_CURRENT or mMutable)
        +     PendingIntent.getService(null, 0, Intent("TEST", mUri), PendingIntent.FLAG_UPDATE_CURRENT or mMutable or PendingIntent.FLAG_NO_CREATE)
        """
      )
  }
}
