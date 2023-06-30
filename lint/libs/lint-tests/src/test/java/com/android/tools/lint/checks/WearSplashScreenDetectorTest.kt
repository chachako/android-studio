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

class WearSplashScreenDetectorTest : AbstractCheckTest() {

  override fun getDetector(): Detector = WearSplashScreenDetector()

  fun testDocumentationExample() {
    lint()
      .files(manifestWithActivityTheme(30))
      .run()
      .expect(
        """
              AndroidManifest.xml:16: Warning: Applications using splash screens are strongly recommended to use the 'androidx.core:core-splashscreen' library [WearSplashScreen]
                      <activity android:name=".SplashActivity"
                      ^
              0 errors, 1 warnings
            """
          .trimIndent()
      )

    // API > 30 will generate splash screens so we do not want the user to add the splashscreen
    // library.
    lint().files(manifestWithActivityTheme(31)).run().expectClean()
  }

  fun testSplashScreenUsesSplashLibrary() {
    lint().files(manifestWithActivityTheme(30), gradleFileWithSplashLibrary).run().expectClean()
  }

  private fun manifestWithActivityTheme(minSdk: Int) =
    manifest(
      // language=xml
      """
          <?xml version="1.0" encoding="utf-8"?>
          <manifest xmlns:android="http://schemas.android.com/apk/res/android"
              package="test.pkg">
               <uses-sdk android:minSdkVersion="$minSdk" />
               <uses-feature android:name="android.hardware.type.watch" />
              <application
                  android:icon="@mipmap/ic_launcher"
                  android:label="@string/app_name">
                  <activity android:name=".MainActivity"
                      android:theme="@style/AppTheme">
                       <intent-filter>
                          <action android:name="android.intent.action.MAIN" />
                          <category android:name="android.intent.category.LAUNCHER" />
                      </intent-filter>
                   </activity>
                  <activity android:name=".SplashActivity"
                      android:theme="@style/AppTheme">
                       <intent-filter>
                          <action android:name="android.intent.action.MAIN" />
                          <category android:name="android.intent.category.LAUNCHER" />
                      </intent-filter>
                  </activity>
              </application>
          </manifest>
        """
        .trimIndent()
    )

  private val gradleFileWithSplashLibrary =
    gradle(
      """
        apply plugin: 'com.android.application'

        android {
            compileSdkVersion 30

            defaultConfig {
                minSdkVersion 30
                targetSdkVersion 32
            }
        }

        dependencies {
            compile 'androidx.core:core-splashscreen:+'
        }
        """
        .trimIndent()
    )
}
