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

class WearBackNavigationDetectorTest : AbstractCheckTest() {

  override fun getDetector(): Detector = WearBackNavigationDetector()

  fun testDocumentationExample() {
    lint()
      .files(manifestWithActivityTheme, themeFile)
      .run()
      .expect(
        """
              res/values/styles.xml:10: Warning: Disabling swipe-to-dismiss is generally not recommended for Wear applications [WearBackNavigation]
                     <item name="android:windowSwipeToDismiss">false</item>
                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
              0 errors, 1 warnings
            """
          .trimIndent()
      )
      .expectFixDiffs(
        """
        Autofix for res/values/styles.xml line 10: Delete `android:windowSwipeToDismiss` from theme:
        @@ -10 +10
        -        <item name="android:windowSwipeToDismiss">false</item>
      """
          .trimIndent()
      )
  }

  private val themeFile =
    xml(
      "res/values/styles.xml",
      """
          <resources>

            <style name="AppTheme" parent="Theme.AppCompat.Light.DarkActionBar">
                <item name="colorPrimary">@color/colorPrimary</item>
                <item name="colorAccent">@color/colorAccent</item>
                <item name="android:windowSwipeToDismiss">true</item>
            </style>

            <style name="SubTheme" parent="AppTheme">
                <item name="android:windowSwipeToDismiss">false</item>
            </style>

         </resources>
        """
        .trimIndent(),
    )

  private val manifestWithActivityTheme =
    manifest(
      // language=xml
      """
          <?xml version="1.0" encoding="utf-8"?>
          <manifest xmlns:android="http://schemas.android.com/apk/res/android"
              package="test.pkg">
               <uses-sdk android:minSdkVersion="30" />
               <uses-feature android:name="android.hardware.type.watch" />
              <application
                  android:icon="@mipmap/ic_launcher"
                  android:label="@string/app_name">
                  <activity android:name=".MainActivity"
                      android:theme="@style/AppTheme" />
              </application>
          </manifest>
        """
        .trimIndent()
    )
}
