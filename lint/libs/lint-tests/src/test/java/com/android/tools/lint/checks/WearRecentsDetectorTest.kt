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

class WearRecentsDetectorTest : AbstractCheckTest() {

  override fun getDetector() = WearRecentsDetector()

  fun testDocumentationExample() {
    lint()
      .files(
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  package="com.example.helloworld"
                  android:versionCode="1"
                  android:versionName="1.0">
                <uses-sdk android:minSdkVersion="30" />
                <uses-feature android:name="android.hardware.type.watch" />
                <application android:icon="@drawable/icon" android:label="@string/app_name">
                <activity android:name=".MainActivity" />
                </application>
            </manifest>"""
          )
          .indented()
      )
      .run()
      .expect(
        """
        AndroidManifest.xml:8: Warning: Set taskAffinity for Wear activities to make them appear correctly in recents [WearRecents]
            <activity android:name=".MainActivity" />
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
      """
      )
      .expectFixDiffs(
        """
              Autofix for AndroidManifest.xml line 8: Set `taskAffinity`:
              @@ -14 +14
              -         <activity android:name=".MainActivity" />
              +         <activity
              +             android:name=".MainActivity"
              +             android:taskAffinity="|" />
              Autofix for AndroidManifest.xml line 8: Exclude from recents:
              @@ -14 +14
              -         <activity android:name=".MainActivity" />
              +         <activity
              +             android:name=".MainActivity"
              +             android:excludeFromRecents="true"
              +             android:noHistory="true" />
        """
      )
  }

  fun testTaskAffinityNonWear() {
    lint()
      .files(
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  package="com.example.helloworld"
                  android:versionCode="1"
                  android:versionName="1.0">
                <uses-sdk android:minSdkVersion="30" />
                <application android:icon="@drawable/icon" android:label="@string/app_name">
                <activity android:name=".MainActivity" />
                </application>
            </manifest>"""
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testExcludeFromRecentsWithoutNoHistory() {
    lint()
      .files(
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  package="com.example.helloworld"
                  android:versionCode="1"
                  android:versionName="1.0">
                <uses-sdk android:minSdkVersion="30" />
                <uses-feature android:name="android.hardware.type.watch" />
                <application android:icon="@drawable/icon" android:label="@string/app_name">
                <activity android:name=".MainActivity" android:excludeFromRecents="true" />
                </application>
            </manifest>"""
          )
          .indented()
      )
      .run()
      .expect(
        """
        AndroidManifest.xml:8: Warning: In addition to excludeFromRecents, set noHistory flag to avoid showing this activity in recents [WearRecents]
            <activity android:name=".MainActivity" android:excludeFromRecents="true" />
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
      """
      )
      .expectFixDiffs(
        """
          Fix for AndroidManifest.xml line 8: Set noHistory:
          @@ -16 +16
          -             android:excludeFromRecents="true" />
          +             android:excludeFromRecents="true"
          +             android:noHistory="true" />
      """
      )
  }

  fun testExcludeFromRecentsWithNoHistory() {
    lint()
      .files(
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  package="com.example.helloworld"
                  android:versionCode="1"
                  android:versionName="1.0">
                <uses-sdk android:minSdkVersion="30" />
                <uses-feature android:name="android.hardware.type.watch" />
                <application android:icon="@drawable/icon" android:label="@string/app_name">
                <activity android:name=".MainActivity"
                        android:excludeFromRecents="true"
                        android:noHistory="true" />
                </application>
            </manifest>"""
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testExcludeFromRecentsWithoutNoHistoryNonWear() {
    lint()
      .files(
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  package="com.example.helloworld"
                  android:versionCode="1"
                  android:versionName="1.0">
                <uses-sdk android:minSdkVersion="30" />
                <application android:icon="@drawable/icon" android:label="@string/app_name">
                <activity android:name=".MainActivity" android:excludeFromRecents="true" />
                </application>
            </manifest>"""
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testFlagActivityNewTask() {
    lint()
      .files(
        java(
          """
          import android.content.Intent;

          public static class MainActivity {
            MainActivity() {
              startActivity(Intent("ACTION_TEST").setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
          }
        """
        ),
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  package="com.example.helloworld"
                  android:versionCode="1"
                  android:versionName="1.0">
                <uses-sdk android:minSdkVersion="30" />
                <uses-feature android:name="android.hardware.type.watch" />
                <application android:icon="@drawable/icon" android:label="@string/app_name" />
            </manifest>"""
          )
          .indented(),
      )
      .run()
      .expect(
        """
            src/MainActivity.java:6: Warning: Avoid using FLAG_ACTIVITY_NEW_TASK and FLAG_ACTIVITY_CLEAR_TOP [WearRecents]
                          startActivity(Intent("ACTION_TEST").setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                                                                              ~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
      """
      )
  }

  fun testFlagActivityNewTaskNonWear() {
    lint()
      .files(
        java(
          """
          import android.content.Intent;

          public static class MainActivity {
            MainActivity() {
              startActivity(Intent("ACTION_TEST").setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
          }
        """
        ),
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  package="com.example.helloworld"
                  android:versionCode="1"
                  android:versionName="1.0">
                <uses-sdk android:minSdkVersion="30" />
                <application android:icon="@drawable/icon" android:label="@string/app_name" />
            </manifest>"""
          )
          .indented(),
      )
      .run()
      .expectClean()
  }
}
