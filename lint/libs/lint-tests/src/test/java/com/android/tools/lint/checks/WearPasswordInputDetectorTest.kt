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

private const val INPUT_TYPE_TEXT =
  """
          <?xml version="1.0" encoding="utf-8"?>
          <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:tools="http://schemas.android.com/tools"
              android:layout_width="match_parent"
              android:layout_height="match_parent">
            <EditText
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:autofillHints=""
                android:inputType="text"
                tools:ignore="LabelFor" />
          </LinearLayout>
    """

private const val INPUT_TYPE_PASSWORD =
  """
          <?xml version="1.0" encoding="utf-8"?>
          <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:tools="http://schemas.android.com/tools"
              android:layout_width="match_parent"
              android:layout_height="match_parent">
            <EditText
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:autofillHints=""
                android:inputType="textPassword"
                tools:ignore="LabelFor" />
          </LinearLayout>
    """

private const val NON_WEAR_MANIFEST =
  """
          <manifest xmlns:android="http://schemas.android.com/apk/res/android"
               package="com.example.helloworld"
               android:versionCode="1"
               android:versionName="1.0">
             <uses-sdk android:minSdkVersion="30" />
             <application android:icon="@drawable/icon" android:label="@string/app_name">
                 <meta-data android:name="com.google.android.actions" android:resource="@xml/actions" />
                 <activity android:name=".HelloWorld"
                           android:label="@string/app_name">
                 </activity>
             </application>
          </manifest>
      """

private const val WEAR_MANIFEST =
  """
          <manifest xmlns:android="http://schemas.android.com/apk/res/android"
               package="com.example.helloworld"
               android:versionCode="1"
               android:versionName="1.0">
             <uses-sdk android:minSdkVersion="30" />
             <uses-feature android:name="android.hardware.type.watch" />
             <application android:icon="@drawable/icon" android:label="@string/app_name">
                 <meta-data android:name="com.google.android.actions" android:resource="@xml/actions" />
                 <activity android:name=".HelloWorld"
                           android:label="@string/app_name">
                 </activity>
             </application>
          </manifest>
      """

class WearPasswordInputDetectorTest : AbstractCheckTest() {

  override fun getDetector(): Detector = WearPasswordInputDetector()

  fun testDocumentationExample() {
    lint()
      .files(
        xml("res/layout/main.xml", INPUT_TYPE_PASSWORD).indented(),
        manifest(WEAR_MANIFEST).indented()
      )
      .run()
      .expect(
        """
        res/layout/main.xml:10: Error: Don't ask Wear OS users for a password [WearPasswordInput]
              android:inputType="textPassword"
              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
      """
      )
  }

  fun testInputTypeTextOnWear() {
    lint()
      .files(
        xml("res/layout/main.xml", INPUT_TYPE_TEXT).indented(),
        manifest(WEAR_MANIFEST).indented()
      )
      .run()
      .expectClean()
  }

  fun testInputTypeTextOnNonWear() {
    lint()
      .files(
        xml("res/layout/main.xml", INPUT_TYPE_TEXT).indented(),
        manifest(NON_WEAR_MANIFEST).indented()
      )
      .run()
      .expectClean()
  }

  fun testInputTypePasswordOnNonWear() {
    lint()
      .files(
        xml("res/layout/main.xml", INPUT_TYPE_PASSWORD).indented(),
        manifest(NON_WEAR_MANIFEST).indented()
      )
      .run()
      .expectClean()
  }
}
