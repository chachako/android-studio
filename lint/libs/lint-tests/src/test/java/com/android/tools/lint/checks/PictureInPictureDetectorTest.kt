/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.tools.lint.detector.api.Detector

class PictureInPictureDetectorTest : AbstractCheckTest() {

  override fun getDetector(): Detector = PictureInPictureDetector()

  fun testDocumentationExample() {
    lint()
      .files(
        androidManifest,
        kotlin(
            """
            package test.pkg

            import android.app.Activity

            class TestActivity: Activity {
              fun test() {
                enterPictureInPictureMode()
              }
            }
            """
          )
          .indented(),
        enterPipModeStub,
      )
      .run()
      .expect(
        """
        AndroidManifest.xml:9: Warning: An activity in this app supports picture-in-picture and the targetSdkVersion is 31 or above; it is therefore strongly recommended to call both setAutoEnterEnabled(true) and setSourceRectHint(...) [PictureInPictureIssue]
            <application
             ~~~~~~~~~~~
        0 errors, 1 warnings
      """
      )
  }

  fun testTargetSdkVersion() {
    lint()
      .files(
        manifest(
            """
            <manifest
              package="test.pkg"
              xmlns:android="http://schemas.android.com/apk/res/android">

                <uses-sdk
                  android:minSdkVersion="28"
                  android:targetSdkVersion="30" />

                <application
                    android:icon="@mipmap/ic_launcher"
                    android:label="@string/app_name">

                    <activity
                        android:name=".TestActivity"
                        android:exported="true"
                        android:supportsPictureInPicture="true"
                        android:configChanges="screenLayout|orientation|screenSize|smallestScreenSize">

                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>

                    </activity>

                </application>

            </manifest>
            """
          )
          .indented(),
        kotlin(
            """
            package test.pkg

            import android.app.Activity

            class TestActivity: Activity {
              fun test() {
                enterPictureInPictureMode()
              }
            }
            """
          )
          .indented(),
        enterPipModeStub,
      )
      .run()
      .expectClean()
  }

  fun testForNewPipApproachUsedCorrectly() {
    lint()
      .files(
        androidManifest,
        kotlin(
            """
            package test.pkg

            import android.app.Activity
            import android.app.PictureInPictureParams
            import android.graphics.Rect

            class TestActivity: Activity {
              fun test() {
                val builder = PictureInPictureParams.Builder()
                builder.setAutoEnterEnabled(true)
                builder.setSourceRectHint(Rect())
                setPictureInPictureParams(builder.build())
              }
            }
            """
          )
          .indented(),
        enterPipModeStub,
        pipParamsBuilderStub,
        rectStub,
      )
      .run()
      .expectClean()
  }

  fun testForNewPipApproachAlmostUsedCorrectly() {
    lint()
      .files(
        androidManifest,
        kotlin(
            """
            package test.pkg

            import android.app.Activity
            import android.app.PictureInPictureParams
            import android.graphics.Rect

            class TestActivity: Activity {
              fun test() {
                val builder = PictureInPictureParams.Builder()
                builder.setAutoEnterEnabled(true)
                // missing call to builder.setSourceRectHint(Rect())
                setPictureInPictureParams(builder.build())
              }
            }
            """
          )
          .indented(),
        enterPipModeStub,
        pipParamsBuilderStub,
        rectStub,
      )
      .run()
      .expect(
        """
          AndroidManifest.xml:9: Warning: An activity in this app supports picture-in-picture and the targetSdkVersion is 31 or above; it is therefore strongly recommended to call both setAutoEnterEnabled(true) and setSourceRectHint(...) [PictureInPictureIssue]
              <application
               ~~~~~~~~~~~
          0 errors, 1 warnings
        """
      )
  }

  fun testForNoPipActivity() {
    lint()
      .files(
        manifest(
            """
            <manifest
              package="test.pkg"
              xmlns:android="http://schemas.android.com/apk/res/android">

                <uses-sdk
                  android:minSdkVersion="28"
                  android:targetSdkVersion="31" />

                <application
                    android:icon="@mipmap/ic_launcher"
                    android:label="@string/app_name">

                    <activity
                        android:name=".TestActivity"
                        android:exported="true"
                        android:configChanges="screenLayout|orientation|screenSize|smallestScreenSize">

                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>

                    </activity>

                </application>

            </manifest>
            """
          )
          .indented(),
        kotlin(
            """
            package test.pkg

            import android.app.Activity

            class TestActivity: Activity {
              fun test() {
                enterPictureInPictureMode()
              }
            }
            """
          )
          .indented(),
        enterPipModeStub,
      )
      .run()
      .expectClean()
  }

  fun testNoPipMethodCalls() {
    lint()
      .files(
        androidManifest,
        kotlin(
            """
            package test.pkg

            import android.app.Activity

            class TestActivity: Activity {
              fun test() {
                // no calls
              }
            }
            """
          )
          .indented(),
        enterPipModeStub,
      )
      .run()
      .expectClean()
  }

  private val androidManifest: TestFile =
    manifest(
        """
        <manifest
          package="test.pkg"
          xmlns:android="http://schemas.android.com/apk/res/android">

            <uses-sdk
              android:minSdkVersion="28"
              android:targetSdkVersion="31" />

            <application
                android:icon="@mipmap/ic_launcher"
                android:label="@string/app_name">

                <activity
                    android:name=".TestActivity"
                    android:exported="true"
                    android:supportsPictureInPicture="true"
                    android:configChanges="screenLayout|orientation|screenSize|smallestScreenSize">

                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>

                </activity>

            </application>

        </manifest>
        """
      )
      .indented()

  private val enterPipModeStub: TestFile =
    java(
        """
        package android.app;
        /*HIDE-FROM-DOCUMENTATION*/

        public class Activity {
          public void enterPictureInPictureMode();
          public void setPictureInPictureParams(PictureInPictureParams params);
        }
        """
      )
      .indented()

  private val pipParamsBuilderStub: TestFile =
    java(
        """
        package android.app;
        /*HIDE-FROM-DOCUMENTATION*/

        public class PictureInPictureParams {
          public static class Builder {
            public Builder() {}
            public Builder setAutoEnterEnabled(boolean flag);
            public Builder setSourceRectHint(Rect sourceRectHint);
            public PictureInPictureParams build();
          }
        }
        """
      )
      .indented()

  private val rectStub: TestFile =
    java(
        """
        package android.graphics;
        /*HIDE-FROM-DOCUMENTATION*/

        class Rect {}
        """
      )
      .indented()
}
