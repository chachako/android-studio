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
                  PendingIntent.getActivity(null, 0, Intent(mContext, PendingIntentKotlinTest::class), PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getBroadcast(null, 0, Intent(mContext, PendingIntentKotlinTest::class), PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, Intent(mContext, PendingIntentKotlinTest::class), PendingIntent.FLAG_MUTABLE)

                  // Intent(String, Uri, Context, Class)
                  PendingIntent.getActivity(null, 0, Intent("TEST", mUri, mContext, PendingIntentKotlinTest::class), PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getBroadcast(null, 0, Intent("TEST", mUri, mContext, PendingIntentKotlinTest::class), PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, Intent("TEST", mUri, mContext, PendingIntentKotlinTest::class), PendingIntent.FLAG_MUTABLE)
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
                  val intentOne = Intent(mContext, PendingIntentKotlinTest::class)
                  PendingIntent.getActivity(null, 0, intentOne, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getBroadcast(null, 0, intentOne, PendingIntent.FLAG_MUTABLE)
                  PendingIntent.getService(null, 0, intentOne, PendingIntent.FLAG_MUTABLE)

                  // Intent(String, Uri, Context, Class)
                  val intentTwo = Intent("TEST", mUri, mContext, PendingIntentKotlinTest::class)
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
        11 errors, 0 warnings
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
                }

                fun Intent.foo() {
                    this.setAction("foo")
                }
                fun bar(intent: Intent) {
                    intent.setPackage("pkg")
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
}
